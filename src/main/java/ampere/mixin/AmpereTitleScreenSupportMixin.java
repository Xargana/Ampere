package ampere.mixin;

import ampere.modules.PackHideState;
import ampere.util.AmpereLinks;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class AmpereTitleScreenSupportMixin extends Screen {
    protected AmpereTitleScreenSupportMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void Ampere$addSupportButtons(CallbackInfo ci) {
        if (PackHideState.isActive()) return;

        Component cardDonateLabel = Component.literal("Card/PayPal");
        this.addRenderableWidget(Button.builder(cardDonateLabel, b -> AmpereLinks.open(AmpereLinks.KOFI))
            .bounds(4, 4, supportWidth(cardDonateLabel), 20).build());

        Component cryptoDonateLabel = Component.literal("Crypto");
        this.addRenderableWidget(Button.builder(cryptoDonateLabel, b -> AmpereLinks.open(AmpereLinks.CRYPTO_DONATE))
            .bounds(4, 28, supportWidth(cryptoDonateLabel), 20).build());

        Component incDiscordLabel = Component.literal("Ampere INC");
        this.addRenderableWidget(Button.builder(incDiscordLabel, b -> AmpereLinks.open(AmpereLinks.Ampere_INC_DISCORD))
            .bounds(4, 52, supportWidth(incDiscordLabel), 20).build());

        Component clientDiscordLabel = Component.literal("Ampere Client");
        this.addRenderableWidget(Button.builder(clientDiscordLabel, b -> AmpereLinks.open(AmpereLinks.DISCORD))
            .bounds(4, 76, supportWidth(clientDiscordLabel), 20).build());
    }

    private int supportWidth(Component label) {
        return Math.max(78, Math.min(this.width - 8, this.font.width(label) + 20));
    }
}
