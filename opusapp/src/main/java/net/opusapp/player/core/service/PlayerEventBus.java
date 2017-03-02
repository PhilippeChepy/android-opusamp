package net.opusapp.player.core.service;


import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

public class PlayerEventBus extends Bus {

    private static class SingletonHolder {
        private final static PlayerEventBus instance = new PlayerEventBus();
    }

    private PlayerEventBus() {
        super(ThreadEnforcer.ANY);
    }

    public static PlayerEventBus getInstance() {
        return SingletonHolder.instance;
    }
}
