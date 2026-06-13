package ampere.mixin.security;

import ampere.security.AmpereProtector;
import ampere.security.AmpereProtectorPacketContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public class AmpereProtectorPacketProcessorMixin {

    @WrapOperation(
        method = "handle",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V")
    )
    private <T extends PacketListener> void Ampere$wrapHandle(Packet<?> instance, T listener,
                                                              Operation<Void> original) {
        if (!AmpereProtector.shouldTagPacketComponents()) {
            original.call(instance, listener);
            return;
        }
        AmpereProtectorPacketContext.setProcessingPacket(true);
        try {
            original.call(instance, listener);
        } finally {
            AmpereProtectorPacketContext.setProcessingPacket(false);
        }
    }
}
