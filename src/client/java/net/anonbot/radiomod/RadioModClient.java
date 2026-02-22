package net.anonbot.radiomod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RadioModClient implements ClientModInitializer {

    public static String t(String pl, String en) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().options == null) return en;
        return Minecraft.getInstance().options.languageCode.equals("pl_pl") ? pl : en;
    }
    public static Component tc(String pl, String en) {
        return Component.literal(t(pl, en));
    }

    private static RadioModClient INSTANCE;
    private static KeyMapping toggleRadioKey;
    private RadioPlayer currentPlayer = null;

    private float globalVolume = 0.5f;
    private boolean showToast = true;
    private boolean showActionBar = true;
    private int toastDuration = 5;
    private final List<String> blacklist = new ArrayList<>();

    private String currentStationName = "";
    private String currentStationUrl = "";
    private String currentStationFavicon = "";
    private String lastSongName = "";
    private int tickCounter = 0;

    public static final ResourceLocation FALLBACK_ICON = ResourceLocation.fromNamespaceAndPath("radio-mod", "icon.png");
    private static final Map<String, ResourceLocation> iconCache = new HashMap<>();
    private static final Set<String> downloadingIcons = new HashSet<>();

    public static ResourceLocation getIcon(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;

        // --- AUDYT (PUNKT 1): Zabezpieczenie przed protokołami file:// i ftp://
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) return null;

        if (iconCache.containsKey(urlStr)) return iconCache.get(urlStr);
        if (!downloadingIcons.contains(urlStr)) {
            downloadingIcons.add(urlStr);
            CompletableFuture.runAsync(() -> {
                try {
                    URL url = URI.create(urlStr).toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000); conn.setReadTimeout(3000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    try (InputStream is = conn.getInputStream()) {
                        NativeImage image = NativeImage.read(is);
                        Minecraft.getInstance().execute(() -> {
                            DynamicTexture texture = new DynamicTexture(image);
                            // --- AUDYT (PUNKT 6): Pancerne ID dla tekstury za pomocą UUID
                            String safeHash = UUID.nameUUIDFromBytes(urlStr.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
                            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("radiomod", "icon_" + safeHash);
                            Minecraft.getInstance().getTextureManager().register(id, texture);
                            iconCache.put(urlStr, id);
                        });
                    }
                } catch (Exception e) { Minecraft.getInstance().execute(() -> iconCache.put(urlStr, null)); }
            });
        }
        return null;
    }

    public static class FavoriteStation {
        public String name; public String url; public String favicon;
        public FavoriteStation(String name, String url, String favicon) { this.name = name; this.url = url; this.favicon = favicon; }
    }
    private final List<FavoriteStation> favorites = new ArrayList<>();

    public RadioModClient() { INSTANCE = this; }
    public static RadioModClient getInstance() { return INSTANCE; }
    public String getLastSongName() { return lastSongName; }

    @Override
    public void onInitializeClient() {
        loadConfig();
        toggleRadioKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("Otwórz Menu Radia", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "Moje Radio"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleRadioKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new RadioScreen(this));
            }
            if (currentPlayer != null && currentPlayer.isPlaying()) {
                tickCounter++;
                if (tickCounter >= 200) {
                    tickCounter = 0;
                    if (showToast || showActionBar) checkCurrentSong();
                }
            }
        });
    }

    public void playStation(String name, String url, String favicon) {
        // --- AUDYT (PUNKT 1): Zabezpieczenie na wypadek API przesyłającego dziwne linki
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) return;

        stopRadio();
        this.currentStationName = name; this.currentStationUrl = url; this.currentStationFavicon = favicon;
        this.lastSongName = null; this.tickCounter = 190;
        currentPlayer = new RadioPlayer(url);
        currentPlayer.setVolume(globalVolume);

        // --- AUDYT (PUNKT 2): Wątek Daemon (Bezpieczne wyłączanie z grą)
        Thread radioThread = new Thread(currentPlayer, "RadioMod-Player");
        radioThread.setDaemon(true);
        radioThread.start();

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(tc("§eŁączenie z nową stacją...", "§eConnecting to new station..."), true);
        }
    }

    public void stopRadio() {
        if (currentPlayer != null && currentPlayer.isPlaying()) {
            currentPlayer.stopRadio();
            this.currentStationUrl = ""; this.lastSongName = "";
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(tc("§cRadio wyłączone", "§cRadio stopped"), true);
            }
        }
    }

    public float getVolume() { return globalVolume; }
    public void setVolume(float volume) { this.globalVolume = volume; if (currentPlayer != null && currentPlayer.isPlaying()) currentPlayer.setVolume(volume); saveConfig(); }
    public boolean isShowToast() { return showToast; }
    public void setShowToast(boolean showToast) { this.showToast = showToast; saveConfig(); }
    public boolean isShowActionBar() { return showActionBar; }
    public void setShowActionBar(boolean showActionBar) { this.showActionBar = showActionBar; saveConfig(); }
    public int getToastDuration() { return toastDuration; }
    public void setToastDuration(int duration) { this.toastDuration = duration; saveConfig(); }
    public List<String> getBlacklist() { return blacklist; }

    public boolean isFavorite(String url) { return favorites.stream().anyMatch(f -> f.url.equals(url)); }
    public void toggleFavorite(String name, String url, String favicon) {
        if (isFavorite(url)) favorites.removeIf(f -> f.url.equals(url));
        else favorites.add(new FavoriteStation(name, url, favicon));
        saveConfig();
    }
    public List<FavoriteStation> getFavorites() { return favorites; }

    private boolean isBlacklisted(String song) {
        String lowerSong = song.toLowerCase();
        return blacklist.stream().anyMatch(word -> lowerSong.contains(word.toLowerCase()));
    }

    private String formatSongTitle(String title) {
        if (title == null || title.isEmpty()) return title;
        long upperCount = title.chars().filter(Character::isUpperCase).count();
        long letterCount = title.chars().filter(Character::isLetter).count();
        if (letterCount > 0 && (double) upperCount / letterCount > 0.6) {
            String[] words = title.toLowerCase().split(" ");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
            return sb.toString().trim();
        }
        return title;
    }

    private void checkCurrentSong() {
        CompletableFuture.runAsync(() -> {
            String fetchedSong = RadioPlayer.fetchCurrentSong(this.currentStationUrl);
            if (fetchedSong != null) {
                String rawSong = fetchedSong.trim().isEmpty() ? t("Audycja na żywo / Reklama", "Live stream / AD") : fetchedSong.trim();
                String newSong = formatSongTitle(rawSong);

                if (!newSong.equals(lastSongName)) {
                    lastSongName = newSong;
                    if (isBlacklisted(newSong)) return;

                    Minecraft.getInstance().execute(() -> {
                        if (showToast) {
                            Minecraft.getInstance().getToastManager().addToast(
                                    new RadioToast(currentStationName, newSong, toastDuration, currentStationFavicon)
                            );
                        }
                        if (showActionBar && Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.displayClientMessage(Component.literal("§b♪ " + newSong), true);
                        }
                    });
                }
            }
        });
    }

    public void saveConfig() {
        try {
            Path configPath = Path.of(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", "radiomod.json");
            Files.createDirectories(configPath.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("volume", globalVolume); json.addProperty("showToast", showToast);
            json.addProperty("showActionBar", showActionBar); json.addProperty("toastDuration", toastDuration);
            JsonArray favArray = new JsonArray();
            for (FavoriteStation fs : favorites) {
                JsonObject fObj = new JsonObject();
                fObj.addProperty("name", fs.name); fObj.addProperty("url", fs.url); fObj.addProperty("favicon", fs.favicon);
                favArray.add(fObj);
            }
            json.add("favorites", favArray);
            JsonArray blackArray = new JsonArray();
            for (String word : blacklist) blackArray.add(word);
            json.add("blacklist", blackArray);
            Files.writeString(configPath, json.toString());
        } catch (Exception e) {
            // --- AUDYT (PUNKT 3): Logowanie błędu zapisu
            System.err.println("[RadioMod] Błąd zapisu konfiguracji: " + e.getMessage());
        }
    }

    private void loadConfig() {
        try {
            Path configPath = Path.of(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", "radiomod.json");
            if (Files.exists(configPath)) {
                JsonObject json = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
                if (json.has("volume")) this.globalVolume = json.get("volume").getAsFloat();
                if (json.has("showToast")) this.showToast = json.get("showToast").getAsBoolean();
                if (json.has("showActionBar")) this.showActionBar = json.get("showActionBar").getAsBoolean();
                if (json.has("toastDuration")) this.toastDuration = json.get("toastDuration").getAsInt();

                favorites.clear();
                if (json.has("favorites")) {
                    for (JsonElement e : json.getAsJsonArray("favorites")) {
                        JsonObject fObj = e.getAsJsonObject();
                        String n = fObj.has("name") ? fObj.get("name").getAsString() : "Nieznana";
                        String u = fObj.has("url") ? fObj.get("url").getAsString() : "";
                        String favic = fObj.has("favicon") ? fObj.get("favicon").getAsString() : "";
                        if (!u.isEmpty()) favorites.add(new FavoriteStation(n, u, favic));
                    }
                }
                blacklist.clear();
                if (json.has("blacklist")) {
                    for (JsonElement e : json.getAsJsonArray("blacklist")) blacklist.add(e.getAsString());
                } else {
                    blacklist.add("reklama"); blacklist.add("audycja na żywo"); blacklist.add("ad");
                }
            }
        } catch (Exception e) {
            // --- AUDYT (PUNKT 3): Logowanie błędu odczytu
            System.err.println("[RadioMod] Błąd odczytu konfiguracji: " + e.getMessage());
        }
    }
}