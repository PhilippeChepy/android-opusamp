package net.opusapp.player.core.service;

import android.os.Handler;
import android.os.Looper;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

public class ProviderEventBus extends Bus {

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private static class SingletonHolder {
        private final static ProviderEventBus instance = new ProviderEventBus(ThreadEnforcer.MAIN);
    }

    private ProviderEventBus(final ThreadEnforcer enforcer) {
        super(enforcer);
    }

    public static ProviderEventBus getInstance() {
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
                    ProviderEventBus.super.post(event);
                }
            });
        }
    }
}