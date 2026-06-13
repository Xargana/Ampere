package ampere.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ampere.mixin.accessor.AmpereConnectionAccessor;
import ampere.modules.AmpereModule;
import ampere.modules.PackModuleRegistry;
import ampere.security.AmpereProtectorPackStrip;
import ampere.security.AmpereSpoofPayloadFilter;
import ampere.util.macro.MacroExecutor;
import ampere.util.macro.PacketGateManager;
import ampere.util.AmpereContainerHold;
import ampere.util.AmpereContainerTarget;
import ampere.util.AmpereSharedState;
import ampere.AmpereClientAddon;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.client.Minecraft;

@Mixin(Connection.class)
public abstract class AmpereConnectionMixin {
    @Unique
    private static final boolean Ampere_PACKET_TRACE = Boolean.getBoolean("Ampere.packet.trace");

    @Unique
    private volatile boolean Ampere$spoofPipelineInstalled;

    @Unique
    private static final String Ampere_SPOOF_FILTER = "Ampere_spoof_filter";

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void Ampere$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
        Ampere$spoofPipelineInstalled = false;
        Ampere$ensureSpoofPipelineFilter();
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void Ampere$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
        Ampere$spoofPipelineInstalled = false;
        AmpereProtectorPackStrip.clearAll();
    }

    @Inject(method = "configurePacketHandler", at = @At("TAIL"))
    private void Ampere$onConfigurePacketHandler(ChannelPipeline pipeline, CallbackInfo ci) {
        Ampere$ensureSpoofPipelineFilter();
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void yang$onSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        Ampere$ensureSpoofPipelineFilter();
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
            AmpereProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
        }
        PacketListener packetListener = ((Connection) (Object) this).getPacketListener();
        AmpereModule module = AmpereModule.get();
        AmpereModule.PacketHookSnapshot hooks = module == null
            ? AmpereModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(isPlayConnectionActive());
        boolean normalLoggerPath = hooks.normalPath();
        if (module != null && hooks.passivePayloadCapture() && (!normalLoggerPath || !hooks.packetLoggerCapturing())) {
            module.capturePassivePayloadPacket(packet, "C2S", Ampere$protocolHint(packetListener));
        }
        if (AmpereSpoofPayloadFilter.shouldBlockForVanillaSpoof(module, packet)) {
            ci.cancel();
            return;
        }
        if (AmpereSpoofPayloadFilter.shouldDropForProtector(packet)) {
            ci.cancel();
            return;
        }

        if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketSend(packet);
        }

        if (packet instanceof ServerboundUseItemOnPacket pibp) {
            if (AmpereSharedState.get().consumeBlockCaptureCallback(pibp.getHitResult().getBlockPos(), pibp.getHitResult().getDirection())) {
                ci.cancel();
                return;
            }
        }

        if (!normalLoggerPath) return;

        if (packet instanceof ServerboundUseItemOnPacket pibp) {
            AmpereSharedState.get().setLastInteractedBlockPos(pibp.getHitResult().getBlockPos());
            AmpereSharedState.get().setLastContainerTarget(AmpereContainerTarget.forBlockHit(pibp.getHitResult(), pibp.getHand()));
        }

        if (packet instanceof ServerboundInteractPacket entityPacket) {
            net.minecraft.world.entity.Entity targeted = Minecraft.getInstance().crosshairPickEntity;
            if (targeted != null && targeted != Minecraft.getInstance().player) {
                net.minecraft.world.InteractionHand capturedHand = entityPacket.hand();
                net.minecraft.world.phys.Vec3 capturedHitPos = entityPacket.location();
                AmpereSharedState.get().setLastContainerTarget(
                    capturedHitPos != null
                        ? AmpereContainerTarget.forEntityAt(targeted, capturedHand, capturedHitPos)
                        : AmpereContainerTarget.forEntity(targeted, capturedHand)
                );
            }
        }

        if (packet instanceof ServerboundInteractPacket && AmpereSharedState.get().hasEntityCaptureCallback()) {
            net.minecraft.world.entity.Entity targeted = Minecraft.getInstance().crosshairPickEntity;
            if (targeted != null && targeted != Minecraft.getInstance().player) {
                AmpereSharedState state = AmpereSharedState.get();
                String payload;
                if (state.isEntityCaptureSpecific()) {

                    String uuid = targeted.getStringUUID();
                    String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(targeted.getType()).toString();
                    String dispName = targeted.getDisplayName().getString().replaceAll("\u00a7.", "").trim();
                    payload = "~" + uuid + "~" + typeId + "~" + dispName;
                    state.setEntityCaptureSpecific(false);
                } else {
                    payload = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(targeted.getType()).toString();
                }
                state.consumeEntityCaptureCallback(payload);
            }
        }

        AmpereSharedState shared = AmpereSharedState.get();

        if (packet instanceof ServerboundContainerClosePacket closeForHold) {
            if (shared.consumeSuppressNextContainerClosePacket()) {
                ci.cancel();
                return;
            }
            if (AmpereContainerHold.isHeld(closeForHold.getContainerId())) {
                AmpereContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ServerboundSignUpdatePacket && shared.consumeSuppressNextSignUpdatePacket()) {
            ci.cancel();
            return;
        }

        if (packet instanceof ServerboundEditBookPacket && shared.consumeSuppressNextBookEditPacket()) {
            ci.cancel();
            return;
        }

        boolean forceBookOrSignPacket =
            packet instanceof ServerboundSignUpdatePacket && shared.consumeForceNextSignUpdatePacket()
                || packet instanceof ServerboundEditBookPacket && shared.consumeForceNextBookEditPacket();

        if (packet instanceof ServerboundSignUpdatePacket && !shared.shouldEditSigns()) {
            shared.setAllowSignEditing(true);
            if (!forceBookOrSignPacket) {
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ServerboundEditBookPacket && !shared.shouldUpdateBook()) {
            shared.setAllowBookUpdate(true);
            if (!forceBookOrSignPacket) {
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ServerboundContainerClosePacket closePacket && Ampere$shouldKeepXCarryOpen(shared, closePacket)) {
            ci.cancel();
            return;
        }

        if (shared.isGBreakCapturing()) {
            if (packet instanceof ServerboundPlayerActionPacket) {

                shared.onGBreakPacket(packet);
            }

            return;
        }

        if (packet instanceof ServerboundPlayerActionPacket actionPacket
            && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            if (PackModuleRegistry.hasStartBreakingHooks()) {
                for (ampere.modules.PackModule moduleEntry : PackModuleRegistry.startBreakingModulesForDispatch()) {
                    if (moduleEntry.shouldCancelStartBreakingBlock(actionPacket.getPos(), actionPacket.getDirection())) {
                        ci.cancel();
                        return;
                    }
                    moduleEntry.onStartBreakingBlock(actionPacket.getPos(), actionPacket.getDirection());
                }
            }
        }

        if (module.handlePacketSend(packet)) {
            ci.cancel();
            return;
        }

        if (forceBookOrSignPacket) return;

        if (!shared.isFlushing()) {
            PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "C2S");
            if (gateResult == PacketGateManager.Result.CANCEL) {
                ci.cancel();
                return;
            }
            if (gateResult == PacketGateManager.Result.DELAY) {
                shared.enqueuePacket(packet);
                ci.cancel();
                return;
            }
        }

        boolean anyFeatureActive = shared.shouldDelayGuiPackets()
            || !shared.shouldSendGuiPackets()
            || shared.shouldUseCustomPackets();
        if (!anyFeatureActive) return;

        if (shared.isFlushing()) return;

        boolean shouldHandle = false;

        if (shared.shouldUseCustomPackets()) {

            shouldHandle = shared.getC2SPackets().contains(packet.getClass());
        } else {

            shouldHandle = isGuiPacket(packet);
        }

        if (!shouldHandle) return;

        if (Ampere_PACKET_TRACE) {
            AmpereClientAddon.LOG.debug("[Ampere] Packet detected: {} | Send={} Delay={} | Custom={}",
                packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(),
                shared.shouldDelayGuiPackets(), shared.shouldUseCustomPackets());
        }

        if (!shared.shouldSendGuiPackets()) {
            if (Ampere_PACKET_TRACE) AmpereClientAddon.LOG.debug("[Ampere] CANCELLED packet (send disabled)");
            ci.cancel();
            return;
        }

        if (shared.shouldDelayGuiPackets()) {
            if (Ampere_PACKET_TRACE) AmpereClientAddon.LOG.debug("[Ampere] QUEUED packet (delay enabled)");
            AmpereModule captureModule = AmpereModule.get();
            AmpereSharedState.ReplayMode captureMode = (captureModule != null && captureModule.isCaptureAsExact())
                ? AmpereSharedState.ReplayMode.EXACT
                : AmpereSharedState.ReplayMode.REGENERATE;
            shared.enqueuePacket(packet, captureMode);
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("TAIL"))
    private void yang$afterSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (!isAmpereActive()) return;
        if (!isPlayConnectionActive()) return;
        MacroExecutor.onPacketSent(packet);
    }

    @Unique
    private boolean isGuiPacket(Packet<?> packet) {
        return packet instanceof ServerboundContainerClickPacket
            || packet instanceof ServerboundContainerButtonClickPacket
            || packet instanceof ServerboundSetCreativeModeSlotPacket
            || packet instanceof ServerboundPlayerActionPacket
            || packet instanceof ServerboundUseItemPacket
            || packet instanceof ServerboundSignUpdatePacket
            || packet instanceof ServerboundEditBookPacket
            || packet instanceof ServerboundChatPacket
            || packet instanceof ServerboundChatCommandPacket
            || packet instanceof ServerboundChatCommandSignedPacket;
    }

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void yang$onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketListener listener = ((Connection) (Object) this).getPacketListener();
        AmpereModule module = AmpereModule.get();
        AmpereModule.PacketHookSnapshot hooks = module == null
            ? AmpereModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(isPlayReceiveListener(listener));
        boolean normalLoggerPath = hooks.normalPath();
        if (module != null && hooks.passivePayloadCapture() && (!normalLoggerPath || !hooks.packetLoggerCapturing())) {
            module.capturePassivePayloadPacket(packet, "S2C", Ampere$protocolHint(listener));
        }
        if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketReceive(packet);
        }
        if (!normalLoggerPath) return;

        ampere.util.macro.ServerTickTracker.onS2CPacket(packet);
        MacroExecutor.onPacketReceived(packet);

        if (packet instanceof ClientboundOpenScreenPacket openScreenPacket) {
            AmpereContainerHold.onContainerOpened(openScreenPacket.getContainerId());
        }
        if (packet instanceof ClientboundDisconnectPacket) {
            AmpereContainerHold.clearAll();
            PacketGateManager.clearAll();

            AmpereSharedState s = AmpereSharedState.get();
            s.setXCarryForcedTargets(java.util.Collections.emptySet(), false);
            s.setXCarryForced(false);
            s.setXCarryActive(false);
        }

        if (module.handlePacketReceive(packet)) {
            ci.cancel();
            return;
        }

        AmpereSharedState shared = AmpereSharedState.get();

        PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "S2C");
        if (gateResult == PacketGateManager.Result.CANCEL) {
            ci.cancel();
            return;
        }
        if (gateResult == PacketGateManager.Result.DELAY) {
            shared.enqueuePacket(packet);
            ci.cancel();
            return;
        }

        boolean anyFeatureActive = shared.shouldDelayGuiPackets()
            || !shared.shouldSendGuiPackets()
            || shared.shouldUseCustomPackets();
        if (!anyFeatureActive) return;

        boolean shouldHandle = false;

        if (shared.shouldUseCustomPackets()) {

            shouldHandle = shared.getS2CPackets().contains(packet.getClass());
        }

        if (!shouldHandle) return;

        if (Ampere_PACKET_TRACE) {
            AmpereClientAddon.LOG.debug("[Ampere] S2C Packet detected: {} | Send={} Delay={}",
                packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(),
                shared.shouldDelayGuiPackets());
        }

        if (!shared.shouldSendGuiPackets()) {
            ci.cancel();
            return;
        }

        if (shared.shouldDelayGuiPackets()) {
            shared.enqueuePacket(packet);
            ci.cancel();
        }
    }

    @Unique
    private boolean isAmpereActive() {
        AmpereModule module = AmpereModule.get();
        return module != null && module.arePacketHooksActive();
    }

    @Unique
    private void Ampere$ensureSpoofPipelineFilter() {
        if (Ampere$spoofPipelineInstalled) return;
        Channel channel = null;
        try {
            channel = ((AmpereConnectionAccessor) this).getChannel();
        } catch (Throwable ignored) {
        }
        if (channel == null) return;

        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline == null || pipeline.get(Ampere_SPOOF_FILTER) != null) {
                Ampere$spoofPipelineInstalled = true;
                return;
            }
            if (pipeline.get("encoder") != null) {
                pipeline.addAfter("encoder", Ampere_SPOOF_FILTER, new AmpereSpoofPayloadFilter());
                Ampere$spoofPipelineInstalled = true;
            }
        } catch (Throwable t) {
            AmpereClientAddon.LOG.debug("[Ampere] Failed to install client spoof payload filter", t);
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void Ampere$onSendPacketWithFlush(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        Ampere$ensureSpoofPipelineFilter();
        if (AmpereSpoofPayloadFilter.shouldBlockForVanillaSpoof(AmpereModule.get(), packet)) {
            ci.cancel();
            return;
        }
        if (AmpereSpoofPayloadFilter.shouldDropForProtector(packet)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean isPlayConnectionActive() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getConnection() != null;
    }

    @Unique
    private static boolean isPlayReceiveListener(PacketListener listener) {
        return listener instanceof ClientGamePacketListener;
    }

    @Unique
    private static String Ampere$protocolHint(PacketListener listener) {
        if (listener == null) return "";
        if (listener instanceof ClientGamePacketListener) return "play";
        String name = listener.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("configuration")) return "configuration";
        return "";
    }

    @Unique
    private boolean Ampere$shouldKeepXCarryOpen(AmpereSharedState shared, ServerboundContainerClosePacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return false;
        AmpereModule module = AmpereModule.get();
        boolean allowPassiveXCarry = module != null && module.isXCarryEnabled();

        if (!allowPassiveXCarry && !shared.isXCarryForced()) return false;
        if (packet.getContainerId() != client.player.inventoryMenu.containerId) return false;

        java.util.Set<Integer> mask;
        boolean carryCursor;
        if (shared.isXCarryForced()) {
            mask = shared.getXCarryForcedSlotMask();
            carryCursor = shared.isXCarryForcedCarryCursor();
        } else {
            mask = module == null ? null : module.getXCarryModuleSlotMask();
            carryCursor = module == null || module.isXCarryCarryCursor();
        }
        boolean hasItems = ampere.util.macro.XCarryAction.hasStoredItems(
                client.player.inventoryMenu, carryCursor, mask);
        shared.setXCarryActive(hasItems);

        return true;
    }
}
