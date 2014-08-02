/*
 * PlaylistEntry.java
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

public class PlaylistEntry {

    public static final String TAG = PlaylistEntry.class.getSimpleName();



    /*

     */
	public final static String TABLE_NAME = "library_playlist_entry";



    /*
        Table fields
    */
	public static final String COLUMN_FIELD_ENTRY_ID        = "_id";

	public static final String COLUMN_FIELD_PLAYLIST_ID     = "playlist_id";
	
	public static final String COLUMN_FIELD_POSITION        = "position";
	
	public static final String COLUMN_FIELD_SONG_ID         = "song_id";



    /*
        Creation & deletion routines.
     */
	public static void createTable(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
				+ COLUMN_FIELD_ENTRY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COLUMN_FIELD_PLAYLIST_ID + " INTEGER, "
				+ COLUMN_FIELD_POSITION + " INTEGER, "
				+ COLUMN_FIELD_SONG_ID + " INTEGER);");
	}
	
	public static void destroyTable(SQLiteDatabase database) {
		database.execSQL("DROP TABLE " + TABLE_NAME + ";");
	}

}
