package net.opusapp.player.core.service.event;

public class RepeatModeChangedEvent {

    private int mMode;

    public RepeatModeChangedEvent(int mode) {
        mMode = mode;
    }

    public int getNewMode() {
        return mMode;
    }
}
