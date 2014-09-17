/*
 * MediaManagerFactory.java
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
package eu.chepy.opus.player.core.service.providers;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.core.service.providers.deezer.DeezerMediaManager;
import eu.chepy.opus.player.core.service.providers.local.LocalMediaManager;
import eu.chepy.opus.player.ui.utils.PlayerApplication;

public class MediaManagerFactory {

    public static MediaManagerDescription[] getMediaManagerList() {
        MediaManagerDescription list[] = new MediaManagerDescription[7];

        list[0] = new MediaManagerDescription(AbstractMediaManager.LOCAL_MEDIA_MANAGER, PlayerApplication.context.getString(R.string.label_local_media_manager), true);
        /*
        list[1] = new MediaManagerDescription(AbstractMediaManager.DLNA_MEDIA_MANAGER, PlayerApplication.context.getString(R.string.label_dlna_media_manager), false);
        */
        list[2] = new MediaManagerDescription(AbstractMediaManager.DEEZER_MEDIA_MANAGER, PlayerApplication.context.getString(R.string.label_deezer_media_manager), true);
        /*
        list[3] = new MediaManagerDescription(AbstractMediaManager.GOOGLE_MUSIC_MEDIA_MANAGER, PlayerApplication.context.getString(R.string.label_google_music_media_manager), false);
        list[4] = new MediaManagerDescription(AbstractMediaManager.GROOVESHARK_MEDIA_MANAGER, PlayerApplication.context.getString(R.string.label_grooveshark_media_manager), false);
        list[5] = new MediaManagerDescription(AbstractMediaManager.QOBUZ_MEDIA_MANAGER, PlayerApplication.context.getString(R.string.label_qobuz_media_manager), false);
        list[6] = new MediaManagerDescription(AbstractMediaManager.SPOTIFY_MEDIA_MANAGER, PlayerApplication.context.getString(R.string.label_spotify_media_manager), false);
        */
        return list;
    }

    public static String getDescriptionFromType(int mediaManagerType) {
        switch (mediaManagerType) {
            case AbstractMediaManager.LOCAL_MEDIA_MANAGER:
                return PlayerApplication.context.getString(R.string.label_local_media_manager);
        /*
            case AbstractMediaManager.DLNA_MEDIA_MANAGER:
                return PlayerApplication.context.getString(R.string.label_dlna_media_manager);
        */
            case AbstractMediaManager.DEEZER_MEDIA_MANAGER:
                return PlayerApplication.context.getString(R.string.label_deezer_media_manager);
        /*
            case AbstractMediaManager.GROOVESHARK_MEDIA_MANAGER:
                return PlayerApplication.context.getString(R.string.label_grooveshark_media_manager);
            case AbstractMediaManager.GOOGLE_MUSIC_MEDIA_MANAGER:
                return PlayerApplication.context.getString(R.string.label_google_music_media_manager);
            case AbstractMediaManager.QOBUZ_MEDIA_MANAGER:
                return PlayerApplication.context.getString(R.string.label_qobuz_media_manager);
            case AbstractMediaManager.SPOTIFY_MEDIA_MANAGER:
                return PlayerApplication.context.getString(R.string.label_spotify_media_manager);
         */
            default:
                return "";
        }
    }

    public static AbstractMediaManager buildMediaManager(int mediaManagerType, int providerId) {
        switch (mediaManagerType) {
            case AbstractMediaManager.LOCAL_MEDIA_MANAGER:
                return new LocalMediaManager(providerId);
            /*
            case AbstractMediaManager.DLNA_MEDIA_MANAGER:
                return new DlnaMediaManager(providerId);
            */
            case AbstractMediaManager.DEEZER_MEDIA_MANAGER:
                return new DeezerMediaManager(providerId);
            /*
            case AbstractMediaManager.GROOVESHARK_MEDIA_MANAGER:
                return new GroovesharkMediaManager(providerId);
            case AbstractMediaManager.GOOGLE_MUSIC_MEDIA_MANAGER:
                return new GoogleMusicMediaManager(providerId);
            case AbstractMediaManager.QOBUZ_MEDIA_MANAGER:
                return new QobuzMediaManager(providerId);
            case AbstractMediaManager.SPOTIFY_MEDIA_MANAGER:
                return new SpotifyMediaManager(providerId);
                */
            default:
                throw new IllegalArgumentException();
        }
    }


    public static class MediaManagerDescription {

        public int typeId;

        public String description;

        public boolean isEnabled;

        public MediaManagerDescription(int id, String desc, boolean enabled) {
            typeId = id;
            description = desc;
            isEnabled = enabled;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
