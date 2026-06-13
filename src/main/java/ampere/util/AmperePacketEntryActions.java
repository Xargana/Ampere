package ampere.util;

import ampere.gui.macro.editor.ActionEditorOverlay;
import ampere.util.macro.PayloadAction;
import ampere.util.macro.SendPacketAction;
import ampere.util.macro.WaitForPacketAction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public final class AmperePacketEntryActions {
    private AmperePacketEntryActions() {
    }

    public static boolean canQueue(AmperePacketLoggerOverlay.LogEntry entry) {
        return entry != null && "C2S".equalsIgnoreCase(entry.direction) && entry.packetRef != null;
    }

    public static boolean canDirectSend(AmperePacketLoggerOverlay.LogEntry entry) {
        return canQueue(entry);
    }

    public static boolean directSend(AmperePacketLoggerOverlay.LogEntry entry) {
        if (!canDirectSend(entry)) {
            AmpereMessaging.sendPrefixed("\u00a7cOnly C2S packets can be sent.");
            return false;
        }

        if (entry.packetRef instanceof ServerboundCustomPayloadPacket) {
            return directSendPayload(entry);
        }

        Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
        if (regenerated == null) {
            AmpereMessaging.sendPrefixed("\u00a7cCannot regenerate: " + entry.shortName);
            return false;
        }
        try {
            AmperePacketSender.send(regenerated);
            AmpereMessaging.sendPrefixed("Sent: " + entry.shortName);
            return true;
        } catch (Exception e) {
            AmpereMessaging.sendPrefixed("\u00a7cSend failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean directSendPayload(AmperePacketLoggerOverlay.LogEntry entry) {
        AmperePayloadSupport.PayloadSnapshot snapshot = AmperePayloadSupport.snapshotFromEntry(entry);
        if (snapshot == null || snapshot.channel() == null || snapshot.channel().isBlank()) {
            Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
            if (regenerated != null) {
                try {
                    AmperePacketSender.send(regenerated);
                    AmpereMessaging.sendPrefixed("Sent: " + entry.shortName);
                    return true;
                } catch (Exception e) {
                    AmpereMessaging.sendPrefixed("\u00a7cSend failed: " + e.getMessage());
                    return false;
                }
            }
            AmpereMessaging.sendPrefixed("\u00a7cCannot send: no payload data for " + entry.shortName);
            return false;
        }
        byte[] rawBytes = snapshot.rawBytes();
        if (!AmperePayloadSupport.sendPayload(snapshot.channel(), rawBytes, snapshot.protocolPhase())) {
            AmpereMessaging.sendPrefixed("\u00a7cFailed to send payload: " + entry.shortName);
            return false;
        }
        AmpereMessaging.sendPrefixed("Sent payload: " + snapshot.channel());
        return true;
    }

    public static boolean canAddSendAction(AmperePacketLoggerOverlay.LogEntry entry) {
        return canQueue(entry);
    }

    public static boolean canAddWaitAction(AmperePacketLoggerOverlay.LogEntry entry) {
        return entry != null && entry.shortName != null && !entry.shortName.isBlank() && entry.direction != null && !entry.direction.isBlank();
    }

    public static boolean canEditPayload(AmperePacketLoggerOverlay.LogEntry entry) {
        return entry != null && entry.isPayload && "C2S".equalsIgnoreCase(entry.direction);
    }

    public static boolean canAddPayloadAction(AmperePacketLoggerOverlay.LogEntry entry) {
        return canEditPayload(entry);
    }

    public static boolean queue(AmperePacketLoggerOverlay.LogEntry entry) {
        if (!canQueue(entry)) {
            AmpereMessaging.sendPrefixed("\u00a7cOnly C2S packets can be queued.");
            return false;
        }

        AmpereSharedState.get().enqueuePacket(entry.packetRef);
        AmpereMessaging.sendPrefixed("Queued: " + entry.shortName);
        return true;
    }

    public static boolean addSendActionToVisibleMacro(AmperePacketLoggerOverlay.LogEntry entry) {
        if (!canAddSendAction(entry)) {
            AmpereMessaging.sendPrefixed("\u00a7cOnly C2S packets can be added as send actions.");
            return false;
        }

        AmpereMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
        if (macroEditor == null) {
            AmpereMessaging.sendPrefixed("\u00a7cCannot open the macro editor.");
            return false;
        }

        Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
        if (regenerated == null) {
            AmpereMessaging.sendPrefixed("\u00a7cCannot regenerate: " + entry.shortName);
            return false;
        }

        SendPacketAction action = new SendPacketAction();
        action.waitForGuiBefore = false;
        action.waitForGuiAfter = false;
        action.guiName = "";
        action.packets.add(new AmpereSharedState.QueuedPacket(regenerated, 0));
        macroEditor.addAction(action);
        AmpereOverlayManager.get().bringToFront(macroEditor);
        AmpereMessaging.sendPrefixed("Added send action: " + entry.shortName);
        return true;
    }

    public static boolean addWaitActionToVisibleMacro(AmperePacketLoggerOverlay.LogEntry entry) {
        if (!canAddWaitAction(entry)) {
            AmpereMessaging.sendPrefixed("\u00a7cCannot add this packet as a wait condition.");
            return false;
        }

        AmpereMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
        if (macroEditor == null) {
            AmpereMessaging.sendPrefixed("\u00a7cCannot open the macro editor.");
            return false;
        }

        String target = WaitForPacketAction.withDirection(entry.direction, entry.shortName);
        if (target.isEmpty()) {
            AmpereMessaging.sendPrefixed("\u00a7cCannot add this packet as a wait condition.");
            return false;
        }

        WaitForPacketAction action = new WaitForPacketAction(target);
        action.packetNames.add(target);
        macroEditor.addAction(action);
        AmpereOverlayManager.get().bringToFront(macroEditor);
        AmpereMessaging.sendPrefixed("Added wait condition: " + WaitForPacketAction.getDisplayLabel(target));
        return true;
    }

    public static boolean openPayloadEditor(AmperePacketLoggerOverlay.LogEntry entry) {
        if (!canEditPayload(entry)) {
            AmpereMessaging.sendPrefixed("\u00a7cOnly captured C2S custom payload packets can be edited.");
            return false;
        }

        PayloadAction action = AmperePayloadSupport.seedActionFromEntry(entry);
        if (action == null) {
            AmpereMessaging.sendPrefixed("\u00a7cFailed to seed payload editor from packet.");
            return false;
        }

        ActionEditorOverlay.getSharedOverlay().openStandalonePayloadEditor(action);
        AmpereOverlayManager.get().bringToFront(ActionEditorOverlay.getSharedOverlay());
        return true;
    }

    public static boolean addPayloadActionToVisibleMacro(AmperePacketLoggerOverlay.LogEntry entry) {
        if (!canAddPayloadAction(entry)) {
            AmpereMessaging.sendPrefixed("\u00a7cOnly captured C2S custom payload packets can be added as payload actions.");
            return false;
        }

        AmpereMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
        if (macroEditor == null) {
            AmpereMessaging.sendPrefixed("\u00a7cCannot open the macro editor.");
            return false;
        }

        PayloadAction action = AmperePayloadSupport.seedActionFromEntry(entry);
        if (action == null) {
            AmpereMessaging.sendPrefixed("\u00a7cFailed to create payload action from packet.");
            return false;
        }

        macroEditor.addAction(action);
        AmpereOverlayManager.get().bringToFront(macroEditor);
        AmpereMessaging.sendPrefixed("Added payload action: " + entry.shortName);
        return true;
    }

    public static boolean hasVisibleMacroEditor() {
        return findVisibleMacroEditor() != null;
    }

    private static AmpereMacroEditorOverlay getOrOpenMacroEditor() {
        AmpereMacroEditorOverlay macroEditor = findVisibleMacroEditor();
        if (macroEditor != null) {
            AmpereOverlayManager.get().bringToFront(macroEditor);
            return macroEditor;
        }

            macroEditor = AmpereMacroEditorOverlay.getSharedOverlay();
        if (macroEditor == null) return null;

        AmpereMacro existingMacro = AmpereSharedState.get().getEditingMacro();
        macroEditor.open(existingMacro);
        AmpereOverlayManager.get().bringToFront(macroEditor);
        return macroEditor;
    }

    private static AmpereMacroEditorOverlay findVisibleMacroEditor() {
        for (IAmpereOverlay overlay : AmpereOverlayManager.get().getOverlays()) {
            if (overlay instanceof AmpereMacroEditorOverlay macroEditor && macroEditor.isVisible()) {
                return macroEditor;
            }
        }
        return null;
    }
}
