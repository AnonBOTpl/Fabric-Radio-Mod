package net.anonbot.radiomod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class RadioBlacklistScreen extends Screen {

    final RadioModClient modClient; // package-private for access by BlacklistEntry
    private final Screen parentScreen;
    private BlacklistWidget listWidget;
    private EditBox phraseBox;

    public RadioBlacklistScreen(RadioModClient modClient, Screen parentScreen) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                RadioModClient.tc("Czarna Lista Powiadomie\u0144", "Notification Blacklist"));
        this.modClient = modClient;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        this.phraseBox = new EditBox(this.font, centerX - 150, 30, 200, 20,
                RadioModClient.tc("Zablokuj s\u0142owo...", "Block phrase..."));
        this.phraseBox.setMaxLength(50);
        this.addRenderableWidget(this.phraseBox);

        this.addRenderableWidget(Button.builder(RadioModClient.tc("Dodaj", "Add"), button -> {
            String phrase = this.phraseBox.getValue().trim();
            if (!phrase.isEmpty() && phrase.length() <= 50 && !modClient.getBlacklist().contains(phrase)) {
                modClient.getBlacklist().add(phrase);
                modClient.saveConfig();
                this.phraseBox.setValue("");
                refreshList();
            }
        }).bounds(centerX + 60, 30, 90, 20).build());

        this.listWidget = new BlacklistWidget(this.minecraft, this.width, this.height - 100, 60, 25);
        this.addRenderableWidget(this.listWidget);
        refreshList();

        this.addRenderableWidget(Button.builder(                RadioModClient.tc("Wroc do Ustawien", "Back to Settings"), button -> {
            if (this.minecraft != null)
                this.minecraft.setScreenAndShow(parentScreen);
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    private void refreshList() {
        listWidget.clearPhrases();
        for (String phrase : modClient.getBlacklist())
            listWidget.addPhrase(phrase);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        var coll = context.textRenderer();
        coll.accept((this.width - this.font.width(this.title.getString())) / 2, 10, this.title);
        super.extractRenderState(context, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bool) {
        return super.mouseClicked(event, bool);
    }

    private class BlacklistWidget extends ObjectSelectionList<BlacklistEntry> {
        public BlacklistWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 300;
        }

        public void clearPhrases() {
            this.clearEntries();
        }

        public void addPhrase(String phrase) {
            this.addEntry(new BlacklistEntry(this, RadioBlacklistScreen.this, phrase));
        }
    }

    private static class BlacklistEntry extends ObjectSelectionList.Entry<BlacklistEntry> {
        final BlacklistWidget parent;
        final RadioBlacklistScreen screen;
        final String phrase;

        BlacklistEntry(BlacklistWidget parent, RadioBlacklistScreen screen, String phrase) {
            this.parent = parent;
            this.screen = screen;
            this.phrase = phrase;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            java.util.List<BlacklistEntry> entries = parent.children();
            int left = parent.getRowLeft();
            int top = parent.getRowTop(entries.indexOf(this));
            int width = parent.getRowWidth();
            int height = 25;

            var coll = graphics.textRenderer();
            if (isHovered) {
                graphics.fill(left, top, left + width, top + height, 0x40FF5555);
                coll.accept(left + width - 50, top + 5,
                        Component.literal(RadioModClient.t("\u00a7c[Usu\u0144]", "\u00a7c[Remove]")));
            }
            coll.accept(left + 5, top + 5,
                    Component.literal(RadioModClient.t("Zablokowana fraza: \u00a7e", "Blocked phrase: \u00a7e") + this.phrase));
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bool) {
            if (event.button() == 0) {
                screen.modClient.getBlacklist().remove(this.phrase);
                screen.modClient.saveConfig();
                screen.refreshList();
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.phrase);
        }
    }
}
