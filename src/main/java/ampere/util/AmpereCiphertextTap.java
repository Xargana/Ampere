package ampere.util;

import ampere.modules.AmpereModule;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public final class AmpereCiphertextTap extends ChannelDuplexHandler {
    private final String direction;

    public AmpereCiphertextTap(String direction) {
        this.direction = direction;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (packetHooksActive() && msg instanceof ByteBuf buf) {
            AmperePacketCapture.captureCiphertext(ctx.channel(), direction, buf);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (packetHooksActive() && msg instanceof ByteBuf buf) {
            AmperePacketCapture.captureCiphertext(ctx.channel(), direction, buf);
        }
        super.write(ctx, msg, promise);
    }

    private static boolean packetHooksActive() {
        AmpereModule module = AmpereModule.get();
        return module != null && module.shouldCapturePacketPlaintext();
    }
}
