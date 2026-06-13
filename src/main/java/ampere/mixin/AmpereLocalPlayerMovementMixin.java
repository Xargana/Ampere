package ampere.mixin;

import ampere.modules.PackFreecamState;
import ampere.modules.PackModuleMovementUtil;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class AmpereLocalPlayerMovementMixin {
    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private void Ampere$flightNoSneak(CallbackInfoReturnable<Boolean> cir) {
        if (PackModuleMovementUtil.flightNoSneak() || PackFreecamState.isActive()) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V",
        ordinal = 0))
    private void Ampere$autoSprintUpdate(CallbackInfo ci) {
        PackModuleMovementUtil.autoSprintLocalPlayerTick();
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z",
        ordinal = 0))
    private boolean Ampere$autoSprintOmniForwardImpulse(ClientInput input) {
        if (PackModuleMovementUtil.sprintIsOmnidirectional()) {
            return input.getMoveVector().length() > 1.0E-5F;
        }
        return input.hasForwardImpulse();
    }

    @Inject(method = "isSprintingPossible", at = @At("HEAD"), cancellable = true)
    private void Ampere$autoSprintHungry(boolean allowedInShallowWater, CallbackInfoReturnable<Boolean> cir) {
        if (PackModuleMovementUtil.sprintIgnoresHunger()) cir.setReturnValue(true);
    }
}
