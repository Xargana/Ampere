package ampere.util;

public final class AmpereEssentialBridge {

    private AmpereEssentialBridge() {
    }

    public static void disable(AmpereConfig config) {

    }

    public static void restoreIfOrphaned(AmpereConfig config) {

        if (config != null && config.essentialHiddenByPanic) {
            config.essentialHiddenByPanic = false;
            config.save();
        }
    }

    public static void restore(AmpereConfig config) {

        if (config != null && config.essentialHiddenByPanic) {
            config.essentialHiddenByPanic = false;
        }
    }
}
