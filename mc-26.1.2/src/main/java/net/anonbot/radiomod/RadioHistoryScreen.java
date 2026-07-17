package net.anonbot.radiomod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.LinkedList;

public class RadioHistoryScreen extends Screen {

    private final RadioModClient modClient;
    private final Screen parentScreen;

    public RadioHistoryScreen(RadioModClient modClient, Screen parentScreen) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                RadioModClient.tc("Historia Piosenek", "Song History"));
        this.modClient = modClient;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;

        HistoryListWidget list = new HistoryListWidget(this.minecraft, this.width, this.height - 80, 40, 22);
        LinkedList<String> history = modClient.getSongHistory();
        if (history.isEmpty()) {                list.addItem(RadioModClient.t("Brak historii \u2014 odtwarzaj radio!", "No history yet \u2014 play the radio!"), false);
        } else {
            for (String song : history) {
                list.addItem(song, true);
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        var coll = context.textRenderer();
        coll.accept((this.width - this.font.width(this.title.getString())) / 2, 15, this.title);
        String hint = RadioModClient.t("Kliknij wpis aby skopiowac do schowka", "Click entry to copy to clipboard");
        coll.accept((this.width - this.font.width(hint)) / 2, 28, Component.literal("\u00a77" + hint));
        super.extractRenderState(context, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bool) {
        return super.mouseClicked(event, bool);
    }

    private class HistoryListWidget extends ObjectSelectionList<HistoryEntry> {
        public HistoryListWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() { return 320; }

        public void addItem(String song, boolean clickable) {
            this.addEntry(new HistoryEntry(this, RadioHistoryScreen.this, song, clickable));
        }
    }

    private static class HistoryEntry extends ObjectSelectionList.Entry<HistoryEntry> {
        private final HistoryListWidget parent;
        private final RadioHistoryScreen screen;
        private final String song;
        private final boolean clickable;
        private long copiedAt = -1;

        HistoryEntry(HistoryListWidget parent, RadioHistoryScreen screen, String song, boolean clickable) {
            this.parent = parent;
            this.screen = screen;
            this.song = song;
            this.clickable = clickable;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            java.util.List<HistoryEntry> entries = parent.children();
            int left = parent.getRowLeft();
            int top = parent.getRowTop(entries.indexOf(this));
            int width = parent.getRowWidth();
            int height = 22;

            if (isHovered && clickable)
                graphics.fill(left, top, left + width, top + height, 0x40FFFFFF);

            boolean justCopied = copiedAt > 0 && (System.currentTimeMillis() - copiedAt < 1500);
            String label = justCopied
                    ? "\u00a7a" + RadioModClient.t("Skopiowano!", "Copied!")
                    : (clickable ? "\u00a7f\u266a " + song : "\u00a78" + song);

            var coll = graphics.textRenderer();
            int textWidth = Minecraft.getInstance().font.width(label);
            int maxW = width - 10;
            if (textWidth <= maxW) {
                coll.accept(left + 5, top + 5, Component.literal(label));
            } else {
                String cut = Minecraft.getInstance().font.plainSubstrByWidth(label, maxW - Minecraft.getInstance().font.width("...")) + "...";
                coll.accept(left + 5, top + 5, Component.literal(cut));
            }

            if (isHovered && clickable && !justCopied) {
                String hint = "\u00a78" + RadioModClient.t("[kliknij = kopiuj]", "[click = copy]");
                int hintW = Minecraft.getInstance().font.width(hint);
                coll.accept(left + width - hintW - 5, top + 5, Component.literal(hint));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bool) {
            if (!clickable || event.button() != 0) return false;
            Minecraft.getInstance().keyboardHandler.setClipboard(song);
            copiedAt = System.currentTimeMillis();
            return true;
        }

        @Override
        public Component getNarration() { return Component.literal(song); }
    }
}
