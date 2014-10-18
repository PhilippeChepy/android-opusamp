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
package net.opusapp.player.core.service.providers.index.database;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

public class Entities {
    public abstract static class Provider implements BaseColumns {

        public static final String TAG = Provider.class.getSimpleName();



        public final static String TABLE_NAME = "provider_index";



        public static final String COLUMN_FIELD_PROVIDER_POSITION   = "provider_position";

        public static final String COLUMN_FIELD_PROVIDER_NAME   = "provider_name";

        public static final String COLUMN_FIELD_PROVIDER_TYPE   = "provider_type";



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

    public abstract static class EqualizerPresets implements BaseColumns {

        public static final String TAG = EqualizerPresets.class.getSimpleName();



        public final static String TABLE_NAME = "equalizer_presets";



        public static final String COLUMN_FIELD_PRESET_NAME = "name";

        public static final String COLUMN_FIELD_PRESET_BAND_COUNT = "band_count";

        public static final String COLUMN_FIELD_PRESERT_PREAMP = "preamp";

        public static final String COLUMN_FIELD_PRESERT_BAND0 = "band0";

        public static final String COLUMN_FIELD_PRESERT_BAND1 = "band1";

        public static final String COLUMN_FIELD_PRESERT_BAND2 = "band2";

        public static final String COLUMN_FIELD_PRESERT_BAND3 = "band3";

        public static final String COLUMN_FIELD_PRESERT_BAND4 = "band4";

        public static final String COLUMN_FIELD_PRESERT_BAND5 = "band5";

        public static final String COLUMN_FIELD_PRESERT_BAND6 = "band6";

        public static final String COLUMN_FIELD_PRESERT_BAND7 = "band7";

        public static final String COLUMN_FIELD_PRESERT_BAND8 = "band8";

        public static final String COLUMN_FIELD_PRESERT_BAND9 = "band9";



        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_PRESET_NAME + " TEXT UNIQUE ON CONFLICT IGNORE, "
                    + COLUMN_FIELD_PRESET_BAND_COUNT + " INT, "
                    + COLUMN_FIELD_PRESERT_PREAMP + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND0 + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND1 + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND2 + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND3 + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND4 + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND5 + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND6 + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND7 + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND8 + " REAL, "
                    + COLUMN_FIELD_PRESERT_BAND9 + " REAL);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }
    }
}
