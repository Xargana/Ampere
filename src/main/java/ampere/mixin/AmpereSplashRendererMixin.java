package ampere.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SplashRenderer.class, priority = 2000)
public class AmpereSplashRendererMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void Ampere$hideSplashText(GuiGraphicsExtractor graphics, int screenWidth, Font font, float alpha, CallbackInfo ci) {
        if (ampere.modules.PackHideState.isActive()) {
            if (!Minecraft.getInstance().options.hideSplashTexts().get()) {
                ampere.util.AmpereVanillaSplash.renderPanicSplash(Minecraft.getInstance(), graphics, screenWidth, font, alpha);
            }
            ci.cancel();
            return;
        }

        if (ampere.util.AmpereMenuPrefs.vanillaMenuVisuals()) return;
        ci.cancel();
    }
}
