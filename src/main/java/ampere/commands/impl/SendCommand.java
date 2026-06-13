package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.commands.args.PacketClassArgumentType;
import ampere.util.AmpereMessaging;
import ampere.util.AmperePacketRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.lang.reflect.Constructor;

public class SendCommand extends Command {
    public SendCommand() { super("send", "Send a raw C2S packet by class name (no-arg constructor only)."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            AmpereMessaging.sendPrefixed("§eUsage: §f" + AmpereCommands.effectivePrefix() + "send <packetName>");
            AmpereMessaging.sendPrefixed("§7Tip: press Tab after the space to search C2S packet classes.");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AmpereCommandSource, String>argument("packet", PacketClassArgumentType.packetClass())
            .executes(ctx -> {
                String name = PacketClassArgumentType.get(ctx, "packet");
                Class<? extends Packet<?>> cls = AmperePacketRegistry.getPacket(name);
                if (cls == null) { AmpereMessaging.sendPrefixed("§cUnknown packet: §f" + name); return SUCCESS; }
                try {
                    Constructor<? extends Packet<?>> ctor = null;
                    for (Constructor<?> c : cls.getDeclaredConstructors()) {
                        if (c.getParameterCount() == 0) { @SuppressWarnings("unchecked") Constructor<? extends Packet<?>> ok = (Constructor<? extends Packet<?>>) c; ctor = ok; break; }
                    }
                    if (ctor == null) { AmpereMessaging.sendPrefixed("§cPacket " + name + " has no no-arg constructor — build it via Send Packet macro action instead."); return SUCCESS; }
                    ctor.setAccessible(true);
                    Packet<?> packet = ctor.newInstance();
                    var conn = Minecraft.getInstance().getConnection();
                    if (conn == null) { AmpereMessaging.sendPrefixed("§cNo network connection."); return SUCCESS; }
                    ampere.util.AmpereSharedState.get().sendPacketBypassDelay(conn, packet);
                    AmpereMessaging.sendPrefixed("§aSent §f" + name);
                } catch (Throwable t) {
                    AmpereMessaging.sendPrefixed("§cFailed to send §f" + name + "§c: " + t.getMessage());
                }
                return SUCCESS;
            }));
    }
}
