package ampere.util;

import ampere.gui.vanillaui.UiContext;
import ampere.gui.vanillaui.UiContexts;
import ampere.gui.vanillaui.UiInputResult;
import ampere.gui.vanillaui.UiInputRouter;
import ampere.gui.vanillaui.UiLayer;
import ampere.gui.vanillaui.UiLayerManager;
import ampere.gui.vanillaui.UiScissorStack;
import ampere.gui.vanillaui.UiTextRenderer;
import ampere.gui.vanillaui.UiTheme;
import ampere.gui.vanillaui.components.OperationalOverlayComponent;
import ampere.gui.screen.AmpereModuleScreen;
import ampere.gui.screen.AmpereOverlayHostScreen;
import ampere.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class AmpereOverlayManager {
    private static final AmpereOverlayManager INSTANCE = new AmpereOverlayManager();
    public static final int HOVER_BLOCKED_MOUSE = -10000;
    private static final double HEADER_CLICK_DRAG_THRESHOLD = 3.0;

    private final List<IAmpereOverlay> overlays = new CopyOnWriteArrayList<>();
    private final List<IAmpereOverlay> renderOverlays = new ArrayList<>();
    private final Map<IAmpereOverlay, OperationalOverlayComponent> overlayComponents = new IdentityHashMap<>();
    private final Map<IAmpereOverlay, IAmpereOverlay.OverlayScope> overlayScopes = new IdentityHashMap<>();
    private final Set<String> temporarilyHiddenOverlayIds = new HashSet<>();
    private final UiTheme uiTheme = new UiTheme();
    private final UiLayerManager overlayLayers = new UiLayerManager();
    private final UiInputRouter overlayInput = new UiInputRouter(overlayLayers);
    private UiTextRenderer uiText;
    private boolean overlayLayersDirty = true;
    private IAmpereOverlay focusedOverlay = null;
    private double cachedHoverBlockMouseX = Double.NaN;
    private double cachedHoverBlockMouseY = Double.NaN;
    private long cachedHoverBlockNanos;
    private boolean cachedHoverBlockResult;

    private AmpereOverlayManager() {}

    public static AmpereOverlayManager get() {
        return INSTANCE;
    }

    public List<IAmpereOverlay> getOverlays() {
        return overlays;
    }

    public boolean hasRegisteredOverlays() {
        return !overlays.isEmpty();
    }

    public boolean hasVisibleOverlay() {
        if (PackHideState.isActive()) return false;
        for (IAmpereOverlay overlay : overlays) {
            if (isOverlayInteractive(overlay)) return true;
        }
        return false;
    }

    public void register(IAmpereOverlay overlay) {
        register(overlay, inferScopeForCurrentScreen(overlay));
    }

    public void register(IAmpereOverlay overlay, IAmpereOverlay.OverlayScope scope) {
        if (overlay == null) return;
        if (!overlays.contains(overlay)) {
            overlays.add(overlay);
        }
        overlayScopes.put(overlay, scope == null ? overlay.getDefaultOverlayScope() : scope);
        overlayComponents.computeIfAbsent(overlay, OperationalOverlayComponent::new);
        overlayLayersDirty = true;
        invalidateHoverBlockCache();
        restoreSavedOverlayOrder();
        normalizeOverlayStack();
        if (overlay instanceof AmpereLauncherOverlay || "Ampere-launcher".equals(overlay.getOverlayId())) {
            reclampAllOverlays();
        }
    }

    public void unregister(IAmpereOverlay overlay) {
        if (overlay == null) return;
        overlays.remove(overlay);
        overlayComponents.remove(overlay);
        overlayScopes.remove(overlay);
        overlayLayersDirty = true;
        temporarilyHiddenOverlayIds.remove(overlay.getOverlayId());
        if (focusedOverlay == overlay) focusedOverlay = null;
        invalidateHoverBlockCache();
        saveOverlayOrder();
    }

    public void clear() {
        overlays.clear();
        overlayComponents.clear();
        overlayScopes.clear();
        overlayLayers.clear();
        overlayLayersDirty = false;
        temporarilyHiddenOverlayIds.clear();
        draggingOverlay = null;
        resizingOverlay = null;
        headerCollapseOverlay = null;
        focusedOverlay = null;
        headerCollapseMoved = false;
        headerCollapseStartBounds = null;
        resizeStartBounds = null;
        inventoryMouseDown = false;
        invalidateHoverBlockCache();
    }

    public void setTemporarilyHidden(IAmpereOverlay overlay, boolean hidden) {
        if (overlay == null) return;
        String id = overlay.getOverlayId();
        if (id == null || id.isEmpty()) return;

        if (hidden) {
            temporarilyHiddenOverlayIds.add(id);
            overlay.clearTextFieldFocus();
            if (focusedOverlay == overlay) focusedOverlay = null;
            if (draggingOverlay == overlay) draggingOverlay = null;
            if (resizingOverlay == overlay) resizingOverlay = null;
            if (headerCollapseOverlay == overlay) headerCollapseOverlay = null;
        } else {
            temporarilyHiddenOverlayIds.remove(id);
        }
        invalidateHoverBlockCache();
    }

    public void clearTemporaryHidden() {
        temporarilyHiddenOverlayIds.clear();
        invalidateHoverBlockCache();
    }

    public boolean isTemporarilyHidden(IAmpereOverlay overlay) {
        if (overlay == null) return false;
        String id = overlay.getOverlayId();
        return id != null && temporarilyHiddenOverlayIds.contains(id);
    }

    private boolean isOverlayInteractive(IAmpereOverlay overlay) {
        Minecraft mc = Minecraft.getInstance();
        return isOverlayInteractive(overlay, mc == null ? null : mc.screen);
    }

    private boolean isOverlayInteractive(IAmpereOverlay overlay, Screen screen) {
        return overlay != null
            && overlay.isVisible()
            && !isTemporarilyHidden(overlay)
            && isScopeValid(overlay, screen);
    }

    private IAmpereOverlay.OverlayScope inferScopeForCurrentScreen(IAmpereOverlay overlay) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc == null ? null : mc.screen;
        if (screen instanceof AmpereModuleScreen) return IAmpereOverlay.OverlayScope.MODULE_MENU;
        if (screen instanceof AmpereOverlayHostScreen || screen == null || screen instanceof ChatScreen) {
            return overlay == null ? IAmpereOverlay.OverlayScope.HOST_SCREEN : overlay.getDefaultOverlayScope();
        }
        return IAmpereOverlay.OverlayScope.CONTAINER_GUI;
    }

    private boolean isScopeValid(IAmpereOverlay overlay, Screen screen) {
        IAmpereOverlay.OverlayScope scope = overlayScopes.getOrDefault(
            overlay,
            overlay == null ? IAmpereOverlay.OverlayScope.HOST_SCREEN : overlay.getDefaultOverlayScope()
        );
        if (scope == IAmpereOverlay.OverlayScope.BACKGROUND_STATUS) return true;
        if (screen == null || screen instanceof ChatScreen || screen instanceof InBedChatScreen) return false;
        if (screen instanceof AmpereOverlayHostScreen || screen instanceof AmpereModuleScreen) return true;
        return switch (scope) {
            case BACKGROUND_STATUS -> true;
            case HOST_SCREEN, MODULE_MENU, CONTAINER_GUI -> true;
        };
    }

    public void hideInvalidOverlaysForCurrentScreen() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc == null ? null : mc.screen;
        for (IAmpereOverlay overlay : overlays) {
            if (overlay == null || !overlay.isVisible()) continue;
            if (!isScopeValid(overlay, screen)) {
                overlay.clearTextFieldFocus();
                if (focusedOverlay == overlay) focusedOverlay = null;
                if (draggingOverlay == overlay) draggingOverlay = null;
                if (resizingOverlay == overlay) resizingOverlay = null;
                if (headerCollapseOverlay == overlay) headerCollapseOverlay = null;
            }
        }
        invalidateHoverBlockCache();
    }

    public void hideAllInteractiveOverlays() {
        for (IAmpereOverlay overlay : overlays) {
            if (overlay == null || overlayScopes.getOrDefault(overlay, overlay.getDefaultOverlayScope()) == IAmpereOverlay.OverlayScope.BACKGROUND_STATUS) {
                continue;
            }
            if (overlay.isVisible()) overlay.setVisible(false);
            overlay.clearTextFieldFocus();
        }
        temporarilyHiddenOverlayIds.clear();
        draggingOverlay = null;
        resizingOverlay = null;
        headerCollapseOverlay = null;
        focusedOverlay = null;
        inventoryMouseDown = false;
        invalidateHoverBlockCache();
    }

    public void bringToFront(IAmpereOverlay overlay) {
        if (overlay == null) return;
        overlays.remove(overlay);
        overlays.add(overlay);
        overlayLayersDirty = true;
        focusedOverlay = overlay;
        AmpereSharedState.get().setFocusedOverlayId(overlay.getOverlayId());
        invalidateHoverBlockCache();
        normalizeOverlayStack();
        saveOverlayOrder();
    }

    public void bringToFrontParent(Object childComponent) {
        if (childComponent == null) return;
        for (IAmpereOverlay overlay : overlays) {
            if (overlay instanceof AmpereCustomFilterOverlay filterOverlay
                && filterOverlay.getPacketSelectorOverlay() == childComponent) {
                bringToFront(overlay);
                return;
            }
        }
    }

    private void restoreSavedOverlayOrder() {
        if (overlays.size() < 2) {
            restoreFocusedOverlay();
            normalizeOverlayStack();
            return;
        }

        List<String> savedOrder = AmpereSharedState.get().getOverlayOrder();
        if (savedOrder.isEmpty()) {
            restoreFocusedOverlay();
            normalizeOverlayStack();
            return;
        }

        String focusedId = AmpereSharedState.get().getFocusedOverlayId();

        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < savedOrder.size(); i++) {
            positions.putIfAbsent(savedOrder.get(i), i);
        }

        List<IAmpereOverlay> ordered = new ArrayList<>(overlays);
        ordered.sort(Comparator
            .comparingInt((IAmpereOverlay overlay) -> positions.getOrDefault(overlay.getOverlayId(), Integer.MAX_VALUE))
            .thenComparingInt(overlay -> focusedId.equals(overlay.getOverlayId()) ? 1 : 0));
        overlays.clear();
        overlays.addAll(ordered);
        overlayLayersDirty = true;
        restoreFocusedOverlay();
        normalizeOverlayStack();
    }

    private void restoreFocusedOverlay() {
        String focusedId = AmpereSharedState.get().getFocusedOverlayId();
        if (focusedId.isEmpty()) {
            focusedOverlay = null;
            return;
        }
        focusedOverlay = null;
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAmpereOverlay overlay = overlays.get(i);
            if (overlay != null && focusedId.equals(overlay.getOverlayId()) && isOverlayInteractive(overlay)) {
                focusedOverlay = overlay;
                break;
            }
        }
    }

    private void saveOverlayOrder() {
        List<String> order = new ArrayList<>(overlays.size());
        for (IAmpereOverlay overlay : overlays) {
            String id = overlay.getOverlayId();
            if (id == null || id.isEmpty() || order.contains(id) || isLauncherOverlay(overlay) || isTransientOverlay(overlay)) continue;
            order.add(id);
        }
        AmpereSharedState.get().setOverlayOrder(order);
    }

    private void normalizeOverlayStack() {
        if (overlays.isEmpty()) return;

        List<IAmpereOverlay> launchers = new ArrayList<>();
        List<IAmpereOverlay> others = new ArrayList<>();
        for (IAmpereOverlay overlay : overlays) {
            if (isLauncherOverlay(overlay)) launchers.add(overlay);
            else others.add(overlay);
        }

        if (launchers.isEmpty()) return;

        overlays.clear();
        overlays.addAll(launchers);
        overlays.addAll(others);
        overlayLayersDirty = true;
    }

    private boolean isLauncherOverlay(IAmpereOverlay overlay) {
        return overlay instanceof AmpereLauncherOverlay || (overlay != null && "Ampere-launcher".equals(overlay.getOverlayId()));
    }

    private boolean isTransientOverlay(IAmpereOverlay overlay) {
        return overlay != null && "macro-step-picker".equals(overlay.getOverlayId());
    }

    public void reclampAllOverlays() {
        for (IAmpereOverlay overlay : overlays) {
            if (overlay == null) continue;
            overlay.setBounds(overlay.getBounds());
        }
        invalidateHoverBlockCache();
    }

    private IAmpereOverlay draggingOverlay = null;
    private IAmpereOverlay resizingOverlay = null;
    private IAmpereOverlay headerCollapseOverlay = null;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;
    private boolean headerCollapseMoved = false;

    private boolean inventoryMouseDown = false;
    private AmpereWindowLayout headerCollapseStartBounds = null;
    private AmpereWindowLayout resizeStartBounds = null;
    private double headerCollapseStartMouseX = 0;
    private double headerCollapseStartMouseY = 0;
    private double resizeStartMouseX = 0;
    private double resizeStartMouseY = 0;

    public void renderAll(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (PackHideState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        hideInvalidOverlaysForCurrentScreen();
        if (mc.getWindow() != null) {
            int sw = AmpereUiScale.getVirtualScreenWidth();
            int sh = AmpereUiScale.getVirtualScreenHeight();
            if (sw != lastScreenWidth || sh != lastScreenHeight) {
                lastScreenWidth = sw;
                lastScreenHeight = sh;
                invalidateHoverBlockCache();
                for (IAmpereOverlay overlay : overlays) {
                    overlay.setBounds(overlay.getBounds());
                }
            }
        }

        int virtualMouseX = (int) Math.round(AmpereUiScale.toVirtual(mouseX));
        int virtualMouseY = (int) Math.round(AmpereUiScale.toVirtual(mouseY));
        renderOverlays.clear();
        for (IAmpereOverlay overlay : overlays) {
            if (isOverlayInteractive(overlay)) {
                renderOverlays.add(overlay);
            }
        }
        if (renderOverlays.isEmpty()) {
            if (AmpereNotifications.hasVisible()) {
                AmpereUiScale.pushOverlayScale(context);
                try {
                    context.nextStratum();
                    AmpereNotifications.render(context);
                } finally {
                    AmpereUiScale.popOverlayScale(context);
                }
            }
            return;
        }

        int hoveredOverlayIndex = -1;
        for (int i = renderOverlays.size() - 1; i >= 0; i--) {
            IAmpereOverlay overlay = renderOverlays.get(i);
            if (overlay.isMouseOver(virtualMouseX, virtualMouseY)) {
                hoveredOverlayIndex = i;
                break;
            }
        }
        if (uiText == null || uiText.font() != mc.font) {
            uiText = UiContexts.textRenderer(mc.font);
        }
        UiContext uiContext = new UiContext(context, uiTheme, uiText, lastScreenWidth, lastScreenHeight, virtualMouseX, virtualMouseY, delta);
        int visibleIndex = 0;
        for (IAmpereOverlay overlay : overlays) {
            OperationalOverlayComponent adapter = adapterFor(overlay);
            boolean interactive = isOverlayInteractive(overlay);
            adapter.setRenderSuppressed(!interactive);
            adapter.setInputSuppressed(!interactive);
            adapter.setHoverBlocked(interactive && hoveredOverlayIndex > visibleIndex);
            if (interactive) visibleIndex++;
        }
        rebuildOverlayLayers();
        AmpereUiScale.pushOverlayScale(context);
        try {
            UiScissorStack.global().clear(context);
            overlayLayers.render(uiContext);
            if (AmpereNotifications.hasVisible()) {
                context.nextStratum();
                AmpereNotifications.render(context);
            }
        } finally {
            UiScissorStack.global().clear(context);
            AmpereUiScale.popOverlayScale(context);
            renderOverlays.clear();
        }
    }

    public boolean isMouseOverAnyOverlay(double mouseX, double mouseY) {
        if (PackHideState.isActive()) return false;
        return isMouseOverAnyOverlayVirtual(AmpereUiScale.toVirtual(mouseX), AmpereUiScale.toVirtual(mouseY));
    }

    private boolean isMouseOverAnyOverlayVirtual(double mouseX, double mouseY) {
        for (IAmpereOverlay overlay : overlays) {
            if (isOverlayInteractive(overlay) && overlay.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldBlockUnderlyingHover(double mouseX, double mouseY) {
        if (PackHideState.isActive()) return false;
        if (overlays.isEmpty()) return false;
        long now = System.nanoTime();
        if (Double.compare(mouseX, cachedHoverBlockMouseX) == 0
            && Double.compare(mouseY, cachedHoverBlockMouseY) == 0
            && now - cachedHoverBlockNanos < 16_000_000L) {
            return cachedHoverBlockResult;
        }

        boolean result = isMouseOverAnyOverlayVirtual(
            AmpereUiScale.toVirtual(mouseX),
            AmpereUiScale.toVirtual(mouseY)
        );
        cachedHoverBlockMouseX = mouseX;
        cachedHoverBlockMouseY = mouseY;
        cachedHoverBlockNanos = now;
        cachedHoverBlockResult = result;
        return result;
    }

    private void invalidateHoverBlockCache() {
        cachedHoverBlockMouseX = Double.NaN;
        cachedHoverBlockMouseY = Double.NaN;
        cachedHoverBlockNanos = 0L;
        cachedHoverBlockResult = false;
    }

    private void clearFocusedTextFields() {
        for (IAmpereOverlay overlay : overlays) {
            if (isOverlayInteractive(overlay) && overlay.hasTextFieldFocused()) {
                overlay.clearTextFieldFocus();
            }
        }
    }

    private IAmpereOverlay getTopmostOverlayAt(double mouseX, double mouseY) {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAmpereOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay) && overlay.isMouseOver(mouseX, mouseY)) {
                return overlay;
            }
        }
        return null;
    }

    public boolean isTopOverlay(IAmpereOverlay overlay) {
        if (overlay == null || overlays.isEmpty()) return false;
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAmpereOverlay candidate = overlays.get(i);
            if (isOverlayInteractive(candidate)) {
                return candidate == overlay;
            }
        }
        return false;
    }

    public boolean isFocusedOverlay(IAmpereOverlay overlay) {
        return overlay != null && overlay == focusedOverlay;
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (PackHideState.isActive()) return false;
        inventoryMouseDown = false;
        mouseX = AmpereUiScale.toVirtual(mouseX);
        mouseY = AmpereUiScale.toVirtual(mouseY);
        IAmpereOverlay topOverlay = getTopmostOverlayAt(mouseX, mouseY);
        if (topOverlay == null) {
            focusedOverlay = null;
            AmpereSharedState.get().setFocusedOverlayId("");
            headerCollapseOverlay = null;
            headerCollapseMoved = false;
            headerCollapseStartBounds = null;
            inventoryMouseDown = true;
            return false;
        }

        clearFocusedTextFields();
        if (button == 0) {
            if (topOverlay.isOverResizeHandle(mouseX, mouseY)) {
                resizingOverlay = topOverlay;
                resizeStartBounds = topOverlay.getBounds();
                resizeStartMouseX = mouseX;
                resizeStartMouseY = mouseY;
                bringToFront(topOverlay);
                return true;
            }

            if (topOverlay.isOverDragBar(mouseX, mouseY)) {
                draggingOverlay = topOverlay;
                if (topOverlay.usesSharedHeaderClickCollapse()) {
                    headerCollapseOverlay = topOverlay;
                    headerCollapseMoved = false;
                    headerCollapseStartMouseX = mouseX;
                    headerCollapseStartMouseY = mouseY;
                    headerCollapseStartBounds = topOverlay.getBounds();
                } else {
                    headerCollapseOverlay = null;
                    headerCollapseMoved = false;
                    headerCollapseStartBounds = null;
                }
                bringToFront(topOverlay);
                adapterFor(topOverlay).mouseClicked((int) mouseX, (int) mouseY, button);
                return true;
            }
        }

        headerCollapseOverlay = null;
        headerCollapseMoved = false;
        headerCollapseStartBounds = null;

        bringToFront(topOverlay);
        syncOverlayLayerInput();
        OperationalOverlayComponent topComponent = adapterFor(topOverlay);
        var topHitBounds = topComponent.hitBounds();
        if (topHitBounds == null || !topHitBounds.contains((int) mouseX, (int) mouseY)) {
            topComponent.mouseClicked((int) mouseX, (int) mouseY, button);
        } else {
            overlayInput.mouseClicked((int) mouseX, (int) mouseY, button);
        }
        return true;
    }

    public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        if (PackHideState.isActive()) return false;
        mouseX = AmpereUiScale.toVirtual(mouseX);
        mouseY = AmpereUiScale.toVirtual(mouseY);
        boolean wasDraggingOrResizing = (draggingOverlay != null || resizingOverlay != null);
        IAmpereOverlay prevDragging = draggingOverlay;
        IAmpereOverlay prevResizing = resizingOverlay;
        boolean shouldToggleHeaderCollapse = button == 0
            && prevDragging != null
            && prevDragging == headerCollapseOverlay
            && prevDragging.usesSharedHeaderClickCollapse()
            && !headerCollapseMoved;
        AmpereWindowLayout headerStartBounds = headerCollapseStartBounds;
        if (button == 0) {
            draggingOverlay = null;
            if (resizingOverlay != null) resizingOverlay.saveLayout();
            resizingOverlay = null;
            resizeStartBounds = null;
            headerCollapseOverlay = null;
            headerCollapseMoved = false;
            headerCollapseStartBounds = null;
        }

        if (prevDragging != null) adapterFor(prevDragging).mouseReleased((int) mouseX, (int) mouseY, button);
        if (prevResizing != null && prevResizing != prevDragging) adapterFor(prevResizing).mouseReleased((int) mouseX, (int) mouseY, button);

        if (shouldToggleHeaderCollapse && isOverlayInteractive(prevDragging)) {
            if (headerStartBounds != null) {
                AmpereWindowLayout current = prevDragging.getBounds();
                prevDragging.setBounds(new AmpereWindowLayout(
                    headerStartBounds.x,
                    headerStartBounds.y,
                    current.width,
                    current.height,
                    current.visible,
                    current.collapsed
                ));
            }
            prevDragging.toggleCollapsed();
            prevDragging.saveLayout();
            invalidateHoverBlockCache();
            return true;
        }

        if (wasDraggingOrResizing) return true;

        if (inventoryMouseDown) {
            inventoryMouseDown = false;
            return false;
        }

        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAmpereOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay)) {
                if (adapterFor(overlay).mouseReleased((int) mouseX, (int) mouseY, button) == UiInputResult.HANDLED) {
                    return true;
                }
            }
        }

        return isMouseOverAnyOverlayVirtual(mouseX, mouseY);
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (PackHideState.isActive()) return false;
        mouseX = AmpereUiScale.toVirtual(mouseX);
        mouseY = AmpereUiScale.toVirtual(mouseY);
        deltaX = AmpereUiScale.toVirtual(deltaX);
        deltaY = AmpereUiScale.toVirtual(deltaY);
        if (resizingOverlay != null && resizeStartBounds != null && isOverlayInteractive(resizingOverlay)) {
            AmpereWindowLayout current = resizingOverlay.getBounds();
            AmpereWindowLayout resized = new AmpereWindowLayout(
                resizeStartBounds.x,
                resizeStartBounds.y,
                Math.max(resizingOverlay.getMinWidth(), resizeStartBounds.width + (int) Math.round(mouseX - resizeStartMouseX)),
                Math.max(resizingOverlay.getMinHeight(), resizeStartBounds.height + (int) Math.round(mouseY - resizeStartMouseY)),
                current.visible,
                current.collapsed
            );
            resizingOverlay.setBounds(resized);
            invalidateHoverBlockCache();
            return true;
        }

        if (isOverlayInteractive(draggingOverlay)) {
            if (draggingOverlay == headerCollapseOverlay && !headerCollapseMoved) {
                if (Math.abs(mouseX - headerCollapseStartMouseX) >= HEADER_CLICK_DRAG_THRESHOLD
                    || Math.abs(mouseY - headerCollapseStartMouseY) >= HEADER_CLICK_DRAG_THRESHOLD) {
                    headerCollapseMoved = true;
                }
            }
            boolean handled = adapterFor(draggingOverlay).mouseDragged((int) mouseX, (int) mouseY, button, deltaX, deltaY) == UiInputResult.HANDLED;
            invalidateHoverBlockCache();
            return handled;
        }

        if (inventoryMouseDown) return false;

        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAmpereOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay)) {
                if (adapterFor(overlay).mouseDragged((int) mouseX, (int) mouseY, button, deltaX, deltaY) == UiInputResult.HANDLED) {
                    return true;
                }
            }
        }

        return isMouseOverAnyOverlayVirtual(mouseX, mouseY);
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double amount) {
        if (PackHideState.isActive()) return false;
        mouseX = AmpereUiScale.toVirtual(mouseX);
        mouseY = AmpereUiScale.toVirtual(mouseY);
        IAmpereOverlay topOverlay = getTopmostOverlayAt(mouseX, mouseY);
        if (topOverlay == null) return false;
        bringToFront(topOverlay);
        syncOverlayLayerInput();
        OperationalOverlayComponent topComponent = adapterFor(topOverlay);
        var topHitBounds = topComponent.hitBounds();
        if (topHitBounds == null || !topHitBounds.contains((int) mouseX, (int) mouseY)) {
            topComponent.mouseScrolled((int) mouseX, (int) mouseY, amount);
        } else {
            overlayInput.mouseScrolled((int) mouseX, (int) mouseY, amount);
        }
        return true;
    }

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (PackHideState.isActive()) return false;
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAmpereOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay) && overlay.wantsKeyboardCapture()) {
                if (adapterFor(overlay).keyPressed(keyCode, scanCode, modifiers) == UiInputResult.HANDLED) {
                    bringToFront(overlay);
                    return true;
                }
            }
        }

        IAmpereOverlay focusedTextOverlay = getTextFieldFocusOverlay();
        if (focusedTextOverlay != null) {
            adapterFor(focusedTextOverlay).keyPressed(keyCode, scanCode, modifiers);
            focusedOverlay = focusedTextOverlay;
            AmpereSharedState.get().setFocusedOverlayId(focusedTextOverlay.getOverlayId());
            return true;
        }

        IAmpereOverlay keyboardTarget = getKeyboardTargetOverlay();
        if (keyboardTarget != null && adapterFor(keyboardTarget).keyPressed(keyCode, scanCode, modifiers) == UiInputResult.HANDLED) {
            return true;
        }

        return isAnyTextFieldFocused();
    }

    public boolean handleCharTyped(char chr, int modifiers) {
        if (PackHideState.isActive()) return false;
        IAmpereOverlay focusedTextOverlay = getTextFieldFocusOverlay();
        if (focusedTextOverlay != null) {
            adapterFor(focusedTextOverlay).charTyped(chr, modifiers);
            focusedOverlay = focusedTextOverlay;
            AmpereSharedState.get().setFocusedOverlayId(focusedTextOverlay.getOverlayId());
            return true;
        }

        IAmpereOverlay keyboardTarget = getKeyboardTargetOverlay();
        if (keyboardTarget != null && adapterFor(keyboardTarget).charTyped(chr, modifiers) == UiInputResult.HANDLED) {
            return true;
        }
        return isAnyTextFieldFocused();
    }

    private IAmpereOverlay getTextFieldFocusOverlay() {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAmpereOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay) && overlay.hasTextFieldFocused()) {
                return overlay;
            }
        }
        return null;
    }

    private IAmpereOverlay getKeyboardTargetOverlay() {
        if (isOverlayInteractive(focusedOverlay)) {
            return focusedOverlay;
        }

        for (int i = overlays.size() - 1; i >= 0; i--) {
            IAmpereOverlay overlay = overlays.get(i);
            if (isOverlayInteractive(overlay)) {
                return overlay;
            }
        }

        return null;
    }

    public boolean isAnyTextFieldFocused() {
        if (PackHideState.isActive()) return false;
        for (IAmpereOverlay overlay : overlays) {
            if (isOverlayInteractive(overlay) && overlay.hasTextFieldFocused()) {
                return true;
            }
        }
        return false;
    }

    private OperationalOverlayComponent adapterFor(IAmpereOverlay overlay) {
        return overlayComponents.computeIfAbsent(overlay, OperationalOverlayComponent::new);
    }

    private void syncOverlayLayerInput() {
        for (IAmpereOverlay overlay : overlays) {
            adapterFor(overlay).setInputSuppressed(!isOverlayInteractive(overlay));
        }
        rebuildOverlayLayers();
    }

    private void rebuildOverlayLayers() {
        if (!overlayLayersDirty) return;
        overlayLayers.clear();
        for (IAmpereOverlay overlay : overlays) {
            overlayLayers.add(isTransientOverlay(overlay) ? UiLayer.DROPDOWN : UiLayer.FLOATING, adapterFor(overlay));
        }
        overlayLayersDirty = false;
    }
}
