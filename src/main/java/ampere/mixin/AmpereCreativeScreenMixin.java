package ampere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ampere.modules.AmpereModule;
import ampere.util.AmpereOverlayManager;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.CreativeModeTab;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class AmpereCreativeScreenMixin {
    @Shadow protected abstract void extractTabButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CreativeModeTab tab);

    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<Boolean> Ampere$inSafeRecall = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$blockCoveredTabHover(GuiGraphicsExtractor graphics, CreativeModeTab tab, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        AmpereModule module = AmpereModule.get();
        if (module == null || !module.isActive()) return;
        if (AmpereOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractTabButton", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$blockCoveredTabCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CreativeModeTab tab, CallbackInfo ci) {
        if (Ampere$inSafeRecall.get()) return;
        AmpereModule module = AmpereModule.get();
        if (module == null || !module.isActive()) return;
        if (!AmpereOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) return;

        Ampere$inSafeRecall.set(Boolean.TRUE);
        try {
            this.extractTabButton(graphics, AmpereOverlayManager.HOVER_BLOCKED_MOUSE, AmpereOverlayManager.HOVER_BLOCKED_MOUSE, tab);
        } finally {
            Ampere$inSafeRecall.set(Boolean.FALSE);
        }
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        AmpereModule module = AmpereModule.get();
        if (module == null || !module.isActive()) return;

        if (AmpereOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        AmpereModule module = AmpereModule.get();
        if (module == null || !module.isActive()) return;

        if (AmpereOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$mouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        AmpereModule module = AmpereModule.get();
        if (module == null || !module.isActive()) return;

        if (AmpereOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        AmpereModule module = AmpereModule.get();
        if (module == null || !module.isActive()) return;

        if (AmpereOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        AmpereModule module = AmpereModule.get();
        if (module == null || !module.isActive()) return;

        if (AmpereOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$charTyped(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
        AmpereModule module = AmpereModule.get();
        if (module == null || !module.isActive()) return;

        if (AmpereOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            cir.setReturnValue(true);
        }
    }
}
