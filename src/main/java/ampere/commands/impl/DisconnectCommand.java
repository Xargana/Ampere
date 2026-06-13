package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DisconnectCommand extends Command {
    public DisconnectCommand() { super("disconnect", "Disconnect from the current server."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) { AmpereMessaging.sendPrefixed("§cNot connected."); return SUCCESS; }
            try {
                mc.getConnection().getConnection().disconnect(Component.literal("Disconnected via .disconnect"));
            } catch (Throwable t) {
                AmpereMessaging.sendPrefixed("§cDisconnect failed: " + t.getMessage());
            }
            return SUCCESS;
        });
    }
}
