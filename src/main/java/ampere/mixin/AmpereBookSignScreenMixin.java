package ampere.mixin;

import ampere.modules.AmpereModule;
import ampere.util.AmpereMessaging;
import ampere.util.AmpereCustomFilterOverlay;
import ampere.util.AmpereCustomFilterPresetOverlay;
import ampere.util.AmpereKeybindOverlay;
import ampere.util.AmpereLANSync;
import ampere.util.AmpereLANSyncOverlay;
import ampere.util.AmpereLauncherOverlay;
import ampere.util.AmpereMacroEditorOverlay;
import ampere.util.AmpereMacroListOverlay;
import ampere.util.AmpereOverlayManager;
import ampere.util.AmperePacketLoggerOverlay;
import ampere.util.AmpereQueueEditorOverlay;
import ampere.util.AmpereSharedState;
import ampere.util.AmpereSpecialGuiActions;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BookSignScreen.class)
public abstract class AmpereBookSignScreenMixin extends Screen implements AmpereSpecialGuiActions {
    @Unique private static final Minecraft MC = Minecraft.getInstance();

    @Unique private AmpereLauncherOverlay launcherOverlay;
    @Unique private AmpereLANSyncOverlay lanSyncOverlay;
    @Unique private AmpereMacroListOverlay macroListOverlay;
    @Unique private AmpereQueueEditorOverlay queueEditorOverlay;
    @Unique private AmperePacketLoggerOverlay packetLoggerOverlay;
    @Unique private AmpereCustomFilterOverlay customFilterOverlay;
    @Unique private AmpereCustomFilterPresetOverlay customFilterPresetOverlay;
    @Unique private AmpereMacroEditorOverlay macroEditorOverlay;
    @Unique private AmpereKeybindOverlay keybindOverlay;
    @Unique private ampere.util.AmpereServerInfoOverlay serverInfoOverlay;

    protected AmpereBookSignScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void Ampere$init(CallbackInfo ci) {
        if (!Ampere$isAmpereActive()) return;

        AmpereLANSync.getInstance().setOnSessionStateChanged(() -> {});

        lanSyncOverlay = AmpereLANSyncOverlay.getSharedOverlay(this.font);
        macroListOverlay = new AmpereMacroListOverlay(this.font);
        queueEditorOverlay = new AmpereQueueEditorOverlay(this.font);
        customFilterOverlay = new AmpereCustomFilterOverlay(this.font);
        customFilterPresetOverlay = customFilterOverlay.getPresetManagerOverlay();

        lanSyncOverlay.restoreState();
        macroListOverlay.restoreState();
        queueEditorOverlay.restoreState();
        customFilterOverlay.restoreLayout();
        if (customFilterPresetOverlay != null) customFilterPresetOverlay.restoreLayout();

        macroEditorOverlay = AmpereMacroEditorOverlay.getSharedOverlay();
        if (macroEditorOverlay != null) macroEditorOverlay.restoreState();

        AmpereOverlayManager manager = AmpereOverlayManager.get();
        manager.clear();
        manager.register(lanSyncOverlay);
        manager.register(macroListOverlay);
        manager.register(queueEditorOverlay);
        manager.register(customFilterOverlay);
        if (customFilterPresetOverlay != null) manager.register(customFilterPresetOverlay);
        if (macroEditorOverlay != null) manager.register(macroEditorOverlay);

        AmpereModule ampereModule = AmpereModule.get();

        keybindOverlay = new AmpereKeybindOverlay();
        keybindOverlay.restoreLayout();
        manager.register(keybindOverlay);

        launcherOverlay = new AmpereLauncherOverlay(macroListOverlay, null, lanSyncOverlay, queueEditorOverlay, packetLoggerOverlay, customFilterOverlay);
        launcherOverlay.setKeybindOverlay(keybindOverlay);
        launcherOverlay.setPacketLoggerOverlaySupplier(() -> {
            if (packetLoggerOverlay == null && ampereModule != null) {
                packetLoggerOverlay = ampereModule.getPacketLoggerOverlay();
                if (packetLoggerOverlay != null) packetLoggerOverlay.restoreLayout();
            }
            if (packetLoggerOverlay != null) manager.register(packetLoggerOverlay);
            return packetLoggerOverlay;
        });
        launcherOverlay.setServerDataOverlaySupplier(() -> {
            if (serverInfoOverlay == null) {
                serverInfoOverlay = AmpereModule.get().getServerDataOverlay();
            }
            if (serverInfoOverlay != null) manager.register(serverInfoOverlay);
            return serverInfoOverlay;
        });
        if (serverInfoOverlay == null && ampere.util.AmpereServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = AmpereModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }
        launcherOverlay.restoreLayout();
        manager.register(launcherOverlay);

        Screen screen = (Screen) (Object) this;
        ScreenEvents.afterExtract(screen).register((scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (Ampere$isAmpereActive()) {
                AmpereOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
            }
        });
    }

    @Override
    public void removed() {
        if (Ampere$isAmpereActive()) {
            if (lanSyncOverlay != null) lanSyncOverlay.saveState();
            if (macroListOverlay != null) macroListOverlay.saveState();
            if (queueEditorOverlay != null) queueEditorOverlay.saveState();
            if (macroEditorOverlay != null) macroEditorOverlay.saveState();
            if (launcherOverlay != null) launcherOverlay.saveLayout();
            if (packetLoggerOverlay != null) packetLoggerOverlay.saveLayout();
            if (customFilterOverlay != null) customFilterOverlay.saveLayout();
            if (customFilterPresetOverlay != null) customFilterPresetOverlay.saveLayout();
            if (keybindOverlay != null) keybindOverlay.saveLayout();
            if (serverInfoOverlay != null) serverInfoOverlay.saveState();
            AmpereOverlayManager.get().clear();
        }
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (Ampere$isAmpereActive() && AmpereOverlayManager.get().handleMouseClicked(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (Ampere$isAmpereActive() && AmpereOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (Ampere$isAmpereActive() && AmpereOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (Ampere$isAmpereActive() && AmpereOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void Ampere$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!Ampere$isAmpereActive()) return;
        if (AmpereOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (Ampere$isAmpereActive() && AmpereOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Unique
    private boolean Ampere$isAmpereActive() {
        AmpereModule module = AmpereModule.get();
        return module != null && module.isActive();
    }

    @Override
    public void Ampere$closeWithPacket() {
        Ampere$closeWithPacket(true);
    }

    @Override
    public void Ampere$closeWithPacket(boolean notify) {
        if (MC.getConnection() != null) {
            AmpereSharedState.get().setForceNextBookEditPacket(true);
            Ampere$invokeSaveChanges();
        }
        MC.setScreen(null);
    }

    @Override
    public void Ampere$closeWithoutPacket() {
        Ampere$closeWithoutPacket(true);
    }

    @Override
    public void Ampere$closeWithoutPacket(boolean notify) {
        MC.setScreen(null);
        if (notify) AmpereMessaging.sendPrefixed("Book signing closed without packet.");
    }

    @Override
    public void Ampere$desync() {
        Ampere$desync(true);
    }

    @Override
    public void Ampere$desync(boolean notify) {
        if (MC.getConnection() == null) {
            if (notify) AmpereMessaging.sendPrefixed("Failed to desync: no network.");
            return;
        }
        AmpereSharedState.get().setForceNextBookEditPacket(true);
        Ampere$invokeSaveChanges();
        if (notify) AmpereMessaging.sendPrefixed("Signed book packet sent; GUI intentionally stays open.");
    }

    @Invoker("saveChanges")
    protected abstract void Ampere$invokeSaveChanges();
}
