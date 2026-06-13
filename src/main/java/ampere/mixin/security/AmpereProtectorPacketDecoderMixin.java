package ampere.mixin.security;

import ampere.security.AmpereProtector;
import ampere.security.AmpereProtectorPacketContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketDecoder.class)
public class AmpereProtectorPacketDecoderMixin {

    @WrapOperation(
        method = "decode",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object Ampere$wrapDecode(StreamCodec instance, Object buffer, Operation<Object> original) {
        if (!AmpereProtector.shouldTagPacketComponents()) return original.call(instance, buffer);
        AmpereProtectorPacketContext.setProcessingPacket(true);
        try {
            return original.call(instance, buffer);
        } finally {
            AmpereProtectorPacketContext.setProcessingPacket(false);
        }
    }
}
