package ampere.mixin;

import ampere.util.AmpereCiphertextTap;
import ampere.util.AmperePacketCapture;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;

@Mixin(Connection.class)
public abstract class AmpereConnectionEncryptionMixin {
    @Shadow private Channel channel;

    @Inject(method = "setEncryptionKey", at = @At("TAIL"))
    private void Ampere$observeEncryptionBoundary(Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci) {
        if (channel == null) return;
        AmperePacketCapture.markEncryptionEnabled(channel);
        try {
            if (channel.pipeline().get("Ampere_ciphertext_in") == null && channel.pipeline().get("decrypt") != null) {
                channel.pipeline().addBefore("decrypt", "Ampere_ciphertext_in", new AmpereCiphertextTap("S2C"));
            }
            if (channel.pipeline().get("Ampere_ciphertext_out") == null && channel.pipeline().get("encrypt") != null) {
                channel.pipeline().addBefore("encrypt", "Ampere_ciphertext_out", new AmpereCiphertextTap("C2S"));
            }
        } catch (Throwable ignored) {
        }
    }
}
