package ampere.gui.screen;

import ampere.util.PacketListCodec;
import ampere.gui.vanillaui.UiBounds;
import ampere.gui.vanillaui.UiRenderer;
import ampere.gui.vanillaui.module.VanillaModuleMenuController;
import ampere.modules.AmpereModule;
import ampere.modules.PackHideState;
import ampere.modules.PackModule;
import ampere.modules.PackModuleOption;
import ampere.util.AmpereConfig;
import ampere.util.AmpereAdminToolsOverlay;
import ampere.util.AmpereCustomFilterOverlay;
import ampere.util.AmpereFabricatorOverlay;
import ampere.util.AmpereKeybindOverlay;
import ampere.util.AmpereLANSyncOverlay;
import ampere.util.AmpereMacroEditorOverlay;
import ampere.util.AmpereMacroListOverlay;
import ampere.util.AmpereOverlayManager;
import ampere.util.AmperePacketLoggerOverlay;
import ampere.util.AmperePacketSelectorOverlay;
import ampere.util.AmpereQueueEditorOverlay;
import ampere.util.AmpereServerInfoOverlay;
import ampere.util.AmpereSharedState;
import ampere.util.AmpereUiScale;
import ampere.util.IAmpereOverlay;
import ampere.util.macro.ToggleModuleAction;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

import java.util.LinkedHashSet;
import java.util.Set;

public class AmpereModuleScreen extends Screen {
    private static final Set<String> TEMPORARILY_HIDDEN_UTILITY_OVERLAYS = new LinkedHashSet<>();

    private final Screen parent;
    private VanillaModuleMenuController menu;
    private AmperePacketSelectorOverlay packetSelectorOverlay;
    private AmpereMacroListOverlay utilityMacroListOverlay;
    private AmpereFabricatorOverlay utilityFabricatorOverlay;
    private AmpereLANSyncOverlay utilityLanSyncOverlay;
    private AmpereQueueEditorOverlay utilityQueueEditorOverlay;
    private AmpereCustomFilterOverlay utilityCustomFilterOverlay;
    private AmpereKeybindOverlay utilityKeybindOverlay;
    private AmpereAdminToolsOverlay utilityAdminToolsOverlay;
    private String returnSettingsModuleId;

