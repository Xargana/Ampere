package ampere.mixin;

import ampere.util.AmpereSharedState;
import ampere.util.AmpereFakeGamemode;
import ampere.modules.PackModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class AmpereMultiPlayerGameModeMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void Ampere$skipTickWithoutPlayer(CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) ci.cancel();
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void Ampere$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (PackModuleRegistry.onStartDestroyBlock(pos, direction)) cir.setReturnValue(true);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void Ampere$routeBlockCapture(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (hit == null || !AmpereSharedState.get().hasBlockCaptureCallback()) return;
        if (AmpereSharedState.get().consumeBlockCaptureCallback(hit.getBlockPos(), hit.getDirection())) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    @Inject(
        method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getId()I", ordinal = 0)
    )
    private void Ampere$onBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        PackModuleRegistry.onBlockBreakingProgress(pos, direction);
    }

    @Inject(method = "setLocalMode(Lnet/minecraft/world/level/GameType;)V", at = @At("RETURN"))
    private void Ampere$trackServerMode(GameType mode, CallbackInfo ci) {
        AmpereFakeGamemode.onVanillaLocalMode(mode);
    }

    @Inject(method = "setLocalMode(Lnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;)V", at = @At("RETURN"))
    private void Ampere$trackServerMode(GameType mode, @Nullable GameType previousMode, CallbackInfo ci) {
        AmpereFakeGamemode.onVanillaLocalMode(mode);
    }
}
