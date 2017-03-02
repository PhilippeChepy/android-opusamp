package net.opusapp.player.core.service.providers.event;

public class PlaybackTimestampChangedEvent {

    private long mTimeStamp = 0;

    public void setTimeStamp(long timeStamp) {
        mTimeStamp = timeStamp;
    }

    public long getTimeStamp() {
        return mTimeStamp;
    }
}
