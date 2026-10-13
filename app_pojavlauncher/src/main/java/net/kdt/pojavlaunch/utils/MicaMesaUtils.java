package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Runtime support for the standalone Mica Mesa bundle.
 *
 * This intentionally does not discover, load, or modify the optional git.mojo.zink plugin.
 */
public final class MicaMesaUtils {
    private static final String TAG = "MicaMesa";
    private static final String ASSET_ROOT = "components/mica_mesa";
    private static File installedRoot;

    private MicaMesaUtils() {}

    public static synchronized void initEnvironment(Context context, Map<String, String> envMap) {
        File root = install(context.getApplicationContext());
        File egl = findFile(root, "libEGL_mesa.so");
        File zinkDriver = findFile(root, "zink_dri.so");
        if (egl == null || zinkDriver == null) {
            throw new IllegalStateException("Mica Mesa bundle is missing libEGL_mesa.so or zink_dri.so");
        }

        File libDir = new File(root, "lib");
        envMap.put("GALLIUM_DRIVER", "zink");
        envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        envMap.put("LIBGL_DRIVERS_PATH", zinkDriver.getParentFile().getAbsolutePath());
        envMap.put("LD_LIBRARY_PATH", libDir.getAbsolutePath() + ":" +
                zinkDriver.getParentFile().getAbsolutePath() + ":" + Tools.NATIVE_LIB_DIR);
        envMap.put("MESA_LOADER_DRIVER_PATH", zinkDriver.getParentFile().getAbsolutePath());
    }

    public static synchronized String getEglPath() {
        if (installedRoot == null) return "libEGL_mesa.so";
        File egl = findFile(installedRoot, "libEGL_mesa.so");
        return egl == null ? "libEGL_mesa.so" : egl.getAbsolutePath();
    }

    public static synchronized String getLibraryPath() {
        if (installedRoot == null) return null;
        File libDir = new File(installedRoot, "lib");
        File driver = findFile(installedRoot, "zink_dri.so");
        if (driver == null) return libDir.getAbsolutePath();
        return libDir.getAbsolutePath() + ":" + driver.getParentFile().getAbsolutePath();
    }

    private static File install(Context context) {
        File root = new File(context.getFilesDir(), "mica-mesa-zink");
        File egl = findFile(root, "libEGL_mesa.so");
        File zink = findFile(root, "zink_dri.so");
        if (egl != null && zink != null) {
            installedRoot = root;
            return root;
        }

        deleteRecursively(root);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("Cannot create Mica Mesa directory: " + root);
        }
        try {
            copyAssetTree(context.getAssets(), ASSET_ROOT, root);
        } catch (IOException e) {
            deleteRecursively(root);
            throw new IllegalStateException("Could not install bundled Mica Mesa libraries", e);
        }
        egl = findFile(root, "libEGL_mesa.so");
        zink = findFile(root, "zink_dri.so");
        if (egl == null || zink == null) {
            deleteRecursively(root);
            throw new IllegalStateException("Mica Mesa assets did not contain the required EGL and Zink libraries");
        }
        installedRoot = root;
        Log.i(TAG, "Installed standalone Mesa bundle to " + root);
        return root;
    }

    private static void copyAssetTree(AssetManager assets, String assetPath, File target) throws IOException {
        String[] children = assets.list(assetPath);
        if (children != null && children.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) {
                throw new IOException("Cannot create directory " + target);
            }
            for (String child : children) {
                copyAssetTree(assets, assetPath + "/" + child, new File(target, child));
            }
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory " + parent);
        }
        try (InputStream in = assets.open(assetPath);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
    }

    private static File findFile(File directory, String name) {
        if (directory == null || !directory.isDirectory()) return null;
        File[] children = directory.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isFile() && name.equals(child.getName())) return child;
            if (child.isDirectory()) {
                File found = findFile(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        // Best effort; install() will fail explicitly if the directory cannot be recreated.
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
