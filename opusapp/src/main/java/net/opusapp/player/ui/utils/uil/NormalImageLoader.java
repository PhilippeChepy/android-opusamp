/*
 * NormalImageLoader.java
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
package net.opusapp.player.ui.utils.uil;

import android.graphics.Bitmap;

import com.nostra13.universalimageloader.cache.disc.naming.FileNameGenerator;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

import net.opusapp.player.R;
import net.opusapp.player.ui.utils.PlayerApplication;

public class NormalImageLoader extends ImageLoader {

    private volatile static NormalImageLoader instance;

    private FileNameGenerator fileNameGenerator;

    private NormalImageLoader() {
        fileNameGenerator = new PrefixedFileNameGenerator("nil");
    }

    public synchronized static NormalImageLoader getInstance() {
        if (instance == null) {
            instance = new NormalImageLoader();
            init();
        }
        return instance;
    }

    public synchronized static void init() {
        if (instance.isInited()) {
            instance.destroy();
        }

        DisplayImageOptions displayImageOptions = new DisplayImageOptions.Builder()
                .bitmapConfig(Bitmap.Config.RGB_565)
                .cacheInMemory(true)
                .cacheOnDisk(true)
                .imageScaleType(ImageScaleType.EXACTLY)
                .considerExifParams(false)
                .build();

        int normalImageCacheSize = PlayerApplication.getIntPreference(R.string.preference_key_cache_size, 30);

        ImageLoaderConfiguration loaderConfiguration = new ImageLoaderConfiguration.Builder(PlayerApplication.context)
                .threadPoolSize(1)
                .threadPriority(Thread.MIN_PRIORITY)
                .diskCacheExtraOptions(500, 500, null)
                .diskCacheSize(normalImageCacheSize * 1024 * 1024)
                .diskCacheFileNameGenerator(instance.fileNameGenerator)
                .memoryCacheExtraOptions(500, 500)
                .memoryCacheSizePercentage(20)
                .defaultDisplayImageOptions(displayImageOptions)
                .build();

        instance.init(loaderConfiguration);
    }

}
