package ampere.mixin.security;

import ampere.security.AmpereFromPacketAccess;
import ampere.security.AmpereProtector;
import ampere.security.AmpereProtectorTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(KeybindContents.class)
public abstract class AmpereProtectorKeybindContentsMixin implements AmpereFromPacketAccess {

    @Shadow @Final private String name;

    @Unique
    private boolean Ampere$fromPacket;

    @Unique
    private Object Ampere$cachedBlocked;

    @Override
    public void Ampere$setFromPacket() {
        this.Ampere$fromPacket = true;
    }

    @WrapOperation(
        method = "getNestedComponent",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;")
    )
    private Object Ampere$interceptKeybind(Supplier<?> supplier, Operation<Object> original) {
        if (!this.Ampere$fromPacket) return original.call(supplier);

        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return original.call(supplier);
        }
        if (mc == null || mc.hasSingleplayerServer()) return original.call(supplier);
        if (!AmpereProtector.shouldProtectTranslationKeys()) return original.call(supplier);

        if (!AmpereProtectorTracker.shouldBlockKeybind(name)) return original.call(supplier);

        if (Ampere$cachedBlocked != null) return Ampere$cachedBlocked;
        Component replacement = Component.literal(name);
        Ampere$cachedBlocked = replacement;
        return replacement;
    }
}
