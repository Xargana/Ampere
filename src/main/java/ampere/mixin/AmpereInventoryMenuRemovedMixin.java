package ampere.mixin;

import ampere.modules.AmpereModule;
import ampere.util.AmpereSharedState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class AmpereInventoryMenuRemovedMixin {

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void Ampere$skipDrainWhenXCarryForced(Player player, CallbackInfo ci) {
        if (!(player instanceof LocalPlayer)) return;
        AmpereSharedState shared = AmpereSharedState.get();
        AmpereModule module = AmpereModule.get();
        boolean passive = module != null && module.isXCarryEnabled();

        if (!passive && !shared.isXCarryForced()) return;

        InventoryMenu self = (InventoryMenu) (Object) this;
        if (player.inventoryMenu != self) return;

        ci.cancel();
    }
}
