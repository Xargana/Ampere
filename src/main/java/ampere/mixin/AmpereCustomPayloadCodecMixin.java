package ampere.mixin;

import ampere.modules.AmpereModule;
import ampere.util.AmperePayloadSupport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public abstract class AmpereCustomPayloadCodecMixin {
    @Inject(method = "encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void Ampere$encodeRawCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
        boolean isRaw = payload instanceof AmperePayloadSupport.RawCustomPacketPayload;
        if (!isRaw && !Ampere$shouldCapturePayloadBytes()) return;

        byte[] rememberedBytes = AmperePayloadSupport.getRememberedUnknownPayloadBytes(payload);
        if (rememberedBytes == null && !isRaw) return;

        CustomPacketPayload.Type<?> type = payload.type();
        if (type == null || type.id() == null) return;

        byte[] bytes = rememberedBytes != null
            ? rememberedBytes
            : ((AmperePayloadSupport.RawCustomPacketPayload) payload).bytes();

        buf.writeIdentifier(type.id());
        if (bytes.length > 0) {
            buf.writeBytes(bytes);
        }
        ci.cancel();
    }

    @Unique
    private static boolean Ampere$shouldCapturePayloadBytes() {
        AmpereModule module = AmpereModule.get();
        return module != null && module.shouldCapturePayloadBytes();
    }
}
