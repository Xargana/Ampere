package ampere;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class AmpereClientAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final String MOD_ID = "Ampere";
    public static final java.io.File FOLDER = FabricLoader.getInstance().getConfigDir().resolve("Ampere").toFile();

    static {
        FOLDER.mkdirs();
    }

    private AmpereClientAddon() {}
}
