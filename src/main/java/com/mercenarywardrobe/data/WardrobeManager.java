package com.mercenarywardrobe.data;

import com.mojang.blaze3d.platform.NativeImage;
import com.robertx22.mine_and_slash.database.data.mercenary.entity.MercenaryEntity;
import forge.net.mca.client.gui.immersive_library.SkinCache;
import forge.net.mca.client.resources.SkinPorter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class WardrobeManager {
    private static final Map<Integer, ResourceLocation> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Set<Integer> ATTEMPTED_SYNCS = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> DOWNLOADING_IDS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Map<String, Integer>> REMOTE_SKINS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Integer>> REMOTE_CLOTHES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Integer>> REMOTE_HAIR = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Boolean>> REMOTE_ARMOR = new ConcurrentHashMap<>();

    public static void clearTextureCache(int contentId) {
        ResourceLocation loc = TEXTURE_CACHE.remove(contentId);
        if (loc != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(loc);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clearAllTextureCache() {
        for (ResourceLocation loc : TEXTURE_CACHE.values()) {
            try {
                Minecraft.getInstance().getTextureManager().release(loc);
            } catch (Throwable ignored) {
            }
        }
        TEXTURE_CACHE.clear();
        ATTEMPTED_SYNCS.clear();
        DOWNLOADING_IDS.clear();
    }

    private static final Set<LivingEntity> PREVIEW_ENTITIES = Collections.newSetFromMap(new WeakHashMap<>());
    private static ResourceLocation EMPTY_TEXTURE = null;

    public static ResourceLocation getEmptyTexture() {
        if (EMPTY_TEXTURE == null) {
            NativeImage image = new NativeImage(64, 64, true);
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    image.setPixelRGBA(x, y, 0);
                }
            }
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation loc = new ResourceLocation("mercenary_wardrobe", "empty");
            Minecraft.getInstance().getTextureManager().register(loc, dynamicTexture);
            EMPTY_TEXTURE = loc;
        }
        return EMPTY_TEXTURE;
    }

    public static void registerPreviewEntity(LivingEntity entity) {
        if (entity != null) {
            PREVIEW_ENTITIES.add(entity);
        }
    }

    public static boolean isPreviewEntity(LivingEntity entity) {
        return entity != null && PREVIEW_ENTITIES.contains(entity);
    }

    private static final Map<LivingEntity, ResourceLocation> PREVIEW_OVERRIDES = new WeakHashMap<>();
    private static final Map<LivingEntity, ResourceLocation> PREVIEW_CLOTHES_OVERRIDES = new WeakHashMap<>();
    private static final Map<LivingEntity, ResourceLocation> PREVIEW_HAIR_OVERRIDES = new WeakHashMap<>();

    public static void setPreviewOverride(LivingEntity entity, ResourceLocation texture) {
        if (entity == null) return;
        if (texture == null) {
            PREVIEW_OVERRIDES.remove(entity);
        } else {
            PREVIEW_OVERRIDES.put(entity, texture);
        }
    }

    public static void setPreviewClothingOverride(LivingEntity entity, ResourceLocation texture) {
        if (entity == null) return;
        if (texture == null) {
            PREVIEW_CLOTHES_OVERRIDES.remove(entity);
        } else {
            PREVIEW_CLOTHES_OVERRIDES.put(entity, texture);
        }
    }

    public static void setPreviewHairOverride(LivingEntity entity, ResourceLocation texture) {
        if (entity == null) return;
        if (texture == null) {
            PREVIEW_HAIR_OVERRIDES.remove(entity);
        } else {
            PREVIEW_HAIR_OVERRIDES.put(entity, texture);
        }
    }

    public static boolean isSlimSkin(int contentId) {
        if (contentId <= 0) return false;
        File diskFile = WardrobeStorage.getInstance().getCacheFile(contentId);
        if (diskFile.exists()) {
            try (InputStream in = new FileInputStream(diskFile)) {
                NativeImage image = NativeImage.read(in);
                return SkinPorter.isSlimFormat(image);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static ResourceLocation getSkinTexture(int contentId, boolean slim) {
        if (contentId <= 0) return null;
        ResourceLocation cached = TEXTURE_CACHE.get(contentId);
        if (cached != null) return cached;

        File diskFile = WardrobeStorage.getInstance().getCacheFile(contentId);
        if (diskFile.exists()) {
            try (InputStream in = new FileInputStream(diskFile)) {
                NativeImage image = NativeImage.read(in);
                if (slim || SkinPorter.isSlimFormat(image)) {
                    SkinPorter.convertSlimToDefault(image);
                }
                DynamicTexture dynamicTexture = new DynamicTexture(image);
                ResourceLocation loc = new ResourceLocation("mercenary_wardrobe", "skins/" + contentId);
                Minecraft.getInstance().getTextureManager().register(loc, dynamicTexture);
                TEXTURE_CACHE.put(contentId, loc);
                return loc;
            } catch (Exception ignored) {
            }
        }

        if (!DOWNLOADING_IDS.contains(contentId)) {
            WardrobeSkinEntry target = null;
            for (WardrobeSkinEntry entry : WardrobeStorage.getInstance().getHistory()) {
                if (entry.contentId() == contentId) {
                    target = entry;
                    break;
                }
            }
            if (target == null) {
                target = new WardrobeSkinEntry(contentId, "", "", slim, 0, "skin");
            }
            triggerMissingDownload(target);
        }

        return null;
    }

    public static void ensureMissingCaches() {
        java.util.List<WardrobeSkinEntry> entries = new java.util.ArrayList<>(WardrobeStorage.getInstance().getHistory());
        for (WardrobeSkinEntry entry : entries) {
            int cid = entry.contentId();
            if (cid <= 0) continue;
            File file = WardrobeStorage.getInstance().getCacheFile(cid);
            if (!file.exists()) {
                triggerMissingDownload(entry);
            }
        }
    }

    public static void triggerMissingDownload(WardrobeSkinEntry entry) {
        if (entry == null) return;
        int cid = entry.contentId();
        if (cid <= 0 || !DOWNLOADING_IDS.add(cid)) return;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes;
                if (cid >= 1_000_000_000) {
                    bytes = fetchSkinBytes(entry.name());
                } else {
                    bytes = fetchMcaSkinBytes(cid);
                }
                if (bytes != null && bytes.length > 0) {
                    File diskFile = WardrobeStorage.getInstance().getCacheFile(cid);
                    java.nio.file.Files.write(diskFile.toPath(), bytes);
                    clearTextureCache(cid);
                    notifyGuiRefresh();
                }
            } catch (Throwable ignored) {
            } finally {
                DOWNLOADING_IDS.remove(cid);
            }
        });
    }

    public static byte[] fetchMcaSkinBytes(int contentId) {
        if (contentId <= 0) return null;
        File mcaFile = new File("./immersive_library/" + contentId + ".png");
        if (mcaFile.exists()) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(mcaFile.toPath());
                if (bytes.length > 4 && bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
                    return bytes;
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            java.net.URL url = new java.net.URL("https:" + "//mca.conczin.net/v1/content/mca/" + contentId);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Minecraft-MercenaryWardrobe/1.0.2");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String json;
                try (InputStream in = conn.getInputStream()) {
                    json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                var elem = com.google.gson.JsonParser.parseString(json);
                if (elem != null && elem.isJsonObject()) {
                    var contentObj = elem.getAsJsonObject().getAsJsonObject("content");
                    if (contentObj != null && contentObj.has("data")) {
                        String b64 = contentObj.get("data").getAsString();
                        byte[] bytes = java.util.Base64.getDecoder().decode(b64);
                        if (bytes.length > 4 && bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
                            try {
                                java.nio.file.Files.write(mcaFile.toPath(), bytes);
                            } catch (Throwable ignored) {
                            }
                            return bytes;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    public static byte[] fetchSkinBytes(String cleanName) {
        if (cleanName == null || cleanName.trim().isEmpty()) return null;
        try {
            java.net.URL url = new java.net.URL("https:" + "//minotar.net/skin/" + java.net.URLEncoder.encode(cleanName, java.nio.charset.StandardCharsets.UTF_8));
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Minecraft-MercenaryWardrobe/1.0.1");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] bytes = in.readAllBytes();
                    if (bytes.length > 4 && bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
                        return bytes;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            java.net.URL mojangUrl = new java.net.URL("https:" + "//api.mojang.com/users/profiles/minecraft/" + java.net.URLEncoder.encode(cleanName, java.nio.charset.StandardCharsets.UTF_8));
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) mojangUrl.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Minecraft-MercenaryWardrobe/1.0.1");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String json;
                try (InputStream in = conn.getInputStream()) {
                    json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                int idIdx = json.indexOf("\"id\"");
                if (idIdx != -1) {
                    int startQuote = json.indexOf('"', idIdx + 4);
                    if (startQuote != -1) {
                        int endQuote = json.indexOf('"', startQuote + 1);
                        if (endQuote != -1) {
                            String uuid = json.substring(startQuote + 1, endQuote);
                            java.net.URL crafatarUrl = new java.net.URL("https:" + "//crafatar.com/skins/" + uuid);
                            java.net.HttpURLConnection cConn = (java.net.HttpURLConnection) crafatarUrl.openConnection();
                            cConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Minecraft-MercenaryWardrobe/1.0.1");
                            cConn.setConnectTimeout(4000);
                            cConn.setReadTimeout(6000);
                            cConn.connect();
                            if (cConn.getResponseCode() == 200) {
                                try (InputStream cIn = cConn.getInputStream()) {
                                    byte[] bytes = cIn.readAllBytes();
                                    if (bytes.length > 4 && bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
                                        return bytes;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static void notifyGuiRefresh() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof com.mercenarywardrobe.client.gui.MercenarySkinHistoryScreen screen) {
                screen.refreshScreen();
            }
        });
    }

    public static ResourceLocation getMercenaryTexture(LivingEntity entity) {
        ResourceLocation override = PREVIEW_OVERRIDES.get(entity);
        if (override != null) return override;
        if (isPreviewEntity(entity)) return null;

        if (!(entity instanceof MercenaryEntity mercenary)) return null;
        String classId = mercenary.getClassId();
        if (classId == null) return null;
        classId = classId.toLowerCase(Locale.ROOT);

        UUID ownerUUID = mercenary.getOwnerUUID();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalOwner = (ownerUUID == null || (mc.player != null && ownerUUID.equals(mc.player.getUUID())));

        if (isLocalOwner) {
            WardrobeSkinEntry entry = WardrobeStorage.getInstance().getActiveSkin(classId);
            if (entry != null) {
                return getSkinTexture(entry.contentId(), entry.slim());
            }
            return null;
        }

        Map<String, Integer> playerSkins = REMOTE_SKINS.get(ownerUUID);
        if (playerSkins != null) {
            Integer contentId = playerSkins.get(classId);
            if (contentId != null && contentId > 0) {
                return getSkinTexture(contentId, false);
            }
        }
        return null;
    }

    public static ResourceLocation getMercenaryClothingTexture(LivingEntity entity) {
        ResourceLocation override = PREVIEW_CLOTHES_OVERRIDES.get(entity);
        if (override != null) return override;
        if (isPreviewEntity(entity)) return null;

        if (!(entity instanceof MercenaryEntity mercenary)) return null;
        String classId = mercenary.getClassId();
        if (classId == null) return null;
        classId = classId.toLowerCase(Locale.ROOT);

        UUID ownerUUID = mercenary.getOwnerUUID();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalOwner = (ownerUUID == null || (mc.player != null && ownerUUID.equals(mc.player.getUUID())));

        if (isLocalOwner) {
            WardrobeSkinEntry entry = WardrobeStorage.getInstance().getActiveClothing(classId);
            if (entry != null) {
                return getSkinTexture(entry.contentId(), entry.slim());
            }
            return null;
        }

        Map<String, Integer> playerClothes = REMOTE_CLOTHES.get(ownerUUID);
        if (playerClothes != null) {
            Integer contentId = playerClothes.get(classId);
            if (contentId != null && contentId > 0) {
                return getSkinTexture(contentId, false);
            }
        }
        return null;
    }

    public static ResourceLocation getMercenaryHairTexture(LivingEntity entity) {
        ResourceLocation override = PREVIEW_HAIR_OVERRIDES.get(entity);
        if (override != null) return override;
        if (isPreviewEntity(entity)) return null;

        if (!(entity instanceof MercenaryEntity mercenary)) return null;
        String classId = mercenary.getClassId();
        if (classId == null) return null;
        classId = classId.toLowerCase(Locale.ROOT);

        UUID ownerUUID = mercenary.getOwnerUUID();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalOwner = (ownerUUID == null || (mc.player != null && ownerUUID.equals(mc.player.getUUID())));

        if (isLocalOwner) {
            WardrobeSkinEntry entry = WardrobeStorage.getInstance().getActiveHair(classId);
            if (entry != null) {
                return getSkinTexture(entry.contentId(), entry.slim());
            }
            return null;
        }

        Map<String, Integer> playerHair = REMOTE_HAIR.get(ownerUUID);
        if (playerHair != null) {
            Integer contentId = playerHair.get(classId);
            if (contentId != null && contentId > 0) {
                return getSkinTexture(contentId, false);
            }
        }
        return null;
    }

    public static boolean shouldHideArmor(LivingEntity entity) {
        if (!(entity instanceof MercenaryEntity mercenary)) return false;
        String classId = mercenary.getClassId();
        if (classId == null) return false;
        classId = classId.toLowerCase(Locale.ROOT);

        UUID ownerUUID = mercenary.getOwnerUUID();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalOwner = (ownerUUID == null || (mc.player != null && ownerUUID.equals(mc.player.getUUID())));

        if (isLocalOwner) {
            return WardrobeStorage.getInstance().isArmorHidden(classId);
        }

        Map<String, Boolean> playerArmor = REMOTE_ARMOR.get(ownerUUID);
        if (playerArmor != null) {
            return playerArmor.getOrDefault(classId, false);
        }
        return false;
    }

    public static void setRemoteSkin(UUID ownerUUID, String classId, int contentId) {
        REMOTE_SKINS.computeIfAbsent(ownerUUID, k -> new ConcurrentHashMap<>())
                .put(classId.toLowerCase(Locale.ROOT), contentId);
    }

    public static void setRemoteClothing(UUID ownerUUID, String classId, int contentId) {
        REMOTE_CLOTHES.computeIfAbsent(ownerUUID, k -> new ConcurrentHashMap<>())
                .put(classId.toLowerCase(Locale.ROOT), contentId);
    }

    public static void setRemoteHair(UUID ownerUUID, String classId, int contentId) {
        REMOTE_HAIR.computeIfAbsent(ownerUUID, k -> new ConcurrentHashMap<>())
                .put(classId.toLowerCase(Locale.ROOT), contentId);
    }

    public static void setRemoteArmor(UUID ownerUUID, String classId, boolean hidden) {
        REMOTE_ARMOR.computeIfAbsent(ownerUUID, k -> new ConcurrentHashMap<>())
                .put(classId.toLowerCase(Locale.ROOT), hidden);
    }
}
