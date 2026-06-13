package ampere.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface AmpereLocalPlayerAccessor {
    @Accessor("positionReminder")
    void Ampere$setPositionReminder(int ticks);
}
