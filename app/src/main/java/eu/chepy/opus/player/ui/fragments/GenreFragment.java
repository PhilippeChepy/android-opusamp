/*
 * GenreFragment.java
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
package eu.chepy.opus.player.ui.fragments;

import android.app.Activity;
import android.content.Intent;
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

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;
import eu.chepy.opus.player.ui.activities.LibraryDetailActivity;
import eu.chepy.opus.player.ui.adapter.LibraryAdapter;
import eu.chepy.opus.player.ui.adapter.LibraryAdapterFactory;
import eu.chepy.opus.player.ui.utils.MusicConnector;
import eu.chepy.opus.player.ui.utils.PlayerApplication;
import eu.chepy.opus.player.ui.views.CustomLinkTextView;
import eu.chepy.opus.player.ui.views.CustomTextView;

public class GenreFragment extends AbstractRefreshableFragment implements LoaderCallbacks<Cursor>, OnItemClickListener {

    public static final String TAG = GenreFragment.class.getSimpleName();

    public static final int FRAGMENT_GROUP_ID = 4;



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
            AbstractMediaManager.Provider.GENRE_ID,
            AbstractMediaManager.Provider.GENRE_NAME,
            AbstractMediaManager.Provider.GENRE_VISIBLE
    };

    public static final int COLUMN_GENRE_ID = 0;

    public static final int COLUMN_GENRE_NAME = 1;

    public static final int COLUMN_GENRE_VISIBLE = 2;



	@Override
	public void doRefresh() {
        getLoaderManager().restartLoader(0, null, this);
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();
        setEmptyContentAction(provider.getEmptyContentAction(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE));
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
                    return PlayerApplication.genreContextItemSelected(getActivity(), menuItem.getItemId(), cursor.getString(COLUMN_GENRE_ID), MusicConnector.genres_sort_order, 0);
                    }
                };
            }

            @Override
            public void createMenu(Menu menu, int position) {
                cursor.moveToPosition(position);
                PlayerApplication.createGenreContextMenu(menu, FRAGMENT_GROUP_ID, cursor.getInt(COLUMN_GENRE_VISIBLE) == 1);
            }
        };

        adapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_GENRE, LibraryAdapter.LIBRARY_MANAGER,
                new int[] {
                        COLUMN_GENRE_ID,
                        COLUMN_GENRE_NAME,
                        COLUMN_GENRE_VISIBLE
                });
		gridView.setOnCreateContextMenuListener(this);
		gridView.setOnItemClickListener(this);
		gridView.setAdapter(adapter);
		gridView.setNumColumns(PlayerApplication.getListColumns());

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final int[] sortFields = new int[] { MusicConnector.genres_sort_order };

        return PlayerApplication.buildGenreLoader(PlayerApplication.libraryManagerIndex, requestedFields, sortFields, PlayerApplication.lastSearchFilter);
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
        if (adapter != null) {
            adapter.changeCursor(null);
        }
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        PlayerApplication.createGenreContextMenu(menu, FRAGMENT_GROUP_ID, cursor.getInt(COLUMN_GENRE_VISIBLE) == 1);
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

        PlayerApplication.genreContextItemSelected(getActivity(), item.getItemId(), cursor.getString(COLUMN_GENRE_ID), MusicConnector.genres_sort_order, 0);
        return super.onContextItemSelected(item);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		Intent intent = new Intent(PlayerApplication.context, LibraryDetailActivity.class);
        cursor.moveToPosition(position);

        intent.putExtra(PlayerApplication.CONTENT_TYPE_KEY, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE);
        intent.putExtra(PlayerApplication.CONTENT_SOURCE_ID_KEY, cursor.getString(COLUMN_GENRE_ID));
		startActivity(intent);
	}
}
