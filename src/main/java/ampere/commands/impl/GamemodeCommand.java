package ampere.commands.impl;

import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.commands.Command;
import ampere.commands.CommandSuggest;
import ampere.util.AmpereMessaging;
import ampere.util.AmpereFakeGamemode;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.world.level.GameType;

public class GamemodeCommand extends Command {
    public GamemodeCommand() {
        super("gamemode", "Set fake client-side game mode.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            AmpereMessaging.sendPrefixed("§eUsage: " + AmpereCommands.effectivePrefix() + "gamemode <survival|creative|adventure|spectator|reset>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AmpereCommandSource, String>argument("mode", StringArgumentType.word())
            .suggests(CommandSuggest::gamemodes)
            .executes(ctx -> {
                String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(java.util.Locale.ROOT);
                AmpereFakeGamemode.Result result;
                if ("reset".equals(mode) || "r".equals(mode)) {
                    result = AmpereFakeGamemode.reset();
                } else {
                    GameType resolved = AmpereFakeGamemode.parseMode(mode);
                    if (resolved == null) {
                        AmpereMessaging.sendPrefixed("§cUnknown mode: §f" + mode);
                        return SUCCESS;
                    }
                    result = AmpereFakeGamemode.apply(resolved);
                }
                AmpereMessaging.sendPrefixed((result.success() ? "§a" : "§c") + result.message());
                return SUCCESS;
            }));
    }
}
