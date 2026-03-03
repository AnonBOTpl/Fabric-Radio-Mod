package net.anonbot.radiomod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedList;

public class RadioHistoryScreen extends Screen {

    private final RadioModClient modClient;
    private final Screen parentScreen;

    public RadioHistoryScreen(RadioModClient modClient, Screen parentScreen) {
        super(RadioModClient.tc("Historia Piosenek", "Song History"));
        this.modClient = modClient;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;

        HistoryListWidget list = new HistoryListWidget(this.minecraft, this.width, this.height - 80, 40, 22);
        LinkedList<String> history = modClient.getSongHistory();
        if (history.isEmpty()) {
            list.addItem(new HistoryEntry(
                    RadioModClient.t("Brak historii — odtwarzaj radio!", "No history yet — play the radio!"), false));
        } else {
            for (String song : history) {
                list.addItem(new HistoryEntry(song, true));
            }
        }
        this.addRenderableWidget(list);

        this.addRenderableWidget(Button.builder(
                RadioModClient.tc("Wyczysc Historie", "Clear History"),
                b -> { modClient.getSongHistory().clear();
                    if (this.minecraft != null) this.minecraft.setScreen(new RadioHistoryScreen(modClient, parentScreen)); }
        ).bounds(cx - 155, this.height - 30, 145, 20).build());

        this.addRenderableWidget(Button.builder(
                RadioModClient.tc("Wroc", "Back"),
                b -> { if (this.minecraft != null) this.minecraft.setScreen(parentScreen); }
        ).bounds(cx + 10, this.height - 30, 145, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                RadioModClient.t("Kliknij wpis aby skopiowac do schowka", "Click entry to copy to clipboard"),
                this.width / 2, 28, 0x888888);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private class HistoryListWidget extends ObjectSelectionList<HistoryEntry> {
        public HistoryListWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() { return 320; }

        public void addItem(HistoryEntry entry) { this.addEntry(entry); }
    }

    private class HistoryEntry extends ObjectSelectionList.Entry<HistoryEntry> {
        private final String song;
        private final boolean clickable;
        private long copiedAt = -1;

        HistoryEntry(String song, boolean clickable) {
            this.song = song;
            this.clickable = clickable;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width,
                           int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            if (isHovered && clickable)
                guiGraphics.fill(left, top, left + width, top + height, 0x40FFFFFF);

            boolean justCopied = copiedAt > 0 && (net.minecraft.Util.getMillis() - copiedAt < 1500);
            int color = justCopied ? 0x55FF55 : (clickable ? 0xFFFFFF : 0x888888);
            String label = justCopied
                    ? RadioModClient.t("Skopiowano!", "Copied!")
                    : (clickable ? "♪ " + song : song);

            int textWidth = Minecraft.getInstance().font.width(label);
            int maxW = width - 10;
            if (textWidth <= maxW) {
                guiGraphics.drawString(Minecraft.getInstance().font, label, left + 5, top + 5, color, false);
            } else {
                String cut = Minecraft.getInstance().font.plainSubstrByWidth(label, maxW - Minecraft.getInstance().font.width("...")) + "...";
                guiGraphics.drawString(Minecraft.getInstance().font, cut, left + 5, top + 5, color, false);
            }

            // Hint kopiowania po prawej przy hover
            if (isHovered && clickable && !justCopied) {
                String hint = RadioModClient.t("[kliknij = kopiuj]", "[click = copy]");
                int hintW = Minecraft.getInstance().font.width(hint);
                guiGraphics.drawString(Minecraft.getInstance().font, hint, left + width - hintW - 5, top + 5, 0x555555, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!clickable || button != 0) return false;
            Minecraft.getInstance().keyboardHandler.setClipboard(song);
            copiedAt = net.minecraft.Util.getMillis();
            return true;
        }

        @Override
        public Component getNarration() { return Component.literal(song); }
    }
}