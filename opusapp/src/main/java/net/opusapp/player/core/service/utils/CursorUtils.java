package net.opusapp.player.core.service.utils;

import android.database.Cursor;

public class CursorUtils {

    public static boolean ifNotEmpty(final Cursor cursor) {
        if (cursor != null) {
            if (cursor.getCount() <= 0) {
                free(cursor);
                return false;
            }
        }

        return cursor != null;
    }

    public static Cursor free(final Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }

        return null;
    }
}
