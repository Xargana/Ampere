package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.util.AmpereMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

public class DismountCommand extends Command {
    public DismountCommand() { super("dismount", "Force-dismount from any vehicle."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null) { AmpereMessaging.sendPrefixed("§cNot in a world."); return SUCCESS; }
            if (mc.player.getVehicle() == null) { AmpereMessaging.sendPrefixed("§eNot riding anything."); return SUCCESS; }
            try {
                mc.player.removeVehicle();
                AmpereMessaging.sendPrefixed("§aDismounted.");
            } catch (Throwable t) {
                AmpereMessaging.sendPrefixed("§cDismount failed: " + t.getMessage());
            }
            return SUCCESS;
        });
    }
}
