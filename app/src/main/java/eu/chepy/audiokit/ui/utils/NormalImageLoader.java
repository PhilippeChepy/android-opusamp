package eu.chepy.audiokit.ui.utils;

import android.graphics.Bitmap;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

public class NormalImageLoader extends ImageLoader {

    private volatile static NormalImageLoader instance;

    public static NormalImageLoader getInstance() {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) {
                    instance = new NormalImageLoader();

                    DisplayImageOptions displayImageOptions = new DisplayImageOptions.Builder()
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .cacheInMemory(true)
                            .cacheOnDisc(true)
                            .imageScaleType(ImageScaleType.EXACTLY)
                            .considerExifParams(false)
                            .build();

                    ImageLoaderConfiguration loaderConfiguration = new ImageLoaderConfiguration.Builder(PlayerApplication.context)
                            .threadPoolSize(4)
                            .diskCacheExtraOptions(500, 500, null)
                            .diskCacheSize(100 * 1024 * 1024)
//                            .discCacheSize(100 * 1024 * 1024)
//                            .discCacheExtraOptions(500, 500, Bitmap.CompressFormat.PNG, 100, null)
//                            .discCache(new TotalSizeLimitedDiscCache(PlayerApplication.getCacheDir("arts"), 100 * 1024 * 1024))
                            .memoryCacheExtraOptions(500, 500)
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
