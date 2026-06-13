package ampere.util;

public interface AmpereSpecialGuiActions {
    void Ampere$closeWithPacket();

    void Ampere$closeWithoutPacket();

    void Ampere$desync();

    default void Ampere$closeWithPacket(boolean notify) {
        Ampere$closeWithPacket();
    }

    default void Ampere$closeWithoutPacket(boolean notify) {
        Ampere$closeWithoutPacket();
    }

    default void Ampere$desync(boolean notify) {
        Ampere$desync();
    }
}
