package autismclient.util;

import autismclient.AutismClientAddon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AutismPayloadChannelListeners {
    public enum Direction {
        ANY, C2S, S2C;

        public static Direction from(String value) {
            if (value == null) return ANY;
            try {
                return Direction.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ANY;
            }
        }

        public boolean accepts(String packetDirection) {
            return true;
        }
    }

    public record Preset(String label, String pattern, Direction direction, String group) {
        public String key() {
            return ruleKey(pattern, direction.name());
        }
    }

    public record Match(String label, String pattern, Direction direction) {
        public String searchText() {
            return (label + " " + pattern + " " + direction.name()).toLowerCase(Locale.ROOT);
        }
    }

    private static final List<Preset> PRESETS = createPresets();
    private static final List<String> CATALOG_WARNINGS = validateCatalog(PRESETS);
    static {
        for (String warning : CATALOG_WARNINGS) {
            AutismClientAddon.LOG.warn("[PayloadListeners] Preset catalog warning: {}", warning);
        }
    }
    private final List<AutismConfig.PayloadChannelListenerRule> rules = new ArrayList<>();

    public AutismPayloadChannelListeners() {
        load();
    }

    public void load() {
        rules.clear();
        AutismConfig config = AutismConfig.getGlobal();
        if (config.packetLoggerPayloadListeners == null) {
            config.packetLoggerPayloadListeners = new ArrayList<>();
        }
        for (AutismConfig.PayloadChannelListenerRule rule : config.packetLoggerPayloadListeners) {
            AutismConfig.PayloadChannelListenerRule clean = cleanRule(rule);
            if (clean != null) rules.add(clean);
        }
    }

    public List<AutismConfig.PayloadChannelListenerRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    public List<Preset> presets() {
        return PRESETS;
    }

    public static List<Preset> presetCatalog() {
        return PRESETS;
    }

    public static List<String> catalogWarnings() {
        return CATALOG_WARNINGS;
    }

    public static boolean presetMatchesChannel(Preset preset, String channelName) {
        if (preset == null) return false;
        return matchesPattern(preset.pattern(), normalizeChannel(channelName));
    }

    public static boolean presetCanIdentifyPlugin(Preset preset) {
        if (preset == null) return false;
        String label = preset.label() == null ? "" : preset.label().trim().toLowerCase(Locale.ROOT);
        String pattern = normalizePattern(preset.pattern());
        if (pattern.isBlank() || "Minecraft".equalsIgnoreCase(preset.group())) return false;
        if (label.contains("family") || label.contains("namespace")) return false;
        int colon = pattern.indexOf(':');
        if (colon <= 0) return false;
        String namespace = pattern.substring(0, colon);
        if (isGenericDiscoveryNamespace(namespace)) return false;
        return true;
    }

    private static boolean isGenericDiscoveryNamespace(String namespace) {
        return switch (namespace == null ? "" : namespace.toLowerCase(Locale.ROOT)) {
            case "ah", "auction", "auctions", "auctionhouse", "shop", "shops", "store", "stores",
                 "crate", "crates", "casino", "slots", "roulette", "gui", "menu", "menus",
                 "inventory", "inventories", "chest", "chests", "storage", "container", "containers",
                 "backpack", "backpacks", "shulker", "shulkers", "enderchest", "echest",
                 "pv", "vault", "vaults", "minion", "minions", "mine", "mines",
                 "claim", "claims", "region", "regions", "protection", "protections" -> true;
            default -> false;
        };
    }

    public boolean addCustom(String label, String pattern, Direction direction) {
        return addRule(label, pattern, direction, false, true);
    }

    public boolean addPreset(Preset preset) {
        if (preset == null) return false;
        return addRule(preset.label(), preset.pattern(), preset.direction(), true, true);
    }

    public boolean addOrEnablePreset(Preset preset) {
        if (preset == null) return false;
        String key = preset.key();
        for (AutismConfig.PayloadChannelListenerRule rule : rules) {
            if (ruleKey(rule.pattern, rule.direction).equals(key)) {
                if (!rule.enabled) {
                    rule.enabled = true;
                    save();
                    return true;
                }
                return false;
            }
        }
        return addPreset(preset);
    }

    public void toggle(int index) {
        if (index < 0 || index >= rules.size()) return;
        AutismConfig.PayloadChannelListenerRule rule = rules.get(index);
        rule.enabled = !rule.enabled;
        save();
    }

    public void remove(int index) {
        if (index < 0 || index >= rules.size()) return;
        rules.remove(index);
        save();
    }

    public void resetPresetRules() {
        boolean changed = false;
        for (Iterator<AutismConfig.PayloadChannelListenerRule> it = rules.iterator(); it.hasNext();) {
            AutismConfig.PayloadChannelListenerRule rule = it.next();
            if (rule != null && rule.preset) {
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
    }

    public boolean hasRuleFor(Preset preset) {
        if (preset == null) return false;
        String key = preset.key();
        for (AutismConfig.PayloadChannelListenerRule rule : rules) {
            if (ruleKey(rule.pattern, rule.direction).equals(key)) return true;
        }
        return false;
    }

    public boolean isRuleEnabledFor(Preset preset) {
        if (preset == null) return false;
        String key = preset.key();
        for (AutismConfig.PayloadChannelListenerRule rule : rules) {
            if (ruleKey(rule.pattern, rule.direction).equals(key)) return rule.enabled;
        }
        return false;
    }

    public boolean hasEnabledRules() {
        for (AutismConfig.PayloadChannelListenerRule rule : rules) {
            if (rule != null && rule.enabled) return true;
        }
        return false;
    }

    public int presetCountForGroup(String group) {
        if (group == null || group.isBlank()) return 0;
        int count = 0;
        for (Preset preset : PRESETS) {
            if (group.equals(preset.group())) count++;
        }
        return count;
    }

    public int enabledPresetCountForGroup(String group) {
        if (group == null || group.isBlank()) return 0;
        int count = 0;
        for (Preset preset : PRESETS) {
            if (group.equals(preset.group()) && isRuleEnabledFor(preset)) count++;
        }
        return count;
    }

    public boolean isGroupFullyEnabled(String group) {
        int total = presetCountForGroup(group);
        return total > 0 && enabledPresetCountForGroup(group) == total;
    }

    public void toggleGroup(String group) {
        if (group == null || group.isBlank()) return;
        setGroupEnabled(group, !isGroupFullyEnabled(group));
    }

    private void setGroupEnabled(String group, boolean enabled) {
        if (group == null || group.isBlank()) return;
        boolean changed = false;
        Set<String> groupKeys = new HashSet<>();
        for (Preset preset : PRESETS) {
            if (group.equals(preset.group())) groupKeys.add(preset.key());
        }
        if (groupKeys.isEmpty()) return;

        if (!enabled) {
            for (Iterator<AutismConfig.PayloadChannelListenerRule> it = rules.iterator(); it.hasNext();) {
                AutismConfig.PayloadChannelListenerRule rule = it.next();
                if (rule != null && rule.preset && groupKeys.contains(ruleKey(rule.pattern, rule.direction))) {
                    it.remove();
                    changed = true;
                }
            }
        } else {
            for (Preset preset : PRESETS) {
                if (!group.equals(preset.group())) continue;
                if (enablePresetWithoutSaving(preset)) changed = true;
            }
        }
        if (changed) save();
    }

    public void toggleOrAddPreset(Preset preset) {
        if (preset == null) return;
        String key = preset.key();
        for (int i = 0; i < rules.size(); i++) {
            AutismConfig.PayloadChannelListenerRule rule = rules.get(i);
            if (ruleKey(rule.pattern, rule.direction).equals(key)) {
                if (rule.enabled) {
                    rules.remove(i);
                    save();
                } else {
                    rule.enabled = true;
                    rule.preset = true;
                    save();
                }
                return;
            }
        }
        addPreset(preset);
    }

    private boolean enablePresetWithoutSaving(Preset preset) {
        if (preset == null) return false;
        String key = preset.key();
        for (AutismConfig.PayloadChannelListenerRule rule : rules) {
            if (ruleKey(rule.pattern, rule.direction).equals(key)) {
                boolean changed = false;
                if (!rule.enabled) {
                    rule.enabled = true;
                    changed = true;
                }
                if (!rule.preset) {
                    rule.preset = true;
                    changed = true;
                }
                return changed;
            }
        }
        AutismConfig.PayloadChannelListenerRule rule = new AutismConfig.PayloadChannelListenerRule();
        rule.pattern = normalizePattern(preset.pattern());
        rule.label = preset.label();
        rule.direction = Direction.ANY.name();
        rule.enabled = true;
        rule.preset = true;
        rules.add(rule);
        return true;
    }

    public void enableAll() {
        boolean changed = false;
        for (AutismConfig.PayloadChannelListenerRule rule : rules) {
            if (rule != null && !rule.enabled) {
                rule.enabled = true;
                changed = true;
            }
        }
        for (Preset preset : PRESETS) {
            if (enablePresetWithoutSaving(preset)) changed = true;
        }
        if (changed) save();
    }

    public void disableAll() {
        boolean changed = false;
        for (Iterator<AutismConfig.PayloadChannelListenerRule> it = rules.iterator(); it.hasNext();) {
            AutismConfig.PayloadChannelListenerRule rule = it.next();
            if (rule == null) {
                it.remove();
                changed = true;
            } else if (rule.preset) {
                it.remove();
                changed = true;
            } else if (rule.enabled) {
                rule.enabled = false;
                changed = true;
            }
        }
        if (changed) save();
    }

    public void enableDefaultRecommended() {
        boolean changed = false;
        for (Preset preset : PRESETS) {
            if (isDefaultRecommendedPreset(preset) && enablePresetWithoutSaving(preset)) {
                changed = true;
            }
        }
        if (changed) save();
    }

    public Match match(AutismPayloadSupport.PayloadSnapshot snapshot, String packetDirection) {
        if (snapshot == null || snapshot.channel() == null || snapshot.channel().isBlank()) return null;
        return matchChannel(snapshot.channel(), packetDirection);
    }

    public Match matchChannel(String channelName, String packetDirection) {
        String channel = normalizeChannel(channelName);
        if (channel.isBlank()) return null;
        for (AutismConfig.PayloadChannelListenerRule rule : rules) {
            if (rule == null || !rule.enabled) continue;
            Direction direction = Direction.from(rule.direction);
            if (!direction.accepts(packetDirection)) continue;
            if (matchesPattern(rule.pattern, channel)) {
                String label = rule.label == null || rule.label.isBlank() ? rule.pattern : rule.label;
                return new Match(label, rule.pattern, direction);
            }
        }
        return null;
    }

    public String listenerSearchText(AutismPayloadSupport.PayloadSnapshot snapshot, String packetDirection) {
        Match match = match(snapshot, packetDirection);
        return match == null ? "" : match.searchText();
    }

    private static boolean isDefaultRecommendedPreset(Preset preset) {
        if (preset == null) return false;
        String pattern = normalizePattern(preset.pattern());
        return switch (pattern) {
            case "minecraft:brand", "minecraft:register", "minecraft:unregister",
                 "register", "unregister", "bungeecord", "bungeecord:main",
                 "velocity:*", "viaversion:*", "viabackwards:*", "geyser:*",
                 "floodgate:*", "fabric:*", "worldedit:*", "worldedit:cui*",
                 "oraxen:*", "itemsadder:*", "nexo:*", "modelengine:*",
                 "voicechat:*", "plasmovoice:*" -> true;
            default -> false;
        };
    }

    public void save() {
        AutismConfig config = AutismConfig.getGlobal();
        config.packetLoggerPayloadListeners = new ArrayList<>(rules);
        config.save();
        autismclient.modules.AutismModule.get().invalidatePayloadListenerCache();
    }

    public static String normalizePattern(String pattern) {
        if (pattern == null) return "";
        return normalizeChannel(pattern.trim());
    }

    private static AutismConfig.PayloadChannelListenerRule cleanRule(AutismConfig.PayloadChannelListenerRule rule) {
        if (rule == null) return null;
        String pattern = normalizePattern(rule.pattern);
        if (pattern.isBlank()) return null;
        AutismConfig.PayloadChannelListenerRule clean = new AutismConfig.PayloadChannelListenerRule();
        clean.pattern = pattern;
        clean.label = rule.label == null || rule.label.isBlank() ? pattern : rule.label.trim();
        clean.direction = Direction.ANY.name();
        clean.enabled = rule.enabled;
        clean.preset = rule.preset;
        return clean;
    }

    private boolean addRule(String label, String pattern, Direction direction, boolean preset, boolean enabled) {
        String normalized = normalizePattern(pattern);
        if (normalized.isBlank()) return false;
        Direction dir = Direction.ANY;
        String key = ruleKey(normalized, dir.name());
        for (AutismConfig.PayloadChannelListenerRule existing : rules) {
            if (ruleKey(existing.pattern, existing.direction).equals(key)) {
                boolean changed = false;
                if (!existing.enabled && enabled) {
                    existing.enabled = true;
                    changed = true;
                }
                if (existing.label == null || existing.label.isBlank() || existing.label.equals(existing.pattern)) {
                    existing.label = label == null || label.isBlank() ? normalized : label.trim();
                    changed = true;
                }
                if (changed) save();
                return changed;
            }
        }
        AutismConfig.PayloadChannelListenerRule rule = new AutismConfig.PayloadChannelListenerRule();
        rule.pattern = normalized;
        rule.label = label == null || label.isBlank() ? normalized : label.trim();
        rule.direction = dir.name();
        rule.enabled = enabled;
        rule.preset = preset;
        rules.add(rule);
        save();
        return true;
    }

    private static boolean matchesPattern(String pattern, String normalizedChannel) {
        String normalizedPattern = normalizePattern(pattern);
        if (normalizedPattern.isEmpty() || normalizedChannel.isEmpty()) return false;
        if ("*".equals(normalizedPattern)) return true;
        if (normalizedPattern.indexOf('*') < 0) return normalizedChannel.equals(normalizedPattern);
        return wildcardMatches(normalizedPattern, normalizedChannel);
    }

    private static boolean wildcardMatches(String pattern, String text) {
        int p = 0;
        int t = 0;
        int star = -1;
        int mark = 0;
        while (t < text.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == text.charAt(t))) {
                p++;
                t++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                mark = t;
            } else if (star != -1) {
                p = star + 1;
                t = ++mark;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') p++;
        return p == pattern.length();
    }

    private static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
    }

    private static String ruleKey(String pattern, String direction) {
        return normalizePattern(pattern) + "|" + Direction.from(direction).name();
    }

    private static List<String> validateCatalog(List<Preset> presets) {
        if (presets == null || presets.isEmpty()) return List.of("catalog is empty");
        List<String> warnings = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Map<String, String> groupCase = new LinkedHashMap<>();
        for (Preset preset : presets) {
            if (preset == null) {
                warnings.add("null preset row");
                continue;
            }
            String key = preset.key();
            if (!keys.add(key)) warnings.add("duplicate pattern " + preset.pattern());
            String group = preset.group() == null ? "" : preset.group().trim();
            if (group.isBlank()) warnings.add("missing group for " + preset.pattern());
            String folded = group.toLowerCase(Locale.ROOT);
            String existing = groupCase.putIfAbsent(folded, group);
            if (existing != null && !existing.equals(group)) {
                warnings.add("category casing differs: " + existing + " / " + group);
            }
            if (presetCanIdentifyPlugin(preset) && preset.pattern().indexOf('*') >= 0
                && !preset.pattern().endsWith(":*") && !preset.pattern().endsWith(":cui*")) {
                warnings.add("identity-capable preset uses broad wildcard " + preset.pattern());
            }
        }
        return Collections.unmodifiableList(warnings);
    }

    private static List<Preset> createPresets() {
        List<Preset> presets = new ArrayList<>();
        add(presets, "Minecraft Brand", "minecraft:brand", "Minecraft");
        add(presets, "Minecraft Register", "minecraft:register", "Minecraft");
        add(presets, "Minecraft Unregister", "minecraft:unregister", "Minecraft");
        add(presets, "Legacy REGISTER", "REGISTER", "Minecraft");
        add(presets, "Legacy UNREGISTER", "UNREGISTER", "Minecraft");
        add(presets, "BungeeCord", "BungeeCord", "Proxy");
        add(presets, "BungeeCord Main", "bungeecord:main", "Proxy");
        add(presets, "Velocity", "velocity:*", "Proxy");
        add(presets, "ViaVersion", "viaversion:*", "Proxy");
        add(presets, "ViaBackwards", "viabackwards:*", "Proxy");
        add(presets, "Geyser", "geyser:*", "Proxy");
        add(presets, "Floodgate", "floodgate:*", "Proxy");
        add(presets, "Fabric", "fabric:*", "Mod API");
        add(presets, "WorldEdit", "worldedit:*", "Mod API");
        add(presets, "WorldEdit CUI", "worldedit:cui*", "Mod API");
        add(presets, "Oraxen", "oraxen:*", "Items");
        add(presets, "ItemsAdder", "itemsadder:*", "Items");
        add(presets, "Nexo", "nexo:*", "Items");
        add(presets, "ModelEngine", "modelengine:*", "Items");
        add(presets, "MythicCrucible", "mythiccrucible:*", "Items");
        add(presets, "MMOItems", "mmoitems:*", "Items");
        add(presets, "ExecutableItems", "executableitems:*", "Items");
        add(presets, "ExecutableBlocks", "executableblocks:*", "Items");
        add(presets, "Nova", "nova:*", "Items");
        add(presets, "EcoItems", "ecoitems:*", "Items");
        add(presets, "EcoArmor", "ecoarmor:*", "Items");
        add(presets, "EcoEnchants", "ecoenchants:*", "Items");
        add(presets, "Vault", "vault:*", "Economy");
        add(presets, "Treasury", "treasury:*", "Economy");
        add(presets, "EssentialsX", "essentials:*", "Economy");
        add(presets, "EssentialsX Alt", "essentialsx:*", "Economy");
        add(presets, "CMI", "cmi:*", "Economy");
        add(presets, "TheNewEconomy", "theneweconomy:*", "Economy");
        add(presets, "TNE", "tne:*", "Economy");
        add(presets, "PlayerPoints", "playerpoints:*", "Economy");
        add(presets, "VotingPlugin Points", "votingplugin:*", "Economy");
        add(presets, "CoinsEngine", "coinsengine:*", "Economy");
        add(presets, "UltraEconomy", "ultraeconomy:*", "Economy");
        add(presets, "XConomy", "xconomy:*", "Economy");
        add(presets, "PEconomy", "peconomy:*", "Economy");
        add(presets, "CMIEInjector", "cmieinjector:*", "Economy");
        add(presets, "GemsEconomy", "gemseconomy:*", "Economy");
        add(presets, "TokenManager", "tokenmanager:*", "Economy");
        add(presets, "RedisEconomy", "rediseconomy:*", "Economy");
        add(presets, "JobsReborn", "jobs:*", "Economy");
        add(presets, "JobsReborn Alt", "jobsreborn:*", "Economy");
        add(presets, "Aurelium", "aurelium:*", "Economy");
        add(presets, "AureliumSkills", "aureliumskills:*", "Economy");
        add(presets, "AuctionHouse", "auctionhouse:*", "Auctions");
        add(presets, "AuctionMaster", "auctionmaster:*", "Auctions");
        add(presets, "CrazyAuctions", "crazyauctions:*", "Auctions");
        add(presets, "CrazyAuctions Plus", "crazyauctionsplus:*", "Auctions");
        add(presets, "ExcellentAuctionHouse", "excellentauctionhouse:*", "Auctions");
        add(presets, "ExcellentAuctions", "excellentauctions:*", "Auctions");
        add(presets, "zAuctionHouse", "zauctionhouse:*", "Auctions");
        add(presets, "AuctionGUIPlus", "auctionguiplus:*", "Auctions");
        add(presets, "PlayerAuctions", "playerauctions:*", "Auctions");
        add(presets, "BetterAuction", "betterauction:*", "Auctions");
        add(presets, "EzAuction", "ezauction:*", "Auctions");
        add(presets, "NexusAuctionHouse", "nexusauctionhouse:*", "Auctions");
        add(presets, "AxAuctions", "axauctions:*", "Auctions");
        add(presets, "AdvancedAuctions", "advancedauctions:*", "Auctions");
        add(presets, "UltimateAuction", "ultimateauction:*", "Auctions");
        add(presets, "Auction", "auction:*", "Auctions");
        add(presets, "ShopGUIPlus", "shopguiplus:*", "Shops");
        add(presets, "ShopGUI+", "shopgui:*", "Shops");
        add(presets, "EconomyShopGUI", "economyshopgui:*", "Shops");
        add(presets, "EconomyShopGUI Premium", "economyshopguipremium:*", "Shops");
        add(presets, "BossShopPro", "bossshoppro:*", "Shops");
        add(presets, "BossShop", "bossshop:*", "Shops");
        add(presets, "ChestShop", "chestshop:*", "Shops");
        add(presets, "QuickShop", "quickshop:*", "Shops");
        add(presets, "QuickShop Reremake", "quickshop-hikari:*", "Shops");
        add(presets, "QuickShop Reremake Alt", "quickshopreremake:*", "Shops");
        add(presets, "GUIShop", "guishop:*", "Shops");
        add(presets, "UltimateShop", "ultimateshop:*", "Shops");
        add(presets, "zShop", "zshop:*", "Shops");
        add(presets, "BetterShop", "bettershop:*", "Shops");
        add(presets, "Shopkeepers", "shopkeepers:*", "Shops");
        add(presets, "dtlTraders", "dtltraders:*", "Shops");
        add(presets, "PlayerShops", "playershops:*", "Shops");
        add(presets, "PlayerWarps", "playerwarps:*", "Shops");
        add(presets, "DeluxeMenus", "deluxemenus:*", "Shops");
        add(presets, "CommandPanels", "commandpanels:*", "Shops");
        add(presets, "TradeSystem", "tradesystem:*", "Shops");
        add(presets, "TradePlus", "tradeplus:*", "Shops");
        add(presets, "Trades", "trades:*", "Shops");
        add(presets, "ExcellentShop", "excellentshop:*", "Shops");
        add(presets, "NightExpress", "nightexpress:*", "Frameworks");
        add(presets, "NexEngine", "nexengine:*", "Frameworks");
        add(presets, "PhoenixPlugins", "phoenixplugins:*", "Frameworks");
        add(presets, "PlaceholderAPI", "placeholderapi:*", "Frameworks");
        add(presets, "ProtocolLib", "protocollib:*", "Frameworks");
        add(presets, "PacketEvents", "packetevents:*", "Frameworks");
        add(presets, "LoneLibs", "lonelibs:*", "Frameworks");
        add(presets, "NBTAPI", "nbtapi:*", "Frameworks");

        add(presets, "DonutSMP", "donutsmp:*", "Protection");
        add(presets, "Donut", "donut:*", "Protection");
        add(presets, "Donut Claims", "donutclaims:*", "Protection");
        add(presets, "Claims", "claims:*", "Protection");
        add(presets, "Claim", "claim:*", "Protection");
        add(presets, "Regions", "regions:*", "Protection");
        add(presets, "Region", "region:*", "Protection");
        add(presets, "Protection", "protection:*", "Protection");
        add(presets, "Protections", "protections:*", "Protection");
        add(presets, "AntiGrief", "antigrief:*", "Protection");
        add(presets, "WorldGuard", "worldguard:*", "Protection");
        add(presets, "WorldGuard Regions", "worldguardregions:*", "Protection");
        add(presets, "WorldGuard ExtraFlags", "worldguardextraflags:*", "Protection");
        add(presets, "WG", "wg:*", "Protection");
        add(presets, "WG Region", "wgregion:*", "Protection");
        add(presets, "Residence", "residence:*", "Protection");
        add(presets, "Residence Plus", "residenceplus:*", "Protection");
        add(presets, "GriefPrevention", "griefprevention:*", "Protection");
        add(presets, "Grief Prevention", "grief_prevention:*", "Protection");
        add(presets, "GriefDefender", "griefdefender:*", "Protection");
        add(presets, "GPFlags", "gpflags:*", "Protection");
        add(presets, "Lands", "lands:*", "Protection");
        add(presets, "Land", "land:*", "Protection");
        add(presets, "LandClaim", "landclaim:*", "Protection");
        add(presets, "LandClaims", "landclaims:*", "Protection");
        add(presets, "UltimateClaims", "ultimateclaims:*", "Protection");
        add(presets, "UltimateLandClaim", "ultimatelandclaim:*", "Protection");
        add(presets, "ProtectionStones", "protectionstones:*", "Protection");
        add(presets, "Protection Stones", "protection_stones:*", "Protection");
        add(presets, "PreciousStones", "preciousstones:*", "Protection");
        add(presets, "RedProtect", "redprotect:*", "Protection");
        add(presets, "ClaimChunk", "claimchunk:*", "Protection");
        add(presets, "ChunkClaim", "chunkclaim:*", "Protection");
        add(presets, "ChunkClaims", "chunkclaims:*", "Protection");
        add(presets, "RClaim", "rclaim:*", "Protection");
        add(presets, "BetterClaims", "betterclaims:*", "Protection");
        add(presets, "SimpleClaimSystem", "simpleclaimsystem:*", "Protection");
        add(presets, "ClaimSystem", "claimsystem:*", "Protection");
        add(presets, "ClaimPlus", "claimplus:*", "Protection");
        add(presets, "ClaimFly", "claimfly:*", "Protection");
        add(presets, "ClaimShield", "claimshield:*", "Protection");
        add(presets, "ProtectionCore", "protectioncore:*", "Protection");
        add(presets, "Homestead", "homestead:*", "Protection");
        add(presets, "Bell Claims", "bellclaims:*", "Protection");
        add(presets, "HuskClaims", "huskclaims:*", "Protection");
        add(presets, "HuskTowns", "husktowns:*", "Protection");
        add(presets, "Towny", "towny:*", "Protection");
        add(presets, "TownyAdvanced", "townyadvanced:*", "Protection");
        add(presets, "Factions", "factions:*", "Protection");
        add(presets, "FactionsUUID", "factionsuuid:*", "Protection");
        add(presets, "MassiveFactions", "massivefactions:*", "Protection");
        add(presets, "SaberFactions", "saberfactions:*", "Protection");
        add(presets, "SuperiorFactions", "superiorfactions:*", "Protection");
        add(presets, "Kingdoms", "kingdoms:*", "Protection");
        add(presets, "KingdomsX", "kingdomsx:*", "Protection");
        add(presets, "Konquest", "konquest:*", "Protection");
        add(presets, "PlotSquared", "plotsquared:*", "Protection");
        add(presets, "Plots", "plots:*", "Protection");
        add(presets, "Plot", "plot:*", "Protection");
        add(presets, "PlotMe", "plotme:*", "Protection");
        add(presets, "SuperiorSkyblock", "superiorskyblock:*", "Protection");
        add(presets, "BentoBox", "bentobox:*", "Protection");
        add(presets, "ASkyBlock", "askyblock:*", "Protection");
        add(presets, "BSkyBlock", "bskyblock:*", "Protection");
        add(presets, "FabledSkyBlock", "fabledskyblock:*", "Protection");
        add(presets, "IridiumSkyblock", "iridiumskyblock:*", "Protection");
        add(presets, "LWC", "lwc:*", "Protection");
        add(presets, "LWCX", "lwcx:*", "Protection");
        add(presets, "Lockette", "lockette:*", "Protection");
        add(presets, "LockettePro", "lockettepro:*", "Protection");
        add(presets, "Bolt", "bolt:*", "Protection");
        add(presets, "ChestProtect", "chestprotect:*", "Protection");
        add(presets, "ChestProtection", "chestprotection:*", "Protection");
        add(presets, "CoreProtect", "coreprotect:*", "Protection");
        add(presets, "Prism", "prism:*", "Protection");
        add(presets, "LogBlock", "logblock:*", "Protection");
        add(presets, "BlockLocker", "blocklocker:*", "Protection");
        add(presets, "Open Parties And Claims", "openpartiesandclaims:*", "Protection");
        add(presets, "Open Parties Claims", "openpartiesclaims:*", "Protection");
        add(presets, "OpenPAC", "openpac:*", "Protection");
        add(presets, "Flan", "flan:*", "Protection");
        add(presets, "Cadmus", "cadmus:*", "Protection");
        add(presets, "FTB Chunks", "ftbchunks:*", "Protection");
        add(presets, "FTB Teams", "ftbteams:*", "Protection");
        add(presets, "WorldBorder", "worldborder:*", "Protection");
        add(presets, "World Border", "world_border:*", "Protection");
        add(presets, "WorldGuard Events", "worldguardevents:*", "Protection");
        add(presets, "WorldGuard Events Alt", "worldguard_events:*", "Protection");
        add(presets, "WorldGuard Flags", "worldguardflags:*", "Protection");
        add(presets, "WG Flags", "wgflags:*", "Protection");
        add(presets, "FastAsyncWorldEdit", "fastasyncworldedit:*", "Protection");
        add(presets, "FAWE", "fawe:*", "Protection");
        add(presets, "AdvancedRegionMarket", "advancedregionmarket:*", "Protection");
        add(presets, "ARM", "arm:*", "Protection");
        add(presets, "AreaShop", "areashop:*", "Protection");
        add(presets, "RegionMarket", "regionmarket:*", "Protection");
        add(presets, "RegionShop", "regionshop:*", "Protection");
        add(presets, "RegionForSale", "regionforsale:*", "Protection");
        add(presets, "RegionRent", "regionrent:*", "Protection");
        add(presets, "RentIt", "rentit:*", "Protection");
        add(presets, "PlotBorder", "plotborder:*", "Protection");
        add(presets, "PlotSquared v6", "plotsquared6:*", "Protection");
        add(presets, "PlotSquared v7", "plotsquared7:*", "Protection");
        add(presets, "PlotGUI", "plotgui:*", "Protection");
        add(presets, "PlotManager", "plotmanager:*", "Protection");
        add(presets, "PlotSystem", "plotsystem:*", "Protection");
        add(presets, "PlotClaim", "plotclaim:*", "Protection");
        add(presets, "PlotClaims", "plotclaims:*", "Protection");
        add(presets, "SuperiorSkyblock2", "superiorskyblock2:*", "Protection");
        add(presets, "IridiumSkyblock2", "iridiumskyblock2:*", "Protection");
        add(presets, "SkyBlock", "skyblock:*", "Protection");
        add(presets, "USkyBlock", "uskyblock:*", "Protection");
        add(presets, "AcidIsland", "acidisland:*", "Protection");
        add(presets, "IslandWorld", "islandworld:*", "Protection");
        add(presets, "OneBlock", "oneblock:*", "Protection");
        add(presets, "OneBlockSkyBlock", "oneblockskyblock:*", "Protection");
        add(presets, "Island", "island:*", "Protection");
        add(presets, "Islands", "islands:*", "Protection");
        add(presets, "MedievalFactions", "medievalfactions:*", "Protection");
        add(presets, "FactionsKore", "factionskore:*", "Protection");
        add(presets, "FactionsBlue", "factionsblue:*", "Protection");
        add(presets, "FactionsX", "factionsx:*", "Protection");
        add(presets, "FactionsOne", "factionsone:*", "Protection");
        add(presets, "FactionsTop", "factionstop:*", "Protection");
        add(presets, "Clans", "clans:*", "Protection");
        add(presets, "SimpleClans", "simpleclans:*", "Protection");
        add(presets, "UltimateClans", "ultimateclans:*", "Protection");
        add(presets, "BetterTeams", "betterteams:*", "Protection");
        add(presets, "Teams", "teams:*", "Protection");
        add(presets, "Guilds", "guilds:*", "Protection");
        add(presets, "Parties", "parties:*", "Protection");
        add(presets, "Party And Friends", "partyandfriends:*", "Protection");
        add(presets, "Nations", "nations:*", "Protection");
        add(presets, "NationsX", "nationsx:*", "Protection");
        add(presets, "Civs", "civs:*", "Protection");
        add(presets, "Civilizations", "civilizations:*", "Protection");
        add(presets, "Dominion", "dominion:*", "Protection");
        add(presets, "Settlements", "settlements:*", "Protection");
        add(presets, "Villages", "villages:*", "Protection");
        add(presets, "NationsGlory", "nationsglory:*", "Protection");
        add(presets, "BlockProt", "blockprot:*", "Protection");
        add(presets, "ColdContainerLock", "coldcontainerlock:*", "Protection");
        add(presets, "PrivateStorage", "privatestorage:*", "Protection");
        add(presets, "PrivateChests", "privatechests:*", "Protection");
        add(presets, "SecureChests", "securechests:*", "Protection");
        add(presets, "ChestLock", "chestlock:*", "Protection");
        add(presets, "ContainerLock", "containerlock:*", "Protection");
        add(presets, "ContainerLocks", "containerlocks:*", "Protection");
        add(presets, "LockSecurity", "locksecurity:*", "Protection");
        add(presets, "SafeDoors", "safedoors:*", "Protection");
        add(presets, "DoorsReloaded", "doorsreloaded:*", "Protection");
        add(presets, "Ledger", "ledger:*", "Protection");
        add(presets, "HawkEye", "hawkeye:*", "Protection");
        add(presets, "Guardian", "guardian:*", "Protection");
        add(presets, "BlockLogger", "blocklogger:*", "Protection");
        add(presets, "RollbackCore", "rollbackcore:*", "Protection");
        add(presets, "Rollback", "rollback:*", "Protection");
        add(presets, "Insights", "insights:*", "Protection");
        add(presets, "Plan AntiGrief", "planantigrief:*", "Protection");
        add(presets, "IllegalStack", "illegalstack:*", "Protection");
        add(presets, "ExploitFixer", "exploitfixer:*", "Protection");
        add(presets, "IllegalItems", "illegalitems:*", "Protection");
        add(presets, "CrazyCrates", "crazycrates:*", "Crates");
        add(presets, "ExcellentCrates", "excellentcrates:*", "Crates");
        add(presets, "GoldenCrates", "goldencrates:*", "Crates");
        add(presets, "SpecializedCrates", "specializedcrates:*", "Crates");
        add(presets, "CrateReloaded", "cratereloaded:*", "Crates");
        add(presets, "PhoenixCrates", "phoenixcrates:*", "Crates");
        add(presets, "Phoenix Crates Alt", "phoenix_crates:*", "Crates");
        add(presets, "AdvancedCrates", "advancedcrates:*", "Crates");
        add(presets, "AquaticCrates", "aquaticcrates:*", "Crates");
        add(presets, "PulseCrates", "pulsecrates:*", "Crates");
        add(presets, "MysteryCrates", "mysterycrates:*", "Crates");
        add(presets, "CratesPlus", "cratesplus:*", "Crates");
        add(presets, "CrateKeys", "cratekeys:*", "Crates");
        add(presets, "GoldenKey", "goldenkey:*", "Crates");
        add(presets, "DeluxeCrates", "deluxecrates:*", "Crates");
        add(presets, "VoidCrates", "voidcrates:*", "Crates");
        add(presets, "CrazyEnvoys", "crazyenvoys:*", "Crates");
        add(presets, "ExcellentEnchants Crates", "excellentenchants:*", "Crates");
        add(presets, "CrazyRewards", "crazyrewards:*", "Crates");
        add(presets, "LootChest", "lootchest:*", "Crates");
        add(presets, "RewardPro", "rewardpro:*", "Rewards");
        add(presets, "DailyRewards", "dailyrewards:*", "Rewards");
        add(presets, "AdvancedDailyRewards", "advanceddailyrewards:*", "Rewards");
        add(presets, "DeluxeRewards", "deluxerewards:*", "Rewards");
        add(presets, "ExcellentRewards", "excellentrewards:*", "Rewards");
        add(presets, "CasinoSlots", "casinoslots:*", "Gambling");
        add(presets, "SlotMachine", "slotmachine:*", "Gambling");
        add(presets, "SlotMachinePlus", "slotmachineplus:*", "Gambling");
        add(presets, "Slots", "slots:*", "Gambling");
        add(presets, "Roulette", "roulette:*", "Gambling");
        add(presets, "Casino", "casino:*", "Gambling");
        add(presets, "CrazyCasino", "crazycasino:*", "Gambling");
        add(presets, "GambleBar", "gamblebar:*", "Gambling");
        add(presets, "Gamble", "gamble:*", "Gambling");
        add(presets, "Lottery", "lottery:*", "Gambling");
        add(presets, "LotteryPlus", "lotteryplus:*", "Gambling");
        add(presets, "ExcellentCasino", "excellentcasino:*", "Gambling");
        add(presets, "CoinFlip", "coinflip:*", "Gambling");
        add(presets, "DeluxeCoinflip", "deluxecoinflip:*", "Gambling");
        add(presets, "CrazyCoinFlip", "crazycoinflip:*", "Gambling");
        add(presets, "Jackpot", "jackpot:*", "Gambling");
        add(presets, "Crash", "crash:*", "Gambling");
        add(presets, "Mines", "mines:*", "Gambling");
        add(presets, "Blackjack", "blackjack:*", "Gambling");
        add(presets, "RPS", "rps:*", "Gambling");
        add(presets, "Betting", "betting:*", "Gambling");
        add(presets, "Bets", "bets:*", "Gambling");
        add(presets, "BetonQuest", "betonquest:*", "Quests");
        add(presets, "Quests", "quests:*", "Quests");
        add(presets, "BeautyQuests", "beautyquests:*", "Quests");
        add(presets, "Citizens", "citizens:*", "NPCs");
        add(presets, "ZNPCsPlus", "znpcsplus:*", "NPCs");
        add(presets, "DecentHolograms", "decentholograms:*", "Holograms");
        add(presets, "HolographicDisplays", "holographicdisplays:*", "Holograms");
        add(presets, "TAB", "tab:*", "UI");
        add(presets, "LibsDisguises", "libsdisguises:*", "Network");
        add(presets, "Simple Voice Chat", "voicechat:*", "Plugins");
        add(presets, "Plasmo Voice", "plasmovoice:*", "Plugins");
        add(presets, "FancyHolograms", "fancyholograms:*", "Plugins");
        add(presets, "FancyNpcs", "fancynpcs:*", "Plugins");
        add(presets, "MythicMobs", "mythicmobs:*", "Plugins");
        add(presets, "LuckPerms", "luckperms:*", "Plugins");
        add(presets, "Forge", "forge:*", "Mod API");
        add(presets, "FML", "fml:*", "Mod API");
        add(presets, "FML Handshake", "fml:handshake*", "Mod API");
        add(presets, "NeoForge", "neoforge:*", "Mod API");
        add(presets, "Quilt", "quilt:*", "Mod API");
        add(presets, "Architectury", "architectury:*", "Mod API");
        add(presets, "Cloth Config", "cloth-config:*", "Mod API");
        add(presets, "ModMenu", "modmenu:*", "Mod API");
        add(presets, "Cardinal Components", "cardinal-components:*", "Mod API");
        add(presets, "CCA", "cca:*", "Mod API");
        add(presets, "Polymer", "polymer:*", "Mod API");
        add(presets, "PolyMc", "polymc:*", "Mod API");
        add(presets, "Litematica", "litematica:*", "Client Mods");
        add(presets, "MiniHUD", "minihud:*", "Client Mods");
        add(presets, "Tweakeroo", "tweakeroo:*", "Client Mods");
        add(presets, "MaLiLib", "malilib:*", "Client Mods");
        add(presets, "Xaero Minimap", "xaerominimap:*", "Client Mods");
        add(presets, "Xaero World Map", "xaeroworldmap:*", "Client Mods");
        add(presets, "JourneyMap", "journeymap:*", "Client Mods");
        add(presets, "VoxelMap", "voxelmap:*", "Client Mods");
        add(presets, "Essential", "essential:*", "Client Mods");
        add(presets, "Emotecraft", "emotecraft:*", "Client Mods");
        add(presets, "Feather", "feather:*", "Client Mods");
        add(presets, "Lunar Client", "lunarclient:*", "Client Mods");
        add(presets, "Badlion", "badlion:*", "Client Mods");
        add(presets, "LabyMod", "labymod:*", "Client Mods");
        add(presets, "Cosmetics", "cosmetics:*", "Client Mods");
        add(presets, "Cosmetic", "cosmetic:*", "Client Mods");
        add(presets, "SkinsRestorer", "skinsrestorer:*", "Network");
        add(presets, "ViaRewind", "viarewind:*", "Proxy");
        add(presets, "ProtocolSupport", "protocolsupport:*", "Proxy");
        add(presets, "RedisBungee", "redisbungee:*", "Proxy");
        add(presets, "LimboAPI", "limboapi:*", "Proxy");
        add(presets, "LimboAuth", "limboauth:*", "Proxy");
        add(presets, "FastLogin", "fastlogin:*", "Proxy");
        add(presets, "AuthMe", "authme:*", "Proxy");
        add(presets, "NLogin", "nlogin:*", "Proxy");
        add(presets, "LoginSecurity", "loginsecurity:*", "Proxy");
        add(presets, "Auth", "auth:*", "Proxy");
        add(presets, "ServerSelectorX", "serverselectorx:*", "Proxy");
        add(presets, "DeluxeHub", "deluxehub:*", "Proxy");
        add(presets, "HubBasics", "hubbasic:*", "Proxy");
        add(presets, "Lobby", "lobby:*", "Proxy");
        add(presets, "Hub", "hub:*", "Proxy");
        add(presets, "ServerListPlus", "serverlistplus:*", "Proxy");
        add(presets, "ServerList", "serverlist:*", "Proxy");
        add(presets, "Economy", "economy:*", "Economy");
        add(presets, "Money", "money:*", "Economy");
        add(presets, "Balance", "balance:*", "Economy");
        add(presets, "Bank", "bank:*", "Economy");
        add(presets, "Banks", "banks:*", "Economy");
        add(presets, "Currency", "currency:*", "Economy");
        add(presets, "Currencies", "currencies:*", "Economy");
        add(presets, "Coins", "coins:*", "Economy");
        add(presets, "Coin", "coin:*", "Economy");
        add(presets, "Tokens", "tokens:*", "Economy");
        add(presets, "Token", "token:*", "Economy");
        add(presets, "Credits", "credits:*", "Economy");
        add(presets, "Credit", "credit:*", "Economy");
        add(presets, "Points", "points:*", "Economy");
        add(presets, "Point", "point:*", "Economy");
        add(presets, "Gems", "gems:*", "Economy");
        add(presets, "Gem", "gem:*", "Economy");
        add(presets, "Crystals", "crystals:*", "Economy");
        add(presets, "Crystal", "crystal:*", "Economy");
        add(presets, "Shards", "shards:*", "Economy");
        add(presets, "Shard", "shard:*", "Economy");
        add(presets, "Bits", "bits:*", "Economy");
        add(presets, "Bit", "bit:*", "Economy");
        add(presets, "CraftConomy", "craftconomy:*", "Economy");
        add(presets, "iConomy", "iconomy:*", "Economy");
        add(presets, "BossEconomy", "bosseconomy:*", "Economy");
        add(presets, "BetterEconomy", "bettereconomy:*", "Economy");
        add(presets, "SimpleEconomy", "simpleeconomy:*", "Economy");
        add(presets, "CrownEconomy", "crowneconomy:*", "Economy");
        add(presets, "RoyaleEconomy", "royaleeconomy:*", "Economy");
        add(presets, "BeastTokens", "beasttokens:*", "Economy");
        add(presets, "TokenEnchant", "tokenenchant:*", "Economy");
        add(presets, "Shop", "shop:*", "Shops");
        add(presets, "Shops", "shops:*", "Shops");
        add(presets, "Store", "store:*", "Shops");
        add(presets, "Stores", "stores:*", "Shops");
        add(presets, "Market", "market:*", "Shops");
        add(presets, "Marketplace", "marketplace:*", "Shops");
        add(presets, "PlayerMarket", "playermarket:*", "Shops");
        add(presets, "PlayerMarketGUI", "playermarketgui:*", "Shops");
        add(presets, "CommunityMarket", "communitymarket:*", "Shops");
        add(presets, "ServerMarket", "servermarket:*", "Shops");
        add(presets, "TradeMarket", "trademarket:*", "Shops");
        add(presets, "TradePost", "tradepost:*", "Shops");
        add(presets, "Bazaar", "bazaar:*", "Shops");
        add(presets, "BlackMarket", "blackmarket:*", "Shops");
        add(presets, "SignShop", "signshop:*", "Shops");
        add(presets, "ShopChest", "shopchest:*", "Shops");
        add(presets, "AdminShop", "adminshop:*", "Shops");
        add(presets, "AutoSell", "autosell:*", "Shops");
        add(presets, "AutoSellChests", "autosellchests:*", "Shops");
        add(presets, "SellChest", "sellchest:*", "Shops");
        add(presets, "SellWands", "sellwands:*", "Shops");
        add(presets, "DynamicShop", "dynamicshop:*", "Shops");
        add(presets, "DynamicShopGUI", "dynamicshopgui:*", "Shops");
        add(presets, "AShop", "ashop:*", "Shops");
        add(presets, "AxShop", "axshop:*", "Shops");
        add(presets, "RoseStacker Shop", "roseshop:*", "Shops");
        add(presets, "AuctionHouse Pro", "auctionhousepro:*", "Auctions");
        add(presets, "AH", "ah:*", "Auctions");
        add(presets, "Auctions", "auctions:*", "Auctions");
        add(presets, "AuctionGUI", "auctiongui:*", "Auctions");
        add(presets, "AuctionPlus", "auctionplus:*", "Auctions");
        add(presets, "AuctionLite", "auctionlite:*", "Auctions");
        add(presets, "AuctionPro", "auctionpro:*", "Auctions");
        add(presets, "Auctioneer", "auctioneer:*", "Auctions");
        add(presets, "CrazyAH", "crazyah:*", "Auctions");
        add(presets, "ZAuction", "zauction:*", "Auctions");
        add(presets, "HuskAuctions", "huskauctions:*", "Auctions");
        add(presets, "PlayerAuction", "playerauction:*", "Auctions");
        add(presets, "CommunityAuction", "communityauction:*", "Auctions");
        add(presets, "MarketAuctions", "marketauctions:*", "Auctions");
        add(presets, "Crate", "crate:*", "Crates");
        add(presets, "Crates", "crates:*", "Crates");
        add(presets, "Keys", "keys:*", "Crates");
        add(presets, "Key", "key:*", "Crates");
        add(presets, "VaultCrates", "vaultcrates:*", "Crates");
        add(presets, "PhoenixCrates Lite", "phoenixcrateslite:*", "Crates");
        add(presets, "Phoenix Crates Lite", "phoenix_crates_lite:*", "Crates");
        add(presets, "LootCrates", "lootcrates:*", "Crates");
        add(presets, "LootCrate", "lootcrate:*", "Crates");
        add(presets, "VoteCrates", "votecrates:*", "Crates");
        add(presets, "VoteCrate", "votecrate:*", "Crates");
        add(presets, "ProCrates", "procrates:*", "Crates");
        add(presets, "EpicCrates", "epiccrates:*", "Crates");
        add(presets, "UltraCrates", "ultracrates:*", "Crates");
        add(presets, "RoyalCrates", "royalcrates:*", "Crates");
        add(presets, "CrateOpener", "crateopener:*", "Crates");
        add(presets, "CratePreview", "cratepreview:*", "Crates");
        add(presets, "MysteryBox", "mysterybox:*", "Crates");
        add(presets, "MysteryBoxes", "mysteryboxes:*", "Crates");
        add(presets, "Cases", "cases:*", "Crates");
        add(presets, "CaseOpening", "caseopening:*", "Crates");
        add(presets, "LootBoxes", "lootboxes:*", "Crates");
        add(presets, "LootBox", "lootbox:*", "Crates");
        add(presets, "RewardCrates", "rewardcrates:*", "Crates");
        add(presets, "CasinoGames", "casinogames:*", "Gambling");
        add(presets, "Vegas", "vegas:*", "Gambling");
        add(presets, "SlotMachines", "slotmachines:*", "Gambling");
        add(presets, "SlotsCasino", "slotscasino:*", "Gambling");
        add(presets, "RoulettePlus", "rouletteplus:*", "Gambling");
        add(presets, "LuckyWheel", "luckywheel:*", "Gambling");
        add(presets, "WheelOfFortune", "wheeloffortune:*", "Gambling");
        add(presets, "Wheel", "wheel:*", "Gambling");
        add(presets, "Dice", "dice:*", "Gambling");
        add(presets, "DiceGames", "dicegames:*", "Gambling");
        add(presets, "Poker", "poker:*", "Gambling");
        add(presets, "Keno", "keno:*", "Gambling");
        add(presets, "Baccarat", "baccarat:*", "Gambling");
        add(presets, "Plinko", "plinko:*", "Gambling");
        add(presets, "CaseBattles", "casebattles:*", "Gambling");
        add(presets, "CoinToss", "cointoss:*", "Gambling");
        add(presets, "HeadsOrTails", "heads_or_tails:*", "Gambling");
        add(presets, "LuckyBlock", "luckyblock:*", "Rewards");
        add(presets, "LuckyBlocks", "luckyblocks:*", "Rewards");
        add(presets, "VoteParty", "voteparty:*", "Rewards");
        add(presets, "Voting", "voting:*", "Rewards");
        add(presets, "Vote", "vote:*", "Rewards");
        add(presets, "NuVotifier", "nuvotifier:*", "Rewards");
        add(presets, "Votifier", "votifier:*", "Rewards");
        add(presets, "Reward", "reward:*", "Rewards");
        add(presets, "Rewards", "rewards:*", "Rewards");
        add(presets, "Daily", "daily:*", "Rewards");
        add(presets, "Streaks", "streaks:*", "Rewards");
        add(presets, "BattlePass", "battlepass:*", "Rewards");
        add(presets, "AdvancedBattlePass", "advancedbattlepass:*", "Rewards");
        add(presets, "Pass", "pass:*", "Rewards");
        add(presets, "QuestsPlus", "questsplus:*", "Quests");
        add(presets, "Quest", "quest:*", "Quests");
        add(presets, "QuestCreator", "questcreator:*", "Quests");
        add(presets, "NotQuests", "notquests:*", "Quests");
        add(presets, "Bounties", "bounties:*", "Quests");
        add(presets, "BountyHunters", "bountyhunters:*", "Quests");
        add(presets, "PlayerBounties", "playerbounties:*", "Quests");
        add(presets, "McMMO", "mcmmo:*", "Skills");
        add(presets, "AuraSkills", "auraskills:*", "Skills");
        add(presets, "AureliumSkills Alt", "skills:*", "Skills");
        add(presets, "LevelledMobs", "levelledmobs:*", "Mobs");
        add(presets, "MythicLib", "mythiclib:*", "Mobs");
        add(presets, "EliteMobs", "elitemobs:*", "Mobs");
        add(presets, "BossMania", "bossmania:*", "Mobs");
        add(presets, "Boss", "boss:*", "Mobs");
        add(presets, "Bosses", "bosses:*", "Mobs");
        add(presets, "ModelEngine4", "modelengine4:*", "Items");
        add(presets, "MythicDungeons", "mythicdungeons:*", "Items");
        add(presets, "CustomItems", "customitems:*", "Items");
        add(presets, "CustomBlocks", "customblocks:*", "Items");
        add(presets, "CustomCrops", "customcrops:*", "Items");
        add(presets, "CustomFishing", "customfishing:*", "Items");
        add(presets, "Items", "items:*", "Items");
        add(presets, "Item", "item:*", "Items");
        add(presets, "ItemBridge", "itembridge:*", "Items");
        add(presets, "Eco", "eco:*", "Items");
        add(presets, "EcoSkills", "ecoskills:*", "Items");
        add(presets, "EcoPets", "ecopets:*", "Items");
        add(presets, "EcoCrates", "ecocrates:*", "Items");
        add(presets, "EcoJobs", "ecojobs:*", "Items");
        add(presets, "CustomEnchants", "customenchants:*", "Items");
        add(presets, "AdvancedEnchantments", "advancedenchantments:*", "Items");
        add(presets, "CrazyEnchantments", "crazyenchantments:*", "Items");
        add(presets, "Enchants", "enchants:*", "Items");
        add(presets, "Enchantments", "enchantments:*", "Items");
        add(presets, "Menus", "menus:*", "Menus");
        add(presets, "Menu", "menu:*", "Menus");
        add(presets, "GUI", "gui:*", "Menus");
        add(presets, "GUIs", "guis:*", "Menus");
        add(presets, "InventoryGUI", "inventorygui:*", "Menus");
        add(presets, "SmartInvs", "smartinvs:*", "Menus");
        add(presets, "TriumphGUI", "triumphgui:*", "Menus");
        add(presets, "IF", "if:*", "Menus");
        add(presets, "InventoryFramework", "inventoryframework:*", "Menus");
        add(presets, "CommandPanel", "commandpanel:*", "Menus");
        add(presets, "AnimatedMenu", "animatedmenu:*", "Menus");
        add(presets, "DeluxeTags", "deluxetags:*", "Chat");
        add(presets, "ChatControl", "chatcontrol:*", "Chat");
        add(presets, "ChatControlRed", "chatcontrolred:*", "Chat");
        add(presets, "VentureChat", "venturechat:*", "Chat");
        add(presets, "DeluxeChat", "deluxechat:*", "Chat");
        add(presets, "HeroChat", "herochat:*", "Chat");
        add(presets, "InteractiveChat", "interactivechat:*", "Chat");
        add(presets, "InteractiveChatDiscordSRV", "interactivechatdiscordsrv:*", "Chat");
        add(presets, "DiscordSRV", "discordsrv:*", "Chat");
        add(presets, "Chat", "chat:*", "Chat");
        add(presets, "ChatGames", "chatgames:*", "Chat");
        add(presets, "TAB Premium", "tabpremium:*", "UI");
        add(presets, "TAB Reborn", "tabreborn:*", "UI");
        add(presets, "NametagEdit", "nametagedit:*", "UI");
        add(presets, "AnimatedNames", "animatednames:*", "UI");
        add(presets, "Scoreboard", "scoreboard:*", "UI");
        add(presets, "Scoreboards", "scoreboards:*", "UI");
        add(presets, "FeatherBoard", "featherboard:*", "UI");
        add(presets, "AnimatedScoreboard", "animatedscoreboard:*", "UI");
        add(presets, "Hologram", "hologram:*", "Holograms");
        add(presets, "Holograms", "holograms:*", "Holograms");
        add(presets, "CMIHolograms", "cmiholograms:*", "Holograms");
        add(presets, "HD", "hd:*", "Holograms");
        add(presets, "FancyNPCs Alt", "fancynpc:*", "NPCs");
        add(presets, "ZNPCs", "znpcs:*", "NPCs");
        add(presets, "NPC", "npc:*", "NPCs");
        add(presets, "NPCs", "npcs:*", "NPCs");
        add(presets, "FancyCitizens", "fancycitizens:*", "NPCs");
        add(presets, "Sentinel", "sentinel:*", "NPCs");
        add(presets, "Denizen", "denizen:*", "NPCs");
        add(presets, "LibsDisguises Alt", "libsdisguise:*", "Network");
        add(presets, "Disguise", "disguise:*", "Network");
        add(presets, "Disguises", "disguises:*", "Network");
        add(presets, "Protocolize", "protocolize:*", "Network");
        add(presets, "PacketWrapper", "packetwrapper:*", "Network");
        add(presets, "CommandAPI", "commandapi:*", "Frameworks");
        add(presets, "Cloud", "cloud:*", "Frameworks");
        add(presets, "Aikar Commands", "acf:*", "Frameworks");
        add(presets, "InventoryLib", "inventorylib:*", "Frameworks");
        add(presets, "XSeries", "xseries:*", "Frameworks");
        add(presets, "RoseGarden", "rosegarden:*", "Frameworks");
        add(presets, "BlueSlimeCore", "blueslimecore:*", "Frameworks");
        add(presets, "Placeholder", "placeholder:*", "Frameworks");
        add(presets, "PAPI", "papi:*", "Frameworks");
        add(presets, "Worlds", "worlds:*", "Worlds");
        add(presets, "World", "world:*", "Worlds");
        add(presets, "Multiverse", "multiverse:*", "Worlds");
        add(presets, "MultiverseCore", "multiversecore:*", "Worlds");
        add(presets, "MultiverseInventories", "multiverseinventories:*", "Worlds");
        add(presets, "MultiWorld", "multiworld:*", "Worlds");
        add(presets, "WorldManager", "worldmanager:*", "Worlds");
        add(presets, "Terra", "terra:*", "Worlds");
        add(presets, "TerraformGenerator", "terraformgenerator:*", "Worlds");
        add(presets, "Iris", "iris:*", "Worlds");
        add(presets, "Dynmap", "dynmap:*", "Maps");
        add(presets, "BlueMap", "bluemap:*", "Maps");
        add(presets, "Squaremap", "squaremap:*", "Maps");
        add(presets, "Pl3xMap", "pl3xmap:*", "Maps");
        add(presets, "Overviewer", "overviewer:*", "Maps");
        add(presets, "Plan", "plan:*", "Admin");
        add(presets, "Spark", "spark:*", "Admin");
        add(presets, "LiteBans", "litebans:*", "Admin");
        add(presets, "AdvancedBan", "advancedban:*", "Admin");
        add(presets, "LibertyBans", "libertybans:*", "Admin");
        add(presets, "BanManager", "banmanager:*", "Admin");
        add(presets, "StaffPlus", "staffplus:*", "Admin");
        add(presets, "StaffPlusPlus", "staffplusplus:*", "Admin");
        add(presets, "StaffFacilities", "stafffacilities:*", "Admin");
        add(presets, "SuperVanish", "supervanish:*", "Admin");
        add(presets, "PremiumVanish", "premiumvanish:*", "Admin");
        add(presets, "Vanish", "vanish:*", "Admin");
        add(presets, "VanishNoPacket", "vanishnopacket:*", "Admin");
        add(presets, "Maintenance", "maintenance:*", "Admin");
        add(presets, "LuckPerms Verbose", "lp:*", "Admin");
        add(presets, "Permissions", "permissions:*", "Admin");
        add(presets, "PermissionsEx", "permissionsex:*", "Admin");
        add(presets, "UltraPermissions", "ultrapermissions:*", "Admin");
        add(presets, "Vulcan", "vulcan:*", "AntiCheat");
        add(presets, "Grim", "grim:*", "AntiCheat");
        add(presets, "GrimAC", "grimac:*", "AntiCheat");
        add(presets, "Matrix", "matrix:*", "AntiCheat");
        add(presets, "Karhu", "karhu:*", "AntiCheat");
        add(presets, "Spartan", "spartan:*", "AntiCheat");
        add(presets, "AAC", "aac:*", "AntiCheat");
        add(presets, "NoCheatPlus", "nocheatplus:*", "AntiCheat");
        add(presets, "NCP", "ncp:*", "AntiCheat");
        add(presets, "Intave", "intave:*", "AntiCheat");
        add(presets, "Polar", "polar:*", "AntiCheat");
        add(presets, "Verus", "verus:*", "AntiCheat");
        add(presets, "Themis", "themis:*", "AntiCheat");
        add(presets, "Negativity", "negativity:*", "AntiCheat");
        add(presets, "Horizon", "horizon:*", "AntiCheat");
        add(presets, "Reflex", "reflex:*", "AntiCheat");
        add(presets, "AntiAura", "antiaura:*", "AntiCheat");
        add(presets, "WatchCat", "watchcat:*", "AntiCheat");
        add(presets, "AntiCheat", "anticheat:*", "AntiCheat");
        add(presets, "AntiCheats", "anticheats:*", "AntiCheat");
        add(presets, "BedWars1058", "bedwars1058:*", "Minigames");
        add(presets, "BedWars", "bedwars:*", "Minigames");
        add(presets, "BW", "bw:*", "Minigames");
        add(presets, "SkyWars", "skywars:*", "Minigames");
        add(presets, "SW", "sw:*", "Minigames");
        add(presets, "SurvivalGames", "survivalgames:*", "Minigames");
        add(presets, "HungerGames", "hungergames:*", "Minigames");
        add(presets, "MurderMystery", "murdermystery:*", "Minigames");
        add(presets, "BuildBattle", "buildbattle:*", "Minigames");
        add(presets, "Spleef", "spleef:*", "Minigames");
        add(presets, "TNTRun", "tntrun:*", "Minigames");
        add(presets, "BlockHunt", "blockhunt:*", "Minigames");
        add(presets, "HideAndSeek", "hideandseek:*", "Minigames");
        add(presets, "Duels", "duels:*", "Minigames");
        add(presets, "KitPvP", "kitpvp:*", "Minigames");
        add(presets, "Arena", "arena:*", "Minigames");
        add(presets, "Arenas", "arenas:*", "Minigames");
        add(presets, "Minigames", "minigames:*", "Minigames");
        add(presets, "MiniGame", "minigame:*", "Minigames");
        add(presets, "PartyGames", "partygames:*", "Minigames");
        add(presets, "Parkour", "parkour:*", "Minigames");
        add(presets, "ParkourPlus", "parkourplus:*", "Minigames");
        add(presets, "TheBridge", "thebridge:*", "Minigames");
        add(presets, "Bridge", "bridge:*", "Minigames");
        add(presets, "SkyBlock Menu", "skyblockmenu:*", "Minigames");
        add(presets, "Prison", "prison:*", "Prison");
        add(presets, "PrisonMines", "prisonmines:*", "Prison");
        add(presets, "MinePacks", "minepacks:*", "Prison");
        add(presets, "Mine", "mine:*", "Prison");
        add(presets, "EzRanksPro", "ezrankspro:*", "Prison");
        add(presets, "AutoRank", "autorank:*", "Prison");
        add(presets, "Rankup", "rankup:*", "Prison");
        add(presets, "RankupX", "rankupx:*", "Prison");
        add(presets, "Rankup3", "rankup3:*", "Prison");
        add(presets, "Backpacks", "backpacks:*", "Storage");
        add(presets, "Backpack", "backpack:*", "Storage");
        add(presets, "AdvancedBackpacks", "advancedbackpacks:*", "Storage");
        add(presets, "PlayerVaults", "playervaults:*", "Storage");
        add(presets, "PlayerVaultsX", "playervaultsx:*", "Storage");
        add(presets, "EnderVaults", "endervaults:*", "Storage");
        add(presets, "PV", "pv:*", "Storage");
        add(presets, "Vaults", "vaults:*", "Storage");
        add(presets, "Lifesteal", "lifesteal:*", "SMP");
        add(presets, "LifeStealCore", "lifestealcore:*", "SMP");
        add(presets, "Hearts", "hearts:*", "SMP");
        add(presets, "Heart", "heart:*", "SMP");
        add(presets, "CombatLogX", "combatlogx:*", "SMP");
        add(presets, "CombatLog", "combatlog:*", "SMP");
        add(presets, "CombatPlus", "combatplus:*", "SMP");
        add(presets, "Combat", "combat:*", "SMP");
        add(presets, "DupeFixes", "dupefixes:*", "Security");
        add(presets, "DupeFix", "dupefix:*", "Security");
        add(presets, "ExploitFixes", "exploitfixes:*", "Security");
        add(presets, "AntiExploit", "antiexploit:*", "Security");
        add(presets, "AntiDupe", "antidupe:*", "Security");
        add(presets, "ItemFilter", "itemfilter:*", "Security");
        add(presets, "IllegalItem", "illegalitem:*", "Security");
        add(presets, "IllegalItemsPlus", "illegalitemsplus:*", "Security");
        add(presets, "LifeStealZ", "lifestealz:*", "SMP");
        add(presets, "LifeStealPlus", "lifestealplus:*", "SMP");
        add(presets, "LifeSteal SMP", "lifestealsmp:*", "SMP");
        add(presets, "LifeStealCoreX", "lifestealcorex:*", "SMP");
        add(presets, "SimpleLifeSteal", "simplelifesteal:*", "SMP");
        add(presets, "PLifeSteal", "plifesteal:*", "SMP");
        add(presets, "LS SMP", "lssmp:*", "SMP");
        add(presets, "SMP", "smp:*", "SMP");
        add(presets, "Survival", "survival:*", "SMP");
        add(presets, "SurvivalCore", "survivalcore:*", "SMP");
        add(presets, "SurvivalPlus", "survivalplus:*", "SMP");
        add(presets, "SurvivalSystem", "survivalsystem:*", "SMP");
        add(presets, "SMPCore", "smpcore:*", "SMP");
        add(presets, "SMPSystem", "smpsystem:*", "SMP");
        add(presets, "SMPPlus", "smpplus:*", "SMP");
        add(presets, "HeartsPlus", "heartsplus:*", "SMP");
        add(presets, "HeartSystem", "heartsystem:*", "SMP");
        add(presets, "Revive", "revive:*", "SMP");
        add(presets, "Revives", "revives:*", "SMP");
        add(presets, "ReviveMe", "reviveme:*", "SMP");
        add(presets, "RevivePlus", "reviveplus:*", "SMP");
        add(presets, "DeathBan", "deathban:*", "SMP");
        add(presets, "DeathBans", "deathbans:*", "SMP");
        add(presets, "CombatTag", "combattag:*", "SMP");
        add(presets, "CombatTagPlus", "combattagplus:*", "SMP");
        add(presets, "CombatTimer", "combattimer:*", "SMP");
        add(presets, "CombatManager", "combatmanager:*", "SMP");
        add(presets, "PvPManager", "pvpmanager:*", "SMP");
        add(presets, "PvPManager Lite", "pvpmanagerlite:*", "SMP");
        add(presets, "PvPManager Premium", "pvpmanagerpremium:*", "SMP");
        add(presets, "PvPToggle", "pvptoggle:*", "SMP");
        add(presets, "PvPControl", "pvpcontrol:*", "SMP");
        add(presets, "KeepInventory", "keepinventory:*", "SMP");
        add(presets, "DeathChest", "deathchest:*", "SMP");
        add(presets, "DeathChests", "deathchests:*", "SMP");
        add(presets, "AngelChest", "angelchest:*", "SMP");
        add(presets, "Graves", "graves:*", "SMP");
        add(presets, "Grave", "grave:*", "SMP");
        add(presets, "GraveStones", "gravestones:*", "SMP");
        add(presets, "GraveStone", "gravestone:*", "SMP");
        add(presets, "BetterRTP", "betterrtp:*", "SMP");
        add(presets, "RandomTeleport", "randomteleport:*", "SMP");
        add(presets, "RTP", "rtp:*", "SMP");
        add(presets, "Homes", "homes:*", "SMP");
        add(presets, "Home", "home:*", "SMP");
        add(presets, "SetHome", "sethome:*", "SMP");
        add(presets, "Warp", "warp:*", "SMP");
        add(presets, "Warps", "warps:*", "SMP");
        add(presets, "BetterWarps", "betterwarps:*", "SMP");
        add(presets, "Teleport", "teleport:*", "SMP");
        add(presets, "TPA", "tpa:*", "SMP");
        add(presets, "Back", "back:*", "SMP");
        add(presets, "Spawn", "spawn:*", "SMP");
        add(presets, "SetSpawn", "setspawn:*", "SMP");
        add(presets, "SleepMost", "sleepmost:*", "SMP");
        add(presets, "Harbor", "harbor:*", "SMP");
        add(presets, "OnePlayerSleep", "oneplayersleep:*", "SMP");
        add(presets, "SinglePlayerSleep", "singleplayersleep:*", "SMP");
        add(presets, "VeinMiner", "veinminer:*", "SMP");
        add(presets, "OreAnnouncer", "oreannouncer:*", "SMP");
        add(presets, "TreeFeller", "treefeller:*", "SMP");
        add(presets, "TreeAssist", "treeassist:*", "SMP");
        add(presets, "Timber", "timber:*", "SMP");
        add(presets, "SilkSpawners", "silkspawners:*", "SMP");
        add(presets, "WildStacker", "wildstacker:*", "SMP");
        add(presets, "RoseStacker", "rosestacker:*", "SMP");
        add(presets, "StackMob", "stackmob:*", "SMP");
        add(presets, "Chunky", "chunky:*", "SMP");
        add(presets, "ChunkyBorder", "chunkyborder:*", "SMP");
        add(presets, "ChunkyBorder Alt", "chunky:border*", "SMP");
        add(presets, "AnarchyExploitFixes", "anarchyexploitfixes:*", "Security");
        add(presets, "AEF", "aef:*", "Security");
        add(presets, "ExploitFixer API", "exploitfixerapi:*", "Security");
        add(presets, "HamsterAPI", "hamsterapi:*", "Security");
        add(presets, "DupeFixer", "dupefixer:*", "Security");
        add(presets, "DupePatches", "dupepatches:*", "Security");
        add(presets, "DupeProtection", "dupeprotection:*", "Security");
        add(presets, "AntiDuplication", "antiduplication:*", "Security");
        add(presets, "AntiBookBan", "antibookban:*", "Security");
        add(presets, "BookExploitFix", "bookexploitfix:*", "Security");
        add(presets, "BookFix", "bookfix:*", "Security");
        add(presets, "BookLimiter", "booklimiter:*", "Security");
        add(presets, "BookGuard", "bookguard:*", "Security");
        add(presets, "PacketLimiter", "packetlimiter:*", "Security");
        add(presets, "PacketFilter", "packetfilter:*", "Security");
        add(presets, "PacketGuard", "packetguard:*", "Security");
        add(presets, "PacketSecurity", "packetsecurity:*", "Security");
        add(presets, "PacketEvents Security", "packeteventssecurity:*", "Security");
        add(presets, "PacketAntiCrash", "packetanticrash:*", "Security");
        add(presets, "AntiCrash", "anticrash:*", "Security");
        add(presets, "CrashFix", "crashfix:*", "Security");
        add(presets, "CrashFixer", "crashfixer:*", "Security");
        add(presets, "CrashGuard", "crashguard:*", "Security");
        add(presets, "AntiCrashPlus", "anticrashplus:*", "Security");
        add(presets, "ExploitPatch", "exploitpatch:*", "Security");
        add(presets, "ExploitPatcher", "exploitpatcher:*", "Security");
        add(presets, "ExploitProtection", "exploitprotection:*", "Security");
        add(presets, "ExploitGuard", "exploitguard:*", "Security");
        add(presets, "AntiExploitPlus", "antiexploitplus:*", "Security");
        add(presets, "AntiCrashReloaded", "anticrashreloaded:*", "Security");
        add(presets, "IllegalStackRemover", "illegalstackremover:*", "Security");
        add(presets, "IllegalStackFixer", "illegalstackfixer:*", "Security");
        add(presets, "IllegalStackFix", "illegalstackfix:*", "Security");
        add(presets, "IllegalItemRemover", "illegalitemremover:*", "Security");
        add(presets, "IllegalItemFixer", "illegalitemfixer:*", "Security");
        add(presets, "IllegalItemFix", "illegalitemfix:*", "Security");
        add(presets, "IllegalEnchants", "illegalenchants:*", "Security");
        add(presets, "OverstackFix", "overstackfix:*", "Security");
        add(presets, "OverstackFixer", "overstackfixer:*", "Security");
        add(presets, "StackFix", "stackfix:*", "Security");
        add(presets, "StackFixer", "stackfixer:*", "Security");
        add(presets, "ItemGuard", "itemguard:*", "Security");
        add(presets, "InventoryGuard", "inventoryguard:*", "Security");
        add(presets, "InventorySecurity", "inventorysecurity:*", "Security");
        add(presets, "OpenInvGuard", "openinvguard:*", "Security");
        add(presets, "CommandWhitelist", "commandwhitelist:*", "Security");
        add(presets, "CommandBlocker", "commandblocker:*", "Security");
        add(presets, "CommandGuard", "commandguard:*", "Security");
        add(presets, "ChatGuard", "chatguard:*", "Security");
        add(presets, "AntiSpam", "antispam:*", "Security");
        add(presets, "AntiSpamPlus", "antispamplus:*", "Security");
        add(presets, "AntiBot", "antibot:*", "Security");
        add(presets, "BotSentry", "botsentry:*", "Security");
        add(presets, "BotGuard", "botguard:*", "Security");
        add(presets, "BotFilter", "botfilter:*", "Security");
        add(presets, "AntiBotDeluxe", "antibotdeluxe:*", "Security");
        add(presets, "AntiVPN", "antivpn:*", "Security");
        add(presets, "VPNGuard", "vpnguard:*", "Security");
        add(presets, "VPNBlocker", "vpnblocker:*", "Security");
        add(presets, "ProxyCheck", "proxycheck:*", "Security");
        add(presets, "ProxyGuard", "proxyguard:*", "Security");
        add(presets, "FastAntiBot", "fastantibot:*", "Security");
        add(presets, "EpicGuard", "epicguard:*", "Security");
        add(presets, "FlameCord", "flamecord:*", "Security");
        add(presets, "BotFilterPlus", "botfilterplus:*", "Security");
        add(presets, "AccountGuard", "accountguard:*", "Security");
        add(presets, "LoginGuard", "loginguard:*", "Security");
        add(presets, "NameSecurity", "namesecurity:*", "Security");
        add(presets, "UUIDSpoofFix", "uuidspooffix:*", "Security");
        add(presets, "UUIDGuard", "uuidguard:*", "Security");
        add(presets, "NettyGuard", "nettyguard:*", "Security");
        add(presets, "PayloadGuard", "payloadguard:*", "Security");
        add(presets, "PayloadFilter", "payloadfilter:*", "Security");
        add(presets, "PayloadSecurity", "payloadsecurity:*", "Security");
        add(presets, "ChannelGuard", "channelguard:*", "Security");
        add(presets, "PluginMessageGuard", "pluginmessageguard:*", "Security");
        add(presets, "PluginMessageFilter", "pluginmessagefilter:*", "Security");
        add(presets, "AxCrates", "axcrates:*", "Crates");
        add(presets, "AxCrate", "axcrate:*", "Crates");
        add(presets, "CratesX", "cratesx:*", "Crates");
        add(presets, "CrateX", "cratex:*", "Crates");
        add(presets, "CratesPro", "cratespro:*", "Crates");
        add(presets, "CratePro", "cratepro:*", "Crates");
        add(presets, "CrateCore", "cratecore:*", "Crates");
        add(presets, "CratesCore", "cratescore:*", "Crates");
        add(presets, "CrateSystem", "cratesystem:*", "Crates");
        add(presets, "CratesSystem", "cratessystem:*", "Crates");
        add(presets, "CrateGUI", "crategui:*", "Crates");
        add(presets, "CratesGUI", "cratesgui:*", "Crates");
        add(presets, "CrateMenu", "cratemenu:*", "Crates");
        add(presets, "CratesMenu", "cratesmenu:*", "Crates");
        add(presets, "CrateMenus", "cratemenus:*", "Crates");
        add(presets, "CratesAPI", "cratesapi:*", "Crates");
        add(presets, "CrateAPI", "crateapi:*", "Crates");
        add(presets, "CratePreviewer", "cratepreviewer:*", "Crates");
        add(presets, "CratesPreview", "cratespreview:*", "Crates");
        add(presets, "CratesPreviewer", "cratespreviewer:*", "Crates");
        add(presets, "CrateAnimation", "crateanimation:*", "Crates");
        add(presets, "CrateAnimations", "crateanimations:*", "Crates");
        add(presets, "CrateRoll", "crateroll:*", "Crates");
        add(presets, "CrateRolls", "craterolls:*", "Crates");
        add(presets, "CrateWheel", "cratewheel:*", "Crates");
        add(presets, "CrateWheels", "cratewheels:*", "Crates");
        add(presets, "CrateSpin", "cratespin:*", "Crates");
        add(presets, "CrateSpins", "cratespins:*", "Crates");
        add(presets, "CrateOpenerPro", "crateopenerpro:*", "Crates");
        add(presets, "CrateOpeners", "crateopeners:*", "Crates");
        add(presets, "CrateKey", "cratekey:*", "Crates");
        add(presets, "CrateKeysPro", "cratekeyspro:*", "Crates");
        add(presets, "KeyCrates", "keycrates:*", "Crates");
        add(presets, "KeyCrate", "keycrate:*", "Crates");
        add(presets, "KeySystem", "keysystem:*", "Crates");
        add(presets, "KeysSystem", "keyssystem:*", "Crates");
        add(presets, "SimpleCrates", "simplecrates:*", "Crates");
        add(presets, "SimpleCrate", "simplecrate:*", "Crates");
        add(presets, "EasyCrates", "easycrates:*", "Crates");
        add(presets, "EasyCrate", "easycrate:*", "Crates");
        add(presets, "BetterCrates", "bettercrates:*", "Crates");
        add(presets, "BetterCrate", "bettercrate:*", "Crates");
        add(presets, "PremiumCrates", "premiumcrates:*", "Crates");
        add(presets, "PremiumCrate", "premiumcrate:*", "Crates");
        add(presets, "UltimateCrates", "ultimatecrates:*", "Crates");
        add(presets, "UltimateCrate", "ultimatecrate:*", "Crates");
        add(presets, "SupremeCrates", "supremecrates:*", "Crates");
        add(presets, "SupremeCrate", "supremecrate:*", "Crates");
        add(presets, "DivineCrates", "divinecrates:*", "Crates");
        add(presets, "DivineCrate", "divinecrate:*", "Crates");
        add(presets, "LegendaryCrates", "legendarycrates:*", "Crates");
        add(presets, "LegendaryCrate", "legendarycrate:*", "Crates");
        add(presets, "RareCrates", "rarecrates:*", "Crates");
        add(presets, "RareCrate", "rarecrate:*", "Crates");
        add(presets, "EpicCrate", "epiccrate:*", "Crates");
        add(presets, "UltraCrate", "ultracrate:*", "Crates");
        add(presets, "RoyalCrate", "royalcrate:*", "Crates");
        add(presets, "GoldenCrate", "goldencrate:*", "Crates");
        add(presets, "CrazyCrate", "crazycrate:*", "Crates");
        add(presets, "AdvancedCrate", "advancedcrate:*", "Crates");
        add(presets, "ExcellentCrate", "excellentcrate:*", "Crates");
        add(presets, "SpecializedCrate", "specializedcrate:*", "Crates");
        add(presets, "PhoenixCrate", "phoenixcrate:*", "Crates");
        add(presets, "AquaticCrate", "aquaticcrate:*", "Crates");
        add(presets, "AquaCrates", "aquacrates:*", "Crates");
        add(presets, "AquaCrate", "aquacrate:*", "Crates");
        add(presets, "PulseCrate", "pulsecrate:*", "Crates");
        add(presets, "MysticCrates", "mysticcrates:*", "Crates");
        add(presets, "MysticCrate", "mysticcrate:*", "Crates");
        add(presets, "MythicCrates", "mythiccrates:*", "Crates");
        add(presets, "MythicCrate", "mythiccrate:*", "Crates");
        add(presets, "OPCrates", "opcrates:*", "Crates");
        add(presets, "OPCrate", "opcrate:*", "Crates");
        add(presets, "VoteKey", "votekey:*", "Crates");
        add(presets, "VoteKeys", "votekeys:*", "Crates");
        add(presets, "Case", "case:*", "Crates");
        add(presets, "CasesPro", "casespro:*", "Crates");
        add(presets, "CaseSystem", "casesystem:*", "Crates");
        add(presets, "CaseOpeningPro", "caseopeningpro:*", "Crates");
        add(presets, "OpenCase", "opencase:*", "Crates");
        add(presets, "OpenCases", "opencases:*", "Crates");
        add(presets, "CaseKeys", "casekeys:*", "Crates");
        add(presets, "CaseKey", "casekey:*", "Crates");
        add(presets, "Boxes", "boxes:*", "Crates");
        add(presets, "Box", "box:*", "Crates");
        add(presets, "RewardBox", "rewardbox:*", "Crates");
        add(presets, "RewardBoxes", "rewardboxes:*", "Crates");
        add(presets, "PrizeCrates", "prizecrates:*", "Crates");
        add(presets, "PrizeCrate", "prizecrate:*", "Crates");
        add(presets, "PrizeBox", "prizebox:*", "Crates");
        add(presets, "PrizeBoxes", "prizeboxes:*", "Crates");
        add(presets, "LootCratesPro", "lootcratespro:*", "Crates");
        add(presets, "LootBoxPro", "lootboxpro:*", "Crates");
        add(presets, "LootBoxesPro", "lootboxespro:*", "Crates");
        add(presets, "RewardCase", "rewardcase:*", "Crates");
        add(presets, "RewardCases", "rewardcases:*", "Crates");
        add(presets, "CasinoCore", "casinocore:*", "Gambling");
        add(presets, "CasinoSystem", "casinosystem:*", "Gambling");
        add(presets, "CasinoPlus", "casinoplus:*", "Gambling");
        add(presets, "CasinoPro", "casinopro:*", "Gambling");
        add(presets, "CasinoGUI", "casinogui:*", "Gambling");
        add(presets, "CasinoMenu", "casinomenu:*", "Gambling");
        add(presets, "Casinos", "casinos:*", "Gambling");
        add(presets, "Gambling", "gambling:*", "Gambling");
        add(presets, "GamblingCore", "gamblingcore:*", "Gambling");
        add(presets, "GamblingSystem", "gamblingsystem:*", "Gambling");
        add(presets, "GamblingPlus", "gamblingplus:*", "Gambling");
        add(presets, "GamblingPro", "gamblingpro:*", "Gambling");
        add(presets, "Bet", "bet:*", "Gambling");
        add(presets, "BetsPlus", "betsplus:*", "Gambling");
        add(presets, "BettingPlus", "bettingplus:*", "Gambling");
        add(presets, "BettingSystem", "bettingsystem:*", "Gambling");
        add(presets, "BettingCore", "bettingcore:*", "Gambling");
        add(presets, "Wager", "wager:*", "Gambling");
        add(presets, "Wagers", "wagers:*", "Gambling");
        add(presets, "Wagering", "wagering:*", "Gambling");
        add(presets, "Stake", "stake:*", "Gambling");
        add(presets, "Stakes", "stakes:*", "Gambling");
        add(presets, "CoinFlips", "coinflips:*", "Gambling");
        add(presets, "CoinFlipPlus", "coinflipplus:*", "Gambling");
        add(presets, "CoinFlipPro", "coinflippro:*", "Gambling");
        add(presets, "CoinFlipSystem", "coinflipsystem:*", "Gambling");
        add(presets, "CoinFlipCore", "coinflipcore:*", "Gambling");
        add(presets, "CoinTossPlus", "cointossplus:*", "Gambling");
        add(presets, "CoinTossPro", "cointosspro:*", "Gambling");
        add(presets, "HeadsTails", "headstails:*", "Gambling");
        add(presets, "HeadsOrTailsPlus", "heads_or_tails_plus:*", "Gambling");
        add(presets, "Jackpots", "jackpots:*", "Gambling");
        add(presets, "JackpotPlus", "jackpotplus:*", "Gambling");
        add(presets, "JackpotPro", "jackpotpro:*", "Gambling");
        add(presets, "JackpotSystem", "jackpotsystem:*", "Gambling");
        add(presets, "LotteryCore", "lotterycore:*", "Gambling");
        add(presets, "LotterySystem", "lotterysystem:*", "Gambling");
        add(presets, "LotteryPro", "lotterypro:*", "Gambling");
        add(presets, "Lotto", "lotto:*", "Gambling");
        add(presets, "LottoPlus", "lottoplus:*", "Gambling");
        add(presets, "LottoSystem", "lottosystem:*", "Gambling");
        add(presets, "Raffle", "raffle:*", "Gambling");
        add(presets, "Raffles", "raffles:*", "Gambling");
        add(presets, "RafflePlus", "raffleplus:*", "Gambling");
        add(presets, "RouletteCore", "roulettecore:*", "Gambling");
        add(presets, "RouletteSystem", "roulettesystem:*", "Gambling");
        add(presets, "RoulettePro", "roulettepro:*", "Gambling");
        add(presets, "RouletteGUI", "roulettegui:*", "Gambling");
        add(presets, "RussianRoulette", "russianroulette:*", "Gambling");
        add(presets, "Slot", "slot:*", "Gambling");
        add(presets, "SlotPlus", "slotplus:*", "Gambling");
        add(presets, "SlotPro", "slotpro:*", "Gambling");
        add(presets, "SlotSystem", "slotsystem:*", "Gambling");
        add(presets, "SlotsCore", "slotscore:*", "Gambling");
        add(presets, "SlotsPlus", "slotsplus:*", "Gambling");
        add(presets, "SlotsPro", "slotspro:*", "Gambling");
        add(presets, "SlotsSystem", "slotssystem:*", "Gambling");
        add(presets, "SlotMachinesPlus", "slotmachinesplus:*", "Gambling");
        add(presets, "SlotMachinesPro", "slotmachinespro:*", "Gambling");
        add(presets, "OneArmedBandit", "onearmedbandit:*", "Gambling");
        add(presets, "LuckySlots", "luckyslots:*", "Gambling");
        add(presets, "LuckySlot", "luckyslot:*", "Gambling");
        add(presets, "WheelPlus", "wheelplus:*", "Gambling");
        add(presets, "WheelPro", "wheelpro:*", "Gambling");
        add(presets, "WheelSystem", "wheelsystem:*", "Gambling");
        add(presets, "LuckyWheelPlus", "luckywheelplus:*", "Gambling");
        add(presets, "LuckyWheelPro", "luckywheelpro:*", "Gambling");
        add(presets, "WheelSpin", "wheelspin:*", "Gambling");
        add(presets, "WheelSpins", "wheelspins:*", "Gambling");
        add(presets, "SpinWheel", "spinwheel:*", "Gambling");
        add(presets, "SpinToWin", "spintowin:*", "Gambling");
        add(presets, "DicePlus", "diceplus:*", "Gambling");
        add(presets, "DicePro", "dicepro:*", "Gambling");
        add(presets, "DiceSystem", "dicesystem:*", "Gambling");
        add(presets, "DiceRoll", "diceroll:*", "Gambling");
        add(presets, "DiceRolls", "dicerolls:*", "Gambling");
        add(presets, "RollDice", "rolldice:*", "Gambling");
        add(presets, "BlackjackPlus", "blackjackplus:*", "Gambling");
        add(presets, "BlackjackPro", "blackjackpro:*", "Gambling");
        add(presets, "BlackjackSystem", "blackjacksystem:*", "Gambling");
        add(presets, "PokerPlus", "pokerplus:*", "Gambling");
        add(presets, "PokerPro", "pokerpro:*", "Gambling");
        add(presets, "PokerSystem", "pokersystem:*", "Gambling");
        add(presets, "TexasHoldem", "texasholdem:*", "Gambling");
        add(presets, "Holdem", "holdem:*", "Gambling");
        add(presets, "BaccaratPlus", "baccaratplus:*", "Gambling");
        add(presets, "KenoPlus", "kenoplus:*", "Gambling");
        add(presets, "PlinkoPlus", "plinkoplus:*", "Gambling");
        add(presets, "PlinkoPro", "plinkopro:*", "Gambling");
        add(presets, "CrashGame", "crashgame:*", "Gambling");
        add(presets, "CrashPlus", "crashplus:*", "Gambling");
        add(presets, "CrashPro", "crashpro:*", "Gambling");
        add(presets, "MinesGame", "minesgame:*", "Gambling");
        add(presets, "MinesPlus", "minesplus:*", "Gambling");
        add(presets, "MinesPro", "minespro:*", "Gambling");
        add(presets, "RPSPlus", "rpsplus:*", "Gambling");
        add(presets, "RockPaperScissors", "rockpaperscissors:*", "Gambling");
        add(presets, "CaseBattle", "casebattle:*", "Gambling");
        add(presets, "CaseBattlePlus", "casebattleplus:*", "Gambling");
        add(presets, "CaseBattlePro", "casebattlepro:*", "Gambling");
        add(presets, "LootBattle", "lootbattle:*", "Gambling");
        add(presets, "LootBattles", "lootbattles:*", "Gambling");
        add(presets, "Ax Family", "ax*", "Frameworks");
        add(presets, "Ax Namespace", "ax:*", "Frameworks");
        add(presets, "Artillex", "artillex:*", "Frameworks");
        add(presets, "ArtillexStudios", "artillexstudios:*", "Frameworks");
        add(presets, "Artillex Studios", "artillex_studios:*", "Frameworks");
        add(presets, "AxVaults", "axvaults:*", "Storage");
        add(presets, "AxVault", "axvault:*", "Storage");
        add(presets, "AxBackpacks", "axbackpacks:*", "Storage");
        add(presets, "AxBackpack", "axbackpack:*", "Storage");
        add(presets, "AxStorage", "axstorage:*", "Storage");
        add(presets, "AxStorages", "axstorages:*", "Storage");
        add(presets, "AxShulkers", "axshulkers:*", "Storage");
        add(presets, "AxShulker", "axshulker:*", "Storage");
        add(presets, "AxEnderChest", "axenderchest:*", "Storage");
        add(presets, "AxEnderChests", "axenderchests:*", "Storage");
        add(presets, "AxTrade", "axtrade:*", "Shops");
        add(presets, "AxTrades", "axtrades:*", "Shops");
        add(presets, "AxMarket", "axmarket:*", "Shops");
        add(presets, "AxMarkets", "axmarkets:*", "Shops");
        add(presets, "AxEconomy", "axeconomy:*", "Economy");
        add(presets, "AxCoins", "axcoins:*", "Economy");
        add(presets, "AxGems", "axgems:*", "Economy");
        add(presets, "AxTokens", "axtokens:*", "Economy");
        add(presets, "AxRewards", "axrewards:*", "Rewards");
        add(presets, "AxReward", "axreward:*", "Rewards");
        add(presets, "AxDailyRewards", "axdailyrewards:*", "Rewards");
        add(presets, "AxKits", "axkits:*", "Rewards");
        add(presets, "AxKit", "axkit:*", "Rewards");
        add(presets, "AxMines", "axmines:*", "Prison");
        add(presets, "AxMine", "axmine:*", "Prison");
        add(presets, "AxPrison", "axprison:*", "Prison");
        add(presets, "AxRankup", "axrankup:*", "Prison");
        add(presets, "AxRanks", "axranks:*", "Prison");
        add(presets, "AxSellwands", "axsellwands:*", "Prison");
        add(presets, "AxSellwand", "axsellwand:*", "Prison");
        add(presets, "AxEnvoys", "axenvoys:*", "Crates");
        add(presets, "AxEnvoy", "axenvoy:*", "Crates");
        add(presets, "AxBoosters", "axboosters:*", "Prison");
        add(presets, "AxBooster", "axbooster:*", "Prison");
        add(presets, "AxGens", "axgens:*", "SMP");
        add(presets, "AxGen", "axgen:*", "SMP");
        add(presets, "AxGenerators", "axgenerators:*", "SMP");
        add(presets, "AxGenerator", "axgenerator:*", "SMP");
        add(presets, "AxCombat", "axcombat:*", "SMP");
        add(presets, "AxDuels", "axduels:*", "Minigames");
        add(presets, "AxDuelsPlus", "axduelsplus:*", "Minigames");
        add(presets, "AxParkour", "axparkour:*", "Minigames");
        add(presets, "AxQuests", "axquests:*", "Quests");
        add(presets, "AxQuest", "axquest:*", "Quests");
        add(presets, "AxMenus", "axmenus:*", "Menus");
        add(presets, "AxMenu", "axmenu:*", "Menus");
        add(presets, "AxChat", "axchat:*", "Chat");
        add(presets, "AxTags", "axtags:*", "Chat");
        add(presets, "AxScoreboard", "axscoreboard:*", "UI");
        add(presets, "AxTab", "axtab:*", "UI");
        add(presets, "AxHolograms", "axholograms:*", "Holograms");
        add(presets, "AxHologram", "axhologram:*", "Holograms");
        add(presets, "AxNPCs", "axnpcs:*", "NPCs");
        add(presets, "AxNPC", "axnpc:*", "NPCs");
        add(presets, "AxSecurity", "axsecurity:*", "Security");
        add(presets, "AxGuard", "axguard:*", "Security");
        add(presets, "AxAntiCrash", "axanticrash:*", "Security");
        add(presets, "AxAntiBot", "axantibot:*", "Security");
        add(presets, "AxAntiVPN", "axantivpn:*", "Security");
        add(presets, "Az Family", "az*", "Frameworks");
        add(presets, "Az Namespace", "az:*", "Frameworks");
        add(presets, "AzAuctionHouse", "azauctionhouse:*", "Auctions");
        add(presets, "AzAuctions", "azauctions:*", "Auctions");
        add(presets, "AzAuction", "azauction:*", "Auctions");
        add(presets, "AzShop", "azshop:*", "Shops");
        add(presets, "AzShops", "azshops:*", "Shops");
        add(presets, "AzMarket", "azmarket:*", "Shops");
        add(presets, "AzMarkets", "azmarkets:*", "Shops");
        add(presets, "AzVault", "azvault:*", "Storage");
        add(presets, "AzVaults", "azvaults:*", "Storage");
        add(presets, "AzStorage", "azstorage:*", "Storage");
        add(presets, "AzStorages", "azstorages:*", "Storage");
        add(presets, "AzBackpack", "azbackpack:*", "Storage");
        add(presets, "AzBackpacks", "azbackpacks:*", "Storage");
        add(presets, "AzMines", "azmines:*", "Prison");
        add(presets, "AzMine", "azmine:*", "Prison");
        add(presets, "AzPrison", "azprison:*", "Prison");
        add(presets, "AzCrates", "azcrates:*", "Crates");
        add(presets, "AzCrate", "azcrate:*", "Crates");
        add(presets, "AzCasino", "azcasino:*", "Gambling");
        add(presets, "AzGamble", "azgamble:*", "Gambling");
        add(presets, "AzRewards", "azrewards:*", "Rewards");
        add(presets, "AzReward", "azreward:*", "Rewards");
        add(presets, "AzKits", "azkits:*", "Rewards");
        add(presets, "AzKit", "azkit:*", "Rewards");
        add(presets, "AzLink", "azlink:*", "Proxy");
        add(presets, "AzAuth", "azauth:*", "Proxy");
        add(presets, "AzLogin", "azlogin:*", "Proxy");
        add(presets, "AzCore", "azcore:*", "Frameworks");
        add(presets, "AzLib", "azlib:*", "Frameworks");
        add(presets, "AzAPI", "azapi:*", "Frameworks");
        add(presets, "AzMenu", "azmenu:*", "Menus");
        add(presets, "AzMenus", "azmenus:*", "Menus");
        add(presets, "AzQuests", "azquests:*", "Quests");
        add(presets, "AzQuest", "azquest:*", "Quests");
        add(presets, "AzChat", "azchat:*", "Chat");
        add(presets, "AzTab", "aztab:*", "UI");
        add(presets, "AzHolograms", "azholograms:*", "Holograms");
        add(presets, "AzNPCs", "aznpcs:*", "NPCs");
        add(presets, "VirtualStorages", "virtualstorages:*", "Storage");
        add(presets, "VirtualStorage", "virtualstorage:*", "Storage");
        add(presets, "Virtual Storage", "virtual_storage:*", "Storage");
        add(presets, "VirtualStoragePlugin", "virtualstorageplugin:*", "Storage");
        add(presets, "VirtualBackpacks", "virtualbackpacks:*", "Storage");
        add(presets, "VirtualBackpack", "virtualbackpack:*", "Storage");
        add(presets, "Virtual Backpack", "virtual_backpack:*", "Storage");
        add(presets, "VirtualBags", "virtualbags:*", "Storage");
        add(presets, "VirtualBag", "virtualbag:*", "Storage");
        add(presets, "VirtualChest", "virtualchest:*", "Storage");
        add(presets, "VirtualChests", "virtualchests:*", "Storage");
        add(presets, "VirtualVault", "virtualvault:*", "Storage");
        add(presets, "VirtualVaults", "virtualvaults:*", "Storage");
        add(presets, "VirtualInventory", "virtualinventory:*", "Storage");
        add(presets, "VirtualInventories", "virtualinventories:*", "Storage");
        add(presets, "VirtualPack", "virtualpack:*", "Storage");
        add(presets, "VirtualPacks", "virtualpacks:*", "Storage");
        add(presets, "PersonalVaults", "personalvaults:*", "Storage");
        add(presets, "PersonalVault", "personalvault:*", "Storage");
        add(presets, "PersonalStorage", "personalstorage:*", "Storage");
        add(presets, "PersonalStorages", "personalstorages:*", "Storage");
        add(presets, "PlayerStorage", "playerstorage:*", "Storage");
        add(presets, "PlayerStorages", "playerstorages:*", "Storage");
        add(presets, "CloudStorage", "cloudstorage:*", "Storage");
        add(presets, "CloudStorages", "cloudstorages:*", "Storage");
        add(presets, "CloudVaults", "cloudvaults:*", "Storage");
        add(presets, "CloudVault", "cloudvault:*", "Storage");
        add(presets, "MineStorage", "minestorage:*", "Storage");
        add(presets, "MineStorages", "minestorages:*", "Storage");
        add(presets, "StoragePlus", "storageplus:*", "Storage");
        add(presets, "StoragePro", "storagepro:*", "Storage");
        add(presets, "StorageSystem", "storagesystem:*", "Storage");
        add(presets, "StorageCore", "storagecore:*", "Storage");
        add(presets, "EasyMines", "easymines:*", "Prison");
        add(presets, "EasyMine", "easymine:*", "Prison");
        add(presets, "EasyPrisonMines", "easyprisonmines:*", "Prison");
        add(presets, "EasyPrisonMine", "easyprisonmine:*", "Prison");
        add(presets, "EasyMineReset", "easyminereset:*", "Prison");
        add(presets, "EasyMineResets", "easymineresets:*", "Prison");
        add(presets, "CataMines", "catamines:*", "Prison");
        add(presets, "CataMine", "catamine:*", "Prison");
        add(presets, "PrisonMine", "prisonmine:*", "Prison");
        add(presets, "PrisonMinesPlus", "prisonminesplus:*", "Prison");
        add(presets, "PrisonMinePlus", "prisonmineplus:*", "Prison");
        add(presets, "PrisonMinesPro", "prisonminespro:*", "Prison");
        add(presets, "PrisonMinePro", "prisonminepro:*", "Prison");
        add(presets, "MineReset", "minereset:*", "Prison");
        add(presets, "MineResets", "mineresets:*", "Prison");
        add(presets, "MineResetLite", "mineresetlite:*", "Prison");
        add(presets, "MineResetPro", "mineresetpro:*", "Prison");
        add(presets, "AutoMineReset", "autominereset:*", "Prison");
        add(presets, "AutoMineResets", "automineresets:*", "Prison");
        add(presets, "AutoMines", "automines:*", "Prison");
        add(presets, "AutoMine", "automine:*", "Prison");
        add(presets, "MineManager", "minemanager:*", "Prison");
        add(presets, "MinesManager", "minesmanager:*", "Prison");
        add(presets, "MineSystem", "minesystem:*", "Prison");
        add(presets, "MinesSystem", "minessystem:*", "Prison");
        add(presets, "MineCore", "minecore:*", "Prison");
        add(presets, "MinesCore", "minescore:*", "Prison");
        add(presets, "BlockMines", "blockmines:*", "Prison");
        add(presets, "BlockMine", "blockmine:*", "Prison");
        add(presets, "AdvancedMines", "advancedmines:*", "Prison");
        add(presets, "AdvancedMine", "advancedmine:*", "Prison");
        add(presets, "UltimateMines", "ultimatemines:*", "Prison");
        add(presets, "UltimateMine", "ultimatemine:*", "Prison");
        add(presets, "MineWorld", "mineworld:*", "Prison");
        add(presets, "MineWorlds", "mineworlds:*", "Prison");
        add(presets, "MineRegion", "mineregion:*", "Prison");
        add(presets, "MineRegions", "mineregions:*", "Prison");
        add(presets, "Container", "container:*", "Storage");
        add(presets, "Containers", "containers:*", "Storage");
        add(presets, "ContainerGUI", "containergui:*", "Storage");
        add(presets, "ContainerMenu", "containermenu:*", "Storage");
        add(presets, "ContainerPlus", "containerplus:*", "Storage");
        add(presets, "ContainerPro", "containerpro:*", "Storage");
        add(presets, "OpenContainer", "opencontainer:*", "Storage");
        add(presets, "OpenContainers", "opencontainers:*", "Storage");
        add(presets, "PortableStorage", "portablestorage:*", "Storage");
        add(presets, "PortableStorages", "portablestorages:*", "Storage");
        add(presets, "PortableChest", "portablechest:*", "Storage");
        add(presets, "PortableChests", "portablechests:*", "Storage");
        add(presets, "PortableVault", "portablevault:*", "Storage");
        add(presets, "PortableVaults", "portablevaults:*", "Storage");
        add(presets, "PortableBackpack", "portablebackpack:*", "Storage");
        add(presets, "PortableBackpacks", "portablebackpacks:*", "Storage");
        add(presets, "PowerfulBackpacks", "powerfulbackpacks:*", "Storage");
        add(presets, "PowerfulBackpack", "powerfulbackpack:*", "Storage");
        add(presets, "MyBackpack", "mybackpack:*", "Storage");
        add(presets, "MyBackpacks", "mybackpacks:*", "Storage");
        add(presets, "My Backpack", "my_backpack:*", "Storage");
        add(presets, "BackpackPlus", "backpackplus:*", "Storage");
        add(presets, "Backpack Plus", "backpack_plus:*", "Storage");
        add(presets, "BetterBackpacks", "betterbackpacks:*", "Storage");
        add(presets, "BetterBackpack", "betterbackpack:*", "Storage");
        add(presets, "AdvancedBackpack", "advancedbackpack:*", "Storage");
        add(presets, "AdvancedBackpacks NBT", "advancedbackpacksnbt:*", "Storage");
        add(presets, "ExpendableBackpacks", "expendablebackpacks:*", "Storage");
        add(presets, "ExpendableBackpack", "expendablebackpack:*", "Storage");
        add(presets, "Minepack", "minepack:*", "Storage");
        add(presets, "MineBackpacks", "minebackpacks:*", "Storage");
        add(presets, "MineBackpack", "minebackpack:*", "Storage");
        add(presets, "SimpleBackpacks", "simplebackpacks:*", "Storage");
        add(presets, "SimpleBackpack", "simplebackpack:*", "Storage");
        add(presets, "EasyBackpacks", "easybackpacks:*", "Storage");
        add(presets, "EasyBackpack", "easybackpack:*", "Storage");
        add(presets, "UltimateBackpacks", "ultimatebackpacks:*", "Storage");
        add(presets, "UltimateBackpack", "ultimatebackpack:*", "Storage");
        add(presets, "EpicBackpacks", "epicbackpacks:*", "Storage");
        add(presets, "EpicBackpack", "epicbackpack:*", "Storage");
        add(presets, "DeluxeBackpacks", "deluxebackpacks:*", "Storage");
        add(presets, "DeluxeBackpack", "deluxebackpack:*", "Storage");
        add(presets, "PremiumBackpacks", "premiumbackpacks:*", "Storage");
        add(presets, "PremiumBackpack", "premiumbackpack:*", "Storage");
        add(presets, "RoyalBackpacks", "royalbackpacks:*", "Storage");
        add(presets, "RoyalBackpack", "royalbackpack:*", "Storage");
        add(presets, "UpgradeableBackpacks", "upgradeablebackpacks:*", "Storage");
        add(presets, "UpgradeableBackpack", "upgradeablebackpack:*", "Storage");
        add(presets, "SmartBackpacks", "smartbackpacks:*", "Storage");
        add(presets, "SmartBackpack", "smartbackpack:*", "Storage");
        add(presets, "BackpackItem", "backpackitem:*", "Storage");
        add(presets, "BackpackItems", "backpackitems:*", "Storage");
        add(presets, "BackpackGUI", "backpackgui:*", "Storage");
        add(presets, "BackpackMenu", "backpackmenu:*", "Storage");
        add(presets, "BackpackSystem", "backpacksystem:*", "Storage");
        add(presets, "BackpacksSystem", "backpackssystem:*", "Storage");
        add(presets, "BackpackCore", "backpackcore:*", "Storage");
        add(presets, "BackpacksCore", "backpackscore:*", "Storage");
        add(presets, "OpenShulker", "openshulker:*", "Storage");
        add(presets, "OpenShulkers", "openshulkers:*", "Storage");
        add(presets, "OpenShulkerBox", "openshulkerbox:*", "Storage");
        add(presets, "OpenShulkerBoxes", "openshulkerboxes:*", "Storage");
        add(presets, "Open Shulker", "open_shulker:*", "Storage");
        add(presets, "Open Shulker Box", "open_shulker_box:*", "Storage");
        add(presets, "ShulkerBackpacks", "shulkerbackpacks:*", "Storage");
        add(presets, "ShulkerBackpack", "shulkerbackpack:*", "Storage");
        add(presets, "Shulker Backpacks", "shulker_backpacks:*", "Storage");
        add(presets, "Shulker Backpack", "shulker_backpack:*", "Storage");
        add(presets, "ShulkerBoxBackpacks", "shulkerboxbackpacks:*", "Storage");
        add(presets, "ShulkerBoxBackpack", "shulkerboxbackpack:*", "Storage");
        add(presets, "ShulkerBoxPlus", "shulkerboxplus:*", "Storage");
        add(presets, "ShulkerPlus", "shulkerplus:*", "Storage");
        add(presets, "EShulkerBox", "eshulkerbox:*", "Storage");
        add(presets, "EShulker", "eshulker:*", "Storage");
        add(presets, "BetterShulker", "bettershulker:*", "Storage");
        add(presets, "BetterShulkers", "bettershulkers:*", "Storage");
        add(presets, "BetterShulkerBoxes", "bettershulkerboxes:*", "Storage");
        add(presets, "BetterShulkerBox", "bettershulkerbox:*", "Storage");
        add(presets, "ShulkerUtils", "shulkerutils:*", "Storage");
        add(presets, "ShulkerUtility", "shulkerutility:*", "Storage");
        add(presets, "ShulkerUtilities", "shulkerutilities:*", "Storage");
        add(presets, "ShulkerOpener", "shulkeropener:*", "Storage");
        add(presets, "ShulkerOpen", "shulkeropen:*", "Storage");
        add(presets, "ShulkerPreview", "shulkerpreview:*", "Storage");
        add(presets, "ShulkerPreviews", "shulkerpreviews:*", "Storage");
        add(presets, "ShulkerViewer", "shulkerviewer:*", "Storage");
        add(presets, "ShulkerView", "shulkerview:*", "Storage");
        add(presets, "ShulkerPacks", "shulkerpacks:*", "Storage");
        add(presets, "ShulkerPack", "shulkerpack:*", "Storage");
        add(presets, "EnderChestPlus", "enderchestplus:*", "Storage");
        add(presets, "EnderChestVault", "enderchestvault:*", "Storage");
        add(presets, "EnderChestVaults", "enderchestvaults:*", "Storage");
        add(presets, "EnderChest", "enderchest:*", "Storage");
        add(presets, "EnderChests", "enderchests:*", "Storage");
        add(presets, "Ender Chest", "ender_chest:*", "Storage");
        add(presets, "Ender Chests", "ender_chests:*", "Storage");
        add(presets, "EnderChestX", "enderchestx:*", "Storage");
        add(presets, "EnderChestPro", "enderchestpro:*", "Storage");
        add(presets, "EnderChestCore", "enderchestcore:*", "Storage");
        add(presets, "EnderChestSystem", "enderchestsystem:*", "Storage");
        add(presets, "EnderStorage", "enderstorage:*", "Storage");
        add(presets, "EnderStorages", "enderstorages:*", "Storage");
        add(presets, "EnderContainer", "endercontainer:*", "Storage");
        add(presets, "EnderContainers", "endercontainers:*", "Storage");
        add(presets, "EnderBackpack", "enderbackpack:*", "Storage");
        add(presets, "EnderBackpacks", "enderbackpacks:*", "Storage");
        add(presets, "EnderPack", "enderpack:*", "Storage");
        add(presets, "EnderPacks", "enderpacks:*", "Storage");
        add(presets, "BetterEnderChest", "betterenderchest:*", "Storage");
        add(presets, "BetterEnderChests", "betterenderchests:*", "Storage");
        add(presets, "AdvancedEnderChest", "advancedenderchest:*", "Storage");
        add(presets, "AdvancedEnderChests", "advancedenderchests:*", "Storage");
        add(presets, "DeluxeEnderChest", "deluxeenderchest:*", "Storage");
        add(presets, "DeluxeEnderChests", "deluxeenderchests:*", "Storage");
        add(presets, "PrivateEnderChest", "privateenderchest:*", "Storage");
        add(presets, "PrivateEnderChests", "privateenderchests:*", "Storage");
        add(presets, "PVaults", "pvaults:*", "Storage");
        add(presets, "PVault", "pvault:*", "Storage");
        add(presets, "PVs", "pvs:*", "Storage");
        add(presets, "PlayerVault", "playervault:*", "Storage");
        add(presets, "PlayerVaultPlus", "playervaultplus:*", "Storage");
        add(presets, "PlayerVaultsPlus", "playervaultsplus:*", "Storage");
        add(presets, "PlayerVaultPro", "playervaultpro:*", "Storage");
        add(presets, "PlayerVaultsPro", "playervaultspro:*", "Storage");
        add(presets, "PlayerVaultsGUI", "playervaultsgui:*", "Storage");
        add(presets, "PlayerVaultGUI", "playervaultgui:*", "Storage");
        add(presets, "PlayerVaultsMenu", "playervaultsmenu:*", "Storage");
        add(presets, "PlayerVaultMenu", "playervaultmenu:*", "Storage");
        add(presets, "PlayerVaultsCore", "playervaultscore:*", "Storage");
        add(presets, "PlayerVaultCore", "playervaultcore:*", "Storage");
        add(presets, "PlayerVaultsSystem", "playervaultssystem:*", "Storage");
        add(presets, "PlayerVaultSystem", "playervaultsystem:*", "Storage");
        add(presets, "BetterPlayerVaults", "betterplayervaults:*", "Storage");
        add(presets, "BetterPlayerVault", "betterplayervault:*", "Storage");
        add(presets, "AdvancedPlayerVaults", "advancedplayervaults:*", "Storage");
        add(presets, "AdvancedPlayerVault", "advancedplayervault:*", "Storage");
        add(presets, "DeluxePlayerVaults", "deluxeplayervaults:*", "Storage");
        add(presets, "DeluxePlayerVault", "deluxeplayervault:*", "Storage");
        add(presets, "InsaneVaults", "insanevaults:*", "Storage");
        add(presets, "InsaneVault", "insanevault:*", "Storage");
        add(presets, "SuperVaults", "supervaults:*", "Storage");
        add(presets, "SuperVault", "supervault:*", "Storage");
        add(presets, "PrivateVaults", "privatevaults:*", "Storage");
        add(presets, "PrivateVault", "privatevault:*", "Storage");
        add(presets, "VaultPlus", "vaultplus:*", "Storage");
        add(presets, "VaultPro", "vaultpro:*", "Storage");
        add(presets, "VaultSystem", "vaultsystem:*", "Storage");
        add(presets, "VaultCore", "vaultcore:*", "Storage");
        add(presets, "VaultGUI", "vaultgui:*", "Storage");
        add(presets, "VaultMenu", "vaultmenu:*", "Storage");
        add(presets, "StorageGUI", "storagegui:*", "Storage");
        add(presets, "StorageMenu", "storagemenu:*", "Storage");
        add(presets, "StorageVaults", "storagevaults:*", "Storage");
        add(presets, "StorageVault", "storagevault:*", "Storage");
        add(presets, "TopMinions", "topminions:*", "Minions");
        add(presets, "TopMinion", "topminion:*", "Minions");
        add(presets, "JetsMinions", "jetsminions:*", "Minions");
        add(presets, "JetsMinion", "jetsminion:*", "Minions");
        add(presets, "JetMinions", "jetminions:*", "Minions");
        add(presets, "JetMinion", "jetminion:*", "Minions");
        add(presets, "MiniMinions", "miniminions:*", "Minions");
        add(presets, "MiniMinion", "miniminion:*", "Minions");
        add(presets, "Minions", "minions:*", "Minions");
        add(presets, "Minion", "minion:*", "Minions");
        add(presets, "MinionPlugin", "minionplugin:*", "Minions");
        add(presets, "MinionsPlugin", "minionsplugin:*", "Minions");
        add(presets, "MinionCore", "minioncore:*", "Minions");
        add(presets, "MinionsCore", "minionscore:*", "Minions");
        add(presets, "MinionSystem", "minionsystem:*", "Minions");
        add(presets, "MinionsSystem", "minionssystem:*", "Minions");
        add(presets, "MinionPlus", "minionplus:*", "Minions");
        add(presets, "MinionsPlus", "minionsplus:*", "Minions");
        add(presets, "MinionPro", "minionpro:*", "Minions");
        add(presets, "MinionsPro", "minionspro:*", "Minions");
        add(presets, "MinionGUI", "miniongui:*", "Minions");
        add(presets, "MinionsGUI", "minionsgui:*", "Minions");
        add(presets, "MinionMenu", "minionmenu:*", "Minions");
        add(presets, "MinionsMenu", "minionsmenu:*", "Minions");
        add(presets, "MinionUpgrades", "minionupgrades:*", "Minions");
        add(presets, "MinionUpgrade", "minionupgrade:*", "Minions");
        add(presets, "MinionStorage", "minionstorage:*", "Minions");
        add(presets, "MinionsStorage", "minionsstorage:*", "Minions");
        add(presets, "MinionChest", "minionchest:*", "Minions");
        add(presets, "MinionChests", "minionchests:*", "Minions");
        add(presets, "MinionShop", "minionshop:*", "Minions");
        add(presets, "MinionsShop", "minionsshop:*", "Minions");
        add(presets, "MinionManager", "minionmanager:*", "Minions");
        add(presets, "MinionsManager", "minionsmanager:*", "Minions");
        add(presets, "SuperiorMinions", "superiorminions:*", "Minions");
        add(presets, "SuperiorMinion", "superiorminion:*", "Minions");
        add(presets, "EpicMinions", "epicminions:*", "Minions");
        add(presets, "EpicMinion", "epicminion:*", "Minions");
        add(presets, "AdvancedMinions", "advancedminions:*", "Minions");
        add(presets, "AdvancedMinion", "advancedminion:*", "Minions");
        add(presets, "UltimateMinions", "ultimateminions:*", "Minions");
        add(presets, "UltimateMinion", "ultimateminion:*", "Minions");
        add(presets, "DeluxeMinions", "deluxeminions:*", "Minions");
        add(presets, "DeluxeMinion", "deluxeminion:*", "Minions");
        add(presets, "BetterMinions", "betterminions:*", "Minions");
        add(presets, "BetterMinion", "betterminion:*", "Minions");
        add(presets, "SimpleMinions", "simpleminions:*", "Minions");
        add(presets, "SimpleMinion", "simpleminion:*", "Minions");
        add(presets, "SkyblockMinions", "skyblockminions:*", "Minions");
        add(presets, "SkyblockMinion", "skyblockminion:*", "Minions");
        add(presets, "HypixelMinions", "hypixelminions:*", "Minions");
        add(presets, "HypixelMinion", "hypixelminion:*", "Minions");
        add(presets, "Robots", "robots:*", "Minions");
        add(presets, "Robot", "robot:*", "Minions");
        add(presets, "Worker", "worker:*", "Minions");
        add(presets, "Workers", "workers:*", "Minions");
        add(presets, "Helpers", "helpers:*", "Minions");
        add(presets, "Helper", "helper:*", "Minions");
        add(presets, "AutoFarmers", "autofarmers:*", "Minions");
        add(presets, "AutoFarmer", "autofarmer:*", "Minions");
        add(presets, "AutoMiners", "autominers:*", "Minions");
        add(presets, "AutoMiner", "autominer:*", "Minions");
        add(presets, "MinerMinions", "minerminions:*", "Minions");
        add(presets, "MinerMinion", "minerminion:*", "Minions");
        add(presets, "FarmerMinions", "farmerminions:*", "Minions");
        add(presets, "FarmerMinion", "farmerminion:*", "Minions");
        add(presets, "Vaults Fabricate", "vaultsfabricate:*", "Storage");
        add(presets, "Vault Fabricate", "vaultfabricate:*", "Storage");
        add(presets, "VaultsFabricated", "vaultsfabricated:*", "Storage");
        add(presets, "FabricatedVaults", "fabricatedvaults:*", "Storage");
        add(presets, "FabricatedVault", "fabricatedvault:*", "Storage");
        add(presets, "FabricateVaults", "fabricatevaults:*", "Storage");
        add(presets, "FabricateVault", "fabricatevault:*", "Storage");
        add(presets, "BsruEnderChest", "bsruenderchest:*", "Storage");
        add(presets, "BSRUEnderChest", "bsru_enderchest:*", "Storage");
        add(presets, "BSRU Ender Chest", "bsru_ender_chest:*", "Storage");
        add(presets, "TH Backpacks", "th_backpacks:*", "Storage");
        add(presets, "TH Backpack", "th_backpack:*", "Storage");
        add(presets, "THBackpacks", "thbackpacks:*", "Storage");
        add(presets, "THBackpack", "thbackpack:*", "Storage");
        add(presets, "Kix Simple Backpacks", "kixsimplebackpacks:*", "Storage");
        add(presets, "Kix Simple Backpack", "kixsimplebackpack:*", "Storage");
        add(presets, "Kix's Simple Backpacks", "kixssimplebackpacks:*", "Storage");
        add(presets, "Kix Backpacks", "kixbackpacks:*", "Storage");
        add(presets, "Kix Backpack", "kixbackpack:*", "Storage");
        add(presets, "Kix", "kix:*", "Storage");
        add(presets, "BundleBack", "bundleback:*", "Storage");
        add(presets, "BundleBackpack", "bundlebackpack:*", "Storage");
        add(presets, "BundleBackpacks", "bundlebackpacks:*", "Storage");
        add(presets, "BundleBag", "bundlebag:*", "Storage");
        add(presets, "BundleBags", "bundlebags:*", "Storage");
        add(presets, "BundleStorage", "bundlestorage:*", "Storage");
        add(presets, "BundleStorages", "bundlestorages:*", "Storage");
        add(presets, "BundleChest", "bundlechest:*", "Storage");
        add(presets, "BundleChests", "bundlechests:*", "Storage");
        add(presets, "BundleVault", "bundlevault:*", "Storage");
        add(presets, "BundleVaults", "bundlevaults:*", "Storage");
        add(presets, "EasyShulkers", "easyshulkers:*", "Storage");
        add(presets, "EasyShulker", "easyshulker:*", "Storage");
        add(presets, "EasyShulkerBox", "easyshulkerbox:*", "Storage");
        add(presets, "EasyShulkerBoxes", "easyshulkerboxes:*", "Storage");
        add(presets, "Xiaoklunar Shulker", "xiaoklunarshulker:*", "Storage");
        add(presets, "Xiaoklunar Shulkers", "xiaoklunarshulkers:*", "Storage");
        add(presets, "XiaoklunarShulker", "xiaoklunar_shulker:*", "Storage");
        add(presets, "XiaoklunarShulkers", "xiaoklunar_shulkers:*", "Storage");
        add(presets, "Xiaoklunar", "xiaoklunar:*", "Storage");
        add(presets, "Bigger Chest", "biggerchest:*", "Storage");
        add(presets, "Bigger Chests", "biggerchests:*", "Storage");
        add(presets, "BiggerChest", "bigger_chest:*", "Storage");
        add(presets, "BiggerChests", "bigger_chests:*", "Storage");
        add(presets, "LargeChest", "largechest:*", "Storage");
        add(presets, "LargeChests", "largechests:*", "Storage");
        add(presets, "HugeChest", "hugechest:*", "Storage");
        add(presets, "HugeChests", "hugechests:*", "Storage");
        add(presets, "ExpandedChest", "expandedchest:*", "Storage");
        add(presets, "ExpandedChests", "expandedchests:*", "Storage");
        add(presets, "ExtendedChest", "extendedchest:*", "Storage");
        add(presets, "ExtendedChests", "extendedchests:*", "Storage");
        add(presets, "DeathChestPro", "deathchestpro:*", "Storage");
        add(presets, "DeathChestPlus", "deathchestplus:*", "Storage");
        add(presets, "DeathChestX", "deathchestx:*", "Storage");
        add(presets, "DeathChestReloaded", "deathchestreloaded:*", "Storage");
        add(presets, "DeathChestSystem", "deathchestsystem:*", "Storage");
        add(presets, "DeathChestsSystem", "deathchestssystem:*", "Storage");
        add(presets, "GraveChest", "gravechest:*", "Storage");
        add(presets, "GraveChests", "gravechests:*", "Storage");
        add(presets, "CorpseChest", "corpsechest:*", "Storage");
        add(presets, "CorpseChests", "corpsechests:*", "Storage");
        add(presets, "RoseStacker GUI", "rosestackergui:*", "Storage");
        add(presets, "RoseStacker Menu", "rosestackermenu:*", "Storage");
        add(presets, "RoseStacker Stacker", "rosestackerstacker:*", "Storage");
        add(presets, "EnderEX", "enderex:*", "Storage");
        add(presets, "EnderEx", "ender_ex:*", "Storage");
        add(presets, "EnderEX Storage", "enderexstorage:*", "Storage");
        add(presets, "EnderEX Chest", "enderexchest:*", "Storage");
        add(presets, "EnderEX Vault", "enderexvault:*", "Storage");
        add(presets, "EnderExpansion", "enderexpansion:*", "Storage");
        add(presets, "EnderExtended", "enderextended:*", "Storage");
        add(presets, "EnderExtender", "enderextender:*", "Storage");
        add(presets, "EnderPlus", "enderplus:*", "Storage");
        add(presets, "EnderPro", "enderpro:*", "Storage");
        add(presets, "GUIContainers", "guicontainers:*", "Storage");
        add(presets, "GUIContainer", "guicontainer:*", "Storage");
        add(presets, "VirtualContainers", "virtualcontainers:*", "Storage");
        add(presets, "VirtualContainer", "virtualcontainer:*", "Storage");
        add(presets, "RemoteStorage", "remotestorage:*", "Storage");
        add(presets, "RemoteStorages", "remotestorages:*", "Storage");
        add(presets, "RemoteChest", "remotechest:*", "Storage");
        add(presets, "RemoteChests", "remotechests:*", "Storage");
        add(presets, "RemoteVault", "remotevault:*", "Storage");
        add(presets, "RemoteVaults", "remotevaults:*", "Storage");
        add(presets, "AnywhereStorage", "anywherestorage:*", "Storage");
        add(presets, "AnywhereChest", "anywherechest:*", "Storage");
        add(presets, "AnywhereVault", "anywherevault:*", "Storage");
        add(presets, "PortableShulker", "portableshulker:*", "Storage");
        add(presets, "PortableShulkers", "portableshulkers:*", "Storage");
        add(presets, "PortableShulkerBox", "portableshulkerbox:*", "Storage");
        add(presets, "PortableShulkerBoxes", "portableshulkerboxes:*", "Storage");
        add(presets, "PocketChest", "pocketchest:*", "Storage");
        add(presets, "PocketChests", "pocketchests:*", "Storage");
        add(presets, "PocketStorage", "pocketstorage:*", "Storage");
        add(presets, "PocketVault", "pocketvault:*", "Storage");
        add(presets, "PocketVaults", "pocketvaults:*", "Storage");
        add(presets, "PocketBackpack", "pocketbackpack:*", "Storage");
        add(presets, "PocketBackpacks", "pocketbackpacks:*", "Storage");
        add(presets, "PersonalChest", "personalchest:*", "Storage");
        add(presets, "PersonalChests", "personalchests:*", "Storage");
        add(presets, "PersonalEnderChest", "personalenderchest:*", "Storage");
        add(presets, "PersonalEnderChests", "personalenderchests:*", "Storage");
        add(presets, "CustomChest", "customchest:*", "Storage");
        add(presets, "CustomChests", "customchests:*", "Storage");
        add(presets, "CustomVault", "customvault:*", "Storage");
        add(presets, "CustomVaults", "customvaults:*", "Storage");
        add(presets, "CustomBackpack", "custombackpack:*", "Storage");
        add(presets, "CustomBackpacks", "custombackpacks:*", "Storage");
        add(presets, "MinionBackpack", "minionbackpack:*", "Minions");
        add(presets, "MinionBackpacks", "minionbackpacks:*", "Minions");
        add(presets, "MinionChestPlus", "minionchestplus:*", "Minions");
        add(presets, "MinionVault", "minionvault:*", "Minions");
        add(presets, "MinionVaults", "minionvaults:*", "Minions");
        add(presets, "MinionContainer", "minioncontainer:*", "Minions");
        add(presets, "MinionContainers", "minioncontainers:*", "Minions");
        add(presets, "VShulker", "vshulker:*", "Storage");
        add(presets, "VShulkers", "vshulkers:*", "Storage");
        add(presets, "VShulkerBox", "vshulkerbox:*", "Storage");
        add(presets, "VShulkerBoxes", "vshulkerboxes:*", "Storage");
        add(presets, "VirtualShulker", "virtualshulker:*", "Storage");
        add(presets, "VirtualShulkers", "virtualshulkers:*", "Storage");
        add(presets, "VirtualShulkerBox", "virtualshulkerbox:*", "Storage");
        add(presets, "VirtualShulkerBoxes", "virtualshulkerboxes:*", "Storage");
        add(presets, "DonutEC", "donutec:*", "Storage");
        add(presets, "Donut EChest", "donutechest:*", "Storage");
        add(presets, "Donut EnderChest", "donutenderchest:*", "Storage");
        add(presets, "Donut EnderChests", "donutenderchests:*", "Storage");
        add(presets, "Donut Ender Chest", "donut_ender_chest:*", "Storage");
        add(presets, "Donut Ender Chests", "donut_ender_chests:*", "Storage");
        add(presets, "Donut Ender Storage", "donutenderstorage:*", "Storage");
        add(presets, "Donut Storage", "donutstorage:*", "Storage");
        add(presets, "Donut Vault", "donutvault:*", "Storage");
        add(presets, "Donut Vaults", "donutvaults:*", "Storage");
        add(presets, "Donut Backpack", "donutbackpack:*", "Storage");
        add(presets, "Donut Backpacks", "donutbackpacks:*", "Storage");
        add(presets, "GUI Family", "gui*", "Menus");
        add(presets, "Menu Family", "menu*", "Menus");
        add(presets, "Inventory Family", "inventory*", "Menus");
        add(presets, "Inventory Namespace", "inventory:*", "Menus");
        add(presets, "Chest Family", "chest*", "Storage");
        add(presets, "Chest Namespace", "chest:*", "Storage");
        add(presets, "OpenInv Family", "openinv*", "Storage");
        add(presets, "OpenInventory Family", "openinventory*", "Storage");
        add(presets, "ChestCommands", "chestcommands:*", "Menus");
        add(presets, "ChestCommand", "chestcommand:*", "Menus");
        add(presets, "ChestCommandsPlus", "chestcommandsplus:*", "Menus");
        add(presets, "ChestCommandsPro", "chestcommandspro:*", "Menus");
        add(presets, "ChestGUI", "chestgui:*", "Menus");
        add(presets, "ChestGUIs", "chestguis:*", "Menus");
        add(presets, "ChestMenu", "chestmenu:*", "Menus");
        add(presets, "ChestMenus", "chestmenus:*", "Menus");
        add(presets, "ChestInventory", "chestinventory:*", "Menus");
        add(presets, "ChestInventories", "chestinventories:*", "Menus");
        add(presets, "ZMenu", "zmenu:*", "Menus");
        add(presets, "ZMenus", "zmenus:*", "Menus");
        add(presets, "ZMenuPlus", "zmenuplus:*", "Menus");
        add(presets, "ZMenuPro", "zmenupro:*", "Menus");
        add(presets, "ZMenuGui", "zmenugui:*", "Menus");
        add(presets, "TrMenu", "trmenu:*", "Menus");
        add(presets, "TrMenus", "trmenus:*", "Menus");
        add(presets, "TrMenuPlus", "trmenuplus:*", "Menus");
        add(presets, "TrMenuPro", "trmenupro:*", "Menus");
        add(presets, "CustomGUI", "customgui:*", "Menus");
        add(presets, "CustomGUIs", "customguis:*", "Menus");
        add(presets, "CustomGuiPlus", "customguiplus:*", "Menus");
        add(presets, "CustomGuiPro", "customguipro:*", "Menus");
        add(presets, "CustomMenu", "custommenu:*", "Menus");
        add(presets, "CustomMenus", "custommenus:*", "Menus");
        add(presets, "CustomMenuPlus", "custommenuplus:*", "Menus");
        add(presets, "CustomMenuPro", "custommenupro:*", "Menus");
        add(presets, "CustomInventory", "custominventory:*", "Menus");
        add(presets, "CustomInventories", "custominventories:*", "Menus");
        add(presets, "InventoryMenu", "inventorymenu:*", "Menus");
        add(presets, "InventoryMenus", "inventorymenus:*", "Menus");
        add(presets, "InventoryGUIPlus", "inventoryguiplus:*", "Menus");
        add(presets, "InventoryGUIPro", "inventoryguipro:*", "Menus");
        add(presets, "InventoryMenuPlus", "inventorymenuplus:*", "Menus");
        add(presets, "InventoryMenuPro", "inventorymenupro:*", "Menus");
        add(presets, "GUIPlus", "guiplus:*", "Menus");
        add(presets, "GUIPro", "guipro:*", "Menus");
        add(presets, "GUICore", "guicore:*", "Menus");
        add(presets, "GUISystem", "guisystem:*", "Menus");
        add(presets, "GUIMenu", "guimenu:*", "Menus");
        add(presets, "GUIMenus", "guimenus:*", "Menus");
        add(presets, "BetterGUI", "bettergui:*", "Menus");
        add(presets, "BetterGUIs", "betterguis:*", "Menus");
        add(presets, "BetterMenu", "bettermenu:*", "Menus");
        add(presets, "BetterMenus", "bettermenus:*", "Menus");
        add(presets, "SimpleGUI", "simplegui:*", "Menus");
        add(presets, "SimpleGUIs", "simpleguis:*", "Menus");
        add(presets, "SimpleMenu", "simplemenu:*", "Menus");
        add(presets, "SimpleMenus", "simplemenus:*", "Menus");
        add(presets, "EasyGUI", "easygui:*", "Menus");
        add(presets, "EasyGUIs", "easyguis:*", "Menus");
        add(presets, "EasyMenu", "easymenu:*", "Menus");
        add(presets, "EasyMenus", "easymenus:*", "Menus");
        add(presets, "AdvancedGUI", "advancedgui:*", "Menus");
        add(presets, "AdvancedGUIs", "advancedguis:*", "Menus");
        add(presets, "AdvancedMenu", "advancedmenu:*", "Menus");
        add(presets, "AdvancedMenus", "advancedmenus:*", "Menus");
        add(presets, "DeluxeGUI", "deluxegui:*", "Menus");
        add(presets, "DeluxeGUIs", "deluxeguis:*", "Menus");
        add(presets, "UltraGUI", "ultragui:*", "Menus");
        add(presets, "UltraGUIs", "ultraguis:*", "Menus");
        add(presets, "UltraMenu", "ultramenu:*", "Menus");
        add(presets, "UltraMenus", "ultramenus:*", "Menus");
        add(presets, "PremiumGUI", "premiumgui:*", "Menus");
        add(presets, "PremiumGUIs", "premiumguis:*", "Menus");
        add(presets, "PremiumMenu", "premiummenu:*", "Menus");
        add(presets, "PremiumMenus", "premiummenus:*", "Menus");
        add(presets, "SmartMenus", "smartmenus:*", "Menus");
        add(presets, "SmartMenu", "smartmenu:*", "Menus");
        add(presets, "MenuGUI", "menugui:*", "Menus");
        add(presets, "MenuGUIs", "menuguis:*", "Menus");
        add(presets, "MenuBuilder", "menubuilder:*", "Menus");
        add(presets, "GUIBuilder", "guibuilder:*", "Menus");
        add(presets, "InventoryBuilder", "inventorybuilder:*", "Menus");
        add(presets, "MenuEngine", "menuengine:*", "Menus");
        add(presets, "GUIEngine", "guiengine:*", "Menus");
        add(presets, "InventoryEngine", "inventoryengine:*", "Menus");
        add(presets, "MenuAPI", "menuapi:*", "Menus");
        add(presets, "GUIAPI", "guiapi:*", "Menus");
        add(presets, "InventoryAPI", "inventoryapi:*", "Menus");
        add(presets, "NoobShulkerBoxes", "noobshulkerboxes:*", "Storage");
        add(presets, "NoobShulkerBox", "noobshulkerbox:*", "Storage");
        add(presets, "NoobShulker", "noobshulker:*", "Storage");
        add(presets, "NoobShulkers", "noobshulkers:*", "Storage");
        add(presets, "FastShulker", "fastshulker:*", "Storage");
        add(presets, "FastShulkers", "fastshulkers:*", "Storage");
        add(presets, "FastShulkerBox", "fastshulkerbox:*", "Storage");
        add(presets, "FastShulkerBoxes", "fastshulkerboxes:*", "Storage");
        add(presets, "UltimateShulker", "ultimateshulker:*", "Storage");
        add(presets, "UltimateShulkers", "ultimateshulkers:*", "Storage");
        add(presets, "UltimateShulkerBox", "ultimateshulkerbox:*", "Storage");
        add(presets, "UltimateShulkerBoxes", "ultimateshulkerboxes:*", "Storage");
        add(presets, "SuperShulker", "supershulker:*", "Storage");
        add(presets, "SuperShulkers", "supershulkers:*", "Storage");
        add(presets, "SmartShulker", "smartshulker:*", "Storage");
        add(presets, "SmartShulkers", "smartshulkers:*", "Storage");
        add(presets, "ShulkerFast", "shulkerfast:*", "Storage");
        add(presets, "ShulkersFast", "shulkersfast:*", "Storage");
        add(presets, "AH Family", "ah*", "Auctions");
        add(presets, "Auction Family", "auction*", "Auctions");
        add(presets, "AuctionHouse Family", "auctionhouse*", "Auctions");
        add(presets, "ZelAuction", "zelauction:*", "Auctions");
        add(presets, "ZelAuctionHouse", "zelauctionhouse:*", "Auctions");
        add(presets, "Zel Auction", "zel_auction:*", "Auctions");
        add(presets, "Zel Auction House", "zel_auction_house:*", "Auctions");
        add(presets, "ZelAH", "zelah:*", "Auctions");
        add(presets, "Zel AH", "zel_ah:*", "Auctions");
        add(presets, "OutAuction", "outauction:*", "Auctions");
        add(presets, "OutAuctions", "outauctions:*", "Auctions");
        add(presets, "OutAuctionHouse", "outauctionhouse:*", "Auctions");
        add(presets, "Out Auction", "out_auction:*", "Auctions");
        add(presets, "Out Auction House", "out_auction_house:*", "Auctions");
        add(presets, "AuctionHousePlus", "auctionhouseplus:*", "Auctions");
        add(presets, "AuctionHouse+", "auctionhouse_plus:*", "Auctions");
        add(presets, "AuctionHousePlugin", "auctionhouseplugin:*", "Auctions");
        add(presets, "Auction House Plugin", "auction_house_plugin:*", "Auctions");
        add(presets, "Auction-House", "auction-house:*", "Auctions");
        add(presets, "Auction House", "auction_house:*", "Auctions");
        add(presets, "AdvancedAuctionHouse", "advancedauctionhouse:*", "Auctions");
        add(presets, "Advanced Auction House", "advanced_auction_house:*", "Auctions");
        add(presets, "UltimateAuctionHouse", "ultimateauctionhouse:*", "Auctions");
        add(presets, "Ultimate Auction House", "ultimate_auction_house:*", "Auctions");
        add(presets, "TheUltimateAuctionHouse", "theultimateauctionhouse:*", "Auctions");
        add(presets, "The Ultimate Auction House", "the_ultimate_auction_house:*", "Auctions");
        add(presets, "FateAuctionHouse", "fateauctionhouse:*", "Auctions");
        add(presets, "FateAuction", "fateauction:*", "Auctions");
        add(presets, "FADAH", "fadah:*", "Auctions");
        add(presets, "FADAuctionHouse", "fadauctionhouse:*", "Auctions");
        add(presets, "FAD Auction House", "fad_auction_house:*", "Auctions");
        add(presets, "ZAuctionHouse Alt", "z_auctionhouse:*", "Auctions");
        add(presets, "Z Auction House", "z_auction_house:*", "Auctions");
        add(presets, "Z Auctions", "zauctions:*", "Auctions");
        add(presets, "zAuctionHousePro", "zauctionhousepro:*", "Auctions");
        add(presets, "zAuctionHousePlus", "zauctionhouseplus:*", "Auctions");
        add(presets, "AzAuctionHouse Plus", "azauctionhouseplus:*", "Auctions");
        add(presets, "AzAuctionHouse Pro", "azauctionhousepro:*", "Auctions");
        add(presets, "AxAuctionHouse", "axauctionhouse:*", "Auctions");
        add(presets, "AxAuction", "axauction:*", "Auctions");
        add(presets, "AxAuctionHouse Plus", "axauctionhouseplus:*", "Auctions");
        add(presets, "AxAuctionHouse Pro", "axauctionhousepro:*", "Auctions");
        add(presets, "NexusAuction", "nexusauction:*", "Auctions");
        add(presets, "NexusAuctions", "nexusauctions:*", "Auctions");
        add(presets, "NexusAH", "nexusah:*", "Auctions");
        add(presets, "Nexus Auction House", "nexus_auction_house:*", "Auctions");
        add(presets, "PlayerAuctionHouse", "playerauctionhouse:*", "Auctions");
        add(presets, "Player Auction House", "player_auction_house:*", "Auctions");
        add(presets, "PlayerAH", "playerah:*", "Auctions");
        add(presets, "Player AH", "player_ah:*", "Auctions");
        add(presets, "OlziePlayerAuctions", "olzieplayerauctions:*", "Auctions");
        add(presets, "Olzie Auctions", "olzieauctions:*", "Auctions");
        add(presets, "CrazyAuctionHouse", "crazyauctionhouse:*", "Auctions");
        add(presets, "Crazy Auction House", "crazy_auction_house:*", "Auctions");
        add(presets, "CrazyAuction", "crazyauction:*", "Auctions");
        add(presets, "Crazy Auction", "crazy_auction:*", "Auctions");
        add(presets, "ExcellentAuction", "excellentauction:*", "Auctions");
        add(presets, "ExcellentAuctionHouse Plus", "excellentauctionhouseplus:*", "Auctions");
        add(presets, "ExcellentAuctionHouse Pro", "excellentauctionhousepro:*", "Auctions");
        add(presets, "ExcellentAH", "excellentah:*", "Auctions");
        add(presets, "AuctionMasterPro", "auctionmasterpro:*", "Auctions");
        add(presets, "AuctionMasterPlus", "auctionmasterplus:*", "Auctions");
        add(presets, "AuctionMasters", "auctionmasters:*", "Auctions");
        add(presets, "AuctionGUIs", "auctionguis:*", "Auctions");
        add(presets, "AuctionGUIPro", "auctionguipro:*", "Auctions");
        add(presets, "AuctionGUIPlus Alt", "auctiongui_plus:*", "Auctions");
        add(presets, "AuctionMenu", "auctionmenu:*", "Auctions");
        add(presets, "AuctionMenus", "auctionmenus:*", "Auctions");
        add(presets, "AuctionMenuPlus", "auctionmenuplus:*", "Auctions");
        add(presets, "AuctionMenuPro", "auctionmenupro:*", "Auctions");
        add(presets, "AuctionSystem", "auctionsystem:*", "Auctions");
        add(presets, "AuctionsSystem", "auctionssystem:*", "Auctions");
        add(presets, "AuctionCore", "auctioncore:*", "Auctions");
        add(presets, "AuctionsCore", "auctionscore:*", "Auctions");
        add(presets, "AuctionAPI", "auctionapi:*", "Auctions");
        add(presets, "AuctionsAPI", "auctionsapi:*", "Auctions");
        add(presets, "AuctionEngine", "auctionengine:*", "Auctions");
        add(presets, "AuctionMarket", "auctionmarket:*", "Auctions");
        add(presets, "AuctionMarkets", "auctionmarkets:*", "Auctions");
        add(presets, "AuctionMarketplace", "auctionmarketplace:*", "Auctions");
        add(presets, "AuctionMarketPlace", "auctionmarket_place:*", "Auctions");
        add(presets, "AuctionListings", "auctionlistings:*", "Auctions");
        add(presets, "AuctionListing", "auctionlisting:*", "Auctions");
        add(presets, "ListingAuction", "listingauction:*", "Auctions");
        add(presets, "ListingsAuction", "listingsauction:*", "Auctions");
        add(presets, "ItemAuction", "itemauction:*", "Auctions");
        add(presets, "ItemAuctions", "itemauctions:*", "Auctions");
        add(presets, "ItemAuctionHouse", "itemauctionhouse:*", "Auctions");
        add(presets, "ItemAH", "itemah:*", "Auctions");
        add(presets, "SellAuction", "sellauction:*", "Auctions");
        add(presets, "SellAuctions", "sellauctions:*", "Auctions");
        add(presets, "BidAuction", "bidauction:*", "Auctions");
        add(presets, "BidAuctions", "bidauctions:*", "Auctions");
        add(presets, "Bidding", "bidding:*", "Auctions");
        add(presets, "Bids", "bids:*", "Auctions");
        add(presets, "Bid", "bid:*", "Auctions");
        add(presets, "MarketplaceAuctions", "marketplaceauctions:*", "Auctions");
        add(presets, "MarketplaceAuction", "marketplaceauction:*", "Auctions");
        add(presets, "MarketAuctionHouse", "marketauctionhouse:*", "Auctions");
        add(presets, "MarketAH", "marketah:*", "Auctions");
        add(presets, "BazaarAuction", "bazaarauction:*", "Auctions");
        add(presets, "BazaarAuctions", "bazaarauctions:*", "Auctions");
        add(presets, "BazaarAH", "bazaarah:*", "Auctions");
        add(presets, "TradeAuction", "tradeauction:*", "Auctions");
        add(presets, "TradeAuctions", "tradeauctions:*", "Auctions");
        add(presets, "TradeAH", "tradeah:*", "Auctions");
        add(presets, "GlobalAuction", "globalauction:*", "Auctions");
        add(presets, "GlobalAuctions", "globalauctions:*", "Auctions");
        add(presets, "GlobalAuctionHouse", "globalauctionhouse:*", "Auctions");
        add(presets, "GlobalMarket", "globalmarket:*", "Auctions");
        add(presets, "GlobalMarketPlus", "globalmarketplus:*", "Auctions");
        add(presets, "GlobalMarketChestShop", "globalmarketchestshop:*", "Auctions");
        add(presets, "ServerAuction", "serverauction:*", "Auctions");
        add(presets, "ServerAuctions", "serverauctions:*", "Auctions");
        add(presets, "ServerAuctionHouse", "serverauctionhouse:*", "Auctions");
        add(presets, "CommunityAuctionHouse", "communityauctionhouse:*", "Auctions");
        add(presets, "CommunityAH", "communityah:*", "Auctions");
        add(presets, "LiteAuction", "liteauction:*", "Auctions");
        add(presets, "LiteAuctions", "liteauctions:*", "Auctions");
        add(presets, "SimpleAuction", "simpleauction:*", "Auctions");
        add(presets, "SimpleAuctions", "simpleauctions:*", "Auctions");
        add(presets, "SimpleAuctionHouse", "simpleauctionhouse:*", "Auctions");
        add(presets, "BetterAuctionHouse", "betterauctionhouse:*", "Auctions");
        add(presets, "BetterAuctions", "betterauctions:*", "Auctions");
        add(presets, "BetterAH", "betterah:*", "Auctions");
        add(presets, "EasyAuction", "easyauction:*", "Auctions");
        add(presets, "EasyAuctions", "easyauctions:*", "Auctions");
        add(presets, "EasyAuctionHouse", "easyauctionhouse:*", "Auctions");
        add(presets, "DeluxeAuction", "deluxeauction:*", "Auctions");
        add(presets, "DeluxeAuctions", "deluxeauctions:*", "Auctions");
        add(presets, "DeluxeAuctionHouse", "deluxeauctionhouse:*", "Auctions");
        add(presets, "PremiumAuction", "premiumauction:*", "Auctions");
        add(presets, "PremiumAuctions", "premiumauctions:*", "Auctions");
        add(presets, "PremiumAuctionHouse", "premiumauctionhouse:*", "Auctions");
        add(presets, "UltraAuction", "ultraauction:*", "Auctions");
        add(presets, "UltraAuctions", "ultraauctions:*", "Auctions");
        add(presets, "UltraAuctionHouse", "ultraauctionhouse:*", "Auctions");
        add(presets, "RoyalAuction", "royalauction:*", "Auctions");
        add(presets, "RoyalAuctions", "royalauctions:*", "Auctions");
        add(presets, "RoyalAuctionHouse", "royalauctionhouse:*", "Auctions");
        add(presets, "SupremeAuction", "supremeauction:*", "Auctions");
        add(presets, "SupremeAuctions", "supremeauctions:*", "Auctions");
        add(presets, "SupremeAuctionHouse", "supremeauctionhouse:*", "Auctions");
        add(presets, "HuskAuction", "huskauction:*", "Auctions");
        add(presets, "HuskAuctionHouse", "huskauctionhouse:*", "Auctions");
        add(presets, "HuskAH", "huskah:*", "Auctions");
        return Collections.unmodifiableList(presets);
    }

    private static void add(List<Preset> presets, String label, String pattern, String group) {
        String normalized = normalizePattern(pattern);
        String key = ruleKey(normalized, Direction.ANY.name());
        for (Preset preset : presets) {
            if (preset.key().equals(key)) return;
        }
        presets.add(new Preset(label, normalized, Direction.ANY, group));
    }
}
