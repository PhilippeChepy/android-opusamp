package eu.chepy.audiokit.core.service.providers.local.entities;

import android.database.sqlite.SQLiteDatabase;

public class ScanDirectory {

    public static final String TAG = ScanDirectory.class.getSimpleName();



    /*

     */
	public final static String TABLE_NAME = "library_scan_paths";



    /*
        Table fields
    */
	public static final String COLUMN_FIELD_SCAN_DIRECTORY_ID        = "_id";

	public static final String COLUMN_FIELD_SCAN_DIRECTORY_NAME      = "directory_path";
	
	public static final String COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED = "exclude";



    /*
        Creation & deletion routines.
     */
	public static void createTable(SQLiteDatabase database) {
		database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
				+ COLUMN_FIELD_SCAN_DIRECTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COLUMN_FIELD_SCAN_DIRECTORY_NAME + " TEXT UNIQUE ON CONFLICT FAIL, "
				+ COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED + " BOOLEAN);");
	}
	
	public static void destroyTable(SQLiteDatabase database) {
		database.execSQL("DROP TABLE " + TABLE_NAME + ";");
	}
}
