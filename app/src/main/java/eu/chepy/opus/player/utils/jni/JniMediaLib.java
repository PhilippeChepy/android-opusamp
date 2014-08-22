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
package eu.chepy.opus.player.utils.jni;

import android.content.ContentValues;

import java.io.File;
import java.io.InputStream;

import eu.chepy.opus.player.core.service.providers.local.database.Entities;
import eu.chepy.opus.player.core.service.providers.local.utils.CoverInputStream;

public abstract class JniMediaLib {

    public static final String TAG = JniMediaLib.class.getSimpleName();


    public static final int TSC_FORMAT_VORBIS = 1;



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



    // Native engine context
    private long engineContext = 0;



    protected native long engineInitialize();

    protected native long engineFinalize();

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

    public native static long coverInputStreamOpen(String path);

    public native static long coverInputStreamClose(long nativeContext);

    public native static int coverInputStreamReadSingle(long nativeContext, int position);

    public native static int coverInputStreamReadGetCount(long nativeContext);

    public native static int coverInputStreamReadArray(long nativeContext, byte target[], int off, int len, int nativePos);


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

	public static ContentValues doReadTags(File file, ContentValues contentValues) {
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
            //contentValues.put(Media.COLUMN_FIELD_COMPOSER, tagComposer);
            contentValues.put(Entities.Media.COLUMN_FIELD_ALBUM_ARTIST, tagAlbumArtist);
            contentValues.put(Entities.Media.COLUMN_FIELD_ALBUM, tagAlbum);
            contentValues.put(Entities.Media.COLUMN_FIELD_GENRE, tagGenre);
            contentValues.put(Entities.Media.COLUMN_FIELD_YEAR, tagYear);
            contentValues.put(Entities.Media.COLUMN_FIELD_TRACK, tagTrack);
            contentValues.put(Entities.Media.COLUMN_FIELD_DISC, tagDisc);
            contentValues.put(Entities.Media.COLUMN_FIELD_BPM, tagBpm);
            contentValues.put(Entities.Media.COLUMN_FIELD_COMMENT, tagComment);
            contentValues.put(Entities.Media.COLUMN_FIELD_LYRICS, tagLyrics);
            contentValues.put(Entities.Media.COLUMN_FIELD_HAS_EMBEDDED_ART, hasEmbeddedArt);
            contentValues.put(Entities.Media.COLUMN_FIELD_USE_EMBEDDED_ART, hasEmbeddedArt);

            return contentValues;
        }
	}

    public static InputStream getCoverInputStream(File file) {
        if (file != null) {
            return new CoverInputStream(file.getAbsolutePath());
        }

        return null;
    }
}
