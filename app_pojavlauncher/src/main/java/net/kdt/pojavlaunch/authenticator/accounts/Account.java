package net.kdt.pojavlaunch.authenticator.accounts;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.Keep;

import com.google.gson.JsonParseException;

import net.kdt.pojavlaunch.SkinHeadRenderer;
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

    protected Account() {}

    /**
     * Updates the cached face for online accounts.
     *
     * Local accounts use the skin stored in:
     *
     * .minecraft/skins/
     *
     * The first valid .png file is used.
     */
    public void updateSkinFace() {
        if (isLocal()) {
            loadLocalSkinFace();
            return;
        }

        String skinFaceUrlTemplate = authType.skinUrl;

        if (skinFaceUrlTemplate == null) {
            return;
        }

        String skinFaceUrl = String.format(skinFaceUrlTemplate, username);

        try {
            Log.i("SkinLoader", "Updating online skin face...");

            File skinFile = getSkinFaceFile();

            byte[] skinBytes = IOUtils.toByteArray(new URL(skinFaceUrl));

            Bitmap skinBitmap = BitmapFactory.decodeByteArray(
                    skinBytes,
                    0,
                    skinBytes.length
            );

            if (skinBitmap == null) {
                return;
            }

            Bitmap skinFace = new SkinHeadRenderer().render(
                    100,
                    skinBitmap
            );

            skinBitmap.recycle();

            if (skinFace == null) {
                return;
            }

            try (FileOutputStream fileOutputStream =
                         new FileOutputStream(skinFile)) {

                skinFace.compress(
                        Bitmap.CompressFormat.WEBP,
                        90,
                        fileOutputStream
                );
            }

            skinFace.recycle();

            mFaceCache = null;

            Log.i("SkinLoader", "Online skin face updated successfully");

        } catch (IOException e) {
            Log.w(
                    "SkinLoader",
                    "Could not update online skin face",
                    e
            );
        }
    }

    /**
     * Returns true when this is a local account.
     */
    public boolean isLocal() {
        return accessToken == null || accessToken.equals("0");
    }

    /**
     * Loads the first PNG skin from:
     *
     * .minecraft/skins/
     *
     * Any PNG filename is accepted.
     *
     * Examples:
     *
     * skins/skin.png
     * skins/mycat.png
     * skins/abc123.png
     */
    private void loadLocalSkinFace() {
        File skinFile = findLocalSkin();

        if (skinFile == null) {
            Log.i(
                    "SkinLoader",
                    "No local PNG skin found"
            );
            return;
        }

        try {
            Log.i(
                    "SkinLoader",
                    "Loading local skin: "
                            + skinFile.getAbsolutePath()
            );

            Bitmap skinBitmap = BitmapFactory.decodeFile(
                    skinFile.getAbsolutePath()
            );

            if (skinBitmap == null) {
                Log.w(
                        "SkinLoader",
                        "Failed to decode local skin: "
                                + skinFile.getName()
                );
                return;
            }

            Bitmap skinFace = new SkinHeadRenderer().render(
                    100,
                    skinBitmap
            );

            skinBitmap.recycle();

            if (skinFace == null) {
                Log.w(
                        "SkinLoader",
                        "Failed to render local skin face"
                );
                return;
            }

            File cacheFile = getSkinFaceFile();

            try (FileOutputStream output =
                         new FileOutputStream(cacheFile)) {

                skinFace.compress(
                        Bitmap.CompressFormat.WEBP,
                        90,
                        output
                );
            }

            mFaceCache = skinFace;

            Log.i(
                    "SkinLoader",
                    "Local skin loaded successfully"
            );

        } catch (Exception e) {
            Log.w(
                    "SkinLoader",
                    "Could not load local skin",
                    e
            );
        }
    }

    /**
     * Finds the first valid PNG file in:
     *
     * .minecraft/skins/
     *
     * Only files whose name ends exactly with ".png"
     * are accepted.
     */
    private File findLocalSkin() {
        File skinsDirectory = new File(
                Tools.DIR_GAME_NEW,
                "skins"
        );

        if (!skinsDirectory.exists()) {
            if (!skinsDirectory.mkdirs()) {
                Log.w(
                        "SkinLoader",
                        "Could not create local skins directory"
                );
            }
        }

        if (!skinsDirectory.isDirectory()) {
            return null;
        }

        File[] files = skinsDirectory.listFiles(
                file -> file.isFile()
                        && file.getName().endsWith(".png")
        );

        if (files == null || files.length == 0) {
            return null;
        }

        /*
         * The user requested that the first PNG be selected.
         */
        return files[0];
    }

    /**
     * Saves the account JSON.
     */
    public void save() throws IOException {
        FileUtils.ensureParentDirectory(mSaveLocation);
        JSONUtils.writeToFile(mSaveLocation, this);
    }

    /**
     * Reloads the account from disk.
     */
    public Account reload() {
        try {
            Account account = JSONUtils.readFromFile(
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

    /**
     * Gets the cached skin face.
     *
     * For local accounts, the first PNG inside
     * .minecraft/skins/ is loaded.
     *
     * For online accounts, the existing cached face is used.
     */
    public Bitmap getSkinFace() {

        /*
         * Local account:
         * check the local skin every time the face is requested.
         *
         * This means replacing the PNG doesn't require
         * recreating the account.
         */
        if (isLocal()) {
            File localSkin = findLocalSkin();

            if (localSkin == null) {
                return null;
            }

            if (mFaceCache == null) {
                loadLocalSkinFace();
            }

            return mFaceCache;
        }

        /*
         * Online account.
         */
        if (mFaceCache != null) {
            return mFaceCache;
        }

        File skinFaceFile = getSkinFaceFile();

        if (!skinFaceFile.exists()) {
            return null;
        }

        mFaceCache = BitmapFactory.decodeFile(
                skinFaceFile.getAbsolutePath()
        );

        return mFaceCache;
    }

    /**
     * Location of the cached rendered face.
     */
    private File getSkinFaceFile() {
        return new File(
                Tools.DIR_CACHE,
                "skin-face-"
                        + profileId
                        + "-"
                        + authType.name()
                        + ".webp"
        );
    }
    }
