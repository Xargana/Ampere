package autismclient.mixin;

import autismclient.commands.AutismCommands;
import autismclient.modules.AutismModule;
import autismclient.modules.InventoryTweaksModule;
import autismclient.modules.PackModuleRegistry;
import autismclient.util.AutismSharedState;
import autismclient.util.macro.MacroConditionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class AutismClientPlayNetworkHandlerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void autism$dispatchAutismCommand(String message, CallbackInfo ci) {
        if (!AutismCommands.isAutismCommandMessage(message)) return;
        if (AutismCommands.isBlockedPanicCommandMessage(message)) {
            ci.cancel();
            return;
        }
        String body = AutismCommands.commandBody(message);
        if (body.isBlank()) {
            ci.cancel();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        autism$rememberChatCommand(mc, message);
        AutismCommands.dispatch(body);
        ci.cancel();
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void yang$onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
        boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
        if (!macroWaits && !inventoryTweaks) return;
        if (macroWaits) MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
        if (inventoryTweaks) InventoryTweaksModule.onContainerSynced(packet.containerId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void yang$onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
        boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
        if (!macroWaits && !inventoryTweaks) return;
        if (macroWaits) MacroConditionRegistry.onSlotUpdate(packet.getSlot());
        if (inventoryTweaks) InventoryTweaksModule.onContainerSynced(packet.getContainerId());
    }

    @Inject(method = "handleSoundEvent", at = @At("RETURN"))
    private void yang$onPlaySound(ClientboundSoundPacket packet, CallbackInfo ci) {
        boolean macroWaits = MacroConditionRegistry.hasPendingSoundConditions();
        boolean moduleHooks = PackModuleRegistry.hasSoundHooks();
        if (!macroWaits && !moduleHooks) return;
        if (macroWaits) {
            try {
                String soundId = packet.getSound().value().location().toString();
                MacroConditionRegistry.onSoundPacket(soundId, packet.getX(), packet.getY(), packet.getZ());
            } catch (Exception ignored) {
            }
        }
        if (moduleHooks) PackModuleRegistry.onSoundPacket(packet);
    }

    @Inject(method = "handleSetTime", at = @At("RETURN"))
    private void yang$onWorldTimeUpdate(ClientboundSetTimePacket packet, CallbackInfo ci) {
        if (!autism$packetHooksActive()) return;
        AutismSharedState.get().onServerTimeSyncReceived();
    }

    @Unique
    private boolean autism$packetHooksActive() {
        AutismModule module = AutismModule.get();
        return module != null && module.arePacketHooksActive();
    }

    @Unique
    private static void autism$rememberChatCommand(Minecraft mc, String message) {
        if (mc == null || message == null || message.isBlank()) return;
        try {
            mc.commandHistory().addCommand(message);
        } catch (Throwable ignored) {
        }
        try {
            if (mc.gui == null || mc.gui.getChat() == null) return;
            net.minecraft.util.ArrayListDeque<String> recent = mc.gui.getChat().getRecentChat();
            if (recent == null || recent.isEmpty() || !message.equals(recent.getLast())) {
                mc.gui.getChat().addRecentChat(message);
            }
        } catch (Throwable ignored) {
        }
    }
}
