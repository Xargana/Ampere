package ampere.mixin.security;

import ampere.security.AmpereProtectorComponentCodec;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(ComponentSerialization.class)
public class AmpereProtectorComponentSerializationMixin {

    @WrapOperation(
        method = "<clinit>",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<Component> Ampere$wrapRecursive(
            String name,
            Function<Codec<Component>, Codec<Component>> body,
            Operation<Codec<Component>> original) {
        return new AmpereProtectorComponentCodec(original.call(name, body));
    }
}
