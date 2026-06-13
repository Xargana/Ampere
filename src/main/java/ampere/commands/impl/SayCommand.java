package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public class SayCommand extends Command {
    public SayCommand() { super("say", "Send a chat message."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            AmpereMessaging.sendPrefixed("§eUsage: " + AmpereCommands.effectivePrefix() + "say <message>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AmpereCommandSource, String>argument("message", StringArgumentType.greedyString())
            .executes(ctx -> {
                String msg = StringArgumentType.getString(ctx, "message");
                var conn = Minecraft.getInstance().getConnection();
                if (conn == null) { AmpereMessaging.sendPrefixed("§cNot connected."); return SUCCESS; }
                if (msg.startsWith("/")) conn.sendCommand(msg.substring(1));
                else conn.sendChat(msg);
                return SUCCESS;
            }));
    }
}
