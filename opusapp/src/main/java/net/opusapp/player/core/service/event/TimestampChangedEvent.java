package net.opusapp.player.core.service.event;

public class TimestampChangedEvent {

    private long mTimestamp = 0;

    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }

    public long getTimestamp() {
        return mTimestamp;
    }
}
