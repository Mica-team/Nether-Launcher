package net.kdt.pojavlaunch.authenticator.accounts;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public final class LocalSkinManager {

    private static final String SKIN_DIRECTORY_NAME = "skin";

    private LocalSkinManager() {
    }

    /**
     * Returns the directory used for local PNG skins.
     *
     * The directory is created automatically if it does not exist.
     */
    public static File getSkinDirectory() {
        File skinDirectory = new File(
                Tools.DIR_GAME_HOME,
                SKIN_DIRECTORY_NAME
        );

        if (!skinDirectory.exists()) {
            if (!skinDirectory.mkdirs()) {
                return null;
            }
        }

        if (!skinDirectory.isDirectory()) {
            return null;
        }

        return skinDirectory;
    }

    /**
     * Returns the first PNG skin from:
     *
     * <launcher storage>/skin/
     *
     * PNG matching is case-insensitive.
     */
    public static File getLocalSkin() {
        File skinDirectory = getSkinDirectory();

        if (skinDirectory == null) {
            return null;
        }

        File[] skins = skinDirectory.listFiles(file ->
                file.isFile()
                        && file.getName().toLowerCase().endsWith(".png")
        );

        if (skins == null || skins.length == 0) {
            return null;
        }

        // Always choose the first skin alphabetically.
        Arrays.sort(
                skins,
                Comparator.comparing(
                        File::getName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        return skins[0];
    }

    /**
     * Returns true when a local PNG skin exists.
     */
    public static boolean hasLocalSkin() {
        return getLocalSkin() != null;
    }
}
