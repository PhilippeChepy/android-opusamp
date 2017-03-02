package net.opusapp.player.core.service.event;

public class PlayStateChangedEvent {

    private int mPlayState;

    public PlayStateChangedEvent(int newPlayState) {
        mPlayState = newPlayState;
    }

    public int getPlayState() {
        return mPlayState;
    }
}
