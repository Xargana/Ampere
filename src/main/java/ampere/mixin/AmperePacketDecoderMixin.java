package ampere.mixin;

import ampere.modules.AmpereModule;
import ampere.util.AmperePacketCapture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
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

import java.util.List;

@Mixin(PacketDecoder.class)
public abstract class AmperePacketDecoderMixin<T extends PacketListener> {
    @Shadow @Final private ProtocolInfo<T> protocolInfo;
    @Unique private byte[] Ampere$incomingPlaintext = new byte[0];

    @Inject(method = "decode", at = @At("HEAD"))
    private void Ampere$captureIncomingPlaintext(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, CallbackInfo ci) {
        Ampere$incomingPlaintext = Ampere$shouldCapturePacketPlaintext()
            ? AmperePacketCapture.copyReadableBytes(input)
            : new byte[0];
    }

    @Inject(method = "decode", at = @At("TAIL"))
    private void Ampere$attachIncomingPlaintext(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, CallbackInfo ci) {
        if (!Ampere$shouldCapturePacketPlaintext() || Ampere$incomingPlaintext.length == 0 || out.isEmpty()) return;
        Object decoded = out.get(out.size() - 1);
        if (decoded instanceof Packet<?> packet) {
            AmperePacketCapture.capturePlaintext(packet, "S2C", protocolInfo.id().id(), packet.type(),
                Unpooled.wrappedBuffer(Ampere$incomingPlaintext));
        }
    }

    @Unique
    private static boolean Ampere$shouldCapturePacketPlaintext() {
        AmpereModule module = AmpereModule.get();
        return module != null && module.shouldCapturePacketPlaintext();
    }
}
