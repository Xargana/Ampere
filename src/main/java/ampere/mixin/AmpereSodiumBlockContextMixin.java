package ampere.mixin;

import ampere.modules.PackModuleRenderUtil;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext")
public class AmpereSodiumBlockContextMixin {
    @Shadow(remap = false) protected BlockState state;
    @Shadow(remap = false) protected BlockAndTintGetter level;
    @Shadow(remap = false) protected BlockPos pos;

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, remap = false)
    private void Ampere$xrayForceSodiumFaces(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (PackModuleRenderUtil.hasXrayRenderWork() && PackModuleRenderUtil.shouldForceXrayFace(state)) cir.setReturnValue(true);
    }

    @Inject(method = "shouldDrawSide", at = @At("RETURN"), cancellable = true, remap = false)
    private void Ampere$xraySodiumFaces(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (PackModuleRenderUtil.hasXrayRenderWork()) {
            cir.setReturnValue(PackModuleRenderUtil.modifyXrayFace(level, state, direction, pos, cir.getReturnValue()));
        }
    }
}
