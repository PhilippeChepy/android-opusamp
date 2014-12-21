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
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.astuetz.PagerSlidingTabStrip;
import com.squareup.otto.Subscribe;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.ProviderEventBus;
import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.core.service.providers.event.LibraryContentChangedEvent;
import net.opusapp.player.core.service.providers.event.LibraryScanStatusChangedEvent;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.core.service.providers.index.database.OpenHelper;
import net.opusapp.player.ui.activities.settings.GeneralSettingsActivity;
import net.opusapp.player.ui.adapter.ProviderAdapter;
import net.opusapp.player.ui.adapter.ux.PagerAdapter;
import net.opusapp.player.ui.fragments.AlbumArtistFragment;
import net.opusapp.player.ui.fragments.AlbumFragment;
import net.opusapp.player.ui.fragments.ArtistFragment;
import net.opusapp.player.ui.fragments.GenreFragment;
import net.opusapp.player.ui.fragments.PlaylistFragment;
import net.opusapp.player.ui.fragments.SongFragment;
import net.opusapp.player.ui.fragments.StorageFragment;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.RefreshableView;
import net.opusapp.player.utils.LogUtils;

import java.io.File;

public class LibraryMainActivity extends AbstractPlayerActivity implements RefreshableView {

    public static final String TAG = LibraryMainActivity.class.getSimpleName();



    private static final int OPTION_MENUITEM_LIBRARY_SETTINGS_ID = 100;

    private static final int OPTION_MENUITEM_APPLICATION_SETTINGS_ID = 101;



    // Actionbar
    private static final int OPTION_MENUITEM_SORT = 1;

    private static final int OPTION_MENUITEM_FILTER = 2;

    private static final int OPTION_MENUITEM_SHOW_HIDDEN = 3;

    private static final int OPTION_MENUITEM_RELOAD = 4;



    // Actionbar search system
    private SearchView mSearchView;



    // Actionbar items
    private MenuItem mSortMenuItem;

    private MenuItem mSearchMenuItem;

    private MenuItem mReloadMenuItem;



    // Main content
    private ViewPager mLibraryPager;

    private PagerAdapter mLibraryAdapter;

    private PagerSlidingTabStrip mScrollingTabs;



    // Navigation
    private DrawerLayout mDrawerLayout;

    private Cursor mNavigationCursor;

    private ProviderAdapter mNavigationAdapter;



    // Drawer
    private static final String SAVED_STATE_ACTION_PLAYER_PANEL_IS_HIDDEN = "player_panel_is_hidden";

    private boolean mHiddenPanel;

    private ActionBarDrawerToggle mDrawerToggle;



    private boolean mDoubleBackToExitPressedOnce = false;




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (TextUtils.isEmpty(PlayerApplication.lastSearchFilter)) {
            PlayerApplication.lastSearchFilter = null;
        }

        mSearchView = new SearchView(getSupportActionBar().getThemedContext());
        mSearchView.setQueryHint(getString(R.string.searchview_query_hint));
        mSearchView.setOnQueryTextListener(searchViewOnQueryTextListener);
        mSearchView.setOnCloseListener(searchViewOnCloseListener);


        super.onCreateOptionsMenu(menu);

        mSortMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SORT, 2, R.string.menuitem_label_sort);
        mSortMenuItem.setIcon(PlayerApplication.iconsAreDark() ? R.drawable.ic_sort_black_48dp : R.drawable.ic_sort_white_48dp);
        MenuItemCompat.setShowAsAction(mSortMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        mSortMenuItem.setOnMenuItemClickListener(mSortOptionMenuItemListener);

        mSearchMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_FILTER, 3, R.string.menuitem_label_filter);
        mSearchMenuItem.setIcon(PlayerApplication.iconsAreDark() ? R.drawable.ic_search_black_48dp : R.drawable.ic_search_white_48dp);
        MenuItemCompat.setActionView(mSearchMenuItem, mSearchView);
        MenuItemCompat.setShowAsAction(mSearchMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        final MenuItem hiddenMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SHOW_HIDDEN, 4, R.string.menuitem_label_show_hidden);
        hiddenMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_visibility_black_48dp : R.drawable.ic_visibility_white_48dp);
        hiddenMenuItem.setCheckable(true);
        MenuItemCompat.setShowAsAction(hiddenMenuItem, MenuItemCompat.SHOW_AS_ACTION_NEVER);
        hiddenMenuItem.setOnMenuItemClickListener(mSwitchHideMenuItemListener);

        doManageMenuitemVisibility(mLibraryAdapter, mLibraryPager.getCurrentItem());

        mReloadMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_RELOAD, 6, R.string.menuitem_label_reload);
        MenuItemCompat.setShowAsAction(mReloadMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        mReloadMenuItem.setOnMenuItemClickListener(mReloadMenuItemListener);
        drawReloadMenuItem();

        final MenuItem settingsMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_APPLICATION_SETTINGS_ID, 7, R.string.drawer_item_label_settings);
        settingsMenuItem.setIcon(R.drawable.ic_settings_grey600_48dp);
        settingsMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                startActivityForResult(new Intent(PlayerApplication.context, GeneralSettingsActivity.class), OPTION_MENUITEM_LIBRARY_SETTINGS_ID);
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.activity_library_main, null);

        // Pager & Tabs
        mLibraryPager = (ViewPager) findViewById(R.id.pager_viewpager);
        mLibraryPager.setPageMargin(getResources().getInteger(R.integer.viewpager_margin_width));

        mScrollingTabs = (PagerSlidingTabStrip) findViewById(R.id.pager_tabs);
        mScrollingTabs.setOnPageChangeListener(scrollingTabsOnPageChangeListener);
        mScrollingTabs.setIndicatorColorResource(R.color.materialAccentColor);
        mScrollingTabs.setTextColor(getResources().getColor(R.color.tabTextColor));
        PlayerApplication.applyThemeOnPagerTabs(mScrollingTabs);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.actionbar_drawer_toggle_label_open, R.string.actionbar_drawer_toggle_label_close) {

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, slideOffset);
                doPlayerPanelPlayManagement(slideOffset);
            }
        };

        mNavigationAdapter = new ProviderAdapter(this, R.layout.view_item_double_line_no_anchor, null);

        final ListView drawerList = (ListView) findViewById(R.id.list_drawer);
        drawerList.setAdapter(mNavigationAdapter);
        drawerList.setOnItemClickListener(navigationDrawerListOnItemClickListener);

        final View footerView = getLayoutInflater().inflate(R.layout.view_item_settings, drawerList, false);
        final TextView textView = (TextView) footerView.findViewById(R.id.line_one);
        textView.setText(R.string.menuitem_label_add_provider);

        footerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerApplication.addLibrary(LibraryMainActivity.this, new Runnable() {

                    @Override
                    public void run() {
                        PlayerApplication.allocateMediaManagers();

                        initProvidersList();
                        initCurrentProvider();
                    }
                });
            }
        });


        drawerList.addFooterView(footerView, null, true);

        mDrawerLayout.setDrawerListener(mDrawerToggle);

        initProvidersList();
        initCurrentProvider();
        final Intent intent = getIntent();
        final String action = intent.getAction();

        if(Intent.ACTION_VIEW.equals(action)){
            final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
            localBroadcastManager.sendBroadcast(PlayerService.CLIENT_STOP_INTENT);

            MediaManager manager = PlayerApplication.mediaManager(1);
            PlayerApplication.setLibraryManager(1);
            PlayerApplication.setPlayerManager(1);
            PlayerApplication.saveLibraryIndexes();

            final Uri dataUri = intent.getData();
            String path = dataUri.getPath();
            final File pathFile = new File(path);
            if (!pathFile.isDirectory()) {
                path = pathFile.getParent();
            }

            manager.getProvider().setProperty(
                    MediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                    path,
                    MediaManager.Provider.ContentProperty.CONTENT_STORAGE_CURRENT_LOCATION,
                    null,
                    null);

            int position = (Integer) manager.getProvider().getProperty(
                    MediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                    dataUri.getPath(),
                    MediaManager.Provider.ContentProperty.CONTENT_STORAGE_RESOURCE_POSITION);

            mLibraryAdapter.refresh();

            PlayerApplication.doContextActionPlay(
                    MediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE,
                    String.valueOf(position),
                    PlayerApplication.library_storage_sort_order,
                    position);

            getSlidingPanel().expandPanel();
        }

        ProviderEventBus.getInstance().register(this);
    }

    @Override
    protected void onDestroy() {
        ProviderEventBus.getInstance().unregister(this);

        getSupportLoaderManager().destroyLoader(0);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_STATE_ACTION_PLAYER_PANEL_IS_HIDDEN, getSlidingPanel().isPanelHidden());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mHiddenPanel = savedInstanceState.getBoolean(SAVED_STATE_ACTION_PLAYER_PANEL_IS_HIDDEN, false);
    }

    @Override
    protected boolean canShowPanel() {
        return !mHiddenPanel;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        int position = mLibraryPager.getCurrentItem();
        final Object currentFragment = mLibraryAdapter.getItem(position);
        if (currentFragment instanceof StorageFragment) {
            if (!((StorageFragment) currentFragment).handleBackButton()) {
                if (mDoubleBackToExitPressedOnce) {
                    super.onBackPressed();
                    return;
                }

                mDoubleBackToExitPressedOnce = true;
                Toast.makeText(PlayerApplication.context, R.string.toast_press_back_again, Toast.LENGTH_SHORT).show();

                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        mDoubleBackToExitPressedOnce = false;
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
        final SupportMenuItem search = (SupportMenuItem) mSearchMenuItem;
        search.expandActionView();
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (PlayerApplication.uiColorsChanged) {
            PlayerApplication.uiColorsChanged = false;
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }

        initProvidersList();
        initCurrentProvider();
    }

    @Override
    public void refresh() {
        if (mLibraryAdapter != null) {
            mLibraryAdapter.refresh();
        }
    }


    protected void initLibraryView() {
        mLibraryAdapter = new PagerAdapter(getApplicationContext(), getSupportFragmentManager());

        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        // Only show tabs that were set in preferences
        if (provider.hasContentType(MediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST)) {
            mLibraryAdapter.addFragment(new PlaylistFragment(), null, R.string.tab_label_playlists);
        }

        if (provider.hasContentType(MediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST)) {
            mLibraryAdapter.addFragment(new ArtistFragment(), null, R.string.tab_label_artists);
        }

        if (provider.hasContentType(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST)) {
            mLibraryAdapter.addFragment(new AlbumArtistFragment(), null, R.string.tab_label_album_artists);
        }

        if (provider.hasContentType(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM)) {
            mLibraryAdapter.addFragment(new AlbumFragment(), null, R.string.tab_label_albums);
        }

        if (provider.hasContentType(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA)) {
            mLibraryAdapter.addFragment(new SongFragment(), null, R.string.tab_label_songs);
        }

        if (provider.hasContentType(MediaManager.Provider.ContentType.CONTENT_TYPE_GENRE)) {
            mLibraryAdapter.addFragment(new GenreFragment(), null, R.string.tab_label_genres);
        }

        if (provider.hasContentType(MediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE)) {
            mLibraryAdapter.addFragment(new StorageFragment(), null, R.string.tab_label_storage);
        }

        mLibraryPager.setAdapter(mLibraryAdapter);
        mScrollingTabs.setViewPager(mLibraryPager);
    }


    protected void initProvidersList() {
        SQLiteDatabase database = OpenHelper.getInstance().getReadableDatabase();

        if (database != null) {
            Cursor cursor = database.query(
                    Entities.Provider.TABLE_NAME,
                    new String[]{
                            Entities.Provider._ID,
                            Entities.Provider.COLUMN_FIELD_PROVIDER_NAME,
                            Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE
                    },
                    null, null, null, null, Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION);

            mNavigationCursor = cursor;
            if (cursor != null) {
                mNavigationAdapter.changeCursor(cursor);
            }
        }
    }

    protected void initCurrentProvider() {
        initLibraryView();
        final MediaManager.Provider provider = PlayerApplication.libraryMediaManager().getProvider();

        //    Launching scan.
        if (!provider.scanIsRunning()) {
            provider.scanStart();
        }

        if (mNavigationCursor != null) {
            try {
                final MediaManager manager = PlayerApplication.libraryMediaManager();

                getSupportActionBar().setTitle(manager.getName());
                getSupportActionBar().setSubtitle(manager.getDescription());
            } catch (final Exception exception) {
                LogUtils.LOGException(TAG, "initCurrentProvider", 0, exception);
            }
        }
    }


    protected void doManageMenuitemVisibility(PagerAdapter pagerAdapter, int position) {
        final Class<?> itemClass = ((Object)pagerAdapter.getItem(position)).getClass();

        if (itemClass.equals(PlaylistFragment.class)) {
            mSortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(ArtistFragment.class)) {
            mSortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(AlbumArtistFragment.class)) {
            mSortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(AlbumFragment.class)) {
            mSortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(SongFragment.class)) {
            mSortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(GenreFragment.class)) {
            mSortMenuItem.setVisible(true);
        }
        else if (itemClass.equals(StorageFragment.class)) {
            mSortMenuItem.setVisible(true);
        }
        else {
            mSortMenuItem.setVisible(false);
        }
    }

    protected void doPlayerPanelPlayManagement(float slideOffset) {

        if (slideOffset >= 0.1 && !getSlidingPanel().isPanelHidden()) {
            getSlidingPanel().hidePanel();
        }
        else if (slideOffset < 0.1 && hasPlaylist() && getSlidingPanel().isPanelHidden()) {
            getSlidingPanel().showPanel();
        }
    }

    protected void drawReloadMenuItem() {
        if (PlayerApplication.thereIsScanningMediaManager()) {
            mReloadMenuItem.setTitle(R.string.menuitem_label_cancel_reload);
            mReloadMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_close_black_48dp : R.drawable.ic_close_white_48dp);
        }
        else {
            mReloadMenuItem.setTitle(R.string.menuitem_label_reload);
            mReloadMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_refresh_black_48dp : R.drawable.ic_refresh_white_48dp);
        }
    }

    // SearchView listener
    private final SearchView.OnQueryTextListener searchViewOnQueryTextListener = new SearchView.OnQueryTextListener() {

        @Override
        public boolean onQueryTextChange(String searchText) {
            PlayerApplication.lastSearchFilter = searchText;
            mLibraryAdapter.refresh();
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            final InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
            return true;
        }
    };

    private final SearchView.OnCloseListener searchViewOnCloseListener = new SearchView.OnCloseListener() {

        @Override
        public boolean onClose() {
            mSearchView.setQuery(null, true);
            mLibraryAdapter.refresh();
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
            if (mSortMenuItem != null /*&& recentMenuItem != null*/) {
                doManageMenuitemVisibility(mLibraryAdapter, position);
            }
        }

    };

    //    Hidden Menuitem click listener
    private final MenuItem.OnMenuItemClickListener mSwitchHideMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            boolean show_hidden = !item.isChecked();

            item.setChecked(show_hidden);
            PlayerApplication.library_show_hidden = show_hidden;
            mLibraryAdapter.refresh();
            return true;
        }
    };



    private final MenuItem.OnMenuItemClickListener mReloadMenuItemListener = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            if (PlayerApplication.libraryMediaManager().getProvider().scanIsRunning()) {
                PlayerApplication.libraryMediaManager().getProvider().scanCancel();
            }
            else {
                PlayerApplication.libraryMediaManager().getProvider().scanStart();
            }
            return true;
        }
    };

    private final MenuItem.OnMenuItemClickListener mSortOptionMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(LibraryMainActivity.this);

            final Class<?> currentItemClass = ((Object) mLibraryAdapter.getItem(mLibraryPager.getCurrentItem())).getClass();

            if (currentItemClass.equals(PlaylistFragment.class)) {
                int sortIndex = 0;
                switch (PlayerApplication.library_playlists_sort_order) {
                    case +MediaManager.Provider.PLAYLIST_NAME: sortIndex = 0;  break;
                    case -MediaManager.Provider.PLAYLIST_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_playlists, sortIndex, playlistFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(ArtistFragment.class)) {
                int sortIndex = 0;
                switch (PlayerApplication.library_artists_sort_order) {
                    case +MediaManager.Provider.ARTIST_NAME: sortIndex = 0;  break;
                    case -MediaManager.Provider.ARTIST_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_artists, sortIndex, artistFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(AlbumArtistFragment.class)) {
                int sortIndex = 0;
                switch (PlayerApplication.library_album_artists_sort_order) {
                    case +MediaManager.Provider.ALBUM_ARTIST_NAME: sortIndex = 0;  break;
                    case -MediaManager.Provider.ALBUM_ARTIST_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_album_artists, sortIndex, albumArtistFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(AlbumFragment.class)) {
                int sortIndex = 0;
                switch (PlayerApplication.library_albums_sort_order) {
                    case +MediaManager.Provider.ALBUM_NAME:   sortIndex = 0;  break;
                    case -MediaManager.Provider.ALBUM_NAME:   sortIndex = 1;  break;
                    case +MediaManager.Provider.ALBUM_ARTIST: sortIndex = 2;  break;
                    case -MediaManager.Provider.ALBUM_ARTIST: sortIndex = 3;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_albums, sortIndex, albumFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(SongFragment.class)) {
                int sortIndex = 0; // case MusicConnector.SORT_A_Z
                switch (PlayerApplication.library_songs_sort_order) {
                    case +MediaManager.Provider.SONG_TITLE:   sortIndex = 0;  break;
                    case -MediaManager.Provider.SONG_TITLE:   sortIndex = 1;  break;
                    case +MediaManager.Provider.SONG_TRACK:   sortIndex = 2;  break;
                    case -MediaManager.Provider.SONG_TRACK:   sortIndex = 3;  break;
                    case +MediaManager.Provider.SONG_URI:     sortIndex = 4;  break;
                    case -MediaManager.Provider.SONG_URI:     sortIndex = 5;  break;
                    case +MediaManager.Provider.SONG_ARTIST:  sortIndex = 6;  break;
                    case -MediaManager.Provider.SONG_ARTIST:  sortIndex = 7;  break;
                    case +MediaManager.Provider.SONG_ALBUM:   sortIndex = 8;  break;
                    case -MediaManager.Provider.SONG_ALBUM:   sortIndex = 9; break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_songs, sortIndex, songFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(GenreFragment.class)) {
                int sortIndex = 0;
                switch (PlayerApplication.library_genres_sort_order) {
                    case +MediaManager.Provider.GENRE_NAME: sortIndex = 0;  break;
                    case -MediaManager.Provider.GENRE_NAME: sortIndex = 1;  break;
                }

                alertDialogBuilder.setSingleChoiceItems(R.array.sort_genre, sortIndex, genreFragmentAlertDialogClickListener);
            }
            else if (currentItemClass.equals(StorageFragment.class)) {
                int sortIndex = 0; // case MusicConnector.SORT_A_Z
                switch (PlayerApplication.library_storage_sort_order) {
                    case MediaManager.Provider.STORAGE_DISPLAY_NAME:               sortIndex = 0;  break;
                    case -MediaManager.Provider.STORAGE_DISPLAY_NAME:              sortIndex = 1;  break;
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
                case 0:  PlayerApplication.library_playlists_sort_order = MediaManager.Provider.PLAYLIST_NAME; break;
                case 1:  PlayerApplication.library_playlists_sort_order = -MediaManager.Provider.PLAYLIST_NAME; break;
            }

            mLibraryAdapter.refresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener artistFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  PlayerApplication.library_artists_sort_order = MediaManager.Provider.ARTIST_NAME; break;
                case 1:  PlayerApplication.library_artists_sort_order = -MediaManager.Provider.ARTIST_NAME; break;
            }

            mLibraryAdapter.refresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener albumArtistFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  PlayerApplication.library_album_artists_sort_order = MediaManager.Provider.ALBUM_ARTIST_NAME; break;
                case 1:  PlayerApplication.library_album_artists_sort_order = -MediaManager.Provider.ALBUM_ARTIST_NAME; break;
            }

            mLibraryAdapter.refresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener albumFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  PlayerApplication.library_albums_sort_order = MediaManager.Provider.ALBUM_NAME; break;
                case 1:  PlayerApplication.library_albums_sort_order = -MediaManager.Provider.ALBUM_NAME;  break;
                case 2:  PlayerApplication.library_albums_sort_order = MediaManager.Provider.ALBUM_ARTIST;  break;
                case 3:  PlayerApplication.library_albums_sort_order = -MediaManager.Provider.ALBUM_ARTIST; break;
            }

            mLibraryAdapter.refresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener songFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  PlayerApplication.library_songs_sort_order = MediaManager.Provider.SONG_TITLE; break;
                case 1:  PlayerApplication.library_songs_sort_order = -MediaManager.Provider.SONG_TITLE; break;
                case 2:  PlayerApplication.library_songs_sort_order = MediaManager.Provider.SONG_TRACK; break;
                case 3:  PlayerApplication.library_songs_sort_order = -MediaManager.Provider.SONG_TRACK; break;
                case 4:  PlayerApplication.library_songs_sort_order = MediaManager.Provider.SONG_URI; break;
                case 5:  PlayerApplication.library_songs_sort_order = -MediaManager.Provider.SONG_URI; break;
                case 6:  PlayerApplication.library_songs_sort_order = MediaManager.Provider.SONG_ARTIST; break;
                case 7:  PlayerApplication.library_songs_sort_order = -MediaManager.Provider.SONG_ARTIST; break;
                case 8:  PlayerApplication.library_songs_sort_order = MediaManager.Provider.SONG_ALBUM; break;
                case 9:  PlayerApplication.library_songs_sort_order = -MediaManager.Provider.SONG_ALBUM; break;
            }

            mLibraryAdapter.refresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener genreFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  PlayerApplication.library_genres_sort_order = MediaManager.Provider.GENRE_NAME; break;
                case 1:  PlayerApplication.library_genres_sort_order = -MediaManager.Provider.GENRE_NAME; break;
            }

            mLibraryAdapter.refresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener storageFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  PlayerApplication.library_storage_sort_order = MediaManager.Provider.STORAGE_DISPLAY_NAME; break;
                case 1:  PlayerApplication.library_storage_sort_order = -MediaManager.Provider.STORAGE_DISPLAY_NAME; break;
            }

            mLibraryAdapter.refresh();
            dialog.dismiss();
        }
    };

    private AdapterView.OnItemClickListener navigationDrawerListOnItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            PlayerApplication.setLibraryManager((int) id);

            initCurrentProvider();
            PlayerApplication.saveLibraryIndexes();

            mDrawerLayout.closeDrawers();
        }
    };

    @SuppressWarnings("unused")
    @Subscribe public void libraryScanStatusChanged(LibraryScanStatusChangedEvent libraryScanStatusChangedEvent) {
        if (mReloadMenuItem != null) {
            drawReloadMenuItem();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe public void libraryContentChangedEvent(LibraryContentChangedEvent libraryContentChangedEvent) {
        mLibraryAdapter.refresh();
    }
}