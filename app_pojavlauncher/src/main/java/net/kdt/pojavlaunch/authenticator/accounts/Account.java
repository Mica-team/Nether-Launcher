package net.kdt.pojavlaunch.authenticator.accounts;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.Keep;

import com.google.gson.JsonParseException;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;

@Keep
public class Account {
    public transient File mSaveLocation;

    public String accessToken = "0";
    public String profileId = "00000000-0000-0000-0000-000000000000";
    public String username = "Steve";
    public AuthType authType = AuthType.LOCAL;
    public boolean isMicrosoft = false;
    public String refreshToken = "0";
    public String xuid;
    public long expiresAt;

    private transient Bitmap mFaceCache;

    protected Account() {}

    public void updateSkinFace() {
        /*
         * Local accounts use the PNG from the launcher skin directory.
         */
        if (authType == AuthType.LOCAL) {
            updateLocalSkinFace();
            return;
        }

        String skinFaceUrlTemplate = authType.skinUrl;
        if (skinFaceUrlTemplate == null) return;

        String skinFaceUrl = String.format(skinFaceUrlTemplate, username);

        try {
            Log.i("SkinLoader", "Updating skin face...");

            File skinFile = getSkinFaceFile();

            // Streaming it directly breaks on some devices.
            byte[] skinBytes = IOUtils.toByteArray(new URL(skinFaceUrl));
            Bitmap skinBitmap =
                    BitmapFactory.decodeByteArray(skinBytes, 0, skinBytes.length);

            if (skinBitmap == null) return;

            Bitmap skinFace = new SkinHeadRenderer().render(100, skinBitmap);
            skinBitmap.recycle();

            if (skinFace == null) return;

            try (FileOutputStream fileOutputStream =
                         new FileOutputStream(skinFile)) {
                skinFace.compress(
                        Bitmap.CompressFormat.WEBP,
                        90,
                        fileOutputStream
                );
            }

            Log.i("SkinLoader", "Update skin face success");

        } catch (IOException e) {
            // Skin refresh limit, no internet connection, etc.
            Log.w("SkinLoader", "Could not update skin face", e);
        }
    }

    /**
     * Creates the cached face for a local PNG skin.
     */
    private void updateLocalSkinFace() {
        File localSkin = LocalSkinManager.getLocalSkin();

        if (localSkin == null) {
            Log.i("SkinLoader", "No local PNG skin found");
            return;
        }

        try {
            Log.i(
                    "SkinLoader",
                    "Loading local skin: " + localSkin.getName()
            );

            Bitmap skinBitmap =
                    BitmapFactory.decodeFile(localSkin.getAbsolutePath());

            if (skinBitmap == null) {
                Log.w("SkinLoader", "Could not decode local skin");
                return;
            }

            Bitmap skinFace =
                    new SkinHeadRenderer().render(100, skinBitmap);

            skinBitmap.recycle();

            if (skinFace == null) return;

            File skinFile = getSkinFaceFile();

            try (FileOutputStream fileOutputStream =
                         new FileOutputStream(skinFile)) {

                skinFace.compress(
                        Bitmap.CompressFormat.WEBP,
                        90,
                        fileOutputStream
                );
            }

            Log.i("SkinLoader", "Local skin face updated");

        } catch (Exception e) {
            Log.w("SkinLoader", "Could not update local skin face", e);
        }
    }

    public boolean isLocal() {
        return accessToken.equals("0");
    }

    public void save() throws IOException {
        FileUtils.ensureParentDirectory(mSaveLocation);
        JSONUtils.writeToFile(mSaveLocation, this);
    }

    public Account reload() {
        try {
            Account account =
                    JSONUtils.readFromFile(mSaveLocation, Account.class);

            if (account == null) return null;

            account.mSaveLocation = mSaveLocation;

            return account;

        } catch (IOException | JsonParseException e) {
            return null;
        }
    }

    public Bitmap getSkinFace() {
        if (authType == AuthType.LOCAL) {
            File localSkin = LocalSkinManager.getLocalSkin();

            if (localSkin == null) {
                return null;
            }
        }

        File skinFaceFile = getSkinFaceFile();

        /*
         * Generate the cached face if it doesn't exist.
         */
        if (!skinFaceFile.exists()) {
            updateSkinFace();
        }

        if (!skinFaceFile.exists()) {
            return null;
        }

        if (mFaceCache == null) {
            mFaceCache =
                    BitmapFactory.decodeFile(
                            skinFaceFile.getAbsolutePath()
                    );
        }

        return mFaceCache;
    }

    private File getSkinFaceFile() {
        String profilePart = profileId;

        if (profilePart == null) {
            profilePart = "local";
        }

        return new File(
                Tools.DIR_CACHE,
                "skin-face-" +
                        profilePart +
                        "-" +
                        authType.name() +
                        ".webp"
        );
    }
                }
