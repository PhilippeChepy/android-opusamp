/*
 * OpenHelper.java
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

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.ui.utils.PlayerApplication;

public class OpenHelper extends SQLiteOpenHelper {


    private final static int DATABASE_VERSION = 1;

    private static EqualizerPreset defaultPresets[] = new EqualizerPreset[] {
            new EqualizerPreset("Flat", 20, new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f}),
            new EqualizerPreset("Classical", 20, new float[] { -1.11022e-15f, -1.11022e-15f, -1.11022e-15f, -1.11022e-15f, -1.11022e-15f, -1.11022e-15f, -7.2f, -7.2f, -7.2f, -9.6f }),
            new EqualizerPreset("Club", 14.0f, new float[] { -1.11022e-15f, -1.11022e-15f, 8.0f, 5.6f, 5.6f, 5.6f, 3.2f, -1.11022e-15f, -1.11022e-15f, -1.11022e-15f }),
            new EqualizerPreset("Dance", 13.0f, new float[] { 9.6f, 7.2f, 2.4f, -1.11022e-15f, -1.11022e-15f, -5.6f, -7.2f, -7.2f, -1.11022e-15f, -1.11022e-15f }),
            new EqualizerPreset("Full bass", 13.0f, new float[] { -8.0f, 9.6f, 9.6f, 5.6f, 1.6f, -4.0f, -8.0f, -10.4f, -11.2f, -11.2f }),
            new EqualizerPreset("Full bass and treble", 12.0f, new float[] { 7.2f, 5.6f, -1.11022e-15f, -7.2f, -4.8f, 1.6f, 8.0f, 11.2f, 12.0f, 12.0f }),
            new EqualizerPreset("Full treble", 11.0f, new float[] { -9.6f, -9.6f, -9.6f, -4.0f, 2.4f, 11.2f, 16.0f, 16.0f, 16.0f, 16.8f }),
            new EqualizerPreset("Headphones", 12.0f, new float[] { 4.8f, 11.2f, 5.6f, -3.2f, -2.4f, 1.6f, 4.8f, 9.6f, 12.8f, 14.4f }),
            new EqualizerPreset("Large Hall", 13.0f,new float[] { 10.4f, 10.4f, 5.6f, 5.6f, -1.11022e-15f, -4.8f, -4.8f, -4.8f, -1.11022e-15f, -1.11022e-15f }),
            new EqualizerPreset("Live", 15.0f,new float[] { -4.8f, -1.11022e-15f, 4.0f, 5.6f, 5.6f, 5.6f, 4.0f, 2.4f, 2.4f, 2.4f }),
            new EqualizerPreset("Party", 14.0f,new float[] { 7.2f, 7.2f, -1.11022e-15f, -1.11022e-15f, -1.11022e-15f, -1.11022e-15f, -1.11022e-15f, -1.11022e-15f, 7.2f, 7.2f }),
            new EqualizerPreset("Pop", 14.0f,new float[] { -1.6f, 4.8f, 7.2f, 8.0f, 5.6f, -1.11022e-15f, -2.4f, -2.4f, -1.6f, -1.6f }),
            new EqualizerPreset("Reggae", 16.0f,new float[] { -1.11022e-15f, -1.11022e-15f, -1.11022e-15f, -5.6f, -1.11022e-15f, 6.4f, 6.4f, -1.11022e-15f, -1.11022e-15f, -1.11022e-15f }),
            new EqualizerPreset("Rock", 13.0f, new float[] { 8.0f, 4.8f, -5.6f, -8.0f, -3.2f, 4.0f, 8.8f, 11.2f, 11.2f, 11.2f }),
            new EqualizerPreset("Ska", 14.0f,new float[] { -2.4f, -4.8f, -4.0f, -1.11022e-15f, 4.0f, 5.6f, 8.8f, 9.6f, 11.2f, 9.6f }),
            new EqualizerPreset("Soft", 13.0f, new float[] { 4.8f, 1.6f, -1.11022e-15f, -2.4f, -1.11022e-15f, 4.0f, 8.0f, 9.6f, 11.2f, 12.0f }),
            new EqualizerPreset("Soft rock", 15.0f, new float[] { 4.0f, 4.0f, 2.4f, -1.11022e-15f, -4.0f, -5.6f, -3.2f, -1.11022e-15f, 2.4f, 8.8f }),
            new EqualizerPreset("Techno", 13.0f, new float[] { 8.0f, 5.6f, -1.11022e-15f, -5.6f, -4.8f, -1.11022e-15f, 8.0f, 9.6f, 9.6f, 8.8f })
    };

    private static class SingletonHolder {
        private final static OpenHelper instance = new OpenHelper();
    }


    private OpenHelper() {
        super(PlayerApplication.context, "provider-index.db", null, DATABASE_VERSION);
    }

    public static OpenHelper getInstance() {
        return SingletonHolder.instance;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
		// Library tables
        Entities.Provider.createTable(database);

        ContentValues contentValues = new ContentValues();
        contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_NAME, PlayerApplication.context.getString(R.string.label_default_library));
        contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION, 0);
        contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE, AbstractMediaManager.LOCAL_MEDIA_MANAGER);
        database.insert(Entities.Provider.TABLE_NAME, null, contentValues);

        // Default equalizer presets
        Entities.EqualizerPresets.createTable(database);

        initDefaultEQPresets(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Entities.Provider.destroyTable(database);
        Entities.EqualizerPresets.destroyTable(database);
        onCreate(database);
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Entities.Provider.destroyTable(database);
        Entities.EqualizerPresets.destroyTable(database);
        onCreate(database);
    }

    public static void initDefaultEQPresets(SQLiteDatabase database) {
        ContentValues contentValues = new ContentValues();

        for (EqualizerPreset preset : defaultPresets) {
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESET_NAME, preset.name);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESET_BAND_COUNT, preset.bandCount);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_PREAMP, preset.preamp);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND0, preset.bands[0]);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND1, preset.bands[1]);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND2, preset.bands[2]);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND3, preset.bands[3]);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND4, preset.bands[4]);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND5, preset.bands[5]);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND6, preset.bands[6]);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND7, preset.bands[7]);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND8, preset.bands[8]);
            contentValues.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND9, preset.bands[9]);
            database.insert(Entities.EqualizerPresets.TABLE_NAME, null, contentValues);
        }
    }


    static public class EqualizerPreset {
        public String name;
        public int bandCount;
        public float preamp;
        public float bands[];

        public EqualizerPreset(String name, float preamp, float bands[]) {
            this.name = name;
            this.bandCount = bands.length;
            this.preamp = preamp;
            this.bands = new float[10];

            System.arraycopy(bands, 0, this.bands, 0, bands.length);
        }
    }
}
