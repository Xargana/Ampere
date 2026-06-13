package ampere.util;

import ampere.AmpereClientAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class AmpereAccountManager implements Iterable<AmpereAccount> {
    private static final AmpereAccountManager INSTANCE = new AmpereAccountManager();
    private final List<AmpereAccount> accounts = new ArrayList<>();
    private boolean loaded;

    private AmpereAccountManager() {
    }

    public static AmpereAccountManager get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    private File saveFile() {
        return new File(Minecraft.getInstance().gameDirectory, "Ampere-accounts.nbt");
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = saveFile();
        if (!file.exists()) return;
        try {
            CompoundTag tag = NbtIo.read(file.toPath());
            if (tag == null) return;
            accounts.clear();
            ListTag list = tag.getListOrEmpty("accounts");
            for (Tag element : list) {
                if (element instanceof CompoundTag compoundTag) accounts.add(new AmpereAccount().fromTag(compoundTag));
            }
        } catch (Exception e) {
            AmpereClientAddon.LOG.error("Failed to load Ampere accounts", e);
        }
    }

    public synchronized void save() {
        try {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (AmpereAccount account : accounts) list.add(account.toTag());
            tag.put("accounts", list);
            NbtIo.write(tag, saveFile().toPath());
        } catch (Exception e) {
            AmpereClientAddon.LOG.error("Failed to save Ampere accounts", e);
        }
    }

    public synchronized List<AmpereAccount> all() {
        return new ArrayList<>(accounts);
    }

    public synchronized void add(AmpereAccount account) {
        if (account == null) return;
        accounts.add(account);
        save();
    }

    public synchronized boolean contains(AmpereAccount account) {
        return accounts.contains(account);
    }

    public synchronized void remove(AmpereAccount account) {
        if (accounts.remove(account)) save();
    }

    public void login(AmpereAccount account) {
        if (account == null) return;
        Thread thread = new Thread(() -> {
            if (account.fetchInfo() && account.login()) {
                save();
                AmpereMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
            } else {
                AmpereMessaging.sendPrefixed("Failed to login account: " + account.displayName());
            }
        }, "Ampere-Account-Login");
        thread.setDaemon(true);
        thread.start();
    }

    public void loginMicrosoft(AmpereAccount account) {
        if (account == null || account.type != AmpereAccountType.Microsoft) return;
        AmpereMicrosoftLogin.getRefreshToken(refreshToken -> {
            if (refreshToken == null) {
                AmpereMessaging.sendPrefixed("Microsoft login cancelled or failed.");
                return;
            }
            account.label = refreshToken;
            Thread thread = new Thread(() -> {
                if (account.fetchInfo() && account.login()) {
                    synchronized (this) {
                        if (!accounts.contains(account)) accounts.add(account);
                    }
                    save();
                    AmpereMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
                } else {
                    AmpereMessaging.sendPrefixed("Failed to login Microsoft account.");
                }
            }, "Ampere-Microsoft-Login");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public Iterator<AmpereAccount> iterator() {
        return all().iterator();
    }
}
