package ampere.security;

import ampere.modules.AmpereModule;
import ampere.util.AmperePayloadSupport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public final class AmpereSpoofPayloadFilter extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Packet<?> packet) {
            if (shouldBlockForVanillaSpoof(AmpereModule.get(), packet)) {
                AmpereProtector.consumeUserBypass(packet);
                promise.setSuccess();
                return;
            }

            AmpereProtectorChannelFilter.Verdict verdict = AmpereProtectorChannelFilter.filter(packet);
            switch (verdict.kind) {
                case DROP -> {
                    AmpereProtector.consumeUserBypass(packet);
                    promise.setSuccess();
                    return;
                }
                case REPLACE -> {
                    AmpereProtector.consumeUserBypass(packet);
                    super.write(ctx, verdict.replacement, promise);
                    return;
                }
                case PASS -> AmpereProtector.consumeUserBypass(packet);
            }
        }
        super.write(ctx, msg, promise);
    }

    public static boolean shouldBlockForVanillaSpoof(AmpereModule module, Packet<?> packet) {
        if (!(packet instanceof ServerboundCustomPayloadPacket customPayload)) return false;
        if (module == null || !module.isSpoofClientVanilla()) return false;
        String channel = AmperePayloadSupport.payloadChannel(customPayload.payload());
        return !AmperePayloadSupport.isBrandChannel(channel);
    }

    public static boolean shouldDropForProtector(Packet<?> packet) {
        AmpereProtectorChannelFilter.Verdict verdict = AmpereProtectorChannelFilter.filter(packet);
        return verdict.kind == AmpereProtectorChannelFilter.Verdict.Kind.DROP;
    }
}
