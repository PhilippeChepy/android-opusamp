package net.opusapp.player.core.service.utils;

import android.database.Cursor;

public class CursorUtils {

    public static boolean validCursor(final Cursor cursor) {
        if (cursor != null) {
            if (cursor.getCount() <= 0) {
                freeCursor(cursor);
                return false;
            }
        }

        return true;
    }

    public static Cursor freeCursor(final Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }

        return null;
    }
}
