package ampere.util;

import ampere.gui.vanillaui.UiContexts;
import ampere.gui.vanillaui.components.ToastStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class AmpereNotifications {
    private static final int SUCCESS = 0xFF35D873;
    private static final int WARNING = 0xFFFFC857;
    private static final int ERROR = 0xFFFF5B5B;
    private static final ToastStack TOASTS = new ToastStack(2100L, 140.0f, 220.0f, 4, 4, 18);

    private AmpereNotifications() {
    }

    public static void copied(String message) {
        show(message == null || message.isBlank() ? "Copied." : message, SUCCESS);
    }

    public static void warning(String message) {
        show(message, WARNING);
    }

    public static void error(String message) {
        show(message, ERROR);
    }

    public static void show(String message, int accentColor) {
        TOASTS.show(message, accentColor);
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (graphics == null || mc == null || mc.font == null || !TOASTS.hasVisibleToasts()) return;
        int width = Math.min(320, Math.max(120, AmpereUiScale.getVirtualScreenWidth() - 16));
        int x = Math.max(8, (AmpereUiScale.getVirtualScreenWidth() - width) / 2);
        TOASTS.render(UiContexts.overlay(graphics, mc.font, -1, -1), x, 8, width);
    }

    public static boolean hasVisible() {
        return TOASTS.hasVisibleToasts();
    }
}
