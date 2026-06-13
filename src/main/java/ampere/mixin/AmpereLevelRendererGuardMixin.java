package ampere.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class AmpereLevelRendererGuardMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void Ampere$skipUpdateWithoutPlayer(Camera camera, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) ci.cancel();
    }

    @Inject(method = "extractLevel", at = @At("HEAD"), cancellable = true)
    private void Ampere$skipExtractWithoutPlayer(DeltaTracker deltaTracker, Camera camera, float deltaPartialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) ci.cancel();
    }
}
