package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class PluginsCommand extends Command {
    public PluginsCommand() { super("plugins", "Open the plugin scanner (alias of `server plugins`)."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> { ServerCommand.openPlugins(); return SUCCESS; });
    }
}
