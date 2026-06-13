package ampere.mixin;

import ampere.modules.NameCensorModule;
import ampere.modules.PackModuleRenderUtil;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class AmpereEntityRendererLightMixin<T extends Entity> {
    @Inject(method = "getSkyLightLevel", at = @At("RETURN"), cancellable = true)
    private void Ampere$fullbrightSkyLight(T entity, BlockPos blockPos, CallbackInfoReturnable<Integer> cir) {
        if (!PackModuleRenderUtil.hasFullbrightLuminanceWork()) return;
        int boosted = PackModuleRenderUtil.fullbrightLuminance(LightLayer.SKY);
        if (boosted > cir.getReturnValue()) cir.setReturnValue(boosted);
    }

    @Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
    private void Ampere$fullbrightBlockLight(T entity, BlockPos blockPos, CallbackInfoReturnable<Integer> cir) {
        if (!PackModuleRenderUtil.hasFullbrightLuminanceWork()) return;
        int boosted = PackModuleRenderUtil.fullbrightLuminance(LightLayer.BLOCK);
        if (boosted > cir.getReturnValue()) cir.setReturnValue(boosted);
    }

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void Ampere$censorNameTag(T entity, CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() != null) {
            cir.setReturnValue(NameCensorModule.censorServerComponent(cir.getReturnValue()));
        }
    }
}
