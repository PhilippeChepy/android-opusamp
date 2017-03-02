package net.opusapp.player.core.service.providers.event;

public class LibraryScanStatusChangedEvent {

    public static final int STATUS_TERMINATED = 0;

    public static final int STATUS_STARTED = 1;



    private boolean mTerminatedByUser;

    private int mStatus;

    public LibraryScanStatusChangedEvent(int status) {
        mStatus = status;
        mTerminatedByUser = false;
    }

    public LibraryScanStatusChangedEvent(boolean terminatedByUser) {
        mStatus = STATUS_TERMINATED;
        mTerminatedByUser = terminatedByUser;
    }

    public boolean isTerminatedByUser() {
        return mTerminatedByUser;
    }

    public int getStatus() {
        return mStatus;
    }
}
