package ampere.commands;

import ampere.AmpereClientAddon;
import ampere.api.AddonRegistrationResult;
import ampere.modules.PackHideState;
import ampere.util.AmpereMessaging;
import ampere.util.AmpereCompatManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class AmpereCommands {
    private static final CommandDispatcher<AmpereCommandSource> DISPATCHER = new CommandDispatcher<>();
    private static final List<Command> ALL = new ArrayList<>();
    private static final Map<String, Command> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, String> ADDON_COMMAND_OWNERS = new LinkedHashMap<>();
    private static final Map<Command, String> ADDON_COMMAND_OBJECT_OWNERS = new IdentityHashMap<>();
    private static final Set<String> DISABLED_ADDON_COMMAND_NAMES = new HashSet<>();
    private static boolean initialized = false;

    private AmpereCommands() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        register(new ampere.commands.impl.ToggleCommand());
        register(new ampere.commands.impl.MacroCommand());
        register(new ampere.commands.impl.DelayCommand());
        register(new ampere.commands.impl.SendCommand());
        register(new ampere.commands.impl.VClipCommand());
        register(new ampere.commands.impl.HClipCommand());
        register(new ampere.commands.impl.NbtCommand());
        register(new ampere.commands.impl.ServerCommand());
        register(new ampere.commands.impl.PluginsCommand());
        register(new ampere.commands.impl.PrefixCommand());
        register(new ampere.commands.impl.CommandsCommand());
        register(new ampere.commands.impl.HelpCommand());
        register(new ampere.commands.impl.ModulesCommand());
        register(new ampere.commands.impl.BindsCommand());
        register(new ampere.commands.impl.BindCommand());
        register(new ampere.commands.impl.DismountCommand());
        register(new ampere.commands.impl.DisconnectCommand());
        register(new ampere.commands.impl.SayCommand());
        register(new ampere.commands.impl.DropCommand());
        register(new ampere.commands.impl.GamemodeCommand());
        register(new ampere.commands.impl.GiveCommand());
        register(new ampere.commands.impl.DamageCommand());
        register(new ampere.commands.impl.XCarryCommand());
    }

    private static void register(Command command) {
        registerWithName(command, command.name(), true);
    }

    private static List<String> registerWithName(Command command, String primaryName, boolean registerAliases) {
        List<String> registeredNames = new ArrayList<>();
        ALL.add(command);
        LiteralArgumentBuilder<AmpereCommandSource> primary = LiteralArgumentBuilder.literal(primaryName);
        command.build(primary);
        DISPATCHER.register(primary);
        BY_NAME.put(primaryName.toLowerCase(Locale.ROOT), command);
        DISABLED_ADDON_COMMAND_NAMES.remove(primaryName.toLowerCase(Locale.ROOT));
        registeredNames.add(primaryName.toLowerCase(Locale.ROOT));

        if (!registerAliases) return registeredNames;
        for (String alias : command.aliases()) {
            if (alias == null || alias.isBlank()) continue;
            if (BY_NAME.containsKey(alias.toLowerCase(Locale.ROOT))) continue;
            LiteralArgumentBuilder<AmpereCommandSource> aliasBuilder = LiteralArgumentBuilder.literal(alias);
            command.build(aliasBuilder);
            DISPATCHER.register(aliasBuilder);
            BY_NAME.put(alias.toLowerCase(Locale.ROOT), command);
            DISABLED_ADDON_COMMAND_NAMES.remove(alias.toLowerCase(Locale.ROOT));
            registeredNames.add(alias.toLowerCase(Locale.ROOT));
        }
        return registeredNames;
    }

    public static synchronized void registerAddonCommand(Command command, String addonId) {
        registerAddonCommandDetailed(command, addonId);
    }

    public static synchronized AddonRegistrationResult registerAddonCommandDetailed(Command command, String addonId) {
        if (command == null) return AddonRegistrationResult.rejected("command", "", "command was null");
        if (addonId == null || addonId.isBlank()) {
            return rejectAddonCommand(addonId, "", "registration outside an addon lifecycle");
        }
        String name = command.name();
        if (name == null || name.isBlank()) {
            return rejectAddonCommand(addonId, "", "blank command name");
        }
        List<String> registered;
        boolean collision = BY_NAME.containsKey(name.toLowerCase(Locale.ROOT));
        if (collision) {
            String namespaced = (addonId == null || addonId.isBlank() ? "addon" : addonId) + ":" + name;
            AmpereClientAddon.LOG.warn("[Commands] Command name '{}' from addon '{}' collides with an existing "
                    + "command; registering it as '{}' instead", name, addonId, namespaced);
            registered = registerWithName(command, namespaced, false);
        } else {
            registered = registerWithName(command, name, true);
        }
        ADDON_COMMAND_OBJECT_OWNERS.put(command, addonId);
        for (String registeredName : registered) {
            ADDON_COMMAND_OWNERS.put(registeredName, addonId);
        }
        String id = registered.isEmpty() ? name : registered.get(0);
        ampere.addons.AddonManager.recordAcceptedRegistration("command", id);
        return AddonRegistrationResult.accepted("command", id);
    }

    private static AddonRegistrationResult rejectAddonCommand(String addonId, String id, String reason) {
        AmpereClientAddon.LOG.warn("[Commands] Rejecting addon command '{}': {}", id, reason);
        ampere.addons.AddonManager.recordRejectedRegistration(addonId, "command", id, reason);
        return AddonRegistrationResult.rejected("command", id, reason);
    }

    public static synchronized void unregisterAddonCommands(String addonId) {
        if (addonId == null || addonId.isBlank()) return;
        List<String> removeNames = new ArrayList<>();
        for (Map.Entry<String, String> owner : ADDON_COMMAND_OWNERS.entrySet()) {
            if (addonId.equals(owner.getValue())) removeNames.add(owner.getKey());
        }
        for (String name : removeNames) {
            BY_NAME.remove(name);
            ADDON_COMMAND_OWNERS.remove(name);
            DISABLED_ADDON_COMMAND_NAMES.add(name);
        }
        ALL.removeIf(command -> addonId.equals(ADDON_COMMAND_OBJECT_OWNERS.get(command)));
        ADDON_COMMAND_OBJECT_OWNERS.entrySet().removeIf(entry -> addonId.equals(entry.getValue()));
    }

    public static CommandDispatcher<AmpereCommandSource> dispatcher() { return DISPATCHER; }

    public static List<Command> all() { return Collections.unmodifiableList(ALL); }

    public static Command find(String nameOrAlias) {
        if (nameOrAlias == null) return null;
        return BY_NAME.get(nameOrAlias.trim().toLowerCase(Locale.ROOT));
    }

    public static String effectivePrefix() { return AmpereCompatManager.effectiveCommandPrefix(); }

    public static boolean isAmpereCommandMessage(String message) {
        if (commandsBlockedByPanic()) return false;
        if (message == null || message.isBlank()) return false;
        String trimmed = message.trim();
        String prefix = effectivePrefix();
        return !prefix.isEmpty() && trimmed.startsWith(prefix);
    }

    public static boolean commandsBlockedByPanic() {
        return PackHideState.isActive();
    }

    public static boolean isBlockedPanicCommandMessage(String message) {
        return commandsBlockedByPanic() && isAmpereCommandMessage(message);
    }

    public static String commandBody(String message) {
        if (!isAmpereCommandMessage(message)) return "";
        String trimmed = message.trim();
        int prefixLength = effectivePrefix().length();
        return trimmed.length() <= prefixLength ? "" : trimmed.substring(prefixLength).trim();
    }

    public static boolean dispatch(String body) {
        if (commandsBlockedByPanic()) return true;
        if (body == null) return false;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return false;
        if (DISABLED_ADDON_COMMAND_NAMES.contains(firstToken(trimmed).toLowerCase(Locale.ROOT))) {
            sendSyntaxError(trimmed, CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create());
            return true;
        }
        try {
            DISPATCHER.execute(trimmed, AmpereCommandSource.INSTANCE);
            return true;
        } catch (CommandSyntaxException e) {
            sendSyntaxError(trimmed, e);
            return true;
        } catch (Exception e) {
            AmpereClientAddon.LOG.warn("[Commands] dispatch failed for '{}'", trimmed, e);
            AmpereMessaging.sendPrefixed("§cCommand error: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return true;
        }
    }

    private static void sendSyntaxError(String body, CommandSyntaxException e) {
        String first = firstToken(body);
        Command command = find(first);
        String prefix = effectivePrefix();
        if (command == null) {
            AmpereMessaging.sendPrefixed("§cUnknown Ampere command: §f" + first);
            AmpereMessaging.sendPrefixed("§7Use §f" + prefix + "commands §7or §f" + prefix + "help§7.");
            return;
        }

        String message = e.getMessage();
        if (message == null || message.isBlank()) message = "Incomplete or invalid command.";
        AmpereMessaging.sendPrefixed("§c" + message);
        AmpereMessaging.sendPrefixed("§7Use §f" + prefix + "help " + command.name() + "§7.");
    }

    private static String firstToken(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return "";
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