    public AmpereModuleScreen(Screen parent) {
        super(Component.literal("Ampere Modules"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        syncUtilityOverlays();
        restoreTemporarilyHiddenUtilityOverlays();
        menu = new VanillaModuleMenuController(new ModuleMenuHost());
        menu.init();
        if (returnSettingsModuleId != null && !returnSettingsModuleId.isBlank()) {
            menu.openSettingsByModuleId(returnSettingsModuleId);
            returnSettingsModuleId = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean blocksGlobalKeybinds() {
        return (menu != null && menu.blocksGlobalKeybinds())
            || (packetSelectorOverlay != null && packetSelectorOverlay.isVisible());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (menu == null) {
            menu = new VanillaModuleMenuController(new ModuleMenuHost());
            menu.init();
        }
        int mx = AmpereUiScale.toVirtualInt(mouseX);
        int my = AmpereUiScale.toVirtualInt(mouseY);
        boolean selectorOpen = packetSelectorOverlay != null && packetSelectorOverlay.isVisible();
        syncUtilityOverlaysForTopLayer(selectorOpen || menu.hasTopLayer());
        boolean overlayBlocksMenuHover = !selectorOpen
            && !menu.hasTopLayer()
            && AmpereOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY);
        int menuMouseX = overlayBlocksMenuHover ? AmpereOverlayManager.HOVER_BLOCKED_MOUSE : mx;
        int menuMouseY = overlayBlocksMenuHover ? AmpereOverlayManager.HOVER_BLOCKED_MOUSE : my;

        AmpereUiScale.pushOverlayScale(graphics);
        try {
            if (selectorOpen) {
                UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), 0xAA050507);
                packetSelectorOverlay.render(graphics, mx, my, delta);
            } else {
                menu.render(graphics, menuMouseX, menuMouseY, delta, screenWidth(), screenHeight());
            }
        } finally {
            AmpereUiScale.popOverlayScale(graphics);
        }

        if (!PackHideState.isActive() && !selectorOpen && !menu.hasSelectedModule() && !menu.hasTopLayer()) {
            AmpereOverlayManager.get().renderAll(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = AmpereUiScale.toVirtualInt(event.x());
        int my = AmpereUiScale.toVirtualInt(event.y());
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible()) {
            return packetSelectorOverlay.mouseClicked(mx, my, event.button());
        }
        if (menu != null && !menu.hasTopLayer() && AmpereOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button())) return true;
        return menu == null || menu.mouseClicked(mx, my, event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int mx = AmpereUiScale.toVirtualInt(event.x());
        int my = AmpereUiScale.toVirtualInt(event.y());
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseReleased(mx, my, event.button())) return true;
        if (menu != null && !menu.hasTopLayer() && AmpereOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button())) return true;
        return menu == null || menu.mouseReleased(mx, my, event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mx = AmpereUiScale.toVirtualInt(event.x());
        int my = AmpereUiScale.toVirtualInt(event.y());
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        if (menu != null && !menu.hasTopLayer() && AmpereOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), dx, dy)) return true;
        return menu == null || menu.mouseDragged(mx, my, event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int mx = AmpereUiScale.toVirtualInt(x);
        int my = AmpereUiScale.toVirtualInt(y);
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible()) return packetSelectorOverlay.mouseScrolled(mx, my, scrollY);
        if (menu != null && !menu.hasTopLayer() && AmpereOverlayManager.get().handleMouseScrolled(x, y, scrollY)) return true;
        return menu == null || menu.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        if (menu != null && !menu.hasTopLayer() && AmpereOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        if (menu != null && menu.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        if (passMovementKey(input, true)) return false;
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        if (passMovementKey(input, false)) return false;
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        char chr = (char) input.codepoint();
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.charTyped(chr, 0)) return true;
        if (menu != null && !menu.hasTopLayer() && AmpereOverlayManager.get().handleCharTyped(chr, 0)) return true;
        if (menu != null && menu.charTyped(chr)) return true;
        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        saveUtilityOverlayStates();
        hideUtilityOverlaysForMenuClose();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private void openPacketSelector(PackModule module, PackModuleOption option) {
        if (module == null || option == null) return;
        if (packetSelectorOverlay == null) packetSelectorOverlay = new AmperePacketSelectorOverlay(font);
        boolean c2s = PacketListCodec.isC2SOption(option.id());
        Set<Class<? extends Packet<?>>> selected = PacketListCodec.resolvePackets(module.value(option.id()), c2s);
        if (c2s) {
            packetSelectorOverlay.openToggleC2S((packetClass, enabled) -> setPacketSelected(module, option, true, packetClass, enabled), selected);
        } else {
            packetSelectorOverlay.openToggleS2C((packetClass, enabled) -> setPacketSelected(module, option, false, packetClass, enabled), selected);
        }
    }

    private void setPacketSelected(PackModule module, PackModuleOption option, boolean c2s, Class<? extends Packet<?>> packetClass, boolean selected) {
        Set<Class<? extends Packet<?>>> packets = new LinkedHashSet<>(PacketListCodec.resolvePackets(module.value(option.id()), c2s));
        if (selected) packets.add(packetClass);
        else packets.remove(packetClass);
        module.setValue(option.id(), PacketListCodec.encodePackets(packets));
    }

    private void syncUtilityOverlays() {
        AmpereOverlayManager manager = AmpereOverlayManager.get();
        utilityMacroListOverlay = findRegisteredOverlay(AmpereMacroListOverlay.class, null);
        if (utilityMacroListOverlay == null) {
            utilityMacroListOverlay = new AmpereMacroListOverlay(font);
            utilityMacroListOverlay.restoreState();
        }
        manager.register(utilityMacroListOverlay);

        if (parent instanceof AbstractContainerScreen<?> handledScreen) {
            utilityFabricatorOverlay = AmpereFabricatorOverlay.getSharedOverlay(handledScreen);
            utilityFabricatorOverlay.restoreState();
            manager.register(utilityFabricatorOverlay);
        }

        utilityLanSyncOverlay = AmpereLANSyncOverlay.getSharedOverlay(font);
        utilityLanSyncOverlay.restoreState();
        manager.register(utilityLanSyncOverlay);

        utilityQueueEditorOverlay = findRegisteredOverlay(AmpereQueueEditorOverlay.class, null);
        if (utilityQueueEditorOverlay == null) {
            utilityQueueEditorOverlay = new AmpereQueueEditorOverlay(font);
            utilityQueueEditorOverlay.restoreState();
        }
        manager.register(utilityQueueEditorOverlay);

        utilityCustomFilterOverlay = findRegisteredOverlay(AmpereCustomFilterOverlay.class, null);
        if (utilityCustomFilterOverlay == null) {
            utilityCustomFilterOverlay = new AmpereCustomFilterOverlay(font);
            utilityCustomFilterOverlay.restoreLayout();
        }
        manager.register(utilityCustomFilterOverlay);
        if (utilityCustomFilterOverlay.getPresetManagerOverlay() != null) {
            utilityCustomFilterOverlay.getPresetManagerOverlay().restoreLayout();
            manager.register(utilityCustomFilterOverlay.getPresetManagerOverlay());
        }

        utilityKeybindOverlay = findRegisteredOverlay(AmpereKeybindOverlay.class, null);
        if (utilityKeybindOverlay == null) {
            utilityKeybindOverlay = new AmpereKeybindOverlay();
            utilityKeybindOverlay.restoreLayout();
        }
        manager.register(utilityKeybindOverlay);

        utilityAdminToolsOverlay = AmpereAdminToolsOverlay.getSharedOverlay();
        utilityAdminToolsOverlay.restoreLayout();
        manager.register(utilityAdminToolsOverlay);

        AmpereMacroEditorOverlay macroEditor = AmpereMacroEditorOverlay.getSharedOverlay();
        if (macroEditor != null) {
            macroEditor.restoreState();
            manager.register(macroEditor);
        }
        manager.register(ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());

        AmpereModule global = AmpereModule.get();
        if (global != null) {
            AmperePacketLoggerOverlay logger = global.getPacketLoggerOverlay();
            if (logger != null) {
                logger.restoreLayout();
                manager.register(logger);
            }
            AmpereServerInfoOverlay serverInfo = global.getServerDataOverlay();
            if (serverInfo != null) {
                serverInfo.restoreState();
                manager.register(serverInfo);
            }
        }
    }

    private void syncUtilityOverlaysForTopLayer(boolean hidden) {
        if (hidden) hideUtilityOverlaysForMenuClose();
        else restoreTemporarilyHiddenUtilityOverlays();
    }

    private void hideUtilityOverlaysForMenuClose() {
        TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.clear();
        setOverlayHiddenForMenuClose(utilityMacroListOverlay);
        setOverlayHiddenForMenuClose(utilityFabricatorOverlay);
        setOverlayHiddenForMenuClose(utilityLanSyncOverlay);
        setOverlayHiddenForMenuClose(utilityQueueEditorOverlay);
        setOverlayHiddenForMenuClose(utilityCustomFilterOverlay);
        setOverlayHiddenForMenuClose(utilityKeybindOverlay);
        setOverlayHiddenForMenuClose(utilityAdminToolsOverlay);
        AmpereModule global = AmpereModule.get();
        if (global != null) {
            setOverlayHiddenForMenuClose(global.getPacketLoggerOverlayIfExists());
            setOverlayHiddenForMenuClose(global.getServerDataOverlayIfExists());
        }
        setOverlayHiddenForMenuClose(AmpereMacroEditorOverlay.getSharedOverlay());
        setOverlayHiddenForMenuClose(ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());
        if (utilityCustomFilterOverlay != null) setOverlayHiddenForMenuClose(utilityCustomFilterOverlay.getPresetManagerOverlay());
    }

    private void setOverlayHiddenForMenuClose(IAmpereOverlay overlay) {
        if (overlay == null || !overlay.isVisible()) return;
        TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.add(overlay.getOverlayId());
        AmpereOverlayManager.get().setTemporarilyHidden(overlay, true);
    }

    private void restoreTemporarilyHiddenUtilityOverlays() {
        if (TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.isEmpty()) return;
        Set<String> restoreIds = new LinkedHashSet<>(TEMPORARILY_HIDDEN_UTILITY_OVERLAYS);
        TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.clear();
        for (IAmpereOverlay overlay : AmpereOverlayManager.get().getOverlays()) {
            if (overlay != null && restoreIds.contains(overlay.getOverlayId())) {
                AmpereOverlayManager.get().setTemporarilyHidden(overlay, false);
            }
        }
    }

    private <T extends IAmpereOverlay> T findRegisteredOverlay(Class<T> type, String overlayId) {
        for (IAmpereOverlay overlay : AmpereOverlayManager.get().getOverlays()) {
            if (overlay == null || !type.isInstance(overlay)) continue;
            if (overlayId != null && !overlayId.equals(overlay.getOverlayId())) continue;
            return type.cast(overlay);
        }
        return null;
    }

    private void runUtility(String id) {
        AmpereModule global = AmpereModule.get();
        if (global == null) return;
        switch (id) {
            case "macros" -> toggleMacroPanel();
            case "admin" -> toggleOverlay(utilityAdminToolsOverlay);
            case "lan" -> toggleOverlay(utilityLanSyncOverlay);
            case "queue" -> toggleOverlay(utilityQueueEditorOverlay);
            case "logger" -> {
                AmperePacketLoggerOverlay logger = global.getPacketLoggerOverlay();
                if (logger != null) {
                    logger.restoreLayout();
                    AmpereOverlayManager.get().register(logger);
                    toggleOverlay(logger);
                }
            }
            case "packets" -> toggleOverlay(utilityCustomFilterOverlay);
            case "server" -> {
                AmpereServerInfoOverlay serverInfo = global.getServerDataOverlay();
                if (serverInfo != null) {
                    AmpereOverlayManager.get().register(serverInfo);
                    toggleOverlay(serverInfo);
                }
            }
            case "keys" -> toggleOverlay(utilityKeybindOverlay);
            case "send" -> {
                boolean newValue = !AmpereSharedState.get().shouldSendGuiPackets();
                global.applySendGuiPacketsUiBehavior(newValue);
                ampere.util.AmpereNotifications.show("Send Packets " + (newValue ? "on" : "off"), newValue ? 0xFF35D873 : 0xFFFF3B3B);
            }
            case "delay" -> {
                boolean newValue = !AmpereSharedState.get().shouldDelayGuiPackets();
                int sent = global.applyDelayGuiPacketsUiBehavior(newValue);
                global.notifyDelayPacketsUiResult(newValue, sent);
            }
            case "flush" -> {
                int count = global.flushQueuedPacketsUiBehavior();
                global.notifyFlushQueuedPacketsUiResult(count);
            }
            case "clear" -> {
                int count = global.clearQueuedPacketsUiBehavior();
                global.notifyClearQueuedPacketsUiResult(count);
            }
            default -> {
            }
        }
    }

    private void toggleMacroPanel() {
        AmpereMacroEditorOverlay editor = AmpereMacroEditorOverlay.getSharedOverlay();
        if (editor != null) AmpereOverlayManager.get().register(editor);
        if (editor != null && editor.isVisible()) {
            if (utilityMacroListOverlay != null) utilityMacroListOverlay.setVisible(false);
            AmpereOverlayManager.get().bringToFront(editor);
            return;
        }
        toggleOverlay(utilityMacroListOverlay);
    }

    private void toggleOverlay(IAmpereOverlay overlay) {
        if (overlay == null) return;
        AmpereOverlayManager.get().register(overlay);
        overlay.setVisible(!overlay.isVisible());
        if (overlay.isVisible()) AmpereOverlayManager.get().bringToFront(overlay);
    }

    private AmpereFabricatorOverlay utilityFabricatorOverlay() {
        if (!(parent instanceof AbstractContainerScreen<?> handledScreen)) return null;
        utilityFabricatorOverlay = AmpereFabricatorOverlay.getSharedOverlay(handledScreen);
        utilityFabricatorOverlay.restoreState();
        AmpereOverlayManager.get().register(utilityFabricatorOverlay);
        return utilityFabricatorOverlay;
    }

    private void addQuickToggleMacroStep(PackModule module) {
        if (module == null) return;
        AmpereMacroEditorOverlay editor = AmpereMacroEditorOverlay.getSharedOverlay();
        if (editor == null) return;
        AmpereOverlayManager manager = AmpereOverlayManager.get();
        manager.register(editor);
        manager.setTemporarilyHidden(editor, false);
        if (!editor.isVisible() || AmpereSharedState.get().getEditingMacro() == null) {
            editor.open(null, true);
        } else {
            editor.setVisible(true);
            manager.bringToFront(editor);
        }
        if (utilityMacroListOverlay != null) utilityMacroListOverlay.setVisible(false);
        editor.addAction(new ToggleModuleAction(module.name()));
    }

    private void saveUtilityOverlayStates() {
        saveOverlayState(utilityMacroListOverlay);
        saveOverlayState(utilityFabricatorOverlay);
        saveOverlayState(utilityLanSyncOverlay);
        saveOverlayState(utilityQueueEditorOverlay);
        saveOverlayState(utilityCustomFilterOverlay);
        saveOverlayState(utilityKeybindOverlay);
        saveOverlayState(utilityAdminToolsOverlay);
        AmpereModule global = AmpereModule.get();
        if (global != null) {
            saveOverlayState(global.getPacketLoggerOverlayIfExists());
            saveOverlayState(global.getServerDataOverlayIfExists());
        }
        saveOverlayState(AmpereMacroEditorOverlay.getSharedOverlay());
        saveOverlayState(ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());
        if (utilityCustomFilterOverlay != null) saveOverlayState(utilityCustomFilterOverlay.getPresetManagerOverlay());
    }

    private void saveOverlayState(IAmpereOverlay overlay) {
        if (overlay == null) return;
        if (overlay instanceof AmpereMacroListOverlay macroList) macroList.saveState();
        else if (overlay instanceof AmpereFabricatorOverlay fabricator) fabricator.saveState();
        else if (overlay instanceof AmpereLANSyncOverlay lanSync) lanSync.saveState();
        else if (overlay instanceof AmpereQueueEditorOverlay queue) queue.saveState();
        else if (overlay instanceof AmpereCustomFilterOverlay filter) filter.saveLayout();
        else if (overlay instanceof AmpereKeybindOverlay keybind) keybind.saveLayout();
        else if (overlay instanceof AmperePacketLoggerOverlay logger) logger.saveLayout();
        else if (overlay instanceof AmpereServerInfoOverlay serverInfo) serverInfo.saveState();
        else if (overlay instanceof AmpereMacroEditorOverlay macroEditor) macroEditor.saveState();
        else overlay.saveLayout();
    }

    private boolean passMovementKey(KeyEvent input, boolean down) {
        if (blocksGlobalKeybinds()) return false;
        if (minecraft == null || minecraft.options == null) return false;
        KeyMapping[] movement = {
            minecraft.options.keyUp,
            minecraft.options.keyDown,
            minecraft.options.keyLeft,
            minecraft.options.keyRight,
            minecraft.options.keyJump,
            minecraft.options.keyShift,
            minecraft.options.keySprint
        };
        for (KeyMapping key : movement) {
            if (key != null && key.matches(input)) {
                key.setDown(down);
                return true;
            }
        }
        return false;
    }

    private int screenWidth() {
        int virtualWidth = AmpereUiScale.getVirtualScreenWidth();
        return virtualWidth <= 0 ? width : virtualWidth;
    }

    private int screenHeight() {
        int virtualHeight = AmpereUiScale.getVirtualScreenHeight();
        return virtualHeight <= 0 ? height : virtualHeight;
    }

    private final class ModuleMenuHost implements VanillaModuleMenuController.Host {
        @Override
        public Screen screen() {
            return AmpereModuleScreen.this;
        }

        @Override
        public Font font() {
            return AmpereModuleScreen.this.font;
        }

        @Override
        public void closeMenu() {
            AmpereModuleScreen.this.onClose();
        }

        @Override
        public void saveConfig() {
            AmpereConfig.getGlobal().save();
        }

        @Override
        public void openPacketSelector(PackModule module, PackModuleOption option) {
            AmpereModuleScreen.this.openPacketSelector(module, option);
        }

        @Override
        public void openStringListEditor(PackModule module, PackModuleOption option) {
            if (minecraft != null) {
                returnSettingsModuleId = module == null ? null : module.id();
                minecraft.setScreen(new AmpereStringListSettingScreen(AmpereModuleScreen.this, module, option));
            }
        }

        @Override
        public void openRegistryListEditor(PackModule module, PackModuleOption option) {
            if (minecraft != null) {
                returnSettingsModuleId = module == null ? null : module.id();
                minecraft.setScreen(new AmpereRegistryListSettingScreen(AmpereModuleScreen.this, module, option));
            }
        }

        @Override
        public void openMacroCreator(PackModule module, PackModuleOption option) {
            openMacroCreator(module, option, null);
        }

        @Override
        public void openMacroCreator(PackModule module, PackModuleOption option, ampere.util.AmpereMacro macro) {
            AmpereMacroEditorOverlay editor = AmpereMacroEditorOverlay.getSharedOverlay();
            if (editor == null) return;
            AmpereOverlayManager manager = AmpereOverlayManager.get();
            manager.register(editor);
            manager.setTemporarilyHidden(editor, false);
            editor.open(macro, true);
            editor.setVisible(true);
            manager.bringToFront(editor);
            if (utilityMacroListOverlay != null) utilityMacroListOverlay.setVisible(false);
        }

        @Override
        public void runUtility(String id) {
            AmpereModuleScreen.this.runUtility(id);
        }

        @Override
        public void addToggleMacro(PackModule module) {
            AmpereModuleScreen.this.addQuickToggleMacroStep(module);
        }
    }
}
