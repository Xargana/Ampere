package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Locale;

public class HelpCommand extends Command {
    public HelpCommand() { super("help", "Show usage info for a command (or list all)."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> { listAll(); return SUCCESS; });
        root.then(RequiredArgumentBuilder.<AmpereCommandSource, String>argument("command", StringArgumentType.word())
            .suggests((ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                for (Command command : AmpereCommands.all()) {
                    if (command.name().toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(command.name());
                    for (String alias : command.aliases()) {
                        if (alias != null && alias.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(alias);
                    }
                }
                return builder.buildFuture();
            })
            .executes(ctx -> {
                String name = StringArgumentType.getString(ctx, "command");
                Command cmd = AmpereCommands.find(name);
                if (cmd == null) { AmpereMessaging.sendPrefixed("§cNo such command: §f" + name); return SUCCESS; }
                String prefix = AmpereCommands.effectivePrefix();
                AmpereMessaging.sendPrefixed("§b" + prefix + cmd.name() + " §7" + cmd.description());
                if (cmd.aliases().length > 0) AmpereMessaging.sendPrefixed("§7aliases: §f" + String.join(", ", cmd.aliases()));
                return SUCCESS;
            }));
    }

    private static void listAll() {
        String prefix = AmpereCommands.effectivePrefix();
        for (Command c : AmpereCommands.all()) {
            AmpereMessaging.sendPrefixed("§b" + prefix + c.name() + "§7 - " + c.description());
        }
    }
}
