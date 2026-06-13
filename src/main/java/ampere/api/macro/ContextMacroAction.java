package ampere.api.macro;

import ampere.util.macro.MacroAction;
import net.minecraft.client.Minecraft;

public interface ContextMacroAction extends MacroAction {

    void run(MacroExecutionContext ctx) throws InterruptedException;

    @Override
    default void execute(Minecraft mc) {

    }
}
