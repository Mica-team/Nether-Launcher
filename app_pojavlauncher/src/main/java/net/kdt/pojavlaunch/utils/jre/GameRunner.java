package net.kdt.pojavlaunch.utils.jre;

import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.JVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.lifecycle.LifecycleAwareAlertDialog;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DateUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.GameOptionsUtils;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.utils.OldVersionsUtils;
import net.kdt.pojavlaunch.utils.RendererCompatUtil;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import git.artdeell.mojo.R;

public class GameRunner {

    /**
     * Checks whether Sodium or a Sodium-based renderer mod
     * is installed.
     */
    private static boolean hasSodium(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;
        for(File file : mods) {
            String name = file.getName();
            if(name.contains("sodium") ||
                    name.contains("embeddium") ||
                    name.contains("rubidium")) return true;
        }
        return false;
    }

    /**
     * Checks whether Angelica is installed.
     */
    private static boolean hasAngelica(File gameDir) {

        File modsDir =
                new File(gameDir, "mods");

        File[] mods =
                modsDir.listFiles(
                        file ->
                                file.isFile()
                                        && file.getName()
                                        .endsWith(".jar")
                );

        if (mods == null) {
            return false;
        }

        for (File file : mods) {

            String name =
                    file.getName().toLowerCase();

            if (name.contains("angelica")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks the Adreno render-distance issue.
     */
    private static boolean affectedByRenderDistanceIssue(
            JVersionList.Version version)
            throws ParseException {

        if (LauncherPreferences.PREF_USE_ANGLE) {
            return false;
        }

        GLInfoUtils.GLInfo info =
                GLInfoUtils.getGlInfo();

        return info.isAdreno()
                && info.glesMajorVersion >= 3
                && DateUtils.dateBefore(
                        DateUtils.getOriginalReleaseDate(version),
                        2025,
                        2,
                        25
                );
    }

    private static boolean checkRenderDistance(
            JVersionList.Version version,
            File gamedir)
            throws ParseException {

        if (!affectedByRenderDistanceIssue(version)) {
            return false;
        }

        if (hasSodium(gamedir)) {
            return false;
        }

        try {
            MCOptionUtils.load();
        } catch (Exception e) {
            Log.e(
                    "GameRunner",
                    "Failed to load config",
                    e
            );
        }

        int renderDistance =
                GameOptionsUtils.parseIntDefault(
                        MCOptionUtils.get("renderDistance"),
                        12
                );

        return renderDistance > 7;
    }

    private static boolean isGl4esCompatible(
            JVersionList.Version version)
            throws Exception {

        return DateUtils.dateBefore(
                DateUtils.getOriginalReleaseDate(version),
                2025,
                1,
                7
        );
    }

    private static boolean isCompatContext(
            JVersionList.Version version)
            throws Exception {

        return DateUtils.dateBefore(
                DateUtils.getOriginalReleaseDate(version),
                2021,
                3,
                9
        );
    }

    private static boolean showDialog(
            AppCompatActivity activity,
            int message)
            throws InterruptedException {

        LifecycleAwareAlertDialog.DialogCreator dialogCreator =
                (alertDialog, dialogBuilder) ->
                        dialogBuilder
                                .setMessage(
                                        activity.getString(message)
                                )
                                .setCancelable(false)
                                .setPositiveButton(
                                        android.R.string.ok,
                                        (d, w) -> {
                                        }
                                );

        return LifecycleAwareAlertDialog.haltOnDialog(
                activity.getLifecycle(),
                activity,
                dialogCreator
        );
    }

    /**
     * Automatically switches to LTW when required.
     */
    private static String switchLtw(
            boolean hasLtw,
            Instance instance,
            AppCompatActivity activity,
            int resId)
            throws InterruptedException, IOException {

        if (hasLtw) {

            String ltwRenderer =
                    "opengles3_ltw";

            instance.renderer =
                    ltwRenderer;

            instance.write();

            return ltwRenderer;
        }

        showDialog(
                activity,
                resId
        );

        System.exit(0);

        return null;
    }

    /**
     * Main game launcher.
     *
     * IMPORTANT:
     *
     * The Account is supplied by GameActivity.
     *
     * This method does NOT use Accounts.getCurrent().
     *
     * Therefore it can work with an already-loaded local/offline
     * Account without needing Accounts.getCurrent().
     */
    public static void launchGame(
            final AppCompatActivity activity,
            Account account,
            Instance instance,
            String versionId,
            File[] classpath,
            String rendererName)
            throws Throwable {

        /*
         * Make sure an Account object always exists.
         *
         * This is only a fallback. Normally GameActivity should
         * provide the selected Account.
         */
        if (account == null) {

            Log.w(
                    "GameRunner",
                    "Account is null; creating offline account"
            );

            account =
                    createOfflineAccount();
        }

        /*
         * Make sure basic offline values are usable.
         */
        prepareOfflineAccount(account);

        int freeDeviceMemory =
                Tools.getFreeDeviceMemory(activity);

        int localeString;

        int freeAddressSpace =
                Architecture.is32BitsDevice()
                        ? Tools.getMaxContinuousAddressSpaceSize()
                        : -1;

        Log.i(
                "MemStat",
                "Free RAM: "
                        + freeDeviceMemory
                        + " Addressable: "
                        + freeAddressSpace
        );

        if (freeDeviceMemory > freeAddressSpace
                && freeAddressSpace != -1) {

            freeDeviceMemory =
                    freeAddressSpace;

            localeString =
                    R.string.address_memory_warning_msg;

        } else {

            localeString =
                    R.string.memory_warning_msg;
        }

        if (LauncherPreferences.PREF_RAM_ALLOCATION
                > freeDeviceMemory) {

            int finalDeviceMemory =
                    freeDeviceMemory;

            LifecycleAwareAlertDialog.DialogCreator
                    dialogCreator =
                    (dialog, builder) ->
                            builder.setMessage(
                                            activity.getString(
                                                    localeString,
                                                    finalDeviceMemory,
                                                    LauncherPreferences
                                                            .PREF_RAM_ALLOCATION
                                            )
                                    )
                                    .setPositiveButton(
                                            android.R.string.ok,
                                            (d, w) -> {
                                            }
                                    );

            if (LifecycleAwareAlertDialog
                    .haltOnDialog(
                            activity.getLifecycle(),
                            activity,
                            dialogCreator
                    )) {

                return;
            }
        }

        File gamedir =
                instance.getGameDirectory();

        JVersionList.Version versionInfo =
                Tools.getVersionInfo(versionId);

        /*
         * Switch renderer for old compatibility contexts.
         */
        if (isCompatContext(versionInfo)
                && !hasAngelica(gamedir)
                && rendererName.equals("opengles3_ltw")) {

            instance.renderer =
                    rendererName =
                            "opengles2";

            instance.write();
        }

        boolean isGl4es =
                rendererName.equals("opengles2");

        boolean ltwSupported =
                RendererCompatUtil
                        .getCompatibleRenderers(activity)
                        .rendererIds
                        .contains("opengles3_ltw");

        /*
         * Sodium + GL4ES is not supported for modern versions.
         */
        if (!isCompatContext(versionInfo)
                && isGl4es
                && hasSodium(gamedir)) {

            rendererName =
                    switchLtw(
                            ltwSupported,
                            instance,
                            activity,
                            R.string.compat_sodium_not_supported
                    );
        }

        /*
         * GL4ES is not supported for newer versions.
         */
        if (!isGl4esCompatible(versionInfo)
                && isGl4es) {

            rendererName =
                    switchLtw(
                            ltwSupported,
                            instance,
                            activity,
                            R.string.compat_version_not_supported
                    );
        }

        RendererCompatUtil
                .releaseRenderersCache();

        boolean isLtw =
                rendererName.equals("opengles3_ltw");

        if (isLtw
                && checkRenderDistance(
                versionInfo,
                gamedir
        )) {

            if (showDialog(
                    activity,
                    R.string.ltw_render_distance_warning_msg
            )) {

                return;
            }

            try {

                MCOptionUtils.set(
                        "renderDistance",
                        "7"
                );

                MCOptionUtils.save();

            } catch (Exception e) {

                Log.e(
                        "GameRunner",
                        "Failed to fix render distance setting",
                        e
                );
            }
        }

        GameOptionsUtils.fixOptions(isLtw);

        if (isLtw
                && GLInfoUtils
                .getGlInfo()
                .forcedMsaa) {

            if (showDialog(
                    activity,
                    R.string.ltw_4x_msaa_warning_msg
            )) {

                return;
            }
        }

        int requiredJavaVersion = 8;

        if (versionInfo.javaVersion != null) {

            requiredJavaVersion =
                    versionInfo
                            .javaVersion
                            .majorVersion;
        }

        Runtime runtime =
                MultiRTUtils.forceReread(
                        pickRuntime(
                                instance,
                                requiredJavaVersion
                        )
                );

        /*
         * Pre-process files.
         */
        disableSplash(gamedir);

        /*
         * Generate Minecraft arguments using the Account
         * already passed into this method.
         *
         * No Accounts.getCurrent().
         */
        List<String> launchArgs =
                getMoJsonClientArgs(
                        account,
                        versionInfo,
                        gamedir
                );

        OldVersionsUtils
                .selectOpenGlVersion(versionInfo);

        ArrayList<String> launchClassPath =
                new ArrayList<>(
                        classpath.length
                );

        for (File classpathEntry : classpath) {

            String entryPath =
                    classpathEntry.getAbsolutePath();

            if (!classpathEntry.exists()) {

                Log.w(
                        "GameRunner",
                        "Skipped classpath entry "
                                + entryPath
                                + " because it is missing"
                );
            }

            launchClassPath.add(
                    entryPath
            );
        }

        launchClassPath.trimToSize();

        List<String> javaArgList =
                new ArrayList<>();

        /*
         * Log4j configuration.
         */
        if (versionInfo.logging != null
                && versionInfo.logging.client != null
                && versionInfo.logging.client.file != null) {

            String configFile =
                    Tools.DIR_DATA
                            + "/security/"
                            + versionInfo.logging.client
                            .file.id
                            .replace(
                                    "client",
                                    "log4j-rce-patch"
                            );

            if (!new File(configFile).exists()) {

                configFile =
                        Tools.DIR_GAME_NEW
                                + "/"
                                + versionInfo.logging
                                .client
                                .file.id;
            }

            javaArgList.add(
                    "-Dlog4j.configurationFile="
                            + configFile
            );
        }

        /*
         * Version-specific natives.
         */
        File versionSpecificNativesDir =
                new File(
                        Tools.DIR_CACHE,
                        "natives/" + versionId
                );

        if (versionSpecificNativesDir.exists()) {

            String dirPath =
                    versionSpecificNativesDir
                            .getAbsolutePath();

            javaArgList.add(
                    "-Djava.library.path="
                            + dirPath
                            + ":"
                            + Tools.NATIVE_LIB_DIR
            );

            javaArgList.add(
                    "-Djna.boot.library.path="
                            + dirPath
            );
        }

        /*
         * LWJGL native extraction.
         */
        File lwjglExtractDir =
                new File(
                        Tools.DIR_CACHE,
                        "lwjgl_native/" + versionId
                );

        FileUtils.ensureDirectory(
                lwjglExtractDir
        );

        javaArgList.add(
                "-Dorg.lwjgl.system.SharedLibraryExtractPath="
                        + lwjglExtractDir.getAbsolutePath()
        );

        /*
         * Only add authlib-injector when appropriate.
         *
         * Local/offline accounts do not require it.
         */
        addAuthlibInjectorArgs(
                javaArgList,
                account
        );

        /*
         * Version JVM arguments.
         */
        javaArgList.addAll(
                getMoJsonJvmArgs(versionId)
        );

        /*
         * User JVM arguments.
         */
        javaArgList.addAll(
                JREUtils.parseJavaArguments(
                        instance.getLaunchArgs()
                )
        );

        JREUtils.setEnviroimentForGame(
                activity,
                rendererName
        );

        JREUtils.chdir(
                instance
                        .getGameDirectory()
                        .getAbsolutePath()
        );

        /*
         * Load renderer.
         */
        String rendererLibrary =
                JREUtils.loadGraphicsLibrary(
                        rendererName
                );

        if (rendererLibrary == null) {

            Log.i(
                    "GameRunner",
                    "Falling back to GL4ES 1.1.4"
            );

            rendererName =
                    "opengles2";

            rendererLibrary =
                    JREUtils.loadGraphicsLibrary(
                            rendererName
                    );
        }

        if (rendererLibrary == null) {

            if (showDialog(
                    activity,
                    R.string.gr_err_renderer_load_Failed
            )) {

                return;
            }

            System.exit(0);
        }

        javaArgList.add(
                "-Dorg.lwjgl.opengl.libname=libGLMojo.so"
        );

        javaArgList.add(
                "-Dorg.lwjgl.freetype.libname="
                        + Tools.NATIVE_LIB_DIR
                        + "/libfreetype.so"
        );

        /*
         * RAM information.
         */
        activity.runOnUiThread(
                () ->
                        Toast.makeText(
                                activity,
                                activity.getString(
                                        R.string.autoram_info_msg,
                                        LauncherPreferences
                                                .PREF_RAM_ALLOCATION
                                ),
                                Toast.LENGTH_SHORT
                        ).show()
        );

        Log.i(
                "GameRunner",
                "Running with "
                        + launchArgs
        );

        try {

            JavaRunner.nativeSetupExit(
                    activity
            );

            JavaRunner.startJvm(
                    runtime,
                    javaArgList,
                    launchClassPath,
                    versionInfo.mainClass,
                    launchArgs
            );

        } catch (VMLoadException e) {
    LifecycleAwareAlertDialog.DialogCreator dialogCreator =
            (dialog, builder) ->
                    builder.setMessage(e.toString(activity))
                            .setPositiveButton(
                                    android.R.string.ok,
                                    (d, w) -> {}
                            );

    if (LifecycleAwareAlertDialog.haltOnDialog(
            activity.getLifecycle(),
            activity,
            dialogCreator
    )) {
        return;
    }
}

Tools.restartLauncherActivity(activity);
Tools.fullyExit();

}

    private static void disableSplash(File dir) {
