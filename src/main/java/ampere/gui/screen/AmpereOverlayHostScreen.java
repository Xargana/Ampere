package ampere.gui.screen;

import ampere.util.AmpereOverlayManager;
import ampere.util.IAmpereOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AmpereOverlayHostScreen extends Screen {

    private final IAmpereOverlay tiedOverlay;
    private final Screen returnScreen;

    public AmpereOverlayHostScreen() {
        this(null, null);
    }

    public AmpereOverlayHostScreen(IAmpereOverlay tiedOverlay) {
        this(tiedOverlay, null);
    }

    public AmpereOverlayHostScreen(IAmpereOverlay tiedOverlay, Screen returnScreen) {
        super(Component.literal("Ampere Overlays"));
        this.tiedOverlay = tiedOverlay;
        this.returnScreen = returnScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (tiedOverlay != null && !tiedOverlay.isVisible() && minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(returnScreen);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {

        AmpereOverlayManager.get().renderAll(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return AmpereOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return AmpereOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return AmpereOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return AmpereOverlayManager.get().handleMouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {

        boolean inventoryKey = minecraft != null && minecraft.options != null
            && minecraft.options.keyInventory != null
            && minecraft.options.keyInventory.matches(input);
        boolean closeKey = input.key() == GLFW.GLFW_KEY_ESCAPE || inventoryKey;

        if (closeKey && minecraft != null && !AmpereOverlayManager.get().isAnyTextFieldFocused()) {
            minecraft.setScreen(returnScreen);
            return true;
        }

        if (AmpereOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }
        if (closeKey && minecraft != null) {
            minecraft.setScreen(returnScreen);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return AmpereOverlayManager.get().handleCharTyped((char) input.codepoint(), 0);
    }
}
