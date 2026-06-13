package ampere.mixin.security;

import ampere.security.AmpereProtectorModResolver;
import ampere.security.AmpereProtectorTracker;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;

@Mixin(targets = "net.fabricmc.fabric.impl.client.keymapping.KeyMappingRegistryImpl")
public class AmpereProtectorKeyMappingRegistryImplMixin {
    @Inject(method = "registerKeyMapping", at = @At("RETURN"))
    private static void Ampere$trackModKeyMapping(KeyMapping keyMapping, CallbackInfoReturnable<KeyMapping> cir) {
        LinkedHashSet<String> mods = AmpereProtectorModResolver.modsFromStacktrace();
        if (!mods.isEmpty()) AmpereProtectorTracker.addModKeybind(keyMapping.getName(), mods.getLast());
    }
}
