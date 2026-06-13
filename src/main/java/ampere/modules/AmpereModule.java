package ampere.modules;

import ampere.gui.screen.AmpereModuleScreen;
import ampere.util.AmpereMessaging;
import ampere.util.AmpereConfig;
import ampere.util.AmpereLANSync;
import ampere.util.AmpereInputGate;
import ampere.util.AmpereJoinMacroController;
import ampere.util.AmpereOverlayManager;
import ampere.util.AmpereMacro;
import ampere.util.AmpereMacroManager;
import ampere.util.AmpereNotifications;
import ampere.util.AmperePacketLoggerOverlay;
import ampere.util.AmperePacketRegistry;
import ampere.util.AmperePerf;
import ampere.util.AmpereServerInfoOverlay;
import ampere.util.AmpereSharedState;
import ampere.util.macro.MacroConditionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AmpereModule {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final AmpereModule INSTANCE = new AmpereModule();
    private static final long PASSIVE_PAYLOAD_CAPTURE_MS = 20_000L;
    private static final int PASSIVE_PAYLOAD_RING_CAP = 96;

    private static final Set<Class<?>> C2S_EXCLUDED_DEFAULTS = Set.of(
        net.minecraft.network.protocol.common.ServerboundKeepAlivePacket.class,
        net.minecraft.network.protocol.common.ServerboundPongPacket.class,
        net.minecraft.network.protocol.game.ServerboundClientTickEndPacket.class,
        net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket.class,
        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.class,
        net.minecraft.network.protocol.game.ServerboundContainerClosePacket.class,
        net.minecraft.network.protocol.game.ServerboundSwingPacket.class,
        net.minecraft.network.protocol.game.ServerboundPlayerInputPacket.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos.class
    );

    private AmpereConfig config;
    private boolean initialized;
    private boolean loadGuiKeyPressed;
    private boolean flushQueueKeyPressed;
    private boolean clearQueueKeyPressed;
    private boolean toggleLoggerKeyPressed;
    private boolean toggleSendKeyPressed;
    private boolean toggleDelayKeyPressed;
    private boolean moduleMenuKeyPressed;
    private final java.util.Map<String, Boolean> macroKeyStates = new java.util.HashMap<>();
    private List<AmpereMacro> cachedKeyboundMacros = List.of();
    private long cachedMacroKeybindRevision = -1L;
    private int autoSendTickCounter;
    private int packetLoggerTickCounter;
    private AmperePacketLoggerOverlay packetLoggerOverlay;
    private ampere.util.AmperePayloadChannelListeners passivePayloadListeners;
    private volatile boolean payloadListenerCacheValid;
    private volatile boolean payloadListenerEnabledCache;
    private AmpereServerInfoOverlay serverInfoOverlay;
    private final Deque<PassivePayloadCapture> passivePayloadRing = new ArrayDeque<>(PASSIVE_PAYLOAD_RING_CAP);
    private volatile boolean joinedPlayConnection;
    private volatile boolean spawnedInWorld;
    private volatile long passivePayloadCaptureUntilMs;

    private volatile boolean autoProbePending;
    private volatile long autoProbePendingSince;
    private static final long AUTO_PROBE_CMD_GRACE_MS = 2000L;
    private static final long AUTO_PROBE_GIVE_UP_MS = 25000L;

    private AmpereModule() {
    }

    public static AmpereModule get() {
        return INSTANCE;
    }

    public void initialize() {
        if (initialized) return;

        config = AmpereConfig.load();
        config.applyRuntimeDefaults();
        AmpereConfig.setGlobal(config);
        PackModuleRegistry.initialize(config);
        if (config.c2sPackets.isEmpty()) {
            config.c2sPackets = encodePackets(defaultC2SPackets());
        }

        applyConfigToSharedState();
        if (PackHideState.isActive()) PackHideState.stopRuntimeWork();

        if (config.lanSyncEnabled && !PackHideState.isActive()) {
            AmpereLANSync.getInstance().start();
        }

        initialized = true;
    }

    public void tick() {
        if (!initialized || MC == null) return;

        AmperePerf.tickJoinWindow();
        long perf = AmperePerf.beginJoin();
        updateWorldSpawnState();
        AmperePerf.endJoinSpike("join.worldSpawnState", perf);
        AmpereLANSync lanSync = AmpereLANSync.getInstance();
        if (!PackHideState.isActive() && lanSync.hasTickWork()) {
            perf = AmperePerf.beginJoin();
            lanSync.tick();
            AmperePerf.endJoinSpike("join.lanSync.tick", perf);
        }
        if (!PackHideState.isActive() && MacroConditionRegistry.hasPendingConditions()) {
            perf = AmperePerf.beginJoin();
            MacroConditionRegistry.onTick(MC);
            AmperePerf.endJoinSpike("join.macroConditions.tick", perf);
        }
        AmpereSharedState shared = AmpereSharedState.get();
        if (!PackHideState.isActive() && shared.hasStaggeredSendWork()) shared.tickStaggeredSend();
        if (PackModuleRegistry.hasTickWork()) {
            perf = AmperePerf.beginJoin();
            PackModuleRegistry.tick();
            AmperePerf.endJoinSpike("join.modules.tick", perf, 8_000_000L);
        }
        if (!PackHideState.isActive()) PackAutoReconnectState.tickCurrentScreen();
        updatePassiveXCarryState();
        AmpereServerInfoOverlay serverInfo = getServerDataOverlayIfExists();
        if (!PackHideState.isActive() && serverInfo != null && (serverInfo.isVisible() || serverInfo.shouldRenderBackgroundProbeBanner())) {
            serverInfo.tickBackground();
        }

        if (shared.shouldDelayGuiPackets() && MC.getConnection() != null) {
            if (autoSendTickCounter++ >= 1) {
                autoSendTickCounter = 0;
            }
        } else {
            autoSendTickCounter = 0;
        }

        if (AmpereInputGate.canRunAmpereKeybinds()) {
            tickKeybinds();
        } else {
            loadGuiKeyPressed = false;
            flushQueueKeyPressed = false;
            clearQueueKeyPressed = false;
            toggleLoggerKeyPressed = false;
            toggleSendKeyPressed = false;
            toggleDelayKeyPressed = false;
            macroKeyStates.clear();
        }

        packetLoggerTickCounter++;
        AmperePacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (logger != null) {
            logger.setGameTick(packetLoggerTickCounter);
        }

        if (!PackHideState.isActive() && ampere.api.event.AddonEvents.hasTickListeners()) {
            ampere.api.event.AddonEvents.fireTick(MC);
        }
    }

    public void onGameJoin() {
        AmperePerf.beginJoinWindow();
        long perf = AmperePerf.beginJoin();
        joinedPlayConnection = true;
        spawnedInWorld = false;
        autoProbePending = config != null && config.autoProbePlugins;
        if (autoProbePending) autoProbePendingSince = System.currentTimeMillis();
        beginPassivePayloadCapture();
        applyRuntimePacketFlowDefaults();
        if (PackHideState.isActive()) PackHideState.stopRuntimeWork();

        if (!PackHideState.isActive() && config != null && config.packetLoggerCapturing) {
            getPacketLoggerOverlay();
        }

        if (config.lanSyncEnabled && !PackHideState.isActive() && !AmpereLANSync.getInstance().isRunning()) {
            AmpereLANSync.getInstance().start();
        }

        if (!PackHideState.isActive()) AmpereLANSync.getInstance().onGameJoined();
        PackModuleRegistry.onGameJoin();
        ampere.api.event.AddonEvents.fireGameJoin();
        AmperePerf.endJoinSpike("join.onGameJoin", perf, 8_000_000L);
    }

    public void onGameLeft() {
        joinedPlayConnection = false;
        spawnedInWorld = false;
        autoProbePending = false;
        ampere.util.AmperePluginPayloadFingerprints.clearSession();
        AmpereServerInfoOverlay leftOverlay = getServerDataOverlayIfExists();
        if (leftOverlay != null) leftOverlay.resetAutoProbeContext();
        passivePayloadCaptureUntilMs = 0L;
        AmpereSharedState.get().clearRealServerVersion();
        loadGuiKeyPressed = false;
        flushQueueKeyPressed = false;
        clearQueueKeyPressed = false;
        toggleLoggerKeyPressed = false;
        toggleSendKeyPressed = false;
        toggleDelayKeyPressed = false;
        moduleMenuKeyPressed = false;
        PackModuleRegistry.onGameLeft();
        ampere.api.event.AddonEvents.fireGameLeft();
    }

    public boolean isActive() {
        return initialized && spawnedInWorld && isPlayerSpawnedInWorld();
    }

    public boolean arePacketHooksActive() {
        if (!isActive() || PackHideState.isActive()) return false;
        AmpereSharedState shared = AmpereSharedState.get();
        return shared.hasPacketFlowWork()
            || isPacketLoggerCapturing()
            || isServerInfoPacketObservationActive()
            || PackModuleRegistry.hasActivePacketEventModules()
            || ampere.api.event.AddonEvents.hasPacketListeners()
            || ampere.util.macro.PacketGateManager.hasActiveGates()
            || ampere.util.macro.MacroExecutor.hasPacketObservationWork();
    }

    public PacketHookSnapshot packetHookSnapshot(boolean playConnection) {
        if (PackHideState.isActive()) return PacketHookSnapshot.inactive();
        boolean passivePayloadCapture = isPassivePayloadCaptureActive();
        if (!isActive()) {
            return new PacketHookSnapshot(false, passivePayloadCapture, false, false);
        }
        AmpereSharedState shared = AmpereSharedState.get();
        boolean loggerCapture = isPacketLoggerCapturing();
        boolean serverInfoObservation = isServerInfoPacketObservationActive();
        boolean normalPath = playConnection && (shared.hasPacketFlowWork()
            || loggerCapture
            || serverInfoObservation
            || PackModuleRegistry.hasActivePacketEventModules()
            || ampere.api.event.AddonEvents.hasPacketListeners()
            || ampere.util.macro.PacketGateManager.hasActiveGates()
            || ampere.util.macro.MacroExecutor.hasPacketObservationWork());
        return new PacketHookSnapshot(normalPath, passivePayloadCapture, loggerCapture, serverInfoObservation);
    }

    public boolean hasPassivePayloadCaptureWork() {
        if (PackHideState.isActive() || !isPassivePayloadCaptureActive()) return false;
        return true;
    }

    public boolean shouldCapturePacketPlaintext() {
        if (PackHideState.isActive()) return false;
        return isPacketLoggerCapturing();
    }

    public boolean shouldCapturePayloadBytes() {
        if (PackHideState.isActive()) return false;
        return isPacketLoggerCapturing() || hasPassivePayloadCaptureWork();
    }

    private boolean isServerInfoPacketObservationActive() {
        if (config != null && config.autoProbePlugins && autoProbePending) return true;
        AmpereServerInfoOverlay overlay = getServerDataOverlayIfExists();
        return overlay != null && overlay.isPacketObservationActive();
    }

    public boolean hasPluginDiscoveryObservationWork() {
        if (!isActive() || PackHideState.isActive()) return false;
        return isServerInfoPacketObservationActive();
    }

    private AmpereServerInfoOverlay getPluginDiscoveryOverlay() {
        AmpereServerInfoOverlay overlay = getServerDataOverlayIfExists();
        if (overlay != null) return overlay;
        if (config != null && config.autoProbePlugins && autoProbePending) {
            return getServerDataOverlay();
        }
        return null;
    }

    public void observePluginDiscoveryPacketSend(Packet<?> packet) {
        if (!hasPluginDiscoveryObservationWork() || packet == null) return;
        AmpereServerInfoOverlay overlay = getPluginDiscoveryOverlay();
        if (overlay == null) return;
        overlay.onCommandSuggestionRequest(packet);
        overlay.onOutgoingCommandPacket(packet);
    }

    public void observePluginDiscoveryPacketReceive(Packet<?> packet) {
        if (!hasPluginDiscoveryObservationWork() || packet == null) return;
        AmpereServerInfoOverlay overlay = getPluginDiscoveryOverlay();
        if (overlay == null) return;
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket suggestions) {
            overlay.onCommandSuggestions(suggestions.id(), suggestions);
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundOpenScreenPacket) {
            overlay.onOpenScreenPacket(packet);
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandsPacket) {
            overlay.onCommandTreeChanged();
            if (spawnedInWorld && !autoProbePending && config != null && config.autoProbePlugins) {
                autoProbePending = true;
                autoProbePendingSince = System.currentTimeMillis();
            }
        }
    }

    private boolean shouldObservePluginPayloadFingerprints() {
        return isPassivePayloadCaptureActive() || isServerInfoPacketObservationActive() || isPacketLoggerCapturing();
    }

    public void invalidatePayloadListenerCache() {
        payloadListenerCacheValid = false;
        if (passivePayloadListeners != null) passivePayloadListeners.load();
    }

    private boolean payloadListenersEnabledCached() {
        if (payloadListenerCacheValid) return payloadListenerEnabledCache;
        boolean enabled = computePayloadListenersEnabled();
        payloadListenerEnabledCache = enabled;
        payloadListenerCacheValid = true;
        return enabled;
    }

    private boolean computePayloadListenersEnabled() {
        AmpereConfig cfg = AmpereConfig.getGlobal();
        if (cfg == null || cfg.packetLoggerPayloadListeners == null) return false;
        for (AmpereConfig.PayloadChannelListenerRule rule : cfg.packetLoggerPayloadListeners) {
            if (rule != null && rule.enabled) return true;
        }
        return false;
    }

    public void onConfigurationConnectionStarted() {
        beginPassivePayloadCapture();
    }

    public boolean isPassivePayloadCaptureActive() {
        return System.currentTimeMillis() <= passivePayloadCaptureUntilMs;
    }

    public boolean isPacketLoggerCapturing() {
        if (PackHideState.isActive()) return false;
        AmperePacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        return logger != null && !logger.isPaused();
    }

    public void capturePassivePayloadPacket(Packet<?> packet, String direction) {
        capturePassivePayloadPacket(packet, direction, "");
    }

    public void capturePassivePayloadPacket(Packet<?> packet, String direction, String protocolPhase) {
        if (PackHideState.isActive()) return;
        if (!hasPassivePayloadCaptureWork()) return;
        if (ampere.util.AmperePayloadSupport.extractPayload(packet) == null) return;
        ampere.util.AmperePayloadSupport.rememberPayloadProtocol(packet, protocolPhase);
        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(packet, direction);
        }

        AmperePacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        boolean fullLoggerCapture = logger != null && !logger.isPaused();
        boolean listenedPayload = fullLoggerCapture || matchesEnabledPayloadListener(packet, direction);
        if (!listenedPayload) return;
        if (logger != null) {
            logger.logPayloadPacketSilently(packet, direction);
        } else {
            rememberPassivePayload(packet, direction);
        }
    }

    private boolean matchesEnabledPayloadListener(Packet<?> packet, String direction) {
        net.minecraft.network.protocol.common.custom.CustomPacketPayload payload =
            ampere.util.AmperePayloadSupport.extractPayload(packet);
        if (payload == null) return false;
        String channel = ampere.util.AmperePayloadSupport.payloadChannel(payload);
        if (channel == null || channel.isBlank()) return false;
        ampere.util.AmperePayloadChannelListeners listeners = getPassivePayloadListeners();
        return listeners.hasEnabledRules() && listeners.matchChannel(channel, direction) != null;
    }

    private ampere.util.AmperePayloadChannelListeners getPassivePayloadListeners() {
        if (passivePayloadListeners == null) {
            passivePayloadListeners = new ampere.util.AmperePayloadChannelListeners();
        }
        return passivePayloadListeners;
    }

    private void rememberPassivePayload(Packet<?> packet, String direction) {
        long perf = AmperePerf.beginJoin();
        ampere.util.AmperePayloadSupport.PayloadSnapshot snapshot =
            ampere.util.AmperePayloadSupport.snapshot(packet, direction);
        if (snapshot == null) return;
        synchronized (passivePayloadRing) {
            passivePayloadRing.addLast(new PassivePayloadCapture(
                System.currentTimeMillis(),
                packetLoggerTickCounter,
                direction == null ? "" : direction,
                packet.getClass(),
                snapshot
            ));
        }
        AmperePerf.endJoinSpike("join.passivePayload.snapshot", perf);
    }

    public void toggle() {
        AmpereMessaging.sendPrefixed("Ampere is always enabled in standalone mode.");
    }

    private void beginPassivePayloadCapture() {
        passivePayloadCaptureUntilMs = Math.max(passivePayloadCaptureUntilMs, System.currentTimeMillis() + PASSIVE_PAYLOAD_CAPTURE_MS);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void appendTooltip(ItemStack stack, List<?> lines) {
        if (!PackModuleRegistry.hasTooltipHooks()) return;
        for (PackModule module : PackModuleRegistry.tooltipModulesForDispatch()) {
            module.appendTooltip(stack, lines);
        }
    }

    public boolean handlePacketSend(Packet<?> packet) {
        long perf = AmperePerf.beginJoin();
        try {
        if (PackHideState.isActive()) return false;
        observePluginDiscoveryPacketSend(packet);
        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(packet, "C2S");
        }
        AmperePacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (PackModuleRegistry.onPacketSend(packet)) return true;
        if (ampere.api.event.AddonEvents.firePacketSend(packet)) return true;
        if (logger == null) return false;
        if (!logger.isPacketBlocked(packet.getClass())) {
            logger.logPacket(packet, "C2S");
        }
        return false;
        } finally {
            AmperePerf.endJoinSpike("join.packetSendHook", perf);
        }
    }

    public boolean handlePacketReceive(Packet<?> packet) {
        long perf = AmperePerf.beginJoin();
        try {
        if (PackHideState.isActive()) return false;
        observePluginDiscoveryPacketReceive(packet);
        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(packet, "S2C");
        }

        AmperePacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (PackModuleRegistry.onPacketReceive(packet)) return true;
        ampere.api.event.AddonEvents.firePacketReceive(packet);
        if (logger == null) return false;
        if (!logger.isPacketBlocked(packet.getClass())) {
            logger.logPacket(packet, "S2C");
        }
        return false;
        } finally {
            AmperePerf.endJoinSpike("join.packetReceiveHook", perf);
        }
    }

    private void observePluginPayloadFingerprint(Packet<?> packet, String direction) {
        if (packet == null || !AmperePacketLoggerOverlay.isPayloadPacket(packet)) return;
        net.minecraft.network.protocol.common.custom.CustomPacketPayload payload =
            ampere.util.AmperePayloadSupport.extractPayload(packet);
        if (payload == null) return;
        String channel = ampere.util.AmperePayloadSupport.payloadChannel(payload);
        if (!ampere.util.AmperePluginPayloadFingerprints.shouldObserveChannel(channel)) return;

        try {
            ampere.util.AmperePayloadSupport.PayloadSnapshot snapshot =
                ampere.util.AmperePayloadSupport.snapshot(packet, direction);
            if (snapshot == null) return;
            boolean changed = ampere.util.AmperePluginPayloadFingerprints.observe(
                currentPayloadFingerprintServerAddress(),
                currentPayloadFingerprintBrand(),
                snapshot
            );
            if (changed) {
                AmpereServerInfoOverlay overlay = getServerDataOverlayIfExists();
                if (overlay != null) overlay.onPayloadFingerprintUpdated();
            }
        } catch (Throwable ignored) {
        }
    }

    private String currentPayloadFingerprintBrand() {
        if (MC == null || MC.getConnection() == null) return "";
        String brand = MC.getConnection().serverBrand();
        return brand == null ? "" : brand;
    }

    private String currentPayloadFingerprintServerAddress() {
        if (MC == null) return "";
        ServerData entry = MC.getCurrentServer();
        if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return entry.ip.trim().toLowerCase(java.util.Locale.ROOT);
        }
        if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
            SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                    host = inet.getAddress().getHostAddress();
                }
                if (host != null && !host.isBlank()) {
                    return (host + ":" + inet.getPort()).trim().toLowerCase(java.util.Locale.ROOT);
                }
            } else if (address != null) {
                String raw = address.toString();
                if (raw != null && !raw.isBlank()) {
                    return raw.replaceFirst("^/", "").trim().toLowerCase(java.util.Locale.ROOT);
                }
            }
        }
        return "";
    }

    public AmperePacketLoggerOverlay getPacketLoggerOverlay() {
        if (packetLoggerOverlay == null && MC != null && MC.font != null) {
            packetLoggerOverlay = new AmperePacketLoggerOverlay(MC.font);
            packetLoggerOverlay.restoreLayout();
            hydratePassivePayloads(packetLoggerOverlay);
        }
        return packetLoggerOverlay;
    }

    public AmperePacketLoggerOverlay getPacketLoggerOverlayIfExists() {
        return packetLoggerOverlay;
    }

    private void hydratePassivePayloads(AmperePacketLoggerOverlay logger) {
        if (logger == null) return;
        List<PassivePayloadCapture> captures;
        synchronized (passivePayloadRing) {
            if (passivePayloadRing.isEmpty()) return;
            captures = new ArrayList<>(passivePayloadRing);
            passivePayloadRing.clear();
        }
        for (PassivePayloadCapture capture : captures) {
            logger.logPayloadSnapshotSilently(
                capture.timestampMs(),
                capture.gameTick(),
                capture.direction(),
                capture.packetClass(),
                capture.snapshot()
            );
        }
    }

    private record PassivePayloadCapture(long timestampMs, int gameTick, String direction, Class<?> packetClass,
                                         ampere.util.AmperePayloadSupport.PayloadSnapshot snapshot) {
    }

    public record PacketHookSnapshot(
        boolean normalPath,
        boolean passivePayloadCapture,
        boolean packetLoggerCapturing,
        boolean pluginDiscoveryObservation
    ) {
        private static final PacketHookSnapshot INACTIVE = new PacketHookSnapshot(false, false, false, false);

        public static PacketHookSnapshot inactive() {
            return INACTIVE;
        }
    }

    public AmpereServerInfoOverlay getServerDataOverlay() {
        if (serverInfoOverlay == null && MC != null && MC.font != null) {
            serverInfoOverlay = new AmpereServerInfoOverlay(MC.font);
            serverInfoOverlay.restoreState();
        }
        return serverInfoOverlay;
    }

    public AmpereServerInfoOverlay getServerDataOverlayIfExists() {
        return serverInfoOverlay;
    }

    public boolean isNoPauseOnLostFocus() {
        return config.noPauseOnLostFocus;
    }

    public boolean isLANSyncEnabled() {
        return config.lanSyncEnabled;
    }

    public boolean isBypassResourcePack() {
        return config != null ? config.pretendPackAccepted : AmpereConfig.getGlobal().pretendPackAccepted;
    }

    public boolean isSpoofClientVanilla() {
        return config != null ? config.spoofClientVanilla : AmpereConfig.getGlobal().spoofClientVanilla;
    }

    public boolean isInventoryMoveEnabled() {
        PackModule module = PackModuleRegistry.get("inv-move");
        return module != null ? module.isEnabled() : config != null && config.inventoryMove;
    }

    public void setInventoryMoveEnabled(boolean value) {
        if (config == null) return;
        config.inventoryMove = value;
        PackModule module = PackModuleRegistry.get("inv-move");
        if (module != null && module.isEnabled() != value) module.setEnabledSilently(value);
        saveConfig();
    }

    public boolean isXCarryEnabled() {
        PackModule module = PackModuleRegistry.get("xcarry");
        return module != null ? module.isEnabled() : config != null && config.xCarry;
    }

    public void setXCarryEnabled(boolean value) {
        if (config == null) return;
        config.xCarry = value;
        PackModule module = PackModuleRegistry.get("xcarry");
        if (module != null && module.isEnabled() != value) module.setEnabledSilently(value);
        saveConfig();
    }

    public void setBypassResourcePack(boolean value) {
        config.pretendPackAccepted = value;
        AmpereSharedState.get().setBypassResourcePack(value);
        saveConfig();
    }

    public void setSpoofClientVanilla(boolean value) {
        if (config == null) return;
        config.spoofClientVanilla = value;
        saveConfig();
    }

    public boolean isForceDenyResourcePack() {
        return config != null ? config.autoDenyResourcePack : AmpereConfig.getGlobal().autoDenyResourcePack;
    }

    public void setForceDenyResourcePack(boolean value) {
        config.autoDenyResourcePack = value;
        AmpereSharedState.get().setResourcePackForceDeny(value);
        saveConfig();
    }

    public boolean useMsSleepMode() {
        return config.useMsSleepMode;
    }

    public int getMsSleepInterval() {
        return config.msSleepInterval;
    }

    public boolean useInstantExecutionMode() {
        return config.instantExecutionMode;
    }

    public int getActionDelayUs() {
        return config.actionDelayUs;
    }

    public boolean usePacketBurstMode() {
        return config.packetBurstMode;
    }

    public boolean shouldUseDirectFlush() {
        return config.useDirectFlush;
    }

    public boolean shouldForceChannelFlush() {
        return config.forceChannelFlush;
    }

    public boolean shouldFlushQueueOnDelayDisable() {
        return config != null && config.flushQueueOnDelayDisable;
    }

    public void setFlushQueueOnDelayDisable(boolean value) {
        if (config == null) return;
        config.flushQueueOnDelayDisable = value;
        saveConfig();
    }

    public boolean isCaptureAsExact() {
        return config != null && config.captureAsExact;
    }

    public void setCaptureAsExact(boolean value) {
        if (config == null) return;
        config.captureAsExact = value;
        saveConfig();
    }

    public boolean shouldUseCustomPackets() {
        return config.useCustomPackets;
    }

    public void setUseCustomPackets(boolean value) {
        config.useCustomPackets = value;
        AmpereSharedState.get().setUseCustomPackets(value);
        saveConfig();
    }

    public void setSendGuiPackets(boolean value) {
        config.sendGuiPackets = value;
        AmpereSharedState.get().setSendGuiPackets(value);
    }

    public boolean applySendGuiPacketsUiBehavior(boolean value) {
        setSendGuiPackets(value);
        saveConfig();
        return value;
    }

    public void setDelayGuiPackets(boolean value) {
        config.delayGuiPackets = value;
        AmpereSharedState.get().setDelayGuiPackets(value);
    }

    public String getCommandPrefix() {
        return config == null ? "" : (config.commandPrefix == null ? "" : config.commandPrefix);
    }

    public void setCommandPrefix(String prefix) {
        if (config == null) return;
        config.commandPrefix = ampere.util.AmpereCompatManager.normalizeStoredCommandPrefix(prefix);
        saveConfig();
    }

    public java.util.Map<Integer, String> getCommandBinds() {
        if (config == null) return java.util.Collections.emptyMap();
        if (config.commandBinds == null) config.commandBinds = new java.util.LinkedHashMap<>();
        return config.commandBinds;
    }

    public boolean hasCommandBinds() {
        return config != null && config.commandBinds != null && !config.commandBinds.isEmpty();
    }

    public void setCommandBind(int key, String command) {
        if (config == null) return;
        if (config.commandBinds == null) config.commandBinds = new java.util.LinkedHashMap<>();
        if (command == null || command.isBlank()) config.commandBinds.remove(key);
        else config.commandBinds.put(key, command);
        saveConfig();
    }

    public void clearCommandBind(int key) {
        if (config == null || config.commandBinds == null) return;
        config.commandBinds.remove(key);
        saveConfig();
    }

    private ampere.modules.PackModule xcarryModule() {
        return ampere.modules.PackModuleRegistry.get("xcarry");
    }
    public boolean isXCarryUseCrafting() {
        ampere.modules.PackModule m = xcarryModule();
        if (m == null) return true;
        String v = m.value("use-crafting");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public boolean isXCarryUseArmor() {
        ampere.modules.PackModule m = xcarryModule();
        if (m == null) return true;
        String v = m.value("use-armor");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public boolean isXCarryUseOffhand() {
        ampere.modules.PackModule m = xcarryModule();
        if (m == null) return true;
        String v = m.value("use-offhand");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public void setXCarryUseCrafting(boolean v) {
        ampere.modules.PackModule m = xcarryModule();
        if (m != null) m.setValue("use-crafting", Boolean.toString(v));
    }
    public void setXCarryUseArmor(boolean v) {
        ampere.modules.PackModule m = xcarryModule();
        if (m != null) m.setValue("use-armor", Boolean.toString(v));
    }
    public void setXCarryUseOffhand(boolean v) {
        ampere.modules.PackModule m = xcarryModule();
        if (m != null) m.setValue("use-offhand", Boolean.toString(v));
    }
    public boolean isXCarryCarryCursor() {
        ampere.modules.PackModule m = xcarryModule();
        if (m == null) return true;
        String v = m.value("carry-cursor");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public void setXCarryCarryCursor(boolean v) {
        ampere.modules.PackModule m = xcarryModule();
        if (m != null) m.setValue("carry-cursor", Boolean.toString(v));
    }

    public java.util.Set<Integer> getXCarryModuleSlotMask() {
        java.util.LinkedHashSet<Integer> s = new java.util.LinkedHashSet<>();
        if (isXCarryUseCrafting()) { s.add(1); s.add(2); s.add(3); s.add(4); }
        if (isXCarryUseArmor())    { s.add(5); s.add(6); s.add(7); s.add(8); }
        if (isXCarryUseOffhand())  { s.add(45); }
        if (s.isEmpty()) { s.add(1); s.add(2); s.add(3); s.add(4); }
        return s;
    }

    public int applyDelayGuiPacketsUiBehavior(boolean value) {
        setDelayGuiPackets(value);
        saveConfig();

        if (!value && shouldFlushQueueOnDelayDisable() && MC != null && MC.getConnection() != null) {
            return AmpereSharedState.get().flushDelayedPackets(MC.getConnection());
        }

        return 0;
    }

    public int flushQueuedPacketsUiBehavior() {
        if (MC == null || MC.getConnection() == null) return 0;
        return AmpereSharedState.get().flushDelayedPackets(MC.getConnection());
    }

    public int clearQueuedPacketsUiBehavior() {
        AmpereSharedState shared = AmpereSharedState.get();
        int count = shared.clearQueuedPackets();
        return count;
    }

    public void notifyDelayPacketsUiResult(boolean enabled, int flushed) {
        AmpereSharedState shared = AmpereSharedState.get();
        if (enabled) {
            AmpereNotifications.show("Delay Packets on", 0xFF35D873);
            return;
        }

        if (flushed > 0) {
            if (shared.isStaggering()) {
                shared.setPendingQueueCompletionMessage("Sent " + flushed + " packet" + (flushed == 1 ? "" : "s") + ".");
                AmpereNotifications.show("Delay Packets off - sending " + flushed, 0xFFFF5B5B);
            } else {
                AmpereNotifications.show("Delay Packets off - sent " + flushed, 0xFFFF5B5B);
            }
            return;
        }

        if (!shouldFlushQueueOnDelayDisable()
            && (!shared.getDelayedPackets().isEmpty() || !shared.getStaggeredQueue().isEmpty())) {
            AmpereNotifications.show("Delay Packets off - queue kept", 0xFFFF5B5B);
        } else {
            AmpereNotifications.show("Delay Packets off - queue empty", 0xFFFF5B5B);
        }
    }

    public void notifyFlushQueuedPacketsUiResult(int count) {
        AmpereSharedState shared = AmpereSharedState.get();
        if (count > 0) {
            if (shared.isStaggering()) {
                shared.setPendingQueueCompletionMessage("Sent " + count + " packet" + (count == 1 ? "" : "s") + ".");
                AmpereNotifications.show("Sending " + count + " packet" + (count == 1 ? "" : "s"), 0xFFFFC857);
            } else {
                AmpereNotifications.show("Sent " + count + " packet" + (count == 1 ? "" : "s"), 0xFF35D873);
            }
        } else {
            AmpereNotifications.show("Queue empty", 0xFFFF5B5B);
        }
    }

    public void notifyClearQueuedPacketsUiResult(int count) {
        AmpereNotifications.show(
            count > 0 ? "Cleared " + count + " packet" + (count == 1 ? "" : "s") : "Queue empty",
            count > 0 ? 0xFFFFC857 : 0xFFFF5B5B
        );
    }

    public boolean togglePacketLoggerUiBehavior() {
        AmperePacketLoggerOverlay overlay = getPacketLoggerOverlay();
        if (overlay == null) return false;
        AmpereOverlayManager.get().register(overlay);
        overlay.toggle();
        return true;
    }

    public boolean restoreSavedScreenUiBehavior() {
        AmpereSharedState shared = AmpereSharedState.get();
        if (MC == null) return false;
        if (shared.getStoredScreen() == null || shared.getStoredAbstractContainerMenu() == null) {
            return false;
        }

        MC.execute(() -> {
            MC.setScreen(shared.getStoredScreen());
            AbstractContainerMenu handler = shared.getStoredAbstractContainerMenu();
            if (MC.player != null) MC.player.containerMenu = handler;
        });
        return true;
    }

    public Set<Class<? extends Packet<?>>> getC2SPackets() {
        return new LinkedHashSet<>(AmpereSharedState.get().getC2SPackets());
    }

    public Set<Class<? extends Packet<?>>> getS2CPackets() {
        return new LinkedHashSet<>(AmpereSharedState.get().getS2CPackets());
    }

    public void setC2SPackets(Set<Class<? extends Packet<?>>> packets) {
        Set<Class<? extends Packet<?>>> safe = packets == null ? defaultC2SPackets() : new LinkedHashSet<>(packets);
        AmpereSharedState.get().setC2SPackets(safe);
        config.c2sPackets = encodePackets(safe);
        saveConfig();
    }

    public void setS2CPackets(Set<Class<? extends Packet<?>>> packets) {
        Set<Class<? extends Packet<?>>> safe = packets == null ? defaultS2CPackets() : new LinkedHashSet<>(packets);
        AmpereSharedState.get().setS2CPackets(safe);
        config.s2cPackets = encodePackets(safe);
        saveConfig();
    }

    public void resetC2SPacketsToDefault() {
        setC2SPackets(defaultC2SPackets());
    }

    public void resetS2CPacketsToDefault() {
        setS2CPackets(defaultS2CPackets());
    }

    public Set<Class<? extends Packet<?>>> defaultC2SPackets() {
        Set<Class<? extends Packet<?>>> defaults = new LinkedHashSet<>();
        for (Class<? extends Packet<?>> packetClass : AmperePacketRegistry.getC2SPackets()) {
            if (!C2S_EXCLUDED_DEFAULTS.contains(packetClass)) defaults.add(packetClass);
        }

        defaults.add(net.minecraft.network.protocol.game.ServerboundChatPacket.class);
        defaults.add(net.minecraft.network.protocol.game.ServerboundChatCommandPacket.class);
        defaults.add(net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket.class);
        defaults.add(net.minecraft.network.protocol.game.ServerboundSignUpdatePacket.class);
        return defaults;
    }

    public Set<Class<? extends Packet<?>>> defaultS2CPackets() {
        return new LinkedHashSet<>();
    }

    private void applyConfigToSharedState() {
        AmpereSharedState shared = AmpereSharedState.get();
        applyRuntimePacketFlowDefaults();
        shared.setUseCustomPackets(config.useCustomPackets);
        shared.setC2SPackets(resolvePackets(config.c2sPackets, true));
        shared.setS2CPackets(resolvePackets(config.s2cPackets, false));
        shared.setAllowSignEditing(config.allowSignEditing);
        shared.setResourcePackForceDeny(config.autoDenyResourcePack);
        shared.setBypassResourcePack(config.pretendPackAccepted);
        shared.setStaggeredPacketSend(config.staggeredPacketSend);
        shared.setStaggeredSendDelay(config.staggeredSendDelay);
    }

    private void applyRuntimePacketFlowDefaults() {
        if (config != null) {
            config.applyRuntimeDefaults();
        }
        AmpereSharedState shared = AmpereSharedState.get();
        shared.setSendGuiPackets(true);
        shared.setDelayGuiPackets(false);
        shared.setStaggeredPacketSend(false);
        shared.setCaptureMode(false);
    }

    private void updatePassiveXCarryState() {
        AmpereSharedState shared = AmpereSharedState.get();
        if (shared.isXCarryForced()) return;
        if (!shared.isXCarryActive() && !isXCarryEnabled()) return;
        if (PackHideState.isActive()) {
            shared.setXCarryActive(false);
            return;
        }

        boolean active = false;
        if (isXCarryEnabled() && MC.player != null && MC.player.inventoryMenu != null) {
            active = ampere.util.macro.XCarryAction.hasStoredItems(MC.player.inventoryMenu, true);
        }

        shared.setXCarryActive(active);
    }

    private void updateWorldSpawnState() {
        if (!joinedPlayConnection) {
            spawnedInWorld = false;
            autoProbePending = false;
            return;
        }

        boolean wasSpawned = spawnedInWorld;
        spawnedInWorld = isPlayerSpawnedInWorld();
        if (!wasSpawned && spawnedInWorld && !PackHideState.isActive()) {
            AmpereJoinMacroController.onWorldReady();

            autoProbePending = true;
            autoProbePendingSince = System.currentTimeMillis();
        }
        tickAutoProbe();
    }

    private void tickAutoProbe() {
        if (!autoProbePending) return;
        long waited = System.currentTimeMillis() - autoProbePendingSince;
        if (waited > AUTO_PROBE_GIVE_UP_MS) { autoProbePending = false; return; }
        if (PackHideState.isActive() || config == null || !config.autoProbePlugins) {
            autoProbePending = false;
            return;
        }
        if (!spawnedInWorld) return;

        AmpereServerInfoOverlay overlay = getServerDataOverlay();
        if (overlay == null || !overlay.isAutoProbeConnectionReady()) return;

        if (!overlay.isAutoProbeContextReady() && waited < AUTO_PROBE_CMD_GRACE_MS) return;

        if (overlay.autoProbeOnSpawn()) autoProbePending = false;
    }

    private boolean isPlayerSpawnedInWorld() {
        return MC != null && MC.getConnection() != null && MC.player != null && MC.level != null;
    }

    private void saveConfig() {
        if (config == null) return;
        config.save();
    }

    private Set<Class<? extends Packet<?>>> resolvePackets(List<String> names, boolean c2s) {
        Set<Class<? extends Packet<?>>> resolved = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                if (name == null || name.isBlank()) continue;
                Class<? extends Packet<?>> packetClass = AmperePacketRegistry.getPacket(name);
                if (packetClass == null) {
                    try {
                        Class<?> direct = Class.forName(name);
                        if (Packet.class.isAssignableFrom(direct)) {
                            @SuppressWarnings("unchecked")
                            Class<? extends Packet<?>> typed = (Class<? extends Packet<?>>) direct;
                            packetClass = typed;
                        }
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                if (packetClass != null) resolved.add(packetClass);
            }
        }

        if (resolved.isEmpty()) {
            return c2s ? defaultC2SPackets() : defaultS2CPackets();
        }

        return resolved;
    }

    private List<String> encodePackets(Set<Class<? extends Packet<?>>> packets) {
        List<String> names = new ArrayList<>();
        for (Class<? extends Packet<?>> packetClass : packets) {
            String name = AmperePacketRegistry.getName(packetClass);
            names.add(name != null ? name : packetClass.getName());
        }
        return names;
    }

    private void tickKeybinds() {
        AmpereConfig cfg = config;
        if (cfg == null) return;
        AmpereSharedState shared = AmpereSharedState.get();
        refreshKeyboundMacroCache();

        if (cfg.keybindModuleMenu != -1) {
            boolean pressed = isBindPressed(cfg.keybindModuleMenu);
            if (pressed && !moduleMenuKeyPressed && MC.screen == null) {
                MC.setScreen(new AmpereModuleScreen(null));
            }
            moduleMenuKeyPressed = pressed;
        }

        if (PackHideState.isActive()) {
            loadGuiKeyPressed = false;
            flushQueueKeyPressed = false;
            clearQueueKeyPressed = false;
            toggleLoggerKeyPressed = false;
            toggleSendKeyPressed = false;
            toggleDelayKeyPressed = false;
            macroKeyStates.clear();
            return;
        }

        if (cfg.keybindLoadGui != -1) {
            boolean pressed = isBindPressed(cfg.keybindLoadGui);
            if (pressed && !loadGuiKeyPressed) {
                if (restoreSavedScreenUiBehavior()) {
                    AmpereNotifications.show("GUI restored.", 0xFF35D873);
                } else {
                    AmpereNotifications.error("No stored GUI.");
                }
            }
            loadGuiKeyPressed = pressed;
        }

        if (cfg.keybindFlushQueue != -1) {
            boolean pressed = isBindPressed(cfg.keybindFlushQueue);
            if (pressed && !flushQueueKeyPressed) {
                int count = flushQueuedPacketsUiBehavior();
                notifyFlushQueuedPacketsUiResult(count);
            }
            flushQueueKeyPressed = pressed;
        }

        if (cfg.keybindClearQueue != -1) {
            boolean pressed = isBindPressed(cfg.keybindClearQueue);
            if (pressed && !clearQueueKeyPressed) {
                int count = clearQueuedPacketsUiBehavior();
                notifyClearQueuedPacketsUiResult(count);
            }
            clearQueueKeyPressed = pressed;
        }

        if (cfg.keybindToggleLogger != -1) {
            boolean pressed = isBindPressed(cfg.keybindToggleLogger);
            if (pressed && !toggleLoggerKeyPressed) {
                togglePacketLoggerUiBehavior();
            }
            toggleLoggerKeyPressed = pressed;
        }

        if (cfg.keybindToggleSend != -1) {
            boolean pressed = isBindPressed(cfg.keybindToggleSend);
            if (pressed && !toggleSendKeyPressed) {
                boolean newValue = !shared.shouldSendGuiPackets();
                applySendGuiPacketsUiBehavior(newValue);
                AmpereNotifications.show("Send Packets " + (newValue ? "on" : "off"), newValue ? 0xFF35D873 : 0xFFFF3B3B);
            }
            toggleSendKeyPressed = pressed;
        }

        if (cfg.keybindToggleDelay != -1) {
            boolean pressed = isBindPressed(cfg.keybindToggleDelay);
            if (pressed && !toggleDelayKeyPressed) {
                boolean newValue = !shared.shouldDelayGuiPackets();
                int sent = applyDelayGuiPacketsUiBehavior(newValue);
                notifyDelayPacketsUiResult(newValue, sent);
            }
            toggleDelayKeyPressed = pressed;
        }

        for (AmpereMacro macro : cachedKeyboundMacros) {
            boolean pressed = isBindPressed(macro.keyCode);
            boolean wasPressed = macroKeyStates.getOrDefault(macro.name, false);
            if (pressed && !wasPressed) {
                if (ampere.util.macro.MacroExecutor.isVisibleRunning()) {
                    ampere.util.macro.MacroExecutor.stop();
                } else {
                    macro.execute();
                }
            }
            macroKeyStates.put(macro.name, pressed);
        }
    }

    private void refreshKeyboundMacroCache() {
        AmpereMacroManager macroManager = AmpereMacroManager.get();
        long revision = macroManager.getRevision();
        if (revision == cachedMacroKeybindRevision) return;

        List<AmpereMacro> keyboundMacros = new ArrayList<>();
        Set<String> activeMacroNames = new HashSet<>();
        for (AmpereMacro macro : macroManager.getAll()) {
            if (macro == null || macro.keyCode == -1) continue;
            keyboundMacros.add(macro);
            activeMacroNames.add(macro.name);
        }

        macroKeyStates.keySet().removeIf(name -> !activeMacroNames.contains(name));
        cachedKeyboundMacros = keyboundMacros;
        cachedMacroKeybindRevision = revision;
    }

    private boolean isAnyTextFieldFocused() {
        return AmpereOverlayManager.get().isAnyTextFieldFocused();
    }

    private boolean isBindPressed(int bindCode) {
        return ampere.util.AmpereBindUtil.isBindPressed(MC, bindCode);
    }

    private void restoreSavedScreen() {
        AmpereSharedState shared = AmpereSharedState.get();
        if (MC == null) return;

        if (shared.getStoredScreen() == null || shared.getStoredAbstractContainerMenu() == null) {
            AmpereNotifications.error("No stored GUI.");
            return;
        }

        MC.setScreen(shared.getStoredScreen());
        AbstractContainerMenu handler = shared.getStoredAbstractContainerMenu();
        if (MC.player != null) MC.player.containerMenu = handler;
        AmpereNotifications.show("GUI restored.", 0xFF35D873);
    }
}
