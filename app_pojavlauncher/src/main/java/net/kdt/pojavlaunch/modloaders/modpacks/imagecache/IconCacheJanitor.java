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
    private static final String PREF_CACHE_LIMIT = "cacheLimit";
    private static final String PREF_CACHE_UNLIMITED = "cacheUnlimited";

    private static Future<?> sJanitorFuture;

    private IconCacheJanitor() {
        // don't allow others to create this
    }

    private static long getCacheSizeLimit() {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        if (preferences == null) return DEFAULT_CACHE_SIZE_LIMIT;

        if (preferences.getBoolean(PREF_CACHE_UNLIMITED, false)) {
            return Long.MAX_VALUE;
        }

        int limitMb = preferences.getInt(PREF_CACHE_LIMIT, 100);
        limitMb = Math.max(10, Math.min(1024, limitMb));
        return limitMb * 1024L * 1024L;
    }

    @Override
    public void run() {
        try {
            long cacheSizeLimit = getCacheSizeLimit();
            if (cacheSizeLimit == Long.MAX_VALUE) {
                Log.i("IconCacheJanitor", "Skipping cleanup because cache is unlimited");
                return;
            }

            File cacheDirectory = ModIconCache.getImageCachePath();
            if (!cacheDirectory.isDirectory() || !cacheDirectory.canRead()) return;

            File[] cacheFiles = cacheDirectory.listFiles();
            if (cacheFiles == null) return;

            ArrayList<File> writableCacheFiles = new ArrayList<>(cacheFiles.length);
            long cacheSize = 0;

            for (File cacheFile : cacheFiles) {
                if (!cacheFile.isFile() || !cacheFile.canRead()) continue;
                cacheSize += cacheFile.length();
                if (cacheFile.canWrite()) writableCacheFiles.add(cacheFile);
            }

            if (cacheSize <= cacheSizeLimit) {
                Log.i("IconCacheJanitor", "Cache is within the selected limit");
                return;
            }

            // Remove the oldest files first and bring the cache down to half its limit.
            final long bringDownLimit = cacheSizeLimit / 2;
            Collections.sort(writableCacheFiles,
                    Comparator.comparingLong(File::lastModified));

            int filesCleanedUp = 0;
            for (File cacheFile : writableCacheFiles) {
                if (cacheSize <= bringDownLimit) break;

                long fileSize = cacheFile.length();
                if (cacheFile.delete()) {
                    cacheSize -= fileSize;
                    filesCleanedUp++;
                }
            }

            Log.i("IconCacheJanitor", "Cleaned up " + filesCleanedUp
                    + " files; cache size=" + cacheSize + " bytes");
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
     * Requests a cleanup after the user changes the cache setting.
     */
    public static void runJanitorNow() {
        runJanitor();
    }

    /**
     * Waits for the janitor task to finish without holding the class lock
     * while Future.get() is running. This avoids a deadlock with run().
     */
    public static void waitForJanitorToFinish() {
        Future<?> future;
        synchronized (IconCacheJanitor.class) {
            future = sJanitorFuture;
        }

        if (future == null) return;

        try {
            future.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Should not happen!", e);
        }
    }
}
