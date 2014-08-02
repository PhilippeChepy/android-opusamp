/*
 * CollectionRecentFragment.java
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
package eu.chepy.audiokit.ui.fragments;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.util.Log;

import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.ui.utils.MusicConnector;
import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class RecentFragment extends SongFragment {

    private static final String TAG = RecentFragment.class.getSimpleName();

    public static final int FRAGMENT_GROUP_ID = 2;



    /*
        ContentType Data
     */
    private final static int[] requestedFields = new int[] {
            AbstractMediaProvider.SONG_ID,
            AbstractMediaProvider.SONG_TITLE,
            AbstractMediaProvider.SONG_ARTIST,
            AbstractMediaProvider.SONG_VISIBLE,
    };



    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        Log.d(TAG, "onCreateLoader()");
        final int[] sortFields = new int[] { MusicConnector.songs_sort_order };

        return PlayerApplication.buildMediaLoader(PlayerApplication.libraryManagerIndex,
                requestedFields, sortFields, PlayerApplication.lastSearchFilter, AbstractMediaProvider.ContentType.CONTENT_TYPE_DEFAULT, null);
    }
}
