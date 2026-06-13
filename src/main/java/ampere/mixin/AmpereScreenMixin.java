package ampere.mixin;

import ampere.gui.vanillaui.components.ScreenButton;
import ampere.ducks.AmpereExternalButtonScreen;
import ampere.mixin.accessor.AmpereScreenAccessor;
import ampere.modules.AmpereModule;
import ampere.util.AmpereLecternButtons;
import ampere.util.AmpereOverlayManager;
import ampere.util.AmpereNotifications;
import ampere.util.AmperePacketLoggerOverlay;
import ampere.util.AmpereQueueEditorOverlay;
import ampere.util.AmpereUiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class AmpereScreenMixin {
    @Unique private static final Minecraft MC = Minecraft.getInstance();

    @Unique private boolean Ampere$lecternInitialized;
    @Unique private AmpereQueueEditorOverlay Ampere$queueEditorOverlay;
    @Unique private AmperePacketLoggerOverlay Ampere$packetLoggerOverlay;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void Ampere$onInit(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!(screen instanceof LecternScreen) || !Ampere$isModuleActive()) return;

        if (Ampere$lecternInitialized) {
            if (Ampere$queueEditorOverlay != null) Ampere$queueEditorOverlay.restoreState();
            if (Ampere$queueEditorOverlay != null) AmpereOverlayManager.get().register(Ampere$queueEditorOverlay);
            return;
        }

        Font textRenderer = ((AmpereScreenAccessor) this).getFont();
        Ampere$queueEditorOverlay = new AmpereQueueEditorOverlay(textRenderer);
        Ampere$queueEditorOverlay.restoreState();
        AmpereOverlayManager.get().register(Ampere$queueEditorOverlay);

        Ampere$lecternInitialized = true;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void Ampere$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof AmpereExternalButtonScreen externalButtonScreen) {
            externalButtonScreen.Ampere$renderExternalButtons(context, mouseX, mouseY, delta);
        }

        if (!Ampere$isModuleActive()) return;

        Font textRenderer = ((AmpereScreenAccessor) this).getFont();
        if (textRenderer == null) return;

        if (!(screen instanceof LecternScreen) || MC.player == null) return;

        AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler != null) {
            int virtualMouseX = AmpereUiScale.toVirtualInt(mouseX);
            int virtualMouseY = AmpereUiScale.toVirtualInt(mouseY);
            AmpereUiScale.pushOverlayScale(context);
            try {
                for (ScreenButton button : AmpereLecternButtons.build(MC, Ampere$queueEditorOverlay)) {
                    button.render(context, textRenderer, virtualMouseX, virtualMouseY);
                }
            } finally {
                AmpereUiScale.popOverlayScale(context);
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void Ampere$renderTopmostNotifications(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Ampere$isModuleActive() || !AmpereNotifications.hasVisible()) return;
        context.nextStratum();
        AmpereUiScale.pushOverlayScale(context);
        try {
            AmpereNotifications.render(context);
        } finally {
            AmpereUiScale.popOverlayScale(context);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void Ampere$onClose(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof LecternScreen) {
            AmpereOverlayManager.get().unregister(Ampere$queueEditorOverlay);
            AmpereOverlayManager.get().unregister(Ampere$packetLoggerOverlay);
        }
    }

    @Unique
    private boolean Ampere$isModuleActive() {
        AmpereModule module = AmpereModule.get();
        return module != null && module.isActive();
    }

}
