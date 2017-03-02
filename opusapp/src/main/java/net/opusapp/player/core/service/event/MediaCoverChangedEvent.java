package net.opusapp.player.core.service.event;

import android.graphics.Bitmap;

public class MediaCoverChangedEvent {


    private Bitmap mBitmap;


    public MediaCoverChangedEvent(final Bitmap bitmap) {
        mBitmap = bitmap;
    }


    public boolean isAvailable() {
        return mBitmap != null && !mBitmap.isRecycled();
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

}
