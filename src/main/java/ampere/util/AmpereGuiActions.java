package ampere.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

public final class AmpereGuiActions {
    private AmpereGuiActions() {
    }

    public static boolean saveCurrentGui(Minecraft mc) {
        return saveCurrentGui(mc, true);
    }

    public static boolean saveCurrentGui(Minecraft mc, boolean notify) {
        if (mc == null || mc.screen == null || mc.player == null) {
            if (notify) {
                AmpereNotifications.error("Failed to store GUI.");
            }
            return false;
        }

        AmpereSharedState.get().storeScreen(mc.screen, mc.player.containerMenu);
        if (notify) {
            AmpereNotifications.show(savedGuiMessage(), 0xFF35D873);
        }
        return true;
    }

    private static String savedGuiMessage() {
        int keyCode = AmpereConfig.getGlobal().keybindLoadGui;
        if (keyCode == -1) return "GUI stored.";
        return "GUI stored. Press " + AmpereKeybindOverlay.getKeyName(keyCode) + " to restore.";
    }

    public static boolean closeCurrentScreen(Minecraft mc, boolean sendPacket) {
        return closeCurrentScreen(mc, sendPacket, true);
    }

    public static boolean closeCurrentScreen(Minecraft mc, boolean sendPacket, boolean notify) {
        if (mc == null || mc.screen == null) return false;

        Screen screen = mc.screen;
        if (screen instanceof AmpereSpecialGuiActions special) {
            if (sendPacket) special.Ampere$closeWithPacket(notify);
            else special.Ampere$closeWithoutPacket(notify);
            return true;
        }

        if (sendPacket) {
            if (mc.player != null
                && mc.player.containerMenu != null
                && mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            } else {
                mc.setScreen(null);
            }
        } else {
            if (mc.player != null
                && mc.player.containerMenu != null
                && mc.player.containerMenu != mc.player.inventoryMenu) {
                AmpereSharedState.get().setSuppressNextContainerClosePacket(true);
                mc.player.closeContainer();
                if (notify) {
                    AmpereMessaging.sendPrefixed("GUI closed without packet.");
                }
            } else {
                mc.setScreen(null);
                if (notify) {
                    AmpereMessaging.sendPrefixed("Screen closed locally.");
                }
            }
        }
        return true;
    }

    public static boolean desyncCurrentScreen(Minecraft mc) {
        return desyncCurrentScreen(mc, true);
    }

    public static boolean desyncCurrentScreen(Minecraft mc, boolean notify) {
        if (mc == null || mc.screen == null) return false;

        Screen screen = mc.screen;
        if (screen instanceof AmpereSpecialGuiActions special) {
            special.Ampere$desync(notify);
            return true;
        }

        if (mc.getConnection() == null || mc.player == null || mc.player.containerMenu == null) {
            return false;
        }

        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
        if (notify) {
            AmpereMessaging.sendPrefixed("GUI desynced: close packet sent while client screen stays open.");
        }
        return true;
    }
}
