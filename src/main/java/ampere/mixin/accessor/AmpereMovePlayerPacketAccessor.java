package ampere.mixin.accessor;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface AmpereMovePlayerPacketAccessor {
    @Mutable
    @Accessor("y")
    void Ampere$setY(double y);

    @Mutable
    @Accessor("onGround")
    void Ampere$setOnGround(boolean onGround);
}
