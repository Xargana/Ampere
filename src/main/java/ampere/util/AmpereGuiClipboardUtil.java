package ampere.util;

import net.minecraft.client.Minecraft;

public final class AmpereGuiClipboardUtil {
    private static final Minecraft MC = Minecraft.getInstance();

    private AmpereGuiClipboardUtil() {
    }

    public static void copyGuiTitleJson() {
        if (MC.screen == null || MC.keyboardHandler == null) {
            AmpereNotifications.error("Copy failed: no screen.");
            return;
        }

        String title = MC.screen.getTitle() == null ? "" : MC.screen.getTitle().getString();
        MC.keyboardHandler.setClipboard(title);
        AmpereNotifications.copied("GUI title copied.");
    }
}
