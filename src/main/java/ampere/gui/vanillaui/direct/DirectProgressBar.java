package ampere.gui.vanillaui.direct;

import ampere.gui.vanillaui.UiBounds;
import ampere.gui.vanillaui.UiContexts;
import ampere.gui.vanillaui.components.ProgressBar;

public final class DirectProgressBar extends DirectUiNode {
    private float progress = 0.0f;

    public DirectProgressBar() {
        this.height = 12;
    }

    public DirectProgressBar setProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        return this;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return 12.0f;
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        ProgressBar.render(UiContexts.overlay(context.drawContext(), context.textRenderer(),
                Math.round(context.mouseX()), Math.round(context.mouseY())),
            UiBounds.of(Math.round(x), Math.round(y), Math.round(width), Math.round(height)), progress);
    }
}
