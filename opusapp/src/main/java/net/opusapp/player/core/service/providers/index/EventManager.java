package net.opusapp.player.core.service.providers.index;

import com.squareup.otto.Produce;

import net.opusapp.player.core.service.PlayerEventBus;
import net.opusapp.player.core.service.providers.event.LibraryScanStatusChangedEvent;
import net.opusapp.player.ui.utils.PlayerApplication;

public class EventManager {

    public EventManager() {
        PlayerEventBus.getInstance().register(this);
    }

    @Override
    protected void finalize() throws Throwable {
        // Will probably never be called
        PlayerEventBus.getInstance().unregister(this);

        super.finalize();
    }

    @Produce public LibraryScanStatusChangedEvent produceLibraryScanStatus() {
        return new LibraryScanStatusChangedEvent(PlayerApplication.thereIsScanningMediaManager() ?
                LibraryScanStatusChangedEvent.STATUS_STARTED :
                LibraryScanStatusChangedEvent.STATUS_TERMINATED
        );
    }
}
