package net.kdt.pojavlaunch.authenticator.accounts;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Small local HTTP server used for offline local-account skins.
 *
 * Everything is served from the Android device itself.
 *
 * Routes:
 *
 * /skin.png
 *     -> local PNG skin
 *
 * /sessionserver/session/minecraft/profile/<uuid>
 *     -> Minecraft profile + textures property
 *
 * /textures/skin.png
 *     -> local PNG skin
 */
public final class LocalSkinServer {

    private static final String TAG = "LocalSkinServer";

    private final Account account;

    private ServerSocket serverSocket;
    private Thread serverThread;

    private volatile boolean running;

    private int port;

    public LocalSkinServer(Account account) {
        this.account = account;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        serverSocket = new ServerSocket(0);
        serverSocket.setReuseAddress(true);

        port = serverSocket.getLocalPort();
        running = true;

        serverThread = new Thread(
                this::runServer,
                "LocalSkinServer"
        );

        serverThread.setDaemon(true);
        serverThread.start();

        Log.i(
                TAG,
                "Started local skin server on 127.0.0.1:" + port
        );
    }

    public synchronized void stop() {
        running = false;

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }

            serverSocket = null;
        }

        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }

        Log.i(TAG, "Local skin server stopped");
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private void runServer() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();

                Thread clientThread = new Thread(
                        () -> handleClient(socket),
                        "LocalSkinServer-Client"
                );

                clientThread.setDaemon(true);
                clientThread.start();

            } catch (IOException e) {
                if (running) {
                    Log.w(TAG, "Server accept failed", e);
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (
                Socket client = socket;
                InputStream input = client.getInputStream();
                OutputStream output = client.getOutputStream()
        ) {

            String request = readRequest(input);

            if (request == null || request.isEmpty()) {
                return;
            }

            String firstLine = request.split("\r\n", 2)[0];

            String[] parts = firstLine.split(" ");

            if (parts.length < 2) {
                send404(output);
                return;
            }

            String method = parts[0];
            String path = parts[1];

            if (!"GET".equalsIgnoreCase(method)) {
                send405(output);
                return;
            }

            if ("/skin.png".equals(path)
                    || "/textures/skin.png".equals(path)) {

                serveSkin(output);
                return;
            }

            String profilePrefix =
                    "/sessionserver/session/minecraft/profile/";

            if (path.startsWith(profilePrefix)) {
                String uuid = path.substring(profilePrefix.length());

                serveProfile(output, uuid);
                return;
            }

            send404(output);

        } catch (Throwable e) {
            Log.w(TAG, "Failed handling local skin request", e);
        }
    }

    private String readRequest(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int previous = -1;
        int current;

        while ((current = input.read()) != -1) {

            buffer.write(current);

            if (previous == '\r' && current == '\n') {
                byte[] data = buffer.toByteArray();

                String text =
                        new String(data, StandardCharsets.UTF_8);

                if (text.contains("\r\n\r\n")) {
                    return text;
                }
            }

            previous = current;

            // Prevent an abnormally large request.
            if (buffer.size() > 8192) {
                return null;
            }
        }

        return new String(
                buffer.toByteArray(),
                StandardCharsets.UTF_8
        );
    }

    private void serveSkin(OutputStream output) throws IOException {

        java.io.File skin =
                LocalSkinManager.getLocalSkin();

        if (skin == null || !skin.exists()) {
            send404(output);
            return;
        }

        byte[] bytes =
                readFile(skin);

        sendBytes(
                output,
                "image/png",
                bytes
        );
    }

    private void serveProfile(
            OutputStream output,
            String requestedUuid
    ) throws IOException {

        String uuid = account.profileId;

        if (uuid == null || uuid.isEmpty()) {
            uuid = requestedUuid;
        }

        uuid = uuid.replace("-", "");

        String skinUrl =
                getBaseUrl() + "/textures/skin.png";

        /*
         * Minecraft expects the textures property to contain
         * Base64 encoded JSON.
         */
        String texturesJson =
                "{"
                        + "\"timestamp\":" + System.currentTimeMillis() + ","
                        + "\"profileId\":\"" + uuid + "\","
                        + "\"profileName\":\""
                        + escapeJson(account.username)
                        + "\","
                        + "\"textures\":{"
                        + "\"SKIN\":{"
                        + "\"url\":\"" + skinUrl + "\""
                        + "}"
                        + "}"
                        + "}";

        String encodedTextures =
                Base64.encodeToString(
                        texturesJson.getBytes(StandardCharsets.UTF_8),
                        Base64.NO_WRAP
                );

        String response =
                "{"
                        + "\"id\":\"" + uuid + "\","
                        + "\"name\":\""
                        + escapeJson(account.username)
                        + "\","
                        + "\"properties\":[{"
                        + "\"name\":\"textures\","
                        + "\"value\":\""
                        + encodedTextures
                        + "\""
                        + "}]"
                        + "}";

        sendBytes(
                output,
                "application/json",
                response.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static byte[] readFile(
            java.io.File file
    ) throws IOException {

        try (InputStream input =
                     new java.io.FileInputStream(file)) {

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            byte[] buffer = new byte[8192];

            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            return output.toByteArray();
        }
    }

    private static void sendBytes(
            OutputStream output,
            String contentType,
            byte[] data
    ) throws IOException {

        String header =
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: " + contentType + "\r\n"
                        + "Content-Length: " + data.length + "\r\n"
                        + "Connection: close\r\n"
                        + "Cache-Control: no-cache\r\n"
                        + "\r\n";

        output.write(
                header.getBytes(StandardCharsets.UTF_8)
        );

        output.write(data);
        output.flush();
    }

    private static void send404(
            OutputStream output
    ) throws IOException {

        byte[] data =
                "Not Found".getBytes(StandardCharsets.UTF_8);

        sendError(
                output,
                "404 Not Found",
                data
        );
    }

    private static void send405(
            OutputStream output
    ) throws IOException {

        byte[] data =
                "Method Not Allowed".getBytes(StandardCharsets.UTF_8);

        sendError(
                output,
                "405 Method Not Allowed",
                data
        );
    }

    private static void sendError(
            OutputStream output,
            String status,
            byte[] data
    ) throws IOException {

        String header =
                "HTTP/1.1 " + status + "\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Content-Length: " + data.length + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n";

        output.write(
                header.getBytes(StandardCharsets.UTF_8)
        );

        output.write(data);
        output.flush();
    }
    }
