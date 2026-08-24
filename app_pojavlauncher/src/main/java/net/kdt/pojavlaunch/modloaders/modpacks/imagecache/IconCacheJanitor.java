package net.kdt.pojavlaunch.modloaders.modpacks.imagecache;

import android.content.SharedPreferences;
import android.util.Log;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Keeps the mod icon cache under the user-selected size limit.
 */
public class IconCacheJanitor implements Runnable {
    private static final long DEFAULT_CACHE_SIZE_LIMIT = 100L * 1024L * 1024L;
    private static final String PREF_CACHE_LIMIT = "modIconCacheLimit";
    private static final String PREF_CACHE_UNLIMITED = "modIconCacheUnlimited";

    private static Future<?> sJanitorFuture;

    private IconCacheJanitor() {
        // don't allow others to create this
    }

    private static long getCacheSizeLimit() {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        if (preferences == null) return DEFAULT_CACHE_SIZE_LIMIT;
        if (preferences.getBoolean(PREF_CACHE_UNLIMITED, false)) return Long.MAX_VALUE;

        int limitMb = preferences.getInt(PREF_CACHE_LIMIT, 100);
        limitMb = Math.max(10, Math.min(1024, limitMb));
        return limitMb * 1024L * 1024L;
    }

    @Override
    public void run() {
        try {
            long cacheSizeLimit = getCacheSizeLimit();
            if (cacheSizeLimit == Long.MAX_VALUE) {
                Log.i("IconCacheJanitor", "Skipping cleanup because mod icon cache is unlimited");
                return;
            }

            File modIconCachePath = ModIconCache.getImageCachePath();
            if (!modIconCachePath.isDirectory() || !modIconCachePath.canRead()) return;

            File[] modIconFiles = modIconCachePath.listFiles();
            if (modIconFiles == null) return;

            ArrayList<File> writableModIconFiles = new ArrayList<>(modIconFiles.length);
            long directoryFileSize = 0;
            for (File modIconFile : modIconFiles) {
                if (!modIconFile.isFile() || !modIconFile.canRead()) continue;
                directoryFileSize += modIconFile.length();
                if (modIconFile.canWrite()) writableModIconFiles.add(modIconFile);
            }

            if (directoryFileSize <= cacheSizeLimit) {
                Log.i("IconCacheJanitor", "Skipping cleanup because cache is within the selected limit");
                return;
            }

            // Remove the oldest files first and bring the cache down to half its limit.
            final long cacheBringdown = cacheSizeLimit / 2;
            Collections.sort(writableModIconFiles,
                    Comparator.comparingLong(File::lastModified));

            int filesCleanedUp = 0;
            for (File modFile : writableModIconFiles) {
                if (directoryFileSize <= cacheBringdown) break;
                long modFileSize = modFile.length();
                if (modFile.delete()) {
                    directoryFileSize -= modFileSize;
                    filesCleanedUp++;
                }
            }

            Log.i("IconCacheJanitor", "Cleaned up " + filesCleanedUp
                    + " files; cache limit=" + cacheSizeLimit + " bytes");
        } finally {
            synchronized (IconCacheJanitor.class) {
                sJanitorFuture = null;
            }
        }
    }

    /**
     * Requests a cleanup. If one is already running, the existing task is reused.
     */
    public static void runJanitor() {
        synchronized (IconCacheJanitor.class) {
            if (sJanitorFuture != null) return;
            sJanitorFuture = PojavApplication.sExecutorService.submit(new IconCacheJanitor());
        }
    }

    /**
     * Forces a cleanup request when the user changes the cache setting.
     */
    public static void runJanitorNow() {
        runJanitor();
    }

    /**
     * Waits for the janitor task to finish, if there is one running already.
     */
    public static void waitForJanitorToFinish() {
        synchronized (IconCacheJanitor.class) {
            if (sJanitorFuture == null) return;
            try {
                sJanitorFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException("Should not happen!", e);
            }
        }
    }
}
