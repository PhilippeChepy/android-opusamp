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
package net.opusapp.player.core.service.providers;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.local.LocalMediaManager;
import net.opusapp.player.ui.utils.PlayerApplication;

public class MediaManagerFactory {

    public static MediaManagerDescription[] getMediaManagerList() {
        MediaManagerDescription list[] = new MediaManagerDescription[1];

        list[0] = new MediaManagerDescription(MediaManager.LOCAL_MEDIA_MANAGER, PlayerApplication.context.getString(R.string.label_local_media_manager), true);
        return list;
    }

    public static String getDescriptionFromType(int mediaManagerType) {
        switch (mediaManagerType) {
            case MediaManager.LOCAL_MEDIA_MANAGER:
                return PlayerApplication.context.getString(R.string.label_local_media_manager);
            default:
                return "";
        }
    }

    public static MediaManager buildMediaManager(int mediaManagerType, int providerId, String name) {
        switch (mediaManagerType) {
            case MediaManager.LOCAL_MEDIA_MANAGER:
                return new LocalMediaManager(providerId, name);
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
