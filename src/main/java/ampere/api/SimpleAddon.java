package ampere.api;

import ampere.api.macro.MacroActionEntry;
import ampere.api.macro.SimpleAction;
import ampere.api.macro.SimpleCondition;
import ampere.modules.PackModule;
import ampere.util.macro.MacroAction;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class SimpleAddon extends AmpereAddon {
    private final int apiVersion;
    private final String rootPackage;

    protected SimpleAddon(int apiVersion, String rootPackage) {
        this.apiVersion = apiVersion;
        this.rootPackage = rootPackage == null ? "" : rootPackage;
    }

    @Override
    public final int apiVersion() {
        return apiVersion;
    }

    @Override
    public final String getPackage() {
        return rootPackage;
    }

    @Override
    public final void onInitialize() {
        initialize();
    }

    protected abstract void initialize();

    protected final String id(String localId) {
        return AmpereAddons.id(localId);
    }

    protected final AddonRegistrationResult registerModule(PackModule module) {
        return AmpereAddons.modules().registerDetailed(module);
    }

    protected final AddonRegistrationResult registerAction(MacroActionEntry entry) {
        return AmpereAddons.macroActions().registerDetailed(entry);
    }

    protected final AddonRegistrationResult registerPreset(String label, String tip, Supplier<List<MacroAction>> builder) {
        return AmpereAddons.presets().registerDetailed(label, tip, builder);
    }

    protected final MacroActionEntry simpleAction(
        String localId,
        String label,
        String tip,
        String icon,
        Consumer<Minecraft> runner
    ) {
        String typeId = id(localId);
        return MacroActionEntry.builder(typeId, () -> new SimpleAction(typeId, label, icon, runner))
            .picker(label, tip)
            .build();
    }

    protected final MacroActionEntry simpleCondition(
        String localId,
        String label,
        String tip,
        String status,
        String icon,
        Predicate<Minecraft> predicate
    ) {
        String typeId = id(localId);
        return MacroActionEntry.builder(typeId, () -> new SimpleCondition(typeId, label, status, icon, predicate))
            .condition(label, tip)
            .build();
    }
}
