/*
 * Playlist.java
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

public class Playlist {

    public static final String TAG = Playlist.class.getSimpleName();



    /*

     */
	public final static String TABLE_NAME = "library_playlist";



    /*
        Table fields
    */
	public static final String COLUMN_FIELD_PLAYLIST_ID        = "_id";

	public static final String COLUMN_FIELD_PLAYLIST_NAME      = "playlist_name";
	
	public static final String COLUMN_FIELD_REPEAT_STATE       = "repeat_mode";
	
	public static final String COLUMN_FIELD_SHUFFLE_STATE      = "shuffle_mode";
	
	public static final String COLUMN_FIELD_VISIBLE            = "visible";

    public static final String COLUMN_FIELD_USER_HIDDEN     = "user_hidden";



    /*
        Creation & deletion routines.
     */
	public static void createTable(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
				+ COLUMN_FIELD_PLAYLIST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COLUMN_FIELD_PLAYLIST_NAME + " TEXT UNIQUE ON CONFLICT FAIL, "
				+ COLUMN_FIELD_REPEAT_STATE + " INTEGER, "
				+ COLUMN_FIELD_SHUFFLE_STATE + " INTEGER, "
                + COLUMN_FIELD_VISIBLE + " BOOLEAN, "
                + COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
	}
	
	public static void destroyTable(SQLiteDatabase database) {
		database.execSQL("DROP TABLE " + TABLE_NAME + ";");
	}

}
