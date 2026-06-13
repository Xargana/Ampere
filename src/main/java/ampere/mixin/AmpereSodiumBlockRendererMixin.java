package ampere.mixin;

import ampere.modules.GoldenLeverModule;
import ampere.modules.PackModuleRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class AmpereSodiumBlockRendererMixin {
    @Unique private int Ampere$xrayAlpha = -1;
    @Unique private boolean Ampere$goldenLever;
    @Unique private boolean Ampere$rebufferingXrayQuad;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void Ampere$xraySodiumBlockStart(@Coerce Object model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        boolean xrayActive = PackModuleRenderUtil.hasXrayRenderWork();
        boolean goldenLeverActive = GoldenLeverModule.isStylingActive();
        if (!xrayActive && !goldenLeverActive) {
            Ampere$xrayAlpha = -1;
            Ampere$goldenLever = false;
            return;
        }
        Ampere$xrayAlpha = xrayActive ? PackModuleRenderUtil.xrayAlpha(state, pos) : -1;
        Ampere$goldenLever = goldenLeverActive && GoldenLeverModule.shouldStyle(state);
        if (Ampere$xrayAlpha == 0) ci.cancel();
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void Ampere$xraySodiumBlockEnd(@Coerce Object model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        Ampere$xrayAlpha = -1;
        Ampere$goldenLever = false;
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"), cancellable = true)
    private void Ampere$xraySodiumBlockMaterial(@Coerce Object quad, float[] brightnesses, @Coerce Object material, CallbackInfo ci) {
        int alpha = Ampere$xrayAlpha;
        if (Ampere$rebufferingXrayQuad) return;

        if (Ampere$goldenLever) PackModuleRenderUtil.applySodiumQuadTint(quad, GoldenLeverModule.GOLD_TINT);
        if (alpha < 0) return;
        PackModuleRenderUtil.applySodiumQuadAlpha(quad, alpha);
        if (alpha >= 255) return;

        Object translucent = PackModuleRenderUtil.sodiumTranslucentMaterial(material);
        if (translucent == null || translucent == material) return;

        Ampere$rebufferingXrayQuad = true;
        try {
            Ampere$invokeSodiumBufferQuad(quad, brightnesses, translucent);
        } finally {
            Ampere$rebufferingXrayQuad = false;
        }
        ci.cancel();
    }

    @Unique
    private void Ampere$invokeSodiumBufferQuad(Object quad, float[] brightnesses, Object material) {
        for (Class<?> type = getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("bufferQuad") || method.getParameterCount() != 3) continue;
                try {
                    method.setAccessible(true);
                    method.invoke(this, quad, brightnesses, material);
                    return;
                } catch (Throwable ignored) {
                    return;
                }
            }
        }
    }
}
