package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.commands.args.KeyArgumentType;
import ampere.modules.AmpereModule;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class BindCommand extends Command {
    public BindCommand() { super("bind", "Bind a key to a command. Usage: bind <key> <command…> | bind clear <key>"); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            String prefix = AmpereCommands.effectivePrefix();
            AmpereMessaging.sendPrefixed("§eUsage: §f" + prefix + "bind <key> <command>");
            AmpereMessaging.sendPrefixed("§7Example: §f" + prefix + "bind G " + prefix + "macro myMacro");
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("clear")
            .then(RequiredArgumentBuilder.<AmpereCommandSource, Integer>argument("key", KeyArgumentType.key())
                .executes(ctx -> {
                    int key = KeyArgumentType.get(ctx, "key");
                    AmpereModule.get().clearCommandBind(key);
                    AmpereMessaging.sendPrefixed("§eCleared bind for §f" + KeyArgumentType.keyName(key));
                    return SUCCESS;
                })));
        root.then(RequiredArgumentBuilder.<AmpereCommandSource, Integer>argument("key", KeyArgumentType.key())
            .then(RequiredArgumentBuilder.<AmpereCommandSource, String>argument("command", StringArgumentType.greedyString())
                .executes(ctx -> {
                    int key = KeyArgumentType.get(ctx, "key");
                    String cmd = StringArgumentType.getString(ctx, "command");
                    AmpereModule.get().setCommandBind(key, cmd);
                    AmpereMessaging.sendPrefixed("§aBound §f" + KeyArgumentType.keyName(key) + "§a → §f" + cmd);
                    return SUCCESS;
                })));
    }
}
