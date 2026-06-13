package ampere.util;

import ampere.modules.PackHideState;

public final class AmpereMenuPrefs {
    private AmpereMenuPrefs() {
    }

    public static boolean customMainMenuEnabled() {
        AmpereConfig config = AmpereConfig.getGlobal();
        return config == null || config.customMainMenu;
    }

    public static boolean vanillaMenuVisuals() {
        return PackHideState.isActive() || !customMainMenuEnabled();
    }
}
