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
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;

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
        Entities.Provider.createTable(database);

        ContentValues contentValues = new ContentValues();
        contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_NAME, context.getString(R.string.label_default_library));
        contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION, 0);
        contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE, AbstractMediaManager.LOCAL_MEDIA_MANAGER);
        database.insert(Entities.Provider.TABLE_NAME, null, contentValues);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Entities.Provider.destroyTable(database);
        onCreate(database);
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Entities.Provider.destroyTable(database);
        onCreate(database);
    }
}
