package eu.chepy.audiokit.ui.utils;

import android.content.Context;
import android.net.Uri;

import com.nostra13.universalimageloader.core.download.BaseImageDownloader;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;

public class ProviderStreamImageDownloader extends BaseImageDownloader {

    public static final String TAG = ProviderStreamImageDownloader.class.getSimpleName();



    private static final String SCHEME_STREAM = "media";

    public static final String SCHEME_URI_PREFIX = SCHEME_STREAM + "://";

    public static final String SUBTYPE_ALBUM = "album";

    public static final String SUBTYPE_MEDIA = "media";

    public static final String SUBTYPE_STORAGE = "storage";


    public ProviderStreamImageDownloader(Context context) {
        super(context);
    }

    @Override
    protected InputStream getStreamFromOtherSource(String imageUri, Object extra) throws IOException {
        Uri uri = Uri.parse(imageUri);
        List<String> segments = uri.getPathSegments();
        InputStream inputStream = null;


        if (uri.getScheme() != null && uri.getScheme().equals(SCHEME_STREAM)) {
            if (segments != null && segments.size() >= 2) {
                final String subType = uri.getAuthority();
                final int managerId = Integer.parseInt(segments.get(0));
                final String objectId = segments.get(1);

                final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[managerId];

                if (!mediaManager.getMediaProvider().hasFeature(AbstractMediaProvider.Feature.SUPPORT_ART)) {
                    return super.getStreamFromOtherSource(imageUri, extra);
                }

                if (subType.equals(SUBTYPE_MEDIA)) {
                    inputStream = (InputStream) mediaManager.getMediaProvider().getProperty(
                            AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA,
                            objectId,
                            AbstractMediaProvider.ContentProperty.CONTENT_ART_STREAM);
                } else if (subType.equals(SUBTYPE_ALBUM)) {
                    inputStream = (InputStream) mediaManager.getMediaProvider().getProperty(
                            AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM,
                            objectId,
                            AbstractMediaProvider.ContentProperty.CONTENT_ART_STREAM);
                }
                else if (subType.equals(SUBTYPE_STORAGE)) {
                    inputStream = (InputStream) mediaManager.getMediaProvider().getProperty(
                            AbstractMediaProvider.ContentType.CONTENT_TYPE_STORAGE,
                            objectId,
                            AbstractMediaProvider.ContentProperty.CONTENT_ART_STREAM);
                }
            }

            return inputStream;
        }

        return super.getStreamFromOtherSource(imageUri, extra);
    }
}
