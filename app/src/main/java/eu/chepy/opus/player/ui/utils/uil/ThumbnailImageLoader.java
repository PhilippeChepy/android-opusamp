/*
 * ThumbnailImageLoader.java
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
package eu.chepy.opus.player.ui.utils.uil;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import com.nostra13.universalimageloader.cache.disc.naming.FileNameGenerator;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.ui.utils.PlayerApplication;

public class ThumbnailImageLoader extends ImageLoader {

    private volatile static ThumbnailImageLoader instance;

    private FileNameGenerator fileNameGenerator;

    private ThumbnailImageLoader() {
        fileNameGenerator = new PrefixedFileNameGenerator("til");
    }

    public synchronized static ThumbnailImageLoader getInstance() {
        if (instance == null) {
            instance = new ThumbnailImageLoader();
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

        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(PlayerApplication.context);

        ImageLoaderConfiguration loaderConfiguration = new ImageLoaderConfiguration.Builder(PlayerApplication.context)
                .threadPoolSize(4)
                .threadPriority(Thread.MIN_PRIORITY)
                .diskCacheExtraOptions(100, 100, null)
                .diskCacheSize(Integer.parseInt(sharedPrefs.getString(PlayerApplication.context.getString(R.string.preference_key_thumbnail_cache_size), "20")) * 1024 * 1024)
                .diskCacheFileNameGenerator(instance.fileNameGenerator)
                .memoryCacheExtraOptions(100, 100)
                .memoryCacheSizePercentage(20)
                .imageDownloader(new ProviderImageDownloader(PlayerApplication.context))
                .defaultDisplayImageOptions(displayImageOptions)
                .build();

        instance.init(loaderConfiguration);
    }

}
