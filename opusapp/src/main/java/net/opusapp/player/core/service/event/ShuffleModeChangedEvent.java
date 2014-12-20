package net.opusapp.player.core.service.event;

public class ShuffleModeChangedEvent {

    private int mMode;

    public ShuffleModeChangedEvent(int mode) {
        mMode = mode;
    }

    public int getNewMode() {
        return mMode;
    }
}
