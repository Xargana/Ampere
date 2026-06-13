package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.commands.CommandSuggest;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GiveCommand extends Command {
    public GiveCommand() { super("give", "Give item to self (creative only)."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            AmpereMessaging.sendPrefixed("§eUsage: " + AmpereCommands.effectivePrefix() + "give <item> [count]");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AmpereCommandSource, String>argument("item", StringArgumentType.word())
            .suggests(CommandSuggest::itemIds)
            .executes(ctx -> give(StringArgumentType.getString(ctx, "item"), 1))
            .then(RequiredArgumentBuilder.<AmpereCommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 64))
                .suggests(CommandSuggest::counts)
                .executes(ctx -> give(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int give(String itemId, int count) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { AmpereMessaging.sendPrefixed("§cNot in a world."); return SUCCESS; }
        if (!mc.player.getAbilities().instabuild) { AmpereMessaging.sendPrefixed("§cCreative mode required."); return SUCCESS; }
        Identifier id = itemId.contains(":") ? Identifier.tryParse(itemId) : Identifier.tryParse("minecraft:" + itemId);
        if (id == null) { AmpereMessaging.sendPrefixed("§cInvalid item id: §f" + itemId); return SUCCESS; }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) { AmpereMessaging.sendPrefixed("§cUnknown item: §f" + id); return SUCCESS; }
        ItemStack stack = new ItemStack(item, count);
        int slot = mc.player.getInventory().getSelectedSlot();
        int containerSlot = 36 + slot;
        mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket((short) containerSlot, stack));
        mc.player.getInventory().setItem(slot, stack);
        AmpereMessaging.sendPrefixed("§aGave §f" + id + " x" + count);
        return SUCCESS;
    }
}
