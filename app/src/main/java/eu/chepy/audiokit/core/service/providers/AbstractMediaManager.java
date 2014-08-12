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
package eu.chepy.audiokit.core.service.providers;

public interface AbstractMediaManager {

    public static final int INVALID_MEDIA_MANAGER = -1;

    public static final int LOCAL_MEDIA_MANAGER = 1;

    public static final int DLNA_MEDIA_MANAGER = 2;

    public static final int DEEZER_MEDIA_MANAGER = 3;

    public static final int GOOGLE_MUSIC_MEDIA_MANAGER = 4;

    public static final int GROOVESHARK_MEDIA_MANAGER = 5;

    public static final int QOBUZ_MEDIA_MANAGER = 6;

    public static final int SPOTIFY_MEDIA_MANAGER = 7;



    public int getMediaManagerType();

    public int getMediaManagerId();

    public AbstractMediaProvider getMediaProvider();

    public AbstractMediaPlayer getMediaPlayer();
}
