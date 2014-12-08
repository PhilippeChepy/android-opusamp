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

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaManagerFactory;
import net.opusapp.player.core.service.providers.index.database.Entities;
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
import net.opusapp.player.ui.utils.MusicConnector;
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

        mSortMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SORT, 2, R.string.menuitem_label_sort);
        mSortMenuItem.setIcon(PlayerApplication.iconsAreDark() ? R.drawable.ic_sort_black_48dp : R.drawable.ic_sort_white_48dp);
        MenuItemCompat.setShowAsAction(mSortMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        mSortMenuItem.setOnMenuItemClickListener(onSortOptionMenuItemListener);

        mSearchMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_FILTER, 3, R.string.menuitem_label_filter);
        mSearchMenuItem.setIcon(PlayerApplication.iconsAreDark() ? R.drawable.ic_search_black_48dp : R.drawable.ic_search_white_48dp);
        MenuItemCompat.setActionView(mSearchMenuItem, mSearchView);
        MenuItemCompat.setShowAsAction(mSearchMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        final MenuItem hiddenMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SHOW_HIDDEN, 4, R.string.menuitem_label_show_hidden);
        hiddenMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_visibility_black_48dp : R.drawable.ic_visibility_white_48dp);
        hiddenMenuItem.setCheckable(true);
        MenuItemCompat.setShowAsAction(hiddenMenuItem, MenuItemCompat.SHOW_AS_ACTION_NEVER);
        hiddenMenuItem.setOnMenuItemClickListener(hiddenOnMenuItemClickListener);

        doManageMenuitemVisibility(mLibraryAdapter, mLibraryPager.getCurrentItem());

        mReloadMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_RELOAD, 6, R.string.menuitem_label_reload);
        mReloadMenuItem.setIcon(PlayerApplication.iconsAreDark() ? R.drawable.ic_refresh_black_48dp : R.drawable.ic_refresh_white_48dp);
        MenuItemCompat.setShowAsAction(mReloadMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        mReloadMenuItem.setOnMenuItemClickListener(reloadOnMenuItemClickListener);

        updateReloadMenuItem();

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
    protected void onCreate(Bundle savedInstanceState) {
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
                MusicConnector.addLibrary(LibraryMainActivity.this, new Runnable() {
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

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        initProvidersList();
        initCurrentProvider();
        final Intent intent = getIntent();
        final String action = intent.getAction();

        if(Intent.ACTION_VIEW.equals(action)){
            for (int index = 0 ; index < PlayerApplication.mediaManagers.length ; index++) {
                final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[index];

                if (mediaManager.getMediaManagerType() == AbstractMediaManager.LOCAL_MEDIA_MANAGER) {
                    if (PlayerApplication.libraryManagerIndex != PlayerApplication.playerManagerIndex) {
                        MusicConnector.sendStopIntent();
                        PlayerApplication.playerManagerIndex = PlayerApplication.libraryManagerIndex;
                        PlayerApplication.saveLibraryIndexes();

                        PlayerApplication.playerService.queueReload();
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

                    mLibraryAdapter.refresh();

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

    protected void updateReloadMenuItem() {
        if (mReloadMenuItem != null) {
            if (PlayerApplication.mediaManagers[PlayerApplication.getLibraryLibraryIndex()].getProvider().scanIsRunning()) {
                mReloadMenuItem.setTitle(R.string.menuitem_label_cancel_reload);
                mReloadMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_close_black_48dp : R.drawable.ic_close_white_48dp);
            } else {
                mReloadMenuItem.setTitle(R.string.menuitem_label_reload);
                mReloadMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_refresh_black_48dp : R.drawable.ic_refresh_white_48dp);
            }
        }
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

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        // Only show tabs that were set in preferences
        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST)) {
            mLibraryAdapter.addFragment(new PlaylistFragment(), null, R.string.tab_label_playlists);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST)) {
            mLibraryAdapter.addFragment(new ArtistFragment(), null, R.string.tab_label_artists);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST)) {
            mLibraryAdapter.addFragment(new AlbumArtistFragment(), null, R.string.tab_label_album_artists);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM)) {
            mLibraryAdapter.addFragment(new AlbumFragment(), null, R.string.tab_label_albums);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA)) {
            mLibraryAdapter.addFragment(new SongFragment(), null, R.string.tab_label_songs);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE)) {
            mLibraryAdapter.addFragment(new GenreFragment(), null, R.string.tab_label_genres);
        }

        if (provider.hasContentType(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE)) {
            mLibraryAdapter.addFragment(new StorageFragment(), null, R.string.tab_label_storage);
        }

        mLibraryPager.setAdapter(mLibraryAdapter);
        mScrollingTabs.setViewPager(mLibraryPager);
    }


    protected void initProvidersList() {
        SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getReadableDatabase();

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

        if (mNavigationCursor != null) {
            try {
                mNavigationCursor.moveToPosition(PlayerApplication.libraryManagerIndex);

                getSupportActionBar().setTitle(mNavigationCursor.getString(1));
                getSupportActionBar().setSubtitle(MediaManagerFactory.getDescriptionFromType(mNavigationCursor.getInt(2)));
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
    private final MenuItem.OnMenuItemClickListener hiddenOnMenuItemClickListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            boolean show_hidden = !item.isChecked();

            item.setChecked(show_hidden);
            MusicConnector.show_hidden = show_hidden;
            mLibraryAdapter.refresh();
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

            final Class<?> currentItemClass = ((Object) mLibraryAdapter.getItem(mLibraryPager.getCurrentItem())).getClass();

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

            mLibraryAdapter.refresh();
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

            mLibraryAdapter.refresh();
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

            mLibraryAdapter.refresh();
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

            mLibraryAdapter.refresh();
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

            mLibraryAdapter.refresh();
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

            mLibraryAdapter.refresh();
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

            mLibraryAdapter.refresh();
            dialog.dismiss();
        }
    };

    private AdapterView.OnItemClickListener navigationDrawerListOnItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            for (int searchIndex = 0 ; searchIndex < PlayerApplication.mediaManagers.length ; searchIndex++) {
                int currentId = PlayerApplication.mediaManagers[searchIndex].getMediaManagerId();

                if (currentId == id) {
                    id = searchIndex;
                    break;
                }
            }

            PlayerApplication.libraryManagerIndex = (int) id;
            initCurrentProvider();
            PlayerApplication.saveLibraryIndexes();

            mDrawerLayout.closeDrawers();
        }
    };


    private AbstractMediaManager.Provider.OnLibraryChangeListener libraryChangeListener = new AbstractMediaManager.Provider.OnLibraryChangeListener() {

        @Override
        public void libraryChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLibraryAdapter.refresh();
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
}