package net.anonbot.radiomod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RadioScreen extends Screen {

    final RadioModClient modClient;
    private StationListWidget listWidget;
    private StringWidget titleWidget;

    private EditBox searchBox;
    private Button searchButton;
    private DropdownWidget countryDropdown;
    private DropdownWidget genreDropdown;

    boolean showFavoritesMode = false;

    private record FilterOption(String name, String value) {
    }

    private static final FilterOption[] COUNTRIES = {
            new FilterOption(RadioModClient.t("Cala Swiat", "Worldwide"), ""),
            new FilterOption(RadioModClient.t("Polska", "Poland"), "PL"),
            new FilterOption("USA", "US"),
            new FilterOption(RadioModClient.t("W. Brytania", "UK"), "GB"),
            new FilterOption(RadioModClient.t("Niemcy", "Germany"), "DE"),
            new FilterOption(RadioModClient.t("Francja", "France"), "FR"),
            new FilterOption(RadioModClient.t("Wlochy", "Italy"), "IT"),
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
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                RadioModClient.tc("Menu Radia Internetowego", "Internet Radio Menu"));
        this.modClient = modClient;
    }

    @Override
    protected void init() {
        clearWidgets();
        rebuildUI();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        rebuildUI();
    }

    private void rebuildUI() {
        int centerX = this.width / 2;

        // Title widget
        String titleText = showFavoritesMode ? RadioModClient.t("Moje Ulubione Stacje", "My Favorite Stations")
                : RadioModClient.t("Menu Radia Internetowego", "Internet Radio Menu");
        this.titleWidget = new StringWidget(Component.literal(titleText), this.font);
        this.titleWidget.setPosition((this.width - this.titleWidget.getWidth()) / 2, 10);
        this.addRenderableWidget(this.titleWidget);

        this.searchBox = new EditBox(this.font, centerX - 150, 30, 140, 20,
                RadioModClient.tc("Szukaj stacji...", "Search stations..."));
        this.addRenderableWidget(this.searchBox);

        this.searchButton = Button
                .builder(RadioModClient.tc("Szukaj", "Search"), button -> performSearch(this.searchBox.getValue()))
                .bounds(centerX - 5, 30, 70, 20).build();
        this.addRenderableWidget(this.searchButton);

        this.addRenderableWidget(Button.builder(RadioModClient.tc("\u2605 Ulubione", "\u2605 Favorites"), button -> {
            showFavoritesMode = !showFavoritesMode;
            if (showFavoritesMode) {
                button.setMessage(RadioModClient.tc("\uD83D\uDD0D Szukaj", "\uD83D\uDD0D Search"));
                searchBox.visible = false;
                searchButton.visible = false;
                countryDropdown.visible = false;
                genreDropdown.visible = false;
                loadFavorites();
            } else {
                button.setMessage(RadioModClient.tc("\u2605 Ulubione", "\u2605 Favorites"));
                searchBox.visible = true;
                searchButton.visible = true;
                countryDropdown.visible = true;
                genreDropdown.visible = true;
                performSearch(searchBox.getValue());
            }
        }).bounds(centerX + 70, 30, 80, 20).build());

        this.countryDropdown = new DropdownWidget(centerX - 150, 55, 145, 20, RadioModClient.t("Kraj: ", "Country: "),
                COUNTRIES, 0, () -> performSearch(searchBox.getValue()));
        this.addRenderableWidget(this.countryDropdown);

        this.genreDropdown = new DropdownWidget(centerX + 5, 55, 145, 20, RadioModClient.t("Gatunek: ", "Genre: "),
                GENRES, 0, () -> performSearch(searchBox.getValue()));
        this.addRenderableWidget(this.genreDropdown);

        this.listWidget = new StationListWidget(this.minecraft, this.width, this.height - 165, 85, 25);
        this.addRenderableWidget(this.listWidget);

        performSearch("");

        int bottomButtonsY = this.height - 52;
        int bW = 100;
        int bGap = 5;
        int totalW = bW * 3 + bGap * 2;
        int bStartX = centerX - totalW / 2;

        this.addRenderableWidget(
                Button.builder(RadioModClient.tc("\u23F9 Wylacz Radio", "\u23F9 Stop Radio"), button -> modClient.stopRadio())
                        .bounds(bStartX, bottomButtonsY, bW, 20).build());

        this.addRenderableWidget(
                Button.builder(RadioModClient.tc("\uD83C\uDFB5 Historia", "\uD83C\uDFB5 History"), button -> {
                    if (this.minecraft != null)
                        this.minecraft.setScreen(new RadioHistoryScreen(modClient, this));
                }).bounds(bStartX + bW + bGap, bottomButtonsY, bW, 20).build());

        this.addRenderableWidget(
                new VolumeSlider(bStartX + (bW + bGap) * 2, bottomButtonsY, bW, 20, Component.empty(), modClient.getVolume()));

        int bW2 = (totalW - bGap) / 2;
        this.addRenderableWidget(                Button.builder(RadioModClient.tc("\u2699 Ustawienia", "\u2699 Settings"), button -> {
            if (this.minecraft != null)
                this.minecraft.setScreen(new RadioSettingsScreen(modClient, this));
        }).bounds(bStartX, bottomButtonsY + 25, bW2, 20).build());

        this.addRenderableWidget(Button.builder(RadioModClient.tc("Zamknij Menu", "Close Menu"), button -> {
            if (this.minecraft != null)
                this.minecraft.setScreen(null);
        }).bounds(bStartX + bW2 + bGap, bottomButtonsY + 25, bW2, 20).build());
    }

    private void drawMarqueeText(GuiGraphicsExtractor context, Component text, int x, int y, int width, boolean isHovered) {
        var collector = context.textRenderer();
        String raw = text.getString();
        int textWidth = this.font.width(raw);
        if (textWidth <= width) {
            collector.accept(x, y, text);
        } else {
            if (isHovered) {
                long time = System.currentTimeMillis();
                double speed = 0.08;
                int overflow = textWidth - width;
                int totalScroll = overflow + 100;
                int offset = (int) ((time * speed) % (totalScroll * 2));
                int scrollX = offset;
                if (offset > totalScroll)
                    scrollX = (totalScroll * 2) - offset;
                scrollX = Math.max(0, Math.min(scrollX, overflow));
                // Uzywamy accept z TextAlignment i Parameters.withScissor (działa)
                var params = collector.defaultParameters().withScissor(x, y, x + width, y + this.font.lineHeight);
                collector.accept(TextAlignment.LEFT, x - scrollX, y, params, text);
            } else {
                String cut = this.font.plainSubstrByWidth(raw, width - this.font.width("...")) + "...";
                collector.accept(x, y, Component.literal(cut));
            }
        }
    }

    private void performSearch(String query) {
        listWidget.clearStations();
        listWidget.addStation(RadioModClient.t("Pobieranie stacji...", "Downloading stations..."), "", "");
        CompletableFuture.runAsync(() -> {
            try {
                String apiUrl = "https://all.api.radio-browser.info/json/stations/search?limit=50&hidebroken=true&order=clickcount&reverse=true&codec=MP3";

                FilterOption currentCountry = this.countryDropdown != null ? this.countryDropdown.getSelected()
                        : COUNTRIES[0];
                FilterOption currentGenre = this.genreDropdown != null ? this.genreDropdown.getSelected() : GENRES[0];

                if (!currentCountry.value().isEmpty())
                    apiUrl += "&countrycode=" + currentCountry.value();
                if (!currentGenre.value().isEmpty())
                    apiUrl += "&tag=" + URLEncoder.encode(currentGenre.value(), StandardCharsets.UTF_8);
                if (!query.trim().isEmpty())
                    apiUrl += "&name=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

                try (HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()) {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(apiUrl))
                            .timeout(Duration.ofSeconds(10))
                            .header("User-Agent", "MinecraftRadioMod/1.0")
                            .GET()
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();

                    Minecraft.getInstance().execute(() -> {
                        listWidget.clearStations();
                        if (array.isEmpty())
                            listWidget.addStation(RadioModClient.t("Brak wynikow.", "No results found."), "", "");
                        else {
                            for (JsonElement element : array) {
                                JsonObject obj = element.getAsJsonObject();
                                String stationName = obj.has("name") && !obj.get("name").isJsonNull()
                                        ? obj.get("name").getAsString().trim()
                                        : "Nieznana Stacja";
                                String streamUrl = obj.has("url_resolved") && !obj.get("url_resolved").isJsonNull()
                                        ? obj.get("url_resolved").getAsString()
                                        : "";
                                String faviconUrl = obj.has("favicon") && !obj.get("favicon").isJsonNull()
                                        ? obj.get("favicon").getAsString()
                                        : "";
                                if (!streamUrl.isEmpty())
                                    listWidget.addStation(stationName, streamUrl, faviconUrl);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    listWidget.clearStations();
                    listWidget.addStation(RadioModClient.t("Blad internetu.", "Internet error."), "", "");
                });
            }
        });
    }

    void loadFavorites() {
        listWidget.clearStations();
        List<RadioModClient.FavoriteStation> favs = modClient.getFavorites();
        if (favs.isEmpty()) {
            listWidget.addStation(RadioModClient.t("Brak ulubionych.", "No favorites yet."), "", "");
            listWidget.addStation(RadioModClient.t("Kliknij gwiazdke [\u2606] przy stacji, aby ja dodac!",
                    "Click the star [\u2606] to add one!"), "", "");
        } else
            for (RadioModClient.FavoriteStation fs : favs)
                listWidget.addStation(fs.name, fs.url, fs.favicon);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        // Półprzezroczyste tlo gradientowe (to samo co pause menu / mod menu)
        context.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xCF101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        // Aktualizuj tytul
        String titleText = showFavoritesMode ? RadioModClient.t("Moje Ulubione Stacje", "My Favorite Stations")
                : RadioModClient.t("Menu Radia Internetowego", "Internet Radio Menu");
        if (this.titleWidget != null) {
            if (!this.titleWidget.getMessage().getString().equals(titleText)) {
                this.titleWidget.setMessage(Component.literal(titleText));
            }
        }

        String currentSong = modClient.getLastSongName();
        if (currentSong != null && !currentSong.isEmpty()) {
            String songText = "\u266a " + currentSong;
            int barWidth = 310;
            int barX = (this.width / 2) - (barWidth / 2);
            int barY = this.height - 75;
            int barHeight = 20;

            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xAA000000);
            context.fill(barX, barY, barX + barWidth, barY + 1, 0xFF555555);
            context.fill(barX, barY + barHeight - 1, barX + barWidth, barY + barHeight, 0xFF555555);
            context.fill(barX, barY, barX + 1, barY + barHeight, 0xFF555555);
            context.fill(barX + barWidth - 1, barY, barX + barWidth, barY + barHeight, 0xFF555555);

            // Okladka piosenki po lewej stronie paska
            String artworkUrl = modClient.getCurrentArtworkUrl();
            int iconSize = 55;
            if (artworkUrl != null && !artworkUrl.isEmpty()) {
                Identifier artIcon = RadioModClient.getIcon(artworkUrl);
                Identifier displayIcon = artIcon != null ? artIcon : RadioModClient.FALLBACK_ICON;
                context.fill(barX - iconSize - 4, barY, barX - 2, barY + iconSize, 0xAA000000);
                context.fill(barX - iconSize - 4, barY, barX - 2, barY + 1, 0xFF555555);
                context.fill(barX - iconSize - 4, barY + iconSize - 1, barX - 2, barY + iconSize, 0xFF555555);
                context.fill(barX - iconSize - 4, barY, barX - iconSize - 3, barY + iconSize, 0xFF555555);
                context.fill(barX - 3, barY, barX - 2, barY + iconSize, 0xFF555555);
                context.blit(RenderPipelines.GUI_TEXTURED, displayIcon, barX - iconSize - 3, barY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
            }
            int textY = barY + (barHeight - this.font.lineHeight) / 2;
            drawMarqueeText(context, Component.literal("\u00a7a" + songText), barX + 5, textY, barWidth - 10, true);

        }

        // super po naszym kodzie — renderuje widgety (przyciski, listę)
        super.extractRenderState(context, mouseX, mouseY, partialTick);

        if (!showFavoritesMode) {
            if (countryDropdown != null)
                countryDropdown.renderPopup(context, mouseX, mouseY);
            if (genreDropdown != null)
                genreDropdown.renderPopup(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bool) {
        if (!showFavoritesMode) {
            if (countryDropdown != null && countryDropdown.isOpen)
                if (countryDropdown.handlePopupClick(event.x(), event.y(), event.button()))
                    return true;
            if (genreDropdown != null && genreDropdown.isOpen)
                if (genreDropdown.handlePopupClick(event.x(), event.y(), event.button()))
                    return true;
        }
        return super.mouseClicked(event, bool);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class DropdownWidget extends AbstractButton {
        private final String prefix;
        private final FilterOption[] options;
        private int selectedIndex;
        public boolean isOpen = false;
        private final Runnable onChange;

        public DropdownWidget(int x, int y, int width, int height, String prefix, FilterOption[] options,
                              int initialIndex, Runnable onChange) {
            super(x, y, width, height, Component.literal(prefix + options[initialIndex].name() + " \u25BC"));
            this.prefix = prefix;
            this.options = options;
            this.selectedIndex = initialIndex;
            this.onChange = onChange;
        }

        @Override
        public void onPress(InputWithModifiers modifiers) {
            if (!this.isOpen) {
                if (countryDropdown != null && countryDropdown != this)
                    countryDropdown.isOpen = false;
                if (genreDropdown != null && genreDropdown != this)
                    genreDropdown.isOpen = false;
            }
            this.isOpen = !this.isOpen;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
            // Render the dropdown button itself
            boolean isHovered = mouseX >= getX() && mouseX <= getX() + width && mouseY >= getY() && mouseY <= getY() + height;
            int bgColor = !active ? 0xFF333333 : (isHovered ? 0xFF555555 : 0xFF444444);
            int borderColor = !active ? 0xFF555555 : (isHovered ? 0xFFFFFFFF : 0xFF888888);

            // Background
            context.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bgColor);
            // Border
            context.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);               // top
            context.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor); // bottom
            context.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);                // left
            context.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);  // right
            // Arrow indicator
            String arrow = isOpen ? "\u25B2" : "\u25BC";
            String btnText = prefix + options[selectedIndex].name() + " " + arrow;
            String colorCode = !active ? "\u00a78" : "\u00a7f";
            var coll = context.textRenderer();
            coll.accept(getX() + 5, getY() + (height - RadioScreen.this.font.lineHeight) / 2,
                    Component.literal(colorCode + btnText));
        }

        public FilterOption getSelected() {
            return options[selectedIndex];
        }

        public void renderPopup(GuiGraphicsExtractor context, int mouseX, int mouseY) {
            if (!isOpen)
                return;
            int itemHeight = 15;
            int popupHeight = options.length * itemHeight;

            context.fill(getX(), getY() + height, getX() + width, getY() + height + popupHeight, 0xEE000000);
            context.fill(getX(), getY() + height, getX() + width, getY() + height + 1, 0xFF555555);
            context.fill(getX(), getY() + height + popupHeight - 1, getX() + width, getY() + height + popupHeight, 0xFF555555);
            context.fill(getX(), getY() + height, getX() + 1, getY() + height + popupHeight, 0xFF555555);
            context.fill(getX() + width - 1, getY() + height, getX() + width, getY() + height + popupHeight, 0xFF555555);

            for (int i = 0; i < options.length; i++) {
                int itemY = getY() + height + (i * itemHeight);
                boolean isHovered = mouseX >= getX() && mouseX <= getX() + width && mouseY >= itemY
                        && mouseY < itemY + itemHeight;
                if (isHovered)
                    context.fill(getX() + 1, itemY, getX() + width - 1, itemY + itemHeight, 0xFF555555);
                String code = (i == selectedIndex) ? "\u00a7e" : (isHovered ? "\u00a7f" : "\u00a77");
                var coll = context.textRenderer();
                coll.accept(getX() + 5, itemY + 4, Component.literal(code + options[i].name()));
            }
        }

        public boolean handlePopupClick(double mouseX, double mouseY, int button) {
            if (!isOpen || button != 0)
                return false;
            int popupHeight = options.length * 15;
            if (mouseX >= getX() && mouseX <= getX() + width && mouseY >= getY() + height
                    && mouseY < getY() + height + popupHeight) {
                int clickedIndex = (int) ((mouseY - (getY() + height)) / 15);
                if (clickedIndex >= 0 && clickedIndex < options.length) {
                    selectedIndex = clickedIndex;
                    this.setMessage(Component.literal(prefix + options[selectedIndex].name() + " \u25BC"));
                    isOpen = false;
                    onChange.run();
                    return true;
                }
            }
            if (mouseX >= getX() && mouseX <= getX() + width && mouseY >= getY() && mouseY < getY() + height)
                return false;
            isOpen = false;
            return true;
        }
    }

    private class StationListWidget extends ObjectSelectionList<StationListWidget.StationEntry> {
        public StationListWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 310;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
            // Ręczne renderowanie — tylko wpisy, żadnego tła
            context.enableScissor(getRowLeft(), getY(), getRowRight(), getBottom());
            var entries = children();
            for (int i = 0; i < entries.size(); i++) {
                StationEntry entry = entries.get(i);
                boolean isHovered = mouseX >= getRowLeft() && mouseX <= getRowRight()
                    && mouseY >= getRowTop(i) && mouseY < getRowBottom(i);
                entry.extractContent(context, mouseX, mouseY, isHovered, partialTick);
            }
            context.disableScissor();
        }

        public void clearStations() {
            super.clearEntries();
        }

        public void addStation(String name, String url, String favicon) {
            this.addEntry(new StationEntry(name, url, favicon));
        }

        private class StationEntry extends ObjectSelectionList.Entry<StationEntry> {
            final String name;
            final String url;
            final String favicon;

            StationEntry(String name, String url, String favicon) {
                this.name = name;
                this.url = url;
                this.favicon = favicon;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            java.util.List<StationEntry> entries = StationListWidget.this.children();
            int left = StationListWidget.this.getRowLeft();
            int top = StationListWidget.this.getRowTop(entries.indexOf(this));
            int width = StationListWidget.this.getRowWidth();
            int height = 25;

                if (isHovered && !this.url.isEmpty())
                    context.fill(left, top, left + width, top + height, 0x40FFFFFF);
                else if (StationListWidget.this.getSelected() == this)
                    context.fill(left, top, left + width, top + height, 0x60808080);

                int textOffset = 5;
                if (!this.url.isEmpty()) {
                    boolean isFav = RadioScreen.this.modClient.isFavorite(this.url);
                    boolean isStarHovered = mouseX >= left && mouseX <= left + 15 && mouseY >= top
                            && mouseY <= top + height;
                    String star = isFav ? "\u00a7e\u2605" : (isStarHovered ? "\u00a7f\u2606" : "\u00a78\u2606");
                    var collector = context.textRenderer();
                    collector.accept(left + 5, top + 5, Component.literal(star));

                    // Favicon stacji
                    if (this.favicon != null && !this.favicon.isEmpty()) {
                        Identifier icon = RadioModClient.getIcon(this.favicon);
                        if (icon != null)
                            context.blit(RenderPipelines.GUI_TEXTURED, icon, left + 20, top + 2, 0.0F, 0.0F, 20, 20, 20, 20);
                        else
                            context.blit(RenderPipelines.GUI_TEXTURED, RadioModClient.FALLBACK_ICON, left + 20, top + 2, 0.0F, 0.0F, 20, 20, 20, 20);
                    } else {
                        context.blit(RenderPipelines.GUI_TEXTURED, RadioModClient.FALLBACK_ICON, left + 20, top + 2, 0.0F, 0.0F, 20, 20, 20, 20);
                    }
                    textOffset = 45;
                }

                boolean isSelected = StationListWidget.this.getSelected() == this;
            String colorCode = (isHovered && !this.url.isEmpty()) || isSelected ? "\u00a7e" : "\u00a7f";
            drawMarqueeText(context, Component.literal(colorCode + this.name), left + textOffset, top + 5,
                    width - textOffset - 15, isHovered);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean bool) {
                if (this.url.isEmpty())
                    return false;
                if (event.x() >= StationListWidget.this.getRowLeft()
                        && event.x() <= StationListWidget.this.getRowLeft() + 15) {
                    RadioScreen.this.modClient.toggleFavorite(this.name, this.url, this.favicon);
                    if (RadioScreen.this.showFavoritesMode)
                        RadioScreen.this.loadFavorites();
                    return true;
                }
                if (event.button() == 0) {
                    StationListWidget.this.setSelected(this);
                    RadioScreen.this.modClient.playStation(this.name, this.url, this.favicon);
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.name);
            }
        }
    }

    private class VolumeSlider extends AbstractSliderButton {
        public VolumeSlider(int x, int y, int width, int height, Component title, double value) {
            super(x, y, width, height, title, value);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component
                    .literal(RadioModClient.t("Glosnosc: ", "Volume: ") + Math.round(this.value * 100.0) + "%"));
        }

        @Override
        protected void applyValue() {
            modClient.setVolume((float) this.value);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            modClient.saveConfig();
        }
    }
}
