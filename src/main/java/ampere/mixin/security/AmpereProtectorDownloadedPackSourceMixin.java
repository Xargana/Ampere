package ampere.mixin.security;

import ampere.security.AmpereProtector;
import ampere.security.AmpereProtectorLangOnlyPackResources;
import ampere.security.AmpereProtectorPackStrip;
import ampere.security.AmpereProtectorServerPackFailureGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.UUID;

@Mixin(DownloadedPackSource.class)
public abstract class AmpereProtectorDownloadedPackSourceMixin {

    @Inject(method = "createRepositorySource", at = @At("RETURN"), cancellable = true)
    private void Ampere$suppressDownloadedPackSourceAfterFailure(CallbackInfoReturnable<RepositorySource> cir) {
        RepositorySource original = cir.getReturnValue();
        cir.setReturnValue(output -> {
            if (AmpereProtectorServerPackFailureGuard.shouldSuppressServerPacks()) return;
            original.loadPacks(output);
        });
    }

    @WrapOperation(
        method = "loadRequestedPacks",
        at = @At(value = "NEW", target = "(Ljava/nio/file/Path;)Lnet/minecraft/server/packs/FilePackResources$FileResourcesSupplier;"))
    private FilePackResources.FileResourcesSupplier Ampere$wrapFilePackSupplier(
            Path file,
            Operation<FilePackResources.FileResourcesSupplier> original,
            @Local PackReloadConfig.IdAndPath idAndPath) {

        FilePackResources.FileResourcesSupplier real = original.call(file);

        if (!AmpereProtector.shouldStripServerPacks()) return real;
        UUID packId = idAndPath.id();
        if (!AmpereProtectorPackStrip.isWrapped(packId)) return real;

        return new FilePackResources.FileResourcesSupplier(file) {
            @Override
            public PackResources openPrimary(PackLocationInfo loc) {
                return new AmpereProtectorLangOnlyPackResources(real.openPrimary(loc));
            }

            @Override
            public PackResources openFull(PackLocationInfo loc, Pack.Metadata md) {
                return new AmpereProtectorLangOnlyPackResources(real.openFull(loc, md));
            }
        };
    }
}
