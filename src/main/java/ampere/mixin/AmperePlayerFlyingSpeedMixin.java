package ampere.mixin;

import ampere.modules.PackModuleMovementUtil;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class AmperePlayerFlyingSpeedMixin {
    @Inject(method = "getFlyingSpeed", at = @At("HEAD"), cancellable = true)
    private void Ampere$flightFlyingSpeed(CallbackInfoReturnable<Float> cir) {
        float speed = PackModuleMovementUtil.flightFlyingSpeed();
        if (speed != -1.0f) cir.setReturnValue(speed);
    }
}
