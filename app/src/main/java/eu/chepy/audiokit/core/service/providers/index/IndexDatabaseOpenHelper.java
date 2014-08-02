package eu.chepy.audiokit.core.service.providers.index;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.index.entities.Provider;
import eu.chepy.audiokit.core.service.providers.local.InternalDatabaseOpenHelper;
import eu.chepy.audiokit.core.service.providers.local.entities.ScanDirectory;
import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class IndexDatabaseOpenHelper extends SQLiteOpenHelper {


    private final static int DATABASE_VERSION = 1;

    private Context context = null;

    public IndexDatabaseOpenHelper(Context context) {
        super(context, "provIndex.db", null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
		/*
		 * Library tables
		 */
        Provider.createTable(database);

        // Default : /sdcard/Music with local provider.
        ContentValues contentValues = new ContentValues();
        contentValues.put(Provider.COLUMN_FIELD_PROVIDER_NAME, context.getString(R.string.label_default_library));
        contentValues.put(Provider.COLUMN_FIELD_PROVIDER_POSITION, 0);
        contentValues.put(Provider.COLUMN_FIELD_PROVIDER_TYPE, AbstractMediaManager.LOCAL_MEDIA_MANAGER);
        database.insert(Provider.TABLE_NAME, null, contentValues);

        final InternalDatabaseOpenHelper localDatabaseOpenHelper = new InternalDatabaseOpenHelper(PlayerApplication.context, 1);
        final SQLiteDatabase localDatabase = localDatabaseOpenHelper.getWritableDatabase();
        if (localDatabase != null) {
            contentValues.clear();
            contentValues.put(ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_NAME, PlayerApplication.getMusicDirectory().getAbsolutePath());
            contentValues.put(ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED, 0);
            localDatabase.insert(ScanDirectory.TABLE_NAME, null, contentValues);
        }
        localDatabaseOpenHelper.close();
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Provider.destroyTable(database);
        onCreate(database);
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Provider.destroyTable(database);
        onCreate(database);
    }
}
