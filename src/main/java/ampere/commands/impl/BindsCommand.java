package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.commands.args.KeyArgumentType;
import ampere.modules.AmpereModule;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.Map;

public class BindsCommand extends Command {
    public BindsCommand() { super("binds", "List command keybinds (set with `bind`)."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            Map<Integer, String> binds = AmpereModule.get().getCommandBinds();
            if (binds.isEmpty()) { AmpereMessaging.sendPrefixed("§eNo command keybinds set."); return SUCCESS; }
            AmpereMessaging.sendPrefixed("§e" + binds.size() + " command keybinds:");
            for (Map.Entry<Integer, String> e : binds.entrySet()) {
                AmpereMessaging.sendPrefixed("§b" + KeyArgumentType.keyName(e.getKey()) + "§7 → §f" + e.getValue());
            }
            return SUCCESS;
        });
    }
}
