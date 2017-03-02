package net.opusapp.player.core.service.event;

public class MediaChangedEvent {
    private String mMediaTitle;

    private String mMediaArtist;

    private String mMediaAlbum;

    private long mDuration;

    private int mQueuePosition;

    public MediaChangedEvent(String mediaTitle, String mediaArtist, String mediaAlbum, long duration, int queuePosition) {
        mMediaTitle = mediaTitle;
        mMediaArtist = mediaArtist;
        mMediaAlbum = mediaAlbum;
        mDuration = duration;
        mQueuePosition = queuePosition;
    }

    public String getTitle() {
        return mMediaTitle;
    }

    public String getArtist() {
        return mMediaArtist;
    }

    public String getAlbum() {
        return mMediaAlbum;
    }

    public long getDuration() {
        return mDuration;
    }

    public int getQueuePosition() {
        return mQueuePosition;
    }
}
