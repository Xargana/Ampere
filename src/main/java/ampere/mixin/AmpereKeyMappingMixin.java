package ampere.mixin;

import ampere.mixin.accessor.AmpereKeyboardHandlerAccessor;
import ampere.mixin.accessor.AmpereMouseHandlerAccessor;
import ampere.util.AmpereKeyMappingBridge;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(KeyMapping.class)
public abstract class AmpereKeyMappingMixin implements AmpereKeyMappingBridge {
    @Shadow
    protected InputConstants.Key key;

    @Shadow
    public abstract void setDown(boolean down);

    @Override
    @Unique
    public boolean Ampere$isActuallyDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        Window window = mc.getWindow();
        int code = key.getValue();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window.handle(), code) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, code);
    }

    @Override
    @Unique
    public void Ampere$resetPressedState() {
        setDown(Ampere$isActuallyDown());
    }

    @Override
    @Unique
    public void Ampere$simulatePress(boolean pressed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.keyboardHandler == null || mc.mouseHandler == null) return;
        Window window = mc.getWindow();
        int action = pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE;
        switch (key.getType()) {
            case KEYSYM -> ((AmpereKeyboardHandlerAccessor) mc.keyboardHandler).Ampere$invokeKeyPress(
                window.handle(), action, new KeyEvent(key.getValue(), 0, 0)
            );
            case SCANCODE -> ((AmpereKeyboardHandlerAccessor) mc.keyboardHandler).Ampere$invokeKeyPress(
                window.handle(), action, new KeyEvent(GLFW.GLFW_KEY_UNKNOWN, key.getValue(), 0)
            );
            case MOUSE -> ((AmpereMouseHandlerAccessor) mc.mouseHandler).Ampere$invokeOnButton(
                window.handle(), new MouseButtonInfo(key.getValue(), 0), action
            );
            default -> setDown(pressed);
        }
    }
}
