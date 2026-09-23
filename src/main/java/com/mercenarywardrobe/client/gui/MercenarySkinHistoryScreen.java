package com.mercenarywardrobe.client.gui;

import com.mercenarywardrobe.data.WardrobeManager;
import com.mercenarywardrobe.data.WardrobeSkinEntry;
import com.mercenarywardrobe.data.WardrobeStorage;
import com.mercenarywardrobe.network.C2SToggleArmorPacket;
import com.mercenarywardrobe.network.C2SUpdateSkinPacket;
import com.mercenarywardrobe.network.WardrobeNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import com.robertx22.mine_and_slash.database.data.mercenary.ClientMercenary;
import com.robertx22.mine_and_slash.database.data.mercenary.entity.MercenaryEntity;
import com.robertx22.mine_and_slash.mmorpg.registers.common.SlashEntities;
import com.robertx22.mine_and_slash.uncommon.datasaving.Load;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import com.mercenarywardrobe.data.SkinClassifier;
import net.minecraft.client.gui.components.EditBox;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class MercenarySkinHistoryScreen extends Screen {
    private final Screen parentScreen;

    private static final ResourceLocation FIGHTER_ICON = new ResourceLocation("mmorpg", "textures/gui/mercenary/classes/fighter.png");
    private static final ResourceLocation ELEMENTALIST_ICON = new ResourceLocation("mmorpg", "textures/gui/mercenary/classes/elementalist.png");
    private static final ResourceLocation HUNTER_ICON = new ResourceLocation("mmorpg", "textures/gui/mercenary/classes/hunter.png");

    private String selectedClass = "fighter";
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 12;

    private final Map<String, MercenaryEntity> previewEntities = new HashMap<>();
    private final Map<Integer, MercenaryEntity> miniEntities = new HashMap<>();

    private boolean showDebugModal = false;
    private String currentFilter = "all";

    private int leftBoxX;
    private int leftBoxY;
    private int leftBoxW;
    private int leftBoxH;

    private int rightBoxX;
    private int rightBoxY;
    private int rightBoxW;
    private int rightBoxH;

    private float previewYaw = 0.0F;
    private float previewPitch = 0.0F;
    private boolean isDraggingPreview = false;

    private EditBox nicknameBox;
    private String savedNickname = "";

    private static final String[] REFRESH_ICON = {
        "                ",
        "     ######     ",
        "   ##WWWWWW###  ",
        "  #WW######WWW# ",
        " #WW#    #WWWW# ",
        " #WW#     ####  ",
        "#WW#       #WW# ",
        "#WW#       #WW# ",
        " #WW#     #WW#  ",
        "  ####    #WW#  ",
        " #WWWW#   #WW#  ",
        " #WWW#####WW#   ",
        "  ###WWWWWW##   ",
        "     ######     ",
        "                ",
        "                "
    };

    private static class IconButton extends Button {
        public IconButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }
    }

    public MercenarySkinHistoryScreen(Screen parentScreen) {
        this(parentScreen, "fighter");
    }

    public MercenarySkinHistoryScreen(Screen parentScreen, String defaultClass) {
        super(Component.literal("Mercenary Wardrobe"));
        this.parentScreen = parentScreen;
        if (defaultClass != null && !defaultClass.isEmpty()) {
            this.selectedClass = defaultClass.toLowerCase(Locale.ROOT);
        }
    }

    @Override
    protected void init() {
        super.init();
        initEntities();
        WardrobeManager.ensureMissingCaches();

        int dialogW = Math.min(width - 20, 580);
        int dialogH = Math.min(height - 20, 320);
        int startX = (width - dialogW) / 2;
        int startY = (height - dialogH) / 2;

        leftBoxW = 160;
        leftBoxH = dialogH - 40;
        leftBoxX = startX + 10;
        leftBoxY = startY + 30;

        rightBoxX = leftBoxX + leftBoxW + 10;
        rightBoxY = leftBoxY;
        rightBoxW = dialogW - leftBoxW - 30;
        rightBoxH = leftBoxH;

        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(startX + dialogW - 25, startY + 6, 18, 18)
                .build());

        addRenderableWidget(Button.builder(Component.literal("D"), b -> {
            showDebugModal = !showDebugModal;
            rebuildWidgets();
        }).bounds(startX + dialogW - 47, startY + 6, 18, 18)
                .tooltip(Tooltip.create(Component.literal("Debug Menu")))
                .build());

        boolean inGame = Minecraft.getInstance().level != null && Minecraft.getInstance().player != null;
        Button.Builder skinLibBuilder = Button.builder(Component.literal("Skin Library"), b -> {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().setScreen(new forge.net.mca.client.gui.SkinLibraryScreen());
            }
        }).bounds(startX + dialogW - 135, startY + 6, 84, 18);
        if (!inGame) {
            skinLibBuilder.tooltip(Tooltip.create(Component.literal("Available only in-game")));
        }
        Button skinLibBtn = skinLibBuilder.build();
        skinLibBtn.active = inGame;
        addRenderableWidget(skinLibBtn);

        if (showDebugModal) {
            initDebugWidgets(startX, startY, dialogW, dialogH);
            return;
        }

        int editBoxX = leftBoxX + 6;
        int editBoxY = leftBoxY + 6;
        int editBoxW = 68;
        int editBoxH = 16;
        nicknameBox = new EditBox(font, editBoxX, editBoxY, editBoxW, editBoxH, Component.empty());
        nicknameBox.setMaxLength(16);
        if (savedNickname != null && !savedNickname.isEmpty()) {
            nicknameBox.setValue(savedNickname);
        }
        nicknameBox.setResponder(val -> savedNickname = val);
        nicknameBox.setEditable(inGame);
        nicknameBox.active = inGame;
        addRenderableWidget(nicknameBox);

        int checkBtnX = editBoxX + editBoxW + 3;
        int checkBtnY = leftBoxY + 6;
        int checkBtnW = 16;
        int checkBtnH = 16;
        Button.Builder checkBtnBuilder = Button.builder(Component.literal("✓"), b -> {
            if (nicknameBox != null) {
                triggerNicknameFetch(nicknameBox.getValue());
            }
        }).bounds(checkBtnX, checkBtnY, checkBtnW, checkBtnH);
        if (inGame) {
            checkBtnBuilder.tooltip(Tooltip.create(Component.literal("Fetch & Apply Skin")));
        } else {
            checkBtnBuilder.tooltip(Tooltip.create(Component.literal("Available only in-game")));
        }
        Button checkBtn = checkBtnBuilder.build();
        checkBtn.active = inGame;
        addRenderableWidget(checkBtn);

        Button resetRotationBtn = new IconButton(leftBoxX + leftBoxW - 41, leftBoxY + 6, 16, 16, Component.empty(), b -> {
            previewYaw = 0.0F;
            previewPitch = 0.0F;
        }) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                int x = getX();
                int y = getY();
                int ironColor = isHovered ? 0xFFFFFFA0 : 0xFFFFFFFF;
                for (int r = 0; r < 16; r++) {
                    String row = REFRESH_ICON[r];
                    for (int c = 0; c < 16; c++) {
                        char ch = row.charAt(c);
                        if (ch == '#') {
                            graphics.fill(x + c, y + r, x + c + 1, y + r + 1, 0xFF000000);
                        } else if (ch == 'W') {
                            graphics.fill(x + c, y + r, x + c + 1, y + r + 1, ironColor);
                        }
                    }
                }
            }
        };
        resetRotationBtn.setTooltip(Tooltip.create(Component.translatable("mercenary_wardrobe.button.reset_rotation")));
        addRenderableWidget(resetRotationBtn);

        boolean armorHidden = WardrobeStorage.getInstance().isArmorHidden(selectedClass);
        Button armorToggleBtn = new IconButton(leftBoxX + leftBoxW - 22, leftBoxY + 6, 16, 16, Component.empty(), b -> {
            boolean nextState = !WardrobeStorage.getInstance().isArmorHidden(selectedClass);
            WardrobeStorage.getInstance().setArmorHidden(selectedClass, nextState);
            WardrobeNetwork.sendToServer(new C2SToggleArmorPacket(selectedClass, nextState));
            rebuildWidgets();
        }) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                boolean hidden = WardrobeStorage.getInstance().isArmorHidden(selectedClass);
                if (hidden) {
                    RenderSystem.setShaderColor(0.35f, 0.35f, 0.35f, 0.9f);
                    graphics.renderItem(new ItemStack(Items.IRON_CHESTPLATE), getX(), getY());
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    graphics.fill(getX(), getY(), getX() + 16, getY() + 16, 0x8818181C);
                } else {
                    graphics.renderItem(new ItemStack(Items.IRON_CHESTPLATE), getX(), getY());
                }
            }
        };
        armorToggleBtn.setTooltip(Tooltip.create(Component.literal(armorHidden ? "Armor: Hidden" : "Armor: Shown")));
        addRenderableWidget(armorToggleBtn);

        int classBtnY = leftBoxY + leftBoxH - 56;
        int classBtnSize = 22;
        int classBtnSpacing = 6;
        int totalClassW = (classBtnSize * 3) + (classBtnSpacing * 2);
        int classStartX = leftBoxX + (leftBoxW - totalClassW) / 2;

        String[] classes = {"fighter", "elementalist", "hunter"};
        ResourceLocation[] icons = {FIGHTER_ICON, ELEMENTALIST_ICON, HUNTER_ICON};
        for (int i = 0; i < classes.length; i++) {
            String cls = classes[i];
            ResourceLocation icon = icons[i];
            int btnX = classStartX + i * (classBtnSize + classBtnSpacing);
            Button btn = new IconButton(btnX, classBtnY, classBtnSize, classBtnSize, Component.empty(), b -> {
                selectedClass = cls;
                rebuildWidgets();
            }) {
                @Override
                public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    boolean isSelected = cls.equals(selectedClass);
                    graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xAA181820);
                    if (isSelected) {
                        graphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY(), 0xFFE5B036);
                        graphics.fill(getX() - 1, getY() + height, getX() + width + 1, getY() + height + 1, 0xFFE5B036);
                        graphics.fill(getX() - 1, getY(), getX(), getY() + height, 0xFFE5B036);
                        graphics.fill(getX() + width, getY(), getX() + width + 1, getY() + height, 0xFFE5B036);
                    } else if (isHovered) {
                        graphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY(), 0xFF666677);
                        graphics.fill(getX() - 1, getY() + height, getX() + width + 1, getY() + height + 1, 0xFF666677);
                        graphics.fill(getX() - 1, getY(), getX(), getY() + height, 0xFF666677);
                        graphics.fill(getX() + width, getY(), getX() + width + 1, getY() + height, 0xFF666677);
                    }
                    try {
                        graphics.blit(icon, getX() + 2, getY() + 2, 18, 18, 0.0f, 0.0f, 36, 36, 36, 36);
                    } catch (Throwable ignored) {
                    }
                }
            };
            btn.setTooltip(Tooltip.create(Component.literal(cls.substring(0, 1).toUpperCase(Locale.ROOT) + cls.substring(1))));
            addRenderableWidget(btn);
        }

        Button resetBtn = Button.builder(Component.literal("Reset to Default"), b -> {
            WardrobeStorage.getInstance().setActiveSkin(selectedClass, null);
            WardrobeStorage.getInstance().setActiveClothing(selectedClass, null);
            WardrobeStorage.getInstance().setActiveHair(selectedClass, null);
            WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, 0, false, 0));
            WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, 0, false, 1));
            WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, 0, false, 2));
            rebuildWidgets();
        }).bounds(leftBoxX + (leftBoxW - 120) / 2, leftBoxY + leftBoxH - 28, 120, 20)
                .build();
        resetBtn.active = (Minecraft.getInstance().level != null);
        addRenderableWidget(resetBtn);

        int filterY = rightBoxY + 3;
        int filterH = 15;
        String[] filters = {"all", "skins", "cloths", "hair"};
        String[] filterLabels = {"All", "Skins", "Cloths", "Hair"};
        int btnW = (rightBoxW - 8 - 12) / 4;
        for (int i = 0; i < filters.length; i++) {
            final String fKey = filters[i];
            int btnX = rightBoxX + 4 + i * (btnW + 4);
            Button filterBtn = new IconButton(btnX, filterY, btnW, filterH, Component.literal(filterLabels[i]), b -> {
                currentFilter = fKey;
                currentPage = 0;
                rebuildWidgets();
            }) {
                @Override
                public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    boolean selected = fKey.equals(currentFilter);
                    int bg = selected ? 0xFF353545 : (isHovered ? 0xFF282832 : 0xAA181820);
                    graphics.fill(getX(), getY(), getX() + width, getY() + height, bg);
                    int border = selected ? 0xFFE5B036 : (isHovered ? 0xFF666677 : 0xFF353540);
                    graphics.fill(getX(), getY(), getX() + width, getY() + 1, border);
                    graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
                    graphics.fill(getX(), getY(), getX() + 1, getY() + height, border);
                    graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);
                    int textColor = selected ? 0xFFFFDD66 : 0xFFCCCCCC;
                    graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
                }
            };
            addRenderableWidget(filterBtn);
        }

        List<WardrobeSkinEntry> history = getFilteredHistory();
        int totalPages = Math.max(1, (int) Math.ceil((double) history.size() / ITEMS_PER_PAGE));
        if (currentPage >= totalPages) currentPage = totalPages - 1;
        if (currentPage < 0) currentPage = 0;

        int gridStartY = rightBoxY + 22;
        int bottomBarH = 26;
        int gridAvailableH = rightBoxH - 22 - bottomBarH;
        int cardH = (gridAvailableH - 12) / 4;
        int cardW = (rightBoxW - 22) / 3;
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, history.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            int col = slot % 3;
            int row = slot / 3;
            int cX = rightBoxX + 6 + col * (cardW + 5);
            int cY = gridStartY + row * (cardH + 4);

            WardrobeSkinEntry entry = history.get(i);
            String type = (entry.type() == null) ? "skin" : entry.type();
            boolean isEquipped;
            if ("clothing".equals(type)) {
                WardrobeSkinEntry active = WardrobeStorage.getInstance().getActiveClothing(selectedClass);
                isEquipped = active != null && active.contentId() == entry.contentId();
            } else if ("hair".equals(type)) {
                WardrobeSkinEntry active = WardrobeStorage.getInstance().getActiveHair(selectedClass);
                isEquipped = active != null && active.contentId() == entry.contentId();
            } else {
                WardrobeSkinEntry active = WardrobeStorage.getInstance().getActiveSkin(selectedClass);
                isEquipped = active != null && active.contentId() == entry.contentId();
            }

            Button actionBtn = Button.builder(Component.literal(isEquipped ? "REMOVE" : "APPLY"), b -> {
                if (isEquipped) {
                    if ("clothing".equals(type)) {
                        WardrobeStorage.getInstance().setActiveClothing(selectedClass, null);
                        WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, 0, false, 1));
                    } else if ("hair".equals(type)) {
                        WardrobeStorage.getInstance().setActiveHair(selectedClass, null);
                        WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, 0, false, 2));
                    } else {
                        WardrobeStorage.getInstance().setActiveSkin(selectedClass, null);
                        WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, 0, false, 0));
                    }
                } else {
                    if ("clothing".equals(type)) {
                        WardrobeStorage.getInstance().setActiveClothing(selectedClass, entry);
                        WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, entry.contentId(), entry.slim(), 1));
                    } else if ("hair".equals(type)) {
                        WardrobeStorage.getInstance().setActiveHair(selectedClass, entry);
                        WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, entry.contentId(), entry.slim(), 2));
                    } else {
                        WardrobeStorage.getInstance().setActiveSkin(selectedClass, entry);
                        WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, entry.contentId(), entry.slim(), 0));
                    }
                }
                rebuildWidgets();
            }).bounds(cX + cardW - 74, cY + cardH - 16, 44, 13).build();
            actionBtn.active = (Minecraft.getInstance().level != null);
            addRenderableWidget(actionBtn);

            Button delBtn = Button.builder(Component.literal("DEL"), b -> {
                WardrobeStorage.getInstance().removeHistory(entry.contentId());
                rebuildWidgets();
            }).bounds(cX + cardW - 28, cY + cardH - 16, 26, 13).build();
            addRenderableWidget(delBtn);

            String typeLabel = type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1);
            int badgeW = font.width(typeLabel) + 6;
            int badgeH = 11;
            int badgeX = cX + 32;
            int badgeY = cY + 23;
            final String currentEntryType = type;
            final int currentContentId = entry.contentId();
            Button badgeBtn = new IconButton(badgeX, badgeY, badgeW, badgeH, Component.literal(typeLabel), b -> {
                String nextType = "skin";
                if ("skin".equals(currentEntryType)) nextType = "clothing";
                else if ("clothing".equals(currentEntryType)) nextType = "hair";
                WardrobeStorage.getInstance().updateType(currentContentId, nextType);
                rebuildWidgets();
            }) {
                @Override
                public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    int tColor = "clothing".equals(currentEntryType) ? 0xFFFFAA00 : ("hair".equals(currentEntryType) ? 0xFF55FFFF : 0xFF55FF55);
                    if (!this.active) {
                        tColor = 0xFF888888;
                    }
                    int bg = !this.active ? 0x66181820 : (isHovered ? 0xDD2A2A35 : 0x88181820);
                    graphics.fill(getX(), getY(), getX() + width, getY() + height, bg);
                    int border = !this.active ? 0xFF555560 : (isHovered ? 0xFFFFFFFF : tColor);
                    graphics.fill(getX(), getY(), getX() + width, getY() + 1, border);
                    graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
                    graphics.fill(getX(), getY(), getX() + 1, getY() + height, border);
                    graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);
                    graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 2, tColor);
                }
            };
            badgeBtn.active = !isEquipped;
            badgeBtn.setTooltip(Tooltip.create(Component.literal(isEquipped ? "Equipped: remove before changing type" : "Click to change type: Skin / Clothing / Hair")));
            addRenderableWidget(badgeBtn);
        }

        int pageControlY = rightBoxY + rightBoxH - 20;
        Button prevPageBtn = Button.builder(Component.literal("<<"), b -> {
            if (currentPage > 0) {
                currentPage--;
                rebuildWidgets();
            }
        }).bounds(rightBoxX + (rightBoxW / 2) - 65, pageControlY, 24, 16).build();
        prevPageBtn.active = (currentPage > 0);
        addRenderableWidget(prevPageBtn);

        Button nextPageBtn = Button.builder(Component.literal(">>"), b -> {
            if (currentPage < totalPages - 1) {
                currentPage++;
                rebuildWidgets();
            }
        }).bounds(rightBoxX + (rightBoxW / 2) + 41, pageControlY, 24, 16).build();
        nextPageBtn.active = (currentPage < totalPages - 1);
        addRenderableWidget(nextPageBtn);
    }

    private List<WardrobeSkinEntry> getFilteredHistory() {
        List<WardrobeSkinEntry> all = WardrobeStorage.getInstance().getHistory();
        WardrobeSkinEntry activeSkin = WardrobeStorage.getInstance().getActiveSkin(selectedClass);
        WardrobeSkinEntry activeCloth = WardrobeStorage.getInstance().getActiveClothing(selectedClass);
        WardrobeSkinEntry activeHair = WardrobeStorage.getInstance().getActiveHair(selectedClass);

        List<WardrobeSkinEntry> pinned = new ArrayList<>();
        Set<Integer> pinnedIds = new HashSet<>();

        if (activeSkin != null && matchesFilter(activeSkin)) {
            WardrobeSkinEntry entry = findInHistory(all, activeSkin.contentId());
            if (entry == null) entry = activeSkin;
            if (pinnedIds.add(entry.contentId())) {
                pinned.add(entry);
            }
        }
        if (activeCloth != null && matchesFilter(activeCloth)) {
            WardrobeSkinEntry entry = findInHistory(all, activeCloth.contentId());
            if (entry == null) entry = activeCloth;
            if (pinnedIds.add(entry.contentId())) {
                pinned.add(entry);
            }
        }
        if (activeHair != null && matchesFilter(activeHair)) {
            WardrobeSkinEntry entry = findInHistory(all, activeHair.contentId());
            if (entry == null) entry = activeHair;
            if (pinnedIds.add(entry.contentId())) {
                pinned.add(entry);
            }
        }

        List<WardrobeSkinEntry> result = new ArrayList<>(pinned);
        for (WardrobeSkinEntry e : all) {
            if (matchesFilter(e) && !pinnedIds.contains(e.contentId())) {
                result.add(e);
            }
        }
        return result;
    }

    private boolean matchesFilter(WardrobeSkinEntry e) {
        if (e == null) return false;
        String type = (e.type() == null) ? "skin" : e.type();
        if ("skins".equals(currentFilter)) return "skin".equals(type);
        if ("cloths".equals(currentFilter)) return "clothing".equals(type);
        if ("hair".equals(currentFilter)) return "hair".equals(type);
        return true;
    }

    private WardrobeSkinEntry findInHistory(List<WardrobeSkinEntry> history, int contentId) {
        for (WardrobeSkinEntry e : history) {
            if (e.contentId() == contentId) {
                return e;
            }
        }
        return null;
    }

    private void initDebugWidgets(int startX, int startY, int dialogW, int dialogH) {
        int modalW = 260;
        int modalH = 160;
        int mX = startX + (dialogW - modalW) / 2;
        int mY = startY + (dialogH - modalH) / 2;

        addRenderableWidget(Button.builder(Component.literal("Clear All Skin History"), b -> {
            WardrobeStorage.getInstance().clearHistory();
            rebuildWidgets();
        }).bounds(mX + 20, mY + 50, modalW - 40, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Clear All Skin Cache"), b -> {
            WardrobeStorage.getInstance().clearAllCache();
            rebuildWidgets();
        }).bounds(mX + 20, mY + 80, modalW - 40, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            showDebugModal = false;
            rebuildWidgets();
        }).bounds(mX + 20, mY + 115, modalW - 40, 20).build());
    }

    private void initEntities() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String[] classes = {"fighter", "elementalist", "hunter"};
        for (String cls : classes) {
            previewEntities.computeIfAbsent(cls, k -> {
                try {
                    MercenaryEntity ent = SlashEntities.MERCENARY.get().create(mc.level);
                    if (ent != null) {
                        ent.setClassId(k);
                        if (mc.player != null) {
                            ent.setOwnerUUID(mc.player.getUUID());
                        }
                        WardrobeManager.registerPreviewEntity(ent);
                        updatePreviewEquipment(ent, k);
                    }
                    return ent;
                } catch (Throwable ignored) {
                    return null;
                }
            });
        }
    }

    private void updatePreviewEquipment(MercenaryEntity preview, String cls) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || preview == null) return;
        try {
            var data = Load.player(mc.player);
            if (data != null && data.mercs != null) {
                var mercData = data.mercs.getOrCreate(cls);
                if (mercData != null && mercData.getGear() != null) {
                    SimpleContainer gear = mercData.getGear();
                    preview.setItemSlot(EquipmentSlot.HEAD, gear.getItem(0));
                    preview.setItemSlot(EquipmentSlot.CHEST, gear.getItem(1));
                    preview.setItemSlot(EquipmentSlot.LEGS, gear.getItem(2));
                    preview.setItemSlot(EquipmentSlot.FEET, gear.getItem(3));
                    preview.setItemSlot(EquipmentSlot.MAINHAND, gear.getItem(4));
                    preview.setItemSlot(EquipmentSlot.OFFHAND, gear.getItem(5));
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            var live = ClientMercenary.get();
            if (live != null && cls.equalsIgnoreCase(live.getClassId())) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    preview.setItemSlot(slot, live.getItemBySlot(slot));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private MercenaryEntity getOrCreateMiniEntity(int contentId, boolean slim) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        return miniEntities.computeIfAbsent(contentId, k -> {
            try {
                MercenaryEntity ent = SlashEntities.MERCENARY.get().create(mc.level);
                if (ent != null) {
                    ent.setClassId("fighter");
                    WardrobeManager.registerPreviewEntity(ent);
                }
                return ent;
            } catch (Throwable ignored) {
                return null;
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int dialogW = Math.min(width - 20, 580);
        int dialogH = Math.min(height - 20, 320);
        int startX = (width - dialogW) / 2;
        int startY = (height - dialogH) / 2;

        graphics.fill(startX, startY, startX + dialogW, startY + dialogH, 0xEE1A1A1E);
        graphics.fill(startX, startY, startX + dialogW, startY + 1, 0xFF4A4A55);
        graphics.fill(startX, startY + dialogH - 1, startX + dialogW, startY + dialogH, 0xFF4A4A55);
        graphics.fill(startX, startY, startX + 1, startY + dialogH, 0xFF4A4A55);
        graphics.fill(startX + dialogW - 1, startY, startX + dialogW, startY + dialogH, 0xFF4A4A55);

        graphics.drawString(font, "Mercenary Wardrobe", startX + 12, startY + 10, 0xFFE0E0E0, false);

        if (showDebugModal) {
            renderDebugModal(graphics, startX, startY, dialogW, dialogH);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderLeftPanel(graphics, mouseX, mouseY);
        renderRightPanel(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderLeftPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(leftBoxX, leftBoxY, leftBoxX + leftBoxW, leftBoxY + leftBoxH, 0x88101014);
        graphics.fill(leftBoxX, leftBoxY, leftBoxX + leftBoxW, leftBoxY + 1, 0xFF2F2F38);
        graphics.fill(leftBoxX, leftBoxY + leftBoxH - 1, leftBoxX + leftBoxW, leftBoxY + leftBoxH, 0xFF2F2F38);
        graphics.fill(leftBoxX, leftBoxY, leftBoxX + 1, leftBoxY + leftBoxH, 0xFF2F2F38);
        graphics.fill(leftBoxX + leftBoxW - 1, leftBoxY, leftBoxX + leftBoxW, leftBoxY + leftBoxH, 0xFF2F2F38);

        MercenaryEntity preview = previewEntities.get(selectedClass);
        if (preview != null && Minecraft.getInstance().level != null) {
            updatePreviewEquipment(preview, selectedClass);

            WardrobeSkinEntry baseSkin = WardrobeStorage.getInstance().getActiveSkin(selectedClass);
            WardrobeManager.setPreviewOverride(preview, baseSkin != null ? WardrobeManager.getSkinTexture(baseSkin.contentId(), baseSkin.slim()) : null);

            WardrobeSkinEntry clothSkin = WardrobeStorage.getInstance().getActiveClothing(selectedClass);
            WardrobeManager.setPreviewClothingOverride(preview, clothSkin != null ? WardrobeManager.getSkinTexture(clothSkin.contentId(), clothSkin.slim()) : null);

            WardrobeSkinEntry hairSkin = WardrobeStorage.getInstance().getActiveHair(selectedClass);
            WardrobeManager.setPreviewHairOverride(preview, hairSkin != null ? WardrobeManager.getSkinTexture(hairSkin.contentId(), hairSkin.slim()) : null);

            int availableH = leftBoxH - 72;
            int scale = Math.min(78, Math.max(50, (int) (availableH / 2.4F)));
            int entityX = leftBoxX + leftBoxW / 2;
            int entityY = leftBoxY + leftBoxH - 74;
            float lookY = entityY - (int) (scale * 1.35F);

            float origBodyRot = preview.yBodyRot;
            float origYRot = preview.getYRot();
            float origXRot = preview.getXRot();
            float origHeadRotO = preview.yHeadRotO;
            float origHeadRot = preview.yHeadRot;

            float f = (float) Math.atan((double) ((entityX - mouseX) / 40.0F));
            float f1 = (float) Math.atan((double) ((lookY - mouseY) / 40.0F));

            preview.yBodyRot = 180.0F + previewYaw + (isDraggingPreview ? 0.0F : f * 15.0F);
            preview.setYRot(preview.yBodyRot);
            preview.setXRot(previewPitch + (isDraggingPreview ? 0.0F : -f1 * 20.0F));
            preview.yHeadRot = preview.getYRot();
            preview.yHeadRotO = preview.getYRot();

            Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf quaternionf1 = new Quaternionf().rotateX((previewPitch + (isDraggingPreview ? 0.0F : f1 * 20.0F)) * ((float) Math.PI / 180.0F));
            quaternionf.mul(quaternionf1);

            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            InventoryScreen.renderEntityInInventory(graphics, entityX, entityY, scale, quaternionf, quaternionf1, preview);
            graphics.pose().popPose();

            preview.yBodyRot = origBodyRot;
            preview.setYRot(origYRot);
            preview.setXRot(origXRot);
            preview.yHeadRotO = origHeadRotO;
            preview.yHeadRot = origHeadRot;
        } else {
            String msg = "3D Preview in World";
            graphics.drawCenteredString(font, msg, leftBoxX + leftBoxW / 2, leftBoxY + (leftBoxH - 60) / 2, 0xFF888888);
        }
    }

    private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(rightBoxX, rightBoxY, rightBoxX + rightBoxW, rightBoxY + rightBoxH, 0x88101014);
        graphics.fill(rightBoxX, rightBoxY, rightBoxX + rightBoxW, rightBoxY + 1, 0xFF2F2F38);
        graphics.fill(rightBoxX, rightBoxY + rightBoxH - 1, rightBoxX + rightBoxW, rightBoxY + rightBoxH, 0xFF2F2F38);
        graphics.fill(rightBoxX, rightBoxY, rightBoxX + 1, rightBoxY + rightBoxH, 0xFF2F2F38);
        graphics.fill(rightBoxX + rightBoxW - 1, rightBoxY, rightBoxX + rightBoxW, rightBoxY + rightBoxH, 0xFF2F2F38);

        List<WardrobeSkinEntry> history = getFilteredHistory();
        int gridStartY = rightBoxY + 22;
        int bottomBarH = 26;
        int gridAvailableH = rightBoxH - 22 - bottomBarH;
        if (history.isEmpty()) {
            graphics.drawCenteredString(font, "EMPTY", rightBoxX + rightBoxW / 2, gridStartY + (gridAvailableH - 30) / 2, 0xFF777777);
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) history.size() / ITEMS_PER_PAGE));
        String pageText = "Page " + (currentPage + 1) + " / " + totalPages;
        int pageControlY = rightBoxY + rightBoxH - 20;
        graphics.drawCenteredString(font, pageText, rightBoxX + rightBoxW / 2, pageControlY + 4, 0xFFAAAAAA);

        int cardH = (gridAvailableH - 12) / 4;
        int cardW = (rightBoxW - 22) / 3;
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, history.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            int col = slot % 3;
            int row = slot / 3;
            int cX = rightBoxX + 6 + col * (cardW + 5);
            int cY = gridStartY + row * (cardH + 4);

            WardrobeSkinEntry entry = history.get(i);
            String type = (entry.type() == null) ? "skin" : entry.type();
            boolean isEquipped;
            if ("clothing".equals(type)) {
                WardrobeSkinEntry active = WardrobeStorage.getInstance().getActiveClothing(selectedClass);
                isEquipped = active != null && active.contentId() == entry.contentId();
            } else if ("hair".equals(type)) {
                WardrobeSkinEntry active = WardrobeStorage.getInstance().getActiveHair(selectedClass);
                isEquipped = active != null && active.contentId() == entry.contentId();
            } else {
                WardrobeSkinEntry active = WardrobeStorage.getInstance().getActiveSkin(selectedClass);
                isEquipped = active != null && active.contentId() == entry.contentId();
            }

            int typeColor = "clothing".equals(type) ? 0xFFFFAA00 : ("hair".equals(type) ? 0xFF55FFFF : 0xFF55FF55);
            boolean isHovered = (mouseX >= cX && mouseX <= cX + cardW && mouseY >= cY && mouseY <= cY + cardH);
            int borderColor = isEquipped ? typeColor : (isHovered ? 0xFF5D5D70 : 0xFF353540);

            graphics.fill(cX, cY, cX + cardW, cY + cardH, isHovered ? 0xCC2A2A35 : 0xAA1C1C24);
            graphics.fill(cX, cY, cX + cardW, cY + 1, borderColor);
            graphics.fill(cX, cY + cardH - 1, cX + cardW, cY + cardH, borderColor);
            graphics.fill(cX, cY, cX + 1, cY + cardH, borderColor);
            graphics.fill(cX + cardW - 1, cY, cX + cardW, cY + cardH, borderColor);

            MercenaryEntity mini = getOrCreateMiniEntity(entry.contentId(), entry.slim());
            if (mini != null) {
                ResourceLocation skinLoc = WardrobeManager.getSkinTexture(entry.contentId(), entry.slim());
                if (type.equals("clothing")) {
                    WardrobeManager.setPreviewOverride(mini, WardrobeManager.getEmptyTexture());
                    WardrobeManager.setPreviewClothingOverride(mini, skinLoc);
                    WardrobeManager.setPreviewHairOverride(mini, null);
                } else if (type.equals("hair")) {
                    WardrobeManager.setPreviewOverride(mini, WardrobeManager.getEmptyTexture());
                    WardrobeManager.setPreviewClothingOverride(mini, null);
                    WardrobeManager.setPreviewHairOverride(mini, skinLoc);
                } else {
                    WardrobeManager.setPreviewOverride(mini, skinLoc);
                    WardrobeManager.setPreviewClothingOverride(mini, null);
                    WardrobeManager.setPreviewHairOverride(mini, null);
                }
                int miniX = cX + 16;
                int miniY = cY + cardH - 6;
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, miniX, miniY, 18, (float) miniX - mouseX, (float) (miniY - 24) - mouseY, mini);
            }

            String title = entry.name() != null ? entry.name() : ("Skin #" + entry.contentId());
            if (font.width(title) > cardW - 36) {
                title = font.plainSubstrByWidth(title, cardW - 46) + "...";
            }
            graphics.drawString(font, title, cX + 32, cY + 4, 0xFFE0E0E0, false);
            String idStr = "#" + entry.contentId();
            if (font.width(idStr) > cardW - 36) {
                idStr = font.plainSubstrByWidth(idStr, cardW - 46) + "...";
            }
            graphics.drawString(font, idStr, cX + 32, cY + 14, 0xFF888888, false);

        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        List<WardrobeSkinEntry> history = getFilteredHistory();
        if (history.isEmpty()) return;

        int gridStartY = rightBoxY + 22;
        int bottomBarH = 26;
        int gridAvailableH = rightBoxH - 22 - bottomBarH;
        int cardH = (gridAvailableH - 12) / 4;
        int cardW = (rightBoxW - 22) / 3;
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, history.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            int col = slot % 3;
            int row = slot / 3;
            int cX = rightBoxX + 6 + col * (cardW + 5);
            int cY = gridStartY + row * (cardH + 4);

            WardrobeSkinEntry entry = history.get(i);
            String title = entry.name() != null ? entry.name() : ("Skin #" + entry.contentId());
            if (font.width(title) > cardW - 36) {
                title = font.plainSubstrByWidth(title, cardW - 46) + "...";
            }
            String idStr = "#" + entry.contentId();
            if (font.width(idStr) > cardW - 36) {
                idStr = font.plainSubstrByWidth(idStr, cardW - 46) + "...";
            }
            int titleIdW = Math.max(font.width(title), font.width(idStr));
            boolean overTitleOrId = (mouseX >= cX + 32 && mouseX <= cX + 32 + titleIdW && mouseY >= cY + 3 && mouseY <= cY + 23);

            if (overTitleOrId) {
                Component nameLine = Component.literal(entry.name());
                Component idLine = Component.literal("ID: #" + entry.contentId());
                Component authorLine = Component.literal("Author: " + (entry.author().isEmpty() ? "Unknown" : entry.author()));
                Component typeLine = Component.literal("Type: " + ((entry.type() == null) ? "skin" : entry.type()));
                boolean isSlim = entry.slim() || WardrobeManager.isSlimSkin(entry.contentId());
                Component modelLine = Component.literal("Model: " + (isSlim ? "Alex (3px)" : "Steve (4px)"));

                graphics.renderComponentTooltip(font, List.of(nameLine, idLine, authorLine, typeLine, modelLine), mouseX, mouseY);
                break;
            }
        }
    }

    private void renderDebugModal(GuiGraphics graphics, int startX, int startY, int dialogW, int dialogH) {
        int modalW = 260;
        int modalH = 160;
        int mX = startX + (dialogW - modalW) / 2;
        int mY = startY + (dialogH - modalH) / 2;

        graphics.fill(mX, mY, mX + modalW, mY + modalH, 0xF0101014);
        graphics.fill(mX, mY, mX + modalW, mY + 1, 0xFF5D5D70);
        graphics.fill(mX, mY + modalH - 1, mX + modalW, mY + modalH, 0xFF5D5D70);
        graphics.fill(mX, mY, mX + 1, mY + modalH, 0xFF5D5D70);
        graphics.fill(mX + modalW - 1, mY, mX + modalW, mY + modalH, 0xFF5D5D70);

        graphics.drawCenteredString(font, "Debug Menu", mX + modalW / 2, mY + 10, 0xFFFFFFFF);

        long cacheBytes = WardrobeStorage.getInstance().getCacheSizeBytes();
        String sizeStr;
        if (cacheBytes < 1024) {
            sizeStr = cacheBytes + " B";
        } else if (cacheBytes < 1024 * 1024) {
            sizeStr = String.format(Locale.ROOT, "%.2f KB", cacheBytes / 1024.0);
        } else {
            sizeStr = String.format(Locale.ROOT, "%.2f MB", cacheBytes / (1024.0 * 1024.0));
        }

        graphics.drawCenteredString(font, "Skin Cache: " + sizeStr, mX + modalW / 2, mY + 30, 0xFFB0B0B0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && mouseX >= leftBoxX && mouseX <= leftBoxX + leftBoxW && mouseY >= leftBoxY && mouseY <= leftBoxY + leftBoxH - 40) {
            isDraggingPreview = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingPreview = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingPreview) {
            previewYaw -= (float) dragX * 1.5F;
            previewPitch = Mth.clamp(previewPitch - (float) dragY * 1.2F, -40.0F, 40.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nicknameBox != null && nicknameBox.isFocused() && (keyCode == 257 || keyCode == 335)) {
            triggerNicknameFetch(nicknameBox.getValue());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void triggerNicknameFetch(String name) {
        boolean inGame = Minecraft.getInstance().level != null && Minecraft.getInstance().player != null;
        if (!inGame) return;
        if (name == null || name.trim().isEmpty()) return;
        String cleanName = name.trim();
        if (!cleanName.matches("^[a-zA-Z0-9_]{1,16}$")) return;

        CompletableFuture.runAsync(() -> {
            try {
                byte[] skinBytes = WardrobeManager.fetchSkinBytes(cleanName);
                if (skinBytes == null || skinBytes.length == 0) return;

                int contentId = 1_000_000_000 + Math.abs(cleanName.toLowerCase(Locale.ROOT).hashCode() % 1_000_000_000);
                if (contentId == 0) contentId = 1_000_000_001;

                File diskFile = WardrobeStorage.getInstance().getCacheFile(contentId);
                Files.write(diskFile.toPath(), skinBytes);

                WardrobeManager.clearTextureCache(contentId);

                boolean isSlim = WardrobeManager.isSlimSkin(contentId);
                String type = SkinClassifier.classify(diskFile);
                if (type == null || type.isEmpty()) type = "skin";

                WardrobeSkinEntry entry = new WardrobeSkinEntry(contentId, cleanName, "Player", isSlim, System.currentTimeMillis(), type);
                WardrobeStorage.getInstance().addHistory(entry);

                WardrobeStorage.getInstance().setActiveSkin(selectedClass, entry);
                WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(selectedClass, contentId, isSlim, 0));

                Minecraft.getInstance().execute(() -> {
                    savedNickname = "";
                    if (nicknameBox != null) {
                        nicknameBox.setValue("");
                    }
                    rebuildWidgets();
                });
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public void onClose() {
        previewYaw = 0.0F;
        previewPitch = 0.0F;
        isDraggingPreview = false;
        Minecraft.getInstance().setScreen(parentScreen);
    }

    public void refreshScreen() {
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
