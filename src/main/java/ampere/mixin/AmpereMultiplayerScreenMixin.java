package ampere.mixin;

import ampere.ducks.AmpereExternalButtonScreen;
import ampere.gui.screen.AmpereAccountsScreen;
import ampere.gui.screen.AmpereJoinMacroScreen;
import ampere.gui.screen.AmpereProxiesScreen;
import ampere.modules.AmpereModule;
import ampere.modules.PackHideState;
import ampere.util.AmpereProxy;
import ampere.util.AmpereProxyManager;
import ampere.util.AmpereJoinMacroController;
import ampere.util.AmpereMacroManager;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JoinMultiplayerScreen.class, priority = 2000)
public abstract class AmpereMultiplayerScreenMixin extends Screen implements AmpereExternalButtonScreen {
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int BUTTON_WIDTH = 60;
    @Unique private static final int MACRO_BUTTON_WIDTH = 50;
    @Unique private static final int STACK_WIDTH = 104;
    @Unique private static final int MARGIN = 4;
    @Unique private static final int GAP = 3;
    @Unique private static final int EXTERNAL_NONE = 0;
    @Unique private static final int EXTERNAL_VIA_FABRIC_PLUS = 1;
    @Unique private static final int EXTERNAL_REPLAY_RECORD = 2;
    @Unique private static final int EXTERNAL_OPSEC = 3;

    @Unique private Button Ampere$accountsButton;
    @Unique private Button Ampere$joinMacroButton;
    @Unique private Button Ampere$proxiesButton;
    @Unique private Button Ampere$spoofButton;
    @Unique private Button Ampere$packsButton;
    @Unique private Button Ampere$recordButton;

    protected AmpereMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void Ampere$repositionElements(CallbackInfo ci) {
        Ampere$layoutButtons();
    }

