package ampere.util;

import ampere.AmpereClientAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AmpereProxyManager implements Iterable<AmpereProxy> {
    private static final AmpereProxyManager INSTANCE = new AmpereProxyManager();
    private final List<AmpereProxy> proxies = new ArrayList<>();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private boolean loaded;
    private int timeoutMs = 5000;
    private int threads = 8;
    private int retries = 1;
    private boolean sortByLatency = true;
    private boolean pruneDead = true;
    private int pruneLatency = 2000;
    private int pruneToCount = 0;

    private static final Pattern PROXY_PATTERN = Pattern.compile("^(?:([\\w\\s]+)=)?((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])(?i:@(socks[45]))?$", Pattern.MULTILINE);
    private static final Pattern PROXY_PATTERN_WEBSHARE = Pattern.compile("^((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5]):([^:]+)(?::(.+))?$", Pattern.MULTILINE);
    private static final Pattern PROXY_PATTERN_URI = Pattern.compile("^(?:(?<type>socks|socks4|socks5)://)?(?:(?<user>[\\w~-]+)(:(?<pass>[\\w~-]+))?@)?(?<addr>(?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(?<port>\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])$", Pattern.MULTILINE);

    private AmpereProxyManager() {
    }

    public static AmpereProxyManager get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    private File saveFile() {
        return new File(Minecraft.getInstance().gameDirectory, "Ampere-proxies.nbt");
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = saveFile();
        if (!file.exists()) return;
        try {
            CompoundTag tag = NbtIo.read(file.toPath());
            if (tag == null) return;
            timeoutMs = tag.getIntOr("timeoutMs", timeoutMs);
            threads = tag.getIntOr("threads", threads);
            retries = tag.getIntOr("retries", retries);
            sortByLatency = tag.getBooleanOr("sortByLatency", sortByLatency);
            pruneDead = tag.getBooleanOr("pruneDead", pruneDead);
            pruneLatency = tag.getIntOr("pruneLatency", pruneLatency);
            pruneToCount = tag.getIntOr("pruneToCount", pruneToCount);
            proxies.clear();
            ListTag list = tag.getListOrEmpty("proxies");
            for (Tag element : list) {
                if (element instanceof CompoundTag compoundTag) proxies.add(new AmpereProxy().fromTag(compoundTag));
            }
        } catch (Exception e) {
            AmpereClientAddon.LOG.error("Failed to load Ampere proxies", e);
        }
    }

    public synchronized void save() {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putInt("timeoutMs", timeoutMs);
            tag.putInt("threads", threads);
            tag.putInt("retries", retries);
            tag.putBoolean("sortByLatency", sortByLatency);
            tag.putBoolean("pruneDead", pruneDead);
            tag.putInt("pruneLatency", pruneLatency);
            tag.putInt("pruneToCount", pruneToCount);
            ListTag list = new ListTag();
            for (AmpereProxy proxy : proxies) list.add(proxy.toTag());
            tag.put("proxies", list);
            NbtIo.write(tag, saveFile().toPath());
        } catch (Exception e) {
            AmpereClientAddon.LOG.error("Failed to save Ampere proxies", e);
        }
    }

    public synchronized List<AmpereProxy> all() {
        return new ArrayList<>(proxies);
    }

    public synchronized boolean add(AmpereProxy proxy) {
        if (proxy == null || !proxy.isValid() || proxies.contains(proxy)) return false;
        if (proxies.isEmpty()) proxy.enabled = true;
        proxies.add(proxy);
        save();
        return true;
    }

    public synchronized void remove(AmpereProxy proxy) {
        if (proxies.remove(proxy)) save();
    }

    public synchronized boolean update(AmpereProxy existing, AmpereProxy updated) {
        if (existing == null || updated == null || !updated.isValid()) return false;
        int index = -1;
        for (int i = 0; i < proxies.size(); i++) {
            AmpereProxy proxy = proxies.get(i);
            if (proxy == existing) {
                index = i;
                break;
            }
        }
        if (index < 0) return false;
        for (int i = 0; i < proxies.size(); i++) {
            if (i != index && proxies.get(i).equals(updated)) return false;
        }
        boolean identityChanged = existing.type != updated.type || existing.port != updated.port || !java.util.Objects.equals(existing.address, updated.address);
        existing.name = updated.name;
        existing.type = updated.type;
        existing.address = updated.address;
        existing.port = updated.port;
        existing.username = updated.username;
        existing.password = updated.password;
        if (identityChanged) {
            existing.status = AmpereProxy.Status.UNCHECKED;
            existing.latency = 0L;
        }
        save();
        return true;
    }

    public synchronized void setEnabled(AmpereProxy proxy, boolean enabled) {
        for (AmpereProxy current : proxies) current.enabled = false;
        if (proxy != null) proxy.enabled = enabled;
        save();
    }

    public synchronized AmpereProxy getEnabled() {
        for (AmpereProxy proxy : proxies) {
            if (proxy.enabled && proxy.isValid()) return proxy;
        }
        return null;
    }

    public boolean isRefreshing() {
        return refreshing.get();
    }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = Math.max(1, v); save(); }
    public int getThreads() { return threads; }
    public void setThreads(int v) { this.threads = Math.max(1, v); save(); }
    public int getRetries() { return retries; }
    public void setRetries(int v) { this.retries = Math.max(0, v); save(); }
    public boolean isSortByLatency() { return sortByLatency; }
    public void setSortByLatency(boolean v) { this.sortByLatency = v; save(); }
    public boolean isPruneDead() { return pruneDead; }
    public void setPruneDead(boolean v) { this.pruneDead = v; save(); }
    public int getPruneLatency() { return pruneLatency; }
    public void setPruneLatency(int v) { this.pruneLatency = Math.max(0, v); save(); }
    public int getPruneToCount() { return pruneToCount; }
    public void setPruneToCount(int v) { this.pruneToCount = Math.max(0, v); save(); }

    public void checkProxies(boolean all) {
        List<AmpereProxy> snapshot;
        synchronized (this) {
            if (refreshing.get() || proxies.isEmpty()) return;
            snapshot = new ArrayList<>(proxies);
        }
        refreshing.set(true);
        Thread thread = new Thread(() -> {
            try (ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(threads, snapshot.size())))) {
                for (AmpereProxy proxy : snapshot) {
                    if (all || proxy.status == AmpereProxy.Status.UNCHECKED) {
                        executor.execute(() -> {
                            int result = proxy.checkStatus(timeoutMs);
                            int attempts = 0;
                            while (result == 3 && attempts < retries) {
                                result = proxy.checkStatus(timeoutMs);
                                attempts++;
                            }
                        });
                    }
                }
            } finally {
                refreshing.set(false);
            }
        }, "Ampere-Proxy-Refresh");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void clean() {
        if (refreshing.get()) return;
        proxies.removeIf(proxy -> pruneDead && proxy.status == AmpereProxy.Status.DEAD);
        proxies.removeIf(proxy -> pruneLatency > 0 && proxy.status == AmpereProxy.Status.ALIVE && proxy.latency >= pruneLatency);
        List<AmpereProxy> sorted = new ArrayList<>(proxies);
        sorted.sort((a, b) -> Long.compare(a.status == AmpereProxy.Status.ALIVE ? a.latency : Long.MAX_VALUE, b.status == AmpereProxy.Status.ALIVE ? b.latency : Long.MAX_VALUE));
        if (pruneToCount > 0 && sorted.size() > pruneToCount) {
            sorted.subList(pruneToCount, sorted.size()).clear();
            proxies.removeIf(proxy -> !sorted.contains(proxy));
        }
        if (sortByLatency) {
            proxies.clear();
            proxies.addAll(sorted);
        }
        save();
    }

    public synchronized int importFromFile(File file) {
        int added = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                AmpereProxy proxy = parseProxyLine(line.trim());
                if (proxy != null && add(proxy)) added++;
            }
        } catch (Exception e) {
            AmpereClientAddon.LOG.error("Failed to import proxies", e);
        }
        return added;
    }

    private static AmpereProxy parseProxyLine(String line) {
        if (line.isBlank() || line.startsWith("#")) return null;
        Matcher m = PROXY_PATTERN.matcher(line);
        if (m.find()) return buildProxy(m.group(1), normalizeAddress(m.group(2)), Integer.parseInt(m.group(3)), m.group(4), AmpereProxyType.Socks4);
        m = PROXY_PATTERN_WEBSHARE.matcher(line);
        if (m.find()) {
            AmpereProxy proxy = buildProxy(null, normalizeAddress(m.group(1)), Integer.parseInt(m.group(2)), null, AmpereProxyType.Socks5);
            if (m.group(3) != null) proxy.username = m.group(3);
            if (m.group(4) != null) proxy.password = m.group(4);
            return proxy;
        }
        m = PROXY_PATTERN_URI.matcher(line);
        if (m.find()) {
            String typeName = m.group("type");
            AmpereProxyType defaultType = m.group("pass") != null || "socks".equals(typeName) ? AmpereProxyType.Socks5 : AmpereProxyType.Socks4;
            AmpereProxy proxy = buildProxy(null, normalizeAddress(m.group("addr")), Integer.parseInt(m.group("port")), typeName, defaultType);
            if (m.group("user") != null) proxy.username = m.group("user");
            if (m.group("pass") != null) proxy.password = m.group("pass");
            return proxy;
        }
        return null;
    }

    private static String normalizeAddress(String address) {
        return address == null ? "" : address.replaceAll("\\b0+\\B", "");
    }

    private static AmpereProxy buildProxy(String name, String address, int port, String typeName, AmpereProxyType defaultType) {
        AmpereProxy proxy = new AmpereProxy();
        proxy.name = name == null ? "" : name.trim();
        proxy.address = address;
        proxy.port = port;
        proxy.type = defaultType == null ? AmpereProxyType.Socks5 : defaultType;
        if (typeName != null) {
            String lower = typeName.toLowerCase();
            if (lower.contains("4")) proxy.type = AmpereProxyType.Socks4;
            else if (lower.contains("5")) proxy.type = AmpereProxyType.Socks5;
        }
        return proxy;
    }

    @Override
    public Iterator<AmpereProxy> iterator() {
        return all().iterator();
    }
}
