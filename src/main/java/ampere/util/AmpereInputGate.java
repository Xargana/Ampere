package ampere.util;

import ampere.gui.screen.AmpereModuleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

public final class AmpereInputGate {
    private static final Minecraft MC = Minecraft.getInstance();

    private AmpereInputGate() {
    }

    public static boolean canRunAmpereKeybinds() {
        AmpereConfig config = AmpereConfig.getGlobal();
        if (MC == null) return false;
        if (MC.screen == null) return true;
        if (config == null || !config.keybindInsideGui) return false;
        if (MC.screen instanceof ChatScreen || MC.screen instanceof InBedChatScreen) return false;
        if (MC.screen instanceof AbstractSignEditScreen) return false;
        GuiEventListener focused = MC.screen.getFocused();
        if (focused instanceof EditBox) return false;
        if (AmpereOverlayManager.get().isAnyTextFieldFocused()) return false;
        if (MC.screen instanceof AmpereModuleScreen moduleScreen) {
            return !moduleScreen.blocksGlobalKeybinds();
        }
        return true;
    }
}
