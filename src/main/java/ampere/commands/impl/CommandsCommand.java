package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class CommandsCommand extends Command {
    public CommandsCommand() { super("commands", "List all available commands.", "cmds"); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            String prefix = AmpereCommands.effectivePrefix();
            AmpereMessaging.sendPrefixed("§e" + AmpereCommands.all().size() + " commands (prefix §f" + prefix + "§e):");
            StringBuilder line = new StringBuilder();
            for (Command c : AmpereCommands.all()) {
                if (line.length() > 0) line.append("§7, ");
                line.append("§f").append(c.name());
                if (line.length() > 200) { AmpereMessaging.sendPrefixed(line.toString()); line.setLength(0); }
            }
            if (line.length() > 0) AmpereMessaging.sendPrefixed(line.toString());
            AmpereMessaging.sendPrefixed("§7Type §f" + prefix + "help <command>§7 for details.");
            return SUCCESS;
        });
    }
}
