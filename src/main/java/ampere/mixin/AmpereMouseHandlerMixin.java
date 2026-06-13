package ampere.mixin;

import ampere.modules.PackModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class AmpereMouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Unique private float Ampere$turnStartYaw;
    @Unique private float Ampere$turnStartPitch;
    @Unique private boolean Ampere$turnHadPlayer;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void Ampere$beforeTurnPlayer(double deltaTime, CallbackInfo ci) {
        if (!PackModuleRegistry.hasMouseRotationHooks()) {
            Ampere$turnHadPlayer = false;
            return;
        }
        Ampere$turnHadPlayer = minecraft != null && minecraft.player != null;
        if (Ampere$turnHadPlayer) {
            Ampere$turnStartYaw = minecraft.player.getYRot();
            Ampere$turnStartPitch = minecraft.player.getXRot();
        }
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void Ampere$afterTurnPlayer(double deltaTime, CallbackInfo ci) {
        if (!Ampere$turnHadPlayer || minecraft == null || minecraft.player == null) return;
        double deltaYaw = minecraft.player.getYRot() - Ampere$turnStartYaw;
        double deltaPitch = minecraft.player.getXRot() - Ampere$turnStartPitch;
        if (Math.abs(deltaYaw) > 1.0E-6 || Math.abs(deltaPitch) > 1.0E-6) {
            PackModuleRegistry.onMouseRotation(deltaYaw, deltaPitch);
        }
    }
}
