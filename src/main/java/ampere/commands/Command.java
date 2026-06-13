package ampere.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

public abstract class Command {
    public static final int SUCCESS = 1;

    private final String name;
    private final String description;
    private final String[] aliases;

    protected Command(String name, String description, String... aliases) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.aliases = aliases == null ? new String[0] : aliases.clone();
    }

    public final String name() { return name; }
    public final String description() { return description; }
    public final String[] aliases() { return aliases.clone(); }

    public abstract void build(LiteralArgumentBuilder<AmpereCommandSource> builder);

    public int run(CommandContext<AmpereCommandSource> ctx) {
        ampere.util.AmpereMessaging.sendPrefixed(
            "§eUsage: " + AmpereCommands.effectivePrefix() + name + (aliases.length > 0 ? " (alias: " + aliases[0] + ")" : ""));
        if (!description.isEmpty()) {
            ampere.util.AmpereMessaging.sendPrefixed("§7" + description);
        }
        return SUCCESS;
    }
}
