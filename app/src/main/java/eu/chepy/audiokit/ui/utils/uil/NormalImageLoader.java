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
package eu.chepy.audiokit.ui.utils.uil;

import android.graphics.Bitmap;

import com.nostra13.universalimageloader.cache.disc.naming.FileNameGenerator;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class NormalImageLoader extends ImageLoader {

    private volatile static NormalImageLoader instance;

    private FileNameGenerator fileNameGenerator;

    private NormalImageLoader() {
        fileNameGenerator = new PrefixedFileNameGenerator("nil");
    }

    public static NormalImageLoader getInstance() {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) {
                    instance = new NormalImageLoader();

                    DisplayImageOptions displayImageOptions = new DisplayImageOptions.Builder()
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .cacheInMemory(true)
                            .cacheOnDisk(true)
                            .imageScaleType(ImageScaleType.EXACTLY)
                            .considerExifParams(false)
                            .build();

                    ImageLoaderConfiguration loaderConfiguration = new ImageLoaderConfiguration.Builder(PlayerApplication.context)
                            .threadPoolSize(2)
                            .diskCacheExtraOptions(500, 500, null)
                            .diskCacheSize(50 * 1024 * 1024)
                            .diskCacheFileNameGenerator(instance.fileNameGenerator)
                            .memoryCacheExtraOptions(500, 500)
                            .memoryCacheSizePercentage(20)
                            .imageDownloader(new ProviderImageDownloader(PlayerApplication.context))
                            .defaultDisplayImageOptions(displayImageOptions)
                            .build();

                    instance.init(loaderConfiguration);
                }
            }
        }
        return instance;
    }

}
