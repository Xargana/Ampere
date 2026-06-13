package ampere.mixin;

import ampere.modules.AmpereModule;
import ampere.security.AmpereProtector;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class AmpereBrandRetrieverMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void Ampere$spoofClientBrand(CallbackInfoReturnable<String> cir) {

        if (AmpereProtector.isFullExternalProtectorPresent()) return;

        AmpereModule module = AmpereModule.get();
        if (module != null && module.isSpoofClientVanilla()) {
            cir.setReturnValue("vanilla");
            return;
        }

        if (AmpereProtector.shouldSpoofBrand()) {
            cir.setReturnValue(AmpereProtector.getEffectiveBrand());
        }
    }
}
