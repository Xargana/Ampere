package ampere.commands.impl;

import ampere.commands.AmpereCommandSource;
import ampere.commands.Command;
import ampere.commands.CommandSuggest;
import ampere.modules.AmpereModule;
import ampere.util.AmpereMessaging;
import ampere.util.AmpereCompatManager;
import ampere.util.AmpereNotifications;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class PrefixCommand extends Command {
    public PrefixCommand() {
        super("prefix", "Change the Ampere command prefix.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            AmpereMessaging.sendPrefixed("Current prefix: " + AmpereCompatManager.effectiveCommandPrefix());
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("reset").executes(ctx -> {
            String prefix = AmpereCompatManager.environmentDefaultCommandPrefix();
            AmpereModule.get().setCommandPrefix(prefix);
            AmpereMessaging.sendPrefixed("Prefix reset to " + prefix);
            return SUCCESS;
        }));
        root.then(RequiredArgumentBuilder.<AmpereCommandSource, String>argument("new", StringArgumentType.word())
            .suggests(CommandSuggest::prefixes)
            .executes(ctx -> {
                String requested = StringArgumentType.getString(ctx, "new");
                if (!AmpereCompatManager.COMMAND_PREFIX_CHOICES.contains(requested)) {
                    AmpereMessaging.sendPrefixed("Prefix must be one of: . % - _ * # @ & =");
                    return SUCCESS;
                }
                String selected = AmpereCompatManager.normalizeStoredCommandPrefix(requested);
                AmpereModule.get().setCommandPrefix(selected);
                if (!selected.equals(requested)) {
                    AmpereNotifications.warning("Meteor uses '.'. Prefix kept as '%'.");
                }
                AmpereMessaging.sendPrefixed("Prefix set to " + selected);
                return SUCCESS;
            }));
    }
}
