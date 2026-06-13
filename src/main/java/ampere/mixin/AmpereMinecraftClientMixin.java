package ampere.mixin;

import ampere.gui.AmpereLoadingOverlay;
import ampere.gui.macro.editor.ActionEditorOverlay;
import ampere.gui.screen.AmpereTitleScreen;
import ampere.modules.AmpereModule;
import ampere.modules.PackHideState;
import ampere.modules.PackModuleMovementUtil;
import ampere.modules.PackModuleRegistry;
import ampere.util.AmpereInputClicker;
import ampere.util.AmpereSharedState;
import ampere.util.AmpereWindowBranding;
import net.minecraft.util.ModCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(Minecraft.class)
public class AmpereMinecraftClientMixin {
    @Unique
    private static final String PACKUTIL_WINDOW_TITLE = AmpereWindowBranding.WINDOW_TITLE;

    @Unique
    private boolean Ampere$escapeWasDown;
    @Unique
    private boolean Ampere$inventoryWasDown;
    @Unique
    private boolean Ampere$replacingTitleScreen;

    @Redirect(
        method = "*",
        at = @At(value = "NEW", target = "net/minecraft/client/gui/screens/LoadingOverlay")
    )
    private static LoadingOverlay Ampere$replaceLoadingOverlay(
        Minecraft minecraft, ReloadInstance reload,
        Consumer<Optional<Throwable>> onFinish, boolean fadeIn
    ) {
        return new AmpereLoadingOverlay(minecraft, reload, onFinish, fadeIn);
    }

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void Ampere$createCustomWindowTitle(CallbackInfoReturnable<String> cir) {

        if (PackHideState.isActive()) return;
        cir.setReturnValue(PACKUTIL_WINDOW_TITLE);
    }

