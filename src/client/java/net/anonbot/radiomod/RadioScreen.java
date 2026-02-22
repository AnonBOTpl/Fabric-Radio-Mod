package net.anonbot.radiomod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RadioScreen extends Screen {

    final RadioModClient modClient;
    private StationListWidget listWidget;

    private EditBox searchBox;
    private Button searchButton;
    private DropdownWidget countryDropdown;
    private DropdownWidget genreDropdown;

    boolean showFavoritesMode = false;

    private record FilterOption(String name, String value) {}

    private static final FilterOption[] COUNTRIES = {
            new FilterOption(RadioModClient.t("Cały Świat", "Worldwide"), ""),
            new FilterOption(RadioModClient.t("Polska", "Poland"), "PL"),
            new FilterOption("USA", "US"),
            new FilterOption(RadioModClient.t("W. Brytania", "UK"), "GB"),
            new FilterOption(RadioModClient.t("Niemcy", "Germany"), "DE"),
            new FilterOption(RadioModClient.t("Francja", "France"), "FR"),
            new FilterOption(RadioModClient.t("Włochy", "Italy"), "IT"),
            new FilterOption(RadioModClient.t("Japonia", "Japan"), "JP")
    };

    private static final FilterOption[] GENRES = {
            new FilterOption(RadioModClient.t("Wszystkie", "All"), ""),
            new FilterOption("Pop", "pop"),
            new FilterOption("Rock", "rock"),
            new FilterOption("Hip Hop", "hiphop"),
            new FilterOption("Electronic", "electronic"),
            new FilterOption("Jazz", "jazz"),
            new FilterOption("Classical", "classical"),
            new FilterOption("News", "news")
    };

    public RadioScreen(RadioModClient modClient) {
        super(RadioModClient.tc("Menu Radia Internetowego", "Internet Radio Menu"));
        this.modClient = modClient;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        this.searchBox = new EditBox(this.font, centerX - 150, 30, 140, 20, RadioModClient.tc("Szukaj stacji...", "Search stations..."));
        this.addRenderableWidget(this.searchBox);

        this.searchButton = Button.builder(RadioModClient.tc("Szukaj", "Search"), button -> performSearch(this.searchBox.getValue()))
                .bounds(centerX - 5, 30, 70, 20).build();
        this.addRenderableWidget(this.searchButton);

        this.addRenderableWidget(Button.builder(RadioModClient.tc("★ Ulubione", "★ Favorites"), button -> {
            showFavoritesMode = !showFavoritesMode;
            if (showFavoritesMode) {
                button.setMessage(RadioModClient.tc("🔍 Szukaj", "🔍 Search"));
                searchBox.visible = false; searchButton.visible = false;
                countryDropdown.visible = false; genreDropdown.visible = false;
                loadFavorites();
            } else {
                button.setMessage(RadioModClient.tc("★ Ulubione", "★ Favorites"));
                searchBox.visible = true; searchButton.visible = true;
                countryDropdown.visible = true; genreDropdown.visible = true;
                performSearch(searchBox.getValue());
            }
        }).bounds(centerX + 70, 30, 80, 20).build());

        // DOMYŚLNY INDEKS TO 0 ("Cały Świat")
        this.countryDropdown = new DropdownWidget(centerX - 150, 55, 145, 20, RadioModClient.t("Kraj: ", "Country: "), COUNTRIES, 0, () -> performSearch(searchBox.getValue()));
        this.addRenderableWidget(this.countryDropdown);

        this.genreDropdown = new DropdownWidget(centerX + 5, 55, 145, 20, RadioModClient.t("Gatunek: ", "Genre: "), GENRES, 0, () -> performSearch(searchBox.getValue()));
        this.addRenderableWidget(this.genreDropdown);

        this.listWidget = new StationListWidget(this.minecraft, this.width, this.height - 165, 85, 25);
        this.addRenderableWidget(this.listWidget);

        performSearch("");

        int bottomButtonsY = this.height - 50;

        this.addRenderableWidget(Button.builder(RadioModClient.tc("⏹ Wyłącz Radio", "⏹ Stop Radio"), button -> modClient.stopRadio())
                .bounds(centerX - 155, bottomButtonsY, 150, 20).build());

        this.addRenderableWidget(new VolumeSlider(centerX + 5, bottomButtonsY, 150, 20, Component.empty(), modClient.getVolume()));

        this.addRenderableWidget(Button.builder(RadioModClient.tc("⚙ Ustawienia", "⚙ Settings"), button -> {
            if (this.minecraft != null) this.minecraft.setScreen(new RadioSettingsScreen(modClient, this));
        }).bounds(centerX - 155, bottomButtonsY + 25, 150, 20).build());

        this.addRenderableWidget(Button.builder(RadioModClient.tc("Zamknij Menu", "Close Menu"), button -> {
            if (this.minecraft != null) this.minecraft.setScreen(null);
        }).bounds(centerX + 5, bottomButtonsY + 25, 150, 20).build());
    }

    private void drawMarqueeText(GuiGraphics guiGraphics, String text, int x, int y, int width, int color, boolean isHovered) {
        int textWidth = this.font.width(text);
        if (textWidth <= width) {
            guiGraphics.drawString(this.font, text, x, y, color, false);
        } else {
            if (isHovered) {
                long time = net.minecraft.Util.getMillis();
                double speed = 0.08;
                int overflow = textWidth - width;
                int totalScroll = overflow + 100;
                int offset = (int) ((time * speed) % (totalScroll * 2));
                int scrollX = offset;
                if (offset > totalScroll) scrollX = (totalScroll * 2) - offset;
                scrollX = Math.max(0, Math.min(scrollX, overflow));
                guiGraphics.enableScissor(x, y, x + width, y + this.font.lineHeight);
                guiGraphics.drawString(this.font, text, x - scrollX, y, color, false);
                guiGraphics.disableScissor();
            } else {
                String cut = this.font.plainSubstrByWidth(text, width - this.font.width("...")) + "...";
                guiGraphics.drawString(this.font, cut, x, y, color, false);
            }
        }
    }

    private void performSearch(String query) {
        listWidget.clearStations();
        listWidget.addStation(RadioModClient.t("Pobieranie stacji...", "Downloading stations..."), "", "");
        CompletableFuture.runAsync(() -> {
            try {
                String apiUrl = "https://de1.api.radio-browser.info/json/stations/search?limit=50&hidebroken=true&order=clickcount&reverse=true&codec=MP3";

                FilterOption currentCountry = this.countryDropdown != null ? this.countryDropdown.getSelected() : COUNTRIES[0];
                FilterOption currentGenre = this.genreDropdown != null ? this.genreDropdown.getSelected() : GENRES[0];

                if (!currentCountry.value().isEmpty()) apiUrl += "&countrycode=" + currentCountry.value();
                if (!currentGenre.value().isEmpty()) apiUrl += "&tag=" + URLEncoder.encode(currentGenre.value(), StandardCharsets.UTF_8);
                if (!query.trim().isEmpty()) apiUrl += "&name=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

                try (HttpClient client = HttpClient.newHttpClient()) {
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).header("User-Agent", "MinecraftRadioMod/1.0").GET().build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();

                    Minecraft.getInstance().execute(() -> {
                        listWidget.clearStations();
                        if (array.isEmpty()) listWidget.addStation(RadioModClient.t("Brak wyników.", "No results found."), "", "");
                        else {
                            for (JsonElement element : array) {
                                JsonObject obj = element.getAsJsonObject();
                                String stationName = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString().trim() : "Nieznana Stacja";
                                String streamUrl = obj.has("url_resolved") && !obj.get("url_resolved").isJsonNull() ? obj.get("url_resolved").getAsString() : "";
                                String faviconUrl = obj.has("favicon") && !obj.get("favicon").isJsonNull() ? obj.get("favicon").getAsString() : "";
                                if (!streamUrl.isEmpty()) listWidget.addStation(stationName, streamUrl, faviconUrl);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    listWidget.clearStations();
                    listWidget.addStation(RadioModClient.t("Błąd internetu.", "Internet error."), "", "");
                });
            }
        });
    }

    void loadFavorites() {
        listWidget.clearStations();
        List<RadioModClient.FavoriteStation> favs = modClient.getFavorites();
        if (favs.isEmpty()) {
            listWidget.addStation(RadioModClient.t("Brak ulubionych.", "No favorites yet."), "", "");
            listWidget.addStation(RadioModClient.t("Kliknij gwiazdkę [☆] przy stacji, aby ją dodać!", "Click the star [☆] to add one!"), "", "");
        }
        else for (RadioModClient.FavoriteStation fs : favs) listWidget.addStation(fs.name, fs.url, fs.favicon);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        String titleText = showFavoritesMode ? RadioModClient.t("Moje Ulubione Stacje", "My Favorite Stations") : RadioModClient.t("Menu Radia Internetowego", "Internet Radio Menu");
        guiGraphics.drawCenteredString(this.font, titleText, this.width / 2, 10, 0xFFFFFF);

        String currentSong = modClient.getLastSongName();
        if (currentSong != null && !currentSong.isEmpty()) {
            String songText = "♪ " + currentSong;
            int barWidth = 310;
            int barX = (this.width / 2) - (barWidth / 2);
            int barY = this.height - 75;
            int barHeight = 20;

            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xAA000000);
            guiGraphics.fill(barX, barY, barX + barWidth, barY + 1, 0xFF555555);
            guiGraphics.fill(barX, barY + barHeight - 1, barX + barWidth, barY + barHeight, 0xFF555555);
            guiGraphics.fill(barX, barY, barX + 1, barY + barHeight, 0xFF555555);
            guiGraphics.fill(barX + barWidth - 1, barY, barX + barWidth, barY + barHeight, 0xFF555555);

            int textY = barY + (barHeight - this.font.lineHeight) / 2;
            drawMarqueeText(guiGraphics, songText, barX + 5, textY, barWidth - 10, 0x55FF55, true);
        }

        if (!showFavoritesMode) {
            if (countryDropdown != null) countryDropdown.renderPopup(guiGraphics, mouseX, mouseY);
            if (genreDropdown != null) genreDropdown.renderPopup(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!showFavoritesMode) {
            if (countryDropdown != null && countryDropdown.isOpen) if (countryDropdown.handlePopupClick(mouseX, mouseY, button)) return true;
            if (genreDropdown != null && genreDropdown.isOpen) if (genreDropdown.handlePopupClick(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private class DropdownWidget extends AbstractButton {
        private final String prefix;
        private final FilterOption[] options;
        private int selectedIndex;
        public boolean isOpen = false;
        private final Runnable onChange;

        public DropdownWidget(int x, int y, int width, int height, String prefix, FilterOption[] options, int initialIndex, Runnable onChange) {
            super(x, y, width, height, Component.literal(prefix + options[initialIndex].name() + " ▼"));
            this.prefix = prefix; this.options = options; this.selectedIndex = initialIndex; this.onChange = onChange;
        }

        @Override
        public void onPress() {
            if (!this.isOpen) {
                if (countryDropdown != null && countryDropdown != this) countryDropdown.isOpen = false;
                if (genreDropdown != null && genreDropdown != this) genreDropdown.isOpen = false;
            }
            this.isOpen = !this.isOpen;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
        public FilterOption getSelected() { return options[selectedIndex]; }

        public void renderPopup(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            if (!isOpen) return;
            int itemHeight = 15;
            int popupHeight = options.length * itemHeight;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 1000);

            guiGraphics.fill(getX(), getY() + height, getX() + width, getY() + height + popupHeight, 0xEE000000);
            guiGraphics.fill(getX(), getY() + height, getX() + width, getY() + height + 1, 0xFF555555);
            guiGraphics.fill(getX(), getY() + height + popupHeight - 1, getX() + width, getY() + height + popupHeight, 0xFF555555);
            guiGraphics.fill(getX(), getY() + height, getX() + 1, getY() + height + popupHeight, 0xFF555555);
            guiGraphics.fill(getX() + width - 1, getY() + height, getX() + width, getY() + height + popupHeight, 0xFF555555);

            for (int i = 0; i < options.length; i++) {
                int itemY = getY() + height + (i * itemHeight);
                boolean isHovered = mouseX >= getX() && mouseX <= getX() + width && mouseY >= itemY && mouseY < itemY + itemHeight;
                if (isHovered) guiGraphics.fill(getX() + 1, itemY, getX() + width - 1, itemY + itemHeight, 0xFF555555);
                int textColor = (i == selectedIndex) ? 0xFFFFFF00 : (isHovered ? 0xFFFFFFFF : 0xFFAAAAAA);
                guiGraphics.drawString(Minecraft.getInstance().font, options[i].name(), getX() + 5, itemY + 4, textColor, false);
            }
            guiGraphics.pose().popPose();
        }

        public boolean handlePopupClick(double mouseX, double mouseY, int button) {
            if (!isOpen || button != 0) return false;
            int popupHeight = options.length * 15;
            if (mouseX >= getX() && mouseX <= getX() + width && mouseY >= getY() + height && mouseY < getY() + height + popupHeight) {
                int clickedIndex = (int) ((mouseY - (getY() + height)) / 15);
                if (clickedIndex >= 0 && clickedIndex < options.length) {
                    selectedIndex = clickedIndex;
                    this.setMessage(Component.literal(prefix + options[selectedIndex].name() + " ▼"));
                    isOpen = false; onChange.run(); return true;
                }
            }
            if (mouseX >= getX() && mouseX <= getX() + width && mouseY >= getY() && mouseY < getY() + height) return false;
            isOpen = false; return true;
        }
    }

    private class StationListWidget extends ObjectSelectionList<StationListWidget.StationEntry> {
        public StationListWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        @Override public int getRowWidth() { return 310; }
        public void clearStations() { super.clearEntries(); }
        public void addStation(String name, String url, String favicon) { this.addEntry(new StationEntry(name, url, favicon)); }

        private class StationEntry extends ObjectSelectionList.Entry<StationEntry> {
            final String name; final String url; final String favicon;

            StationEntry(String name, String url, String favicon) { this.name = name; this.url = url; this.favicon = favicon; }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
                if (isHovered && !this.url.isEmpty()) guiGraphics.fill(left, top, left + width, top + height, 0x40FFFFFF);
                else if (StationListWidget.this.getSelected() == this) guiGraphics.fill(left, top, left + width, top + height, 0x60808080);

                int textOffset = 5;
                if (!this.url.isEmpty()) {
                    boolean isFav = RadioScreen.this.modClient.isFavorite(this.url);
                    boolean isStarHovered = mouseX >= left && mouseX <= left + 15 && mouseY >= top && mouseY <= top + height;
                    String star = isFav ? "§e★" : (isStarHovered ? "§f☆" : "§8☆");
                    guiGraphics.drawString(Minecraft.getInstance().font, star, left + 5, top + 5, 0xFFFFFF, false);

                    if (this.favicon != null && !this.favicon.isEmpty()) {
                        ResourceLocation icon = RadioModClient.getIcon(this.favicon);
                        if (icon != null) guiGraphics.blit(RenderType::guiTextured, icon, left + 20, top + 2, 0.0F, 0.0F, 20, 20, 20, 20);
                        else guiGraphics.blit(RenderType::guiTextured, RadioModClient.FALLBACK_ICON, left + 20, top + 2, 0.0F, 0.0F, 20, 20, 20, 20);
                    } else {
                        guiGraphics.blit(RenderType::guiTextured, RadioModClient.FALLBACK_ICON, left + 20, top + 2, 0.0F, 0.0F, 20, 20, 20, 20);
                    }
                    textOffset = 45;
                }

                int textColor = isHovered && !this.url.isEmpty() ? 0xFFFF55 : 0xFFFFFF;
                drawMarqueeText(guiGraphics, this.name, left + textOffset, top + 5, width - textOffset - 15, textColor, isHovered);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (this.url.isEmpty()) return false;
                if (mouseX >= StationListWidget.this.getRowLeft() && mouseX <= StationListWidget.this.getRowLeft() + 15) {
                    RadioScreen.this.modClient.toggleFavorite(this.name, this.url, this.favicon);
                    if (RadioScreen.this.showFavoritesMode) RadioScreen.this.loadFavorites();
                    return true;
                }
                if (button == 0) {
                    StationListWidget.this.setSelected(this);
                    RadioScreen.this.modClient.playStation(this.name, this.url, this.favicon);
                    return true;
                }
                return false;
            }

            @Override public Component getNarration() { return Component.literal(this.name); }
        }
    }

    private class VolumeSlider extends AbstractSliderButton {
        public VolumeSlider(int x, int y, int width, int height, Component title, double value) {
            super(x, y, width, height, title, value); this.updateMessage();
        }
        @Override protected void updateMessage() { this.setMessage(Component.literal(RadioModClient.t("Głośność: ", "Volume: ") + Math.round(this.value * 100.0) + "%")); }
        @Override protected void applyValue() { modClient.setVolume((float) this.value); }
    }
}