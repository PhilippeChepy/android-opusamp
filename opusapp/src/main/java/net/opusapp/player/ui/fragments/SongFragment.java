/*
 * SongFragment.java
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
package net.opusapp.player.ui.fragments;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.widget.PopupMenu;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.ui.adapter.LibraryAdapter;
import net.opusapp.player.ui.adapter.LibraryAdapterFactory;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.CustomLinkTextView;
import net.opusapp.player.ui.views.CustomTextView;

public class SongFragment extends AbstractRefreshableFragment implements LoaderCallbacks<Cursor>, OnItemClickListener {

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
            MediaManager.Provider.SONG_ID,
            MediaManager.Provider.SONG_TITLE,
            MediaManager.Provider.SONG_ARTIST,
            MediaManager.Provider.SONG_TRACK,
            MediaManager.Provider.SONG_ART_URI,
            MediaManager.Provider.SONG_VISIBLE,
    };

    public static final int COLUMN_SONG_ID = 0;

    public static final int COLUMN_SONG_TITLE = 1;

    public static final int COLUMN_SONG_ARTIST = 2;

    public static final int COLUMN_SONG_TRACK_NUMBER = 3;

    public static final int COLUMN_SONG_ART_URI = 4;

    public static final int COLUMN_SONG_VISIBLE = 5;




    @Override
	public void refresh() {
        getLoaderManager().restartLoader(0, null, this);
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();
        setEmptyContentAction(provider.getEmptyContentAction(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA));
    }
        
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        super.onActivityCreated(savedInstanceState);

        final Activity hostActivity = getActivity();
        final LibraryAdapter.LibraryAdapterContainer container = new LibraryAdapter.LibraryAdapterContainer() {
            @Override
            public Activity getActivity() {
                return hostActivity;
            }

            @Override
            public PopupMenu.OnMenuItemClickListener getOnPopupMenuItemClickListener(final int position) {
                return new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        cursor.moveToPosition(position);
                        return PlayerApplication.songContextItemSelected(getActivity(), menuItem.getItemId(), cursor.getString(COLUMN_SONG_ID), cursor.getPosition());
                    }
                };
            }

            @Override
            public void createMenu(Menu menu, int position) {
                cursor.moveToPosition(position);
                PlayerApplication.createSongContextMenu(menu, FRAGMENT_GROUP_ID, cursor.getInt(COLUMN_SONG_VISIBLE) == 1);
            }
        };

        adapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_SONG, LibraryAdapter.LIBRARY_MANAGER,
                new int[]{
                        COLUMN_SONG_ID,
                        COLUMN_SONG_TITLE,
                        COLUMN_SONG_ARTIST,
                        COLUMN_SONG_TRACK_NUMBER,
                        COLUMN_SONG_ART_URI,
                        COLUMN_SONG_VISIBLE
                });
/*
        adapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_SONG_SIMPLE, LibraryAdapter.LIBRARY_MANAGER,
                new int[] {
                        COLUMN_SONG_ID,
                        COLUMN_SONG_TITLE,
                        COLUMN_SONG_ARTIST,
                        COLUMN_SONG_TRACK_NUMBER,
                        COLUMN_SONG_VISIBLE
                });*/
        gridView.setOnCreateContextMenuListener(this);
        gridView.setOnItemClickListener(this);
        gridView.setAdapter(adapter);
        gridView.setNumColumns(PlayerApplication.getListColumns());

        getLoaderManager().initLoader(0, null, this);
	}
	
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        final int[] sortFields = new int[] { PlayerApplication.library_songs_sort_order};

        return PlayerApplication.buildMediaLoader(
                PlayerApplication.libraryMediaManager().getProvider(),
                requestedFields,
                sortFields,
                PlayerApplication.lastSearchFilter,
                MediaManager.Provider.ContentType.CONTENT_TYPE_DEFAULT, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data == null) {
            return;
        }

        adapter.changeCursor(data);
        gridView.invalidateViews();
        cursor = data;
	}
	
	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
        if (this.adapter != null) {
        	this.adapter.changeCursor(null);
        }
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
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

        PlayerApplication.songContextItemSelected(getActivity(), item.getItemId(), cursor.getString(COLUMN_SONG_ID), cursor.getPosition());
		return super.onContextItemSelected(item);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        PlayerApplication.songContextItemSelected(getActivity(), PlayerApplication.CONTEXT_MENUITEM_PLAY, cursor.getString(COLUMN_SONG_ID), cursor.getPosition());
	}
}
