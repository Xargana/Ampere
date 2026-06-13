package ampere.mixin;

import ampere.modules.NameCensorModule;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Objective.class)
public class AmpereObjectiveMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void Ampere$censorObjectiveName(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NameCensorModule.censorServerComponent(cir.getReturnValue()));
    }
}
