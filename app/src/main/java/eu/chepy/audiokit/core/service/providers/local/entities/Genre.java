/*
 * Genre.java
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

public class Genre {

    public static final String TAG = Genre.class.getSimpleName();



    /*

     */
	public final static String TABLE_NAME = "library_genre";



    /*
        Table fields
    */
	public static final String COLUMN_FIELD_GENRE_ID        = "_id";

	public static final String COLUMN_FIELD_GENRE_NAME      = "genre_name";
	
	public static final String COLUMN_FIELD_VISIBLE         = "visible";

	public static final String COLUMN_FIELD_USER_HIDDEN     = "user_hidden";



    /*
        Creation & deletion routines.
     */
	public static void createTable(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
				+ COLUMN_FIELD_GENRE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COLUMN_FIELD_GENRE_NAME + " TEXT UNIQUE ON CONFLICT IGNORE, "
				+ COLUMN_FIELD_VISIBLE + " BOOLEAN, "
				+ COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
	}
	
	public static void destroyTable(SQLiteDatabase database) {
		database.execSQL("DROP TABLE " + TABLE_NAME + ";");
	}
	
}