    @Unique
    private void Ampere$layoutButtons() {
        Ampere$suppressMeteorWidgets();
        AbstractWidget via = null;
        AbstractWidget opsec = null;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof AbstractWidget widget) || Ampere$isOwned(widget)) continue;
            int kind = Ampere$externalKind(widget);
            if (kind == EXTERNAL_REPLAY_RECORD) {
                Ampere$setVisible(widget, false);
            } else if (kind == EXTERNAL_VIA_FABRIC_PLUS) {
                via = widget;
            } else if (kind == EXTERNAL_OPSEC) {
                opsec = widget;
            }
        }

        boolean hidden = PackHideState.isActive();
        if (hidden) {
            Ampere$setVisible(Ampere$accountsButton, false);
            Ampere$setVisible(Ampere$joinMacroButton, false);
            Ampere$setVisible(Ampere$proxiesButton, false);
            Ampere$setVisible(Ampere$spoofButton, false);
            Ampere$setVisible(Ampere$packsButton, false);
            Ampere$setVisible(Ampere$recordButton, false);
            Ampere$setVisible(via, false);
            Ampere$setVisible(opsec, false);
            return;
        }

        Ampere$ensureOwnedButtons();
        int topRight = this.width - MARGIN;
        Ampere$place(Ampere$accountsButton, topRight - BUTTON_WIDTH, MARGIN, BUTTON_WIDTH, BUTTON_HEIGHT);
        Ampere$place(Ampere$proxiesButton, topRight - (BUTTON_WIDTH * 2) - GAP, MARGIN, BUTTON_WIDTH, BUTTON_HEIGHT);
        Ampere$place(Ampere$joinMacroButton, topRight - (BUTTON_WIDTH * 2) - (GAP * 2) - MACRO_BUTTON_WIDTH, MARGIN, MACRO_BUTTON_WIDTH, BUTTON_HEIGHT);

        int footerRight = (this.width / 2) - 154 - GAP;
        int footerWidth = Math.max(60, Math.min(STACK_WIDTH, footerRight - MARGIN));
        int footerX = Math.max(MARGIN, footerRight - footerWidth);
        int footerY = Math.max(MARGIN, this.height - (BUTTON_HEIGHT * 2 + GAP + 8));
        Ampere$spoofButton.setMessage(Ampere$spoofClientLabel(footerWidth));
        Ampere$packsButton.setMessage(Ampere$bypassPacksLabel(footerWidth));
        Ampere$place(Ampere$spoofButton, footerX, footerY, footerWidth, BUTTON_HEIGHT);
        Ampere$place(Ampere$packsButton, footerX, footerY + BUTTON_HEIGHT + GAP, footerWidth, BUTTON_HEIGHT);

        int rightX = Math.min(this.width - MARGIN - STACK_WIDTH, (this.width / 2) + 154 + GAP);
        int rightWidth = Math.max(60, Math.min(STACK_WIDTH, this.width - MARGIN - rightX));
        int count = (FabricLoader.getInstance().isModLoaded("replaymod") ? 1 : 0) + (via != null ? 1 : 0) + (opsec != null ? 1 : 0);
        int rightY = Math.max(MARGIN, this.height - 8 - Math.max(0, count * BUTTON_HEIGHT + Math.max(0, count - 1) * GAP));
        int slot = 0;

        if (FabricLoader.getInstance().isModLoaded("replaymod")) {
            Ampere$recordButton.setMessage(Ampere$replayServerLabel(rightWidth));
            Ampere$place(Ampere$recordButton, rightX, rightY + slot++ * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        } else {
            Ampere$setVisible(Ampere$recordButton, false);
        }
        if (via != null) {
            Ampere$place(via, rightX, rightY + slot++ * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        }
        if (opsec != null) {
            Ampere$place(opsec, rightX, rightY + slot * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        }
    }

    @Unique
    private void Ampere$ensureOwnedButtons() {
        if (Ampere$accountsButton == null) {
            Ampere$accountsButton = this.addRenderableWidget(Button.builder(Component.literal("Accounts"),
                ignored -> this.minecraft.setScreen(new AmpereAccountsScreen(this))).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (Ampere$joinMacroButton == null) {
            Ampere$joinMacroButton = this.addRenderableWidget(Button.builder(Component.literal("Macro"),
                ignored -> this.minecraft.setScreen(new AmpereJoinMacroScreen(this))).bounds(0, 0, MACRO_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (Ampere$proxiesButton == null) {
            Ampere$proxiesButton = this.addRenderableWidget(Button.builder(Component.literal("Proxies"),
                ignored -> this.minecraft.setScreen(new AmpereProxiesScreen(this))).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (Ampere$spoofButton == null) {
            Ampere$spoofButton = this.addRenderableWidget(Button.builder(Component.literal("Client"),
                ignored -> {
                    AmpereModule module = AmpereModule.get();
                    if (module != null) module.setSpoofClientVanilla(!module.isSpoofClientVanilla());
                    Ampere$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
        if (Ampere$packsButton == null) {
            Ampere$packsButton = this.addRenderableWidget(Button.builder(Component.literal("Packs"),
                ignored -> {
                    AmpereModule module = AmpereModule.get();
                    if (module != null) module.setBypassResourcePack(!module.isBypassResourcePack());
                    Ampere$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
        if (Ampere$recordButton == null) {
            Ampere$recordButton = this.addRenderableWidget(Button.builder(Component.literal("Replay"),
                ignored -> {
                    Ampere$toggleReplayServerRecording();
                    Ampere$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
    }

    @Unique
    private boolean Ampere$isOwned(AbstractWidget widget) {
        return widget == Ampere$accountsButton || widget == Ampere$joinMacroButton || widget == Ampere$proxiesButton || widget == Ampere$spoofButton
            || widget == Ampere$packsButton || widget == Ampere$recordButton;
    }

    @Unique
    private static void Ampere$place(AbstractWidget widget, int x, int y, int width, int height) {
        if (widget == null) return;
        widget.setX(Math.max(MARGIN, x));
        widget.setY(Math.max(MARGIN, y));
        widget.setSize(Math.max(1, width), Math.max(1, height));
        Ampere$setVisible(widget, true);
    }

    @Unique
    private static void Ampere$setVisible(AbstractWidget widget, boolean visible) {
        if (widget == null) return;
        widget.visible = visible;
        widget.active = visible;
    }

    @Unique
    private Component Ampere$spoofClientLabel(int width) {
        AmpereModule module = AmpereModule.get();
        boolean enabled = module != null && module.isSpoofClientVanilla();
        return Ampere$fitLabel(width, enabled ? "Client: Vanilla" : "Client: Modded", enabled ? "Vanilla" : "Modded", "Client");
    }

    @Unique
    private Component Ampere$bypassPacksLabel(int width) {
        AmpereModule module = AmpereModule.get();
        boolean enabled = module != null && module.isBypassResourcePack();
        return Ampere$fitLabel(width, enabled ? "Packs: Bypass" : "Packs: Normal", enabled ? "Bypass" : "Normal", "Packs");
    }

    @Unique
    private Component Ampere$replayServerLabel(int width) {
        boolean enabled = Ampere$getReplayBoolean("RECORD_SERVER", true);
        return Ampere$fitLabel(width, enabled ? "Replay: On" : "Replay: Off", enabled ? "Rec: On" : "Rec: Off", "Replay");
    }

    @Unique
    private Component Ampere$fitLabel(int width, String... candidates) {
        int available = Math.max(1, width - 8);
        for (String candidate : candidates) {
            if (this.font.width(candidate) <= available) return Component.literal(candidate);
        }
        return Component.literal(candidates[candidates.length - 1]);
    }

    @Unique
    private int Ampere$externalKind(AbstractWidget widget) {
        String label = widget.getMessage().getString();
        String normalized = label.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "");
        String className = widget.getClass().getName().toLowerCase(Locale.ROOT);
        if (FabricLoader.getInstance().isModLoaded("viafabricplus") && "ViaFabricPlus".equals(label)) return EXTERNAL_VIA_FABRIC_PLUS;
        if (FabricLoader.getInstance().isModLoaded("replaymod")
            && (className.contains("replaymod") || normalized.contains("recordserver") || normalized.contains("replaymodguisettingsrecordserver"))) {
            return EXTERNAL_REPLAY_RECORD;
        }
        if (className.contains("opsec") || normalized.contains("opsec")) return EXTERNAL_OPSEC;
        return EXTERNAL_NONE;
    }

    @Unique
    private void Ampere$suppressMeteorWidgets() {
        if (!FabricLoader.getInstance().isModLoaded("meteor-client")) return;
        Ampere$disableMeteorMultiplayerUiConfig();
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button button) || Ampere$isOwned(button)) continue;
            String label = button.getMessage().getString();
            if ("Accounts".equals(label) || "Proxies".equals(label)) Ampere$setVisible(button, false);
        }
    }

    @Unique
    private void Ampere$toggleReplayServerRecording() {
        boolean enabled = Ampere$getReplayBoolean("RECORD_SERVER", true);
        Ampere$setReplayBoolean("RECORD_SERVER", !enabled);
    }

    @Unique
    private static boolean Ampere$getReplayBoolean(String settingField, boolean fallback) {
        try {
            Object settings = Ampere$replaySettingsRegistry();
            Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
            Object value = settings.getClass().getMethod("get", Class.forName("com.replaymod.core.SettingsRegistry$SettingKey")).invoke(settings, key);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }

    @Unique
    private static void Ampere$setReplayBoolean(String settingField, boolean value) {
        try {
            Object settings = Ampere$replaySettingsRegistry();
            Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
            Class<?> settingKeyClass = Class.forName("com.replaymod.core.SettingsRegistry$SettingKey");
            settings.getClass().getMethod("set", settingKeyClass, Object.class).invoke(settings, key, value);
            settings.getClass().getMethod("save").invoke(settings);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    @Unique
    private static Object Ampere$replaySettingsRegistry() throws ReflectiveOperationException {
        Object replayMod = Class.forName("com.replaymod.core.ReplayMod").getField("instance").get(null);
        return replayMod.getClass().getMethod("getSettingsRegistry").invoke(replayMod);
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void Ampere$disableMeteorMultiplayerUiConfig() {
        try {
            Class<?> configClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config");
            Object config = configClass.getMethod("get").invoke(null);
            Class<?> buttonPositionClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config$ButtonPosition");
            Object hidden = Enum.valueOf((Class<? extends Enum>) buttonPositionClass.asSubclass(Enum.class), "Hidden");
            Ampere$setMeteorSetting(configClass.getField("accountButtonAnchor").get(config), hidden);
            Ampere$setMeteorSetting(configClass.getField("proxiesButtonAnchor").get(config), hidden);
            Ampere$setMeteorSetting(configClass.getField("showAccountStatus").get(config), false);
            Ampere$setMeteorSetting(configClass.getField("showProxiesStatus").get(config), false);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private static void Ampere$setMeteorSetting(Object setting, Object value) throws ReflectiveOperationException {
        setting.getClass().getSuperclass().getMethod("set", Object.class).invoke(setting, value);
    }

    @Override
    public void Ampere$renderExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        Ampere$layoutButtons();
        if (PackHideState.isActive()) return;
        String username = this.minecraft.getUser().getName();
        graphics.text(this.font, "Logged in as " + username, MARGIN, MARGIN, 0xFFFFFFFF, false);
        AmpereProxy proxy = AmpereProxyManager.get().getEnabled();
        int statusY = MARGIN + 12;
        if (proxy != null) {
            String proxyLabel = "Using proxy " + proxy.address + ":" + proxy.port;
            graphics.text(this.font, proxyLabel, MARGIN, statusY, 0xFFAFAFAF, false);
            statusY += 12;
        }
        String macroName = AmpereJoinMacroController.selectedMacroName();
        String macroLabel;
        int macroColor;
        if (macroName.isBlank()) {
            macroLabel = "Join Macro: none";
            macroColor = 0xFF8F8A8A;
        } else if (AmpereMacroManager.get().get(macroName) == null) {
            macroLabel = "Join Macro missing: " + macroName;
            macroColor = 0xFFFF6B6B;
        } else {
            macroLabel = "Join Macro: " + macroName + " - " + AmpereJoinMacroController.modeSummary();
            macroColor = 0xFF66E08A;
        }
        graphics.text(this.font, Ampere$fitPlain(macroLabel, 220), MARGIN, statusY, macroColor, false);
    }

    @Unique
    private String Ampere$fitPlain(String label, int maxWidth) {
        if (label == null) return "";
        if (this.font.width(label) <= maxWidth) return label;
        return this.font.plainSubstrByWidth(label, Math.max(1, maxWidth - 4));
    }

}
