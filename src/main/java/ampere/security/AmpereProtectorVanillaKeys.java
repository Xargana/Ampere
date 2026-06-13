package ampere.security;

import ampere.AmpereClientAddon;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AmpereProtectorVanillaKeys {

    private static final boolean DEBUG = Boolean.getBoolean("Ampere.protector.debug");
    private static final Object LOCK = new Object();
    private static volatile Set<String> KEYS;

    private AmpereProtectorVanillaKeys() {
    }

    public static boolean contains(String key) {
        if (key == null || key.isEmpty()) return false;
        return ensureLoaded().contains(key);
    }

    public static boolean isLoaded() {
        return KEYS != null;
    }

    private static Set<String> ensureLoaded() {
        Set<String> current = KEYS;
        if (current != null) return current;
        synchronized (LOCK) {
            if (KEYS == null) {
                KEYS = load();
            }
            return KEYS;
        }
    }

    private static Set<String> load() {
        try (InputStream in = Minecraft.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            if (in == null) {
                AmpereClientAddon.LOG.warn("[AmpereProtector] vanilla en_us.json not on classpath; falling back to blocklist only.");
                return Collections.emptySet();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> out = new HashSet<>(root.size() * 2);
            for (var entry : root.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    out.add(entry.getKey());
                }
            }
            if (DEBUG) {
                AmpereClientAddon.LOG.debug("[AmpereProtector] Loaded {} vanilla translation keys.", out.size());
            }
            return Collections.unmodifiableSet(out);
        } catch (Exception e) {
            AmpereClientAddon.LOG.warn("[AmpereProtector] Failed to load vanilla en_us.json: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    public static void primeAsync() {
        Thread t = new Thread(AmpereProtectorVanillaKeys::ensureLoaded, "AmpereProtector-Vanilla-Keys");
        t.setDaemon(true);
        t.start();
    }
}
