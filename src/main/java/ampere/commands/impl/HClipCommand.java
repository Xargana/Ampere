package ampere.commands.impl;

import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.commands.Command;
import ampere.commands.CommandSuggest;
import ampere.util.AmpereMessaging;
import ampere.util.macro.HClipAction;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public class HClipCommand extends Command {
    public HClipCommand() {
        super("hclip", "Lets you clip through blocks horizontally (forward) with movement packets.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            String prefix = AmpereCommands.effectivePrefix();
            AmpereMessaging.sendPrefixed("\u00a7eUsage: \u00a7f" + prefix + "hclip <blocks>");
            AmpereMessaging.sendPrefixed("\u00a77Modes: \u00a7fdefault, forward, back, padding, single, custom (paper alias still works)");
            AmpereMessaging.sendPrefixed("\u00a77Examples: \u00a7f" + prefix + "hclip forward \u00a77or \u00a7f" + prefix + "hclip custom -25 10 20 true true true");
            return SUCCESS;
        });

        root.then(blocksArgument("blocks", options -> options));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("forward")
            .executes(ctx -> {
                HClipAction.Options options = HClipAction.Options.defaults(0.0);
                options.mode = HClipAction.Mode.FORWARD;
                run(options);
                return SUCCESS;
            }));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("back")
            .executes(ctx -> {
                HClipAction.Options options = HClipAction.Options.defaults(0.0);
                options.mode = HClipAction.Mode.BACK;
                run(options);
                return SUCCESS;
            }));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("default")
            .then(blocksArgument("blocks", options -> options)));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("paper")
            .then(blocksArgument("blocks", options -> options)));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("padding")
            .then(blocksArgument("blocks", options -> options)));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("single")
            .then(blocksArgument("blocks", HClipAction.Options::singlePacket)));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("normal")
            .then(blocksArgument("blocks", HClipAction.Options::singlePacket)));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("custom")
            .then(RequiredArgumentBuilder.<AmpereCommandSource, Double>argument("blocks", DoubleArgumentType.doubleArg())
                .suggests(CommandSuggest::offsets)
                .then(RequiredArgumentBuilder.<AmpereCommandSource, Integer>argument("segment", IntegerArgumentType.integer(1, 50))
                    .suggests(CommandSuggest::vclipSegments)
                    .executes(ctx -> {
                        HClipAction.Options options = HClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
                        options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
                        run(options);
                        return SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<AmpereCommandSource, Integer>argument("maxPackets", IntegerArgumentType.integer(1, 100))
                        .suggests(CommandSuggest::vclipPacketLimits)
                        .executes(ctx -> {
                            HClipAction.Options options = HClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
                            options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
                            options.maxPackets = IntegerArgumentType.getInteger(ctx, "maxPackets");
                            run(options);
                            return SUCCESS;
                        })
                        .then(RequiredArgumentBuilder.<AmpereCommandSource, Boolean>argument("updateLocal", BoolArgumentType.bool())
                            .executes(ctx -> {
                                HClipAction.Options options = customOptions(ctx);
                                options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                run(options);
                                return SUCCESS;
                            })
                            .then(RequiredArgumentBuilder.<AmpereCommandSource, Boolean>argument("vehicle", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    HClipAction.Options options = customOptions(ctx);
                                    options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                    options.tryVehicleFirst = BoolArgumentType.getBool(ctx, "vehicle");
                                    run(options);
                                    return SUCCESS;
                                })
                                .then(RequiredArgumentBuilder.<AmpereCommandSource, Boolean>argument("forceGround", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        HClipAction.Options options = customOptions(ctx);
                                        options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                        options.tryVehicleFirst = BoolArgumentType.getBool(ctx, "vehicle");
                                        options.forceGrounded = BoolArgumentType.getBool(ctx, "forceGround");
                                        run(options);
                                        return SUCCESS;
                                    }))))))));
    }

    private interface OptionsCustomizer {
        HClipAction.Options apply(HClipAction.Options options);
    }

    private static RequiredArgumentBuilder<AmpereCommandSource, Double> blocksArgument(String name, OptionsCustomizer customizer) {
        return RequiredArgumentBuilder.<AmpereCommandSource, Double>argument(name, DoubleArgumentType.doubleArg())
            .suggests(CommandSuggest::offsets)
            .executes(ctx -> {
                HClipAction.Options options = HClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, name));
                run(customizer.apply(options));
                return SUCCESS;
            });
    }

    private static HClipAction.Options customOptions(com.mojang.brigadier.context.CommandContext<AmpereCommandSource> ctx) {
        HClipAction.Options options = HClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
        options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
        options.maxPackets = IntegerArgumentType.getInteger(ctx, "maxPackets");
        options.maxRoutePackets = options.maxPackets;
        return options;
    }

    public static void hclip(double blocks) {
        run(HClipAction.Options.defaults(blocks));
    }

    private static void run(HClipAction.Options options) {
        options.showMessage = true;
        HClipAction.perform(Minecraft.getInstance(), options);
    }
}
