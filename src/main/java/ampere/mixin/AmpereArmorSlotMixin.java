package ampere.mixin;

import ampere.modules.AmpereModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class AmpereArmorSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void Ampere$mayPlaceForXCarry(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (Ampere$armorAllowed()) cir.setReturnValue(true);
    }

    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void Ampere$maxStackSizeForXCarry(CallbackInfoReturnable<Integer> cir) {
        if (Ampere$armorAllowed()) cir.setReturnValue(64);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean Ampere$armorAllowed() {
        AmpereModule mod = AmpereModule.get();
        boolean modAllow = mod != null && mod.isXCarryUseArmor();
        boolean bypass = ampere.util.AmpereSharedState.get().isXCarryArmorBypass();
        return modAllow || bypass;
    }
}
