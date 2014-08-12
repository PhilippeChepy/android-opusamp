package eu.chepy.audiokit.ui.utils.uil;

import android.graphics.Bitmap;

import com.nostra13.universalimageloader.cache.disc.naming.FileNameGenerator;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class ThumbnailImageLoader extends ImageLoader {

    private volatile static ThumbnailImageLoader instance;

    private FileNameGenerator fileNameGenerator;

    private ThumbnailImageLoader() {
        fileNameGenerator = new PrefixedFileNameGenerator("til");
    }

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
                            .diskCacheExtraOptions(100, 100, null)
                            .diskCacheSize(50 * 1024 * 1024)
                            .diskCacheFileNameGenerator(instance.fileNameGenerator)
                            .memoryCacheExtraOptions(100, 100)
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
