package net.anonbot.radiomod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RadioBlacklistScreen extends Screen {

    private final RadioModClient modClient;
    private final Screen parentScreen;
    private BlacklistWidget listWidget;
    private EditBox phraseBox;

    public RadioBlacklistScreen(RadioModClient modClient, Screen parentScreen) {
        super(RadioModClient.tc("Czarna Lista Powiadomień", "Notification Blacklist"));
        this.modClient = modClient;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        this.phraseBox = new EditBox(this.font, centerX - 150, 30, 200, 20, RadioModClient.tc("Zablokuj słowo...", "Block phrase..."));
        this.addRenderableWidget(this.phraseBox);

        this.addRenderableWidget(Button.builder(RadioModClient.tc("Dodaj", "Add"), button -> {
            String phrase = this.phraseBox.getValue().trim();
            if (!phrase.isEmpty() && !modClient.getBlacklist().contains(phrase)) {
                modClient.getBlacklist().add(phrase);
                modClient.saveConfig();
                this.phraseBox.setValue("");
                refreshList();
            }
        }).bounds(centerX + 60, 30, 90, 20).build());

        this.listWidget = new BlacklistWidget(this.minecraft, this.width, this.height - 100, 60, 25);
        this.addRenderableWidget(this.listWidget);
        refreshList();

        this.addRenderableWidget(Button.builder(RadioModClient.tc("Wróć do Ustawień", "Back to Settings"), button -> {
            if (this.minecraft != null) this.minecraft.setScreen(parentScreen);
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    private void refreshList() {
        listWidget.clearPhrases();
        for (String phrase : modClient.getBlacklist()) listWidget.addPhrase(phrase);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    @Override public boolean isPauseScreen() { return false; }

    private class BlacklistWidget extends ObjectSelectionList<BlacklistEntry> {
        public BlacklistWidget(Minecraft mc, int width, int height, int y, int itemHeight) { super(mc, width, height, y, itemHeight); }
        @Override public int getRowWidth() { return 300; }
        public void clearPhrases() { this.clearEntries(); }
        public void addPhrase(String phrase) { this.addEntry(new BlacklistEntry(phrase)); }
    }

    private class BlacklistEntry extends ObjectSelectionList.Entry<BlacklistEntry> {
        final String phrase;
        BlacklistEntry(String phrase) { this.phrase = phrase; }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            if (isHovered) {
                guiGraphics.fill(left, top, left + width, top + height, 0x40FF5555);
                guiGraphics.drawString(Minecraft.getInstance().font, RadioModClient.t("§c[Usuń]", "§c[Remove]"), left + width - 50, top + 5, 0xFFFFFF, false);
            }
            guiGraphics.drawString(Minecraft.getInstance().font, RadioModClient.t("Zablokowana fraza: §e", "Blocked phrase: §e") + this.phrase, left + 5, top + 5, 0xFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                modClient.getBlacklist().remove(this.phrase);
                modClient.saveConfig();
                RadioBlacklistScreen.this.refreshList();
                return true;
            }
            return false;
        }
        @Override public Component getNarration() { return Component.literal(this.phrase); }
    }
}