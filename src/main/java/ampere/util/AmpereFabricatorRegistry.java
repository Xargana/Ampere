package ampere.util;

import org.jetbrains.annotations.Nullable;

public class AmpereFabricatorRegistry {
    private static volatile AmpereFabricatorOverlay activeOverlay = null;

    public static void setActiveOverlay(@Nullable AmpereFabricatorOverlay overlay) {
        activeOverlay = overlay;
    }

    @Nullable
    public static AmpereFabricatorOverlay getActiveOverlay() {
        return activeOverlay;
    }
}
