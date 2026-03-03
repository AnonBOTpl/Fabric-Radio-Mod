package net.anonbot.radiomod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

public class RadioSettingsScreen extends Screen {

    private final RadioModClient modClient;
    private final Screen parentScreen;

    public RadioSettingsScreen(RadioModClient modClient, Screen parentScreen) {
        super(RadioModClient.tc("Ustawienia Radia", "Radio Settings"));
        this.modClient = modClient;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;

        String on  = RadioModClient.t("\u00a7aWLACZONE", "\u00a7aON");
        String off = RadioModClient.t("\u00a7cWYLACZONE", "\u00a7cOFF");

        // Toast wl/wyl
        this.addRenderableWidget(Button.builder(
                Component.literal(RadioModClient.t("Okienko w rogu (Toast): ", "Corner popup (Toast): ")
                        + (modClient.isShowToast() ? on : off)),
                b -> { modClient.setShowToast(!modClient.isShowToast());
                    if (this.minecraft != null) this.minecraft.setScreen(new RadioSettingsScreen(modClient, parentScreen)); }
        ).bounds(cx - 150, cy - 80, 300, 20).build());

        // Action bar wl/wyl
        this.addRenderableWidget(Button.builder(
                Component.literal(RadioModClient.t("Napis nad ekwipunkiem: ", "Action bar text: ")
                        + (modClient.isShowActionBar() ? on : off)),
                b -> { modClient.setShowActionBar(!modClient.isShowActionBar());
                    if (this.minecraft != null) this.minecraft.setScreen(new RadioSettingsScreen(modClient, parentScreen)); }
        ).bounds(cx - 150, cy - 55, 300, 20).build());

        // Czas wyswietlania
        float initialValue = (modClient.getToastDuration() - 2) / 18.0f;
        this.addRenderableWidget(new DurationSlider(cx - 150, cy - 30, 300, 20, Component.empty(), initialValue));

        // Rozmiar toastu - 3 przyciski obok siebie
        String[] sizeLabels = {
                RadioModClient.t("Maly", "Small"),
                RadioModClient.t("Sredni", "Medium"),
                RadioModClient.t("Duzy", "Large")
        };
        int btnW = 96;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            boolean selected = modClient.getToastSize() == i;
            String label = (selected ? "\u00a7e> " : "") + sizeLabels[i] + (selected ? " <\u00a7r" : "");
            this.addRenderableWidget(Button.builder(
                    Component.literal(RadioModClient.t("Rozmiar toastu: ", "Toast size: ").equals("Rozmiar toastu: ")
                            ? (i == 0 ? RadioModClient.t("Rozmiar: ", "Size: ") + label : label)
                            : (i == 0 ? RadioModClient.t("Rozmiar: ", "Size: ") + label : label)),
                    b -> { modClient.setToastSize(idx);
                        if (this.minecraft != null) this.minecraft.setScreen(new RadioSettingsScreen(modClient, parentScreen)); }
            ).bounds(cx - 150 + i * (btnW + 3), cy - 5, btnW, 20).build());
        }

        // Czarna lista
        this.addRenderableWidget(Button.builder(
                RadioModClient.tc("\u2699 Edytuj Czarna Liste Powiadomien", "\u2699 Edit Notification Blacklist"),
                b -> { if (this.minecraft != null) this.minecraft.setScreen(new RadioBlacklistScreen(modClient, this)); }
        ).bounds(cx - 150, cy + 20, 300, 20).build());

        // Eksport ulubionych
        this.addRenderableWidget(Button.builder(
                RadioModClient.tc("\u2B06 Eksportuj Ulubione", "\u2B06 Export Favorites"),
                b -> exportFavorites()
        ).bounds(cx - 150, cy + 45, 145, 20).build());

        // Import ulubionych
        this.addRenderableWidget(Button.builder(
                RadioModClient.tc("\u2B07 Importuj Ulubione", "\u2B07 Import Favorites"),
                b -> importFavorites()
        ).bounds(cx + 5, cy + 45, 145, 20).build());

        // Zapisz i Wroc
        this.addRenderableWidget(Button.builder(
                RadioModClient.tc("Zapisz i Wroc", "Save & Return"),
                b -> { if (this.minecraft != null) this.minecraft.setScreen(parentScreen); }
        ).bounds(cx - 100, cy + 75, 200, 20).build());
    }

    private void exportFavorites() {
        try {
            Path exportPath = Path.of(
                    net.minecraft.client.Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                    "config", "radiomod_favorites_export.json");
            JsonArray arr = new JsonArray();
            for (RadioModClient.FavoriteStation fs : modClient.getFavorites()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", fs.name);
                o.addProperty("url", fs.url);
                o.addProperty("favicon", fs.favicon);
                arr.add(o);
            }
            Files.writeString(exportPath, arr.toString());
            net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                    RadioModClient.tc("Wyeksportowano do config/radiomod_favorites_export.json",
                            "Exported to config/radiomod_favorites_export.json"), false);
        } catch (Exception e) {
            System.err.println("[RadioMod] Blad eksportu: " + e.getMessage());
        }
    }

    private void importFavorites() {
        try {
            Path importPath = Path.of(
                    net.minecraft.client.Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                    "config", "radiomod_favorites_export.json");
            if (!Files.exists(importPath)) {
                net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                        RadioModClient.tc("Brak pliku do importu!", "Import file not found!"), false);
                return;
            }
            JsonArray arr = JsonParser.parseString(Files.readString(importPath)).getAsJsonArray();
            int added = 0;
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                String n = o.has("name") ? o.get("name").getAsString() : "Nieznana";
                String u = o.has("url")  ? o.get("url").getAsString()  : "";
                String f = o.has("favicon") ? o.get("favicon").getAsString() : "";
                if (!u.isEmpty() && !modClient.isFavorite(u)) {
                    modClient.toggleFavorite(n, u, f);
                    added++;
                }
            }
            net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(RadioModClient.t("Zaimportowano ", "Imported ") + added +
                            RadioModClient.t(" stacji!", " stations!")), false);
        } catch (Exception e) {
            System.err.println("[RadioMod] Blad importu: " + e.getMessage());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        // Etykieta rozmiaru toastu
        guiGraphics.drawCenteredString(this.font,
                RadioModClient.t("Rozmiar toastu:", "Toast size:"),
                this.width / 2, this.height / 2 - 17, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private class DurationSlider extends AbstractSliderButton {
        public DurationSlider(int x, int y, int width, int height, Component title, double value) {
            super(x, y, width, height, title, value);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int seconds = Math.round((float) this.value * 18) + 2;
            this.setMessage(Component.literal(
                    RadioModClient.t("Czas wyswietlania okienka: ", "Popup duration: ") + seconds + " sec"));
        }

        @Override
        protected void applyValue() {
            int seconds = Math.round((float) this.value * 18) + 2;
            modClient.setToastDuration(seconds);
        }
    }
}