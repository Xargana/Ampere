package ampere.util;

import net.minecraft.core.BlockPos;

public interface AmpereSignEditAccess {
    BlockPos Ampere$getSignPos();
    boolean Ampere$isFrontText();
    String[] Ampere$getSignLines();
}
