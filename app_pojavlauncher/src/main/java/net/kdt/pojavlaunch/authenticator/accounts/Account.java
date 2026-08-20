package net.kdt.pojavlaunch.authenticator.accounts;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.Keep;

import com.google.gson.JsonParseException;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
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

    protected Account() {
    }

    public void updateSkinFace() {
        /*
         * Local accounts use the PNG from the launcher skin directory.
         */
        if (authType == AuthType.LOCAL) {
            updateLocalSkinFace();
            return;
        }

        String skinFaceUrlTemplate = authType.skinUrl;
        if (skinFaceUrlTemplate == null) {
            return;
        }

        String skinFaceUrl = String.format(skinFaceUrlTemplate, username);

        try {
            Log.i("SkinLoader", "Updating skin face...");

            File skinFile = getSkinFaceFile();

            /*
             * Streaming directly can fail on some Android devices,
             * so download the complete response first.
             */
            byte[] skinBytes = IOUtils.toByteArray(new URL(skinFaceUrl));

            Bitmap skinBitmap =
                    BitmapFactory.decodeByteArray(
                            skinBytes,
                            0,
                            skinBytes.length
                    );

            if (skinBitmap == null) {
                Log.w("SkinLoader", "Could not decode downloaded skin");
                return;
            }

            Bitmap skinFace =
                    new SkinHeadRenderer().render(100, skinBitmap);

            skinBitmap.recycle();

            if (skinFace == null) {
                Log.w("SkinLoader", "Could not render skin face");
                return;
            }

            FileUtils.ensureParentDirectory(skinFile);

            try (FileOutputStream fileOutputStream =
                         new FileOutputStream(skinFile)) {

                skinFace.compress(
                        Bitmap.CompressFormat.WEBP,
                        90,
                        fileOutputStream
                );
            }

            skinFace.recycle();

            /*
             * Clear the old cached bitmap so the next call loads
             * the newly generated face.
             */
            if (mFaceCache != null) {
                mFaceCache.recycle();
                mFaceCache = null;
            }

            Log.i("SkinLoader", "Update skin face success");

        } catch (IOException | RuntimeException e) {
            /*
             * Network failure, skin refresh limit, invalid image,
             * etc. should not crash the launcher.
             */
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
                    BitmapFactory.decodeFile(
                            localSkin.getAbsolutePath()
                    );

            if (skinBitmap == null) {
                Log.w("SkinLoader", "Could not decode local skin");
                return;
            }

            Bitmap skinFace =
                    new SkinHeadRenderer().render(100, skinBitmap);

            skinBitmap.recycle();

            if (skinFace == null) {
                Log.w("SkinLoader", "Could not render local skin face");
                return;
            }

            File skinFile = getSkinFaceFile();
            FileUtils.ensureParentDirectory(skinFile);

            try (FileOutputStream fileOutputStream =
                         new FileOutputStream(skinFile)) {

                skinFace.compress(
                        Bitmap.CompressFormat.WEBP,
                        90,
                        fileOutputStream
                );
            }

            skinFace.recycle();

            if (mFaceCache != null) {
                mFaceCache.recycle();
                mFaceCache = null;
            }

            Log.i("SkinLoader", "Local skin face updated");

        } catch (Exception e) {
            Log.w(
                    "SkinLoader",
                    "Could not update local skin face",
                    e
            );
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
                    JSONUtils.readFromFile(
                            mSaveLocation,
                            Account.class
                    );

            if (account == null) {
                return null;
            }

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

        if (profilePart == null || profilePart.isEmpty()) {
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
