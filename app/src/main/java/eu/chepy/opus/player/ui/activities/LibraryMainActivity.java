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
package eu.chepy.opus.player.ui.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.astuetz.PagerSlidingTabStrip;

import java.io.File;
import java.util.ArrayList;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;
import eu.chepy.opus.player.core.service.providers.AbstractMediaProvider;
import eu.chepy.opus.player.core.service.providers.AbstractMediaProvider.ContentType;
import eu.chepy.opus.player.core.service.providers.AbstractProviderAction;
import eu.chepy.opus.player.core.service.providers.index.database.Entities;
import eu.chepy.opus.player.ui.adapter.ux.NavigationDrawerAdapter;
import eu.chepy.opus.player.ui.adapter.ux.PagerAdapter;
import eu.chepy.opus.player.ui.drawer.AbstractNavigationDrawerItem;
import eu.chepy.opus.player.ui.drawer.NavigationMenuItem;
import eu.chepy.opus.player.ui.drawer.NavigationMenuSection;
import eu.chepy.opus.player.ui.fragments.AlbumArtistFragment;
import eu.chepy.opus.player.ui.fragments.AlbumFragment;
import eu.chepy.opus.player.ui.fragments.ArtistFragment;
import eu.chepy.opus.player.ui.fragments.GenreFragment;
import eu.chepy.opus.player.ui.fragments.PlaylistFragment;
import eu.chepy.opus.player.ui.fragments.SongFragment;
import eu.chepy.opus.player.ui.fragments.StorageFragment;
import eu.chepy.opus.player.ui.utils.MusicConnector;
import eu.chepy.opus.player.ui.utils.PlayerApplication;
import eu.chepy.opus.player.utils.LogUtils;

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



    /*
        Actionbar search system
     */
    private SearchView searchView = null;



    /*
        Actionbar items
     */
    private MenuItem sortMenuItem = null;

    //private MenuItem recentMenuItem = null;



    /*
        Main content
     */
    private ViewPager libraryPager;

    private PagerAdapter libraryAdapter;

    private PagerSlidingTabStrip scrollingTabs;



    /*
        Navigation
     */
    private ArrayAdapter<CollectionDescriptor> navigationAdapter;

    private static final int ID_LIBRARY_MANAGEMENT = -1;



    /*
        Drawer
     */
    private DrawerLayout drawerLayout;

    private ListView drawerList;

    private ActionBarDrawerToggle drawerToggle;



    /*
        UI/UX vars
     */
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
        sortMenuItem.setIcon(R.drawable.ic_action_sort_2_dark);
        MenuItemCompat.setShowAsAction(sortMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        sortMenuItem.setOnMenuItemClickListener(onSortOptionMenuItemListener);

        final MenuItem searchMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_FILTER, 3, R.string.menuitem_label_filter);
        searchMenuItem.setIcon(R.drawable.ic_action_search_dark);
        MenuItemCompat.setActionView(searchMenuItem, searchView);
        MenuItemCompat.setShowAsAction(searchMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        final MenuItem hiddenMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SHOW_HIDDEN, 4, R.string.menuitem_label_show_hidden);
        hiddenMenuItem.setIcon(R.drawable.ic_action_show_dark);
        hiddenMenuItem.setCheckable(true);
        MenuItemCompat.setShowAsAction(hiddenMenuItem, MenuItemCompat.SHOW_AS_ACTION_NEVER);
        hiddenMenuItem.setOnMenuItemClickListener(hiddenOnMenuItemClickListener);

        doManageMenuitemVisibility(libraryAdapter, libraryPager.getCurrentItem());

        final MenuItem reloadMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_RELOAD, 6, R.string.menuitem_label_reload);
        reloadMenuItem.setIcon(R.drawable.ic_action_reload);
        MenuItemCompat.setShowAsAction(reloadMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        reloadMenuItem.setOnMenuItemClickListener(reloadOnMenuItemClickListener);

        return true;
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        return item.getGroupId() == Menu.NONE && getPlayerView().onContextItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_library_main);

        initPlayerView(savedInstanceState);

        libraryPager = (ViewPager) findViewById(R.id.pager_viewpager);
        libraryPager.setPageMargin(getResources().getInteger(R.integer.viewpager_margin_width));

        scrollingTabs = (PagerSlidingTabStrip) findViewById(R.id.pager_tabs);
        scrollingTabs.setOnPageChangeListener(scrollingTabsOnPageChangeListener);
        scrollingTabs.setIndicatorColorResource(R.color.view_scrollingtabs_color);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        drawerList = (ListView) findViewById(R.id.list_drawer);
        drawerList.setOnItemClickListener(new DrawerItemClickListener());

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.drawable.ic_drawer, R.string.actionbar_drawer_toggle_label_open, R.string.actionbar_drawer_toggle_label_close) {
            public void onDrawerClosed(View view) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
                super.onDrawerClosed(view);
            }

            public void onDrawerOpened(View drawerView) {
                getSupportActionBar().setDisplayShowTitleEnabled(true);
                getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                super.onDrawerOpened(drawerView);
            }
        };
        drawerLayout.setDrawerListener(drawerToggle);

        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        doInitLibrary();
        doInitLibraryContent();
        getSupportLoaderManager().initLoader(0, null, getPlayerView());

        if (PlayerApplication.playerService == null) {
            final Context context = getApplicationContext();

            if (context != null) {
                PlayerApplication.connectService(this);
            }
        }
        else {
            getPlayerView().registerServiceListener();
        }

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

                    mediaManager.getMediaProvider().setProperty(
                            ContentType.CONTENT_TYPE_STORAGE,
                            path,
                            AbstractMediaProvider.ContentProperty.CONTENT_STORAGE_CURRENT_LOCATION,
                            null,
                            null);
                    int position = (Integer) mediaManager.getMediaProvider().getProperty(
                            ContentType.CONTENT_TYPE_STORAGE,
                            dataUri.getPath(),
                            AbstractMediaProvider.ContentProperty.CONTENT_STORAGE_RESOURCE_POSITION);

                    libraryAdapter.doRefresh();

                    MusicConnector.doContextActionPlay(
                            ContentType.CONTENT_TYPE_STORAGE,
                            String.valueOf(position),
                            MusicConnector.storage_sort_order,
                            position);

                    getPlayerView().getSlidingPanel().expandPanel();
                    getSupportActionBar().hide();
                }
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        getSupportLoaderManager().destroyLoader(0);

        for (AbstractMediaManager mediaManager : PlayerApplication.mediaManagers) {
            mediaManager.getMediaProvider().removeLibraryChangeListener(libraryChangeListener);
        }

        getPlayerView().onActivityDestroy();
        getPlayerView().unregisterServiceListener();
        PlayerApplication.unregisterServiceCallback(this);

        unbindDrawables(findViewById(R.id.drawer_layout));
        super.onDestroy();
        System.gc();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (drawerLayout.isDrawerOpen(drawerList)) {
                    drawerLayout.closeDrawer(drawerList);
                } else {
                    drawerLayout.openDrawer(drawerList);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case DRAWERITEM_LIBRARY_SETTINGS_ID:
            case AbstractMediaProvider.ACTIVITY_NEED_UI_REFRESH:
                doInitLibrary();
                break;
        }
    }

    public void doRefresh() {
        if (libraryAdapter != null) {
            libraryAdapter.doRefresh();
        }
    }

    public void setSwipeMenuEnabled(boolean enabled) {
        if (enabled) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
        else {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
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

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    public void selectItem(int position) {
        drawerLayout.closeDrawers();

        final AbstractNavigationDrawerItem abstractNavigationDrawerItem = (AbstractNavigationDrawerItem)drawerList.getItemAtPosition(position);
        if (abstractNavigationDrawerItem != null) {
            int menuItemId = abstractNavigationDrawerItem.getId();
            switch (menuItemId) {
                case DRAWERITEM_LIBRARY_SETTINGS_ID:
                    doLibraryManagement();
                    break;
                case DRAWERITEM_APPLICATION_SETTINGS_ID:
                    startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                    break;
                case DRAWERITEM_AUDIO_FX_ID:
                    startActivity(new Intent(PlayerApplication.context, SoundEffectsActivity.class));
                    break;
                default:
                    final AbstractMediaProvider mediaProvider = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex].getMediaProvider();
                    final AbstractProviderAction providerAction = mediaProvider.getAbstractProviderAction(menuItemId - 200);
                    if (providerAction != null) {
                        providerAction.launch(this);
                    }
            }

        }

        if (drawerLayout.isDrawerOpen(drawerList)) {
            drawerLayout.closeDrawer(drawerList);
        }
    }

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
            PlayerApplication.mediaManagers[PlayerApplication.getLibraryLibraryIndex()].getMediaProvider().scanStart();
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
                    case +AbstractMediaProvider.PLAYLIST_NAME: sortIndex = 0;  break;
                    case -AbstractMediaProvider.PLAYLIST_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_playlists, sortIndex, playlistFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(ArtistFragment.class)) {
                int sortIndex = 0;
                switch (MusicConnector.artists_sort_order) {
                    case +AbstractMediaProvider.ARTIST_NAME: sortIndex = 0;  break;
                    case -AbstractMediaProvider.ARTIST_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_artists, sortIndex, artistFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(AlbumArtistFragment.class)) {
                int sortIndex = 0;
                switch (MusicConnector.album_artists_sort_order) {
                    case +AbstractMediaProvider.ALBUM_ARTIST_NAME: sortIndex = 0;  break;
                    case -AbstractMediaProvider.ALBUM_ARTIST_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_album_artists, sortIndex, albumArtistFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(AlbumFragment.class)) {
                int sortIndex = 0;
                switch (MusicConnector.albums_sort_order) {
                    case +AbstractMediaProvider.ALBUM_NAME:   sortIndex = 0;  break;
                    case -AbstractMediaProvider.ALBUM_NAME:   sortIndex = 1;  break;
                    case +AbstractMediaProvider.ALBUM_ARTIST: sortIndex = 2;  break;
                    case -AbstractMediaProvider.ALBUM_ARTIST: sortIndex = 3;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_albums, sortIndex, albumFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(SongFragment.class)) {
                int sortIndex = 0; // case MusicConnector.SORT_A_Z
                switch (MusicConnector.songs_sort_order) {
                    case +AbstractMediaProvider.SONG_TITLE:   sortIndex = 0;  break;
                    case -AbstractMediaProvider.SONG_TITLE:   sortIndex = 1;  break;
                    case +AbstractMediaProvider.SONG_TRACK:   sortIndex = 2;  break;
                    case -AbstractMediaProvider.SONG_TRACK:   sortIndex = 3;  break;
                    case +AbstractMediaProvider.SONG_URI:     sortIndex = 4;  break;
                    case -AbstractMediaProvider.SONG_URI:     sortIndex = 5;  break;
                    case +AbstractMediaProvider.SONG_ARTIST:  sortIndex = 6;  break;
                    case -AbstractMediaProvider.SONG_ARTIST:  sortIndex = 7;  break;
                    case +AbstractMediaProvider.SONG_ALBUM:   sortIndex = 8;  break;
                    case -AbstractMediaProvider.SONG_ALBUM:   sortIndex = 9; break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_songs, sortIndex, songFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(GenreFragment.class)) {
                int sortIndex = 0;
                switch (MusicConnector.genres_sort_order) {
                    case +AbstractMediaProvider.GENRE_NAME: sortIndex = 0;  break;
                    case -AbstractMediaProvider.GENRE_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_genre, sortIndex, genreFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(StorageFragment.class)) {
                int sortIndex = 0; // case MusicConnector.SORT_A_Z
                switch (MusicConnector.storage_sort_order) {
                    case AbstractMediaProvider.STORAGE_DISPLAY_NAME:               sortIndex = 0;  break;
                    case -AbstractMediaProvider.STORAGE_DISPLAY_NAME:              sortIndex = 1;  break;
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
                case 0:  MusicConnector.playlists_sort_order = AbstractMediaProvider.PLAYLIST_NAME; break;
                case 1:  MusicConnector.playlists_sort_order = -AbstractMediaProvider.PLAYLIST_NAME; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener artistFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.artists_sort_order = AbstractMediaProvider.ARTIST_NAME; break;
                case 1:  MusicConnector.artists_sort_order = -AbstractMediaProvider.ARTIST_NAME; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener albumArtistFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.album_artists_sort_order = AbstractMediaProvider.ALBUM_ARTIST_NAME; break;
                case 1:  MusicConnector.album_artists_sort_order = -AbstractMediaProvider.ALBUM_ARTIST_NAME; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener albumFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.albums_sort_order = AbstractMediaProvider.ALBUM_NAME; break;
                case 1:  MusicConnector.albums_sort_order = -AbstractMediaProvider.ALBUM_NAME;  break;
                case 2:  MusicConnector.albums_sort_order = AbstractMediaProvider.ALBUM_ARTIST;  break;
                case 3:  MusicConnector.albums_sort_order = -AbstractMediaProvider.ALBUM_ARTIST; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener songFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.songs_sort_order = AbstractMediaProvider.SONG_TITLE; break;
                case 1:  MusicConnector.songs_sort_order = -AbstractMediaProvider.SONG_TITLE; break;
                case 2:  MusicConnector.songs_sort_order = AbstractMediaProvider.SONG_TRACK; break;
                case 3:  MusicConnector.songs_sort_order = -AbstractMediaProvider.SONG_TRACK; break;
                case 4:  MusicConnector.songs_sort_order = AbstractMediaProvider.SONG_URI; break;
                case 5:  MusicConnector.songs_sort_order = -AbstractMediaProvider.SONG_URI; break;
                case 6:  MusicConnector.songs_sort_order = AbstractMediaProvider.SONG_ARTIST; break;
                case 7:  MusicConnector.songs_sort_order = -AbstractMediaProvider.SONG_ARTIST; break;
                case 8:  MusicConnector.songs_sort_order = AbstractMediaProvider.SONG_ALBUM; break;
                case 9:  MusicConnector.songs_sort_order = -AbstractMediaProvider.SONG_ALBUM; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener genreFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.genres_sort_order = AbstractMediaProvider.GENRE_NAME; break;
                case 1:  MusicConnector.genres_sort_order = -AbstractMediaProvider.GENRE_NAME; break;
            }

            libraryAdapter.doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener storageFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.storage_sort_order = AbstractMediaProvider.STORAGE_DISPLAY_NAME; break;
                case 1:  MusicConnector.storage_sort_order = -AbstractMediaProvider.STORAGE_DISPLAY_NAME; break;
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

    private AbstractMediaProvider.OnLibraryChangeListener libraryChangeListener = new AbstractMediaProvider.OnLibraryChangeListener() {

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
                    setSupportProgressBarIndeterminateVisibility(true);
                }
            });
        }

        @Override
        public void libraryScanFinished() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setSupportProgressBarIndeterminateVisibility(false);
                }
            });
        }
    };


    protected void initLibraryView() {
        libraryAdapter = new PagerAdapter(getApplicationContext(), getSupportFragmentManager());

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        // Only show tabs that were set in preferences
        if (mediaProvider.hasContentType(ContentType.CONTENT_TYPE_PLAYLIST)) {
            libraryAdapter.addFragment(new PlaylistFragment(), null, R.string.tab_label_playlists);
        }

        if (mediaProvider.hasContentType(ContentType.CONTENT_TYPE_ARTIST)) {
            libraryAdapter.addFragment(new ArtistFragment(), null, R.string.tab_label_artists);
        }

        if (mediaProvider.hasContentType(ContentType.CONTENT_TYPE_ALBUM_ARTIST)) {
            libraryAdapter.addFragment(new AlbumArtistFragment(), null, R.string.tab_label_album_artists);
        }

        if (mediaProvider.hasContentType(ContentType.CONTENT_TYPE_ALBUM)) {
            libraryAdapter.addFragment(new AlbumFragment(), null, R.string.tab_label_albums);
        }

        if (mediaProvider.hasContentType(ContentType.CONTENT_TYPE_MEDIA)) {
            libraryAdapter.addFragment(new SongFragment(), null, R.string.tab_label_songs);
        }

        if (mediaProvider.hasContentType(ContentType.CONTENT_TYPE_GENRE)) {
            libraryAdapter.addFragment(new GenreFragment(), null, R.string.tab_label_genres);
        }

        if (mediaProvider.hasContentType(ContentType.CONTENT_TYPE_STORAGE)) {
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

        ArrayList<AbstractNavigationDrawerItem> objects = new ArrayList<AbstractNavigationDrawerItem>();
        objects.add(NavigationMenuSection.create(DRAWERITEM_SEPARATOR_ID, getString(R.string.drawer_section_label_media_management)));

        final AbstractMediaProvider mediaProvider = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex].getMediaProvider();
        final AbstractProviderAction providerActionList[] = mediaProvider.getAbstractProviderActionList();

        if (providerActionList != null) {
            for (int index = 0; index < providerActionList.length; index++) {
                final AbstractProviderAction providerAction = providerActionList[index];

                if (providerAction.isVisible()) {
                    objects.add(NavigationMenuItem.create(200 + index, providerAction.getDescription(), R.drawable.ic_action_tiles_large, false));
                }
            }
        }

        objects.add(NavigationMenuSection.create(DRAWERITEM_SEPARATOR_ID, getString(R.string.drawer_section_label_label_settings)));

        // TODO: add appropriate icon.
        objects.add(NavigationMenuItem.create(DRAWERITEM_AUDIO_FX_ID, getString(R.string.drawer_item_label_library_soundfx), R.drawable.ic_action_database, false));
        objects.add(NavigationMenuItem.create(DRAWERITEM_LIBRARY_SETTINGS_ID, getString(R.string.drawer_item_label_manage_libraries), R.drawable.ic_action_database, false));
        objects.add(NavigationMenuItem.create(DRAWERITEM_APPLICATION_SETTINGS_ID, getString(R.string.drawer_item_label_application_settings), R.drawable.ic_action_settings, false));

        final NavigationDrawerAdapter navigationDrawerAdapter = new NavigationDrawerAdapter(this, R.layout.view_drawer_item_single_line, objects);
        drawerList.setAdapter(navigationDrawerAdapter);

        //    Launching scan.
        for (AbstractMediaManager mediaManager : PlayerApplication.mediaManagers) {
            mediaManager.getMediaProvider().addLibraryChangeListener(libraryChangeListener);
        }

        if (mediaProvider.scanIsRunning()) {
            libraryChangeListener.libraryScanStarted();
        }
        else {
            mediaProvider.scanStart();
        }
    }

    protected  void doLibraryManagement() {
        startActivityForResult(new Intent(PlayerApplication.context, SettingsLibrariesActivity.class), DRAWERITEM_LIBRARY_SETTINGS_ID);
    }

    protected void doManageMenuitemVisibility(PagerAdapter pagerAdapter, int position) {
        final Class<?> itemClass = ((Object)pagerAdapter.getItem(position)).getClass();

        if (itemClass.equals(PlaylistFragment.class)) {
            sortMenuItem.setVisible(true);
//            recentMenuItem.setVisible(false);
        }
        else if (itemClass.equals(ArtistFragment.class)) {
            sortMenuItem.setVisible(true);
//            recentMenuItem.setVisible(false);
        }
        else if (itemClass.equals(AlbumArtistFragment.class)) {
            sortMenuItem.setVisible(true);
//            recentMenuItem.setVisible(false);
        }
        else if (itemClass.equals(AlbumFragment.class)) {
            sortMenuItem.setVisible(true);
//            recentMenuItem.setVisible(false);
        }
        else if (itemClass.equals(SongFragment.class)) {
            sortMenuItem.setVisible(true);
//            recentMenuItem.setVisible(false);
        }
        else if (itemClass.equals(GenreFragment.class)) {
            sortMenuItem.setVisible(true);
//            recentMenuItem.setVisible(false);
        }
        else if (itemClass.equals(StorageFragment.class)) {
            sortMenuItem.setVisible(true);
//            recentMenuItem.setVisible(false);
        }
        else {
            sortMenuItem.setVisible(false);
//            recentMenuItem.setVisible(false);
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

}
