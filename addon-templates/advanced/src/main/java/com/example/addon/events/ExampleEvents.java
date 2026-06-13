package com.example.addon.events;

import ampere.api.AmpereAddons;
import ampere.util.AmpereMessaging;

// Event hooks. Register listeners once in onInitialize().
// Available: onTick, onPacketSend (return true to cancel), onPacketReceive, onGameJoin, onGameLeft.
public final class ExampleEvents {
    private ExampleEvents() {}

    public static void register() {
        AmpereAddons.events().onGameJoin(() ->
            AmpereMessaging.sendPrefixed("\u00a7a[Example] joined a world!"));
    }
}
