package autismclient.api.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface MacroExecutionContext {

    Minecraft mc();

    void runOnClientThread(Runnable r);

    <T> T callOnClientThread(Supplier<T> s);

    void waitTicks(int ticks);

    void awaitCondition(CompletableFuture<Void> future);

    void awaitCondition(AddonCondition condition);

    boolean isActive();

    void setStatus(String status);

    void sendPacket(Packet<?> packet);
}
