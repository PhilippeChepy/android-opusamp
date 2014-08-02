package eu.chepy.audiokit.ui.utils;

import android.graphics.Bitmap;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

public class ThumbnailImageLoader extends ImageLoader {

    private volatile static ThumbnailImageLoader instance;

    public static ThumbnailImageLoader getInstance() {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) {
                    instance = new ThumbnailImageLoader();

                    DisplayImageOptions displayImageOptions = new DisplayImageOptions.Builder()
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .cacheInMemory(true)
                            .cacheOnDisk(true)
                            .imageScaleType(ImageScaleType.EXACTLY)
                            .considerExifParams(false)
                            .build();

                    ImageLoaderConfiguration loaderConfiguration = new ImageLoaderConfiguration.Builder(PlayerApplication.context)
                            .threadPoolSize(4)
                            .diskCacheSize(50 * 1024 * 1024)
                            .diskCacheExtraOptions(100, 100, null) //Bitmap.CompressFormat.PNG, 100, null)
//                            .discCache(new TotalSizeLimitedDiscCache(PlayerApplication.getCacheDir("thumbs"), 50 * 1024 * 1024))
                            .memoryCacheExtraOptions(100, 100)
                            .memoryCacheSizePercentage(20)
                            .imageDownloader(new ProviderStreamImageDownloader(PlayerApplication.context))
                            .defaultDisplayImageOptions(displayImageOptions)
                            .build();

                    instance.init(loaderConfiguration);
                }
            }
        }
        return instance;
    }

}
