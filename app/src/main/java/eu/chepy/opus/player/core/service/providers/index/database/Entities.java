/*
 * Entities.java
 *
 * Copyright (c) 2014, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.opus.player.core.service.providers.index.database;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

public class Entities {
    public abstract static class Provider implements BaseColumns {

        public static final String TAG = Provider.class.getSimpleName();



        /*

         */
        public final static String TABLE_NAME = "provider_index";



        /*
            Table fields
        */
        public static final String COLUMN_FIELD_PROVIDER_POSITION   = "provider_position";

        public static final String COLUMN_FIELD_PROVIDER_NAME   = "provider_name";

        public static final String COLUMN_FIELD_PROVIDER_TYPE   = "provider_type";



        /*
            Creation & deletion routines.
         */
        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_PROVIDER_POSITION + " INTEGER, "
                    + COLUMN_FIELD_PROVIDER_NAME + " TEXT UNIQUE ON CONFLICT IGNORE, "
                    + COLUMN_FIELD_PROVIDER_TYPE + " INTEGER);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }
    }
}
