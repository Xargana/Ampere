package ampere.commands.impl;

import ampere.commands.Command;
import ampere.commands.AmpereCommandSource;
import ampere.gui.screen.AmpereOverlayHostScreen;
import ampere.modules.AmpereModule;
import ampere.util.AmpereMessaging;
import ampere.util.AmpereOverlayManager;
import ampere.util.AmpereServerInfoOverlay;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

public class ServerCommand extends Command {
    public ServerCommand() { super("server", "Open the server info panel, or the plugin scanner."); }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> { openInfo(); return SUCCESS; });
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("info").executes(ctx -> { openInfo(); return SUCCESS; }));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("plugins").executes(ctx -> { openPlugins(); return SUCCESS; }));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("tps").executes(ctx -> { tps(); return SUCCESS; }));
    }

    static void openInfo() {
        openOverlay(false);
    }

    static void openPlugins() {
        openOverlay(true);
    }

    private static void openOverlay(boolean pluginsTab) {
        AmpereServerInfoOverlay overlay = AmpereModule.get().getServerDataOverlay();
        if (overlay == null) { AmpereMessaging.sendPrefixed("§cServer overlay unavailable."); return; }
        AmpereOverlayManager.get().register(overlay);
        if (pluginsTab) overlay.openPluginsTab();
        else overlay.openInfoTab();

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.screen == null) {
                    mc.setScreen(new AmpereOverlayHostScreen(overlay));
                }
            });
        }
    }

    private static void tps() {
        double tps = ampere.util.macro.ServerTickTracker.getEstimatedTps();
        AmpereMessaging.sendPrefixed(String.format("§eTPS: §f%.2f §7(estimated)", tps));
    }
}
