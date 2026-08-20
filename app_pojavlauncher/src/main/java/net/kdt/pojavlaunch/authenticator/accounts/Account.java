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

    /**
     * Updates both:
     *
     * 1. The full skin cache.
     * 2. The small launcher face cache.
     *
     * For local accounts the PNG in:
     *
     * <launcher storage>/skin/
     *
     * is used.
     */
    public void updateSkinFace() {

        if (authType == AuthType.LOCAL) {
            updateLocalSkin();
            return;
        }

        String skinUrlTemplate = authType.skinUrl;

        if (skinUrlTemplate == null || username == null) {
            return;
        }

        String skinUrl = String.format(
                skinUrlTemplate,
                username
        );

        try {
            Log.i(
                    "SkinLoader",
                    "Downloading full skin..."
            );

            byte[] skinBytes =
                    IOUtils.toByteArray(
                            new URL(skinUrl)
                    );

            if (skinBytes == null || skinBytes.length == 0) {
                Log.w(
                        "SkinLoader",
                        "Downloaded skin is empty"
                );
                return;
            }

            Bitmap skinBitmap =
                    BitmapFactory.decodeByteArray(
                            skinBytes,
                            0,
                            skinBytes.length
                    );

            if (skinBitmap == null) {
                Log.w(
                        "SkinLoader",
                        "Could not decode downloaded skin"
                );
                return;
            }

            /*
             * Save the COMPLETE skin first.
             *
             * Minecraft needs the complete texture,
             * not only the face.
             */
            saveFullSkin(skinBytes);

            /*
             * Generate the launcher face.
             */
            Bitmap skinFace =
                    new SkinHeadRenderer().render(
                            100,
                            skinBitmap
                    );

            skinBitmap.recycle();

            if (skinFace == null) {
                Log.w(
                        "SkinLoader",
                        "Could not render skin face"
                );
                return;
            }

            File skinFaceFile =
                    getSkinFaceFile();

            FileUtils.ensureParentDirectory(
                    skinFaceFile
            );

            try (
                    FileOutputStream output =
                            new FileOutputStream(
                                    skinFaceFile
                            )
            ) {
                skinFace.compress(
                        Bitmap.CompressFormat.WEBP,
                        90,
                        output
                );
            }

            skinFace.recycle();

            clearFaceCache();

            Log.i(
                    "SkinLoader",
                    "Skin and face cache updated"
            );

        } catch (IOException | RuntimeException e) {

            /*
             * IMPORTANT:
             * Network failure must NEVER prevent
             * an offline/local account from being used.
             */
            Log.w(
                    "SkinLoader",
                    "Could not download skin; using cached skin if available",
                    e
            );
        }
    }

    /**
     * Loads the local PNG skin.
     */
    private void updateLocalSkin() {

        File localSkin =
                LocalSkinManager.getLocalSkin();

        if (localSkin == null) {
            Log.i(
                    "SkinLoader",
                    "No local PNG skin found"
            );
            return;
        }

        try {

            Log.i(
                    "SkinLoader",
                    "Loading local skin: " +
                            localSkin.getAbsolutePath()
            );

            Bitmap skinBitmap =
                    BitmapFactory.decodeFile(
                            localSkin.getAbsolutePath()
                    );

            if (skinBitmap == null) {
                Log.w(
                        "SkinLoader",
                        "Could not decode local skin"
                );
                return;
            }

            /*
             * Copy the original PNG into our
             * stable cache location.
             */
            copyLocalSkinToCache(
                    localSkin
            );

            Bitmap skinFace =
                    new SkinHeadRenderer().render(
                            100,
                            skinBitmap
                    );

            skinBitmap.recycle();

            if (skinFace == null) {
                return;
            }

            File skinFaceFile =
                    getSkinFaceFile();

            FileUtils.ensureParentDirectory(
                    skinFaceFile
            );

            try (
                    FileOutputStream output =
                            new FileOutputStream(
                                    skinFaceFile
                            )
            ) {
                skinFace.compress(
                        Bitmap.CompressFormat.WEBP,
                        90,
                        output
                );
            }

            skinFace.recycle();

            clearFaceCache();

            Log.i(
                    "SkinLoader",
                    "Local skin cache updated"
            );

        } catch (Exception e) {

            Log.w(
                    "SkinLoader",
                    "Could not update local skin",
                    e
            );
        }
    }

    /**
     * Saves the complete downloaded PNG.
     */
    private void saveFullSkin(
            byte[] skinBytes
    ) throws IOException {

        File skinFile =
                getFullSkinFile();

        FileUtils.ensureParentDirectory(
                skinFile
        );

        try (
                FileOutputStream output =
                        new FileOutputStream(
                                skinFile
                        )
        ) {
            output.write(skinBytes);
            output.flush();
        }

        Log.i(
                "SkinLoader",
                "Full skin saved: " +
                        skinFile.getAbsolutePath()
        );
    }

    /**
     * Copies the user's local PNG into
     * the cache used by GameRunner.
     */
    private void copyLocalSkinToCache(
            File source
    ) throws IOException {

        byte[] bytes =
                IOUtils.toByteArray(
                        new java.io.FileInputStream(
                                source
                        )
                );

        saveFullSkin(bytes);
    }

    /**
     * Returns the complete cached Minecraft skin.
     */
    public File getFullSkinFile() {

        String profilePart =
                profileId;

        if (
                profilePart == null ||
                profilePart.isEmpty()
        ) {
            profilePart = "local";
        }

        return new File(
                Tools.DIR_CACHE,
                "skin-" +
                        profilePart +
                        "-" +
                        authType.name() +
                        ".png"
        );
    }

    /**
     * Returns true if a complete skin is already cached.
     */
    public boolean hasCachedSkin() {

        File file =
                getFullSkinFile();

        return file.exists()
                && file.isFile()
                && file.length() > 0;
    }

    /**
     * Makes sure a skin is available.
     *
     * Online accounts try to update first.
     * If that fails, the old cached skin remains usable.
     */
    public void ensureSkinAvailable() {

        try {

            if (authType == AuthType.LOCAL) {

                if (LocalSkinManager.hasLocalSkin()) {
                    updateLocalSkin();
                }

                return;
            }

            /*
             * Try to download a fresh copy.
             *
             * If there is no internet this simply
             * fails safely and the old cache remains.
             */
            updateSkinFace();

        } catch (Throwable e) {

            Log.w(
                    "SkinLoader",
                    "Skin update failed",
                    e
            );
        }
    }

    public boolean isLocal() {
        return accessToken == null
                || accessToken.equals("0");
    }

    public void save()
            throws IOException {

        FileUtils.ensureParentDirectory(
                mSaveLocation
        );

        JSONUtils.writeToFile(
                mSaveLocation,
                this
        );
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

            account.mSaveLocation =
                    mSaveLocation;

            return account;

        } catch (
                IOException |
                JsonParseException e
        ) {
            return null;
        }
    }

    /**
     * Returns the launcher avatar.
     */
    public Bitmap getSkinFace() {

        /*
         * Local accounts need a local skin.
         */
        if (authType == AuthType.LOCAL) {

            File localSkin =
                    LocalSkinManager.getLocalSkin();

            if (localSkin == null) {
                return null;
            }

            /*
             * Make sure the local full skin
             * and face cache exist.
             */
            if (!getFullSkinFile().exists()) {
                updateLocalSkin();
            }
        }

        File skinFaceFile =
                getSkinFaceFile();

        /*
         * Try to create the face if missing.
         */
        if (!skinFaceFile.exists()) {

            updateSkinFace();
        }

        /*
         * If online downloading failed but
         * the full skin exists, generate the
         * face from the cached full skin.
         */
        if (!skinFaceFile.exists()) {

            generateFaceFromCachedSkin();
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

    /**
     * Generates the launcher face from the
     * cached complete skin.
     */
    private void generateFaceFromCachedSkin() {

        File fullSkin =
                getFullSkinFile();

        if (
                !fullSkin.exists() ||
                fullSkin.length() == 0
        ) {
            return;
        }

        try {

            Bitmap bitmap =
                    BitmapFactory.decodeFile(
                            fullSkin.getAbsolutePath()
                    );

            if (bitmap == null) {
                return;
            }

            Bitmap face =
                    new SkinHeadRenderer().render(
                            100,
                            bitmap
                    );

            bitmap.recycle();

            if (face == null) {
                return;
            }

            File faceFile =
                    getSkinFaceFile();

            FileUtils.ensureParentDirectory(
                    faceFile
            );

            try (
                    FileOutputStream output =
                            new FileOutputStream(
                                    faceFile
                            )
            ) {
                face.compress(
                        Bitmap.CompressFormat.WEBP,
                        90,
                        output
                );
            }

            face.recycle();

        } catch (Exception e) {

            Log.w(
                    "SkinLoader",
                    "Could not generate cached face",
                    e
            );
        }
    }

    private void clearFaceCache() {

        if (mFaceCache != null) {

            mFaceCache.recycle();
            mFaceCache = null;
        }
    }

    private File getSkinFaceFile() {

        String profilePart =
                profileId;

        if (
                profilePart == null ||
                profilePart.isEmpty()
        ) {
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
