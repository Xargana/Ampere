package ampere.mixin.security;

import ampere.security.AmpereProtectorModResolver;
import ampere.security.AmpereProtectorTracker;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnstableApiUsage")
@Mixin(PayloadTypeRegistryImpl.class)
public class AmpereProtectorPayloadTypeRegistryImplMixin {
    @Inject(method = "register", at = @At("RETURN"))
    private void Ampere$trackPayloadDefaultMod(CustomPacketPayload.Type<?> type, StreamCodec<?, ?> codec,
                                               CallbackInfoReturnable<CustomPacketPayload.TypeAndCodec<?, ?>> cir) {
        for (String mod : AmpereProtectorModResolver.modsFromStacktrace()) {
            AmpereProtectorTracker.addDefaultAllowedMod(mod);
            AmpereProtectorTracker.addDefaultAllowedMods(AmpereProtectorModResolver.dependenciesFor(mod));
        }
    }
}
