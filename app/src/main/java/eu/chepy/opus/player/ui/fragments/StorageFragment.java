/*
 * StorageFragment.java
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
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.PopupMenu;
import android.view.ContextMenu;
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
import eu.chepy.opus.player.ui.adapter.LibraryAdapter;
import eu.chepy.opus.player.ui.adapter.LibraryAdapterFactory;
import eu.chepy.opus.player.ui.utils.MusicConnector;
import eu.chepy.opus.player.ui.utils.PlayerApplication;
import eu.chepy.opus.player.ui.views.CustomLinkTextView;
import eu.chepy.opus.player.ui.views.CustomTextView;

public class StorageFragment extends AbstractRefreshableFragment implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener {

    public static final int FRAGMENT_GROUP_ID = 7;



    /*
        ContentType UI
     */
    private GridView gridView;

    private LibraryAdapter adapter;

    private CustomTextView pathTextView;



    /*
        ContentType Data
     */
    private Cursor cursor;

    private final static int[] requestedFields = new int[] {
            AbstractMediaManager.Provider.STORAGE_ID,
            AbstractMediaManager.Provider.STORAGE_DISPLAY_NAME,
            AbstractMediaManager.Provider.STORAGE_DISPLAY_DETAIL,
            AbstractMediaManager.Provider.SONG_ART,
            AbstractMediaManager.Provider.SONG_VISIBLE
    };

    public static final int COLUMN_STORAGE_ID = 0;

    public static final int COLUMN_STORAGE_DISPLAY_NAME = 1;

    public static final int COLUMN_STORAGE_DISPLAY_DETAIL = 2;

    public static final int COLUMN_STORAGE_THUMBNAIL = 3;

    public static final int COLUMN_STORAGE_VISIBLE = 4;



    @Override
    public void doRefresh() {
        doLocationUpdate();
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();
        setEmptyContentAction(provider.getEmptyContentAction(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_storage, container, false);
        if (rootView != null) {
            gridView = (GridView) rootView.findViewById(R.id.grid_view_base);
            gridView.setEmptyView(rootView.findViewById(R.id.grid_view_empty));
            pathTextView = (CustomTextView) rootView.findViewById(R.id.path);

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
                        return PlayerApplication.storageContextItemSelected(menuItem.getItemId(), cursor.getString(COLUMN_STORAGE_ID), MusicConnector.storage_sort_order, position);
                    }
                };
            }

            @Override
            public void createMenu(Menu menu, int position) {
                cursor.moveToPosition(position);
                doOnCreateContextMenu(menu, position);
            }
        };

        adapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_STORAGE, LibraryAdapter.LIBRARY_MANAGER,
                new int[]{
                        COLUMN_STORAGE_ID,
                        COLUMN_STORAGE_DISPLAY_NAME,
                        COLUMN_STORAGE_DISPLAY_DETAIL,
                        COLUMN_STORAGE_THUMBNAIL,
                        COLUMN_STORAGE_VISIBLE
                }
        );
        gridView.setOnCreateContextMenuListener(this);
        gridView.setOnItemClickListener(this);
        gridView.setAdapter(adapter);
        gridView.setNumColumns(PlayerApplication.getListColumns());

        doLocationUpdate();
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        final int[] sortFields = new int[] { MusicConnector.storage_sort_order };

        return PlayerApplication.buildStorageLoader(
                PlayerApplication.libraryManagerIndex,
                requestedFields,
                sortFields,
                PlayerApplication.lastSearchFilter);
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
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        if (info != null) {
            doOnCreateContextMenu(menu, info.position);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (!getUserVisibleHint() || item.getGroupId() != FRAGMENT_GROUP_ID) {
            return false;
        }

        PlayerApplication.storageContextItemSelected(item.getItemId(), cursor.getString(COLUMN_STORAGE_ID), MusicConnector.storage_sort_order, cursor.getPosition());
        return super.onContextItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        boolean hasChild = (Boolean) provider.getProperty(
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                position,
                AbstractMediaManager.Provider.ContentProperty.CONTENT_STORAGE_HAS_CHILD);

        if (hasChild) {
            provider.setProperty(
                    AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                    position,
                    AbstractMediaManager.Provider.ContentProperty.CONTENT_STORAGE_UPDATE_VIEW,
                    null,
                    null
            );
            doRefresh();
        }
        else {
            PlayerApplication.storageContextItemSelected(PlayerApplication.CONTEXT_MENUITEM_PLAY, cursor.getString(COLUMN_STORAGE_ID), MusicConnector.storage_sort_order, position);
        }
    }

    public boolean handleBackButton() {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        boolean hasParent = (Boolean) provider.getProperty(
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                null,
                AbstractMediaManager.Provider.ContentProperty.CONTENT_STORAGE_HAS_PARENT);

        if (hasParent) {
            provider.setProperty(
                    AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                    0,  // Parent dir is always 0.
                    AbstractMediaManager.Provider.ContentProperty.CONTENT_STORAGE_UPDATE_VIEW,
                    null,
                    null
            );
            doRefresh();
        }

        return hasParent;
    }

    protected void doLocationUpdate() {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        String currentPath = (String) provider.getProperty(
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                null,
                AbstractMediaManager.Provider.ContentProperty.CONTENT_STORAGE_CURRENT_LOCATION);
        pathTextView.setText(currentPath);
    }

    protected void doOnCreateContextMenu(Menu menu, int position) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        boolean hasChild = (Boolean) provider.getProperty(
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                position,
                AbstractMediaManager.Provider.ContentProperty.CONTENT_STORAGE_HAS_CHILD);

        if (!hasChild) {
            PlayerApplication.createStorageContextMenu(menu, FRAGMENT_GROUP_ID);
        }
    }
}
