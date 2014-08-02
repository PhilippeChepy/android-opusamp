/*
 * MediaSong.java
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
package eu.chepy.audiokit.core.service.providers.local.entities;

import android.database.sqlite.SQLiteDatabase;

public class Media {

    public static final String TAG = Media.class.getSimpleName();



    /*

     */
	public static final String TABLE_NAME = "media_audio";



    /*
        Table fields
    */
    public static final String COLUMN_FIELD_ID             = "_id";

    public static final String COLUMN_FIELD_URI             = "uri";

	public static final String COLUMN_FIELD_ART             = "track_art";
	
	public static final String COLUMN_FIELD_DURATION        = "duration";

	public static final String COLUMN_FIELD_BITRATE         = "bitrate";
	
	public static final String COLUMN_FIELD_SAMPLE_RATE     = "sample_rate";

	public static final String COLUMN_FIELD_CODEC           = "codec";

	public static final String COLUMN_FIELD_SCORE           = "score";

	public static final String COLUMN_FIELD_NOTE            = "note";

	public static final String COLUMN_FIELD_FIRST_PLAYED    = "first_played";

	public static final String COLUMN_FIELD_LAST_PLAYED     = "last_played";

	public static final String COLUMN_FIELD_TITLE           = "title";

	public static final String COLUMN_FIELD_ARTIST          = "artist";
	
	public static final String COLUMN_FIELD_ARTIST_ID       = "artist_id";

	public static final String COLUMN_FIELD_ALBUM_ARTIST    = "album_artist";
	
	public static final String COLUMN_FIELD_ALBUM_ARTIST_ID = "album_artist_id";

	public static final String COLUMN_FIELD_ALBUM           = "album";
	
	public static final String COLUMN_FIELD_ALBUM_ID        = "album_id";

	public static final String COLUMN_FIELD_GENRE           = "genre";
	
	public static final String COLUMN_FIELD_GENRE_ID        = "genre_id";
	
	public static final String COLUMN_FIELD_YEAR            = "year";

	public static final String COLUMN_FIELD_TRACK           = "track";

	public static final String COLUMN_FIELD_DISC            = "disc";

	public static final String COLUMN_FIELD_BPM             = "bpm";

	public static final String COLUMN_FIELD_COMMENT         = "comment";

	public static final String COLUMN_FIELD_LYRICS          = "lyrics";
	
	public static final String COLUMN_FIELD_VISIBLE         = "visible";

    public static final String COLUMN_FIELD_HAS_EMBEDDED_ART = "has_embedded_art";

    public static final String COLUMN_FIELD_USE_EMBEDDED_ART = "uses_embedded_art";
	
	public static final String COLUMN_FIELD_IS_QUEUE_FILE_ENTRY   = "queue_file_entry"; /* queue entry is file from StorageFragment */

	public static final String COLUMN_FIELD_USER_HIDDEN           = "user_hidden";



    /*
        Creation & deletion routines.
     */
	public static void createTable(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
				+ COLUMN_FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COLUMN_FIELD_URI + " TEXT, "
				+ COLUMN_FIELD_ART + " TEXT, "
				+ COLUMN_FIELD_DURATION + " INTEGER, "
				+ COLUMN_FIELD_BITRATE + " TEXT, "
				+ COLUMN_FIELD_SAMPLE_RATE + " INTEGER, "
				+ COLUMN_FIELD_CODEC + " TEXT, "
				+ COLUMN_FIELD_SCORE + " INTEGER, "
				+ COLUMN_FIELD_NOTE + " INTEGER, "
				+ COLUMN_FIELD_FIRST_PLAYED + " INTEGER, "
				+ COLUMN_FIELD_LAST_PLAYED + " INTEGER, "
				+ COLUMN_FIELD_TITLE + " TEXT, "
				+ COLUMN_FIELD_ARTIST + " TEXT, "
				+ COLUMN_FIELD_ARTIST_ID + " INTEGER, "
				+ COLUMN_FIELD_ALBUM_ARTIST + " TEXT, "
				+ COLUMN_FIELD_ALBUM_ARTIST_ID + " INTEGER, "
				+ COLUMN_FIELD_ALBUM + " TEXT, "
				+ COLUMN_FIELD_ALBUM_ID + " INTEGER, "
				+ COLUMN_FIELD_GENRE + " TEXT, "
				+ COLUMN_FIELD_GENRE_ID + " INTEGER, "
				+ COLUMN_FIELD_YEAR + " INTEGER, "
				+ COLUMN_FIELD_TRACK + " INTEGER, "
				+ COLUMN_FIELD_DISC + " INTEGER, "
				+ COLUMN_FIELD_BPM + " INTEGER, "
				+ COLUMN_FIELD_COMMENT + " TEXT, "
				+ COLUMN_FIELD_LYRICS + " TEXT, "
				+ COLUMN_FIELD_VISIBLE + " BOOLEAN, "
                + COLUMN_FIELD_HAS_EMBEDDED_ART + " BOOLEAN, "
                + COLUMN_FIELD_USE_EMBEDDED_ART + " BOOLEAN, "
				+ COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " BOOLEAN, "
				+ COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
	}
	
	public static void destroyTable(SQLiteDatabase database) {
		database.execSQL("DROP TABLE " + TABLE_NAME + ";");
	}
}
