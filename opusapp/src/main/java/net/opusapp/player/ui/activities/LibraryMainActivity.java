/*
 * LibraryMainActivity.java
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
package net.opusapp.player.ui.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.astuetz.PagerSlidingTabStrip;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.ui.adapter.ux.PagerAdapter;
import net.opusapp.player.ui.fragments.AlbumArtistFragment;
import net.opusapp.player.ui.fragments.AlbumFragment;
import net.opusapp.player.ui.fragments.ArtistFragment;
import net.opusapp.player.ui.fragments.GenreFragment;
import net.opusapp.player.ui.fragments.PlaylistFragment;
import net.opusapp.player.ui.fragments.SongFragment;
import net.opusapp.player.ui.fragments.StorageFragment;
import net.opusapp.player.ui.utils.MusicConnector;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.utils.LogUtils;

import java.io.File;

public class LibraryMainActivity extends AbstractPlayerActivity {

    public static final String TAG = LibraryMainActivity.class.getSimpleName();



    /*

     */
    private static final int DRAWERITEM_SEPARATOR_ID = 0;

    private static final int DRAWERITEM_LIBRARY_SETTINGS_ID = 100;

    private static final int DRAWERITEM_APPLICATION_SETTINGS_ID = 101;

    private static final int DRAWERITEM_AUDIO_FX_ID = 102;



    /*
        Actionbar
     */
    private static final int OPTION_MENUITEM_SORT = 1;

    private static final int OPTION_MENUITEM_FILTER = 2;

    private static final int OPTION_MENUITEM_SHOW_HIDDEN = 3;

    private static final int OPTION_MENUITEM_RELOAD = 4;



    // Actionbar search system
    private SearchView searchView;



    // Actionbar items
    private MenuItem sortMenuItem;

    private MenuItem searchMenuItem;

    private MenuItem reloadMenuItem;



    // Main content
    private ViewPager libraryPager;

    private PagerAdapter libraryAdapter;

    private PagerSlidingTabStrip scrollingTabs;



    // Navigation
    private ArrayAdapter<CollectionDescriptor> navigationAdapter;

    private static final int ID_LIBRARY_MANAGEMENT = -1;



    private boolean doubleBackToExitPressedOnce = false;



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (TextUtils.isEmpty(PlayerApplication.lastSearchFilter)) {
            PlayerApplication.lastSearchFilter = null;
        }

        searchView = new SearchView(getSupportActionBar().getThemedContext());
        searchView.setQueryHint(getString(R.string.searchview_query_hint));
        searchView.setOnQueryTextListener(searchViewOnQueryTextListener);
        searchView.setOnCloseListener(searchViewOnCloseListener);

        sortMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SORT, 2, R.string.menuitem_label_sort);
        sortMenuItem.setIcon(R.drawable.ic_action_sort_2);
        MenuItemCompat.setShowAsAction(sortMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        sortMenuItem.setOnMenuItemClickListener(onSortOptionMenuItemListener);

        searchMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_FILTER, 3, R.string.menuitem_label_filter);
        searchMenuItem.setIcon(R.drawable.ic_action_search);
        MenuItemCompat.setActionView(searchMenuItem, searchView);
        MenuItemCompat.setShowAsAction(searchMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        final MenuItem hiddenMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SHOW_HIDDEN, 4, R.string.menuitem_label_show_hidden);
        hiddenMenuItem.setIcon(R.drawable.ic_action_show);
        hiddenMenuItem.setCheckable(true);
        MenuItemCompat.setShowAsAction(hiddenMenuItem, MenuItemCompat.SHOW_AS_ACTION_NEVER);
        hiddenMenuItem.setOnMenuItemClickListener(hiddenOnMenuItemClickListener);

        doManageMenuitemVisibility(libraryAdapter, libraryPager.getCurrentItem());

        reloadMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_RELOAD, 6, R.string.menuitem_label_reload);
        reloadMenuItem.setIcon(R.drawable.ic_action_reload);
        MenuItemCompat.setShowAsAction(reloadMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        reloadMenuItem.setOnMenuItemClickListener(reloadOnMenuItemClickListener);

        updateReloadMenuItem();

        final MenuItem audioEffectsMenuItem = menu.add(Menu.NONE, DRAWERITEM_AUDIO_FX_ID, 8, R.string.drawer_item_label_library_soundfx);
        audioEffectsMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                startActivity(new Intent(PlayerApplication.context, SoundEffectsActivity.class));
                return true;
            }
        });

        final SubMenu options = menu.addSubMenu(Menu.NONE, 0, 9, R.string.menuitem_label_other_options);

        int menuIndex = 1;

        final AbstractMediaManager.Provider provider = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex].getProvider();
        final AbstractMediaManager.ProviderAction providerActionList[] = provider.getAbstractProviderActionList();

        if (providerActionList != null) {
            for (int index = 0; index < providerActionList.length; index++) {
                final AbstractMediaManager.ProviderAction providerAction = providerActionList[index];

                if (providerAction.isVisible()) {
                    final MenuItem menuItem = options.add(Menu.NONE, 200 + index, menuIndex++, providerAction.getDescription());
                    menuItem.setIcon(R.drawable.ic_action_tiles_large);
                    menuItem.setOnMenuItemClickListener(new ActionMenuItemClickListener(index));
                }
            }
        }

        final MenuItem libraryManagementMenuItem = options.add(Menu.NONE, DRAWERITEM_LIBRARY_SETTINGS_ID, menuIndex++, R.string.drawer_item_label_manage_libraries);
        libraryManagementMenuItem.setIcon(R.drawable.ic_action_database);
        libraryManagementMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                doLibraryManagement();
                return true;
            }
        });

        final MenuItem settingsMenuItem = options.add(Menu.NONE, DRAWERITEM_APPLICATION_SETTINGS_ID, menuIndex, R.string.drawer_item_label_application_settings);
        settingsMenuItem.setIcon(R.drawable.ic_action_settings);
        settingsMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        return item.getGroupId() == Menu.NONE && super.onContextItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.activity_library_main, new int[] { Window.FEATURE_INDETERMINATE_PROGRESS });

        libraryPager = (ViewPager) findViewById(R.id.pager_viewpager);
        libraryPager.setPageMargin(getResources().getInteger(R.integer.viewpager_margin_width));

        scrollingTabs = (PagerSlidingTabStrip) findViewById(R.id.pager_tabs);
        scrollingTabs.setOnPageChangeListener(scrollingTabsOnPageChangeListener);
        scrollingTabs.setIndicatorColorResource(R.color.view_scrollingtabs_color);


        final Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        doInitLibrary();
        doInitLibraryContent();

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if(Intent.ACTION_VIEW.equals(action)){
            for (int index = 0 ; index < PlayerApplication.mediaManagers.length ; index++) {
                final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[index];

                if (mediaManager.getMediaManagerType() == AbstractMediaManager.LOCAL_MEDIA_MANAGER) {
                    if (PlayerApplication.libraryManagerIndex != PlayerApplication.playerManagerIndex) {
                        MusicConnector.doStopAction();
                        PlayerApplication.playerManagerIndex = PlayerApplication.libraryManagerIndex;
                        PlayerApplication.saveLibraryIndexes();

                        try {
                            PlayerApplication.playerService.queueReload();
                        } catch (final RemoteException remoteException) {
                            LogUtils.LOGException(TAG, "doReloadServiceState", 0, remoteException);
                        }
                    }

                    final Uri dataUri = intent.getData();
                    String path = dataUri.getPath();
                    final File pathFile = new File(path);
                    if (!pathFile.isDirectory()) {
                        path = pathFile.getParent();
                    }

                    mediaManager.getProvider().setProperty(
                            AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                            path,
                            AbstractMediaManager.Provider.ContentProperty.CONTENT_STORAGE_CURRENT_LOCATION,
                            null,
                            null);
                    int position = (Integer) mediaManager.getProvider().getProperty(
                            AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                            dataUri.getPath(),
                            AbstractMediaManager.Provider.ContentProperty.CONTENT_STORAGE_RESOURCE_POSITION);

                    libraryAdapter.doRefresh();

                    MusicConnector.doContextActionPlay(
                            AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                            String.valueOf(position),
                            MusicConnector.storage_sort_order,
                            position);

                    getSlidingPanel().expandPanel();
                }
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        getSupportLoaderManager().destroyLoader(0);

        for (AbstractMediaManager mediaManager : PlayerApplication.mediaManagers) {
            mediaManager.getProvider().removeLibraryChangeListener(libraryChangeListener);
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        int position = libraryPager.getCurrentItem();
        final Object currentFragment = libraryAdapter.getItem(position);
        if (currentFragment instanceof StorageFragment) {
            if (!((StorageFragment) currentFragment).handleBackButton()) {
                if (doubleBackToExitPressedOnce) {
                    super.onBackPressed();
                    return;
                }

                doubleBackToExitPressedOnce = true;
                Toast.makeText(PlayerApplication.context, R.string.toast_press_back_again, Toast.LENGTH_SHORT).show();

                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        doubleBackToExitPressedOnce = false;
                    }
                }, 2000);
            }
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onSearchRequested() {
        final SupportMenuItem search = (SupportMenuItem)searchMenuItem;
        search.expandActionView();
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case DRAWERITEM_LIBRARY_SETTINGS_ID:
            case AbstractMediaManager.Provider.ACTIVITY_NEED_UI_REFRESH:
                doInitLibrary();
                break;
        }
    }

    public void doRefresh() {
        if (libraryAdapter != null) {
            libraryAdapter.doRefresh();
        }
    }

    protected void updateReloadMenuItem() {
        if (reloadMenuItem != null) {
            if (PlayerApplication.mediaManagers[PlayerApplication.getLibraryLibraryIndex()].getProvider().scanIsRunning()) {
                reloadMenuItem.setTitle(R.string.menuitem_label_cancel_reload);
                reloadMenuItem.setIcon(R.drawable.ic_action_cancel);
            } else {
                reloadMenuItem.setTitle(R.string.menuitem_label_reload);
                reloadMenuItem.setIcon(R.drawable.ic_action_reload);
            }
        }
    }

    // SearchView listener
    private final SearchView.OnQueryTextListener searchViewOnQueryTextListener = new SearchView.OnQueryTextListener() {

        @Override
        public boolean onQueryTextChange(String searchText) {
            PlayerApplication.lastSearchFilter = searchText;
            libraryAdapter.doRefresh();
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            final InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
            return true;
        }
    };

    private final SearchView.OnCloseListener searchViewOnCloseListener = new SearchView.OnCloseListener() {

        @Override
        public boolean onClose() {
            searchView.setQuery(null, true);
            libraryAdapter.doRefresh();
            return false;
        }
    };

    //    ScrollingTabs listener
    private final ViewPager.OnPageChangeListener scrollingTabsOnPageChangeListener = new ViewPager.SimpleOnPageChangeListener() {

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            if (sortMenuItem != null /*&& recentMenuItem != null*/) {
                doManageMenuitemVisibility(libraryAdapter, position);
            }
        }

    };

    //    Hidden Menuitem click listener
    private final MenuItem.OnMenuItemClickListener hiddenOnMenuItemClickListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            boolean show_hidden = !item.isChecked();

            item.setChecked(show_hidden);
            MusicConnector.show_hidden = show_hidden;
            libraryAdapter.doRefresh();
            return true;
        }
    };



    private final MenuItem.OnMenuItemClickListener reloadOnMenuItemClickListener = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            if (PlayerApplication.mediaManagers[PlayerApplication.getLibraryLibraryIndex()].getProvider().scanIsRunning()) {
                PlayerApplication.mediaManagers[PlayerApplication.getLibraryLibraryIndex()].getProvider().scanCancel();
            }
            else {
                PlayerApplication.mediaManagers[PlayerApplication.getLibraryLibraryIndex()].getProvider().scanStart();
            }
            return true;
        }
    };

    private final MenuItem.OnMenuItemClickListener onSortOptionMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(LibraryMainActivity.this);

            final Class<?> currentItemClass = ((Object)libraryAdapter.getItem(libraryPager.getCurrentItem())).getClass();

            if (currentItemClass.equals(PlaylistFragment.class)) {
                int sortIndex = 0;
                switch (MusicConnector.playlists_sort_order) {
                    case +AbstractMediaManager.Provider.PLAYLIST_NAME: sortIndex = 0;  break;
                    case -AbstractMediaManager.Provider.PLAYLIST_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_playlists, sortIndex, playlistFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(ArtistFragment.class)) {
                int sortIndex = 0;
                switch (MusicConnector.artists_sort_order) {
                    case +AbstractMediaManager.Provider.ARTIST_NAME: sortIndex = 0;  break;
                    case -AbstractMediaManager.Provider.ARTIST_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_artists, sortIndex, artistFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(AlbumArtistFragment.class)) {
                int sortIndex = 0;
                switch (MusicConnector.album_artists_sort_order) {
                    case +AbstractMediaManager.Provider.ALBUM_ARTIST_NAME: sortIndex = 0;  break;
                    case -AbstractMediaManager.Provider.ALBUM_ARTIST_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_album_artists, sortIndex, albumArtistFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(AlbumFragment.class)) {
                int sortIndex = 0;
                switch (MusicConnector.albums_sort_order) {
                    case +AbstractMediaManager.Provider.ALBUM_NAME:   sortIndex = 0;  break;
                    case -AbstractMediaManager.Provider.ALBUM_NAME:   sortIndex = 1;  break;
                    case +AbstractMediaManager.Provider.ALBUM_ARTIST: sortIndex = 2;  break;
                    case -AbstractMediaManager.Provider.ALBUM_ARTIST: sortIndex = 3;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_albums, sortIndex, albumFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(SongFragment.class)) {
                int sortIndex = 0; // case MusicConnector.SORT_A_Z
                switch (MusicConnector.songs_sort_order) {
                    case +AbstractMediaManager.Provider.SONG_TITLE:   sortIndex = 0;  break;
                    case -AbstractMediaManager.Provider.SONG_TITLE:   sortIndex = 1;  break;
                    case +AbstractMediaManager.Provider.SONG_TRACK:   sortIndex = 2;  break;
                    case -AbstractMediaManager.Provider.SONG_TRACK:   sortIndex = 3;  break;
                    case +AbstractMediaManager.Provider.SONG_URI:     sortIndex = 4;  break;
                    case -AbstractMediaManager.Provider.SONG_URI:     sortIndex = 5;  break;
                    case +AbstractMediaManager.Provider.SONG_ARTIST:  sortIndex = 6;  break;
                    case -AbstractMediaManager.Provider.SONG_ARTIST:  sortIndex = 7;  break;
                    case +AbstractMediaManager.Provider.SONG_ALBUM:   sortIndex = 8;  break;
                    case -AbstractMediaManager.Provider.SONG_ALBUM:   sortIndex = 9; break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_songs, sortIndex, songFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(GenreFragment.class)) {
                int sortIndex = 0;
                switch (MusicConnector.genres_sort_order) {
                    case +AbstractMediaManager.Provider.GENRE_NAME: sortIndex = 0;  break;
                    case -AbstractMediaManager.Provider.GENRE_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_genre, sortIndex, genreFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(StorageFragment.class)) {
                int sortIndex = 0; // case MusicConnector.SORT_A_Z
                switch (MusicConnector.storage_sort_order) {
                    case AbstractMediaManager.Provider.STORAGE_DISPLAY_NAME:               sortIndex = 0;  break;
                    case -AbstractMediaManager.Provider.STORAGE_DISPLAY_NAME:              sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_files, sortIndex, storageFragmentAlertDialogClickListener);
            }

            alertDialogBuilder.show();
            return true;
        }
    };

    private DialogInterface.OnClickListener playlistFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.playlists_sort_order = AbstractMediaManager.Provider.PLAYLIST_NAME; break;
                case 1:  MusicConnector.playlists_sort_order = -AbstractMediaManager.Provider.PLAYLIST_NAME; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener artistFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.artists_sort_order = AbstractMediaManager.Provider.ARTIST_NAME; break;
                case 1:  MusicConnector.artists_sort_order = -AbstractMediaManager.Provider.ARTIST_NAME; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener albumArtistFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.album_artists_sort_order = AbstractMediaManager.Provider.ALBUM_ARTIST_NAME; break;
                case 1:  MusicConnector.album_artists_sort_order = -AbstractMediaManager.Provider.ALBUM_ARTIST_NAME; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener albumFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.albums_sort_order = AbstractMediaManager.Provider.ALBUM_NAME; break;
                case 1:  MusicConnector.albums_sort_order = -AbstractMediaManager.Provider.ALBUM_NAME;  break;
                case 2:  MusicConnector.albums_sort_order = AbstractMediaManager.Provider.ALBUM_ARTIST;  break;
                case 3:  MusicConnector.albums_sort_order = -AbstractMediaManager.Provider.ALBUM_ARTIST; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener songFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.songs_sort_order = AbstractMediaManager.Provider.SONG_TITLE; break;
                case 1:  MusicConnector.songs_sort_order = -AbstractMediaManager.Provider.SONG_TITLE; break;
                case 2:  MusicConnector.songs_sort_order = AbstractMediaManager.Provider.SONG_TRACK; break;
                case 3:  MusicConnector.songs_sort_order = -AbstractMediaManager.Provider.SONG_TRACK; break;
                case 4:  MusicConnector.songs_sort_order = AbstractMediaManager.Provider.SONG_URI; break;
                case 5:  MusicConnector.songs_sort_order = -AbstractMediaManager.Provider.SONG_URI; break;
                case 6:  MusicConnector.songs_sort_order = AbstractMediaManager.Provider.SONG_ARTIST; break;
                case 7:  MusicConnector.songs_sort_order = -AbstractMediaManager.Provider.SONG_ARTIST; break;
                case 8:  MusicConnector.songs_sort_order = AbstractMediaManager.Provider.SONG_ALBUM; break;
                case 9:  MusicConnector.songs_sort_order = -AbstractMediaManager.Provider.SONG_ALBUM; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener genreFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.genres_sort_order = AbstractMediaManager.Provider.GENRE_NAME; break;
                case 1:  MusicConnector.genres_sort_order = -AbstractMediaManager.Provider.GENRE_NAME; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener storageFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.storage_sort_order = AbstractMediaManager.Provider.STORAGE_DISPLAY_NAME; break;
                case 1:  MusicConnector.storage_sort_order = -AbstractMediaManager.Provider.STORAGE_DISPLAY_NAME; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private ActionBar.OnNavigationListener collectionOnNavigationListener = new ActionBar.OnNavigationListener() {

        @Override
        public boolean onNavigationItemSelected(int itemPosition, long itemId) {
            itemId = navigationAdapter.getItem(itemPosition).id;

            if (itemId == ID_LIBRARY_MANAGEMENT) {
                doLibraryManagement();
                getSupportActionBar().setSelectedNavigationItem(0);
            }
            else {
                for (int searchIndex = 0 ; searchIndex < PlayerApplication.mediaManagers.length ; searchIndex++) {
                    int currentId = PlayerApplication.mediaManagers[searchIndex].getMediaManagerId();

                    if (currentId == itemId) {
                        itemId = searchIndex;
                        break;
                    }
                }

                PlayerApplication.libraryManagerIndex = (int) itemId;
                doInitLibraryContent();
                PlayerApplication.saveLibraryIndexes();
            }

            return false;
        }
    };

    private AbstractMediaManager.Provider.OnLibraryChangeListener libraryChangeListener = new AbstractMediaManager.Provider.OnLibraryChangeListener() {

        @Override
        public void libraryChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    libraryAdapter.doRefresh();
                }
            });
        }

        @Override
        public void libraryScanStarted() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateReloadMenuItem();
                }
            });
        }

        @Override
        public void libraryScanFinished() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateReloadMenuItem();
                }
            });
        }
    };


    protected void initLibraryView() {
        libraryAdapter = new PagerAdapter(getApplicationContext(), getSupportFragmentManager());

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        // Only show tabs that were set in preferences
        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST)) {
            libraryAdapter.addFragment(new PlaylistFragment(), null, R.string.tab_label_playlists);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST)) {
            libraryAdapter.addFragment(new ArtistFragment(), null, R.string.tab_label_artists);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST)) {
            libraryAdapter.addFragment(new AlbumArtistFragment(), null, R.string.tab_label_album_artists);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM)) {
            libraryAdapter.addFragment(new AlbumFragment(), null, R.string.tab_label_albums);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA)) {
            libraryAdapter.addFragment(new SongFragment(), null, R.string.tab_label_songs);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE)) {
            libraryAdapter.addFragment(new GenreFragment(), null, R.string.tab_label_genres);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE)) {
            libraryAdapter.addFragment(new StorageFragment(), null, R.string.tab_label_storage);
        }

        libraryPager.setAdapter(libraryAdapter);
        // libraryPager.setOffscreenPageLimit(libraryAdapter.getCount());
        scrollingTabs.setViewPager(libraryPager);
    }


    protected void doInitLibrary() {
        CollectionDescriptor collections[];

        SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getReadableDatabase();

        if (database != null) {
            Cursor cursor = database.query(
                    Entities.Provider.TABLE_NAME,
                    new String[]{
                            Entities.Provider._ID,
                            Entities.Provider.COLUMN_FIELD_PROVIDER_NAME
                    },
                    null, null, null, null, Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION);

            final int COLUMN_PROVIDER_ID = 0;

            final int COLUMN_PROVIDER_NAME = 1;

            if (cursor != null && cursor.getCount() > 0) {
                collections = new CollectionDescriptor[cursor.getCount() + 1];

                int collectionIndex = 0;
                while (cursor.moveToNext()) {
                    collections[collectionIndex] = new CollectionDescriptor(
                            cursor.getInt(COLUMN_PROVIDER_ID),
                            cursor.getString(COLUMN_PROVIDER_NAME)
                    );
                    collectionIndex++;
                }

                cursor.close();
            } else {
                collections = new CollectionDescriptor[1];
            }
        }
        else {
            collections = new CollectionDescriptor[1];
        }
        collections[collections.length - 1] = new CollectionDescriptor(ID_LIBRARY_MANAGEMENT, getString(R.string.drawer_item_label_manage_libraries));

        navigationAdapter = new ArrayAdapter<CollectionDescriptor>(getSupportActionBar().getThemedContext(), R.layout.support_simple_spinner_dropdown_item, collections);
        navigationAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);

        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        getSupportActionBar().setListNavigationCallbacks(navigationAdapter, collectionOnNavigationListener);
        getSupportActionBar().setSelectedNavigationItem(PlayerApplication.libraryManagerIndex);
    }

    protected void doInitLibraryContent() {
        initLibraryView();
        final AbstractMediaManager.Provider provider = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex].getProvider();

        //    Launching scan.
        for (AbstractMediaManager mediaManager : PlayerApplication.mediaManagers) {
            mediaManager.getProvider().addLibraryChangeListener(libraryChangeListener);
        }

        if (provider.scanIsRunning()) {
            libraryChangeListener.libraryScanStarted();
        }
        else {
            provider.scanStart();
        }
    }

    protected  void doLibraryManagement() {
        startActivityForResult(new Intent(PlayerApplication.context, SettingsLibrariesActivity.class), DRAWERITEM_LIBRARY_SETTINGS_ID);
    }

    protected void doManageMenuitemVisibility(PagerAdapter pagerAdapter, int position) {
        final Class<?> itemClass = ((Object)pagerAdapter.getItem(position)).getClass();

        if (itemClass.equals(PlaylistFragment.class)) {
            sortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(ArtistFragment.class)) {
            sortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(AlbumArtistFragment.class)) {
            sortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(AlbumFragment.class)) {
            sortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(SongFragment.class)) {
            sortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(GenreFragment.class)) {
            sortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(StorageFragment.class)) {
            sortMenuItem.setVisible(true);
        }
        else {
            sortMenuItem.setVisible(false);
        }
    }



    class CollectionDescriptor {

        public int id;

        public String description;

        CollectionDescriptor(int collectionId, String collectionDescription) {
            id = collectionId;
            description = collectionDescription;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    class ActionMenuItemClickListener implements MenuItem.OnMenuItemClickListener {

        private final int index;

        public ActionMenuItemClickListener(int index) {
            this.index = index;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final AbstractMediaManager.Provider provider = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex].getProvider();
            final AbstractMediaManager.ProviderAction providerAction = provider.getAbstractProviderAction(index);
            if (providerAction != null) {
                providerAction.launch(LibraryMainActivity.this);
            }

            return true;
        }
    }

}
