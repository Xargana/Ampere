package ampere.mixin;

import ampere.commands.AmpereCommands;
import ampere.modules.AmpereModule;
import ampere.modules.PackHideState;
import ampere.util.AmpereMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class AmpereChatScreenMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void yang$onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (message == null) return;
        String trimmed = message.trim();
        if (trimmed.isEmpty()) return;
        if (AmpereCommands.isBlockedPanicCommandMessage(trimmed)) {
            ci.cancel();
            return;
        }

        if ("^toggleAmpere".equalsIgnoreCase(trimmed)) {
            if (PackHideState.isActive()) {
                ci.cancel();
                return;
            }
            AmpereModule module = AmpereModule.get();
            module.toggle();
            Minecraft mc = Minecraft.getInstance();
            AmpereMessaging.sendPrefixed("Ampere is now " + (module.isActive() ? "enabled" : "disabled") + ".");
            mc.commandHistory().addCommand(message);
            mc.setScreen(null);
            ci.cancel();
        }
    }
}
