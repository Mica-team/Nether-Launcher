package net.kdt.pojavlaunch.modloaders.modpacks.imagecache;

import android.util.Log;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Keeps the launcher mod-icon cache below the user-selected limit. */
public class IconCacheJanitor implements Runnable {
    private static final long DEFAULT_LIMIT_BYTES = 100L * 1024L * 1024L;
    private static final int BRINGDOWN_PERCENT = 50;
    private static Future<?> sJanitorFuture;

    private IconCacheJanitor() {
    }

    public static long getCacheLimitBytes() {
        if (LauncherPreferences.DEFAULT_PREF == null) return DEFAULT_LIMIT_BYTES;
        int limitMb = LauncherPreferences.DEFAULT_PREF.getInt("cacheLimitMb", 100);
        if (limitMb <= 0) return 0L; // Unlimited
        return limitMb * 1024L * 1024L;
    }

    public static long getCacheSizeBytes() {
        File cachePath = ModIconCache.getImageCachePath();
        if (!cachePath.isDirectory()) return 0L;
        File[] files = cachePath.listFiles();
        if (files == null) return 0L;
        long size = 0L;
        for (File file : files) {
            if (file.isFile() && file.canRead()) size += file.length();
        }
        return size;
    }

    /** Returns false when the configured cache is still full after cleanup. */
    public static boolean canCreateCacheFile() {
        long limit = getCacheLimitBytes();
        if (limit == 0L) return true;

        long size = getCacheSizeBytes();
        if (size < limit) return true;

        runJanitor();
        waitForJanitorToFinish();
        return getCacheSizeBytes() < limit;
    }

    @Override
    public void run() {
        try {
            long limit = getCacheLimitBytes();
            if (limit == 0L) {
                return;
            }

            File cachePath = ModIconCache.getImageCachePath();
            if (!cachePath.isDirectory() || !cachePath.canRead()) return;

            File[] files = cachePath.listFiles();
            if (files == null) return;

            ArrayList<File> writableFiles = new ArrayList<>(files.length);
            long cacheSize = 0L;
            for (File file : files) {
                if (!file.isFile() || !file.canRead()) continue;
                cacheSize += file.length();
                if (file.canWrite()) writableFiles.add(file);
            }

            if (cacheSize < limit) {
                Log.i("IconCacheJanitor", "Cache is within the configured limit");
                return;
            }

            // Oldest files are removed first. The newest icons remain cached.
            writableFiles.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            long targetSize = limit * BRINGDOWN_PERCENT / 100L;
            int filesCleanedUp = 0;
            for (File file : writableFiles) {
                if (cacheSize <= targetSize) break;
                long fileSize = file.length();
                if (file.delete()) {
                    cacheSize -= fileSize;
                    filesCleanedUp++;
                }
            }
            Log.i("IconCacheJanitor", "Cleaned up " + filesCleanedUp + " files; cache is " + cacheSize + " bytes");
        } finally {
            synchronized (IconCacheJanitor.class) {
                sJanitorFuture = null;
            }
        }
    }

    /** Starts cleanup if one is not already running. */
    public static void runJanitor() {
        synchronized (IconCacheJanitor.class) {
            if (sJanitorFuture != null) return;
            sJanitorFuture = PojavApplication.sExecutorService.submit(new IconCacheJanitor());
        }
    }

    /** Waits for an active cleanup task to finish. */
    public static void waitForJanitorToFinish() {
        Future<?> future;
        synchronized (IconCacheJanitor.class) {
            future = sJanitorFuture;
        }
        if (future == null) return;
        try {
            future.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Cache cleanup failed", e);
        }
    }
}
