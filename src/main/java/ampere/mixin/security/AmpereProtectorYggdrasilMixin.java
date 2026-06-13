package ampere.mixin.security;

import ampere.security.AmpereProtector;
import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executor;

@Mixin(value = YggdrasilUserApiService.class, remap = false)
public class AmpereProtectorYggdrasilMixin {

    @Inject(method = "newTelemetrySession", at = @At("HEAD"), cancellable = true)
    private void Ampere$disableTelemetrySession(Executor executor, CallbackInfoReturnable<TelemetrySession> info) {
        if (AmpereProtector.shouldDisableTelemetry()) {
            info.setReturnValue(TelemetrySession.DISABLED);
        }
    }
}
