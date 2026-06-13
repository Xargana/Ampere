package ampere.mixin.security;

import ampere.security.AmpereFromPacketAccess;
import ampere.security.AmpereProtector;
import ampere.security.AmpereProtectorTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TranslatableContents.class)
public abstract class AmpereProtectorTranslatableContentsMixin implements AmpereFromPacketAccess {

    @Unique
    private boolean Ampere$fromPacket;

    @Unique
    private boolean Ampere$silent;

    @Override
    public void Ampere$setFromPacket() {
        this.Ampere$fromPacket = true;
    }

    @Override
    public void Ampere$setSilent() {
        this.Ampere$silent = true;
    }

    @Unique
    private static final String Ampere_ALLOW = "\0__Ampere_allow__";

    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;")
    )
    private String Ampere$wrapGetOrDefault(Language instance, String id, Operation<String> original) {
        String result = Ampere$handle(id, id);
        if (result == Ampere_ALLOW) return original.call(instance, id);
        return result;
    }

    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    )
    private String Ampere$wrapGetOrDefaultFallback(Language instance, String idArg, String defaultValue,
                                                   Operation<String> original) {
        String result = Ampere$handle(idArg, defaultValue);
        if (result == Ampere_ALLOW) return original.call(instance, idArg, defaultValue);
        return result;
    }

    @Unique
    private String Ampere$handle(String translationKey, String defaultValue) {

        if (Ampere$silent) return Ampere_ALLOW;
        if (!this.Ampere$fromPacket) return Ampere_ALLOW;

        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return Ampere_ALLOW;
        }
        if (mc == null || mc.hasSingleplayerServer()) return Ampere_ALLOW;
        if (!AmpereProtector.shouldProtectTranslationKeys()) return Ampere_ALLOW;

        String replacement = AmpereProtectorTracker.translationReplacement(translationKey, defaultValue);
        return replacement == null ? Ampere_ALLOW : replacement;
    }
}
