package ampere.mixin;

import ampere.modules.AmpereModule;
import ampere.util.AmperePacketCapture;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketEncoder.class)
public abstract class AmperePacketEncoderMixin<T extends PacketListener> {
    @Shadow @Final private ProtocolInfo<T> protocolInfo;

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
        at = @At("TAIL"))
    private void Ampere$captureEncodedPlaintext(ChannelHandlerContext ctx, Packet<T> packet, ByteBuf output, CallbackInfo ci) {
        if (!Ampere$shouldCapturePacketPlaintext()) return;
        AmperePacketCapture.capturePlaintext(packet, "C2S", protocolInfo.id().id(), packet.type(), output);
    }

    @Unique
    private static boolean Ampere$shouldCapturePacketPlaintext() {
        AmpereModule module = AmpereModule.get();
        return module != null && module.shouldCapturePacketPlaintext();
    }
}
