/*
 * Album.java
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

public class Album {

    public static final String TAG = Album.class.getSimpleName();



    /*

     */
	public final static String TABLE_NAME = "library_album";



    /*
        Table fields
    */
	public static final String COLUMN_FIELD_ALBUM_ID        = "_id";

	public static final String COLUMN_FIELD_ALBUM_NAME      = "album_name";

	public static final String COLUMN_FIELD_ALBUM_ARTIST    = "album_artist";
	
	public static final String COLUMN_FIELD_ALBUM_ARTIST_ID = "album_artist_id";

	public static final String COLUMN_FIELD_ALBUM_ART       = "album_art";

	public static final String COLUMN_FIELD_USER_HIDDEN     = "user_hidden";



    /*
        Creation & deletion routines.
     */
	public static void createTable(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
				+ COLUMN_FIELD_ALBUM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COLUMN_FIELD_ALBUM_NAME + " TEXT UNIQUE ON CONFLICT IGNORE, "
				+ COLUMN_FIELD_ALBUM_ARTIST + " TEXT, "
				+ COLUMN_FIELD_ALBUM_ARTIST_ID + " INTEGER, "
				+ COLUMN_FIELD_ALBUM_ART + " TEXT, "
				+ COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
	}
	
	public static void destroyTable(SQLiteDatabase database) {
		database.execSQL("DROP TABLE " + TABLE_NAME + ";");
	}
}
