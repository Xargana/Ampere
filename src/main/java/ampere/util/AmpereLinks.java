package ampere.util;

import net.minecraft.util.Util;

public final class AmpereLinks {
    public static final String DISCORD = "https://discord.com/invite/JZ7XgUCtBu";
    public static final String Ampere_INC_DISCORD = "https://discord.gg/V8GsKP6k5u";
    public static final String KOFI = "https://ko-fi.com/melonik";
    public static final String CRYPTO_DONATE = "https://nowpayments.io/donation/melonikAmpere";

    private AmpereLinks() {
    }

    public static void open(String url) {
        try {
            Util.getPlatform().openUri(url);
        } catch (Throwable ignored) {
        }
    }
}
