package ampere.security;

public final class AmpereProtectorServerPackFailureGuard {
    private static volatile long suppressServerPacksUntilMs;

    private AmpereProtectorServerPackFailureGuard() {
    }

    public static void suppressServerPacksTemporarily() {
        suppressServerPacksUntilMs = Math.max(suppressServerPacksUntilMs, System.currentTimeMillis() + 15_000L);
    }

    public static boolean shouldSuppressServerPacks() {
        return System.currentTimeMillis() < suppressServerPacksUntilMs;
    }

    public static void clear() {
        suppressServerPacksUntilMs = 0L;
    }
}
