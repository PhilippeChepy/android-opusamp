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
    private DrawerLayout drawerLayout;

    private Cursor navigationCursor;

    private ProviderAdapter navigationAdapter;



    // Drawer
    private static final String SAVED_STATE_ACTION_PLAYER_PANEL_IS_HIDDEN = "player_panel_is_hidden";

    private boolean hiddenPanel;

    private ActionBarDrawerToggle drawerToggle;



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
        sortMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_action_sort_2 : R.drawable.ic_action_sort_2_dark);
        MenuItemCompat.setShowAsAction(sortMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        sortMenuItem.setOnMenuItemClickListener(onSortOptionMenuItemListener);

        searchMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_FILTER, 3, R.string.menuitem_label_filter);
        searchMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_action_search : R.drawable.ic_action_search_dark);
        MenuItemCompat.setActionView(searchMenuItem, searchView);
        MenuItemCompat.setShowAsAction(searchMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        final MenuItem hiddenMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SHOW_HIDDEN, 4, R.string.menuitem_label_show_hidden);
        hiddenMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_action_show : R.drawable.ic_action_show_dark);
        hiddenMenuItem.setCheckable(true);
        MenuItemCompat.setShowAsAction(hiddenMenuItem, MenuItemCompat.SHOW_AS_ACTION_NEVER);
        hiddenMenuItem.setOnMenuItemClickListener(hiddenOnMenuItemClickListener);

        doManageMenuitemVisibility(libraryAdapter, libraryPager.getCurrentItem());

        reloadMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_RELOAD, 6, R.string.menuitem_label_reload);
        reloadMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_action_reload : R.drawable.ic_action_reload_dark);
        MenuItemCompat.setShowAsAction(reloadMenuItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        reloadMenuItem.setOnMenuItemClickListener(reloadOnMenuItemClickListener);

        updateReloadMenuItem();

        int menuIndex = 7;
/*
        final AbstractMediaManager.Provider provider = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex].getProvider();
        final AbstractMediaManager.ProviderAction providerActionList[] = provider.getActionList();

        if (providerActionList != null) {
            for (int index = 0; index < providerActionList.length; index++) {
                final AbstractMediaManager.ProviderAction providerAction = providerActionList[index];

                if (providerAction.isVisible()) {
                    final MenuItem menuItem = menu.add(Menu.NONE, 200 + index, menuIndex++, providerAction.getDescription());
                    menuItem.setIcon(R.drawable.ic_action_tiles_large);
                    menuItem.setOnMenuItemClickListener(new ActionMenuItemClickListener(index));
                }
            }
        }
*/
        final MenuItem settingsMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_APPLICATION_SETTINGS_ID, menuIndex, R.string.drawer_item_label_settings);
        settingsMenuItem.setIcon(R.drawable.ic_action_settings);
        settingsMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                startActivityForResult(new Intent(PlayerApplication.context, SettingsActivity.class), OPTION_MENUITEM_LIBRARY_SETTINGS_ID);
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
        libraryPager = (ViewPager) findViewById(R.id.pager_viewpager);
        libraryPager.setPageMargin(getResources().getInteger(R.integer.viewpager_margin_width));

        scrollingTabs = (PagerSlidingTabStrip) findViewById(R.id.pager_tabs);
        scrollingTabs.setOnPageChangeListener(scrollingTabsOnPageChangeListener);
        scrollingTabs.setIndicatorColorResource(R.color.materialAccentColor);
        scrollingTabs.setTextColor(getResources().getColor(R.color.tabTextColor));
        PlayerApplication.applyThemeOnPagerTabs(scrollingTabs);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.actionbar_drawer_toggle_label_open, R.string.actionbar_drawer_toggle_label_close) {

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, slideOffset);
                doPlayerPanelPlayManagement(slideOffset);
            }
        };

        navigationAdapter = new ProviderAdapter(this, R.layout.view_item_double_line_no_anchor, null);

        final View footerView = getLayoutInflater().inflate(R.layout.view_item_settings, null, false);
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

        final ListView drawerList = (ListView) findViewById(R.id.list_drawer);
        drawerList.setAdapter(navigationAdapter);
        drawerList.setOnItemClickListener(navigationDrawerListOnItemClickListener);
        drawerList.addFooterView(footerView, null, true);

        drawerLayout.setDrawerListener(drawerToggle);

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

                    libraryAdapter.refresh();

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
        hiddenPanel = savedInstanceState.getBoolean(SAVED_STATE_ACTION_PLAYER_PANEL_IS_HIDDEN, false);
    }

    @Override
    protected boolean canShowPanel() {
        return !hiddenPanel;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return drawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
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

    protected void updateReloadMenuItem() {
        if (reloadMenuItem != null) {
            if (PlayerApplication.mediaManagers[PlayerApplication.getLibraryLibraryIndex()].getProvider().scanIsRunning()) {
                reloadMenuItem.setTitle(R.string.menuitem_label_cancel_reload);
                reloadMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_action_cancel : R.drawable.ic_action_cancel_dark);
            } else {
                reloadMenuItem.setTitle(R.string.menuitem_label_reload);
                reloadMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_action_reload : R.drawable.ic_action_reload_dark);
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
        if (libraryAdapter != null) {
            libraryAdapter.refresh();
        }
    }


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

            navigationCursor = cursor;
            if (cursor != null) {
                navigationAdapter.changeCursor(cursor);
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

        if (navigationCursor != null) {
            try {
                navigationCursor.moveToPosition(PlayerApplication.libraryManagerIndex);

                getSupportActionBar().setTitle(navigationCursor.getString(1));
                getSupportActionBar().setSubtitle(MediaManagerFactory.getDescriptionFromType(navigationCursor.getInt(2)));
            } catch (final Exception exception) {
                LogUtils.LOGException(TAG, "initCurrentProvider", 0, exception);
            }
        }
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
            libraryAdapter.refresh();
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
            libraryAdapter.refresh();
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
            libraryAdapter.refresh();
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

            libraryAdapter.refresh();
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

            libraryAdapter.refresh();
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

            libraryAdapter.refresh();
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

            libraryAdapter.refresh();
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

            libraryAdapter.refresh();
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

            libraryAdapter.refresh();
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

            libraryAdapter.refresh();
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

            drawerLayout.closeDrawers();
        }
    };


    private AbstractMediaManager.Provider.OnLibraryChangeListener libraryChangeListener = new AbstractMediaManager.Provider.OnLibraryChangeListener() {

        @Override
        public void libraryChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    libraryAdapter.refresh();
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