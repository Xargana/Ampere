package ampere.mixin;

import ampere.modules.PackModuleRenderUtil;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public class AmpereVisGraphMixin {
    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void Ampere$xrayDisableChunkOcclusion(BlockPos pos, CallbackInfo ci) {
        if (PackModuleRenderUtil.hasXrayRenderWork()) ci.cancel();
    }
}
