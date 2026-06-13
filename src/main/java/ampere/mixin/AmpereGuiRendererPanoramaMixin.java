package ampere.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class AmpereGuiRendererPanoramaMixin {
    @Unique
    private final CubeMap Ampere$customCubeMap = new CubeMap(
        Identifier.fromNamespaceAndPath("ampere", "textures/gui/title/background/panorama"));

    @Inject(method = "registerPanoramaTextures", at = @At("TAIL"))
    private void Ampere$registerCustomPanorama(TextureManager textureManager, CallbackInfo ci) {
        Ampere$customCubeMap.registerTextures(textureManager);
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(FF)V")
    )
    private void Ampere$renderPanorama(CubeMap vanillaCubeMap, float rotX, float rotY) {
        CubeMap target = ampere.util.AmpereMenuPrefs.vanillaMenuVisuals() ? vanillaCubeMap : Ampere$customCubeMap;
        target.render(rotX, rotY);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void Ampere$closeCustomPanorama(CallbackInfo ci) {
        Ampere$customCubeMap.close();
    }
}
