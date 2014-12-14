package net.opusapp.player.ui.utils.uil;

import android.graphics.Bitmap;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

import net.opusapp.player.ui.utils.PlayerApplication;

public class ThumbnailUncachedImageLoader extends ImageLoader {

    private volatile static ThumbnailUncachedImageLoader instance;

    public synchronized static ThumbnailUncachedImageLoader getInstance() {
        if (instance == null) {
            instance = new ThumbnailUncachedImageLoader();
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
                .cacheInMemory(false)
                .cacheOnDisk(false)
                .imageScaleType(ImageScaleType.EXACTLY)
                .considerExifParams(false)
                .build();

        ImageLoaderConfiguration loaderConfiguration = new ImageLoaderConfiguration.Builder(PlayerApplication.context)
                .threadPoolSize(1)
                .threadPriority(Thread.MIN_PRIORITY)
                .defaultDisplayImageOptions(displayImageOptions)
                .build();

        instance.init(loaderConfiguration);
    }
}
