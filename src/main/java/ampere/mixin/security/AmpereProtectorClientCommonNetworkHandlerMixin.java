package ampere.mixin.security;

import ampere.security.AmpereProtectorPackStrip;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class AmpereProtectorClientCommonNetworkHandlerMixin {

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"))
    private void Ampere$onPackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        AmpereProtectorPackStrip.onPackPush(packet.id());
    }

    @Inject(method = "handleResourcePackPop", at = @At("HEAD"))
    private void Ampere$onPackPop(ClientboundResourcePackPopPacket packet, CallbackInfo ci) {
        Optional<UUID> id = packet.id();
        AmpereProtectorPackStrip.onPop(id.orElse(null));
    }
}
