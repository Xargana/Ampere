package ampere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ampere.gui.vanillaui.UiBounds;
import ampere.gui.vanillaui.UiContexts;
import ampere.gui.vanillaui.UiTextRenderer;
import ampere.gui.vanillaui.components.Banner;
import ampere.modules.AmpereModule;
import ampere.modules.PackModule;
import ampere.modules.PackHideState;
import ampere.modules.PackModuleRenderUtil;
import ampere.modules.PackModuleRegistry;
import ampere.modules.PackModuleScreenRenderer;
import ampere.util.AmpereHudManager;
import ampere.util.AmpereCaptureBannerSpec;
import ampere.util.AmpereMacroProgressRenderer;
import ampere.util.AmpereNotifications;
import ampere.util.AmpereOverlayManager;
import ampere.util.AmpereQueueRenderer;
import ampere.util.AmpereServerInfoOverlay;
import ampere.util.AmpereSharedState;
import ampere.util.AmpereUiScale;
import ampere.util.macro.MacroExecutor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;

@Mixin(Gui.class)
public abstract class AmpereInGameHudMixin {
    @Unique private static final Minecraft MC = Minecraft.getInstance();
    @Unique private static final int PACKUTIL_RIGHT_PANEL_W = 172;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void yang$renderAmpereQueue(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!isAmpereActive()) return;
        if (MacroExecutor.hasRenderWork()) MacroExecutor.onRender(1.0f);
        if (MC.options.hideGui) return;
        if (PackHideState.isActive()) return;

        AmpereSharedState shared = AmpereSharedState.get();
        boolean macroRunning = MacroExecutor.isVisibleRunning();
        boolean queueSending = shared.hasStaggeredPackets();
        boolean queueVisible = shared.shouldDelayGuiPackets()
            || shared.hasDelayedPackets()
            || queueSending;
        boolean captureActive = hasAnyCaptureSession(shared);
        PackModule hud = PackModuleRegistry.get("hud");
        boolean nativeHudVisible = AmpereHudManager.shouldRenderInGame(MC.screen, hud);
        boolean esp2dVisible = PackModuleRenderUtil.has2dEspWork();
        boolean mainHudVisible = nativeHudVisible || macroRunning || queueVisible || captureActive || esp2dVisible;

        AmpereServerInfoOverlay serverInfoOverlay = null;
        boolean serverProbeBannerVisible = false;
        AmpereOverlayManager overlayManager = null;
        boolean overlayVisible = false;
        if (MC.screen == null) {
            serverInfoOverlay = AmpereModule.get().getServerDataOverlayIfExists();
            serverProbeBannerVisible = serverInfoOverlay != null && serverInfoOverlay.shouldRenderBackgroundProbeBanner();
            overlayManager = AmpereOverlayManager.get();
            overlayVisible = overlayManager.hasRegisteredOverlays() && overlayManager.hasVisibleOverlay();
        }
        boolean notificationsVisible = AmpereNotifications.hasVisible();
        if (!mainHudVisible && !serverProbeBannerVisible && !overlayVisible && !notificationsVisible) return;

        Runnable renderHudElements = () -> {
            int screenWidth = AmpereUiScale.getVirtualScreenWidth();
            int x = Math.max(0, screenWidth - PACKUTIL_RIGHT_PANEL_W);
            int y = 0;
            AmpereCaptureBannerSpec captureBanner = captureActive ? captureBannerSpec(shared, context) : null;
            java.util.ArrayList<AmpereHudManager.ElementBounds> hudOccluders = new java.util.ArrayList<>(3);
            if (captureBanner != null) {
                hudOccluders.add(new AmpereHudManager.ElementBounds("capture_banner",
                    captureBanner.x(), captureBanner.y(), captureBanner.width(), captureBanner.height()));
            } else if (captureActive) {
                int fallbackW = Math.min(screenWidth - 16, 300);
                hudOccluders.add(new AmpereHudManager.ElementBounds("capture_banner",
                    Math.max(0, (screenWidth - fallbackW) / 2), 0, fallbackW, 56));
            }
            if (queueVisible) {
                int queueHeight = AmpereQueueRenderer.measureStacked(MC.font, PACKUTIL_RIGHT_PANEL_W, 8);
                if (queueHeight > 0) {
                    hudOccluders.add(new AmpereHudManager.ElementBounds("packet_queue", x, y, PACKUTIL_RIGHT_PANEL_W, queueHeight));
                    y += queueHeight;
                }
            }
            if (macroRunning) {
                int macroHeight = AmpereMacroProgressRenderer.measureStacked(MC.font, PACKUTIL_RIGHT_PANEL_W, 10);
                if (macroHeight > 0) hudOccluders.add(new AmpereHudManager.ElementBounds("macro_queue", x, y, PACKUTIL_RIGHT_PANEL_W, macroHeight));
            }

            if (nativeHudVisible) renderNativeModuleHud(context, hudOccluders);

            if (captureBanner != null) {
                Banner.render(UiContexts.overlay(context, MC.font, 0, 0),
                    UiBounds.of(captureBanner.x(), captureBanner.y(), captureBanner.width(), captureBanner.height()),
                    captureBanner.title(), captureBanner.line1(), captureBanner.line2());
            }

            y = 0;

            if (queueVisible) {
                int queueHeight = AmpereQueueRenderer.renderStacked(context, MC.font, x, y, PACKUTIL_RIGHT_PANEL_W, 8,
                    false, !macroRunning, false);
                y += queueHeight;
            }

            if (macroRunning) {
                AmpereMacroProgressRenderer.renderStacked(context, MC.font, x, y, PACKUTIL_RIGHT_PANEL_W, 10,
                    false, true, false);
            }

            if (esp2dVisible) PackModuleScreenRenderer.render(context);
        };

