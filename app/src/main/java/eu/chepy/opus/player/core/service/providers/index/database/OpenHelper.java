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
package eu.chepy.opus.player.core.service.providers.index.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;
import eu.chepy.opus.player.core.service.providers.local.database.Entities;
import eu.chepy.opus.player.ui.utils.PlayerApplication;

public class OpenHelper extends SQLiteOpenHelper {


    private final static int DATABASE_VERSION = 1;

    private Context context = null;

    public OpenHelper(Context context) {
        super(context, "provider-index.db", null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
		/*
		 * Library tables
		 */
        eu.chepy.opus.player.core.service.providers.index.database.Entities.Provider.createTable(database);

        // Default : /sdcard/Music with local provider.
        ContentValues contentValues = new ContentValues();
        contentValues.put(eu.chepy.opus.player.core.service.providers.index.database.Entities.Provider.COLUMN_FIELD_PROVIDER_NAME, context.getString(R.string.label_default_library));
        contentValues.put(eu.chepy.opus.player.core.service.providers.index.database.Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION, 0);
        contentValues.put(eu.chepy.opus.player.core.service.providers.index.database.Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE, AbstractMediaManager.LOCAL_MEDIA_MANAGER);
        database.insert(eu.chepy.opus.player.core.service.providers.index.database.Entities.Provider.TABLE_NAME, null, contentValues);

        final eu.chepy.opus.player.core.service.providers.local.database.OpenHelper localOpenHelper = new eu.chepy.opus.player.core.service.providers.local.database.OpenHelper(PlayerApplication.context, 1);
        final SQLiteDatabase localDatabase = localOpenHelper.getWritableDatabase();
        if (localDatabase != null) {
            contentValues.clear();
            contentValues.put(Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_NAME, PlayerApplication.getMusicDirectory().getAbsolutePath());
            contentValues.put(Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED, 0);
            localDatabase.insert(Entities.ScanDirectory.TABLE_NAME, null, contentValues);
        }
        localOpenHelper.close();
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        eu.chepy.opus.player.core.service.providers.index.database.Entities.Provider.destroyTable(database);
        onCreate(database);
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        eu.chepy.opus.player.core.service.providers.index.database.Entities.Provider.destroyTable(database);
        onCreate(database);
    }
}