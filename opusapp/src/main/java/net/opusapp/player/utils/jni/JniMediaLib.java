/*
 * JniMediaLib.java
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package net.opusapp.player.utils.jni;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.utils.LogUtils;

import java.io.File;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

public abstract class JniMediaLib {

    public static final String TAG = JniMediaLib.class.getSimpleName();

	private static int tagDuration;
	private static int tagBitrate;
	private static int tagSamplerate;
	private static String tagTitle;
	private static String tagArtist;
	private static String tagComposer;
	private static String tagAlbumArtist;
	private static String tagAlbum;
	private static String tagGenre;
	private static int tagYear;
	private static int tagTrack;
	private static int tagDisc;
	private static int tagBpm;
	private static String tagComment;
	private static String tagLyrics;
    private static boolean hasEmbeddedArt;

    private static final Object threadLocker = new Object();

    private static final Object threadLocker2 = new Object();



    // Native engine context
    @SuppressWarnings("unused")
    private long engineContext = 0;



    protected native long engineInitialize();

    protected native long engineFinalize();

    protected native boolean engineEqualizerIsEnabled();

    protected native long engineEqualizerSetEnabled(boolean enabled);

    protected native long engineEqualizerBandSetValue(int bandId, int gain);

    protected native int engineEqualizerBandGetValue(int bandId);

    protected native boolean engineEqualizerApplyProperties();


    protected native long streamInitialize(String mediaPath);

    protected native long streamFinalize(long streamContext);

    protected native long streamPreload(long streamContext);

    protected native long streamTranscode(long streamContext, int targetFormat, String mediaPath);

    protected native long streamStart(long streamContext);

    protected native long streamStop(long streamContext);

    protected native long streamSetPosition(long streamContext, long position);

    protected native long streamGetPosition(long streamContext);

    protected native long streamGetDuration(long streamContext);


    private native static void tagsRead(String path);

    private native static int saveCover(String path, String savePath);

    private native static void tagsWrite(String path);




    @SuppressWarnings("unused")
    abstract protected void playbackUpdateTimestamp(long timestamp);

    @SuppressWarnings("unused")
    abstract protected void playbackEndNotification();

	static {
        System.loadLibrary("soxr");
        System.loadLibrary("ffmpeg");
        System.loadLibrary("taglib");
        System.loadLibrary("medialib");
	}
	
	private static void resetTags() {
		tagDuration = 0;
		tagBitrate = 0;
		tagSamplerate = 0;
		tagTitle = null;
		tagArtist = null;
		tagComposer = null;
		tagAlbumArtist = null;
		tagAlbum = null;
		tagGenre = null;
		tagYear = 0;
		tagTrack = 0;
		tagDisc = 0;
		tagBpm = 0;
		tagComment = null;
		tagLyrics = null;
        hasEmbeddedArt = false;
	}

	public static void readTags(final File file, final ContentValues contentValues) {
        synchronized (threadLocker) {
            resetTags();
            tagsRead(file.getAbsolutePath());

            contentValues.clear();
            contentValues.put(Entities.Media.COLUMN_FIELD_URI, "file://" + file.getAbsolutePath());
            contentValues.put(Entities.Media.COLUMN_FIELD_DURATION, tagDuration);
            contentValues.put(Entities.Media.COLUMN_FIELD_BITRATE, tagBitrate);
            contentValues.put(Entities.Media.COLUMN_FIELD_SAMPLE_RATE, tagSamplerate);
            contentValues.put(Entities.Media.COLUMN_FIELD_TITLE, tagTitle);
            contentValues.put(Entities.Media.COLUMN_FIELD_ARTIST, tagArtist);
            //contentValues.put(Entities.Media.COLUMN_FIELD_COMPOSER, tagComposer);
            contentValues.put(Entities.Media.COLUMN_FIELD_ALBUM_ARTIST, tagAlbumArtist);
            contentValues.put(Entities.Media.COLUMN_FIELD_ALBUM, tagAlbum);
            contentValues.put(Entities.Media.COLUMN_FIELD_GENRE, tagGenre);
            contentValues.put(Entities.Media.COLUMN_FIELD_YEAR, tagYear);
            contentValues.put(Entities.Media.COLUMN_FIELD_TRACK, tagTrack);
            contentValues.put(Entities.Media.COLUMN_FIELD_DISC, tagDisc);
            contentValues.put(Entities.Media.COLUMN_FIELD_BPM, tagBpm);
            contentValues.put(Entities.Media.COLUMN_FIELD_COMMENT, tagComment);
            contentValues.put(Entities.Media.COLUMN_FIELD_LYRICS, tagLyrics);
            contentValues.put(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART, hasEmbeddedArt);
        }
	}

    public static File embeddedCoverDump(File file) {
        synchronized (threadLocker2) {
            final File targetPath = embeddedCoverCacheFile(file);

            if (targetPath == null || (targetPath.exists() && targetPath.length() > 0)) {
                return targetPath;
            }

            if (saveCover(file.getAbsolutePath(), targetPath.getAbsolutePath()) >= 0) {
                embeddedCoverCleanCache();
                return targetPath;
            }
        }

        return null;
    }

    public static File embeddedCoverCacheFile(File file) {
        final int RADIX = 10 + 26; // 10 digits + 26 letters

        byte[] md5 = getHash(file.getAbsolutePath().getBytes());
        BigInteger bi = new BigInteger(md5).abs();

        File basePath = PlayerApplication.getDiskCacheDir("embedded-src");
        if (!basePath.exists()) {
            if (!basePath.mkdirs()) {
                return null;
            }
        }

        return new File(basePath + File.separator + bi.toString(RADIX) + ".jpg");
    }

    public static synchronized void embeddedCoverCleanCache() {
        if (mCoverCacheCleanupThread != null) {
            mCoverCacheCleanupThread.mTerminated = true;

            while (true) {
                try {
                    mCoverCacheCleanupThread.join();
                    mCoverCacheCleanupThread = null;
                    break;
                }
                catch (final InterruptedException interruptedException) {
                    LogUtils.LOGException(TAG, "embeddedCoverCleanCache", 0, interruptedException);
                }
            }
        }

        mCoverCacheCleanupThread = new CoverCacheCleanupThread();
        mCoverCacheCleanupThread.start();
    }

    private static long folderSize(File directory) {
        long length = 0;
        for (File file : directory.listFiles()) {
            if (file.isFile())
                length += file.length();
            else
                length += folderSize(file);
        }
        return length;
    }

    private static byte[] getHash(byte[] data) {
        final String HASH_ALGORITHM = "MD5";

        byte[] hash = null;
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(data);
            hash = digest.digest();
        } catch (final NoSuchAlgorithmException exception) {
            LogUtils.LOGException(TAG, "getHash", 0, exception);
        }

        return hash;
    }

    public static void writeTags(File file, ContentValues contentValues) {
        synchronized (threadLocker) {
            resetTags();

            tagTitle = contentValues.getAsString(Entities.Media.COLUMN_FIELD_TITLE);
            tagArtist = contentValues.getAsString(Entities.Media.COLUMN_FIELD_ARTIST);
            //tagComposer = contentValues.getAsString(Media.COLUMN_FIELD_COMPOSER);
            tagAlbumArtist = contentValues.getAsString(Entities.Media.COLUMN_FIELD_ALBUM_ARTIST);
            tagAlbum = contentValues.getAsString(Entities.Media.COLUMN_FIELD_ALBUM);
            tagGenre = contentValues.getAsString(Entities.Media.COLUMN_FIELD_GENRE);
            tagYear = contentValues.getAsInteger(Entities.Media.COLUMN_FIELD_YEAR);
            tagTrack = contentValues.getAsInteger(Entities.Media.COLUMN_FIELD_TRACK);
            tagDisc = contentValues.getAsInteger(Entities.Media.COLUMN_FIELD_DISC);
            tagBpm = contentValues.getAsInteger(Entities.Media.COLUMN_FIELD_BPM);
            tagComment = contentValues.getAsString(Entities.Media.COLUMN_FIELD_COMMENT);
            tagLyrics = contentValues.getAsString(Entities.Media.COLUMN_FIELD_LYRICS);

            tagsWrite(file.getAbsolutePath());
        }
    }


    private static CoverCacheCleanupThread mCoverCacheCleanupThread;

    static class CoverCacheCleanupThread extends Thread {

        public boolean mTerminated;

        @Override
        public void run() {
            mTerminated = false;

            File basePath = PlayerApplication.getDiskCacheDir("embedded-src");
            final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(PlayerApplication.context);

            int embeddedArtCacheSize = Integer.parseInt(sharedPrefs.getString(PlayerApplication.context.getString(R.string.preference_key_embedded_art_cache_size), "100")) * 1024 * 1024;

            if (folderSize(basePath) > embeddedArtCacheSize) {
                final File[] fileList = basePath.listFiles();

                Arrays.sort(fileList, new Comparator<File>() {

                    @Override
                    public int compare(File lhs, File rhs) {
                        if (lhs.lastModified() == rhs.lastModified()) {
                            return 0;
                        }

                        if (lhs.lastModified() > rhs.lastModified()) {
                            return 1;
                        }

                        return -1;
                    }
                });

                for (final File file : fileList) {
                    if (mTerminated) {
                        break;
                    }

                    if (file.delete()) {
                        if (folderSize(basePath) < embeddedArtCacheSize) {
                            break;
                        }
                    }
                }
            }
        }
    }
}
