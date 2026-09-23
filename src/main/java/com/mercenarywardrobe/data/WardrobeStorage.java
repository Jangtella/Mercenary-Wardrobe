package com.mercenarywardrobe.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class WardrobeStorage {
    private static WardrobeStorage INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configDir;
    private final Path cacheDir;
    private final File historyFile;

    private Map<String, WorldData> worlds = new HashMap<>();

    public static class WorldData {
        public List<WardrobeSkinEntry> history = new ArrayList<>();
        public Map<String, WardrobeSkinEntry> activeSkins = new HashMap<>();
        public Map<String, WardrobeSkinEntry> activeClothes = new HashMap<>();
        public Map<String, WardrobeSkinEntry> activeHairs = new HashMap<>();
        public Map<String, Boolean> armorHidden = new HashMap<>();
    }

    public static synchronized WardrobeStorage getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WardrobeStorage();
        }
        return INSTANCE;
    }

    private WardrobeStorage() {
        this.configDir = FMLPaths.CONFIGDIR.get().resolve("mercenary_wardrobe");
        this.cacheDir = this.configDir.resolve("cache");
        this.historyFile = this.configDir.resolve("history.json").toFile();
        try {
            Files.createDirectories(this.cacheDir);
        } catch (IOException ignored) {
        }
        load();
    }

    public String getWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return "local_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        ServerData serverData = mc.getCurrentServer();
        if (serverData != null) {
            return "server_" + serverData.ip.replace(':', '_');
        }
        return "global";
    }

    public synchronized WorldData getCurrentWorldData() {
        WorldData data = worlds.computeIfAbsent(getWorldKey(), k -> new WorldData());
        if (data.history == null) data.history = new ArrayList<>();
        if (data.activeSkins == null) data.activeSkins = new HashMap<>();
        if (data.activeClothes == null) data.activeClothes = new HashMap<>();
        if (data.activeHairs == null) data.activeHairs = new HashMap<>();
        if (data.armorHidden == null) data.armorHidden = new HashMap<>();
        return data;
    }

    public synchronized List<WardrobeSkinEntry> getHistory() {
        return getCurrentWorldData().history;
    }

    public synchronized void addHistory(WardrobeSkinEntry entry) {
        WorldData data = getCurrentWorldData();
        data.history.removeIf(e -> e.contentId() == entry.contentId());
        data.history.add(0, entry);
        save();
    }

    public synchronized void removeHistory(int contentId) {
        WorldData data = getCurrentWorldData();
        data.history.removeIf(e -> e.contentId() == contentId);
        save();
    }

    public synchronized void updateType(int contentId, String newType) {
        WorldData data = getCurrentWorldData();
        for (int i = 0; i < data.history.size(); i++) {
            WardrobeSkinEntry e = data.history.get(i);
            if (e.contentId() == contentId) {
                WardrobeSkinEntry updated = e.withType(newType);
                data.history.set(i, updated);
                for (var entry : data.activeSkins.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().contentId() == contentId) {
                        entry.setValue(updated);
                    }
                }
                for (var entry : data.activeClothes.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().contentId() == contentId) {
                        entry.setValue(updated);
                    }
                }
                for (var entry : data.activeHairs.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().contentId() == contentId) {
                        entry.setValue(updated);
                    }
                }
                break;
            }
        }
        save();
    }

    public synchronized void clearHistory() {
        getCurrentWorldData().history.clear();
        save();
    }

    public synchronized WardrobeSkinEntry getActiveSkin(String classId) {
        return getCurrentWorldData().activeSkins.get(classId.toLowerCase(Locale.ROOT));
    }

    public synchronized void setActiveSkin(String classId, WardrobeSkinEntry entry) {
        String key = classId.toLowerCase(Locale.ROOT);
        if (entry == null) {
            getCurrentWorldData().activeSkins.remove(key);
        } else {
            getCurrentWorldData().activeSkins.put(key, entry);
            addHistory(entry);
        }
        save();
    }

    public synchronized WardrobeSkinEntry getActiveClothing(String classId) {
        return getCurrentWorldData().activeClothes.get(classId.toLowerCase(Locale.ROOT));
    }

    public synchronized void setActiveClothing(String classId, WardrobeSkinEntry entry) {
        String key = classId.toLowerCase(Locale.ROOT);
        if (entry == null) {
            getCurrentWorldData().activeClothes.remove(key);
        } else {
            getCurrentWorldData().activeClothes.put(key, entry);
            addHistory(entry);
        }
        save();
    }

    public synchronized WardrobeSkinEntry getActiveHair(String classId) {
        return getCurrentWorldData().activeHairs.get(classId.toLowerCase(Locale.ROOT));
    }

    public synchronized void setActiveHair(String classId, WardrobeSkinEntry entry) {
        String key = classId.toLowerCase(Locale.ROOT);
        if (entry == null) {
            getCurrentWorldData().activeHairs.remove(key);
        } else {
            getCurrentWorldData().activeHairs.put(key, entry);
            addHistory(entry);
        }
        save();
    }

    public synchronized boolean isArmorHidden(String classId) {
        return getCurrentWorldData().armorHidden.getOrDefault(classId.toLowerCase(Locale.ROOT), false);
    }

    public synchronized void setArmorHidden(String classId, boolean hidden) {
        getCurrentWorldData().armorHidden.put(classId.toLowerCase(Locale.ROOT), hidden);
        save();
    }

    public Path getCacheDir() {
        return cacheDir;
    }

    public File getCacheFile(int contentId) {
        return cacheDir.resolve("skin_" + contentId + ".png").toFile();
    }

    public long getCacheSizeBytes() {
        try {
            if (!Files.exists(cacheDir)) return 0;
            return Files.walk(cacheDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    public void clearAllCache() {
        try {
            if (Files.exists(cacheDir)) {
                Files.walk(cacheDir)
                        .filter(Files::isRegularFile)
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized void load() {
        if (!historyFile.exists()) return;
        try (FileReader reader = new FileReader(historyFile)) {
            Type type = new TypeToken<Map<String, WorldData>>() {}.getType();
            Map<String, WorldData> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                this.worlds = loaded;
                for (WorldData wd : this.worlds.values()) {
                    if (wd.history != null) {
                        for (int i = 0; i < wd.history.size(); i++) {
                            WardrobeSkinEntry e = wd.history.get(i);
                            if (e.type() == null || e.type().isEmpty()) {
                                String classified = classifyEntryFile(e.contentId());
                                wd.history.set(i, e.withType(classified != null ? classified : "skin"));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String classifyEntryFile(int contentId) {
        File f = getCacheFile(contentId);
        if (f.exists()) {
            return SkinClassifier.classify(f);
        }
        File libFile = new File("immersive_library", contentId + ".png");
        if (libFile.exists()) {
            return SkinClassifier.classify(libFile);
        }
        return null;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configDir);
            try (FileWriter writer = new FileWriter(historyFile)) {
                GSON.toJson(worlds, writer);
            }
        } catch (Exception ignored) {
        }
    }
}
