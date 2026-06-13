package ampere.mixin.security;

import ampere.AmpereClientAddon;
import ampere.security.AmpereProtectorServerPackFailureGuard;
import ampere.security.AmpereProtectorPackStrip;
import ampere.util.AmpereNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Mixin(ServerPackManager.class)
public abstract class AmpereProtectorServerPackManagerMixin {

    @Unique private static long Ampere$lastRecoveryReloadMs;
    @Unique private static long Ampere$lastRecoveryToastMs;

    @Shadow public abstract void popAll();

    @Inject(method = "onDownload", at = @At("HEAD"))
    private void Ampere$makeFailedServerPackBatchAtomic(Collection<?> data, DownloadQueue.BatchResult result, CallbackInfo ci) {
        if (result == null || result.failed().isEmpty()) return;
        AmpereProtectorServerPackFailureGuard.suppressServerPacksTemporarily();

        try {
            Map<UUID, ?> downloaded = result.downloaded();
            if (downloaded != null) downloaded.clear();
        } catch (Throwable error) {
            AmpereClientAddon.LOG.warn("[AmpereProtector] Failed to clear partial server-pack batch.", error);
        }
    }

    @Inject(method = "onDownload", at = @At("TAIL"))
    private void Ampere$recoverFromFailedServerPackDownload(Collection<?> data, DownloadQueue.BatchResult result, CallbackInfo ci) {
        if (result == null || result.failed().isEmpty()) return;

        AmpereProtectorServerPackFailureGuard.suppressServerPacksTemporarily();
        AmpereProtectorPackStrip.clearAll();

        try {
            popAll();
        } catch (Throwable error) {
            AmpereClientAddon.LOG.warn("[AmpereProtector] Failed to clear server packs after download failure.", error);
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                try {
                    client.getDownloadedPackSource().popAll();
                    long now = System.currentTimeMillis();
                    if (now - Ampere$lastRecoveryToastMs > 5000L) {
                        Ampere$lastRecoveryToastMs = now;
                        AmpereNotifications.warning("Server resource pack failed. Restored client resources.");
                    }
                    if (now - Ampere$lastRecoveryReloadMs > 1000L) {
                        Ampere$lastRecoveryReloadMs = now;
                        client.reloadResourcePacks();
                    }
                } catch (Throwable error) {
                    AmpereClientAddon.LOG.warn("[AmpereProtector] Failed to clear downloaded pack source after download failure.", error);
                }
            });
        }
    }
}
