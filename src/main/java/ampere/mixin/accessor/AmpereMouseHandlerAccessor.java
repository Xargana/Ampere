package ampere.mixin.accessor;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface AmpereMouseHandlerAccessor {
    @Invoker("onButton")
    void Ampere$invokeOnButton(long handle, MouseButtonInfo buttonInfo, int action);
}
