package net.opusapp.player.core.service.providers.deezer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.text.TextUtils;

import com.deezer.sdk.model.Album;
import com.deezer.sdk.model.Artist;
import com.deezer.sdk.model.Genre;
import com.deezer.sdk.model.Playlist;
import com.deezer.sdk.network.connect.DeezerConnect;
import com.deezer.sdk.network.connect.SessionStore;
import com.deezer.sdk.network.request.DeezerRequest;
import com.deezer.sdk.network.request.DeezerRequestFactory;
import com.deezer.sdk.network.request.JsonUtils;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.deezer.ui.activities.ConnectionActivity;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.utils.LogUtils;
import net.opusapp.player.utils.backport.android.content.SharedPreferencesCompat;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeezerProvider implements AbstractMediaManager.Provider {

    private final static String TAG = DeezerProvider.class.getSimpleName();



    private DeezerMediaManager mediaManager;



    private final static String APPLICATION_ID = "102481";

    private final DeezerConnect deezerConnect;

    private boolean authorized = false;


    public final AlbumArtistEmptyAction EMPTY_ACTION_ALBUM_ARTIST = new AlbumArtistEmptyAction();

    public final AlbumEmptyAction EMPTY_ACTION_ALBUM = new AlbumEmptyAction();

    public final ArtistEmptyAction EMPTY_ACTION_ARTIST = new ArtistEmptyAction();

    public final GenreEmptyAction EMPTY_ACTION_GENRE = new GenreEmptyAction();

    public final SongEmptyAction EMPTY_ACTION_SONG = new SongEmptyAction();

    public final NotConnectedEmptyAction EMPTY_ACTION_NOT_CONNECTED = new NotConnectedEmptyAction();



    public AbstractMediaManager.ProviderAction ACTION_LIST[] = new AbstractMediaManager.ProviderAction[] {
            new ConnectionAction()
    };

    private ArrayList<OnLibraryChangeListener> scanListeners;


    public DeezerProvider(DeezerMediaManager mediaManager) {
        this.mediaManager = mediaManager;
        deezerConnect = new DeezerConnect(PlayerApplication.context, APPLICATION_ID);

        scanListeners = new ArrayList<OnLibraryChangeListener>();

        SessionStore sessionStore = new SessionStore();
        if (sessionStore.restore(deezerConnect, PlayerApplication.context)) {
            authorized = true;
        }
    }

    public DeezerConnect getDeezerConnect() {
        return deezerConnect;
    }

    @Override
    public void erase() {
        File filePath = PlayerApplication.context.getFilesDir();
        if (filePath != null) {
            File providerPrefs = new File(filePath.getPath() + "/shared_prefs/provider-" + mediaManager.getMediaManagerId() + ".xml");
            if (!providerPrefs.delete()) {
                LogUtils.LOGE(TAG, "deleting provider-" + mediaManager.getMediaManagerId() + " preferences failed");
            }
        }
    }

    @Override
    public boolean scanStart() {

        for (OnLibraryChangeListener libraryChangeListener : scanListeners) {
            libraryChangeListener.libraryChanged();
        }

        return true;
    }

    @Override
    public boolean scanCancel() {
        return true;
    }

    @Override
    public boolean scanIsRunning() {
        return false;
    }

    @Override
    public void addLibraryChangeListener(OnLibraryChangeListener libraryChangeListener) {
        if (scanListeners.indexOf(libraryChangeListener) < 0) {
            scanListeners.add(libraryChangeListener);
        }
    }

    @Override
    public void removeLibraryChangeListener(OnLibraryChangeListener libraryChangeListener) {
        scanListeners.remove(libraryChangeListener);
    }

    @Override
    public Cursor buildCursor(ContentType contentType, int[] fields, int[] sortFields, String filter, ContentType source, String sourceId) {
        if (source == null) {
            source = ContentType.CONTENT_TYPE_DEFAULT;
        }

        switch (contentType) {
            case CONTENT_TYPE_ALBUM:
                return doBuildAlbumCursor(fields, sortFields, filter, source, sourceId);
            case CONTENT_TYPE_ARTIST:
                return doBuildArtistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_GENRE:
                return doBuildGenreCursor(fields, sortFields, filter);
            case CONTENT_TYPE_PLAYLIST:
                return doBuildPlaylistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_MEDIA:
                return doBuildMediaCursor(fields, sortFields, filter, source, sourceId);
            case CONTENT_TYPE_STORAGE:
                return doBuildStorageCursor(fields, sortFields, filter);
        }

        return null;
    }

    @Override
    public boolean play(ContentType contentType, String sourceId, int sortOrder, int position, String filter) {
        return false;
    }

    @Override
    public boolean playNext(ContentType contentType, String sourceId, int sortOrder, String filter) {
        return false;
    }

    @Override
    public AbstractMediaManager.Media[] getCurrentPlaylist(AbstractMediaManager.Player player) {
        return new AbstractMediaManager.Media[0];
    }

    @Override
    public String playlistNew(String playlistName) {
        return null;
    }

    @Override
    public boolean playlistDelete(String playlistId) {
        return false;
    }

    @Override
    public boolean playlistAdd(String playlistId, ContentType contentType, String sourceId, int sortOrder, String filter) {
        return false;
    }

    @Override
    public void playlistMove(String playlistId, int moveFrom, int moveTo) {

    }

    @Override
    public void playlistRemove(String playlistId, int position) {

    }

    @Override
    public void playlistClear(String playlistId) {

    }

    @Override
    public boolean hasFeature(Feature feature) {
        switch (feature) {
            case CONSTRAINT_REQUIRE_SD_CARD:
            case CONSTRAINT_REQUIRE_CONNECTION:
            case SUPPORT_HIDING:
            case SUPPORT_ART:
            case SUPPORT_CONFIGURATION:
                return false;
        }
        return false;
    }

    @Override
    public void setProperty(ContentType contentType, Object target, ContentProperty key, Object object, Object options) {

    }

    @Override
    public Object getProperty(ContentType contentType, Object target, ContentProperty key) {
        switch (key) {
        case CONTENT_STORAGE_HAS_PARENT:
            return false;
        case CONTENT_STORAGE_HAS_CHILD:
            return false;
        case CONTENT_STORAGE_CURRENT_LOCATION:
            return "deezer://radio";
        }
        return null;
    }

    @Override
    public boolean hasContentType(ContentType contentType) {
        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getMediaManagerId(), Context.MODE_PRIVATE);

        final String[] tabTitles = resources.getStringArray(R.array.preference_values_tab_visibility);

        Set<String> defaultTabs = new HashSet<String>(Arrays.asList(tabTitles));
        Set<String> userEnabledTabs = SharedPreferencesCompat.getStringSet(
                sharedPrefs, resources.getString(R.string.preference_key_tab_visibility), defaultTabs);

        if (userEnabledTabs.size() == 0) {
            userEnabledTabs = defaultTabs;
        }

        switch (contentType) {
            case CONTENT_TYPE_PLAYLIST:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_playlists));
            case CONTENT_TYPE_ARTIST:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_artists));
            case CONTENT_TYPE_ALBUM:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_albums));
            case CONTENT_TYPE_MEDIA:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_songs));
            case CONTENT_TYPE_GENRE:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_genres));
            case CONTENT_TYPE_STORAGE:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_storage));
        }

        return false;
    }

    @Override
    public AbstractMediaManager.AbstractEmptyContentAction getEmptyContentAction(ContentType contentType) {
        if (authorized) {
            switch (contentType) {
                case CONTENT_TYPE_ARTIST:
                    return EMPTY_ACTION_ARTIST;
                case CONTENT_TYPE_ALBUM_ARTIST:
                    return EMPTY_ACTION_ALBUM_ARTIST;
                case CONTENT_TYPE_ALBUM:
                    return EMPTY_ACTION_ALBUM;
                case CONTENT_TYPE_MEDIA:
                    return EMPTY_ACTION_SONG;
                case CONTENT_TYPE_GENRE:
                    return EMPTY_ACTION_GENRE;
            }
        }

        return EMPTY_ACTION_NOT_CONNECTED;
    }

    @Override
    public AbstractMediaManager.ProviderAction getAbstractProviderAction(int index) {
        return ACTION_LIST[index];
    }

    @Override
    public AbstractMediaManager.ProviderAction[] getAbstractProviderActionList() {
        return ACTION_LIST;
    }

    @Override
    public void databaseMaintain() {
        // nothing to be done.
    }



    @SuppressWarnings("unchecked")
    protected Cursor doBuildAlbumCursor(final int[] requestedFields, final int[] sortFields, String filter, final ContentType source, final String sourceId) {
        // TODO: implement source
        DeezerRequest request = DeezerRequestFactory.requestCurrentUserAlbums();

        if (!TextUtils.isEmpty(filter)) {
            request = DeezerRequestFactory.requestSearchAlbums(filter);
        }
        List<Album> albumList;

        try {
            final String result = deezerConnect.requestSync(request);
            JSONObject jsonObject = new JSONObject(result);

            albumList = (List<Album>) JsonUtils.deserializeArray(jsonObject.optJSONArray("data"));

            Collections.sort(albumList, new Comparator<Album>() {
                @Override
                public int compare(Album lhs, Album rhs) {
                    if (lhs != null && rhs != null) {
                        switch (sortFields[0]) {
                            case ALBUM_NAME:
                                return lhs.getTitle().compareTo(rhs.getTitle());
                            case -ALBUM_NAME:
                                return -lhs.getTitle().compareTo(rhs.getTitle());
                            case ALBUM_ARTIST:
                                return lhs.getArtist().getName().compareTo(rhs.getArtist().getName());
                            case -ALBUM_ARTIST:
                                return -lhs.getArtist().getName().compareTo(rhs.getArtist().getName());
                            default:
                                throw new IllegalArgumentException();
                        }
                    }
                    return 0;
                }
            });
        }
        catch (final Exception exception) {
            albumList = Collections.emptyList();
            LogUtils.LOGException(TAG, "buildCursor", 0, exception);
        }

        final String[] columns = new String[requestedFields.length + 1];
        final Object[] currentRow = new Object[requestedFields.length + 1];

        columns[requestedFields.length] = "_id";
        for (int columnIndex = 0 ; columnIndex < requestedFields.length ; columnIndex++) {
            columns[columnIndex] = String.valueOf(requestedFields[columnIndex]);
        }

        final MatrixCursor cursor = new MatrixCursor(columns);
        currentRow[requestedFields.length] = 0;

        for (final Album album : albumList) {
            for (int columnIndex = 0; columnIndex < requestedFields.length; columnIndex++) {
                switch (requestedFields[columnIndex]) {
                    case AbstractMediaManager.Provider.ALBUM_ID:
                        currentRow[columnIndex] = album.getId();
                        break;
                    case AbstractMediaManager.Provider.ALBUM_NAME:
                        currentRow[columnIndex] = album.getTitle();
                        break;
                    case AbstractMediaManager.Provider.ALBUM_ARTIST:
                        currentRow[columnIndex] = album.getArtist();
                        break;
                    case AbstractMediaManager.Provider.ALBUM_VISIBLE:
                        currentRow[columnIndex] = 1;
                        break;
                }
            }
            cursor.addRow(currentRow);
        }

        return cursor;
    }

    @SuppressWarnings("unchecked")
    protected Cursor doBuildArtistCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        DeezerRequest request = DeezerRequestFactory.requestCurrentUserArtists();

        if (!TextUtils.isEmpty(filter)) {
            request = DeezerRequestFactory.requestSearchArtists(filter);
        }

        List<Artist> artistList;

        try {
            final String result = deezerConnect.requestSync(request);
            JSONObject jsonObject = new JSONObject(result);

            artistList = (List<Artist>) JsonUtils.deserializeArray(jsonObject.optJSONArray("data"));

            if (sortFields != null && sortFields.length > 0) {
                Collections.sort(artistList, new Comparator<Artist>() {
                    @Override
                    public int compare(Artist lhs, Artist rhs) {
                        if (lhs != null && rhs != null) {
                            switch (sortFields[0]) {
                                case ARTIST_NAME:
                                    return lhs.getName().compareTo(rhs.getName());
                                case -ARTIST_NAME:
                                    return -lhs.getName().compareTo(rhs.getName());
                                default:
                                    throw new IllegalArgumentException();
                            }
                        }
                        return 0;
                    }
                });
            }
        }
        catch (final Exception exception) {
            artistList = Collections.emptyList();
            LogUtils.LOGException(TAG, "buildCursor", 0, exception);
        }

        final String[] columns = new String[requestedFields.length + 1];
        final Object[] currentRow = new Object[requestedFields.length + 1];

        columns[requestedFields.length] = "_id";
        for (int columnIndex = 0 ; columnIndex < requestedFields.length ; columnIndex++) {
            columns[columnIndex] = String.valueOf(requestedFields[columnIndex]);
        }

        final MatrixCursor cursor = new MatrixCursor(columns);
        currentRow[requestedFields.length] = 0;

        for (final Artist artist : artistList) {
            for (int columnIndex = 0; columnIndex < requestedFields.length; columnIndex++) {
                switch (requestedFields[columnIndex]) {
                    case AbstractMediaManager.Provider.ARTIST_ID:
                        currentRow[columnIndex] = artist.getId();
                        break;
                    case AbstractMediaManager.Provider.ARTIST_NAME:
                        currentRow[columnIndex] = artist.getName();
                        break;
                    // TODO: add optional artist cover support.
                    case AbstractMediaManager.Provider.ARTIST_VISIBLE:
                        currentRow[columnIndex] = 1;
                        break;
                }
            }
            cursor.addRow(currentRow);
        }

        return cursor;
    }

    @SuppressWarnings("unchecked")
    protected Cursor doBuildGenreCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        DeezerRequest request = DeezerRequestFactory.requestGenres();
        List<Genre> genreList;

        try {
            final String result = deezerConnect.requestSync(request);
            JSONObject jsonObject = new JSONObject(result);

            genreList = (List<Genre>) JsonUtils.deserializeArray(jsonObject.optJSONArray("data"));

            if (sortFields != null && sortFields.length > 0) {
                Collections.sort(genreList, new Comparator<Genre>() {
                    @Override
                    public int compare(Genre lhs, Genre rhs) {
                        if (lhs != null && rhs != null) {
                            switch (sortFields[0]) {
                                case GENRE_NAME:
                                    return lhs.getName().compareTo(rhs.getName());
                                case -GENRE_NAME:
                                    return -lhs.getName().compareTo(rhs.getName());
                                default:
                                    throw new IllegalArgumentException();
                            }
                        }
                        return 0;
                    }
                });
            }
        }
        catch (final Exception exception) {
            genreList = Collections.emptyList();
            LogUtils.LOGException(TAG, "buildCursor", 0, exception);
        }

        final String[] columns = new String[requestedFields.length + 1];
        final Object[] currentRow = new Object[requestedFields.length + 1];

        columns[requestedFields.length] = "_id";
        for (int columnIndex = 0 ; columnIndex < requestedFields.length ; columnIndex++) {
            columns[columnIndex] = String.valueOf(requestedFields[columnIndex]);
        }

        final MatrixCursor cursor = new MatrixCursor(columns);
        currentRow[requestedFields.length] = 0;

        for (final Genre genre : genreList) {
            if (!TextUtils.isEmpty(filter)) {
                if (!genre.getName().toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }
            }

            for (int columnIndex = 0; columnIndex < requestedFields.length; columnIndex++) {
                switch (requestedFields[columnIndex]) {
                    case AbstractMediaManager.Provider.GENRE_ID:
                        currentRow[columnIndex] = genre.getId();
                        break;
                    case AbstractMediaManager.Provider.GENRE_NAME:
                        currentRow[columnIndex] = genre.getName();
                        break;
                    case AbstractMediaManager.Provider.GENRE_VISIBLE:
                        currentRow[columnIndex] = 1;
                        break;
                }
            }
            cursor.addRow(currentRow);
        }

        return cursor;
    }

    @SuppressWarnings("unchecked")
    protected Cursor doBuildPlaylistCursor(int[] requestedFields, final int[] sortFields, String filter) {
        DeezerRequest request = DeezerRequestFactory.requestCurrentUserPlaylists();

        List<Playlist> playlistList;

        try {
            final String result = deezerConnect.requestSync(request);
            JSONObject jsonObject = new JSONObject(result);

            playlistList = (List<Playlist>) JsonUtils.deserializeArray(jsonObject.optJSONArray("data"));

            if (sortFields != null && sortFields.length > 0) {
                Collections.sort(playlistList, new Comparator<Playlist>() {
                    @Override
                    public int compare(Playlist lhs, Playlist rhs) {
                        if (lhs != null && rhs != null) {
                            switch (sortFields[0]) {
                                case PLAYLIST_NAME:
                                    return lhs.getTitle().compareTo(rhs.getTitle());
                                case -PLAYLIST_NAME:
                                    return -lhs.getTitle().compareTo(rhs.getTitle());
                                default:
                                    throw new IllegalArgumentException();
                            }
                        }
                        return 0;
                    }
                });
            }
        }
        catch (final Exception exception) {
            playlistList = Collections.emptyList();
            LogUtils.LOGException(TAG, "buildCursor", 0, exception);
        }

        final String[] columns = new String[requestedFields.length + 1];
        final Object[] currentRow = new Object[requestedFields.length + 1];

        columns[requestedFields.length] = "_id";
        for (int columnIndex = 0 ; columnIndex < requestedFields.length ; columnIndex++) {
            columns[columnIndex] = String.valueOf(requestedFields[columnIndex]);
        }

        final MatrixCursor cursor = new MatrixCursor(columns);
        currentRow[requestedFields.length] = 0;

        for (final Playlist playlist : playlistList) {
            if (!TextUtils.isEmpty(filter)) {
                if (!playlist.getTitle().toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }
            }

            for (int columnIndex = 0; columnIndex < requestedFields.length; columnIndex++) {
                switch (requestedFields[columnIndex]) {
                    case AbstractMediaManager.Provider.PLAYLIST_ID:
                        currentRow[columnIndex] = playlist.getId();
                        break;
                    case AbstractMediaManager.Provider.PLAYLIST_NAME:
                        currentRow[columnIndex] = playlist.getTitle();
                        break;
                    case AbstractMediaManager.Provider.PLAYLIST_VISIBLE:
                        currentRow[columnIndex] = 1;
                        break;
                }
            }
            cursor.addRow(currentRow);
        }

        return cursor;
    }

    protected Cursor doBuildMediaCursor(int[] requestedFields, int[] sortFields, String filter, ContentType contentType, String sourceId) {
        return null;
    }

    protected Cursor doBuildStorageCursor(int[] requestedFields, int[] sortFields, String filter) {
        return null;
    }



    public class ConnectionAction implements AbstractMediaManager.ProviderAction {

        @Override
        public String getDescription() {
            if (authorized) {
                return PlayerApplication.context.getString(R.string.activity_label_deezer_signout);
            }

            return PlayerApplication.context.getString(R.string.activity_label_deezer_signin);
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public void launch(Activity source) {
            final Intent intent = new Intent(PlayerApplication.context, ConnectionActivity.class);
            intent.putExtra(KEY_PROVIDER_ID, mediaManager.getMediaManagerId());
            source.startActivityForResult(intent, ACTIVITY_NEED_UI_REFRESH);
        }
    }

    public class AlbumArtistEmptyAction extends ConnectionAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_artists);
        }

        @Override
        public String getActionDescription() {
            return super.getDescription();
        }
    }

    public class AlbumEmptyAction extends ConnectionAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_albums);
        }

        @Override
        public String getActionDescription() {
            return super.getDescription();
        }
    }

    public class ArtistEmptyAction extends ConnectionAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_artists);
        }

        @Override
        public String getActionDescription() {
            return super.getDescription();
        }
    }

    public class GenreEmptyAction extends ConnectionAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_genres);
        }

        @Override
        public String getActionDescription() {
            return super.getDescription();
        }
    }

    public class SongEmptyAction extends ConnectionAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_songs);
        }

        @Override
        public String getActionDescription() {
            return super.getDescription();
        }
    }

    public class NotConnectedEmptyAction extends ConnectionAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_songs);
        }

        @Override
        public String getActionDescription() {
            return super.getDescription();
        }
    }



    public static class RadioDescriptor {

        public String description;

        public int id;

        public RadioDescriptor(int description, int id) {
            this.description = PlayerApplication.context.getString(description);
            this.id = id;
        }
    }

    private static final RadioDescriptor[] sourceDescription = new RadioDescriptor[] {
            new RadioDescriptor(R.string.label_deezer_storage_thematic_radio, 0),
            new RadioDescriptor(R.string.label_deezer_storage_artist_radio, 1),
    };

    private int radioPath = RADIO_PATH_INDEX;

    private static final int RADIO_PATH_INDEX = 0;

    private static final int RADIO_PATH_THEMATIC = 1;

    private static final int RADIO_PATH_ARTIST = 2;
}
