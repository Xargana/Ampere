package ampere.mixin.security;

import ampere.security.AmpereProtectorLocalAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(Connection.class)
public class AmpereProtectorConnectionTrackingMixin {

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void Ampere$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
        try {
            if (context.channel() == null) return;
            SocketAddress addr = context.channel().remoteAddress();
            if (addr instanceof InetSocketAddress inet && inet.getAddress() != null) {
                AmpereProtectorLocalAddressUtil.serverAddress = inet.getAddress().getHostAddress();
            } else {
                AmpereProtectorLocalAddressUtil.serverAddress = null;
            }
        } catch (Throwable ignored) {
            AmpereProtectorLocalAddressUtil.serverAddress = null;
        }
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void Ampere$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
        AmpereProtectorLocalAddressUtil.serverAddress = null;
    }
}
