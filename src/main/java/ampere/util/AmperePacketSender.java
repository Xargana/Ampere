package ampere.util;

import ampere.security.AmpereProtector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

public class AmperePacketSender {

    public static void send(Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        AmpereProtector.markUserBypass(packet);
        mc.getConnection().send(packet);
    }

    public static void sendPacketDirect(Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        AmpereProtector.markUserBypass(packet);
        mc.getConnection().send(packet);
    }

}
