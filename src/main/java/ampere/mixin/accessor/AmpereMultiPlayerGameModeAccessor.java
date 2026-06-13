package ampere.mixin.accessor;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface AmpereMultiPlayerGameModeAccessor {
    @Accessor("destroyProgress")
    float Ampere$getDestroyProgress();

    @Accessor("destroyProgress")
    void Ampere$setDestroyProgress(float progress);

    @Accessor("destroyDelay")
    void Ampere$setDestroyDelay(int delay);

    @Accessor("isDestroying")
    boolean Ampere$isDestroying();

    @Accessor("destroyBlockPos")
    BlockPos Ampere$getDestroyBlockPos();

    @Invoker("startPrediction")
    void Ampere$startPrediction(ClientLevel level, PredictiveAction action);
}
