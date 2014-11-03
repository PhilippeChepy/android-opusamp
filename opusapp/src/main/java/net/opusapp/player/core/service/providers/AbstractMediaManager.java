/*
 * AbstractMediaManager.java
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
package net.opusapp.player.core.service.providers;

import android.app.Activity;
import android.database.Cursor;

import net.opusapp.player.ui.views.RefreshableView;

public interface AbstractMediaManager {

    public static final int INVALID_MEDIA_MANAGER = -1;

    public static final int LOCAL_MEDIA_MANAGER = 1;

    public static final int DEEZER_MEDIA_MANAGER = 3;



    public int getMediaManagerType();

    public int getMediaManagerId();

    public Provider getProvider();

    public Player getPlayer();



    public abstract class Media {
        public static final String TAG = Media.class.getSimpleName();

        public abstract String getUri();

        public abstract boolean isLoaded();

        public abstract void load();

        public abstract void unload();

        public String name = null;

        public String album = null;

        public String artist = null;

        public String artUri = null;

        public long duration = 0;
    }



    public interface Provider {



        public static final String KEY_PROVIDER_ID = "providerId";

        public static final String KEY_SOURCE_ID = "sourceId";

        public static final String KEY_SELECTED_ART_URI = "selectedUri";

        public static final String KEY_SELECTED_ART_ID = "selectedId";

        public static int ACTIVITY_NEED_UI_REFRESH = 200;



        public void erase();



        /*
            Library management
         */
        public boolean scanStart();
        public boolean scanCancel();
        public boolean scanIsRunning();


        public void addLibraryChangeListener(OnLibraryChangeListener libraryChangeListener);
        public void removeLibraryChangeListener(OnLibraryChangeListener libraryChangeListener);


        public static final int ALBUM_ARTIST_ID = 1;
        public static final int ALBUM_ARTIST_NAME = 2;
        public static final int ALBUM_ARTIST_VISIBLE = 3;



        public static final int ALBUM_ID = 101;
        public static final int ALBUM_NAME = 102;
        public static final int ALBUM_ARTIST = 103;
        public static final int ALBUM_ART_URI = 104;
        public static final int ALBUM_VISIBLE = 105;



        public static final int ARTIST_ID = 201;
        public static final int ARTIST_NAME = 202;
        public static final int ARTIST_VISIBLE = 203;



        public static final int GENRE_ID = 301;
        public static final int GENRE_NAME = 302;
        public static final int GENRE_VISIBLE = 303;



        public static final int PLAYLIST_ID = 401;
        public static final int PLAYLIST_NAME = 402;
        public static final int PLAYLIST_REPEAT_MODE = 403; /* unimplemented */
        public static final int PLAYLIST_SHUFFLE_MODE = 404; /* unimplemented */
        public static final int PLAYLIST_VISIBLE = 405;



        public static final int SONG_ID = 501;
        public static final int SONG_URI = 502;
        public static final int SONG_ART_URI = 503;
        public static final int SONG_DURATION = 504;
        public static final int SONG_BITRATE = 505;
        public static final int SONG_SAMPLE_RATE = 506;
        public static final int SONG_CODEC = 507;
        public static final int SONG_SCORE = 508;
        public static final int SONG_FIRST_PLAYED = 509;
        public static final int SONG_LAST_PLAYED = 510;
        public static final int SONG_TITLE = 511;
        public static final int SONG_ARTIST = 512;
        public static final int SONG_ARTIST_ID = 513;
        //public static final int SONG_COMPOSER = 514;  /* unimplemented */
        //public static final int SONG_COMPOSER_ID = 515;  /* unimplemented */
        public static final int SONG_ALBUM_ARTIST = 516;
        public static final int SONG_ALBUM_ARTIST_ID = 517;
        public static final int SONG_ALBUM = 518;
        public static final int SONG_ALBUM_ID = 519;
        public static final int SONG_GENRE = 520;
        public static final int SONG_GENRE_ID = 521;
        public static final int SONG_YEAR = 522;
        public static final int SONG_TRACK = 523;
        public static final int SONG_DISC = 524;
        public static final int SONG_BPM = 525;
        public static final int SONG_COMMENT = 526;
        public static final int SONG_LYRICS = 527;
        public static final int SONG_VISIBLE = 528;

        public static final int PLAYLIST_ENTRY_ID = 601;
        public static final int PLAYLIST_ENTRY_POSITION = 602;

        public static final int STORAGE_ID = 701;
        public static final int STORAGE_DISPLAY_NAME = 702;
        public static final int STORAGE_DISPLAY_DETAIL = 703;



        public Cursor buildCursor(ContentType contentType, final int[] fields, final int[] sortFields, final String filter, ContentType source, String sourceId);
        public boolean play(ContentType contentType, String sourceId, int sortOrder, int position, String filter);
        public boolean playNext(ContentType contentType, String sourceId, int sortOrder, String filter);



        /*
            Playlist management
         */
        public Media[] getCurrentPlaylist(Player player);
        public String playlistNew(String playlistName);
        public boolean playlistDelete(String playlistId);
        public boolean playlistAdd(String playlistId, ContentType contentType, String sourceId, int sortOrder, String filter);
        public void playlistMove(String playlistId, int moveFrom, int moveTo);
        public void playlistRemove(String playlistId, int position);
        public void playlistClear(String playlistId);


        public boolean hasFeature(Feature feature);
        public void setProperty(ContentType contentType, Object target, ContentProperty key, Object object, Object options);
        public Object getProperty(ContentType contentType, Object target, ContentProperty key);
        public String getAlbumArtUri(String albumId);

        public boolean hasContentType(ContentType contentType);

        public AbstractEmptyContentAction getEmptyContentAction(ContentType contentType);
        public ProviderAction getAbstractProviderAction(int index);
        public ProviderAction[] getAbstractProviderActionList();


        public void changeAlbumArt(Activity sourceActivity, RefreshableView sourceRefreshable, String albumId, boolean restore);

        public void databaseMaintain();



        public enum ContentProperty {
            CONTENT_VISIBILITY_TOGGLE,          /* write only */
            CONTENT_ART_URI,                    /* write only */
            CONTENT_ART_ORIGINAL_URI,           /* write only */
            CONTENT_ART_STREAM,                 /* read only */
            CONTENT_METADATA_LIST,              /* read only */

            CONTENT_STORAGE_UPDATE_VIEW,        /* write only */
            CONTENT_STORAGE_HAS_PARENT,         /* read only */
            CONTENT_STORAGE_HAS_CHILD,          /* read only */
            CONTENT_STORAGE_CURRENT_LOCATION,   /* read/write only */
            CONTENT_STORAGE_RESOURCE_POSITION,  /* read only */
        }

        /*
            Other content management routines
         */
        public enum Feature {
            CONSTRAINT_REQUIRE_SD_CARD,         /* unimplemented */
            CONSTRAINT_REQUIRE_CONNECTION,      /* unimplemented */

            SUPPORT_HIDING,                     /* unimplemented */
            SUPPORT_ART,                        /* unimplemented */
            SUPPORT_CONFIGURATION               /* unimplemented */
        }

        /*
            Library content management
         */
        public enum ContentType {
            CONTENT_TYPE_DEFAULT,
            CONTENT_TYPE_PLAYLIST,
            CONTENT_TYPE_ARTIST,
            CONTENT_TYPE_ALBUM_ARTIST,
            CONTENT_TYPE_ALBUM,
            CONTENT_TYPE_MEDIA,
            CONTENT_TYPE_GENRE,
            CONTENT_TYPE_STORAGE,
            CONTENT_TYPE_ART,
        }


        public interface OnLibraryChangeListener {
            public void libraryChanged();
            public void libraryScanStarted();
            public void libraryScanFinished();
        }
    }



    public interface Player {

        /*
            Stream management
         */
        public boolean loadMedia(Media media);
        public void unloadMedia(Media media);



        /*
            MediaPlayer management
         */
        public void playerSetContent(Media context);
        public void playerPlay();
        public void playerPause(boolean setPaused);
        public void playerStop();
        public boolean playerIsPlaying();
        public void playerSeek(Media context, long position);
        public long playerGetPosition();
        public long playerGetDuration();

        public boolean equalizerIsEnabled();
        public long equalizerSetEnabled(boolean enabled);
        public long equalizerBandSetGain(int band, long gain);
        public long equalizerBandGetGain(int band);
        public boolean equalizerApplyProperties();

        void addCompletionListener(OnProviderCompletionListener listener);
        void removeCompletionListener(OnProviderCompletionListener listener);
        void resetListeners();

        public interface OnProviderCompletionListener {
            public void onCodecCompletion();
            public void onCodecTimestampUpdate(long newPosition);
        }
    }

    public interface ProviderAction {
        public int getDescription();
        public boolean isVisible();
        public void launch(Activity source);
    }

    public interface AbstractEmptyContentAction extends ProviderAction {
        public int getDescription();
        public int getActionDescription();
    }
}
