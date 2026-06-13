package ampere.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ampere.modules.AmpereModule;
import ampere.modules.InventoryTweaksModule;
import ampere.modules.NameCensorModule;
import ampere.gui.vanillaui.UiBounds;
import ampere.gui.vanillaui.UiContexts;
import ampere.gui.vanillaui.UiTextRenderer;
import ampere.gui.vanillaui.components.Banner;
import ampere.util.AmpereCustomFilterOverlay;
import ampere.util.AmpereCustomFilterPresetOverlay;
import ampere.util.AmpereFabricatorOverlay;
import ampere.util.AmpereLANSync;
import ampere.util.AmpereLANSyncOverlay;
import ampere.util.AmpereLauncherOverlay;
import ampere.util.AmpereMacroListOverlay;
import ampere.util.AmpereQueueEditorOverlay;
import ampere.util.AmpereInventoryMoveHelper;
import ampere.util.AmpereOverlayManager;
import ampere.util.AmpereMacroEditorOverlay;
import ampere.util.AmpereItemNbtInspectOverlay;
import ampere.util.AmperePacketLoggerOverlay;
import ampere.util.AmpereUiScale;

import ampere.util.AmpereSharedState;
import ampere.util.AmpereCursorClickHelper;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Shadow;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

@Mixin(AbstractContainerScreen.class)
public abstract class AmpereHandledScreenMixin<T extends AbstractContainerMenu> extends Screen {
    @Shadow @Nullable protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput actionType);
    @Unique private static final Minecraft MC = Minecraft.getInstance();
    @Unique private Slot Ampere$blockedFocusedSlot;

    @Unique private AmpereLauncherOverlay launcherOverlay;
    @Unique private AmpereFabricatorOverlay fabricatorOverlay;
    @Unique private AmpereLANSyncOverlay lanSyncOverlay;
    @Unique private AmpereMacroListOverlay macroListOverlay;
    @Unique private AmpereQueueEditorOverlay queueEditorOverlay;
    @Unique private AmperePacketLoggerOverlay packetLoggerOverlay;
    @Unique private AmpereCustomFilterOverlay customFilterOverlay;
    @Unique private AmpereCustomFilterPresetOverlay customFilterPresetOverlay;
    @Unique private AmpereMacroEditorOverlay macroEditorOverlay;
    @Unique private AmpereItemNbtInspectOverlay itemNbtInspectOverlay;
    @Unique private ampere.util.AmpereKeybindOverlay keybindOverlay;
    @Unique private ampere.util.AmpereServerInfoOverlay serverInfoOverlay;
    @Unique private Button inventoryTweaksStealButton;
    @Unique private Button inventoryTweaksDumpButton;
    @Unique private int inventoryTweaksLastShiftDragSlot = -1;
    @Unique private ItemStack Ampere$cursorClickBeforeCarried = ItemStack.EMPTY;
    @Unique private ItemStack Ampere$cursorClickBeforeSlot = ItemStack.EMPTY;
    @Unique private int Ampere$cursorClickBeforeSlotId = -1;
    @Unique private int Ampere$cursorClickBeforeButton = 0;
    @Unique private ContainerInput Ampere$cursorClickBeforeInput = null;

    protected AmpereHandledScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void Ampere$syncInvMoveOnInit(CallbackInfo ci) {
        if (!isAmpereActive()) return;
        AmpereInventoryMoveHelper.syncHeldMovementKeysIfSafe();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void Ampere$syncInvMoveOnTick(CallbackInfo ci) {
        if (!isAmpereActive()) return;
        AmpereInventoryMoveHelper.syncHeldMovementKeysIfSafe();
    }

    @ModifyArg(
        method = "extractLabels",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
        index = 1,
        require = 0
    )
    private Component Ampere$censorContainerLabel(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yang$init(CallbackInfo ci) {

        if (!isAmpereActive()) return;

        AmpereLANSync.getInstance().setOnSessionStateChanged(() -> {});

        AbstractContainerScreen<?> handledScreen = (AbstractContainerScreen<?>) (Object) this;
        fabricatorOverlay = AmpereFabricatorOverlay.getSharedOverlay(handledScreen);
        lanSyncOverlay = AmpereLANSyncOverlay.getSharedOverlay(this.font);
        macroListOverlay = new AmpereMacroListOverlay(this.font);
        queueEditorOverlay = new AmpereQueueEditorOverlay(this.font);
        customFilterOverlay = new AmpereCustomFilterOverlay(this.font);
        customFilterPresetOverlay = customFilterOverlay.getPresetManagerOverlay();
        fabricatorOverlay.restoreState();
        lanSyncOverlay.restoreState();
        macroListOverlay.restoreState();
        queueEditorOverlay.restoreState();
        customFilterOverlay.restoreLayout();
        if (customFilterPresetOverlay != null) {
            customFilterPresetOverlay.restoreLayout();
        }

        macroEditorOverlay = AmpereMacroEditorOverlay.getSharedOverlay();
        if (macroEditorOverlay != null) {
            macroEditorOverlay.restoreState();
        }

        AmpereOverlayManager manager = AmpereOverlayManager.get();
        manager.clear();
        manager.register(fabricatorOverlay);
        manager.register(lanSyncOverlay);
        manager.register(macroListOverlay);
        manager.register(queueEditorOverlay);
        manager.register(customFilterOverlay);
        if (customFilterPresetOverlay != null) {
            manager.register(customFilterPresetOverlay);
        }
        if (macroEditorOverlay != null) {
            manager.register(macroEditorOverlay);
        }

        manager.register(ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());
        itemNbtInspectOverlay = AmpereItemNbtInspectOverlay.getSharedOverlay(this.font);
        if (itemNbtInspectOverlay != null) manager.register(itemNbtInspectOverlay);

        AmpereModule ampereModule = AmpereModule.get();
        keybindOverlay = new ampere.util.AmpereKeybindOverlay();
        keybindOverlay.restoreLayout();
        manager.register(keybindOverlay);

        launcherOverlay = new AmpereLauncherOverlay(macroListOverlay, fabricatorOverlay, lanSyncOverlay, queueEditorOverlay, packetLoggerOverlay, customFilterOverlay);
        launcherOverlay.setKeybindOverlay(keybindOverlay);
        launcherOverlay.setPacketLoggerOverlaySupplier(() -> {
            if (packetLoggerOverlay == null && ampereModule != null) {
                packetLoggerOverlay = ampereModule.getPacketLoggerOverlay();
                if (packetLoggerOverlay != null) packetLoggerOverlay.restoreLayout();
            }
            if (packetLoggerOverlay != null) manager.register(packetLoggerOverlay);
            return packetLoggerOverlay;
        });
        launcherOverlay.setServerDataOverlaySupplier(() -> {
            if (serverInfoOverlay == null) {
                serverInfoOverlay = ampere.modules.AmpereModule.get().getServerDataOverlay();
            }
            if (serverInfoOverlay != null) manager.register(serverInfoOverlay);
            return serverInfoOverlay;
        });
        if (serverInfoOverlay == null && ampere.util.AmpereServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = ampere.modules.AmpereModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }
        launcherOverlay.restoreLayout();
        manager.register(launcherOverlay);

        inventoryTweaksStealButton = Button.builder(Component.literal("Steal"), button -> InventoryTweaksModule.stealFromButton())
                .bounds(leftPos, topPos - 22, 40, 20)
                .build();
        inventoryTweaksDumpButton = Button.builder(Component.literal("Dump"), button -> InventoryTweaksModule.dumpFromButton())
                .bounds(leftPos + 42, topPos - 22, 40, 20)
                .build();
        addRenderableWidget(inventoryTweaksStealButton);
        addRenderableWidget(inventoryTweaksDumpButton);

        Screen screen = (Screen)(Object)this;
        ScreenEvents.afterExtract(screen).register((scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (isAmpereActive()) {
                AmpereOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
                AmpereUiScale.pushOverlayScale(drawContext);
                try {
                    renderMacroCaptureBanner(drawContext);
                } finally {
                    AmpereUiScale.popOverlayScale(drawContext);
                }
            }
        });

        refreshButtonVisibility();
    }

    @Unique private boolean coreExpanded = true;
    @Unique private boolean queueExpanded = false;
    @Unique private boolean toolsExpanded = false;
    @Unique private int coreButtonsStartY, queueHeaderY, queueButtonsStartY, queueButtonsEndY;
    @Unique private int toolsHeaderY, toolsButtonsStartY, toolsButtonsEndY;

    @Unique
    private void refreshButtonVisibility() {
        boolean visible = isAmpereActive() && MC != null && MC.player != null
                && InventoryTweaksModule.shouldShowButtons(MC.player.containerMenu);
        if (inventoryTweaksStealButton != null) {
            inventoryTweaksStealButton.visible = visible;
            inventoryTweaksStealButton.active = visible;
            inventoryTweaksStealButton.setX(leftPos);
            inventoryTweaksStealButton.setY(topPos - 22);
        }
        if (inventoryTweaksDumpButton != null) {
            inventoryTweaksDumpButton.visible = visible;
            inventoryTweaksDumpButton.active = visible;
            inventoryTweaksDumpButton.setX(leftPos + 42);
            inventoryTweaksDumpButton.setY(topPos - 22);
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void yang$blockCoveredSlotHover(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!isAmpereActive()) return;

        if (AmpereOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            Ampere$blockedFocusedSlot = hoveredSlot;
            hoveredSlot = null;
        } else {
            Ampere$blockedFocusedSlot = null;
        }
    }

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$blockCoveredSlotLookup(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
        if (!isAmpereActive()) return;

        if (AmpereOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$blockCoveredSlotHitbox(Slot slot, double pointX, double pointY, CallbackInfoReturnable<Boolean> cir) {
        if (!isAmpereActive()) return;

        if (AmpereOverlayManager.get().shouldBlockUnderlyingHover(pointX, pointY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void Ampere$blockCoveredHandledTooltip(GuiGraphicsExtractor context, int x, int y, CallbackInfo ci) {
        if (!isAmpereActive()) return;

        if (AmpereOverlayManager.get().shouldBlockUnderlyingHover(x, y)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void yang$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Ampere$blockedFocusedSlot != null) {
            hoveredSlot = Ampere$blockedFocusedSlot;
            Ampere$blockedFocusedSlot = null;
        }
        refreshButtonVisibility();
    }

    @Unique
    private void renderMacroCaptureBanner(GuiGraphicsExtractor context) {
        ampere.gui.macro.editor.ActionEditorOverlay actionEditor =
                ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        ampere.util.AmpereAdminToolsOverlay adminToolsOverlay =
                ampere.util.AmpereAdminToolsOverlay.getSharedOverlay();
        boolean macroCapture = macroEditorOverlay != null && macroEditorOverlay.shouldRenderAbstractContainerScreenCaptureBanner();
        boolean actionCapture = actionEditor != null && actionEditor.shouldRenderAbstractContainerScreenCaptureBanner();
        boolean adminCapture = adminToolsOverlay != null && adminToolsOverlay.shouldRenderAbstractContainerScreenCaptureBanner();
        if (!macroCapture && !actionCapture && !adminCapture) return;
        if (MC == null || MC.getWindow() == null || this.font == null) return;

        String title = macroCapture
                ? macroEditorOverlay.getAbstractContainerScreenCaptureTitle()
                : actionCapture
                ? actionEditor.getAbstractContainerScreenCaptureTitle()
                : adminToolsOverlay.getAbstractContainerScreenCaptureTitle();
        String instruction = macroCapture
                ? macroEditorOverlay.getAbstractContainerScreenCaptureInstruction()
                : actionCapture
                ? actionEditor.getAbstractContainerScreenCaptureInstruction()
                : adminToolsOverlay.getAbstractContainerScreenCaptureInstruction();
        String hover = "";

        if (hoveredSlot != null) {
            net.minecraft.world.item.ItemStack stack = hoveredSlot.getItem();
            String itemName = stack.isEmpty() ? "" : stack.getHoverName().getString();
            String registryId = stack.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            hover = macroCapture
                    ? macroEditorOverlay.getAbstractContainerScreenCaptureHoverText(hoveredSlot, itemName, registryId)
                    : actionCapture
                    ? actionEditor.getAbstractContainerScreenCaptureHoverText(hoveredSlot, itemName, registryId)
                    : adminToolsOverlay.getAbstractContainerScreenCaptureHoverText(hoveredSlot, itemName, registryId);
        }

        UiTextRenderer text = UiContexts.textRenderer(this.font);
        int maxTextWidth = Math.max(text.width(title), text.width(instruction));
        if (!hover.isEmpty()) {
            maxTextWidth = Math.max(maxTextWidth, text.width(hover));
        }

        int screenWidth = AmpereUiScale.getVirtualScreenWidth();
        int boxWidth = Math.min(screenWidth - 16, Math.max(250, maxTextWidth + 18));
        int boxX = (screenWidth - boxWidth) / 2;
        int boxY = 0;
        var uiContext = UiContexts.overlay(context, this.font, 0, 0);
        int bannerHeight = Banner.height(uiContext, boxWidth, instruction, hover);
        Banner.render(uiContext, UiBounds.of(boxX, boxY, boxWidth, bannerHeight),
            title, instruction, hover);
        if (actionCapture && actionEditor != null && actionEditor.hasAbstractContainerScreenCaptureToasts()) {
            actionEditor.renderAbstractContainerScreenCaptureToasts(context, boxX, boxY + bannerHeight + 6, boxWidth);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void yang$removed(CallbackInfo ci) {
        if (!isAmpereActive()) return;
        AmpereInventoryMoveHelper.releaseMovementKeysIfSafe();

        ampere.gui.macro.editor.ActionEditorOverlay actionEditor =
                ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        boolean skipTransientCaptureSave = actionEditor != null && actionEditor.hasActiveCaptureSession();

        if (!skipTransientCaptureSave) {
            if (fabricatorOverlay != null) {
                fabricatorOverlay.saveState();
            }
            if (lanSyncOverlay != null) {
                lanSyncOverlay.saveState();
            }
            if (macroListOverlay != null) {
                macroListOverlay.saveState();
            }
            if (queueEditorOverlay != null) {
                queueEditorOverlay.saveState();
            }
            if (macroEditorOverlay != null) {
                macroEditorOverlay.saveState();
            }
            if (launcherOverlay != null) {
                launcherOverlay.saveLayout();
            }
            if (packetLoggerOverlay != null) {
                packetLoggerOverlay.saveLayout();
            }
            if (customFilterOverlay != null) {
                customFilterOverlay.saveLayout();
            }
            if (customFilterPresetOverlay != null) {
                customFilterPresetOverlay.saveLayout();
            }
            if (keybindOverlay != null) {
                keybindOverlay.saveLayout();
            }
            if (serverInfoOverlay != null) {
                serverInfoOverlay.saveState();
            }
        }

        AmpereOverlayManager.get().clear();
    }

    @Unique
    private boolean isAmpereActive() {
        AmpereModule module = AmpereModule.get();
        return module != null && module.isActive();
    }

    @Unique
    private void updateButtonLabels() {
    }

    @Unique
    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    @Unique
    private static String stateText(boolean value) {
        return value ? "enabled" : "disabled";
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void yang$mouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!isAmpereActive()) return;

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        AmpereOverlayManager manager = AmpereOverlayManager.get();

        if (manager.handleMouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (InventoryTweaksModule.handleSortMouse(button, hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (button == 1 && hoveredSlot != null && click.hasControlDown() && click.hasShiftDown()) {
            net.minecraft.world.item.ItemStack stack = hoveredSlot.getItem();
            if (!stack.isEmpty()) {
                if (itemNbtInspectOverlay == null) {
                    itemNbtInspectOverlay = new AmpereItemNbtInspectOverlay(this.font);
                    manager.register(itemNbtInspectOverlay);
                }
                itemNbtInspectOverlay.open(stack, (int) Math.round(mouseX + 8), (int) Math.round(mouseY + 8));
                cir.setReturnValue(true);
                return;
            }
        }

        if (button == 1 && hoveredSlot != null) {
            net.minecraft.world.item.ItemStack captureStack = hoveredSlot.getItem();
            String captureItemName   = captureStack.isEmpty() ? "" : captureStack.getHoverName().getString();
            String captureRegistryId = captureStack.isEmpty() ? "" :
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(captureStack.getItem()).toString();

            AmpereMacroEditorOverlay editor = macroEditorOverlay;
            if (editor != null && editor.wantsSlotCapture()
                    && editor.onSlotRightClick(hoveredSlot, captureItemName, captureRegistryId)) {
                cir.setReturnValue(true);
                return;
            }

            ampere.gui.macro.editor.ActionEditorOverlay actionEditor =
                    ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.wantsItemSlotCapture()
                    && actionEditor.onInventorySlotCapture(hoveredSlot, captureItemName, captureRegistryId)) {
                cir.setReturnValue(true);
                return;
            }

            ampere.util.AmpereAdminToolsOverlay adminToolsOverlay =
                    ampere.util.AmpereAdminToolsOverlay.getSharedOverlay();
            if (adminToolsOverlay != null && adminToolsOverlay.wantsItemStackCapture()
                    && adminToolsOverlay.onInventoryItemStackCapture(hoveredSlot)) {
                cir.setReturnValue(true);
                return;
            }

            if (ampere.util.AmpereContainerHold.hasPendingCapture()) {
                ampere.util.AmperePacketClick.Target captured =
                        Ampere$buildPacketClickTarget(hoveredSlot, captureItemName);
                if (captured != null && ampere.util.AmpereContainerHold.deliverCapture(captured)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (fabricatorOverlay != null && fabricatorOverlay.isVisible() && button == 1 && hoveredSlot != null) {
            fabricatorOverlay.onSlotClick(hoveredSlot, button);
            cir.setReturnValue(true);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private ampere.util.AmperePacketClick.Target Ampere$buildPacketClickTarget(
            net.minecraft.world.inventory.Slot slot, String itemName) {
        if (MC == null || MC.player == null || slot == null) return null;
        net.minecraft.world.inventory.AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler == null) return null;
        net.minecraft.client.gui.screens.Screen screen = MC.screen;
        String screenTitle = (screen != null && screen.getTitle() != null) ? screen.getTitle().getString() : "";
        String menuClass = handler.getClass().getName();
        int visibleSlot = ampere.util.AmpereInventoryHelper.toUserVisibleSlot(MC, slot.index);
        return new ampere.util.AmperePacketClick.Target(
                handler.containerId,
                handler.getStateId(),
                slot.index,
                visibleSlot,
                screenTitle,
                menuClass,
                itemName == null ? "" : itemName,
                ampere.util.AmperePacketClick.Mode.RIGHT_CLICK,
                System.currentTimeMillis()
        );
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void yang$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!isAmpereActive()) return;

        boolean inventoryKey = MC != null && MC.options != null && MC.options.keyInventory.matches(input);
        if (inventoryKey) {
            if (AmpereSharedState.get().consumeCaptureCancelCallback()) {
                cir.setReturnValue(true);
                return;
            }

            ampere.gui.macro.editor.ActionEditorOverlay actionEditor =
                    ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
                cir.setReturnValue(true);
                return;
            }
        }

        if (AmpereOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
            return;
        }

        if (InventoryTweaksModule.handleSortKey(input.key(), hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (AmpereSharedState.get().consumeCaptureCancelCallback()) {
                cir.setReturnValue(true);
                return;
            }

            ampere.gui.macro.editor.ActionEditorOverlay actionEditor =
                    ampere.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
                cir.setReturnValue(true);
            }
        }

        if (AmpereInventoryMoveHelper.handleKeyEvent(input, true)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void yang$keyReleased(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!isAmpereActive()) return;

        if (AmpereInventoryMoveHelper.handleKeyEvent(input, false)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void yang$mouseReleased(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (!isAmpereActive()) return;
        inventoryTweaksLastShiftDragSlot = -1;

        if (AmpereOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), require = 0)
    private void Ampere$captureCursorClickOriginBefore(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        Ampere$cursorClickBeforeCarried = ItemStack.EMPTY;
        Ampere$cursorClickBeforeSlot = ItemStack.EMPTY;
        Ampere$cursorClickBeforeSlotId = slotId;
        Ampere$cursorClickBeforeButton = button;
        Ampere$cursorClickBeforeInput = actionType;
        if (MC.player == null || MC.player.containerMenu == null) return;
        AbstractContainerMenu handler = MC.player.containerMenu;
        Ampere$cursorClickBeforeCarried = handler.getCarried().copy();
        if (slotId >= 0 && slotId < handler.slots.size()) {
            Ampere$cursorClickBeforeSlot = handler.slots.get(slotId).getItem().copy();
        }
    }

    @Inject(method = "slotClicked", at = @At("TAIL"), require = 0)
    private void Ampere$captureCursorClickOriginAfter(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        if (MC.player == null || MC.player.containerMenu == null) return;
        if (slotId != Ampere$cursorClickBeforeSlotId || button != Ampere$cursorClickBeforeButton || actionType != Ampere$cursorClickBeforeInput) return;
        AmpereCursorClickHelper.recordAfterContainerClick(
                MC,
                MC.player.containerMenu,
                slotId,
                button,
                actionType,
                Ampere$cursorClickBeforeCarried,
                Ampere$cursorClickBeforeSlot
        );
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void yang$mouseDragged(MouseButtonEvent click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!isAmpereActive()) return;

        if (AmpereOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
            return;
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && click.hasShiftDown()
                && hoveredSlot != null && !hoveredSlot.getItem().isEmpty()
                && InventoryTweaksModule.shouldShiftDragMove()
                && inventoryTweaksLastShiftDragSlot != hoveredSlot.index) {
            inventoryTweaksLastShiftDragSlot = hoveredSlot.index;
            slotClicked(hoveredSlot, hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void yang$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (!isAmpereActive()) return;

        if (AmpereOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
        if (!isAmpereActive()) return super.charTyped(input);

        if (AmpereOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            return true;
        }

        return super.charTyped(input);
    }
}
