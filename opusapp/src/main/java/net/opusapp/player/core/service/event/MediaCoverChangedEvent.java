package net.opusapp.player.core.service.event;

import android.graphics.Bitmap;

import java.lang.ref.WeakReference;

public class MediaCoverChangedEvent {


    private WeakReference<Bitmap> mBitmap;


    public MediaCoverChangedEvent(final Bitmap bitmap) {
        mBitmap = new WeakReference<Bitmap>(bitmap);
    }


    public boolean isAvailable() {
        return mBitmap != null;
    }

    public Bitmap getBitmap() {
        return mBitmap.get();
    }

}
