/*
 * JniCodecContext.java
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.opus.player.core.service.providers.local;

import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;
import eu.chepy.opus.player.utils.LogUtils;

public class LocalMedia extends AbstractMediaManager.Media {
	
	public static final String TAG = LocalMedia.class.getSimpleName();



    public long nativeContext = 0;

    public String sourceUri;



    private AbstractMediaManager.Player player;

    private boolean loaded = false;



    public LocalMedia(AbstractMediaManager.Player player, String sourceUri) {
        this.player = player;
        this.sourceUri = sourceUri;
    }

    @Override
    public void load() {
        if (!loaded) {
            LogUtils.LOGD(TAG, "loaded : " + sourceUri);
            loaded = player.loadMedia(this);
        }
    }

    @Override
    public void unload() {
        if (loaded) {
            LogUtils.LOGD(TAG, "unloaded : " + sourceUri);
            player.unloadMedia(this);
            loaded = false;
        }
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    protected void finalize() throws Throwable {
        unload();
        super.finalize();
    }

    @Override
    public String getUri() {
        return sourceUri;
    }
}
