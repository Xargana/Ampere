package ampere.util;

import ampere.AmpereClientAddon;
import ampere.mixin.accessor.AmpereMinecraftAccessor;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class AmpereAccountSessionSwitcher {
    private static User originalUser;

    private AmpereAccountSessionSwitcher() {
    }

    public static User getOriginalUser() {
        if (originalUser == null) originalUser = Minecraft.getInstance().getUser();
        return originalUser;
    }

    public static boolean setSession(User user) {
        return setSession(user, new YggdrasilAuthenticationService(Minecraft.getInstance().getProxy()));
    }

    public static boolean setSession(User user, YggdrasilAuthenticationService authService) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (originalUser == null) originalUser = mc.getUser();
            AmpereMinecraftAccessor accessor = (AmpereMinecraftAccessor) mc;
            Services services = Services.create(authService, mc.gameDirectory);
            UserApiService apiService = authService.createUserApiService(user.getAccessToken());
            Path skinCachePath = mc.gameDirectory.toPath().resolve("assets").resolve("skins");

            accessor.Ampere$setServices(services);
            accessor.Ampere$setUser(user);
            accessor.Ampere$setUserApiService(apiService);
            accessor.Ampere$setPlayerSocialManager(new PlayerSocialManager(mc, apiService));
            accessor.Ampere$setProfileKeyPairManager(ProfileKeyPairManager.create(apiService, user, mc.gameDirectory.toPath()));
            accessor.Ampere$setReportingContext(ReportingContext.create(ReportEnvironment.local(), apiService));
            accessor.Ampere$setProfileFuture(CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(mc.getUser().getProfileId(), true), Util.nonCriticalIoPool()));
            accessor.Ampere$setSkinManager(new SkinManager(skinCachePath, services, new SkinTextureDownloader(mc.getProxy(), mc.getTextureManager(), mc), mc));
            return true;
        } catch (Exception e) {
            AmpereClientAddon.LOG.error("Failed to switch Ampere account session", e);
            return false;
        }
    }
}
