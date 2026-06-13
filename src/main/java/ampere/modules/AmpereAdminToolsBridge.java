package ampere.modules;

import net.minecraft.world.item.ItemStack;

public final class AmpereAdminToolsBridge {
    private AmpereAdminToolsBridge() {
    }

    public static boolean fillNbtEditorSilently(ItemStack stack) {
        PackModule module = PackModuleRegistry.get("admin-tools");
        if (module instanceof PackBuiltinModules.AdminToolsModule adminTools) {
            return adminTools.fillItemEditorFromStack(stack, false);
        }
        return false;
    }

    public static boolean openFilledAdminEditor(ItemStack stack) {
        if (!fillNbtEditorSilently(stack)) return false;
        try {
            ampere.util.AmpereAdminToolsOverlay overlay =
                ampere.util.AmpereAdminToolsOverlay.getSharedOverlay();
            ampere.util.AmpereOverlayManager manager = ampere.util.AmpereOverlayManager.get();
            manager.register(overlay);
            overlay.setVisible(true);
            overlay.showItemEditor();
            manager.bringToFront(overlay);
        } catch (Throwable ignored) {
        }
        return true;
    }
}
