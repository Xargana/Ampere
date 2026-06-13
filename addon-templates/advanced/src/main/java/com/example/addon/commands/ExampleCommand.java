package com.example.addon.commands;

import ampere.commands.AmpereCommandSource;
import ampere.commands.Command;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

// A Brigadier command. Users run it with the command prefix, e.g. .example or .example hi.
public final class ExampleCommand extends Command {
    public ExampleCommand() {
        super("example", "Example addon command.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            AmpereMessaging.sendPrefixed("\u00a7dExample addon command works!");
            return SUCCESS;
        });

        root.then(RequiredArgumentBuilder.<AmpereCommandSource, String>argument("text", StringArgumentType.greedyString())
            .executes(ctx -> {
                AmpereMessaging.sendPrefixed("\u00a7dYou said: \u00a7f" + StringArgumentType.getString(ctx, "text"));
                return SUCCESS;
            }));
    }
}
