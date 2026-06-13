package ampere.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import ampere.modules.PackModuleRenderUtil;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidRenderer.class)
public class AmpereFluidRendererMixin {
    @Unique private static final ThreadLocal<Integer> PACKUTIL_XRAY_FLUID_ALPHA = ThreadLocal.withInitial(() -> -1);
    @Unique private static final ThreadLocal<Boolean> PACKUTIL_FORCE_XRAY_FLUID_SIDES = ThreadLocal.withInitial(() -> false);

    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$xrayFluidMode(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        PACKUTIL_XRAY_FLUID_ALPHA.set(-1);
        PACKUTIL_FORCE_XRAY_FLUID_SIDES.set(false);
        if (!PackModuleRenderUtil.hasXrayRenderWork()) return;
        int alpha = PackModuleRenderUtil.xrayFluidAlpha(level, pos, fluidState);
        if (alpha == 0) {
            ci.cancel();
            return;
        }
        PACKUTIL_XRAY_FLUID_ALPHA.set(alpha);
        PACKUTIL_FORCE_XRAY_FLUID_SIDES.set(PackModuleRenderUtil.shouldForceXrayFluidSides());
    }

    @Inject(method = "tesselate", at = @At("RETURN"), require = 0)
    private void Ampere$clearXrayFluidMode(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        PACKUTIL_XRAY_FLUID_ALPHA.set(-1);
        PACKUTIL_FORCE_XRAY_FLUID_SIDES.set(false);
    }

    @Inject(method = "isFaceOccludedByNeighbor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void Ampere$xrayFluidSides(Direction direction, float height, BlockState neighborState, CallbackInfoReturnable<Boolean> cir) {
        boolean occluded = cir.getReturnValue();
        if (!occluded) return;
        if (direction.getAxis().isVertical()) return;
        if (!PACKUTIL_FORCE_XRAY_FLUID_SIDES.get()) return;
        cir.setReturnValue(!PackModuleRenderUtil.shouldKeepXrayFluidSide(neighborState));
    }

    @Inject(method = "vertex", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$xrayFluidAlpha(VertexConsumer builder, float x, float y, float z, int color, float u, float v, int lightCoords, CallbackInfo ci) {
        int alpha = PACKUTIL_XRAY_FLUID_ALPHA.get();
        if (alpha == -1) return;
        builder.addVertex(x, y, z)
            .setColor(ARGB.red(color), ARGB.green(color), ARGB.blue(color), alpha)
            .setUv(u, v)
            .setLight(lightCoords)
            .setNormal(0.0F, 1.0F, 0.0F);
        ci.cancel();
    }

    @ModifyArg(method = "tesselate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidRenderer$Output;getBuilder(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), index = 0, require = 0)
    private ChunkSectionLayer Ampere$xrayFluidLayer(ChunkSectionLayer layer) {
        int alpha = PACKUTIL_XRAY_FLUID_ALPHA.get();
        if (alpha > 0 && alpha < 255) return ChunkSectionLayer.TRANSLUCENT;
        return layer;
    }
}
