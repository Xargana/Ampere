package ampere.util;

import net.minecraft.client.KeyMapping;

public interface AmpereKeyMappingBridge {
    static AmpereKeyMappingBridge of(KeyMapping mapping) {
        return (AmpereKeyMappingBridge) mapping;
    }

    boolean Ampere$isActuallyDown();

    void Ampere$resetPressedState();

    void Ampere$simulatePress(boolean pressed);
}
