package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.commands.CommandSuggest;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public class DamageCommand extends Command {
    public DamageCommand() { super("damage", "Take damage to self (vanilla server allows via /damage if op)."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            AmpereMessaging.sendPrefixed("§eUsage: " + AmpereCommands.effectivePrefix() + "damage <amount>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AmpereCommandSource, Double>argument("amount", DoubleArgumentType.doubleArg(0.1, 1024.0))
            .suggests(CommandSuggest::damage)
            .executes(ctx -> {
                double amt = DoubleArgumentType.getDouble(ctx, "amount");
                var conn = Minecraft.getInstance().getConnection();
                if (conn == null) { AmpereMessaging.sendPrefixed("§cNot connected."); return SUCCESS; }
                conn.sendCommand("damage @s " + amt);
                return SUCCESS;
            }));
    }
}
