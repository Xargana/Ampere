package ampere.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AmpereMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> SODIUM_MIXINS = Set.of(
        "AmpereSodiumBlockRendererMixin",
        "AmpereSodiumBlockContextMixin",
        "AmpereSodiumLightDataAccessMixin",
        "AmpereSodiumDefaultFluidRendererMixin",
        "AmpereSodiumFluidRendererImplMixin"
    );

    private boolean sodiumLoaded;
    private boolean indigoLoaded;
    private boolean opsecLoaded;
    private boolean exploitPreventerLoaded;
    private boolean replayModLoaded;
    private boolean essentialLoaded;

    private static final Set<String> EXPLOIT_PREVENTER_OVERLAP_SECURITY_MIXINS = Set.of(
        "AmpereProtectorTranslatableContentsMixin",
        "AmpereProtectorKeybindContentsMixin",
        "AmpereProtectorComponentSerializationMixin",
        "AmpereProtectorPacketProcessorMixin",
        "AmpereProtectorPacketDecoderMixin",
        "AmpereProtectorHttpUtilMixin",
        "AmpereProtectorConnectionTrackingMixin",
        "AmpereProtectorDownloadQueueMixin",
        "AmpereProtectorClientLanguageMixin",
        "AmpereProtectorDeprecatedTranslationsInfoMixin",
        "AmpereProtectorOptionsMixin",
        "AmpereProtectorKeyMappingRegistryImplMixin",
        "AmpereProtectorPayloadTypeRegistryImplMixin",
        "AmpereProtectorResourceLoaderImplMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
        FabricLoader loader = FabricLoader.getInstance();
        sodiumLoaded = loader.isModLoaded("sodium");
        indigoLoaded = loader.isModLoaded("fabric-renderer-indigo");
        opsecLoaded = loader.isModLoaded("opsec");
        exploitPreventerLoaded = loader.isModLoaded("exploitpreventer");
        replayModLoaded = loader.isModLoaded("replaymod");
        essentialLoaded = loader.isModLoaded("essential")
            || loader.isModLoaded("essential-container")
            || loader.isModLoaded("essential-loader");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (SODIUM_MIXINS.contains(simpleName)) return sodiumLoaded;
        if ("AmpereFluidRendererMixin".equals(simpleName) && sodiumLoaded) return false;
        if ("AmpereReplayModGuiHandlerMixin".equals(simpleName)) return replayModLoaded;
        if (simpleName.startsWith("AmpereEssential")) return essentialLoaded;

        if (mixinClassName.startsWith("Ampere.mixin.indigo.")) return indigoLoaded && !sodiumLoaded;

        if (mixinClassName.startsWith("Ampere.mixin.security.")) {
            if (opsecLoaded) return false;
            return !exploitPreventerLoaded || !EXPLOIT_PREVENTER_OVERLAP_SECURITY_MIXINS.contains(simpleName);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