        if (mainHudVisible) {
            AmpereUiScale.pushOverlayScale(context);
            try {
                renderHudElements.run();
            } finally {
                AmpereUiScale.popOverlayScale(context);
            }
        }

        if (MC.screen == null) {
            if (serverProbeBannerVisible) {
                AmpereUiScale.pushOverlayScale(context);
                try {
                    serverInfoOverlay.renderBackgroundProbeBanner(context);
                } finally {
                    AmpereUiScale.popOverlayScale(context);
                }
            }

            if (overlayVisible) {
                overlayManager.renderAll(context, -1, -1, 0f);
            }
        }

        if (notificationsVisible) {
            AmpereUiScale.pushOverlayScale(context);
            try {
                AmpereNotifications.render(context);
            } finally {
                AmpereUiScale.popOverlayScale(context);
            }
        }
    }

    @Unique
    private boolean isAmpereActive() {
        AmpereModule module = AmpereModule.get();
        return module != null && module.isActive();
    }

    @Unique
    private void renderNativeModuleHud(GuiGraphicsExtractor context) {
        renderNativeModuleHud(context, java.util.List.of());
    }

    @Unique
    private void renderNativeModuleHud(GuiGraphicsExtractor context, java.util.List<AmpereHudManager.ElementBounds> occluders) {
        PackModule hud = PackModuleRegistry.get("hud");
        if (!AmpereHudManager.shouldRenderInGame(MC.screen, hud)) return;
        AmpereHudManager.render(context, MC.font, false, null, -1, -1, occluders);
    }

    @Unique
    private AmpereCaptureBannerSpec captureBannerSpec(AmpereSharedState shared, GuiGraphicsExtractor graphics) {
        boolean blockCap = shared.hasBlockCaptureCallback();
        boolean entityCap = shared.hasEntityCaptureCallback();
        boolean attackCap = shared.hasAttackCaptureCallback();
        boolean gbreakCap = shared.isGBreakCapturing();
        if (!blockCap && !entityCap && !attackCap && !gbreakCap) return null;

        String title = gbreakCap
            ? "GBreak Capture"
            : (blockCap ? "Block Capture" : (entityCap ? "Entity Capture" : "Position Capture"));
        String line1 = gbreakCap
            ? "Break a block to capture the insta-break packet. Esc = cancel"
            : (blockCap
                ? "Right-click a block to capture it. Esc = cancel"
                : (entityCap
                    ? "Right-click an entity to capture it. Esc = cancel"
                    : "Left-click to capture the target position. Esc = cancel"));
        String line2 = "";
        if (gbreakCap) {
            line2 = "Waiting for the block-break packet from your next block break";
        } else if (blockCap && MC.hitResult != null
                && MC.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && MC.level != null) {
            net.minecraft.world.phys.BlockHitResult bhr = (net.minecraft.world.phys.BlockHitResult) MC.hitResult;
            String bn = MC.level.getBlockState(bhr.getBlockPos()).getBlock().getName().getString();
            net.minecraft.core.BlockPos bp = bhr.getBlockPos();
            line2 = "Aimed at: " + bn + " (" + bp.getX() + ", " + bp.getY() + ", " + bp.getZ() + ")";
        } else if (entityCap && MC.crosshairPickEntity != null && MC.crosshairPickEntity != MC.player) {
            String eName = MC.crosshairPickEntity.getType().getDescription().getString();
            String eId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(MC.crosshairPickEntity.getType()).toString();
            line2 = "Aimed at: " + eName + " (" + eId + ")";
        }

        int sw = AmpereUiScale.getVirtualScreenWidth();
        UiTextRenderer text = UiContexts.textRenderer(MC.font);
        int boxWidth = Math.min(sw - 16, Math.max(270, Math.max(
            text.width(title),
            Math.max(
                text.width(line1),
                line2.isEmpty() ? 0 : text.width(line2)
            )
        ) + 18));
        int height = Banner.height(UiContexts.overlay(graphics, MC.font, 0, 0), boxWidth, line1, line2);
        return new AmpereCaptureBannerSpec((sw - boxWidth) / 2, 0, boxWidth, height, title, line1, line2);
    }

    @Unique
    private boolean hasAnyCaptureSession(AmpereSharedState shared) {
        if (shared == null) return false;
        if (shared.isCaptureMode() || shared.hasCaptureCancelCallback() || shared.hasAttackCaptureCallback()
            || shared.hasBlockCaptureCallback() || shared.hasEntityCaptureCallback() || shared.isGBreakCapturing()) {
            return true;
        }
        ampere.gui.macro.editor.ActionEditorOverlay editor =
            ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        return editor != null && editor.hasActiveCaptureSession();
    }
}
