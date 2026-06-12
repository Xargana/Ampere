package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.direct.DirectUiButton;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectUiLabel;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.direct.DirectSurface;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.direct.DirectProgressBar;
import autismclient.gui.vanillaui.direct.DirectInfoRow;
import autismclient.gui.vanillaui.direct.DirectRow;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.gui.vanillaui.direct.DirectSlider;
import autismclient.gui.vanillaui.direct.DirectSpacer;
import autismclient.gui.vanillaui.direct.DirectTabStrip;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.gui.vanillaui.direct.DirectViewportSlot;
import autismclient.gui.vanillaui.direct.DirectWindow;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.OverlayTopBar;
import autismclient.gui.macro.editor.ActionEditorOverlay;
import autismclient.modules.PackHideState;
import autismclient.util.macro.PayloadAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class AutismServerInfoOverlay extends AutismOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final String OVERLAY_ID = "autism-serverinfo";
    private static final int COMPLETION_ID = 1337;
    private static final int HEADER_CONTROL = 12;
    private static final int HEADER_ARROW_WIDTH = 10;
    private static final int HEADER_ARROW_GAP = 3;
    private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0f;

    private int panelX = 350;
    private int panelY = 30;
    private int panelW = 236;
    private int panelH = 246;
    private static final int ROW_H = 16;
    private static final int PAD = 8;

    private boolean visible = false;
    private boolean collapsed = false;
    private boolean isDragging = false;
    private double dragOffsetX, dragOffsetY;
    private float pressStartUiX, pressStartUiY;
    private int pressStartPanelX, pressStartPanelY;
    private boolean dragMoved = false;
    private final Font textRenderer;
    private final CompactTheme theme = new CompactTheme();
    private final DirectWindow windowNode = new DirectWindow("Server Info");
    private final DirectSurface surface = new DirectSurface(theme, windowNode);
    private final DirectTabStrip tabStrip = new DirectTabStrip();
    private final CompactTextInput searchField = new CompactTextInput();
    private final DirectSlider probeDelaySlider = new DirectSlider();
    private final DirectProgressBar scanProgressBar = new DirectProgressBar();
    private final DirectViewportSlot pluginListSlot = new DirectViewportSlot();
    private boolean pluginScrollbarDragging = false;
    private int pluginScrollbarGrabOffset = 0;
    private long lastUiRebuildMs = 0L;

    private static final String[] TAB_NAMES = {"Info", "Plugins"};
    private int activeTab = 0;

    private final List<ClickRegion> clickRegions = new ArrayList<>();

    private volatile String resolvedIp = null;
    private String lastResolvedAddress = null;
    private volatile boolean resolvingIp = false;

    private final List<String> detectedPlugins = new ArrayList<>();
    private final Map<String, List<String>> pluginCommands = new LinkedHashMap<>();
    private final Map<String, List<String>> pluginChannels = new LinkedHashMap<>();
    private final Map<String, List<String>> pluginGuis = new LinkedHashMap<>();
    private List<PluginListRow> cachedPluginRows = List.of();
    private String cachedPluginRowsQuery = null;
    private String cachedPluginRowsSelectedKey = null;
    private int cachedPluginRowsWidth = -1;
    private int[] cachedPluginRowOffsets = new int[0];
    private int cachedPluginRowsHeight = 0;
    private int pluginDataRevision = 0;
    private int pluginRowsRevision = 0;
    private int cachedPluginRowsRevision = -1;
    private Map<String, PluginDetail> cachedPluginDetails = Map.of();
    private int cachedPluginDetailsRevision = -1;
    private final ScrollState pluginScrollState = new ScrollState();
    private int pluginContentHeight = 0;
    private String selectedPlugin = null;
    private int pluginProbeDelayMs = 50;
    private boolean pluginScanDone = false;
    private boolean pluginScanInProgress = false;
    private long pluginScanStartedAt = 0L;
    private long pluginScanLastResponseAt = 0L;
    private final Set<Integer> pendingPluginProbeIds = new HashSet<>();
    private final Map<Integer, PluginProbeSpec> pluginProbes = new HashMap<>();
    private final Set<String> observedPluginCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Deque<PluginProbeRequest> queuedPluginProbes = new ArrayDeque<>();
    private final Map<String, PluginEvidence> pluginEvidence = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, PluginConfidence> pluginConfidence = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, PluginResultKind> pluginResultKinds = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, PluginScanEntry> scanWorkingEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<Integer, PluginProbeSpec> observedSuggestionRequests = new LinkedHashMap<>();
    private final Deque<ObservedCommand> recentCommands = new ArrayDeque<>();
    private long nextPluginProbeSendAt = 0L;
    private int pluginScanTotalSteps = 0;
    private boolean pluginScanCompletionAnnounced = false;
    private boolean uiRebuildRequested = true;
    private int activeProbeDelayMs = DEFAULT_PLUGIN_PROBE_DELAY_MS;
    private String lastAutoScanContext = null;

    private String scannedServerAddress = null;
    private String scannedPluginContextSignature = "";
    private String cachedPluginContextServerAddress = "";
    private String cachedPluginContextBrand = "";
    private String cachedPluginContextSignature = "";
    private boolean pluginContextSignatureDirty = true;
    private long lastPayloadFingerprintRevision = -1L;
    private static final long PLUGIN_SCAN_IDLE_MS = 700L;
    private static final long PLUGIN_SCAN_TIMEOUT_MS = 12000L;
    private static final int DEFAULT_PLUGIN_PROBE_DELAY_MS = 50;
    private static final int MIN_PLUGIN_PROBE_DELAY_MS = 10;
    private static final int MAX_PLUGIN_PROBE_DELAY_MS = 500;

    private static final long PLUGIN_CACHE_MAX_AGE_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final long PLUGIN_SCAN_SETTLE_MS = 450L;
    private static final long COMMAND_GUI_LINK_MS = 5000L;
    private static final int MAX_TRACKED_SUGGESTION_REQUESTS = 256;
    private static final int MAX_RECENT_COMMANDS = 24;
    private static final int PLUGIN_HEADER_H = 14;
    private static final int SHARED_PANEL_WIDTH = 200;
    private static final int PLUGIN_SETUP_WIDTH = SHARED_PANEL_WIDTH;
    private static final int PLUGIN_SETUP_HEIGHT = 126;
    private static final int PLUGIN_SCANNING_WIDTH = SHARED_PANEL_WIDTH;
    private static final int PLUGIN_SCANNING_HEIGHT = 92;
    private static final int INFO_MIN_WIDTH = SHARED_PANEL_WIDTH;
    private static final int INFO_MIN_HEIGHT = 258;
    private static final int PLUGIN_RESULTS_MIN_WIDTH = SHARED_PANEL_WIDTH;
    private static final int PLUGIN_RESULTS_MIN_HEIGHT = 280;
    private int infoPreferredWidth = INFO_MIN_WIDTH;
    private int infoPreferredHeight = INFO_MIN_HEIGHT;
    private int pluginPreferredWidth = PLUGIN_RESULTS_MIN_WIDTH;
    private int pluginPreferredHeight = PLUGIN_RESULTS_MIN_HEIGHT;
    private static final String[] COMMON_PLUGIN_NAMESPACES = {
        "essentials", "essentialsx", "worldedit", "worldguard", "luckperms", "vault",
        "citizens", "cmi", "cmilib", "multiverse-core", "multiverse", "viaversion",
        "viabackwards", "viarewind", "geysermc", "geyser", "floodgate", "protocollib",
        "coreprotect", "griefprevention", "shopkeepers", "dynmap", "placeholderapi",
        "skinsrestorer", "skript", "advancedanticheat", "vulcan", "grimac", "matrix",
        "spartan", "aac", "karhu", "verus", "nocheatplus", "authme", "deluxemenus",
        "plotsquared", "supervanish", "packetevents", "oraxen", "itemsadder",
        "fawe", "fastasyncworldedit", "luckpermsbukkit", "essentialsgeoip", "essentialsprotect",
        "essentialsspawn", "essentialsxspawn", "multiverse-inventories", "multiverse-netherportals",
        "worldborder", "votifier", "nuVotifier", "votingplugin", "excellentcrates", "crazycrates",
        "cratekeys", "jobs", "jobsreborn", "mcmmo", "towny", "factions", "factionsuuid",
        "lands", "residence", "claimchunk", "quickshop", "quickshop-hikari", "chestshop",
        "shopgui", "auctionhouse", "combatlogx", "litebans", "advancedban", "libertybans",
        "luckpermsgui", "tab", "tablist", "scoreboard", "animatedscoreboard", "ajleaderboards",
        "ajqueue", "spark", "sparkbukkit", "plan", "minimotd", "protocolsupport", "viaversion",
        "excellentenchants", "eco", "ecoenchants", "mythicmobs", "mythiclib", "modelengine",
        "mmoitems", "mmocore", "denizen", "citizenscmd", "sentinel", "npcs", "proantitab",
        "pat", "papiproxybridge", "groupmanager", "vulcanbungee",
        "grimacbukkit", "negativity", "intave", "polar", "horizon", "themis", "libreforge"
    };
    private static final String[] PLUGIN_LIST_PROBE_COMMANDS = {
        "/plugins ", "/pl ", "/bukkit:plugins ", "/bukkit:pl "
    };
    private static final String[] VERSION_PROBE_COMMANDS = {
        "/ver ", "/version ", "/about ", "/icanhasbukkit ", "/bukkit:ver ", "/bukkit:version "
    };
    private static final String[] HELP_PROBE_COMMANDS = {
        "/help ", "/? ", "/bukkit:help ", "/minecraft:help "
    };
    private static final String[] HIGH_VALUE_PLUGIN_HINTS = {
        "essentials", "essentialsx", "worldedit", "worldguard", "luckperms", "vault",
        "cmi", "citizens", "multiverse", "viaversion", "geyser", "floodgate",
        "protocollib", "coreprotect", "placeholderapi", "skinsrestorer", "skript",
        "vulcan", "grimac", "matrix", "spartan", "authme", "deluxemenus",
        "plotsquared", "tab", "spark", "proantitab"
    };
    private static final String ROOT_PROBE_PREFIXES = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Map<String, String> ROOT_COMMAND_PLUGIN_ALIASES = Map.ofEntries(
        Map.entry("lp", "luckperms"),
        Map.entry("we", "worldedit"),
        Map.entry("rg", "worldguard"),
        Map.entry("mv", "multiverse-core"),
        Map.entry("npc", "citizens"),
        Map.entry("papi", "placeholderapi"),
        Map.entry("cmi", "cmi"),
        Map.entry("co", "coreprotect"),
        Map.entry("grim", "grimac"),
        Map.entry("geyser", "geysermc"),
        Map.entry("floodgate", "floodgate"),
        Map.entry("viaver", "viaversion"),
        Map.entry("sr", "skinsrestorer"),
        Map.entry("authme", "authme"),
        Map.entry("dm", "deluxemenus"),
        Map.entry("plots", "plotsquared"),
        Map.entry("sv", "supervanish"),
        Map.entry("spawn", "essentialsx"),
        Map.entry("home", "essentialsx"),
        Map.entry("homes", "essentialsx"),
        Map.entry("warp", "essentialsx"),
        Map.entry("warps", "essentialsx"),
        Map.entry("tpa", "essentialsx"),
        Map.entry("tpahere", "essentialsx"),
        Map.entry("bal", "essentialsx"),
        Map.entry("balance", "essentialsx"),
        Map.entry("money", "essentialsx"),
        Map.entry("eco", "essentialsx"),
        Map.entry("ban", "essentialsx"),
        Map.entry("kick", "essentialsx"),
        Map.entry("mute", "essentialsx"),
        Map.entry("jail", "essentialsx"),
        Map.entry("seen", "essentialsx"),
        Map.entry("ptime", "essentialsx"),
        Map.entry("pweather", "essentialsx"),
        Map.entry("tppos", "essentialsx"),
        Map.entry("near", "essentialsx"),
        Map.entry("back", "essentialsx"),
        Map.entry("afk", "essentialsx"),
        Map.entry("msg", "essentialsx"),
        Map.entry("r", "essentialsx"),
        Map.entry("reply", "essentialsx"),
        Map.entry("mail", "essentialsx"),
        Map.entry("pay", "essentialsx"),
        Map.entry("sell", "essentialsx"),
        Map.entry("worth", "essentialsx"),
        Map.entry("kit", "essentialsx"),
        Map.entry("kits", "essentialsx"),
        Map.entry("lb", "litebans"),
        Map.entry("litebans", "litebans"),
        Map.entry("ab", "advancedban"),
        Map.entry("advancedban", "advancedban"),
        Map.entry("tab", "tab"),
        Map.entry("pat", "proantitab"),
        Map.entry("proantitab", "proantitab"),
        Map.entry("spark", "spark"),
        Map.entry("plan", "plan"),
        Map.entry("votingplugin", "votingplugin"),
        Map.entry("vote", "votingplugin"),
        Map.entry("votes", "votingplugin"),
        Map.entry("jobs", "jobsreborn"),
        Map.entry("mcmmo", "mcmmo"),
        Map.entry("towny", "towny"),
        Map.entry("f", "factions"),
        Map.entry("factions", "factions"),
        Map.entry("lands", "lands"),
        Map.entry("res", "residence"),
        Map.entry("residence", "residence"),
        Map.entry("qs", "quickshop"),
        Map.entry("quickshop", "quickshop"),
        Map.entry("auctionhouse", "auctionhouse"),
        Map.entry("mythicmobs", "mythicmobs"),
        Map.entry("mm", "mythicmobs"),
        Map.entry("mmoitems", "mmoitems"),
        Map.entry("denizen", "denizen"),
        Map.entry("sentinel", "sentinel"),
        Map.entry("vulcan", "vulcan"),
        Map.entry("matrix", "matrix"),
        Map.entry("karhu", "karhu"),
        Map.entry("verus", "verus"),
        Map.entry("ncp", "nocheatplus")
    );

    private static final Map<String, String> COMMAND_FEATURE_LABELS = Map.ofEntries(
        Map.entry("shop", "Shop"),
        Map.entry("shops", "Shop"),
        Map.entry("store", "Shop"),
        Map.entry("stores", "Shop"),
        Map.entry("market", "Market"),
        Map.entry("marketplace", "Market"),
        Map.entry("ah", "Auction"),
        Map.entry("auction", "Auction"),
        Map.entry("auctions", "Auction"),
        Map.entry("crate", "Crates"),
        Map.entry("crates", "Crates"),
        Map.entry("key", "Crates"),
        Map.entry("keys", "Crates"),
        Map.entry("casino", "Gambling"),
        Map.entry("slots", "Gambling"),
        Map.entry("roulette", "Gambling"),
        Map.entry("backpack", "Backpacks"),
        Map.entry("backpacks", "Backpacks"),
        Map.entry("bp", "Backpacks"),
        Map.entry("pv", "Player Vaults"),
        Map.entry("playervault", "Player Vaults"),
        Map.entry("playervaults", "Player Vaults"),
        Map.entry("vaults", "Player Vaults"),
        Map.entry("enderchest", "Ender Chest"),
        Map.entry("echest", "Ender Chest"),
        Map.entry("ec", "Ender Chest"),
        Map.entry("shulker", "Shulker"),
        Map.entry("shulkers", "Shulker"),
        Map.entry("minion", "Minions"),
        Map.entry("minions", "Minions"),
        Map.entry("mine", "Mines"),
        Map.entry("mines", "Mines"),
        Map.entry("kit", "Kits"),
        Map.entry("kits", "Kits")
    );

    private static final Set<String> ANTICHEATS = Set.of(
        "nocheatplus", "aac", "spartan", "matrix", "vulcan", "grim",
        "grimac", "intave", "karhu", "verus", "polar", "negativity",
        "themis", "fairfight", "wraith", "horizon", "reflex", "antiaura",
        "guardian", "hac", "thotpatrol", "alice"
    );

    private static final Set<String> VANILLA_NAMESPACES = Set.of(
        "minecraft", "brigadier", "bukkit", "spigot", "paper", "purpur",
        "velocity", "bungeecord", "waterfall"
    );

    private static final Set<String> VANILLA_COMMAND_ROOTS = Set.of(
        "advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear",
        "clone", "damage", "data", "datapack", "debug", "defaultgamemode",
        "deop", "difficulty", "effect", "enchant", "execute", "experience", "xp",
        "fill", "fillbiome", "forceload", "function", "gamemode", "gamerule",
        "give", "help", "item", "jfr", "kick", "kill", "list", "locate", "loot",
        "me", "msg", "op", "pardon", "pardon-ip", "particle", "perf", "place",
        "playsound", "publish", "random", "recipe", "reload", "return", "ride",
        "rotate", "save-all", "save-off", "save-on", "say", "schedule", "scoreboard",
        "seed", "setblock", "setidletimeout", "setworldspawn", "spawnpoint", "spectate",
        "spreadplayers", "stop", "stopsound", "summon", "tag", "team", "teammsg",
        "tm", "teleport", "tell", "tellraw", "tick", "time", "title", "tp",
        "transfer", "trigger", "w", "weather", "whitelist", "worldborder"
    );

    private static final class PluginScanEntry {
        String displayName;
        boolean commandBacked;
        PluginResultKind resultKind;
        PluginEvidence evidence = PluginEvidence.UNKNOWN;
        PluginConfidence confidence = PluginConfidence.FEATURE;
        final Set<String> commands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Set<String> channels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Set<String> guis = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    private enum PluginResultKind {
        PLUGIN,
        FEATURE
    }

    private enum PluginConfidence {
        EXACT,
        STRONG,
        FEATURE,
        UNKNOWN
    }

    private enum PluginEvidence {
        PAYLOAD_CHANNEL,
        COMMAND_GUI,
        COMMAND_TREE,
        USER_AUTOCOMPLETE,
        SCANNER_AUTOCOMPLETE,
        NAMESPACE,
        ROOT_HINT,
        HELP_HINT,
        PLUGIN_LIST,
        VERSION_HINT,
        FEATURE,
        UNKNOWN
    }

    private enum PluginProbeKind {
        ROOT,
        HELP,
        PLUGIN_LIST,
        VERSION,
        NAMESPACE,
        USER
    }

    private static final class PluginProbeSpec {
        final String query;
        final PluginProbeKind kind;
        final String hint;

        PluginProbeSpec(String query, PluginProbeKind kind, String hint) {
            this.query = query;
            this.kind = kind;
            this.hint = hint;
        }
    }

    private static final class PluginProbeRequest {
        final int id;
        final PluginProbeSpec spec;

        PluginProbeRequest(int id, PluginProbeSpec spec) {
            this.id = id;
            this.spec = spec;
        }
    }

    private record ObservedCommand(String root, String fullCommand, long timestampMs) {
    }

    private enum PluginRowType {
        HEADER,
        PLUGIN,
        COMMAND,
        GUI,
        CHANNEL,
        WHY
    }

    private static final class PluginListRow {
        final PluginRowType type;
        final String title;
        final String plugin;
        final String label;
        final String actionCommand;
        final PluginResultKind resultKind;
        final PluginEvidence evidence;
        final PluginConfidence confidence;
        final int height;
        final String countLabel;
        final String searchText;

        private PluginListRow(PluginRowType type, String title, String plugin, String label, String actionCommand,
                              PluginResultKind resultKind, PluginEvidence evidence, PluginConfidence confidence,
                              int height, String countLabel, String searchText) {
            this.type = type;
            this.title = title;
            this.plugin = plugin;
            this.label = label;
            this.actionCommand = actionCommand;
            this.resultKind = resultKind == null ? PluginResultKind.PLUGIN : resultKind;
            this.evidence = evidence == null ? PluginEvidence.UNKNOWN : evidence;
            this.confidence = confidence == null ? PluginConfidence.UNKNOWN : confidence;
            this.height = Math.max(1, height);
            this.countLabel = countLabel == null ? "" : countLabel;
            this.searchText = searchText == null ? "" : searchText;
        }

        static PluginListRow header(String title, int height) {
            return new PluginListRow(PluginRowType.HEADER, title, null, title, null,
                PluginResultKind.PLUGIN, PluginEvidence.UNKNOWN, PluginConfidence.UNKNOWN, height, "", lower(title));
        }

        static PluginListRow plugin(String plugin, PluginResultKind resultKind, PluginEvidence evidence,
                                    PluginConfidence confidence, int height, String countLabel, String searchText) {
            return new PluginListRow(PluginRowType.PLUGIN, null, plugin, plugin, null,
                resultKind, evidence, confidence, height, countLabel, searchText);
        }

        static PluginListRow detail(PluginRowType type, String plugin, String label, String actionCommand,
                                    PluginEvidence evidence, PluginConfidence confidence, int height, String searchText) {
            return new PluginListRow(type, null, plugin, label, actionCommand,
                PluginResultKind.PLUGIN, evidence, confidence, height, "", searchText);
        }

        boolean header() {
            return type == PluginRowType.HEADER;
        }

        private static String lower(String text) {
            return text == null ? "" : text.toLowerCase(Locale.ROOT);
        }
    }

    private static final class PluginDetail {
        final String displayName;
        final String key;
        final List<String> commands;
        final List<String> guis;
        final List<String> channels;
        final PluginResultKind resultKind;
        final PluginEvidence evidence;
        final PluginConfidence confidence;
        final String sourceLabel;
        final String countLabel;
        final String searchText;

        PluginDetail(String displayName, String key, List<String> commands, List<String> guis, List<String> channels,
                     PluginResultKind resultKind, PluginEvidence evidence, PluginConfidence confidence,
                     String sourceLabel, String countLabel, String searchText) {
            this.displayName = displayName == null ? "" : displayName;
            this.key = key == null ? "" : key;
            this.commands = commands == null ? List.of() : commands;
            this.guis = guis == null ? List.of() : guis;
            this.channels = channels == null ? List.of() : channels;
            this.resultKind = resultKind == null ? PluginResultKind.PLUGIN : resultKind;
            this.evidence = evidence == null ? PluginEvidence.UNKNOWN : evidence;
            this.confidence = confidence == null ? PluginConfidence.UNKNOWN : confidence;
            this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
            this.countLabel = countLabel == null ? "" : countLabel;
            this.searchText = searchText == null ? "" : searchText;
        }
    }

    private static final class PluginDetailBuilder {
        String displayName;
        final Set<String> commands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Set<String> guis = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final List<String> channels = new ArrayList<>();
    }

    private static class ClickRegion {
        final int x, y, width, height;
        final Runnable action;
        ClickRegion(int x, int y, int width, int height, Runnable action) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.action = action;
        }
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    public AutismServerInfoOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        buildUi();
    }

    private void buildUi() {
        windowNode.setCenterTitle(false);
        windowNode.setTitleTone(UiTone.LABEL);
        windowNode.setHeaderControls(true, true);
        windowNode.setTitleAreaInsets(panelPadding() + 1, panelPadding() + headerControlSize() + headerArrowWidth() + headerArrowGap() + 12);
        windowNode.content().setGap(contentGap()).setPadding(DirectUiInsets.all(panelPadding()));

        tabStrip.setTabs(TAB_NAMES).setActiveIndex(activeTab).setOnSelect(this::selectTab);
        searchField
            .setPlaceholder("Search plugins...")
            .setPreferredWidth(pluginSearchWidth())
            .setFieldHeight(searchFieldHeight())
            .setOnChange(text -> {
                pluginScrollState.jumpTo(0, 0);
                requestUiRebuild();
            });
        probeDelaySlider
            .setRange(MIN_PLUGIN_PROBE_DELAY_MS, MAX_PLUGIN_PROBE_DELAY_MS)
            .setStep(10.0f)
            .setValue(pluginProbeDelayMs)
            .setOnChange(value -> setPluginProbeDelayMs(Math.round(value)));
        scanProgressBar.setProgress(0.0f);
    }

    public void saveState() {
        AutismSharedState shared = AutismSharedState.get();
        rememberCurrentTabSize();
        String stateAddress = currentServerAddress();
        if (stateAddress.isEmpty() && scannedServerAddress != null) stateAddress = scannedServerAddress;
        shared.setServerDataOverlayActiveTab(activeTab);
        shared.setServerDataOverlayPluginScrollOffset(pluginScrollState.targetOffset());
        shared.setServerDataOverlaySelectedPlugin(selectedPlugin);
        shared.setServerDataOverlayStateAddress(stateAddress);
        shared.setServerDataOverlayProbeDelayMs(pluginProbeDelayMs);
        shared.setServerDataOverlayInfoWidth(infoPreferredWidth);
        shared.setServerDataOverlayInfoHeight(infoPreferredHeight);
        shared.setServerDataOverlayPluginWidth(pluginPreferredWidth);
        shared.setServerDataOverlayPluginHeight(pluginPreferredHeight);
        cacheCurrentScan();
        saveLayout();
    }

    public void restoreState() {
        AutismSharedState shared = AutismSharedState.get();
        restoreLayout();
        activeTab = Math.max(0, Math.min(TAB_NAMES.length - 1, shared.getServerDataOverlayActiveTab()));
        pluginProbeDelayMs = Math.max(MIN_PLUGIN_PROBE_DELAY_MS, Math.min(MAX_PLUGIN_PROBE_DELAY_MS, shared.getServerDataOverlayProbeDelayMs()));
        infoPreferredWidth = Math.max(infoMinWidth(), shared.getServerDataOverlayInfoWidth());
        infoPreferredHeight = Math.max(infoMinHeight(), shared.getServerDataOverlayInfoHeight());
        pluginPreferredWidth = Math.max(pluginResultsMinWidth(), shared.getServerDataOverlayPluginWidth());
        pluginPreferredHeight = Math.max(pluginResultsMinHeightPreset(), shared.getServerDataOverlayPluginHeight());
        syncScanStateForCurrentServer();
        String stateAddress = currentServerAddress();
        if (stateAddress.isEmpty() && scannedServerAddress != null) stateAddress = scannedServerAddress;
        if (!stateAddress.isEmpty() && stateAddress.equals(shared.getServerDataOverlayStateAddress())) {
            pluginScrollState.restore(shared.getServerDataOverlayPluginScrollOffset());
            selectedPlugin = shared.getServerDataOverlaySelectedPlugin();
            if (selectedPlugin != null && selectedPlugin.isBlank()) selectedPlugin = null;
            if (selectedPlugin != null && !hasDetectedPlugin(selectedPlugin)) selectedPlugin = null;
        } else {
            pluginScrollState.jumpTo(0, 0);
            selectedPlugin = null;
        }
        if (activeTab == 0) {
            applyInfoLayout();
        } else {
            if (pluginScanInProgress) applyPluginScanningLayout();
            else if (!pluginScanDone) applyPluginSetupLayout();
        }
        tabStrip.setActiveIndex(activeTab);
        probeDelaySlider.setValue(pluginProbeDelayMs);
    }

    public static boolean shouldRestoreSavedVisible() {
        AutismWindowLayout layout = AutismSharedState.get().getWindowLayout(OVERLAY_ID);
        return layout != null && layout.visible;
    }

    private void selectTab(int tabIdx) {
        rememberCurrentTabSize();
        activeTab = Math.max(0, Math.min(TAB_NAMES.length - 1, tabIdx));
        tabStrip.setActiveIndex(activeTab);
        pluginScrollState.jumpTo(0, 0);
        selectedPlugin = null;
        if (activeTab == 0) {
            applyInfoLayout();
        } else if (pluginScanInProgress) {
            applyPluginScanningLayout();
        } else if (!pluginScanDone) {
            applyPluginSetupLayout();
        } else {
            applyPluginResultsLayout();
        }
        saveState();
    }

    private void rebuildUi() {
        uiRebuildRequested = false;
        windowNode.content().clearChildren();
        tabStrip.setActiveIndex(activeTab);
        probeDelaySlider.setValue(pluginProbeDelayMs);
        scanProgressBar.setProgress(getPluginScanProgress());

        if (!pluginScanInProgress) {
            windowNode.content().add(tabStrip);
        }

        if (activeTab == 0) {
            buildInfoUi();
            return;
        }

        if (!pluginScanDone && !pluginScanInProgress) {
            buildPluginSetupUi();
            return;
        }

        if (pluginScanInProgress) {
            buildPluginScanningUi();
            return;
        }

        buildPluginResultsUi();
    }

    private void requestUiRebuild() {
        uiRebuildRequested = true;
    }

    private void buildInfoUi() {
        ServerData entry = MC.getCurrentServer();
        String displayedAddress = getDisplayedServerAddress();
        String realIp = getDisplayedRealIp(displayedAddress);
        String software = getSoftwareGuess(entry);
        String versionNote = getVersionNote(entry);
        String ping = entry != null ? entry.ping + " ms" : "--";
        String players = "--";
        if (MC.getConnection() != null) {
            int online = MC.getConnection().getListedOnlinePlayers().size();
            players = entry != null && entry.players != null ? online + " / " + entry.players.max() : String.valueOf(online);
        }
        String proto = entry != null ? String.valueOf(entry.protocol) : "--";
        String diff = MC.level != null ? MC.level.getDifficulty().getDisplayName().getString() : "--";
        String time = "--";
        if (MC.level != null) {
            long dayCount = MC.level.getOverworldClockTime() / 24000L;
            long timeOfDay = MC.level.getOverworldClockTime() % 24000L;
            int hours = (int) ((timeOfDay / 1000 + 6) % 24);
            int minutes = (int) ((timeOfDay % 1000) * 60 / 1000);
            time = "Day " + dayCount + " (" + String.format("%02d:%02d", hours, minutes) + ")";
        }
        double estimatedTps = AutismSharedState.get().getEstimatedTps();
        String tps = estimatedTps > 0 ? String.format("%.1f", Math.min(20.0, estimatedTps)) : "--";

        addInfoRow("IP:", displayedAddress, null, () -> copyClipboardValue(displayedAddress, "Server address copied.", "Server address unavailable."));
        String host = extractLookupHost(displayedAddress);
        if (!host.isBlank() && (!host.equals(realIp) || resolvingIp)) {
            addInfoRow("Real IP:", realIp, null, this::copyResolvedServerIp);
        }
        addInfoRow("Version:", getReportedVersion(entry), null);
        addInfoRow("Real Version:", getRealServerVersion(), null);
        addInfoRow("Brand:", getLiveBrand(), null);
        if (!"--".equals(software)) addInfoRow("Software:", software, null);
        addInfoRow("Ping:", ping, null);
        addInfoRow("Players:", players, null);
        addInfoRow("Protocol:", proto, null);
        if (!"--".equals(versionNote)) addInfoRow("Version Note:", versionNote, AutismColors.packetYellow());
        addInfoRow("Difficulty:", diff, null);
        addInfoRow("World:", getCurrentWorldName(), null);
        addInfoRow("Time:", time, null);
        addInfoRow("TPS:", tps, null);

        List<String> detectedAcs = getDetectedAnticheats();
        if (pluginScanInProgress) addInfoRow("AntiCheats:", "Scanning...", 0xFFFF4444);
        else if (!pluginScanDone) addInfoRow("AntiCheats:", "Probe Plugins First", 0xFFFF4444);
        else if (detectedAcs.isEmpty()) addInfoRow("AntiCheats:", "None detected", null);
        else addInfoRow("AntiCheats:", String.join(", ", detectedAcs), 0xFFFF4444);

        windowNode.content().add(new DirectSpacer(0, 2));
        windowNode.content().add(new DirectUiButton("Copy Report", DirectUiButton.Variant.SECONDARY, this::copyServerData).setGrowX(true).setButtonHeight(actionButtonHeight()));
    }

    private void addInfoRow(String label, String value, Integer valueColor) {
        addInfoRow(label, value, valueColor, null);
    }

    private void addInfoRow(String label, String value, Integer valueColor, Runnable onPress) {
        windowNode.content().add(
            new DirectInfoRow(label, value)
                .setLabelWidth(infoLabelWidth())
                .setValueColorOverride(valueColor)
                .setOnPress(onPress)
        );
    }

    private void buildPluginSetupUi() {
        boolean autoOn = AutismConfig.getGlobal().autoProbePlugins;
        windowNode.content().add(new DirectUiButton(
            "Auto Probe: " + (autoOn ? "ON" : "OFF"),
            autoOn ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.SECONDARY,
            () -> {
                AutismConfig cfg = AutismConfig.getGlobal();
                cfg.autoProbePlugins = !cfg.autoProbePlugins;
                cfg.save();
                applyPluginSetupLayout();
            }).setGrowX(true).setButtonHeight(actionButtonHeight()));
        windowNode.content().add(new DirectUiLabel("Auto-Scans on join.", UiTone.MUTED));
        windowNode.content().add(new DirectSpacer(0, 2));
        windowNode.content().add(new DirectUiLabel("Start probing manually", UiTone.MUTED));
        windowNode.content().add(new DirectInfoRow("Delay:", pluginProbeDelayMs + " ms").setLabelWidth(pluginSetupLabelWidth()));
        windowNode.content().add(probeDelaySlider);
        windowNode.content().add(new DirectUiLabel("Default: " + DEFAULT_PLUGIN_PROBE_DELAY_MS + " ms", UiTone.MUTED));
        windowNode.content().add(new DirectUiLabel("If you get kicked increase the delay.", UiTone.MUTED));
        windowNode.content().add(new DirectUiButton("Start Probing", DirectUiButton.Variant.SECONDARY, () -> {
            AutismConfig.getGlobal().removePluginScan(currentServerAddress(), currentPluginContextSignature());
            resetScan();
            scanPlugins();
        }).setGrowX(true).setButtonHeight(actionButtonHeight()));
    }

    private void buildPluginScanningUi() {
        int progressPercent = Math.max(0, Math.min(100, Math.round(getPluginScanProgress() * 100.0f)));
        windowNode.content().add(new DirectInfoRow("State:", getPluginScanPhaseLabel()).setLabelWidth(pluginScanLabelWidth()).setValueColorOverride(getPluginScanPhaseColor()));
        windowNode.content().add(new DirectInfoRow("Detail:", getPluginScanPhaseDetail()).setLabelWidth(pluginScanLabelWidth()).setValueColorOverride(getPluginScanPhaseColor()));
        int foundCount = getDisplayedPluginCount();
        windowNode.content().add(new DirectInfoRow("Found:", foundCount + " plugin" + (foundCount == 1 ? "" : "s")).setLabelWidth(pluginScanLabelWidth()));
        windowNode.content().add(new DirectInfoRow("Progress:", progressPercent + "%").setLabelWidth(pluginScanLabelWidth()).setValueColorOverride(getPluginScanPhaseColor()));
        windowNode.content().add(scanProgressBar);
    }

    private void buildPluginResultsUi() {
        searchField.setPreferredWidth(Math.max(pluginSearchWidth(), panelW - pluginSearchReserveWidth()));
        windowNode.content().add(searchField);

        DirectRow buttons = new DirectRow().setGap(buttonRowGap());
        buttons.add(new DirectUiButton("Rescan", DirectUiButton.Variant.SECONDARY, () -> {
            AutismConfig.getGlobal().removePluginScan(currentServerAddress(), currentPluginContextSignature());
            resetScan();
            applyPluginSetupLayout();
            saveState();
        }).setGrowX(true).setButtonHeight(actionButtonHeight()));
        buttons.add(new DirectUiButton("Copy Plugins", DirectUiButton.Variant.SECONDARY, this::copyPluginList).setGrowX(true).setButtonHeight(actionButtonHeight()));
        windowNode.content().add(buttons);

        String query = searchField.text().toLowerCase(Locale.ROOT);
        int filteredCount = 0;
        for (PluginDetail detail : getCachedPluginDetails()) {
            if (detail != null && (query.isEmpty() || detail.searchText.contains(query))) filteredCount++;
        }
        String header = detectedPlugins.isEmpty() ? "No plugins detected" : "Detected: " + filteredCount + " plugin" + (filteredCount == 1 ? "" : "s");
        windowNode.content().add(new DirectUiLabel(header, UiTone.MUTED));

        float viewportHeight = Math.max(pluginViewportMinHeight(), panelH - getPluginResultsReservedHeight());
        pluginListSlot.setPreferredHeight(viewportHeight);
        windowNode.content().add(pluginListSlot);
    }

    private String currentServerAddress() {
        ServerData entry = MC.getCurrentServer();
        if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return entry.ip.trim().toLowerCase(Locale.ROOT);
        }
        if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
            SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                    host = inet.getAddress().getHostAddress();
                }
                if (host != null && !host.isBlank()) {
                    return (host + ":" + inet.getPort()).trim().toLowerCase(Locale.ROOT);
                }
            } else if (address != null) {
                String raw = address.toString();
                if (raw != null && !raw.isBlank()) {
                    return raw.replaceFirst("^/", "").trim().toLowerCase(Locale.ROOT);
                }
            }
        }
        return "";
    }

    private String normalizePluginContextSignature(String signature) {
        return signature == null ? "" : signature.trim().toLowerCase(Locale.ROOT);
    }

    private String currentPluginContextSignature() {
        if (MC.getConnection() == null) return "";

        String currentAddress = currentServerAddress();
        String brand = MC.getConnection().serverBrand();
        String normalizedBrand = brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
        if (!pluginContextSignatureDirty
            && currentAddress.equals(cachedPluginContextServerAddress)
            && normalizedBrand.equals(cachedPluginContextBrand)) {
            return cachedPluginContextSignature;
        }

        List<String> parts = new ArrayList<>();
        if (brand != null && !brand.isBlank()) {
            parts.add("brand=" + normalizedBrand);
        }

        try {
            com.mojang.brigadier.CommandDispatcher<?> dispatcher = MC.getConnection().getCommands();
            if (dispatcher != null) {
                com.mojang.brigadier.tree.RootCommandNode<?> root = dispatcher.getRoot();
                if (root != null && !root.getChildren().isEmpty()) {
                    List<String> rootCommands = new ArrayList<>();
                    for (com.mojang.brigadier.tree.CommandNode<?> child : root.getChildren()) {
                        String name = child.getName();
                        if (name != null && !name.isBlank()) {
                            rootCommands.add(name.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                    rootCommands.sort(String.CASE_INSENSITIVE_ORDER);
                    if (!rootCommands.isEmpty()) {
                        parts.add("cmd=" + String.join(",", rootCommands));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        cachedPluginContextServerAddress = currentAddress;
        cachedPluginContextBrand = normalizedBrand;
        cachedPluginContextSignature = normalizePluginContextSignature(String.join("|", parts));
        pluginContextSignatureDirty = false;
        return cachedPluginContextSignature;
    }

    private void invalidatePluginContextSignature() {
        pluginContextSignatureDirty = true;
    }

    private void setPluginProbeDelayMs(int delayMs) {
        pluginProbeDelayMs = Math.max(MIN_PLUGIN_PROBE_DELAY_MS, Math.min(MAX_PLUGIN_PROBE_DELAY_MS, delayMs));
        AutismSharedState.get().setServerDataOverlayProbeDelayMs(pluginProbeDelayMs);
    }

    private void applyClampedBounds() {
        AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(panelX, panelY, panelW, panelH, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelW = clamped.width;
        panelH = clamped.height;
    }

    private void applyPluginSetupLayout() {
        panelW = getSharedPanelWidth();
        panelH = getPluginSetupPanelHeight();
        applyClampedBounds();
        requestUiRebuild();
    }

    private void applyPluginScanningLayout() {
        panelW = getSharedPanelWidth();
        panelH = getPluginScanningPanelHeight();
        applyClampedBounds();
        requestUiRebuild();
    }

    private void applyPluginResultsLayout() {
        panelW = getSharedPanelWidth();
        panelH = Math.max(getPluginResultsMinHeight(), Math.min(getSharedPanelHeight(), getPluginResultsMaxHeight()));
        applyClampedBounds();
        requestUiRebuild();
    }

    private void applyInfoLayout() {
        panelW = getSharedPanelWidth();
        panelH = getSharedPanelHeight();
        applyClampedBounds();
        requestUiRebuild();
    }

    private int getSharedPanelWidth() {
        DirectViewport viewport = surface.viewport();
        return Math.max(infoMinWidth(), Math.min(sharedPanelWidth(), Math.round(viewport.uiWidth())));
    }

    private int getPluginResultsMinHeight() {
        return getSharedPanelHeight();
    }

    private int getPluginResultsMaxHeight() {
        DirectViewport viewport = surface.viewport();
        int viewportHeight = Math.round(viewport.uiHeight());
        return Math.max(getPluginResultsMinHeight(), viewportHeight - 28);
    }

    private int getSharedPanelHeight() {
        DirectViewport viewport = surface.viewport();
        int measured = Math.max(infoMinHeight(), Math.max(infoPreferredHeight, measurePreferredInfoPanelHeight()));
        return Math.max(infoMinHeight(), Math.min(measured, Math.max(infoMinHeight(), Math.round(viewport.uiHeight()) - viewportHeightMargin())));
    }

    private int getPluginSetupPanelHeight() {
        DirectViewport viewport = surface.viewport();
        int measured = Math.max(pluginSetupHeight(), measurePreferredHeightForState(1, false, false, Math.max(panelW, pluginSetupWidth())));
        return Math.min(measured, Math.max(pluginSetupHeight(), Math.round(viewport.uiHeight()) - viewportHeightMargin()));
    }

    private int getPluginScanningPanelHeight() {
        DirectViewport viewport = surface.viewport();
        int measured = Math.max(pluginScanningHeight(), measurePreferredHeightForState(1, false, true, Math.max(panelW, pluginScanningWidth())));
        return Math.min(measured, Math.max(pluginScanningHeight(), Math.round(viewport.uiHeight()) - viewportHeightMargin()));
    }

    private int getPluginResultsReservedHeight() {
        int contentPadding = panelPadding() * 2;
        int contentGap = contentGap() * 3;
        int searchHeight = searchFieldHeight();
        int buttonRowHeight = actionButtonHeight();
        int headerLabelHeight = 13;
        return theme.headerHeight() + contentPadding + contentGap + searchHeight + buttonRowHeight + headerLabelHeight;
    }

    private int measurePreferredInfoPanelHeight() {
        return Math.max(infoMinHeight(), measurePreferredHeightForState(0, pluginScanDone, pluginScanInProgress, Math.max(infoMinWidth(), panelW)));
    }

    private int measurePreferredHeightForState(int tab, boolean scanDone, boolean scanInProgress, int measureWidth) {
        int previousTab = activeTab;
        boolean previousScanDone = pluginScanDone;
        boolean previousScanInProgress = pluginScanInProgress;
        activeTab = tab;
        pluginScanDone = scanDone;
        pluginScanInProgress = scanInProgress;
        rebuildUi();
        int measured = Math.round(surface.measurePreferredHeight(measureWidth));
        activeTab = previousTab;
        pluginScanDone = previousScanDone;
        pluginScanInProgress = previousScanInProgress;
        rebuildUi();
        return measured;
    }

    private int getInfoRequiredHeight() {
        int rowCount = 11;
        String displayedAddress = getDisplayedServerAddress();
        String host = extractLookupHost(displayedAddress);
        String realIp = getDisplayedRealIp(displayedAddress);
        if (!host.isBlank() && (!host.equals(realIp) || resolvingIp)) rowCount++;

        ServerData entry = MC.getCurrentServer();
        String software = getSoftwareGuess(entry);
        if (!"--".equals(software)) rowCount++;

        String versionNote = getVersionNote(entry);
        if (!"--".equals(versionNote)) rowCount++;

        int contentHeight = 5 + (rowCount * 13) + 11 + actionButtonHeight();
        return theme.headerHeight() + (panelPadding() * 2) + (contentGap() * 2) + contentHeight;
    }

    private void rememberCurrentTabSize() {
        if (activeTab == 0) {
            infoPreferredWidth = Math.max(infoMinWidth(), panelW);
            infoPreferredHeight = Math.max(getInfoRequiredHeight(), panelH);
            pluginPreferredWidth = Math.max(pluginPreferredWidth, infoPreferredWidth);
            return;
        }

        if (pluginScanDone && !pluginScanInProgress) {
            pluginPreferredWidth = Math.max(pluginResultsMinWidth(), panelW);
            pluginPreferredHeight = Math.max(getPluginResultsMinHeight(), Math.min(getPluginResultsMaxHeight(), panelH));
            infoPreferredWidth = Math.max(infoPreferredWidth, pluginPreferredWidth);
        }
    }

    private void clearLocalScanState(String address, String contextSignature) {
        pluginScanDone = false;
        pluginScanInProgress = false;
        pluginScanCompletionAnnounced = false;
        pluginScanStartedAt = 0L;
        pluginScanLastResponseAt = 0L;
        pendingPluginProbeIds.clear();
        pluginProbes.clear();
        observedPluginCommands.clear();
        queuedPluginProbes.clear();
        pluginEvidence.clear();
        pluginConfidence.clear();
        pluginResultKinds.clear();
        scanWorkingEntries.clear();
        nextPluginProbeSendAt = 0L;
        pluginScanTotalSteps = 0;
        scannedServerAddress = address;
        scannedPluginContextSignature = normalizePluginContextSignature(contextSignature);
        detectedPlugins.clear();
        pluginCommands.clear();
        pluginChannels.clear();
        pluginGuis.clear();
        invalidatePluginRows();
        pluginScrollState.jumpTo(0, 0);
        selectedPlugin = null;
        lastPayloadFingerprintRevision = -1L;
        observedSuggestionRequests.clear();
        recentCommands.clear();
        invalidatePluginContextSignature();
    }

    private boolean loadCachedScan(String address, String contextSignature) {
        AutismConfig.PluginScanCacheEntry cached = AutismConfig.getGlobal().getPluginScan(address, contextSignature);
        if (cached == null) return false;

        detectedPlugins.clear();
        if (cached.plugins != null) detectedPlugins.addAll(dedupePluginNames(cached.plugins));
        pluginCommands.clear();
        if (cached.commands != null) pluginCommands.putAll(cached.commands);
        pluginChannels.clear();
        if (cached.channels != null) pluginChannels.putAll(cached.channels);
        pluginGuis.clear();
        if (cached.guis != null) pluginGuis.putAll(cached.guis);
        pluginScrollState.jumpTo(0, 0);
        selectedPlugin = null;
        pluginScanDone = true;
        pluginScanInProgress = false;
        pluginScanStartedAt = 0L;
        pluginScanLastResponseAt = 0L;
        pendingPluginProbeIds.clear();
        pluginProbes.clear();
        observedPluginCommands.clear();
        queuedPluginProbes.clear();
        pluginEvidence.clear();
        pluginConfidence.clear();
        pluginResultKinds.clear();
        scanWorkingEntries.clear();
        observedSuggestionRequests.clear();
        recentCommands.clear();
        nextPluginProbeSendAt = 0L;
        pluginScanTotalSteps = 0;
        scannedServerAddress = address;
        scannedPluginContextSignature = normalizePluginContextSignature(cached.contextSignature);
        Map<String, String> cachedEvidence = cached.evidence == null ? Map.of() : cached.evidence;
        Map<String, String> cachedConfidence = cached.confidence == null ? Map.of() : cached.confidence;
        for (String plugin : detectedPlugins) {
            String key = normalizePluginKey(plugin);
            pluginEvidence.put(key, parseEvidenceName(cachedEvidence.get(key)));
            pluginConfidence.put(key, parseConfidenceName(cachedConfidence.get(key), confidenceForEvidence(pluginEvidence.get(key))));
            pluginResultKinds.put(key, resultKindForStoredPlugin(plugin, pluginEvidence.get(key), pluginConfidence.get(key)));
        }
        lastPayloadFingerprintRevision = -1L;
        syncPayloadFingerprintsIntoCurrentScan();
        invalidatePluginRows();
        return true;
    }

    private void cacheCurrentScan() {
        if (scannedServerAddress == null || scannedServerAddress.isBlank()) return;
        if (!pluginScanDone || pluginScanInProgress) return;

        if (detectedPlugins.isEmpty()) return;
        Map<String, String> evidenceSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, PluginEvidence> entry : pluginEvidence.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            evidenceSnapshot.put(entry.getKey(), entry.getValue().name());
        }
        Map<String, String> confidenceSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, PluginConfidence> entry : pluginConfidence.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            confidenceSnapshot.put(entry.getKey(), entry.getValue().name());
        }

        AutismConfig.getGlobal().putPluginScan(scannedServerAddress, scannedPluginContextSignature,
            detectedPlugins, pluginCommands, evidenceSnapshot, pluginChannels, pluginGuis, confidenceSnapshot);
    }

    private void syncScanStateForCurrentServer() {
        String currentAddress = currentServerAddress();
        if (currentAddress.isEmpty()) return;

        String currentContextSignature = currentPluginContextSignature();
        boolean sameAddress = currentAddress.equals(scannedServerAddress);
        boolean hasCurrentSignature = !currentContextSignature.isEmpty();
        boolean sameContext = sameAddress && currentContextSignature.equals(scannedPluginContextSignature);

        if (sameContext && (pluginScanDone || pluginScanInProgress)) return;
        if (sameAddress && !hasCurrentSignature && (pluginScanDone || pluginScanInProgress)) return;

        if (!loadCachedScan(currentAddress, currentContextSignature)) {
            clearLocalScanState(currentAddress, currentContextSignature);
        }
    }

    private String normalizePluginKey(String plugin) {
        return AutismPluginPayloadFingerprints.canonicalPluginKey(plugin);
    }

    private int evidenceRank(PluginEvidence evidence) {
        return switch (evidence) {
            case PAYLOAD_CHANNEL -> 0;
            case PLUGIN_LIST -> 1;
            case VERSION_HINT -> 2;
            case COMMAND_TREE -> 3;
            case COMMAND_GUI -> 4;
            case NAMESPACE -> 5;
            case ROOT_HINT -> 6;
            case SCANNER_AUTOCOMPLETE -> 7;
            case USER_AUTOCOMPLETE -> 8;
            case FEATURE -> 9;
            case HELP_HINT -> 10;
            case UNKNOWN -> 11;
        };
    }

    private int confidenceRank(PluginConfidence confidence) {
        return switch (confidence == null ? PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> 0;
            case STRONG -> 1;
            case FEATURE -> 2;
            case UNKNOWN -> 3;
        };
    }

    private PluginEvidence mergeEvidence(PluginEvidence current, PluginEvidence candidate) {
        if (current == null) return candidate == null ? PluginEvidence.UNKNOWN : candidate;
        if (candidate == null) return current;
        return evidenceRank(candidate) < evidenceRank(current) ? candidate : current;
    }

    private PluginConfidence mergeConfidence(PluginConfidence current, PluginConfidence candidate) {
        if (current == null) return candidate == null ? PluginConfidence.UNKNOWN : candidate;
        if (candidate == null) return current;
        return confidenceRank(candidate) < confidenceRank(current) ? candidate : current;
    }

    private PluginConfidence confidenceForEvidence(PluginEvidence evidence) {
        return switch (evidence == null ? PluginEvidence.UNKNOWN : evidence) {
            case PAYLOAD_CHANNEL, PLUGIN_LIST, VERSION_HINT, NAMESPACE -> PluginConfidence.EXACT;
            case ROOT_HINT -> PluginConfidence.STRONG;
            case COMMAND_TREE, COMMAND_GUI, USER_AUTOCOMPLETE, SCANNER_AUTOCOMPLETE, FEATURE, HELP_HINT -> PluginConfidence.FEATURE;
            case UNKNOWN -> PluginConfidence.UNKNOWN;
        };
    }

    private PluginEvidence parseEvidenceName(String value) {
        if (value == null || value.isBlank()) return PluginEvidence.UNKNOWN;
        try {
            return PluginEvidence.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PluginEvidence.UNKNOWN;
        }
    }

    private PluginConfidence parseConfidenceName(String value, PluginConfidence fallback) {
        if (value == null || value.isBlank()) return fallback == null ? PluginConfidence.UNKNOWN : fallback;
        try {
            return PluginConfidence.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback == null ? PluginConfidence.UNKNOWN : fallback;
        }
    }

    private int getConfidenceColor(PluginConfidence confidence, boolean hovered) {
        if (hovered) return AutismColors.packetWhite();
        return switch (confidence == null ? PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> AutismColors.packetGreen();
            case STRONG -> AutismColors.packetCyan();
            case FEATURE -> AutismColors.packetYellow();
            case UNKNOWN -> 0xFFB79E9E;
        };
    }

    private String confidenceLabel(PluginConfidence confidence) {
        return switch (confidence == null ? PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> "Exact";
            case STRONG -> "Strong";
            case FEATURE -> "Feature";
            case UNKNOWN -> "Unknown";
        };
    }

    private Map<String, PluginScanEntry> buildCurrentPluginEntries() {
        Map<String, PluginScanEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String plugin : detectedPlugins) {
            if (plugin == null || plugin.isBlank()) continue;
            List<String> existing = getPluginCommands(plugin);
            PluginEvidence evidence = pluginEvidence.getOrDefault(normalizePluginKey(plugin), existing != null && !existing.isEmpty() ? PluginEvidence.ROOT_HINT : PluginEvidence.UNKNOWN);
            List<String> existingChannels = getPluginChannels(plugin);
            List<String> existingGuis = getPluginGuis(plugin);
            PluginConfidence confidence = pluginConfidence.getOrDefault(normalizePluginKey(plugin), confidenceForEvidence(evidence));
            PluginResultKind resultKind = resultKindForStoredPlugin(plugin, evidence, confidence);
            mergePluginEntry(entries, null, plugin, existing, existingChannels, existingGuis, existing != null && !existing.isEmpty(), evidence, confidence, resultKind);
        }
        return entries;
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String pluginName, Collection<String> commands, boolean commandBacked, PluginEvidence evidence) {
        return mergePluginEntry(entries, null, pluginName, commands, List.of(), List.of(), commandBacked, evidence, confidenceForEvidence(evidence));
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String pluginName, Collection<String> commands, Collection<String> channels, boolean commandBacked, PluginEvidence evidence) {
        return mergePluginEntry(entries, null, pluginName, commands, channels, List.of(), commandBacked, evidence, confidenceForEvidence(evidence));
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String explicitKey, String pluginName, Collection<String> commands, Collection<String> channels, boolean commandBacked, PluginEvidence evidence) {
        return mergePluginEntry(entries, explicitKey, pluginName, commands, channels, List.of(), commandBacked, evidence, confidenceForEvidence(evidence));
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String explicitKey, String pluginName,
                                     Collection<String> commands, Collection<String> channels, Collection<String> guis,
                                     boolean commandBacked, PluginEvidence evidence, PluginConfidence confidence) {
        return mergePluginEntry(entries, explicitKey, pluginName, commands, channels, guis,
            commandBacked, evidence, confidence, PluginResultKind.PLUGIN);
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String explicitKey, String pluginName,
                                     Collection<String> commands, Collection<String> channels, Collection<String> guis,
                                     boolean commandBacked, PluginEvidence evidence, PluginConfidence confidence,
                                     PluginResultKind resultKind) {
        if (pluginName == null || pluginName.isBlank()) return false;

        String cleanName = pluginName.trim();
        String key = explicitKey == null || explicitKey.isBlank() ? normalizePluginKey(cleanName) : normalizePluginKey(explicitKey);
        if (key.isEmpty() || (VANILLA_NAMESPACES.contains(key) && evidence != PluginEvidence.PAYLOAD_CHANNEL)) return false;

        PluginScanEntry entry = entries.computeIfAbsent(key, unused -> new PluginScanEntry());
        String beforeName = entry.displayName;
        boolean beforeBacked = entry.commandBacked;
        PluginEvidence beforeEvidence = entry.evidence;
        PluginConfidence beforeConfidence = entry.confidence;
        PluginResultKind beforeKind = entry.resultKind;
        int beforeCommandCount = entry.commands.size();
        int beforeChannelCount = entry.channels.size();
        int beforeGuiCount = entry.guis.size();
        if (entry.displayName == null || entry.displayName.isBlank() || (commandBacked && !entry.commandBacked) || isMoreReadablePluginName(cleanName, entry.displayName)) {
            entry.displayName = cleanName;
        }
        entry.resultKind = mergeResultKind(entry.resultKind, resultKind);
        if (commandBacked) entry.commandBacked = true;
        entry.evidence = mergeEvidence(entry.evidence, evidence);
        entry.confidence = mergeConfidence(entry.confidence, confidence);
        pluginEvidence.put(key, mergeEvidence(pluginEvidence.get(key), evidence));
        pluginConfidence.put(key, mergeConfidence(pluginConfidence.get(key), confidence));
        pluginResultKinds.put(key, mergeResultKind(pluginResultKinds.get(key), resultKind));

        if (commands != null) {
            for (String command : commands) {
                if (command == null) continue;
                String cleanCommand = command.trim();
                if (!cleanCommand.isEmpty()) entry.commands.add(cleanCommand);
            }
        }
        if (channels != null) {
            for (String channel : channels) {
                if (channel == null) continue;
                String cleanChannel = channel.trim();
                if (!cleanChannel.isEmpty()) entry.channels.add(cleanChannel);
            }
        }
        if (guis != null) {
            for (String gui : guis) {
                if (gui == null) continue;
                String cleanGui = gui.trim();
                if (!cleanGui.isEmpty()) entry.guis.add(cleanGui);
            }
        }

        return !Objects.equals(beforeName, entry.displayName)
            || beforeBacked != entry.commandBacked
            || beforeEvidence != entry.evidence
            || beforeConfidence != entry.confidence
            || beforeKind != entry.resultKind
            || beforeCommandCount != entry.commands.size()
            || beforeChannelCount != entry.channels.size()
            || beforeGuiCount != entry.guis.size();
    }

    private PluginResultKind mergeResultKind(PluginResultKind current, PluginResultKind candidate) {
        if (candidate == null) return current == null ? PluginResultKind.PLUGIN : current;
        if (current == null) return candidate;
        if (current == PluginResultKind.PLUGIN || candidate == PluginResultKind.PLUGIN) return PluginResultKind.PLUGIN;
        return PluginResultKind.FEATURE;
    }

    private PluginResultKind resultKindForStoredPlugin(String plugin, PluginEvidence evidence, PluginConfidence confidence) {
        String key = normalizePluginKey(plugin);
        PluginResultKind stored = pluginResultKinds.get(key);
        if (stored != null) return stored;
        if (confidence == PluginConfidence.EXACT || confidence == PluginConfidence.STRONG) return PluginResultKind.PLUGIN;
        if (evidence == PluginEvidence.PAYLOAD_CHANNEL || evidence == PluginEvidence.PLUGIN_LIST
            || evidence == PluginEvidence.VERSION_HINT || evidence == PluginEvidence.NAMESPACE) {
            return PluginResultKind.PLUGIN;
        }
        String lower = plugin == null ? "" : plugin.trim().toLowerCase(Locale.ROOT);
        for (String featureLabel : COMMAND_FEATURE_LABELS.values()) {
            if (lower.equals(featureLabel.toLowerCase(Locale.ROOT))) return PluginResultKind.FEATURE;
        }
        return PluginResultKind.PLUGIN;
    }

    private boolean isMoreReadablePluginName(String candidate, String current) {
        if (candidate == null || candidate.isBlank()) return false;
        if (current == null || current.isBlank()) return true;
        String c = candidate.trim();
        String old = current.trim();
        if (c.equalsIgnoreCase(old)) return false;
        String cKey = normalizePluginKey(c);
        String oldKey = normalizePluginKey(old);
        if (!cKey.equals(oldKey)) return false;
        boolean candidateHasCase = !c.equals(c.toLowerCase(Locale.ROOT));
        boolean currentHasCase = !old.equals(old.toLowerCase(Locale.ROOT));
        boolean candidateHasSpace = c.indexOf(' ') >= 0;
        boolean currentHasSpace = old.indexOf(' ') >= 0;
        return (candidateHasCase && !currentHasCase) || (candidateHasSpace && !currentHasSpace);
    }

    private void applyPluginEntries(Map<String, PluginScanEntry> entries, boolean resetSelection) {
        Map<String, PluginScanEntry> normalizedEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (entries != null) normalizedEntries.putAll(entries);
        collapseWeakPluginEntries(normalizedEntries);

        detectedPlugins.clear();
        pluginCommands.clear();
        pluginChannels.clear();
        pluginGuis.clear();
        pluginEvidence.clear();
        pluginConfidence.clear();
        pluginResultKinds.clear();

        List<PluginScanEntry> sortedEntries = new ArrayList<>(normalizedEntries.values());
        sortedEntries.removeIf(entry -> entry == null || entry.displayName == null || entry.displayName.isBlank());
        sortedEntries.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.displayName, b.displayName));

        for (PluginScanEntry entry : sortedEntries) {
            String plugin = entry.displayName;
            List<String> commands = new ArrayList<>(entry.commands);
            commands.removeIf(cmd -> cmd == null || cmd.isBlank());
            commands.sort(String.CASE_INSENSITIVE_ORDER);

            detectedPlugins.add(plugin);
            pluginCommands.put(plugin, commands);

            pluginChannels.put(plugin, dedupeChannelRows(entry.channels));
            List<String> guis = new ArrayList<>(entry.guis);
            guis.removeIf(gui -> gui == null || gui.isBlank());
            guis.sort(String.CASE_INSENSITIVE_ORDER);
            pluginGuis.put(plugin, List.copyOf(guis));
            pluginEvidence.put(normalizePluginKey(plugin), entry.evidence == null ? PluginEvidence.UNKNOWN : entry.evidence);
            pluginConfidence.put(normalizePluginKey(plugin), entry.confidence == null ? confidenceForEvidence(entry.evidence) : entry.confidence);
            pluginResultKinds.put(normalizePluginKey(plugin), entry.resultKind == null ? PluginResultKind.PLUGIN : entry.resultKind);
        }

        if (resetSelection) {
            pluginScrollState.jumpTo(0, 0);
            selectedPlugin = null;
        } else if (selectedPlugin != null && !hasDetectedPlugin(selectedPlugin)) {
            selectedPlugin = null;
        }
        invalidatePluginRows();
    }

    private boolean isKnownPluginNamespace(String key) {
        for (String namespace : COMMON_PLUGIN_NAMESPACES) {
            if (namespace.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private String normalizeCommandToken(String raw) {
        if (raw == null) return "";
        String token = raw.trim();
        if (token.isEmpty()) return "";
        while (token.startsWith("/")) token = token.substring(1).trim();
        int spaceIndex = token.indexOf(' ');
        if (spaceIndex >= 0) token = token.substring(0, spaceIndex).trim();
        while (token.endsWith(":")) token = token.substring(0, token.length() - 1).trim();
        return token.toLowerCase(Locale.ROOT);
    }

    private boolean isTrackableCommandToken(String token) {
        if (token == null || token.isBlank()) return false;
        String clean = token.trim().toLowerCase(Locale.ROOT);
        if (clean.length() < 2) return false;
        if (VANILLA_NAMESPACES.contains(clean) || VANILLA_COMMAND_ROOTS.contains(clean)) return false;
        if (getOnlinePlayerNames().contains(clean)) return false;
        return clean.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.' || ch == ':');
    }

    private String humanizeFeatureCommand(String token) {
        if (token == null || token.isBlank()) return "Command";
        String mapped = COMMAND_FEATURE_LABELS.get(token.toLowerCase(Locale.ROOT));
        if (mapped != null && !mapped.isBlank()) return mapped;
        String[] parts = token.replace(':', ' ').split("[_\\-.\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            if (part.length() <= 3) {
                sb.append(part.toUpperCase(Locale.ROOT));
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? token : sb.toString();
    }

    private PluginEvidence commandEvidenceForSource(PluginEvidence source, boolean exactPluginName) {
        if (exactPluginName) return source == PluginEvidence.USER_AUTOCOMPLETE ? PluginEvidence.ROOT_HINT : source;
        return source == null ? PluginEvidence.FEATURE : source;
    }

    private boolean mergeCommandEvidence(Map<String, PluginScanEntry> entries, String rawCommand, PluginEvidence source) {
        String token = normalizeCommandToken(rawCommand);
        if (!isTrackableCommandToken(token)) return false;

        observedPluginCommands.add(token);
        if (token.contains(":")) {
            String[] parts = token.split(":", 2);
            String namespace = parts[0].trim().toLowerCase(Locale.ROOT);
            String command = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
            if (isTrackableCommandToken(namespace) && !VANILLA_NAMESPACES.contains(namespace)) {
                return mergePluginEntry(entries, namespace, namespace, command.isBlank() ? List.of() : List.of(command), List.of(), List.of(),
                    !command.isBlank(), PluginEvidence.NAMESPACE, PluginConfidence.EXACT);
            }
            return false;
        }

        String plugin = null;
        PluginConfidence confidence = PluginConfidence.FEATURE;
        PluginEvidence evidence = source == null ? PluginEvidence.FEATURE : source;
        String explicitKey = null;

        if (isKnownPluginNamespace(token)) {
            plugin = token;
            confidence = PluginConfidence.EXACT;
            evidence = PluginEvidence.NAMESPACE;
        } else if (ROOT_COMMAND_PLUGIN_ALIASES.containsKey(token)) {
            plugin = ROOT_COMMAND_PLUGIN_ALIASES.get(token);
            confidence = PluginConfidence.STRONG;
            evidence = PluginEvidence.ROOT_HINT;
        } else if (source == PluginEvidence.USER_AUTOCOMPLETE
            || source == PluginEvidence.SCANNER_AUTOCOMPLETE
            || source == PluginEvidence.COMMAND_GUI
            || COMMAND_FEATURE_LABELS.containsKey(token)) {
            plugin = humanizeFeatureCommand(token);
            explicitKey = "feature_" + token;
            confidence = PluginConfidence.FEATURE;
            evidence = PluginEvidence.FEATURE;
        }

        if (plugin == null || plugin.isBlank()) return false;
        PluginResultKind resultKind = explicitKey != null && explicitKey.startsWith("feature_")
            ? PluginResultKind.FEATURE
            : PluginResultKind.PLUGIN;
        return mergePluginEntry(entries, explicitKey, plugin, List.of(token), List.of(), List.of(), true, evidence, confidence, resultKind);
    }

    private Set<String> getOnlinePlayerNames() {
        if (MC.getConnection() == null) return Set.of();
        Set<String> names = new HashSet<>();
        for (var player : MC.getConnection().getListedOnlinePlayers()) {
            if (player == null || player.getProfile() == null) continue;
            String name = player.getProfile().name();
            if (name != null && !name.isBlank()) {
                names.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private boolean isLikelyPluginNameCandidate(String candidate, PluginProbeKind kind) {
        if (candidate == null) return false;
        String clean = candidate.trim();
        String key = normalizePluginKey(clean);
        if (key.isEmpty()) return false;
        if (key.length() < 2) return false;
        if (clean.contains(" ") || clean.contains("/") || clean.contains(":")) return false;
        if (VANILLA_NAMESPACES.contains(key)) return false;
        if (getOnlinePlayerNames().contains(key)) return false;

        if (kind == PluginProbeKind.PLUGIN_LIST || kind == PluginProbeKind.VERSION) {
            if (key.equals("plugins") || key.equals("plugin") || key.equals("version") || key.equals("about")) return false;
        }

        return key.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.');
    }

    private void addObservedPluginCommand(String raw) {
        String token = normalizeCommandToken(raw);
        if (!isTrackableCommandToken(token)) return;
        observedPluginCommands.add(token);
    }

    private void inferPluginsFromObservedCommands(Map<String, PluginScanEntry> entries) {
        if (observedPluginCommands.isEmpty()) return;

        for (String command : observedPluginCommands) {
            mergeCommandEvidence(entries, command, PluginEvidence.SCANNER_AUTOCOMPLETE);
        }
    }

    private boolean mergePayloadFingerprintEntries(Map<String, PluginScanEntry> entries) {
        String address = currentServerAddress();
        if (address == null || address.isBlank()) return false;
        boolean changed = false;
        for (AutismPluginPayloadFingerprints.PluginFingerprint fingerprint : AutismPluginPayloadFingerprints.pluginsFor(address, getLiveBrand())) {
            if (fingerprint == null || fingerprint.plugin() == null || fingerprint.plugin().isBlank()) continue;
            changed |= promoteMatchingFeatureEntries(entries, fingerprint);
            PluginConfidence confidence = payloadFingerprintConfidence(fingerprint);
            changed |= mergePluginEntry(entries, fingerprint.key(), fingerprint.plugin(), List.of(), fingerprint.channels(), List.of(), false,
                PluginEvidence.PAYLOAD_CHANNEL, confidence);
        }
        return changed;
    }

    private PluginConfidence payloadFingerprintConfidence(AutismPluginPayloadFingerprints.PluginFingerprint fingerprint) {
        String basis = fingerprint == null ? "" : fingerprint.basis();
        if ("embedded".equalsIgnoreCase(basis)) return PluginConfidence.STRONG;
        return PluginConfidence.EXACT;
    }

    private boolean promoteMatchingFeatureEntries(Map<String, PluginScanEntry> entries,
                                                  AutismPluginPayloadFingerprints.PluginFingerprint fingerprint) {
        if (entries == null || entries.isEmpty() || fingerprint == null) return false;
        Set<String> keywords = fingerprintKeywords(fingerprint);
        if (keywords.isEmpty()) return false;

        boolean changed = false;
        List<String> removeKeys = new ArrayList<>();
        for (Map.Entry<String, PluginScanEntry> mapEntry : entries.entrySet()) {
            String key = mapEntry.getKey();
            PluginScanEntry entry = mapEntry.getValue();
            if (key == null || !key.startsWith("feature")) continue;
            if (entry == null || !featureEntryMatches(entry, keywords)) continue;
            changed |= mergePluginEntry(entries, fingerprint.key(), fingerprint.plugin(), entry.commands, fingerprint.channels(), entry.guis,
                entry.commandBacked, PluginEvidence.PAYLOAD_CHANNEL, payloadFingerprintConfidence(fingerprint));
            removeKeys.add(key);
        }
        for (String key : removeKeys) {
            entries.remove(key);
            pluginEvidence.remove(key);
            pluginConfidence.remove(key);
            pluginResultKinds.remove(key);
            changed = true;
        }
        return changed;
    }

    private Set<String> fingerprintKeywords(AutismPluginPayloadFingerprints.PluginFingerprint fingerprint) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addFingerprintKeywords(keywords, fingerprint.plugin());
        addFingerprintKeywords(keywords, fingerprint.key());
        if (fingerprint.channels() != null) {
            for (String channel : fingerprint.channels()) addFingerprintKeywords(keywords, channel);
        }
        keywords.removeIf(keyword -> keyword.length() < 3);
        return keywords;
    }

    private void addFingerprintKeywords(Set<String> out, String text) {
        if (out == null || text == null || text.isBlank()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : COMMAND_FEATURE_LABELS.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String label = entry.getValue().toLowerCase(Locale.ROOT);
            if (lower.contains(key) || lower.contains(label.replace(" ", "")) || lower.contains(label)) {
                out.add(key);
                out.add(label);
            }
        }
    }

    private boolean featureEntryMatches(PluginScanEntry entry, Set<String> keywords) {
        if (entry == null || keywords == null || keywords.isEmpty()) return false;
        List<String> evidenceText = new ArrayList<>();
        evidenceText.add(entry.displayName);
        evidenceText.addAll(entry.commands);
        evidenceText.addAll(entry.guis);
        for (String text : evidenceText) {
            if (text == null) continue;
            String lower = text.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    private boolean collapseWeakPluginEntries(Map<String, PluginScanEntry> entries) {
        if (entries == null || entries.size() < 2) return false;
        boolean changedAny = false;
        boolean changed;
        do {
            changed = false;
            List<Map.Entry<String, PluginScanEntry>> snapshot = new ArrayList<>(entries.entrySet());
            for (Map.Entry<String, PluginScanEntry> weakEntry : snapshot) {
                String weakKey = weakEntry.getKey();
                PluginScanEntry weak = weakEntry.getValue();
                if (weakKey == null || weak == null || !entries.containsKey(weakKey)) continue;

                Map.Entry<String, PluginScanEntry> stronger = findStrongerRelatedEntry(weakKey, weak, entries);
                if (stronger == null) continue;
                mergeWeakEntryIntoStronger(stronger.getValue(), weak);
                entries.remove(weakKey);
                pluginEvidence.remove(weakKey);
                pluginConfidence.remove(weakKey);
                pluginResultKinds.remove(weakKey);
                changed = true;
                changedAny = true;
            }
        } while (changed);
        return changedAny;
    }

    private Map.Entry<String, PluginScanEntry> findStrongerRelatedEntry(String weakKey, PluginScanEntry weak,
                                                                        Map<String, PluginScanEntry> entries) {
        Map.Entry<String, PluginScanEntry> best = null;
        for (Map.Entry<String, PluginScanEntry> candidateEntry : entries.entrySet()) {
            String candidateKey = candidateEntry.getKey();
            PluginScanEntry candidate = candidateEntry.getValue();
            if (candidateKey == null || candidate == null || candidateKey.equals(weakKey)) continue;
            if (!isMoreTrustedPluginEntry(candidate, weak)) continue;
            if (!entriesAreRelated(weakKey, weak, candidateKey, candidate)) continue;
            if (best == null || isMoreTrustedPluginEntry(candidate, best.getValue())) {
                best = candidateEntry;
            }
        }
        return best;
    }

    private boolean isMoreTrustedPluginEntry(PluginScanEntry candidate, PluginScanEntry weak) {
        if (candidate == null || weak == null) return false;
        int candidateConfidence = confidenceRank(candidate.confidence);
        int weakConfidence = confidenceRank(weak.confidence);
        if (candidateConfidence != weakConfidence) return candidateConfidence < weakConfidence;
        if (candidate.resultKind != weak.resultKind) return candidate.resultKind == PluginResultKind.PLUGIN;
        return evidenceRank(candidate.evidence) < evidenceRank(weak.evidence);
    }

    private boolean entriesAreRelated(String weakKey, PluginScanEntry weak, String candidateKey, PluginScanEntry candidate) {
        if (weak == null || candidate == null) return false;
        if (!Collections.disjoint(weak.commands, candidate.commands)) return true;
        if (shareNormalizedText(weak.guis, candidate.guis)) return true;
        if (!Collections.disjoint(normalizedChannelBases(weak.channels), normalizedChannelBases(candidate.channels))) return true;

        if (weakKey != null && (weakKey.startsWith("feature") || weakKey.startsWith("gui"))) {
            Set<String> candidateKeywords = entryKeywords(candidate);
            if (featureEntryMatches(weak, candidateKeywords)) return true;
        }
        if (candidateKey != null && (candidateKey.startsWith("feature") || candidateKey.startsWith("gui"))) {
            Set<String> weakKeywords = entryKeywords(weak);
            return featureEntryMatches(candidate, weakKeywords);
        }
        return false;
    }

    private boolean shareNormalizedText(Collection<String> a, Collection<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        Set<String> left = new HashSet<>();
        for (String value : a) {
            String normalized = normalizeEvidenceText(value);
            if (!normalized.isBlank()) left.add(normalized);
        }
        for (String value : b) {
            String normalized = normalizeEvidenceText(value);
            if (!normalized.isBlank() && left.contains(normalized)) return true;
        }
        return false;
    }

    private Set<String> normalizedChannelBases(Collection<String> channels) {
        Set<String> out = new HashSet<>();
        if (channels == null) return out;
        for (String channel : channels) {
            String base = channelDetailBase(channel).toLowerCase(Locale.ROOT);
            if (!base.isBlank() && !isNoChannelRow(base) && !isProbableChannelRow(base)) out.add(base);
        }
        return out;
    }

    private Set<String> entryKeywords(PluginScanEntry entry) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (entry == null) return keywords;
        addFingerprintKeywords(keywords, entry.displayName);
        for (String command : entry.commands) addFingerprintKeywords(keywords, command);
        for (String gui : entry.guis) addFingerprintKeywords(keywords, gui);
        for (String channel : entry.channels) addFingerprintKeywords(keywords, channel);
        keywords.removeIf(keyword -> keyword == null || keyword.length() < 3);
        return keywords;
    }

    private String normalizeEvidenceText(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("\u00a7.", "")
            .replaceAll("[^a-z0-9]+", "");
    }

    private void mergeWeakEntryIntoStronger(PluginScanEntry strong, PluginScanEntry weak) {
        if (strong == null || weak == null) return;
        strong.commands.addAll(weak.commands);
        strong.guis.addAll(weak.guis);
        strong.channels.addAll(weak.channels);
        if (weak.commandBacked) strong.commandBacked = true;
        strong.resultKind = mergeResultKind(strong.resultKind, weak.resultKind);
        strong.evidence = mergeEvidence(strong.evidence, weak.evidence);
        strong.confidence = mergeConfidence(strong.confidence, weak.confidence);
    }

    private void syncPayloadFingerprintsIntoCurrentScan() {
        if (pluginScanInProgress) {
            if (mergePayloadFingerprintEntries(scanWorkingEntries)) {
                pluginScanLastResponseAt = System.currentTimeMillis();
            }
            return;
        }

        String address = currentServerAddress();
        long revision = AutismPluginPayloadFingerprints.revisionFor(address, getLiveBrand());
        if (revision == lastPayloadFingerprintRevision) return;
        lastPayloadFingerprintRevision = revision;
        if (revision <= 0L) return;

        Map<String, PluginScanEntry> entries = buildCurrentPluginEntries();
        if (mergePayloadFingerprintEntries(entries)) {
            applyPluginEntries(entries, false);
            if (pluginScanDone) cacheCurrentScan();
        }
    }

    private int addProbeVariants(Map<Integer, PluginProbeSpec> probes, int nextId, PluginProbeKind kind, String... baseCommands) {
        for (String base : baseCommands) {
            if (base == null || base.isBlank()) continue;
            String trimmed = base.trim();
            probes.put(nextId++, new PluginProbeSpec(trimmed, kind, null));
            probes.put(nextId++, new PluginProbeSpec(trimmed + " ", kind, null));
        }
        return nextId;
    }

    private Map<Integer, PluginProbeSpec> buildPluginProbes() {
        Map<Integer, PluginProbeSpec> probes = new LinkedHashMap<>();
        int nextId = COMPLETION_ID;

        probes.put(nextId++, new PluginProbeSpec("/", PluginProbeKind.ROOT, null));
        probes.put(nextId++, new PluginProbeSpec("/ ", PluginProbeKind.ROOT, null));

        nextId = addProbeVariants(probes, nextId, PluginProbeKind.PLUGIN_LIST, "/plugins", "/pl", "/bukkit:plugins", "/bukkit:pl");
        nextId = addProbeVariants(probes, nextId, PluginProbeKind.VERSION, "/ver", "/version", "/about", "/icanhasbukkit", "/bukkit:ver", "/bukkit:version");
        nextId = addProbeVariants(probes, nextId, PluginProbeKind.HELP, "/help", "/?", "/bukkit:help", "/minecraft:help");

        for (int i = 0; i < ROOT_PROBE_PREFIXES.length(); i++) {
            char prefix = ROOT_PROBE_PREFIXES.charAt(i);
            String rootProbe = "/" + prefix;
            probes.put(nextId++, new PluginProbeSpec(rootProbe, PluginProbeKind.ROOT, String.valueOf(prefix)));
            probes.put(nextId++, new PluginProbeSpec("/help " + prefix, PluginProbeKind.HELP, String.valueOf(prefix)));
            probes.put(nextId++, new PluginProbeSpec("/? " + prefix, PluginProbeKind.HELP, String.valueOf(prefix)));
        }

        for (String namespace : COMMON_PLUGIN_NAMESPACES) {
            probes.put(nextId++, new PluginProbeSpec("/" + namespace + ":", PluginProbeKind.NAMESPACE, namespace));
        }

        for (String plugin : HIGH_VALUE_PLUGIN_HINTS) {
            probes.put(nextId++, new PluginProbeSpec("/version " + plugin, PluginProbeKind.VERSION, plugin));
            probes.put(nextId++, new PluginProbeSpec("/ver " + plugin, PluginProbeKind.VERSION, plugin));
            probes.put(nextId++, new PluginProbeSpec("/help " + plugin, PluginProbeKind.HELP, plugin));
        }

        return probes;
    }

    public boolean isAutoProbeConnectionReady() {
        return MC != null && MC.getConnection() != null && !currentServerAddress().isBlank();
    }

    public boolean isAutoProbeContextReady() {
        try {
            if (MC == null || MC.getConnection() == null) return false;
            com.mojang.brigadier.CommandDispatcher<?> dispatcher = MC.getConnection().getCommands();
            return dispatcher != null && dispatcher.getRoot() != null && !dispatcher.getRoot().getChildren().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean autoProbeOnSpawn() {
        if (!isAutoProbeConnectionReady()) return false;

        String address = currentServerAddress();
        String context = currentPluginContextSignature();
        syncScanStateForCurrentServer();
        if (pluginScanInProgress) return true;

        if (context.equals(lastAutoScanContext) && pluginScanDone) return true;

        long cachedAt = AutismConfig.getGlobal().getPluginScanTimestamp(address, context);
        boolean fresh = cachedAt > 0L && (System.currentTimeMillis() - cachedAt) < PLUGIN_CACHE_MAX_AGE_MS;
        if (fresh && pluginScanDone) {
            lastAutoScanContext = context;
            return true;
        }

        lastAutoScanContext = context;
        AutismConfig.getGlobal().removePluginScan(address, context);
        resetScan();
        scanPlugins();
        return pluginScanInProgress;
    }

    public void resetAutoProbeContext() {
        lastAutoScanContext = null;
    }

    private void finalizePluginScan() {
        if (!pluginScanInProgress && pluginScanDone) return;

        mergePayloadFingerprintEntries(scanWorkingEntries);
        applyPluginEntries(scanWorkingEntries, true);
        pluginScanInProgress = false;
        pluginScanDone = true;
        pendingPluginProbeIds.clear();
        pluginProbes.clear();
        observedPluginCommands.clear();
        queuedPluginProbes.clear();
        nextPluginProbeSendAt = 0L;
        scanWorkingEntries.clear();
        pluginScanStartedAt = 0L;
        pluginScanLastResponseAt = 0L;
        pluginScanTotalSteps = 0;
        scanWorkingEntries.clear();
        applyPluginResultsLayout();
        cacheCurrentScan();
        announcePluginScanComplete();
        activeProbeDelayMs = pluginProbeDelayMs;
    }

    private void dispatchQueuedPluginProbes() {
        if (!pluginScanInProgress || MC.getConnection() == null) return;

        long now = System.currentTimeMillis();
        if (now < nextPluginProbeSendAt) return;

        PluginProbeRequest request = queuedPluginProbes.pollFirst();
        if (request == null) return;

        try {
            net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket packet =
                new net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket(request.id, request.spec.query);
            MC.getConnection().send(packet);
            nextPluginProbeSendAt = now + activeProbeDelayMs;
        } catch (Exception ignored) {
            pendingPluginProbeIds.remove(request.id);
            pluginProbes.remove(request.id);
        }
    }

    private int getTotalPluginProbeCount() {
        return Math.max(0, pluginScanTotalSteps - 1);
    }

    private long getPluginScanSendWindowMs() {
        return Math.max(activeProbeDelayMs, (long) getTotalPluginProbeCount() * activeProbeDelayMs);
    }

    private long getPluginScanFinishedSendingAt() {
        if (pluginScanStartedAt <= 0L) return 0L;
        if (!queuedPluginProbes.isEmpty()) return 0L;
        long candidate = nextPluginProbeSendAt - activeProbeDelayMs;
        return Math.max(pluginScanStartedAt, candidate);
    }

    private long getPluginScanHardTimeoutMs() {
        return Math.max(PLUGIN_SCAN_TIMEOUT_MS, getPluginScanSendWindowMs() + PLUGIN_SCAN_SETTLE_MS + 600L);
    }

    private int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (from >>> 24) & 0xFF;
        int r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF;
        int b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF;
        int r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF;
        int b2 = to & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * clamped);
        int r = Math.round(r1 + (r2 - r1) * clamped);
        int g = Math.round(g1 + (g2 - g1) * clamped);
        int b = Math.round(b1 + (b2 - b1) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int getSentPluginProbeCount() {
        int total = getTotalPluginProbeCount();
        return Math.max(0, Math.min(total, total - queuedPluginProbes.size()));
    }

    private int getAnsweredPluginProbeCount() {
        int total = getTotalPluginProbeCount();
        return Math.max(0, Math.min(total, total - pendingPluginProbeIds.size()));
    }

    private float getPluginScanProgress() {
        if (pluginScanDone) return 1.0f;
        int total = getTotalPluginProbeCount();
        if (total <= 0) return 0.0f;

        float sentRatio = getSentPluginProbeCount() / (float) total;
        float answeredRatio = getAnsweredPluginProbeCount() / (float) total;
        if (!queuedPluginProbes.isEmpty()) {
            float progress = 0.80f * sentRatio;
            progress += 0.05f * answeredRatio;
            return Math.max(0.0f, Math.min(0.85f, progress));
        }

        float progress = 0.85f;
        long finishedSendingAt = getPluginScanFinishedSendingAt();
        long waitWindow = pendingPluginProbeIds.isEmpty() ? PLUGIN_SCAN_IDLE_MS : PLUGIN_SCAN_SETTLE_MS;
        if (finishedSendingAt > 0L && waitWindow > 0L) {
            long now = System.currentTimeMillis();
            float settle = Math.max(0.0f, Math.min(1.0f, (now - finishedSendingAt) / (float) waitWindow));
            progress += 0.14f * settle;
        }

        return Math.max(0.0f, Math.min(0.99f, progress));
    }

    private int getWorkingPluginCount() {
        return (int) scanWorkingEntries.values().stream()
            .filter(entry -> entry != null && entry.displayName != null && !entry.displayName.isBlank())
            .count();
    }

    private int getDisplayedPluginCount() {
        if (pluginScanInProgress) {
            return getWorkingPluginCount();
        }
        return detectedPlugins.size();
    }

    private String getPluginScanStatusLabel() {
        int foundCount = getDisplayedPluginCount();
        if (foundCount > 0) {
            return "Scanning plugins | found " + foundCount;
        }
        return "Scanning plugins";
    }

    private String getPluginScanPhaseLabel() {
        return queuedPluginProbes.isEmpty() ? "Analyzing replies" : "Sending probes";
    }

    private int getPluginScanPhaseColor() {
        return queuedPluginProbes.isEmpty() ? AutismColors.packetCyan() : AutismColors.packetYellow();
    }

    private String getPluginScanPhaseDetail() {
        int total = Math.max(1, getTotalPluginProbeCount());
        return queuedPluginProbes.isEmpty()
            ? getAnsweredPluginProbeCount() + "/" + total
            : getSentPluginProbeCount() + "/" + total;
    }

    private void announcePluginScanComplete() {
        if (pluginScanCompletionAnnounced) return;
        pluginScanCompletionAnnounced = true;

        int count = detectedPlugins.size();
        if (count <= 0) {
            AutismClientMessaging.sendPrefixed("Plugin probing finished: no plugins found.");
        } else if (count == 1) {
            AutismClientMessaging.sendPrefixed("Plugin probing finished: found 1 plugin.");
        } else {
            AutismClientMessaging.sendPrefixed("Plugin probing finished: found " + count + " plugins.");
        }
    }

    public boolean shouldRenderBackgroundProbeBanner() {
        return pluginScanInProgress;
    }

    public boolean isPacketObservationActive() {
        return visible || pluginScanInProgress || pluginScanDone || !detectedPlugins.isEmpty();
    }

    public void renderBackgroundProbeBanner(GuiGraphicsExtractor ctx) {
        if (!shouldRenderBackgroundProbeBanner() || MC == null || MC.getWindow() == null || textRenderer == null) return;

        DirectViewport viewport = surface.viewport();
        String status = getPluginScanStatusLabel();
        String phase = getPluginScanPhaseLabel();
        String detail = getPluginScanPhaseDetail();
        int progressPercent = Math.max(0, Math.min(100, Math.round(getPluginScanProgress() * 100.0f)));
        String percentComponent = progressPercent + "%";
        int screenW = Math.round(viewport.uiWidth());
        int statusWidth = UiText.width(textRenderer, status, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED));
        int phaseWidth = UiText.width(textRenderer, phase, theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY));
        int detailWidth = UiText.width(textRenderer, detail, theme.fontFor(UiTone.MUTED), getPluginScanPhaseColor());
        int percentWidth = UiText.width(textRenderer, percentComponent, theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY));
        int contentW = Math.max(180, Math.max(statusWidth, Math.max(phaseWidth + 12 + detailWidth, percentWidth + 150)));

        int boxW = Math.max(1, Math.min(Math.max(1, screenW - 16), Math.max(236, contentW + 24)));
        int boxH = 42;
        int boxX = Math.max(8, (screenW - boxW) / 2);
        int boxY = 8;
        int innerX = boxX + 8;
        int barY = boxY + 17;
        int barW = boxW - 16;
        int barH = 10;

        viewport.push(ctx);
        try {
            DirectRenderContext bannerContext = new DirectRenderContext(ctx, textRenderer, viewport, theme, 0, 0, 0.0f);
            int border = theme.borderColor();
            int headerAccent = theme.headerAccent();
            UiRenderer.window(
                ctx,
                UiBounds.of(boxX, boxY, boxW, boxH),
                14,
                bannerContext.applyAlpha(theme.windowFill()),
                bannerContext.applyAlpha(theme.headerFill()),
                bannerContext.applyAlpha(theme.windowFill()),
                bannerContext.applyAlpha(border),
                bannerContext.applyAlpha(headerAccent),
                1.0f
            );

            UiText.draw(ctx, textRenderer, status, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED), innerX, boxY + 3, false);
            UiText.draw(ctx, textRenderer, percentComponent, theme.fontFor(UiTone.BODY), getPluginScanPhaseColor(), boxX + boxW - 8 - percentWidth, boxY + 3, false);

            UiRenderer.frame(
                ctx,
                UiBounds.of(innerX, barY, barW, barH),
                bannerContext.applyAlpha(theme.overlaySurfaceSoft(0x000A090C)),
                bannerContext.applyAlpha(0xFF8F3131)
            );

            int fillW = Math.max(0, Math.min(barW - 2, Math.round((barW - 2) * getPluginScanProgress())));
            if (fillW > 0) {
                int fillColor = UiSizing.lerpColor(0xFFFF5A5A, 0xFF66E08A, getPluginScanProgress());
                UiRenderer.rect(ctx, UiBounds.of(innerX + 1, barY + 1, fillW, barH - 2), bannerContext.applyAlpha(fillColor));
            }

            UiText.draw(ctx, textRenderer, phase, theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY), innerX, barY + 13, false);
            UiText.draw(ctx, textRenderer, detail, theme.fontFor(UiTone.MUTED), getPluginScanPhaseColor(), boxX + boxW - 8 - detailWidth, barY + 13, false);
        } finally {
            viewport.pop(ctx);
        }
    }

    private void updatePluginScanLifecycle() {
        if (!pluginScanInProgress) return;

        dispatchQueuedPluginProbes();

        long now = System.currentTimeMillis();
        boolean allProbesSent = queuedPluginProbes.isEmpty();
        boolean allResponsesReceived = pendingPluginProbeIds.isEmpty();
        boolean idle = pluginScanLastResponseAt > 0L && now - pluginScanLastResponseAt >= PLUGIN_SCAN_IDLE_MS;
        long finishedSendingAt = getPluginScanFinishedSendingAt();
        boolean settledAfterSend = allProbesSent
            && finishedSendingAt > 0L
            && now - finishedSendingAt >= (allResponsesReceived ? PLUGIN_SCAN_IDLE_MS : PLUGIN_SCAN_SETTLE_MS);
        boolean timedOut = pluginScanStartedAt > 0L && now - pluginScanStartedAt >= getPluginScanHardTimeoutMs();

        if ((allProbesSent && allResponsesReceived) || (allProbesSent && settledAfterSend) || timedOut) {
            finalizePluginScan();
        }
    }

    public void tickBackground() {
        syncScanStateForCurrentServer();
        syncPayloadFingerprintsIntoCurrentScan();
        if (pluginScanInProgress) {
            updatePluginScanLifecycle();
        }
    }

    private void parseCommandTree(com.mojang.brigadier.tree.RootCommandNode<?> root) {
        Map<String, PluginScanEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        entries.putAll(scanWorkingEntries);
        for (var child : root.getChildren()) {
            String name = child.getName();
            if (name.contains(":")) {
                String[] parts = name.split(":", 2);
                String ns = parts[0].toLowerCase();
                String cmd = parts[1];
                if (!VANILLA_NAMESPACES.contains(ns)) {
                    mergeCommandEvidence(entries, ns + ":" + cmd, PluginEvidence.COMMAND_TREE);
                }
            } else {
                mergeCommandEvidence(entries, name, PluginEvidence.COMMAND_TREE);
            }
        }
        inferPluginsFromObservedCommands(entries);
        scanWorkingEntries.clear();
        scanWorkingEntries.putAll(entries);
    }

    public void resetScan() {
        clearLocalScanState(null, "");
        searchField.setText("");
        searchField.setFocused(false);
        resolvedIp = null;
        lastResolvedAddress = null;
        invalidatePluginContextSignature();
    }

    private void scanPlugins() {
        syncScanStateForCurrentServer();

        if (pluginScanDone || pluginScanInProgress) return;
        if (MC.getConnection() == null) {
            pluginScanDone = true;
            cacheCurrentScan();
            return;
        }

        activeProbeDelayMs = pluginProbeDelayMs;
        scannedServerAddress = currentServerAddress();
        scannedPluginContextSignature = currentPluginContextSignature();
        activeTab = 1;
        pluginScanInProgress = true;
        pluginScanDone = false;
        pluginScanStartedAt = System.currentTimeMillis();
        pluginScanLastResponseAt = pluginScanStartedAt;
        pendingPluginProbeIds.clear();
        pluginProbes.clear();
        observedPluginCommands.clear();
        queuedPluginProbes.clear();
        pluginEvidence.clear();
        pluginConfidence.clear();
        pluginResultKinds.clear();
        scanWorkingEntries.clear();
        nextPluginProbeSendAt = pluginScanStartedAt + activeProbeDelayMs;
        applyPluginScanningLayout();
        try {
            com.mojang.brigadier.CommandDispatcher<?> dispatcher =
                MC.getConnection().getCommands();
            if (dispatcher != null) {
                com.mojang.brigadier.tree.RootCommandNode<?> root = dispatcher.getRoot();
                if (root != null && !root.getChildren().isEmpty()) {
                    parseCommandTree(root);
                }
            }
            mergePayloadFingerprintEntries(scanWorkingEntries);

            Map<Integer, PluginProbeSpec> probes = buildPluginProbes();
            pluginScanTotalSteps = probes.size() + 1;
            for (Map.Entry<Integer, PluginProbeSpec> probe : probes.entrySet()) {
                pendingPluginProbeIds.add(probe.getKey());
                pluginProbes.put(probe.getKey(), probe.getValue());
                queuedPluginProbes.addLast(new PluginProbeRequest(probe.getKey(), probe.getValue()));
            }
            updatePluginScanLifecycle();
        } catch (Exception ignored) {
            finalizePluginScan();
        }
    }

    public void onCommandSuggestionRequest(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet == null || PackHideState.isActive()) return;
        if (!"ServerboundCommandSuggestionPacket".equals(packet.getClass().getSimpleName())) return;
        int id = intValue(packet, -1, "getId", "id");
        String command = stringValue(packet, "getCommand", "command");
        if (id < 0 || command == null || command.isBlank()) return;
        if (pluginProbes.containsKey(id)) return;
        observedSuggestionRequests.put(id, new PluginProbeSpec(command, PluginProbeKind.USER, null));
        while (observedSuggestionRequests.size() > MAX_TRACKED_SUGGESTION_REQUESTS) {
            Integer first = observedSuggestionRequests.keySet().iterator().next();
            observedSuggestionRequests.remove(first);
        }
    }

    public void onOutgoingCommandPacket(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet == null || PackHideState.isActive()) return;
        String simple = packet.getClass().getSimpleName();
        String command = null;
        if ("ServerboundChatCommandPacket".equals(simple) || "ServerboundChatCommandSignedPacket".equals(simple)) {
            command = stringValue(packet, "command", "getCommand");
        } else if ("ServerboundChatPacket".equals(simple)) {
            String message = stringValue(packet, "message", "getMessage");
            if (message != null && message.trim().startsWith("/")) {
                command = message.trim().substring(1);
            }
        }
        if (command == null || command.isBlank()) return;
        String root = normalizeCommandToken(command);
        if (!isTrackableCommandToken(root)) return;
        registerRecentCommand(root, command);
        mergeLiveCommandEvidence(command, PluginEvidence.USER_AUTOCOMPLETE, null);
    }

    public void onOpenScreenPacket(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet == null || PackHideState.isActive()) return;
        if (!"ClientboundOpenScreenPacket".equals(packet.getClass().getSimpleName())) return;
        String title = componentText(invokeFirstNoArg(packet, "getTitle", "title"));
        if (title == null || title.isBlank()) return;

        ObservedCommand command = findRecentCommand(System.currentTimeMillis());
        if (command != null) {
            mergeLiveCommandEvidence(command.fullCommand, PluginEvidence.COMMAND_GUI, title);
            return;
        }

        String feature = featureLabelFromGuiTitle(title);
        if (feature == null || feature.isBlank()) return;
        mergeLiveGuiFeature(feature, title);
    }

    private void registerRecentCommand(String root, String fullCommand) {
        if (root == null || root.isBlank()) return;
        recentCommands.addLast(new ObservedCommand(root, fullCommand == null ? root : fullCommand, System.currentTimeMillis()));
        while (recentCommands.size() > MAX_RECENT_COMMANDS) recentCommands.removeFirst();
    }

    private ObservedCommand findRecentCommand(long now) {
        ObservedCommand best = null;
        while (!recentCommands.isEmpty() && now - recentCommands.peekFirst().timestampMs > COMMAND_GUI_LINK_MS) {
            recentCommands.removeFirst();
        }
        for (ObservedCommand command : recentCommands) {
            if (command == null) continue;
            if (now - command.timestampMs <= COMMAND_GUI_LINK_MS) best = command;
        }
        return best;
    }

    private void mergeLiveCommandEvidence(String rawCommand, PluginEvidence evidence, String guiTitle) {
        if (rawCommand == null || rawCommand.isBlank()) return;
        MC.execute(() -> {
            if (MC.getConnection() == null) return;
            syncScanStateForCurrentServer();
            registerRecentCommand(normalizeCommandToken(rawCommand), rawCommand);
            Map<String, PluginScanEntry> entries = pluginScanInProgress
                ? new TreeMap<>(scanWorkingEntries)
                : buildCurrentPluginEntries();
            boolean changed = mergeCommandEvidence(entries, rawCommand, evidence);
            if (guiTitle != null && !guiTitle.isBlank()) {
                changed |= attachGuiToCommandEntry(entries, rawCommand, guiTitle);
            }
            if (!changed) return;
            applyLivePluginEntries(entries);
        });
    }

    private void mergeLiveGuiFeature(String feature, String guiTitle) {
        MC.execute(() -> {
            if (MC.getConnection() == null) return;
            syncScanStateForCurrentServer();
            Map<String, PluginScanEntry> entries = pluginScanInProgress
                ? new TreeMap<>(scanWorkingEntries)
                : buildCurrentPluginEntries();
            String key = "gui_" + normalizePluginKey(feature);
            boolean changed = mergePluginEntry(entries, key, feature, List.of(), List.of(), List.of(guiTitle), false,
                PluginEvidence.FEATURE, PluginConfidence.FEATURE, PluginResultKind.FEATURE);
            if (!changed) return;
            applyLivePluginEntries(entries);
        });
    }

    private boolean attachGuiToCommandEntry(Map<String, PluginScanEntry> entries, String rawCommand, String guiTitle) {
        String token = normalizeCommandToken(rawCommand);
        if (token.isBlank() || guiTitle == null || guiTitle.isBlank()) return false;
        boolean changed = false;
        for (Map.Entry<String, PluginScanEntry> mapEntry : entries.entrySet()) {
            PluginScanEntry entry = mapEntry.getValue();
            if (entry == null || !entry.commands.contains(token)) continue;
            int before = entry.guis.size();
            entry.guis.add(guiTitle.trim());
            entry.evidence = mergeEvidence(entry.evidence, PluginEvidence.COMMAND_GUI);
            entry.confidence = mergeConfidence(entry.confidence, PluginConfidence.FEATURE);
            pluginEvidence.put(mapEntry.getKey(), mergeEvidence(pluginEvidence.get(mapEntry.getKey()), PluginEvidence.COMMAND_GUI));
            pluginConfidence.put(mapEntry.getKey(), mergeConfidence(pluginConfidence.get(mapEntry.getKey()), PluginConfidence.FEATURE));
            changed |= before != entry.guis.size();
        }
        return changed;
    }

    private void applyLivePluginEntries(Map<String, PluginScanEntry> entries) {
        mergePayloadFingerprintEntries(entries);
        if (pluginScanInProgress) {
            scanWorkingEntries.clear();
            scanWorkingEntries.putAll(entries);
            pluginScanLastResponseAt = System.currentTimeMillis();
            invalidatePluginRows();
            return;
        }

        scannedServerAddress = currentServerAddress();
        scannedPluginContextSignature = currentPluginContextSignature();
        pluginScanDone = true;
        pluginScanInProgress = false;
        applyPluginEntries(entries, false);
        cacheCurrentScan();
    }

    private String featureLabelFromGuiTitle(String title) {
        if (title == null || title.isBlank()) return null;
        String clean = title.toLowerCase(Locale.ROOT).replaceAll("\u00a7.", "");
        for (Map.Entry<String, String> entry : COMMAND_FEATURE_LABELS.entrySet()) {
            String key = entry.getKey();
            if (key.length() >= 3 && clean.contains(key)) return entry.getValue();
        }
        return null;
    }

    public void onCommandSuggestions(int id, net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket packet) {
        PluginProbeSpec probe = pluginProbes.get(id);
        PluginProbeSpec observed = observedSuggestionRequests.remove(id);
        boolean scanProbe = probe != null || pendingPluginProbeIds.contains(id);
        if (!scanProbe && observed == null) return;

        MC.execute(() -> {
            boolean liveObservation = !scanProbe && observed != null;
            if ((scannedServerAddress == null || scannedServerAddress.isBlank()) && !liveObservation) return;
            String currentAddress = currentServerAddress();
            String currentContextSignature = currentPluginContextSignature();
            if (!liveObservation && !currentAddress.equals(scannedServerAddress)) return;
            if (!liveObservation && !currentContextSignature.isEmpty() && !currentContextSignature.equals(scannedPluginContextSignature)) {
                syncScanStateForCurrentServer();
                return;
            }

            var list = packet.suggestions();
            Map<String, PluginScanEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            if (pluginScanInProgress) entries.putAll(scanWorkingEntries);
            else entries.putAll(buildCurrentPluginEntries());
            if (scanProbe) {
                pendingPluginProbeIds.remove(id);
                pluginScanLastResponseAt = System.currentTimeMillis();
            }

            for (var s : list) {
                String text = s.text();
                String normalizedToken = normalizeCommandToken(text);
                if (!normalizedToken.isEmpty()) addObservedPluginCommand(normalizedToken);

                if (text.contains(":")) {
                    mergeCommandEvidence(entries, text, scanProbe ? PluginEvidence.COMMAND_TREE : PluginEvidence.USER_AUTOCOMPLETE);
                    continue;
                }

                if (probe != null) {
                    if (probe.kind == PluginProbeKind.NAMESPACE && probe.hint != null && !normalizedToken.isEmpty()) {
                        mergePluginEntry(entries, probe.hint, List.of(normalizedToken), true, PluginEvidence.NAMESPACE);
                    } else if ((probe.kind == PluginProbeKind.PLUGIN_LIST || probe.kind == PluginProbeKind.VERSION)
                        && text != null) {
                        String pluginCandidate = text.trim();
                        String pluginKey = normalizePluginKey(pluginCandidate);
                        if (isLikelyPluginNameCandidate(pluginCandidate, probe.kind)
                            && !ROOT_COMMAND_PLUGIN_ALIASES.containsKey(pluginKey)) {
                            mergePluginEntry(entries, pluginCandidate, List.of(), false,
                                probe.kind == PluginProbeKind.PLUGIN_LIST ? PluginEvidence.PLUGIN_LIST : PluginEvidence.VERSION_HINT);
                        }
                    } else if (probe.kind == PluginProbeKind.HELP && probe.hint != null && !normalizedToken.isEmpty()) {
                        mergePluginEntry(entries, probe.hint, List.of(normalizedToken), true, PluginEvidence.HELP_HINT);
                    }
                } else if (observed != null && !normalizedToken.isEmpty()) {
                    mergeCommandEvidence(entries, normalizedToken, PluginEvidence.USER_AUTOCOMPLETE);
                } else if (!normalizedToken.isEmpty()) {
                    mergeCommandEvidence(entries, normalizedToken, PluginEvidence.SCANNER_AUTOCOMPLETE);
                }
            }

            inferPluginsFromObservedCommands(entries);
            if (scanProbe || pluginScanInProgress) {
                scanWorkingEntries.clear();
                scanWorkingEntries.putAll(entries);
                updatePluginScanLifecycle();
            } else {
                applyLivePluginEntries(entries);
            }
        });
    }

    public void onCommandTreeChanged() {
        invalidatePluginContextSignature();
        MC.execute(() -> {
            String currentAddress = currentServerAddress();
            if (currentAddress.isEmpty()) return;

            String currentContextSignature = currentPluginContextSignature();
            if (currentAddress.equals(scannedServerAddress) && currentContextSignature.equals(scannedPluginContextSignature)) {
                return;
            }

            syncScanStateForCurrentServer();
        });
    }

    public void onPayloadFingerprintUpdated() {
        lastPayloadFingerprintRevision = -1L;
        invalidatePluginRows();
        invalidatePluginContextSignature();
    }

    public void openInfoTab() {
        setVisible(true);
        selectTab(0);
    }

    public void openPluginsTab() {
        setVisible(true);
        selectTab(1);
    }

    public void toggle() { setVisible(!visible); }

    @Override
    public void setVisible(boolean v) {
        this.visible = v;
        if (v) {
            windowNode.syncShowBody(!collapsed);
            AutismOverlayManager.get().bringToFront(this);
            syncScanStateForCurrentServer();
            if (activeTab == 0) {
                applyInfoLayout();
            } else {
                if (pluginScanInProgress) applyPluginScanningLayout();
                else if (!pluginScanDone) applyPluginSetupLayout();
                else applyPluginResultsLayout();
            }
        }
        saveState();
    }

    @Override public boolean isVisible() { return visible; }
    @Override public boolean isCollapsed() { return collapsed; }
    @Override public void setCollapsed(boolean c) {
        if (collapsed == c) return;
        this.collapsed = c;
        windowNode.syncShowBody(!collapsed);
        if (c) clearHiddenInteractionState();
        saveState();
    }
    @Override public String getOverlayId() { return OVERLAY_ID; }

    @Override
    public boolean isMouseOver(double mx, double my) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiX = viewport.toUiX(mx);
        float uiY = viewport.toUiY(my);
        return uiX >= panelX && uiX <= panelX + panelW && uiY >= panelY && uiY <= panelY + panelH;
    }

    @Override
    public boolean isOverDragBar(double mx, double my) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        return isOverHeaderUi(viewport.toUiX(mx), viewport.toUiY(my));
    }

    @Override
    public boolean hasTextFieldFocused() {
        return surface.hasFocusedTextInput();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, panelW, panelH, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout b) {
        if (b == null) return;
        AutismWindowLayout clamped = clampToViewport(b);
        panelX = clamped.x; panelY = clamped.y; panelW = clamped.width; panelH = clamped.height;
        visible = clamped.visible; collapsed = clamped.collapsed;
        windowNode.syncShowBody(!collapsed);
        rememberCurrentTabSize();
    }

    @Override
    public int getMinWidth() {
        return getSharedPanelWidth();
    }

    @Override
    public int getMinHeight() {
        if (activeTab == 1) {
            if (pluginScanInProgress) return getPluginScanningPanelHeight();
            if (!pluginScanDone) return getPluginSetupPanelHeight();
            return getPluginResultsMinHeight();
        }
        return measurePreferredInfoPanelHeight();
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (!visible) return;

        syncScanStateForCurrentServer();
        syncPayloadFingerprintsIntoCurrentScan();
        updatePluginScanLifecycle();
        long nowMs = System.currentTimeMillis();
        if (uiRebuildRequested || pluginScanInProgress && nowMs - lastUiRebuildMs >= 100L) {
            clickRegions.clear();
            rebuildUi();
            lastUiRebuildMs = nowMs;
        }

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mx);
        float uiMouseY = viewport.toUiY(my);
        boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
        boolean headerHovered = isOverHeaderUi(uiMouseX, uiMouseY);
        DirectRenderContext metrics = new DirectRenderContext(ctx, textRenderer, viewport, theme, uiMouseX, uiMouseY, delta);

        windowNode.setShowBody(!collapsed);
        windowNode.setActive(active);
        windowNode.setHeaderHovered(headerHovered);

        int preferredHeight = Math.round(windowNode.preferredHeight(metrics, panelW));
        if (collapsed) {
            panelH = preferredHeight;
        } else {
            if (activeTab == 0) {
                panelH = getSharedPanelHeight();
            } else if (pluginScanInProgress) {
                panelH = getPluginScanningPanelHeight();
            } else if (!pluginScanDone) {
                panelH = getPluginSetupPanelHeight();
            } else {
                panelH = Math.max(getPluginResultsMinHeight(), Math.min(getPluginResultsMaxHeight(), getSharedPanelHeight()));
            }
        }
        AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(panelX, panelY, panelW, panelH, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelW = clamped.width;
        panelH = clamped.height;

        windowNode.setBounds(panelX, panelY, panelW, panelH);
        surface.render(ctx, mx, my, delta);

        if (!collapsed && activeTab == 1 && pluginScanDone && !pluginScanInProgress) {
            clickRegions.clear();
            renderPluginResultsViewport(ctx, viewport, uiMouseX, uiMouseY, delta);
        }
    }

    private String getReportedVersion(ServerData entry) {
        return entry != null && entry.version != null ? entry.version.getString() : "--";
    }

    private String getRealServerVersion() {
        String version = AutismSharedState.get().getRealServerVersion(getDisplayedServerAddress());
        if (version != null && !version.isBlank()) return version;
        String brand = getLiveBrand();
        if (brand != null && !brand.isBlank() && !"--".equals(brand)) {
            String extracted = extractVersionFromBrand(brand);
            if (extracted != null) return extracted;
        }
        return "--";
    }

    private String extractVersionFromBrand(String brand) {
        if (brand == null || brand.isBlank()) return null;
        String lower = brand.toLowerCase(Locale.ROOT);
        int dashIdx = lower.indexOf('-');
        if (dashIdx >= 0 && dashIdx + 1 < brand.length()) {
            String afterDash = brand.substring(dashIdx + 1);
            int spaceIdx = afterDash.indexOf(' ');
            if (spaceIdx >= 0) {
                return afterDash.substring(0, spaceIdx);
            }
            return afterDash;
        }
        return null;
    }

    private void renderPluginResultsViewport(GuiGraphicsExtractor ctx, DirectViewport viewport, float uiMouseX, float uiMouseY, float delta) {
        int x = Math.round(pluginListSlot.x());
        int y = Math.round(pluginListSlot.y());
        int rowW = Math.round(pluginListSlot.width());
        int bodyBottom = panelY + panelH - 10;
        int rawViewH = Math.max(0, Math.min(Math.round(pluginListSlot.height()), bodyBottom - y));
        int viewH = getPluginlistViewportHeight(rawViewH);
        int listTop = y;
        int listBottom = listTop + viewH;
        if (rowW <= 0 || viewH <= 0) return;
        int clipLeft = x + 1;
        int clipTop = listTop + 1;
        int clipRight = x + rowW - 1;
        int clipBottom = listBottom - 1;
        int innerViewH = getPluginListInnerHeight(viewH);
        if (innerViewH <= 0) return;

        int rowContentW = Math.max(48, clipRight - clipLeft);
        List<PluginListRow> rows = getCachedPluginRows(searchField.text(), rowContentW);
        int estimatedContentHeight = estimatePluginContentHeight(rows);
        int maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
        int quantizedTarget = quantizeScrollOffset(pluginScrollState.targetOffset(), pluginListRowStep(), maxScroll);
        pluginScrollState.setTarget(quantizedTarget, maxScroll);
        int visualScroll = quantizeScrollOffset(pluginScrollState.tick(delta, maxScroll), pluginListRowStep(), maxScroll);
        pluginContentHeight = estimatedContentHeight;

        CompactScrollbar.Metrics scrollbarMetrics = getPluginScrollbarMetrics(x, listTop, rowW, viewH);
        int scrolledRowContentW = Math.max(48, (clipRight - clipLeft) - (scrollbarMetrics.hasScroll() ? 10 : 0));
        if (scrolledRowContentW != rowContentW) {
            rowContentW = scrolledRowContentW;
            rows = getCachedPluginRows(searchField.text(), rowContentW);
            estimatedContentHeight = estimatePluginContentHeight(rows);
            maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
            quantizedTarget = quantizeScrollOffset(pluginScrollState.targetOffset(), pluginListRowStep(), maxScroll);
            pluginScrollState.setTarget(quantizedTarget, maxScroll);
            visualScroll = quantizeScrollOffset(pluginScrollState.tick(0.0f, maxScroll), pluginListRowStep(), maxScroll);
            pluginContentHeight = estimatedContentHeight;
            scrollbarMetrics = getPluginScrollbarMetrics(x, listTop, rowW, viewH);
        }

        boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);

        viewport.push(ctx);
        try {
            CompactListRenderer.drawFrame(ctx, x, listTop, rowW, viewH, active);
            viewport.enableScissor(ctx, clipLeft, clipTop, clipRight, clipBottom);
            try {
                int startIndex = firstVisiblePluginRowIndex(rows, visualScroll);
                int cy = clipTop - visualScroll + pluginRowOffset(startIndex);
                for (int rowIndex = startIndex; rowIndex < rows.size(); rowIndex++) {
                    PluginListRow row = rows.get(rowIndex);
                    int rowY = cy;
                    int rowTop = rowY;
                    int rowBottom = rowY + row.height;
                    boolean rowVisible = rowBottom > clipTop && rowTop < clipBottom;

                    if (row.header()) {
                        if (rowVisible) {
                            CompactSurfaces.header(ctx, clipLeft, rowTop, clipRight - clipLeft, rowBottom - rowTop);
                            int headerTextY = UiSizing.alignTextY(rowY, row.height, theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
                            UiText.draw(ctx, textRenderer, row.title, theme.fontFor(UiTone.MUTED), 0xFFB79E9E, clipLeft + rowTextInset(), headerTextY, false);
                        }
                    } else if (row.type == PluginRowType.PLUGIN) {
                        boolean selected = isSamePlugin(row.plugin, selectedPlugin);
                        boolean hovered = rowVisible && uiMouseX >= clipLeft && uiMouseX < clipLeft + rowContentW && uiMouseY >= rowTop && uiMouseY < rowBottom;
                        if (rowVisible) {
                            CompactSurfaces.row(ctx, clipLeft, rowTop, rowContentW, rowBottom - rowTop, hovered, selected);
                        }

                        int rowTextY = UiSizing.alignTextY(rowY, row.height, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());
                        if (rowVisible) {
                            String arrow = selected ? "v" : ">";
                            int arrowColor = hovered || selected ? 0xFFF3ECE7 : 0xFFB79E9E;
                            UiText.draw(ctx, textRenderer, arrow, theme.fontFor(UiTone.BODY), arrowColor, clipLeft + 2, rowTextY, false);
                        }

                        boolean isAnticheat = row.plugin != null && ANTICHEATS.contains(row.plugin.toLowerCase(Locale.ROOT));
                        int nameColor = isAnticheat ? 0xFFFF5555 : getConfidenceColor(row.confidence, hovered);
                        String label = isAnticheat ? "! " + row.plugin : row.plugin;
                        int labelX = clipLeft + 13;
                        int labelMaxWidth = Math.max(40, rowContentW - 18);
                        if (row.countLabel != null && !row.countLabel.isBlank()) {
                            int cw = UiText.width(textRenderer, row.countLabel, theme.fontFor(UiTone.MUTED), 0xFFB79E9E);
                            int countX = clipLeft + rowContentW - cw - 4;
                            labelMaxWidth = Math.max(36, countX - labelX - 4);
                            int countTextY = UiSizing.alignTextY(rowY, row.height, theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
                            if (rowVisible) {
                                UiText.draw(ctx, textRenderer, row.countLabel, theme.fontFor(UiTone.MUTED), 0xFFB79E9E, countX, countTextY, false);
                            }
                        }
                        if (rowVisible) {
                            String displayLabel = UiText.trimToWidth(textRenderer, label, labelMaxWidth, theme.fontFor(UiTone.BODY), nameColor);
                            UiText.draw(ctx, textRenderer, displayLabel, theme.fontFor(UiTone.BODY), nameColor, labelX, rowTextY, false);
                            final String clickedPlugin = row.plugin;
                            int hitTop = Math.max(rowTop, clipTop);
                            int hitBottom = Math.min(rowBottom, clipBottom);
                            clickRegions.add(new ClickRegion(clipLeft, hitTop, rowContentW, Math.max(1, hitBottom - hitTop), () -> {
                                selectedPlugin = isSamePlugin(clickedPlugin, selectedPlugin) ? null : clickedPlugin;
                                invalidatePluginRowView();
                                saveState();
                            }));
                        }
                    } else {
                        boolean commandRow = row.type == PluginRowType.COMMAND;
                        boolean channelRow = row.type == PluginRowType.CHANNEL && row.actionCommand != null && !row.actionCommand.isBlank();
                        boolean clickableRow = commandRow || channelRow;
                        boolean hovered = clickableRow && rowVisible && uiMouseX >= clipLeft + 9 && uiMouseX < clipLeft + rowContentW - 6 && uiMouseY >= rowTop && uiMouseY < rowBottom;
                        if (rowVisible) {
                            int detailColor = pluginDetailColor(row);
                            CompactSurfaces.row(ctx, clipLeft + 5, rowTop, rowContentW - 9, rowBottom - rowTop, hovered, false);
                            CompactSurfaces.indicator(ctx, clipLeft + 5, rowTop, 1, rowBottom - rowTop, detailColor);
                            int textColor = hovered ? 0xFFF3ECE7 : detailColor;
                            String detailLabel = UiText.trimToWidth(textRenderer, row.label, Math.max(20, rowContentW - 20), theme.fontFor(UiTone.MUTED), textColor);
                            int detailTextY = UiSizing.alignTextY(rowY, row.height, theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
                            UiText.draw(ctx, textRenderer, detailLabel, theme.fontFor(UiTone.MUTED), textColor, clipLeft + 11, detailTextY, false);
                        }
                        if (rowVisible && clickableRow && row.actionCommand != null && !row.actionCommand.isBlank()) {
                            final String actionValue = row.actionCommand;
                            int hitTop = Math.max(rowTop, clipTop);
                            int hitBottom = Math.min(rowBottom, clipBottom);
                            clickRegions.add(new ClickRegion(clipLeft + 9, hitTop, Math.max(1, rowContentW - 15), Math.max(1, hitBottom - hitTop), () -> {
                                clearTextFieldFocus();
                                clickRegions.clear();
                                if (commandRow) {
                                    String fullCmd = actionValue.startsWith("/") ? actionValue : "/" + actionValue;
                                    AutismOverlayManager.get().setTemporarilyHidden(this, true);
                                    MC.setScreen(new ChatScreen(fullCmd, false));
                                } else {
                                    openPayloadSenderForChannel(actionValue);
                                }
                            }));
                        }
                    }

                    cy += row.height;
                    if (cy > clipBottom + pluginListRowStep()) break;
                }
            } finally {
                viewport.disableScissor(ctx);
            }
            CompactScrollbar.draw(ctx, scrollbarMetrics, scrollbarMetrics.contains(uiMouseX, uiMouseY), pluginScrollbarDragging);
        } finally {
            viewport.pop(ctx);
        }
    }

    private int pluginDetailColor(PluginListRow row) {
        if (row == null) return 0xFFB79E9E;
        return switch (row.type) {
            case COMMAND -> 0xFFE5D0D0;
            case GUI -> AutismColors.packetYellow();
            case CHANNEL -> channelDetailColor(row.label == null ? null : row.label.replaceFirst("^ch\\s+", ""));
            case WHY -> getConfidenceColor(row.confidence, false);
            case HEADER, PLUGIN -> 0xFFB79E9E;
        };
    }

    private CompactScrollbar.Metrics getPluginScrollbarMetrics(int x, int y, int width, int height) {
        int innerHeight = getPluginListInnerHeight(height);
        int maxScroll = Math.max(0, pluginContentHeight - innerHeight);
        return CompactScrollbar.compute(
            pluginContentHeight,
            Math.max(1, innerHeight),
            x + width - 5,
            y + 1,
            3,
            Math.max(1, innerHeight),
            quantizeScrollOffset(pluginScrollState.tick(0.0f, maxScroll), pluginListRowStep(), maxScroll)
        );
    }

    private String detailCountLabel(List<String> commands, List<String> guis, List<String> channels) {
        int commandCount = commands == null ? 0 : commands.size();
        int guiCount = guis == null ? 0 : guis.size();
        int observedChannelCount = 0;
        int probableChannelCount = 0;
        boolean noChannelObserved = false;
        if (channels != null) {
            for (String channel : channels) {
                if (channel == null || channel.isBlank()) continue;
                if (isNoChannelRow(channel)) noChannelObserved = true;
                else if (isProbableChannelRow(channel)) probableChannelCount++;
                else observedChannelCount++;
            }
        }
        int channelCount = observedChannelCount + probableChannelCount;
        String channelLabel;
        if (observedChannelCount > 0) channelLabel = observedChannelCount + " ch";
        else if (probableChannelCount > 0) channelLabel = probableChannelCount + "? ch";
        else if (noChannelObserved) channelLabel = "no ch";
        else channelLabel = "0 ch";

        List<String> parts = new ArrayList<>();
        if (commandCount > 0) parts.add(commandCount + "c");
        if (guiCount > 0) parts.add(guiCount + "g");
        if (channelCount > 0 || noChannelObserved) parts.add(channelLabel);
        if (!parts.isEmpty()) return String.join("/", parts);
        return channelLabel;
    }

    private String detailCountLabel(List<String> commands, List<String> channels) {
        return detailCountLabel(commands, List.of(), channels);
    }

    private List<String> getPluginChannelDetailRows(String plugin) {
        List<String> observed = getPluginChannels(plugin);
        if (observed != null && !observed.isEmpty()) return observed;

        List<String> probableNamespaces = AutismPluginPayloadFingerprints.probableNamespacesForPlugin(plugin);
        if (!probableNamespaces.isEmpty()) {
            List<String> rows = new ArrayList<>();
            for (String namespace : probableNamespaces) {
                if (namespace == null || namespace.isBlank()) continue;
                rows.add("? " + namespace.trim().toLowerCase(Locale.ROOT) + ":*");
            }
            rows = dedupeChannelRows(rows);
            if (!rows.isEmpty()) return rows;
        }

        return List.of("no channel");
    }

    private int channelDetailColor(String detail) {
        if (detail == null) return 0xFFB79E9E;
        String lower = detail.toLowerCase(Locale.ROOT);
        if (isNoChannelRow(lower)) return 0xFF8F8585;
        if (isProbableChannelRow(lower)) return 0xFF8F8585;
        if (lower.contains("[registered")) return AutismColors.packetGreen();
        if (lower.contains("[live")) return AutismColors.packetCyan();
        return AutismColors.packetCyan();
    }

    private String channelDetailLabel(String detail) {
        if (detail == null || detail.isBlank()) return "--";
        if (isNoChannelRow(detail)) return "no channel";
        String base = channelDetailBase(detail);
        if (base.isBlank()) return "--";
        return isProbableChannelRow(detail) ? "? " + base : base;
    }

    private List<String> dedupeChannelRows(Collection<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String row : source) {
            if (row == null || row.isBlank()) continue;
            String clean = row.trim();
            String key = channelDetailKey(clean);
            if (key.isBlank()) continue;
            String existing = unique.get(key);
            if (existing == null || channelRowRank(clean) > channelRowRank(existing)) {
                unique.put(key, clean);
            }
        }
        List<String> out = new ArrayList<>(unique.values());
        out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(channelDetailLabel(a), channelDetailLabel(b)));
        return out;
    }

    private String channelDetailKey(String detail) {
        if (isNoChannelRow(detail)) return "no-channel";
        return channelDetailBase(detail).toLowerCase(Locale.ROOT);
    }

    private String channelDetailBase(String detail) {
        if (detail == null) return "";
        String text = detail.trim();
        if (text.regionMatches(true, 0, "ch ", 0, 3)) text = text.substring(3).trim();
        if (text.startsWith("?")) text = text.substring(1).trim();
        int bracket = text.indexOf(" [");
        if (bracket >= 0) text = text.substring(0, bracket).trim();
        return text;
    }

    private String payloadChannelActionTarget(String detail) {
        if (detail == null || isNoChannelRow(detail)) return null;
        String base = channelDetailBase(detail);
        String channel = bestGuessPayloadChannel(base);
        return channel.isBlank() ? null : channel;
    }

    private String bestGuessPayloadChannel(String rawChannel) {
        if (rawChannel == null) return "";
        String channel = rawChannel.trim();
        if (channel.isBlank()) return "";
        String legacy = channel.replace(" ", "");
        if ("REGISTER".equalsIgnoreCase(legacy) || "minecraft:register".equalsIgnoreCase(legacy)) return "minecraft:register";
        if ("UNREGISTER".equalsIgnoreCase(legacy) || "minecraft:unregister".equalsIgnoreCase(legacy)) return "minecraft:unregister";
        if ("BungeeCord".equalsIgnoreCase(legacy) || "bungeecord".equalsIgnoreCase(legacy)) return "bungeecord:main";

        int wildcard = channel.indexOf('*');
        if (wildcard >= 0) {
            channel = channel.substring(0, wildcard);
            while (channel.endsWith(":") || channel.endsWith("/") || channel.endsWith(".")) {
                channel = channel.substring(0, channel.length() - 1);
            }
            if (channel.isBlank()) return "";
            if (!channel.contains(":")) channel = channel + ":main";
            else if (channel.endsWith(":")) channel = channel + "main";
        }

        channel = channel.toLowerCase(Locale.ROOT)
            .replace('\\', '/')
            .replaceAll("[^a-z0-9_./:-]", "_");
        int colon = channel.indexOf(':');
        if (colon < 0) channel = channel + ":main";
        else if (colon == 0) channel = "minecraft" + channel;
        else if (colon == channel.length() - 1) channel = channel + "main";
        try {
            AutismPayloadSupport.parseChannel(channel);
            return channel;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private void openPayloadSenderForChannel(String channel) {
        String target = channel == null ? "" : channel.trim();
        if (target.isBlank()) return;
        ActionEditorOverlay existing = ActionEditorOverlay.getSharedOverlayIfExists();
        if (existing != null && existing.updateOpenPayloadChannel(target)) return;

        PayloadAction action = new PayloadAction();
        action.channel = target;
        action.payloadDirection = "C2S";
        action.sourceDirection = "C2S";
        action.payloadPhase = "PLAY";
        action.sourceProtocol = "play";
        action.payloadProvenance = "serverInfoChannel";

        ActionEditorOverlay editor = ActionEditorOverlay.getSharedOverlay();
        editor.openStandalonePayloadEditor(action);
        AutismOverlayManager.get().bringToFront(editor);
    }

    private boolean isNoChannelRow(String detail) {
        if (detail == null) return false;
        String lower = detail.trim().toLowerCase(Locale.ROOT);
        return lower.equals("no channel") || lower.startsWith("no client payload");
    }

    private boolean isProbableChannelRow(String detail) {
        if (detail == null) return false;
        String lower = detail.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("? ") || lower.contains("not observed") || lower.contains("[probable");
    }

    private int channelRowRank(String detail) {
        if (detail == null) return 0;
        String lower = detail.toLowerCase(Locale.ROOT);
        if (lower.contains("[live")) return 4;
        if (lower.contains("[registered")) return 3;
        if (lower.contains("[embedded")) return 2;
        if (isProbableChannelRow(lower)) return 1;
        return isNoChannelRow(lower) ? 0 : 3;
    }

    private String getLiveBrand() {
        if (MC.getConnection() == null) return "--";
        String brand = MC.getConnection().serverBrand();
        return brand != null && !brand.isBlank() ? brand : "--";
    }

    private boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
        return uiMouseX >= panelX && uiMouseX < panelX + panelW
            && uiMouseY >= panelY && uiMouseY < panelY + theme.headerHeight();
    }

    private boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
        return OverlayTopBar.isOverClose(UiBounds.of(panelX, panelY, panelW, Math.max(theme.headerHeight(), panelH)),
            theme.headerHeight(), uiMouseX, uiMouseY);
    }

    private int panelPadding() {
        return 5;
    }

    private int contentGap() {
        return 3;
    }

    private int searchFieldHeight() {
        return 15;
    }

    private int actionButtonHeight() {
        return 15;
    }

    private int buttonRowGap() {
        return 2;
    }

    private int infoLabelWidth() {
        return 82;
    }

    private int pluginSetupLabelWidth() {
        return 46;
    }

    private int pluginScanLabelWidth() {
        return 40;
    }

    private int pluginSearchWidth() {
        return 128;
    }

    private int pluginSearchReserveWidth() {
        return 28;
    }

    private float pluginViewportMinHeight() {
        return 66.0f;
    }

    private int sharedPanelWidth() {
        return 204;
    }

    private int pluginSetupWidth() {
        return sharedPanelWidth();
    }

    private int pluginScanningWidth() {
        return sharedPanelWidth();
    }

    private int infoMinWidth() {
        return sharedPanelWidth();
    }

    private int infoMinHeight() {
        return 246;
    }

    private int pluginResultsMinWidth() {
        return sharedPanelWidth();
    }

    private int pluginResultsMinHeightPreset() {
        return 266;
    }

    private int pluginSetupHeight() {
        return 120;
    }

    private int pluginScanningHeight() {
        return 92;
    }

    private int viewportHeightMargin() {
        return 24;
    }

    private int pluginHeaderHeight() {
        return pluginListRowStep();
    }

    private int pluginRowHeight() {
        return 15;
    }

    private int pluginDetailRowHeight() {
        return pluginListRowStep();
    }

    private int pluginDetailPadding() {
        return 0;
    }

    private int headerControlSize() {
        return 12;
    }

    private int headerArrowWidth() {
        return 10;
    }

    private int headerArrowGap() {
        return 3;
    }

    private int rowTextInset() {
        return 3;
    }

    private int pluginListRowStep() {
        return pluginRowHeight();
    }

    private int getPluginlistViewportHeight(int rawHeight) {
        if (rawHeight <= 0) return 0;
        return Math.min(rawHeight, getPluginListInnerHeight(rawHeight) + 2);
    }

    private int getPluginListInnerHeight(int viewHeight) {
        return Math.max(0, alignViewportHeight(Math.max(0, viewHeight - 2), pluginListRowStep()));
    }

    private AutismWindowLayout clampToViewport(AutismWindowLayout bounds) {
        DirectViewport viewport = surface.viewport();
        int viewportWidth = Math.round(viewport.uiWidth());
        int viewportHeight = Math.round(viewport.uiHeight());
        int margin = 4;
        int availableWidth = Math.max(1, viewportWidth - margin * 2);
        int availableHeight = Math.max(theme.headerHeight(), viewportHeight - margin * 2);
        int minWidth = Math.min(getMinWidth(), availableWidth);
        int width = Math.max(minWidth, Math.min(bounds.width, availableWidth));
        int minHeight = bounds.collapsed ? theme.headerHeight() : Math.min(getMinHeight(), availableHeight);
        int height = Math.max(minHeight, Math.min(bounds.height, availableHeight));
        int renderedHeight = bounds.collapsed ? theme.headerHeight() : height;
        int x = Math.max(margin, Math.min(bounds.x, Math.max(margin, viewportWidth - margin - width)));
        int y = Math.max(margin, Math.min(bounds.y, Math.max(margin, viewportHeight - margin - renderedHeight)));
        return new AutismWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
    }

    private String getSoftwareGuess(ServerData entry) {
        LinkedHashSet<String> guesses = new LinkedHashSet<>();
        String brand = getLiveBrand().toLowerCase(Locale.ROOT);
        if (brand.contains("purpur")) guesses.add("Purpur");
        else if (brand.contains("pufferfish")) guesses.add("Pufferfish");
        else if (brand.contains("folia")) guesses.add("Folia");
        else if (brand.contains("paper")) guesses.add("Paper");
        else if (brand.contains("spigot")) guesses.add("Spigot");
        else if (brand.contains("craftbukkit") || brand.contains("bukkit")) guesses.add("Bukkit");
        else if (brand.contains("waterfall")) guesses.add("Waterfall");
        else if (brand.contains("velocity")) guesses.add("Velocity");
        else if (brand.contains("bungeecord") || brand.contains("bungee")) guesses.add("Bungee");

        if (detectedPlugins.stream().anyMatch(plugin -> "viaversion".equalsIgnoreCase(plugin) || "viabackwards".equalsIgnoreCase(plugin) || "viarewind".equalsIgnoreCase(plugin))) {
            guesses.add("ViaVersion");
        }
        if (detectedPlugins.stream().anyMatch(plugin -> "geysermc".equalsIgnoreCase(plugin) || "floodgate".equalsIgnoreCase(plugin))) {
            guesses.add("Bedrock Bridge");
        }

        return guesses.isEmpty() ? "--" : String.join(" + ", guesses);
    }

    private String getVersionNote(ServerData entry) {
        String brand = getLiveBrand();
        String reportedVersion = getReportedVersion(entry);
        String brandLower = brand.toLowerCase(Locale.ROOT);
        String versionLower = reportedVersion.toLowerCase(Locale.ROOT);
        if (entry != null && entry.protocol == 0) {
            return "Ping Spoof";
        }
        if (!"--".equals(brand)
            && (versionLower.contains("paper")
            || versionLower.contains("spigot")
            || versionLower.contains("purpur")
            || versionLower.contains("velocity")
            || versionLower.contains("bungee"))
            && ((brandLower.contains("paper") && !versionLower.contains("paper"))
            || (brandLower.contains("spigot") && !versionLower.contains("spigot"))
            || (brandLower.contains("purpur") && !versionLower.contains("purpur"))
            || (brandLower.contains("velocity") && !versionLower.contains("velocity"))
            || (brandLower.contains("bungee") && !versionLower.contains("bungee")))) {
            return "Brand Mismatch";
        }
        if (detectedPlugins.stream().anyMatch(plugin -> "viaversion".equalsIgnoreCase(plugin) || "viabackwards".equalsIgnoreCase(plugin) || "viarewind".equalsIgnoreCase(plugin) || "protocolsupport".equalsIgnoreCase(plugin))) {
            return "Protocol Bridge";
        }
        return "--";
    }

    private String getDisplayedServerAddress() {
        ServerData entry = MC.getCurrentServer();
        if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return entry.ip.trim();
        }
        if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
            SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                    host = inet.getAddress().getHostAddress();
                }
                if (host != null && !host.isBlank()) {
                    return host + ":" + inet.getPort();
                }
            } else if (address != null) {
                String raw = address.toString();
                if (raw != null && !raw.isBlank()) {
                    return raw.replaceFirst("^/", "").trim();
                }
            }
        }
        return "--";
    }

    private String extractLookupHost(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank() || "--".equals(rawAddress)) return "";
        String trimmed = rawAddress.trim();
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            return trimmed.substring(1, trimmed.indexOf(']'));
        }
        int colonCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == ':') colonCount++;
        }
        if (colonCount == 1 && trimmed.contains(":")) {
            return trimmed.substring(0, trimmed.lastIndexOf(':'));
        }
        return trimmed;
    }

    private String getLiveSocketIp() {
        if (MC.getConnection() == null || MC.getConnection().getConnection() == null) return "";
        SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            String hostAddress = inet.getAddress().getHostAddress();
            return hostAddress == null ? "" : hostAddress.trim();
        }
        return "";
    }

    private Integer extractPort(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank() || "--".equals(rawAddress)) return null;
        String trimmed = rawAddress.trim();
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int closing = trimmed.indexOf(']');
            if (closing >= 0 && closing + 1 < trimmed.length() && trimmed.charAt(closing + 1) == ':') {
                try {
                    return Integer.parseInt(trimmed.substring(closing + 2));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        int firstColon = trimmed.indexOf(':');
        int lastColon = trimmed.lastIndexOf(':');
        if (firstColon >= 0 && firstColon == lastColon) {
            try {
                return Integer.parseInt(trimmed.substring(lastColon + 1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String appendPortIfPresent(String host, Integer port) {
        if (host == null || host.isBlank() || port == null) return host;
        if (host.indexOf(':') >= 0 && !host.startsWith("[") && !host.endsWith("]")) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    private Object invokeFirstNoArg(Object target, String... names) {
        if (target == null || names == null) return null;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private String stringValue(Object target, String... names) {
        Object value = invokeFirstNoArg(target, names);
        if (value == null) return "";
        if (value instanceof Component component) return component.getString();
        return String.valueOf(value);
    }

    private int intValue(Object target, int fallback, String... names) {
        Object value = invokeFirstNoArg(target, names);
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private String componentText(Object value) {
        if (value instanceof Component component) return component.getString();
        return value == null ? "" : String.valueOf(value);
    }

    private void copyClipboardValue(String value, String successMessage, String unavailableMessage) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || "--".equals(trimmed) || "Resolving...".equalsIgnoreCase(trimmed) || "Failed".equalsIgnoreCase(trimmed)) {
            AutismNotifications.error(unavailableMessage);
            return;
        }
        MC.keyboardHandler.setClipboard(trimmed);
        AutismNotifications.copied(successMessage);
    }

    private void copyResolvedServerIp() {
        String displayedAddress = getDisplayedServerAddress();
        String realIp = getDisplayedRealIp(displayedAddress);
        String resolvedWithPort = appendPortIfPresent(realIp, extractPort(displayedAddress));
        copyClipboardValue(resolvedWithPort, "Real IP copied.", "Real IP unavailable.");
    }

    private void ensureResolvedIpLookup(String rawAddress) {
        String host = extractLookupHost(rawAddress);
        if (host.isEmpty()) return;
        if (!host.equals(lastResolvedAddress)) {
            resolvedIp = null;
            lastResolvedAddress = host;
            resolvingIp = false;
        }
        if (resolvedIp == null && !resolvingIp) {
            resolvingIp = true;
            Thread t = new Thread(() -> {
                try { resolvedIp = java.net.InetAddress.getByName(host).getHostAddress(); }
                catch (Exception e) { resolvedIp = "Failed"; }
                finally { resolvingIp = false; }
            }, "Autism-DNS");
            t.setDaemon(true);
            t.start();
        }
    }

    private String getDisplayedRealIp(String rawAddress) {
        ensureResolvedIpLookup(rawAddress);
        String socketIp = getLiveSocketIp();
        if (!socketIp.isBlank()) return socketIp;
        if (resolvedIp != null) return resolvedIp;
        if (resolvingIp) return "Resolving...";
        return "--";
    }

    private List<String> getDetectedAnticheats() {
        return detectedPlugins.stream()
            .filter(Objects::nonNull)
            .filter(p -> ANTICHEATS.contains(p.toLowerCase(Locale.ROOT)))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
    }

    private String getCurrentWorldName() {
        if (MC.level == null) return "--";

        net.minecraft.resources.Identifier worldId = MC.level.dimension().identifier();
        if (worldId == null) return "--";

        String namespace = worldId.getNamespace();
        String path = worldId.getPath();
        if ("minecraft".equals(namespace)) {
            return switch (path) {
                case "overworld" -> "Overworld";
                case "the_nether" -> "The Nether";
                case "the_end" -> "The End";
                default -> path;
            };
        }

        return namespace + ":" + path;
    }

    private List<PluginListRow> buildPluginRows(List<PluginDetail> filteredPlugins) {
        Map<String, List<PluginListRow>> grouped = new LinkedHashMap<>();
        for (String title : List.of("Exact Plugins", "Strong Matches", "Detected Features", "Weak Hints", "Other")) {
            grouped.put(title, new ArrayList<>());
        }

        for (PluginDetail detail : filteredPlugins) {
            if (detail == null || detail.displayName.isBlank()) continue;
            String plugin = detail.displayName;
            String groupTitle = pluginGroupTitle(detail.resultKind, detail.confidence);

            grouped.computeIfAbsent(groupTitle, unused -> new ArrayList<>()).add(
                PluginListRow.plugin(plugin, detail.resultKind, detail.evidence, detail.confidence, pluginRowHeight(), detail.countLabel, detail.searchText)
            );

            if (isSamePlugin(plugin, selectedPlugin)) {
                for (String command : detail.commands) {
                    grouped.get(groupTitle).add(PluginListRow.detail(PluginRowType.COMMAND, plugin, "cmd /" + command, "/" + command,
                        detail.evidence, detail.confidence, pluginDetailRowHeight(), lowerSearch(plugin, command, detail.sourceLabel)));
                }
                for (String gui : detail.guis) {
                    grouped.get(groupTitle).add(PluginListRow.detail(PluginRowType.GUI, plugin, "gui " + gui, null,
                        detail.evidence, detail.confidence, pluginDetailRowHeight(), lowerSearch(plugin, gui, detail.sourceLabel)));
                }
                List<String> channels = detail.channels.isEmpty() ? getPluginChannelDetailRows(plugin) : detail.channels;
                for (String channel : channels) {
                    String label = channelDetailLabel(channel);
                    grouped.get(groupTitle).add(PluginListRow.detail(PluginRowType.CHANNEL, plugin, "ch " + label, payloadChannelActionTarget(channel),
                        detail.evidence, detail.confidence, pluginDetailRowHeight(), lowerSearch(plugin, label, detail.sourceLabel)));
                }
            }
        }

        List<PluginListRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<PluginListRow>> group : grouped.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            group.getValue().sort((a, b) -> {
                int byPlugin = String.CASE_INSENSITIVE_ORDER.compare(
                    a.plugin == null ? "" : a.plugin,
                    b.plugin == null ? "" : b.plugin
                );
                if (byPlugin != 0) return byPlugin;
                return Integer.compare(pluginRowTypeOrder(a.type), pluginRowTypeOrder(b.type));
            });
            rows.add(PluginListRow.header(group.getKey(), pluginHeaderHeight()));
            rows.addAll(group.getValue());
        }
        return rows;
    }

    private int pluginRowTypeOrder(PluginRowType type) {
        return switch (type == null ? PluginRowType.WHY : type) {
            case HEADER -> 0;
            case PLUGIN -> 1;
            case COMMAND -> 2;
            case GUI -> 3;
            case CHANNEL -> 4;
            case WHY -> 5;
        };
    }

    private String pluginGroupTitle(PluginResultKind resultKind, PluginConfidence confidence) {
        if (resultKind == PluginResultKind.FEATURE) return "Detected Features";
        return switch (confidence == null ? PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> "Exact Plugins";
            case STRONG -> "Strong Matches";
            case FEATURE -> "Weak Hints";
            case UNKNOWN -> "Other";
        };
    }

    private String sourceLabelFor(PluginResultKind resultKind, PluginEvidence evidence, PluginConfidence confidence) {
        if (resultKind == PluginResultKind.FEATURE) return "feature command";
        return switch (evidence == null ? PluginEvidence.UNKNOWN : evidence) {
            case PAYLOAD_CHANNEL -> "payload channel";
            case PLUGIN_LIST -> "plugin list";
            case VERSION_HINT -> "version hint";
            case COMMAND_TREE -> "command tree";
            case NAMESPACE -> "namespace";
            case ROOT_HINT -> "root hint";
            case COMMAND_GUI -> "command and gui";
            case USER_AUTOCOMPLETE -> "user autocomplete";
            case SCANNER_AUTOCOMPLETE -> "probe autocomplete";
            case HELP_HINT -> "help hint";
            case FEATURE -> "feature command";
            case UNKNOWN -> confidence == PluginConfidence.UNKNOWN ? "unknown" : "weak hint";
        };
    }

    private String pluginSearchBlob(String plugin, List<String> commands, List<String> guis, List<String> channels,
                                    String sourceLabel, PluginConfidence confidence) {
        StringBuilder sb = new StringBuilder();
        sb.append(plugin == null ? "" : plugin).append(' ')
            .append(confidenceLabel(confidence)).append(' ')
            .append(sourceLabel == null ? "" : sourceLabel);
        for (String command : commands) sb.append(' ').append(command);
        for (String gui : guis) sb.append(' ').append(gui);
        for (String channel : channels) sb.append(' ').append(channelDetailLabel(channel));
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private String lowerSearch(String... parts) {
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (String part : parts) {
                if (part == null || part.isBlank()) continue;
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(part);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private List<String> dedupePluginNames(Collection<String> plugins) {
        if (plugins == null || plugins.isEmpty()) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String plugin : plugins) {
            if (plugin == null || plugin.isBlank()) continue;
            String clean = plugin.trim();
            String key = normalizePluginKey(clean);
            if (key.isBlank()) continue;
            String existing = unique.get(key);
            if (existing == null || isMoreReadablePluginName(clean, existing)) {
                unique.put(key, clean);
            }
        }
        List<String> out = new ArrayList<>(unique.values());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private boolean isSamePlugin(String a, String b) {
        if (a == null || b == null) return false;
        return normalizePluginKey(a).equals(normalizePluginKey(b));
    }

    private boolean hasDetectedPlugin(String plugin) {
        if (plugin == null || plugin.isBlank()) return false;
        String key = normalizePluginKey(plugin);
        for (String detected : detectedPlugins) {
            if (detected != null && normalizePluginKey(detected).equals(key)) return true;
        }
        return false;
    }

    private Collection<PluginDetail> getCachedPluginDetails() {
        if (cachedPluginDetailsRevision == pluginDataRevision) return cachedPluginDetails.values();

        Map<String, PluginDetailBuilder> builders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String plugin : dedupePluginNames(detectedPlugins)) {
            String key = normalizePluginKey(plugin);
            if (key.isBlank()) continue;
            PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new PluginDetailBuilder());
            if (builder.displayName == null || builder.displayName.isBlank() || isMoreReadablePluginName(plugin, builder.displayName)) {
                builder.displayName = plugin.trim();
            }
        }

        mergePluginDetailCommands(builders, pluginCommands);
        mergePluginDetailGuis(builders, pluginGuis);
        mergePluginDetailChannels(builders, pluginChannels);

        Map<String, PluginDetail> details = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, PluginDetailBuilder> entry : builders.entrySet()) {
            String key = entry.getKey();
            PluginDetailBuilder builder = entry.getValue();
            if (builder == null) continue;
            String displayName = builder.displayName == null || builder.displayName.isBlank() ? key : builder.displayName.trim();
            List<String> commands = List.copyOf(builder.commands);
            List<String> guis = List.copyOf(builder.guis);
            List<String> channels = dedupeChannelRows(builder.channels);
            PluginEvidence evidence = pluginEvidence.getOrDefault(key, commands.isEmpty() ? PluginEvidence.UNKNOWN : PluginEvidence.ROOT_HINT);
            PluginConfidence confidence = pluginConfidence.getOrDefault(key, confidenceForEvidence(evidence));
            PluginResultKind resultKind = resultKindForStoredPlugin(displayName, evidence, confidence);
            String sourceLabel = sourceLabelFor(resultKind, evidence, confidence);
            String countLabel = detailCountLabel(commands, guis, channels);
            String searchText = pluginSearchBlob(displayName, commands, guis, channels, sourceLabel, confidence);
            details.put(key, new PluginDetail(displayName, key, commands, guis, channels, resultKind, evidence, confidence, sourceLabel, countLabel, searchText));
        }
        cachedPluginDetails = Collections.unmodifiableMap(new TreeMap<>(details));
        cachedPluginDetailsRevision = pluginDataRevision;
        return cachedPluginDetails.values();
    }

    private PluginDetail getPluginDetail(String plugin) {
        if (plugin == null || plugin.isBlank()) return null;
        getCachedPluginDetails();
        return cachedPluginDetails.get(normalizePluginKey(plugin));
    }

    private void mergePluginDetailCommands(Map<String, PluginDetailBuilder> builders, Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = normalizePluginKey(entry.getKey());
            if (key.isBlank()) continue;
            PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new PluginDetailBuilder());
            if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) builder.displayName = entry.getKey().trim();
            if (entry.getValue() == null) continue;
            for (String command : entry.getValue()) {
                if (command != null && !command.isBlank()) builder.commands.add(command.trim());
            }
        }
    }

    private void mergePluginDetailGuis(Map<String, PluginDetailBuilder> builders, Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = normalizePluginKey(entry.getKey());
            if (key.isBlank()) continue;
            PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new PluginDetailBuilder());
            if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) builder.displayName = entry.getKey().trim();
            if (entry.getValue() == null) continue;
            for (String gui : entry.getValue()) {
                if (gui != null && !gui.isBlank()) builder.guis.add(gui.trim());
            }
        }
    }

    private void mergePluginDetailChannels(Map<String, PluginDetailBuilder> builders, Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = normalizePluginKey(entry.getKey());
            if (key.isBlank()) continue;
            PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new PluginDetailBuilder());
            if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) builder.displayName = entry.getKey().trim();
            if (entry.getValue() != null) builder.channels.addAll(entry.getValue());
        }
    }

    private List<String> getPluginCommands(String plugin) {
        PluginDetail detail = getPluginDetail(plugin);
        return detail == null ? List.of() : detail.commands;
    }

    private List<String> getPluginGuis(String plugin) {
        PluginDetail detail = getPluginDetail(plugin);
        return detail == null ? List.of() : detail.guis;
    }

    private List<String> getPluginChannels(String plugin) {
        PluginDetail detail = getPluginDetail(plugin);
        return detail == null ? List.of() : detail.channels;
    }

    private List<PluginListRow> getCachedPluginRows(String queryText, int rowContentWidth) {
        String query = queryText == null ? "" : queryText.trim().toLowerCase(Locale.ROOT);
        String selectedKey = selectedPlugin == null ? "" : normalizePluginKey(selectedPlugin);
        if (cachedPluginRowsRevision == pluginRowsRevision
            && Objects.equals(cachedPluginRowsQuery, query)
            && Objects.equals(cachedPluginRowsSelectedKey, selectedKey)
            && cachedPluginRowsWidth == rowContentWidth) {
            return cachedPluginRows;
        }
        List<PluginDetail> filtered = new ArrayList<>();
        for (PluginDetail detail : getCachedPluginDetails()) {
            if (detail != null && (query.isEmpty() || detail.searchText.contains(query))) {
                filtered.add(detail);
            }
        }
        setCachedPluginRows(buildPluginRows(filtered));
        cachedPluginRowsQuery = query;
        cachedPluginRowsSelectedKey = selectedKey;
        cachedPluginRowsWidth = rowContentWidth;
        cachedPluginRowsRevision = pluginRowsRevision;
        return cachedPluginRows;
    }

    private boolean pluginDetailsMatchQuery(String plugin, String query) {
        if (plugin == null || query == null || query.isBlank()) return false;
        PluginDetail detail = getPluginDetail(plugin);
        return detail != null && detail.searchText.contains(query);
    }

    private void invalidatePluginRows() {
        pluginDataRevision++;
        pluginRowsRevision++;
        cachedPluginRowsRevision = -1;
        cachedPluginRowsQuery = null;
        cachedPluginRowsSelectedKey = null;
        cachedPluginRowsWidth = -1;
        cachedPluginRows = List.of();
        cachedPluginRowOffsets = new int[0];
        cachedPluginRowsHeight = 0;
        cachedPluginDetailsRevision = -1;
        cachedPluginDetails = Map.of();
        requestUiRebuild();
    }

    private void invalidatePluginRowView() {
        pluginRowsRevision++;
        cachedPluginRowsRevision = -1;
        cachedPluginRowsQuery = null;
        cachedPluginRowsSelectedKey = null;
        cachedPluginRowsWidth = -1;
        cachedPluginRows = List.of();
        cachedPluginRowOffsets = new int[0];
        cachedPluginRowsHeight = 0;
        requestUiRebuild();
    }

    private int estimatePluginContentHeight(List<PluginListRow> rows) {
        if (rows == cachedPluginRows) return cachedPluginRowsHeight;
        int total = 0;
        for (PluginListRow row : rows) {
            if (row != null) total += row.height;
        }
        return total;
    }

    private void setCachedPluginRows(List<PluginListRow> rows) {
        List<PluginListRow> safeRows = rows == null ? List.of() : List.copyOf(rows);
        int[] offsets = new int[safeRows.size()];
        int cursor = 0;
        for (int i = 0; i < safeRows.size(); i++) {
            offsets[i] = cursor;
            PluginListRow row = safeRows.get(i);
            cursor += row == null ? 0 : row.height;
        }
        cachedPluginRows = safeRows;
        cachedPluginRowOffsets = offsets;
        cachedPluginRowsHeight = cursor;
    }

    private int pluginRowOffset(int index) {
        if (index < 0 || index >= cachedPluginRowOffsets.length) return 0;
        return cachedPluginRowOffsets[index];
    }

    private int firstVisiblePluginRowIndex(List<PluginListRow> rows, int scrollOffset) {
        if (rows == null || rows.isEmpty() || cachedPluginRowOffsets.length == 0 || scrollOffset <= 0) return 0;
        int idx = Arrays.binarySearch(cachedPluginRowOffsets, scrollOffset);
        if (idx < 0) idx = Math.max(0, -idx - 2);
        while (idx > 0) {
            PluginListRow row = rows.get(idx);
            int rowBottom = cachedPluginRowOffsets[idx] + (row == null ? 0 : row.height);
            if (rowBottom <= scrollOffset) break;
            idx--;
        }
        while (idx < rows.size()) {
            PluginListRow row = rows.get(idx);
            int rowBottom = cachedPluginRowOffsets[idx] + (row == null ? 0 : row.height);
            if (rowBottom > scrollOffset) break;
            idx++;
        }
        return Math.max(0, Math.min(idx, rows.size() - 1));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mx);
        float uiMouseY = viewport.toUiY(my);

        if (isOverCloseButton(uiMouseX, uiMouseY)) {
            setVisible(false);
            isDragging = false;
            dragMoved = false;
            return true;
        }
        if (button == 0 && isOverHeaderUi(uiMouseX, uiMouseY)) {
            isDragging = true;
            dragMoved = false;
            dragOffsetX = uiMouseX - panelX;
            dragOffsetY = uiMouseY - panelY;
            pressStartUiX = uiMouseX;
            pressStartUiY = uiMouseY;
            pressStartPanelX = panelX;
            pressStartPanelY = panelY;
            return true;
        }

        if (collapsed) return false;
        if (button != 0) return isMouseOver(mx, my);

        if (activeTab == 1 && pluginScanDone && !pluginScanInProgress) {
            CompactScrollbar.Metrics scrollbarMetrics = getPluginScrollbarMetrics(
                Math.round(pluginListSlot.x()),
                Math.round(pluginListSlot.y()),
                Math.round(pluginListSlot.width()),
                getPluginlistViewportHeight(Math.max(0, Math.min(Math.round(pluginListSlot.height()), panelY + panelH - 10 - Math.round(pluginListSlot.y()))))
            );
            if (scrollbarMetrics.hasScroll() && scrollbarMetrics.contains(uiMouseX, uiMouseY)) {
                pluginScrollbarDragging = true;
                pluginScrollbarGrabOffset = Math.max(0, Math.round(uiMouseY) - scrollbarMetrics.thumbY());
                pluginScrollState.setFromThumbStepped(scrollbarMetrics, Math.round(uiMouseY), pluginScrollbarGrabOffset, pluginListRowStep());
                saveState();
                return true;
            }
        }

        if (activeTab == 1 && pluginScanDone && !pluginScanInProgress) {
            for (ClickRegion region : clickRegions) {
                if (region.contains(uiMouseX, uiMouseY)) {
                    surface.clearFocusedTextInputs();
                    region.action.run();
                    return true;
                }
            }
        }

        if (surface.mouseClicked(mx, my, button)) {
            return true;
        }

        surface.clearFocusedTextInputs();

        return isMouseOver(mx, my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && pluginScrollbarDragging) {
            pluginScrollbarDragging = false;
            saveState();
            return true;
        }
        if (isDragging) {
            boolean shouldCollapse = !dragMoved;
            isDragging = false;
            if (shouldCollapse) {
                setCollapsed(!collapsed);
                if (!collapsed) {
                    if (activeTab == 0) applyInfoLayout();
                    else if (pluginScanInProgress) applyPluginScanningLayout();
                    else if (!pluginScanDone) applyPluginSetupLayout();
                    else applyPluginResultsLayout();
                }
            }
            saveState();
            return true;
        }
        return surface.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pluginScrollbarDragging && button == 0) {
            DirectViewport viewport = surface.viewport();
            float uiMouseY = viewport.toUiY(my);
            CompactScrollbar.Metrics scrollbarMetrics = getPluginScrollbarMetrics(
                Math.round(pluginListSlot.x()),
                Math.round(pluginListSlot.y()),
                Math.round(pluginListSlot.width()),
                getPluginlistViewportHeight(Math.max(0, Math.min(Math.round(pluginListSlot.height()), panelY + panelH - 10 - Math.round(pluginListSlot.y()))))
            );
            pluginScrollState.setFromThumbStepped(scrollbarMetrics, Math.round(uiMouseY), pluginScrollbarGrabOffset, pluginListRowStep());
            return true;
        }
        if (isDragging) {
            DirectViewport viewport = surface.viewport();
            float uiMouseX = viewport.toUiX(mx);
            float uiMouseY = viewport.toUiY(my);
            AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(
                Math.round(uiMouseX - (float) dragOffsetX),
                Math.round(uiMouseY - (float) dragOffsetY),
                panelW,
                panelH,
                visible,
                collapsed
            ));
            panelX = clamped.x;
            panelY = clamped.y;
            dragMoved = dragMoved
                || Math.abs(uiMouseX - pressStartUiX) >= HEADER_CLICK_DRAG_THRESHOLD
                || Math.abs(uiMouseY - pressStartUiY) >= HEADER_CLICK_DRAG_THRESHOLD
                || panelX != pressStartPanelX
                || panelY != pressStartPanelY;
            return true;
        }
        return surface.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || collapsed || !isMouseOver(mx, my)) return false;
        if (activeTab == 1 && pluginScanDone && !pluginScanInProgress) {
            int slotY = Math.round(pluginListSlot.y());
            int slotH = Math.round(pluginListSlot.height());
            int rawViewH = Math.max(0, Math.min(slotH, panelY + panelH - 10 - slotY));
            int viewH = getPluginlistViewportHeight(rawViewH);
            int maxScroll = Math.max(0, pluginContentHeight - getPluginListInnerHeight(viewH));
            pluginScrollState.nudge(amount, pluginListRowStep(), maxScroll);
            return true;
        }
        return surface.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return visible && !collapsed && surface.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return visible && !collapsed && surface.charTyped(chr, modifiers);
    }

    private void copyServerData() {
        StringBuilder sb = new StringBuilder();
        ServerData entry = MC.getCurrentServer();
        String ip = getDisplayedServerAddress();
        String realIp = getDisplayedRealIp(ip);
        List<String> detectedAcs = getDetectedAnticheats();
        String software = getSoftwareGuess(entry);
        String versionNote = getVersionNote(entry);
        sb.append("=== Server Info ===\n");
        sb.append("IP:         ").append(ip).append("\n");
        if (!realIp.equals("--")) {
            sb.append("Real IP:    ").append(realIp).append("\n");
        }
        sb.append("Version:    ").append(getReportedVersion(entry)).append("\n");
        sb.append("RealVersion: ").append(getRealServerVersion()).append("\n");
        sb.append("Brand:      ").append(getLiveBrand()).append("\n");
        if (!"--".equals(software)) {
            sb.append("Software:   ").append(software).append("\n");
        }
        if (MC.getConnection() != null) {
            sb.append("Players:    ").append(MC.getConnection().getListedOnlinePlayers().size());
            if (entry != null && entry.players != null) sb.append(" / ").append(entry.players.max());
            sb.append("\n");
        } else {
            sb.append("Players:    --\n");
        }
        sb.append("Protocol:   ").append(entry != null ? entry.protocol : "--").append("\n");
        if (!"--".equals(versionNote)) {
            sb.append("VersionNote: ").append(versionNote).append("\n");
        }
        sb.append("Difficulty: ").append(MC.level != null ? MC.level.getDifficulty().getDisplayName().getString() : "--").append("\n");
        sb.append("World:      ").append(getCurrentWorldName()).append("\n");
        if (MC.level != null) {
            long dayCount = MC.level.getOverworldClockTime() / 24000L;
            long timeOfDay = MC.level.getOverworldClockTime() % 24000L;
            int hours = (int) ((timeOfDay / 1000 + 6) % 24);
            int minutes = (int) ((timeOfDay % 1000) * 60 / 1000);
            sb.append("Time:       Day ").append(dayCount).append(" (").append(String.format("%02d:%02d", hours, minutes)).append(")\n");
        } else {
            sb.append("Time:       --\n");
        }
        if (pluginScanInProgress) {
            sb.append("AntiCheats: Scanning...\n");
        } else if (!pluginScanDone) {
            sb.append("AntiCheats: Probe Plugins First\n");
        } else if (detectedAcs.isEmpty()) {
            sb.append("AntiCheats: None detected\n");
        } else {
            sb.append("AntiCheats: ").append(String.join(", ", detectedAcs)).append("\n");
        }
        MC.keyboardHandler.setClipboard(sb.toString());
        AutismNotifications.copied("Server info copied.");
    }

    private void copyPluginList() {
        if (detectedPlugins.isEmpty()) {
            AutismNotifications.error("No plugins detected.");
            return;
        }
        List<String> copyPlugins = new ArrayList<>();
        for (String plugin : dedupePluginNames(detectedPlugins)) {
            if (plugin == null || plugin.isBlank()) continue;
            PluginEvidence evidence = pluginEvidence.getOrDefault(normalizePluginKey(plugin),
                getPluginCommands(plugin).isEmpty() ? PluginEvidence.UNKNOWN : PluginEvidence.ROOT_HINT);
            if (evidence == PluginEvidence.HELP_HINT) continue;
            copyPlugins.add(plugin);
        }
        if (copyPlugins.isEmpty()) {
            AutismNotifications.error("No non-hint plugins to copy.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Plugins (").append(copyPlugins.size()).append("):\n");
        List<PluginDetail> copyDetails = new ArrayList<>();
        for (String plugin : copyPlugins) {
            PluginDetail detail = getPluginDetail(plugin);
            if (detail != null) copyDetails.add(detail);
        }
        List<PluginListRow> rows = buildPluginRows(copyDetails);
        for (PluginListRow row : rows) {
            if (row.header()) {
                sb.append("\n[").append(row.title).append("]\n");
                continue;
            }
            if (row.type != PluginRowType.PLUGIN || row.plugin == null || row.plugin.isBlank()) continue;
            boolean ac = ANTICHEATS.contains(row.plugin.toLowerCase(Locale.ROOT));
            List<String> commands = getPluginCommands(row.plugin);
            List<String> guis = getPluginGuis(row.plugin);
            List<String> channels = getPluginChannelDetailRows(row.plugin);
            PluginConfidence confidence = pluginConfidence.getOrDefault(normalizePluginKey(row.plugin), confidenceForEvidence(row.evidence));
            sb.append(ac ? "[AC] " : "- ").append(row.plugin);
            sb.append(" [").append(confidenceLabel(confidence)).append("]");
            sb.append(" [").append(sourceLabelFor(row.resultKind, row.evidence, confidence).replace("why ", "")).append("]");
            if (!commands.isEmpty() || !guis.isEmpty() || !channels.isEmpty()) {
                sb.append(" (").append(detailCountLabel(commands, guis, channels)).append(")");
            }
            sb.append("\n");
            for (String command : commands) {
                sb.append("    [cmd] /").append(command).append("\n");
            }
            for (String gui : guis) {
                sb.append("    [gui] ").append(gui).append("\n");
            }
            for (String channel : channels) {
                sb.append("    [ch] ").append(channelDetailLabel(channel)).append("\n");
            }
        }
        MC.keyboardHandler.setClipboard(sb.toString());
        AutismNotifications.copied("Plugin list copied.");
    }
}
