package ampere.mixin;

import ampere.modules.AmpereModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InBedChatScreen.class)
public abstract class AmpereSleepingChatScreenMixin extends Screen {
    protected AmpereSleepingChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void Ampere$init(CallbackInfo ci) {
        AmpereModule module = AmpereModule.get();
        if (module == null || !module.isActive()) return;

        this.addRenderableWidget(Button.builder(
            Component.literal("Client wake up"),
            button -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.stopSleepInBed(false, true);
                    mc.setScreen(null);
                }
            }
        ).bounds(5, 5, 140, 20).build());
    }
}
