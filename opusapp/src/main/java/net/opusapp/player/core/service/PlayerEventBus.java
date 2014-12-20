package net.opusapp.player.core.service;

import android.os.Handler;
import android.os.Looper;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

public class PlayerEventBus extends Bus {

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private static class SingletonHolder {
        private final static PlayerEventBus instance = new PlayerEventBus(ThreadEnforcer.MAIN);
    }

    private PlayerEventBus(final ThreadEnforcer enforcer) {
        super(enforcer);
    }

    public static PlayerEventBus getInstance() {
        return SingletonHolder.instance;
    }

    @Override
    public void post(final Object event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            super.post(event);
        }
        else {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    PlayerEventBus.super.post(event);
                }
            });
        }
    }
}