package ampere.mixin;

import ampere.modules.PackHideState;
import ampere.util.AmpereVanillaSplash;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SplashManager.class, priority = 2000)
public abstract class AmpereSplashManagerMixin {
    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
    private void Ampere$prepareOnlyVanillaSplashes(ResourceManager manager, ProfilerFiller profiler, CallbackInfoReturnable<List<Component>> cir) {
        if (!PackHideState.isActive()) return;
        cir.setReturnValue(AmpereVanillaSplash.components(Minecraft.getInstance()));
    }

    @Inject(method = "getSplash", at = @At("HEAD"), cancellable = true)
    private void Ampere$getOnlyVanillaSplash(CallbackInfoReturnable<SplashRenderer> cir) {
        if (!PackHideState.isActive()) return;
        cir.setReturnValue(AmpereVanillaSplash.pick(Minecraft.getInstance()));
    }
}
