package ampere.util.macro;

import ampere.AmpereClientAddon;
import ampere.util.AmpereClipboardHelper;
import ampere.util.PacketRegenerator;
import ampere.util.AmpereMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;

public class PacketAction implements MacroAction {
    public String packetData = "";
    public boolean regenerate = true;
    public String description = "Packet";

    public PacketAction() {}

    public PacketAction(String packetData, boolean regenerate, String description) {
        this.packetData = packetData;
        this.regenerate = regenerate;
        this.description = description;
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc.getConnection() == null) {
            AmpereMessaging.sendPrefixed("§cNo network connection!");
            return;
        }

        if (packetData.isEmpty()) {
            AmpereMessaging.sendPrefixed("§cPacket data is empty!");
            return;
        }

        try {
            Packet<?> packet = AmpereClipboardHelper.deserializePacketFromBase64(packetData);
            if (packet == null) {
                AmpereMessaging.sendPrefixed("§cFailed to deserialize packet! Invalid data.");
                return;
            }

            if (regenerate) {
                Packet<?> regenerated = PacketRegenerator.regenerate(packet);
                if (regenerated == null) {
                    AmpereMessaging.sendPrefixed("§cFailed to regenerate packet: " + packet.getClass().getSimpleName());
                    return;
                }
                packet = regenerated;
            }

            mc.getConnection().send(packet);

        } catch (Exception e) {
            AmpereMessaging.sendPrefixed("§cPacket send error: " + e.getMessage());
            AmpereClientAddon.LOG.error("[MacroExecutor] Packet action failed", e);
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("packetData", packetData);
        tag.putBoolean("regenerate", regenerate);
        tag.putString("description", description);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("packetData")) packetData = tag.getStringOr("packetData", "");
        if (tag.contains("regenerate")) regenerate = tag.getBooleanOr("regenerate", true);
        if (tag.contains("description")) description = tag.getStringOr("description", "");
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.PACKET;
    }

    @Override
    public String getDisplayName() {
        return description.isEmpty() ? "Unknown Packet" : description;
    }

    @Override
    public String getIcon() {
        return "Pkt";
    }
}
