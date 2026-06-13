package ampere.mixin;

import ampere.modules.NameCensorModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public class AmpereGuiNameCensorMixin {
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component Ampere$censorTitle(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component Ampere$censorSubtitle(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component Ampere$censorOverlayMessage(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }
}
