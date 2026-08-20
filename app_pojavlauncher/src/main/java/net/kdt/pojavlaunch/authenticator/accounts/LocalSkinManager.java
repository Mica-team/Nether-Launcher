package net.kdt.pojavlaunch.authenticator.accounts;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public final class LocalSkinManager {

    private LocalSkinManager() {
    }

    /**
     * Returns the first PNG skin from:
     *
     * <launcher storage>/skin/
     *
     * Only files whose name ends with ".png" are accepted.
     */
    public static File getLocalSkin() {
        File skinDirectory = new File(Tools.DIR_GAME_HOME, "skin");

        if (!skinDirectory.exists() || !skinDirectory.isDirectory()) {
            return null;
        }

        File[] skins = skinDirectory.listFiles(file ->
                file.isFile() &&
                file.getName().endsWith(".png")
        );

        if (skins == null || skins.length == 0) {
            return null;
        }

        // Always choose the first skin in alphabetical order.
        Arrays.sort(skins, Comparator.comparing(File::getName));

        return skins[0];
    }

    /**
     * Returns true when a valid local PNG skin exists.
     */
    public static boolean hasLocalSkin() {
        return getLocalSkin() != null;
    }
}
