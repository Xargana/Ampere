package ampere.mixin;

import net.minecraft.client.renderer.Panorama;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Panorama.class)
public abstract class AmperePanoramaOverlayMixin {
    @Unique
    private static final Identifier Ampere_PANORAMA_OVERLAY =
        Identifier.fromNamespaceAndPath("ampere", "textures/gui/title/background/panorama_overlay.png");

    @ModifyArg(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIII)V"
        ),
        index = 1
    )
    private Identifier Ampere$swapOverlay(Identifier original) {
        return ampere.util.AmpereMenuPrefs.vanillaMenuVisuals() ? original : Ampere_PANORAMA_OVERLAY;
    }
}
