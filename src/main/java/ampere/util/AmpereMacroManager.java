package ampere.util;

import ampere.AmpereClientAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AmpereMacroManager {
    private static AmpereMacroManager INSTANCE;
    private List<AmpereMacro> macros = new ArrayList<>();
    private final File saveFile;
    private volatile long revision;

    private AmpereMacroManager() {
        saveFile = new File(AmpereClientAddon.FOLDER, "Ampere_macros.nbt");
        load();
    }

    public static synchronized AmpereMacroManager get() {
        if (INSTANCE == null) {
            INSTANCE = new AmpereMacroManager();
        }
        return INSTANCE;
    }

    public synchronized String createUniqueName(String preferredName) {
        String baseName = preferredName == null || preferredName.isBlank() ? "New Macro" : preferredName.trim();
        String candidate = baseName;
        int suffix = 1;
        while (get(candidate) != null) {
            candidate = baseName + " (" + suffix++ + ")";
        }
        return candidate;
    }

    public synchronized AmpereMacro addImportedCopy(AmpereMacro source, String preferredName) {
        if (source == null) return null;

        AmpereMacro copy = source.deepCopy();
        copy.name = createUniqueName(preferredName != null && !preferredName.isBlank() ? preferredName : source.name);
        add(copy);
        return copy;
    }

    public synchronized void add(AmpereMacro macro) {
        macros.add(macro);
        save();
    }

    public synchronized AmpereMacro get(String name) {
        for (AmpereMacro macro : macros) {
            if (macro.name.equalsIgnoreCase(name)) return macro;
        }
        return null;
    }

    public synchronized List<AmpereMacro> getAll() {
        return new ArrayList<>(macros);
    }

    public long getRevision() {
        return revision;
    }

    public synchronized void remove(AmpereMacro macro) {
        if (ampere.util.macro.MacroExecutor.isMacroRunning(macro.name)) {
            ampere.util.macro.MacroExecutor.stopMacro(macro.name);
            AmpereMessaging.sendPrefixed("§eStopped running macro before deletion: " + macro.name);
        }

        if (macros.remove(macro)) {
            save();
            AmpereMessaging.sendPrefixed("§aDeleted macro: " + macro.name);

        AmpereMacroEditorOverlay editor = AmpereMacroEditorOverlay.getSharedOverlay();
            if (editor != null && editor.isEditingMacro(macro)) {
                editor.close();
            }

            if (AmpereLANSync.getInstance().isInSession()) {
                AmpereLANSync.getInstance().broadcastMacroDeletion(macro.name);
            }
        }
    }

    public void delete(AmpereMacro macro) {
        remove(macro);
    }

    public void executeMacro(String name) {
        AmpereMacro macro = get(name);
        if (macro != null) {
            macro.execute();
            AmpereMessaging.sendPrefixed("§aExecuting macro: " + macro.name);
        } else {
            AmpereMessaging.sendPrefixed("§cMacro not found: " + name);
        }
    }

    public void stopMacro() {
        if (ampere.util.macro.MacroExecutor.isVisibleRunning()) {
            ampere.util.macro.MacroExecutor.stop();
        } else {
            AmpereMessaging.sendPrefixed("§eNo macro is currently running.");
        }
    }

    public synchronized void save() {
        revision++;
        try {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (AmpereMacro macro : macros) {
                list.add(macro.toTag());
            }
            tag.put("macros", list);
            NbtIo.write(tag, saveFile.toPath());
        } catch (Exception e) {
            AmpereClientAddon.LOG.error("Failed to save Ampere macros", e);
        }

        if (AmpereLANSync.getInstance().isInSession()) {
            AmpereLANSync.getInstance().broadcastMacroList();
        }
    }

    public synchronized void load() {
        if (!saveFile.exists()) return;

        try {
            CompoundTag tag = NbtIo.read(saveFile.toPath());
            if (tag != null && tag.contains("macros")) {
                macros.clear();
                ListTag list = (ListTag) tag.get("macros");
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof CompoundTag) {
                        AmpereMacro macro = new AmpereMacro();
                        macro.fromTag((CompoundTag) list.get(i));
                        macros.add(macro);
                    }
                }
            }
            revision++;
        } catch (Exception e) {
            AmpereClientAddon.LOG.error("Failed to load Ampere macros", e);
        }
    }
}
