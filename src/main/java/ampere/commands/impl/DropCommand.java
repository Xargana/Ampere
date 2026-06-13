package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class DropCommand extends Command {
    public DropCommand() { super("drop", "Drop hand|hotbar|all from inventory."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> { dropHand(); return SUCCESS; });
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("hand").executes(ctx -> { dropHand(); return SUCCESS; }));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("hotbar").executes(ctx -> { dropHotbar(); return SUCCESS; }));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("all").executes(ctx -> { dropAll(); return SUCCESS; }));
    }

    private static LocalPlayer player() { return Minecraft.getInstance().player; }

    private static void dropHand() {
        LocalPlayer p = player();
        if (p == null) { AmpereMessaging.sendPrefixed("§cNot in a world."); return; }
        p.drop(true);
    }

    private static void dropHotbar() {
        LocalPlayer p = player();
        if (p == null) { AmpereMessaging.sendPrefixed("§cNot in a world."); return; }
        int original = p.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            p.getInventory().setSelectedSlot(slot);
            p.drop(true);
        }
        p.getInventory().setSelectedSlot(original);
    }

    private static void dropAll() {
        LocalPlayer p = player();
        if (p == null) { AmpereMessaging.sendPrefixed("§cNot in a world."); return; }

        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        int original = p.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            p.getInventory().setSelectedSlot(slot);
            conn.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS, BlockPos.ZERO, Direction.DOWN));
        }
        p.getInventory().setSelectedSlot(original);
        AmpereMessaging.sendPrefixed("§eDropped hotbar. (Main inventory drop requires open inventory — use macro DropAction for that.)");
    }
}
