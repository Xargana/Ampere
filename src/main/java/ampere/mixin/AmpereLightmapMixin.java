package ampere.mixin;

import ampere.modules.PackModuleRenderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lightmap.class)
public abstract class AmpereLightmapMixin {
    @Shadow
    @Final
    private GpuTexture texture;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void Ampere$fullbrightLightmap(LightmapRenderState renderState, CallbackInfo ci) {
        if (!PackModuleRenderUtil.hasBrightLightmapWork()) return;
        var profiler = Profiler.get();
        profiler.push("Ampere_lightmap");
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, -1);
        profiler.pop();
        ci.cancel();
    }
}
