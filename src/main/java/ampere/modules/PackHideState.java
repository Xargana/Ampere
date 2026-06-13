package ampere.modules;

import ampere.util.AmpereMessaging;
import ampere.util.AmpereConfig;
import ampere.util.AmpereInputClicker;
import ampere.util.AmpereLANSync;
import ampere.util.AmpereSharedState;
import ampere.util.macro.MacroExecutor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PackHideState {
    public static final String HIDE_ID = "hide";

    private static boolean silentOverride;

    private PackHideState() {
    }

    public static boolean isActive() {
        AmpereConfig config = AmpereConfig.getGlobal();
        AmpereConfig.ModuleState state = config.modules.get(HIDE_ID);
        return state != null && state.enabled;
    }

    public static boolean isSilenced() {
        return silentOverride || isActive();
    }

    public static boolean isHideModule(PackModule module) {
        return module != null && HIDE_ID.equals(module.id());
    }

    public static boolean isHideModuleName(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) return false;
        String normalized = idOrName.toLowerCase(Locale.ROOT).replace(' ', '-').replace("_", "-");
        return HIDE_ID.equals(normalized) || "panic-mode".equals(normalized) || "panic".equals(normalized);
    }

    public static boolean blocksEnable(PackModule module) {
        return isActive() && !isHideModule(module);
    }

    public static void enable(PackModule hideModule) {
        withSilence(() -> {
            AmpereMessaging.clearClientMessages();
            AmpereConfig config = AmpereConfig.getGlobal();
            Set<String> enabled = new LinkedHashSet<>();
            for (PackModule module : PackModuleRegistry.all()) {
                if (module == null || isHideModule(module)) continue;
                if (module.isEnabled()) enabled.add(module.id());
            }
            config.hideRestoreModules = new ArrayList<>(enabled);

            stopRuntimeWork();
            for (PackModule module : PackModuleRegistry.all()) {
                if (module == null || isHideModule(module)) continue;
                if (module.isEnabled()) module.setEnabledSilently(false);
            }

            ampere.util.AmpereMeteorBridge.disableAndSave(config);

            ampere.util.AmpereEssentialBridge.disable(config);
            config.save();
        });
    }

    public static void disableAndRestore(PackModule hideModule) {
        withSilence(() -> {
            AmpereConfig config = AmpereConfig.getGlobal();
            List<String> restore = config.hideRestoreModules == null ? List.of() : new ArrayList<>(config.hideRestoreModules);
            config.hideRestoreModules = new ArrayList<>();
            config.save();

            for (String id : restore) {
                PackModule module = PackModuleRegistry.get(id);
                if (module != null && !isHideModule(module)) module.setEnabledSilently(true);
            }

            ampere.util.AmpereMeteorBridge.restore(config);

            ampere.util.AmpereEssentialBridge.restore(config);
            config.save();

            if (config.lanSyncEnabled) {
                AmpereLANSync.getInstance().start();
            }
            PackModuleRegistry.clearKeyStates();
            AmpereInputClicker.clear();
        });
    }

    public static void enforceStartupHidden() {
        if (!isActive()) return;
        withSilence(() -> {
            stopRuntimeWork();
            for (PackModule module : PackModuleRegistry.all()) {
                if (module == null || isHideModule(module)) continue;
                if (module.isEnabled()) module.setEnabledSilently(false);
            }

            ampere.util.AmpereMeteorBridge.enforceHidden();

            ampere.util.AmpereEssentialBridge.disable(AmpereConfig.getGlobal());
            AmpereMessaging.clearClientMessages();
        });
    }

    public static void stopRuntimeWork() {
        PackModuleRegistry.clearKeyStates();
        AmpereInputClicker.clear();
        AmpereLANSync.getInstance().stopSilently();
        if (MacroExecutor.isRunning()) MacroExecutor.stop();
        AmpereSharedState shared = AmpereSharedState.get();
        shared.setSendGuiPackets(true);
        shared.setDelayGuiPackets(false);
        shared.setStaggeredPacketSend(false);
        shared.setCaptureMode(false);
        shared.clearQueuedPackets();
    }

    private static void withSilence(Runnable action) {
        boolean previous = silentOverride;
        silentOverride = true;
        try {
            action.run();
        } finally {
            silentOverride = previous;
        }
    }
}
