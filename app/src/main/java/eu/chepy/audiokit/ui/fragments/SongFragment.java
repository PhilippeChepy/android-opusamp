/*
 * CollectionSongFragment.java
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

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.ui.adapter.LibraryAdapter;
import eu.chepy.audiokit.ui.adapter.LibraryAdapterFactory;
import eu.chepy.audiokit.ui.utils.MusicConnector;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.ui.views.CustomLinkTextView;
import eu.chepy.audiokit.ui.views.CustomTextView;

public class SongFragment extends AbstractRefreshableFragment implements LoaderCallbacks<Cursor>, OnItemClickListener {

	private static final String TAG = SongFragment.class.getSimpleName();

    public static final int FRAGMENT_GROUP_ID = 1;



    /*
        ContentType UI
     */
	private GridView gridView;

    private LibraryAdapter adapter;



    /*
        ContentType Data
     */
    private Cursor cursor;

    private final static int[] requestedFields = new int[] {
            AbstractMediaProvider.SONG_ID,
            AbstractMediaProvider.SONG_TITLE,
            AbstractMediaProvider.SONG_ARTIST,
            AbstractMediaProvider.SONG_VISIBLE,
    };

    public static final int COLUMN_SONG_ID = 0;

    public static final int COLUMN_SONG_TITLE = 1;

    public static final int COLUMN_SONG_ARTIST = 2;

    public static final int COLUMN_SONG_VISIBLE = 3;



    @Override
	public void doRefresh() {
		Log.d(TAG, "doRefresh()");
        getLoaderManager().restartLoader(0, null, this);
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();
        setEmptyContentAction(mediaProvider.getEmptyContentAction(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA));
    }
        
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.d(TAG, "onCreateView()");
		
		final View rootView = inflater.inflate(R.layout.view_list_gridview, container, false);
        if (rootView != null) {
            gridView = (GridView) rootView.findViewById(R.id.grid_view_base);
            gridView.setEmptyView(rootView.findViewById(R.id.grid_view_empty));

            final CustomTextView emptyDescription = (CustomTextView) rootView.findViewById(R.id.empty_description);
            final CustomLinkTextView emptyAction = (CustomLinkTextView) rootView.findViewById(R.id.empty_action);
            setEmptyAction(emptyDescription, emptyAction);
        }

        return rootView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		Log.d(TAG, "onActivityCreated()");
        super.onActivityCreated(savedInstanceState);

        final Activity activity = getActivity();
        adapter = LibraryAdapterFactory.build(activity, LibraryAdapterFactory.ADAPTER_SONG_SIMPLE, LibraryAdapter.LIBRARY_MANAGER,
                new int[] {
                        COLUMN_SONG_ID,
                        COLUMN_SONG_TITLE,
                        COLUMN_SONG_ARTIST,
                        COLUMN_SONG_VISIBLE
                });
        gridView.setOnCreateContextMenuListener(this);
        gridView.setOnItemClickListener(this);
        gridView.setAdapter(adapter);
        gridView.setNumColumns(PlayerApplication.getListColumns());

        getLoaderManager().initLoader(0, null, this);
	}
	
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
		Log.d(TAG, "onCreateLoader()");
        final int[] sortFields = new int[] { MusicConnector.songs_sort_order };

        return PlayerApplication.buildMediaLoader(
                PlayerApplication.libraryManagerIndex,
                requestedFields,
                sortFields,
                PlayerApplication.lastSearchFilter,
                AbstractMediaProvider.ContentType.CONTENT_TYPE_DEFAULT, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		Log.d(TAG, "onLoadFinished()");
		
        if (data == null) {
            return;
        }

        adapter.changeCursor(data);
        gridView.invalidateViews();
        cursor = data;
	}
	
	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		Log.d(TAG, "onLoaderReset()");
		
        if (this.adapter != null) {
        	this.adapter.changeCursor(null);
        }
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		Log.d(TAG, "onCreateContextMenu()");
		
        PlayerApplication.createSongContextMenu(menu, FRAGMENT_GROUP_ID, cursor.getInt(COLUMN_SONG_VISIBLE) == 1);
		super.onCreateContextMenu(menu, v, menuInfo);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (!getUserVisibleHint()) {
			return false;
		}

        if (item.getGroupId() != FRAGMENT_GROUP_ID) {
            return false;
        }
		
		Log.d(TAG, "onContextItemSelected()");
        PlayerApplication.songContextItemSelected(getActivity(), item.getItemId(), cursor.getString(COLUMN_SONG_ID), cursor.getPosition());
		return super.onContextItemSelected(item);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		Log.d(TAG, "onItemClick()");

        PlayerApplication.songContextItemSelected(getActivity(), PlayerApplication.CONTEXT_MENUITEM_PLAY, cursor.getString(COLUMN_SONG_ID), cursor.getPosition());
	}
}
