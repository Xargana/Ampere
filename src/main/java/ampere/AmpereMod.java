package ampere;

import ampere.gui.vanillaui.components.UiText;
import ampere.modules.PackAutoReconnectState;
import ampere.modules.AmpereModule;
import ampere.security.AmpereProtector;
import ampere.security.AmpereProtectorPackStrip;
import ampere.security.AmpereProtectorServerPackFailureGuard;
import ampere.security.AmpereProtectorTracker;
import ampere.security.AmpereProtectorVanillaKeys;
import ampere.util.AmpereInstaBreakRenderer;
import ampere.util.AmpereFakeGamemode;
import ampere.util.AmpereJoinMacroController;
import ampere.util.AmpereLANSync;
import ampere.util.AmpereOverlayManager;
import ampere.util.AmpereSvgHudLogo;
import ampere.util.AmpereWindowBranding;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

public final class AmpereMod implements ClientModInitializer {
    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        AmpereClientAddon.FOLDER.mkdirs();

        AmpereModule.get().initialize();

        AmpereProtectorTracker.bootstrap();

        if (!AmpereProtector.isOverlapExternalProtectorPresent()) {
            AmpereProtectorVanillaKeys.primeAsync();
        }
        if (AmpereProtector.isFullExternalProtectorPresent()) {
            AmpereClientAddon.LOG.info(
                "[AmpereProtector] External protection mod detected; deferring all anti-fingerprint mixins to it.");
        } else if (AmpereProtector.isExploitPreventerPresent()) {
            AmpereClientAddon.LOG.info(
                "[AmpereProtector] ExploitPreventer detected; deferring overlapping protections while keeping brand/channel hiding active.");
        } else {
            AmpereClientAddon.LOG.info(
                "[AmpereProtector] Built-in anti-fingerprint layer active.");
        }
        AmpereInstaBreakRenderer.initialize();
        ampere.commands.AmpereCommands.init();

        ampere.addons.AddonManager.init();

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
            @Override
            public net.minecraft.resources.Identifier getFabricId() {
                return net.minecraft.resources.Identifier.fromNamespaceAndPath("ampere", "ui_assets");
            }

            @Override
            public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                UiText.onClientResourceReload();
                AmpereSvgHudLogo.clear();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AmpereInstaBreakRenderer.tickPlacePreview();
            AmpereWindowBranding.apply(client);
            AmpereModule.get().tick();
            AmpereJoinMacroController.onClientTick(client);
        });
        ClientTickEvents.END_LEVEL_TICK.register(level -> AmpereLANSync.getInstance().onLevelTick(level.getGameTime()));
        ClientConfigurationConnectionEvents.INIT.register((listener, client) -> AmpereModule.get().onConfigurationConnectionStarted());
        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> {
            AmpereFakeGamemode.clear();
            AmpereInstaBreakRenderer.clear();
            AmpereProtectorServerPackFailureGuard.clear();
            AmpereOverlayManager.get().hideAllInteractiveOverlays();
            AmpereModule.get().onGameLeft();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            PackAutoReconnectState.remember(client.getCurrentServer());
            AmpereModule.get().onGameJoin();
            AmpereJoinMacroController.onPlayJoin();
            ampere.addons.AddonManager.surfaceFailuresOnJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AmpereFakeGamemode.clear();
            AmpereInstaBreakRenderer.clear();
            AmpereProtectorPackStrip.clearAll();
            AmpereProtectorServerPackFailureGuard.clear();
            AmpereOverlayManager.get().hideAllInteractiveOverlays();
            AmpereJoinMacroController.onGameLeft();
            AmpereModule.get().onGameLeft();
        });
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) ->
            AmpereModule.get().appendTooltip(stack, lines)
        );
    }
}
