package ampere.commands.impl;

import ampere.commands.AmpereCommandSource;
import ampere.commands.Command;
import ampere.gui.screen.AmpereOverlayHostScreen;
import ampere.util.AmpereMessaging;
import ampere.util.AmpereItemCommandSerializer;
import ampere.util.AmpereItemNbtInspectOverlay;
import ampere.util.AmpereNotifications;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class NbtCommand extends Command {
    public NbtCommand() {
        super("nbt", "Inspect or copy the held item's components (NBT).");
    }

    @Override
    public void build(LiteralArgumentBuilder<AmpereCommandSource> root) {
        root.executes(ctx -> {
            inspect();
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("get").executes(ctx -> {
            inspect();
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<AmpereCommandSource>literal("copy").executes(ctx -> {
            copy();
            return SUCCESS;
        }));
    }

    private static ItemStack held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getMainHandItem();
        return !main.isEmpty() ? main : mc.player.getOffhandItem();
    }

    private static void inspect() {
        ItemStack stack = held();
        if (stack.isEmpty()) {
            AmpereMessaging.sendPrefixed("\u00a7cHold an item in either hand first.");
            return;
        }
        if (!AmpereItemNbtInspectOverlay.openGlobal(stack)) {
            AmpereMessaging.sendPrefixed("\u00a7cCould not open the NBT inspector.");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            ampere.util.AmpereItemNbtInspectOverlay overlay =
                ampere.util.AmpereItemNbtInspectOverlay.getSharedOverlay(mc.font);
            mc.execute(() -> {

                if (mc.screen == null) {
                    mc.setScreen(new AmpereOverlayHostScreen(overlay));
                }
            });
        }
    }

    private static void copy() {
        ItemStack stack = held();
        if (stack.isEmpty()) {
            AmpereMessaging.sendPrefixed("\u00a7cHold an item in either hand first.");
            return;
        }
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(AmpereItemCommandSerializer.giveCommand(stack));
            AmpereNotifications.copied("Copied /give command.");
        } catch (Throwable t) {
            AmpereNotifications.error("Copy failed.");
        }
    }
}
