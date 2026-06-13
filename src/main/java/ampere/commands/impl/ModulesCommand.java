package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.modules.PackModuleRegistry;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class ModulesCommand extends Command {
    public ModulesCommand() { super("modules", "List installed modules.", "features"); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            java.util.List<String> names = PackModuleRegistry.names();
            AmpereMessaging.sendPrefixed("§e" + names.size() + " modules:");
            StringBuilder line = new StringBuilder();
            for (String n : names) {
                if (line.length() > 0) line.append("§7, ");
                line.append("§f").append(n);
                if (line.length() > 200) { AmpereMessaging.sendPrefixed(line.toString()); line.setLength(0); }
            }
            if (line.length() > 0) AmpereMessaging.sendPrefixed(line.toString());
            return SUCCESS;
        });
    }
}
