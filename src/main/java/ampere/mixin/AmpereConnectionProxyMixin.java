package ampere.mixin;

import ampere.util.AmpereProxy;
import ampere.util.AmpereProxyManager;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;

@Mixin(value = Connection.class, priority = 2000)
public abstract class AmpereConnectionProxyMixin {
    @Inject(method = "configureSerialization", at = @At("HEAD"))
    private static void Ampere$disableMeteorProxyBeforeHandlers(ChannelPipeline pipeline, PacketFlow inboundDirection, boolean local, @Nullable BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (local || inboundDirection != PacketFlow.CLIENTBOUND) return;
        Ampere$disableMeteorProxy();
    }

    @Inject(method = "configureSerialization", at = @At("RETURN"))
    private static void Ampere$addProxyHandler(ChannelPipeline pipeline, PacketFlow inboundDirection, boolean local, @Nullable BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (local || inboundDirection != PacketFlow.CLIENTBOUND) return;
        AmpereProxy proxy = AmpereProxyManager.get().getEnabled();
        if (proxy == null) return;
        InetSocketAddress address = new InetSocketAddress(proxy.address, proxy.port);
        switch (proxy.type) {
            case Socks4 -> pipeline.addFirst("Ampere_socks4_proxy", new Socks4ProxyHandler(address, proxy.username == null ? "" : proxy.username));
            case Socks5 -> pipeline.addFirst("Ampere_socks5_proxy", new Socks5ProxyHandler(address, proxy.username == null ? "" : proxy.username, proxy.password == null ? "" : proxy.password));
        }
    }

    private static void Ampere$disableMeteorProxy() {
        if (!FabricLoader.getInstance().isModLoaded("meteor-client")) return;
        try {
            Class<?> proxiesClass = Class.forName("meteordevelopment.meteorclient.systems.proxies.Proxies");
            Class<?> proxyClass = Class.forName("meteordevelopment.meteorclient.systems.proxies.Proxy");
            Object proxies = proxiesClass.getMethod("get").invoke(null);
            Object enabled = proxiesClass.getMethod("getEnabled").invoke(proxies);
            if (enabled != null) proxiesClass.getMethod("setEnabled", proxyClass, boolean.class).invoke(proxies, enabled, false);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