    @Inject(method = "checkModStatus", at = @At("HEAD"), cancellable = true)
    private static void Ampere$reportVanillaWhileHidden(CallbackInfoReturnable<ModCheck> cir) {

        if (PackHideState.isActive()) {
            cir.setReturnValue(new ModCheck(ModCheck.Confidence.PROBABLY_NOT, "Client jar signature and brand is untouched"));
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void Ampere$replacePauseScreen(Screen screen, CallbackInfo ci) {

        if (!PackHideState.isActive()) return;
        if (screen instanceof net.minecraft.client.gui.screens.PauseScreen ps && ps.showsPauseMenu()) {
            ((Minecraft) (Object) this).setScreen(new ampere.gui.screen.AmperePauseScreen());
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void Ampere$replaceTitleScreen(Screen screen, CallbackInfo ci) {

        if (Ampere$replacingTitleScreen || !(screen instanceof TitleScreen)) {
            return;
        }
        Minecraft client = (Minecraft) (Object) this;

        if (!PackHideState.isActive() && ampere.util.AmpereWelcomeGate.shouldShow(ampere.util.AmpereConfig.getGlobal())) {
            ampere.util.AmpereWelcomeGate.markShown(ampere.util.AmpereConfig.getGlobal());
            client.setScreen(new ampere.gui.screen.AmpereWelcomeScreen());
            ci.cancel();
            return;
        }

        if (PackHideState.isActive()) {
            Ampere$setMenuScreen(client, new ampere.gui.screen.AmperePanicTitleScreen());
            ci.cancel();
            return;
        }

        if (!ampere.util.AmpereMenuPrefs.customMainMenuEnabled()) return;

        Ampere$setMenuScreen(client, new AmpereTitleScreen());
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void Ampere$onTickHead(CallbackInfo ci) {
        AmpereWindowBranding.tick((Minecraft) (Object) this);
        AmpereSharedState.get().onClientTickStart();
        AmpereInputClicker.onClientTickStart();
        PackModuleMovementUtil.preMovementTick();
        if (ampere.util.AmpereContainerHold.hasExpiryWork()) ampere.util.AmpereContainerHold.tickExpiry();
        AmpereModule Ampere = AmpereModule.get();
        if (!PackHideState.isActive() && Ampere.hasCommandBinds()) Ampere$pollCommandBinds(Ampere);
    }

    @Unique
    private final java.util.Map<Integer, Boolean> Ampere$commandBindWasDown = new java.util.HashMap<>();

    @Unique
    private void Ampere$pollCommandBinds(AmpereModule module) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.getWindow() == null) return;

        if (client.screen instanceof net.minecraft.client.gui.screens.ChatScreen) return;
        if (client.screen instanceof net.minecraft.client.gui.screens.inventory.SignEditScreen) return;
        java.util.Map<Integer, String> binds = module.getCommandBinds();
        if (binds.isEmpty()) return;
        long handle = client.getWindow().handle();
        for (java.util.Map.Entry<Integer, String> entry : binds.entrySet()) {
            int key = entry.getKey();
            boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
            boolean wasDown = Ampere$commandBindWasDown.getOrDefault(key, false);
            Ampere$commandBindWasDown.put(key, down);
            if (down && !wasDown) {
                String cmd = entry.getValue();
                if (cmd != null && !cmd.isBlank()) {
                    ampere.commands.AmpereCommands.dispatch(cmd);
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void Ampere$repairStrayTitleScreen(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;

        if (Ampere$replacingTitleScreen) return;
        if (!(client.screen instanceof TitleScreen)) return;
        if (PackHideState.isActive()) {
            Ampere$setMenuScreen(client, new ampere.gui.screen.AmperePanicTitleScreen());
        } else if (ampere.util.AmpereMenuPrefs.customMainMenuEnabled()) {
            Ampere$setMenuScreen(client, new AmpereTitleScreen());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void Ampere$swapMenuOnPanicChange(CallbackInfo ci) {
        if (Ampere$replacingTitleScreen) return;
        Minecraft client = (Minecraft) (Object) this;

        if (PackHideState.isActive() && client.screen instanceof AmpereTitleScreen) {
            Ampere$setMenuScreen(client, new ampere.gui.screen.AmperePanicTitleScreen());
        } else if (!PackHideState.isActive()
                && client.screen instanceof ampere.gui.screen.AmperePanicTitleScreen
                && ampere.util.AmpereMenuPrefs.customMainMenuEnabled()) {
            Ampere$setMenuScreen(client, new AmpereTitleScreen());
        }
    }

    @Unique
    private void Ampere$setMenuScreen(Minecraft client, Screen menu) {
        Ampere$replacingTitleScreen = true;
        try {
            client.setScreen(menu);
        } finally {
            Ampere$replacingTitleScreen = false;
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void Ampere$onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft) (Object) this;

        if (AmpereSharedState.get().hasEntityCaptureCallback()) {
            cir.setReturnValue(false);
            return;
        }
        if (PackModuleRegistry.hasAttackUseHooks()) {
            for (ampere.modules.PackModule module : PackModuleRegistry.attackUseModulesForDispatch()) {
                if (module.shouldCancelAttack(client.hitResult)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
        if (AmpereSharedState.get().hasAttackCaptureCallback()) {
            AmpereSharedState.get().consumeAttackCaptureCallback();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void Ampere$cancelUseForModules(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;

        if (AmpereSharedState.get().hasEntityCaptureCallback()
                && !(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult)) {
            ci.cancel();
            return;
        }
        if (PackModuleRegistry.hasAttackUseHooks()) {
            for (ampere.modules.PackModule module : PackModuleRegistry.attackUseModulesForDispatch()) {
                if (module.shouldCancelUse(client.hitResult, net.minecraft.world.InteractionHand.MAIN_HAND)) {
                    ci.cancel();
                    return;
                }
            }
        }
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void Ampere$cancelCaptureOnEscape(CallbackInfo ci) {
        AmpereInputClicker.beforeHandleKeybinds();
        Minecraft client = (Minecraft) (Object) this;
        if (client.getWindow() == null) {
            AmpereInputClicker.afterHandleKeybinds();
            return;
        }

        boolean escapeDown = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        boolean justPressed = escapeDown && !Ampere$escapeWasDown;
        Ampere$escapeWasDown = escapeDown;
        boolean inventoryDown = client.options != null && client.options.keyInventory.isDown();
        boolean inventoryJustPressed = inventoryDown && !Ampere$inventoryWasDown;
        Ampere$inventoryWasDown = inventoryDown;

        if (justPressed || inventoryJustPressed) {
            if (AmpereSharedState.get().consumeCaptureCancelCallback()) {
                AmpereInputClicker.afterHandleKeybinds();
                ci.cancel();
                return;
            }

            ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
                AmpereInputClicker.afterHandleKeybinds();
                ci.cancel();
            }
        }
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void Ampere$releaseQueuedClicks(CallbackInfo ci) {
        AmpereInputClicker.afterHandleKeybinds();
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void Ampere$cancelLostFocusPause(boolean suppressPauseMenuIfWeReallyArePausing, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;

        if (AmpereSharedState.get().consumeCaptureCancelCallback()) {
            ci.cancel();
            return;
        }
        ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
        if (actionEditor != null && actionEditor.hasActiveCaptureSession()) {
            if (actionEditor.cancelCaptureIfActive()) {
                ci.cancel();
                return;
            }

            ci.cancel();
            return;
        }

        if (client.getWindow() != null && !client.getWindow().isFocused()) {
            AmpereModule module = AmpereModule.get();
            if (module != null && module.isActive() && module.isNoPauseOnLostFocus()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "getTickTargetMillis", at = @At("RETURN"), cancellable = true)
    private void Ampere$applySpeedTimer(float defaultTickTargetMillis, CallbackInfoReturnable<Float> cir) {

        if (((Minecraft) (Object) this).player == null) return;
        float multiplier = PackModuleMovementUtil.speedTimerMultiplier();
        if (multiplier != 1.0f) cir.setReturnValue(cir.getReturnValue() / multiplier);
    }
}
