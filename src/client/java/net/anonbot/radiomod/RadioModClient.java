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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RadioModClient implements ClientModInitializer {

    public static String t(String pl, String en) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().options == null)
            return en;
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
    private int toastSize = 1; // 0=small, 1=medium, 2=large
    private final LinkedList<String> songHistory = new LinkedList<>();
    private static final int MAX_HISTORY = 20;
    private final List<String> blacklist = new ArrayList<>();

    private String currentStationName = "";
    private String currentStationUrl = "";
    private String currentStationFavicon = "";
    private String lastSongName = "";
    private volatile String currentArtworkUrl = null;
    private int tickCounter = 0;

    public static final ResourceLocation FALLBACK_ICON = ResourceLocation.fromNamespaceAndPath("radio-mod", "icon.png");

    // POPRAWKA NPE: ConcurrentHashMap nie przyjmuje null jako wartosci!
    // Uzywamy Optional: empty = "brak obrazka", of(x) = "jest tekstura"
    private static final Map<String, Optional<ResourceLocation>> iconCache = new ConcurrentHashMap<>();
    private static final Set<String> downloadingIcons = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static ResourceLocation getIcon(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) return null;

        Optional<ResourceLocation> cached = iconCache.get(urlStr);
        if (cached != null) {
            return cached.orElse(null);
        }

        if (!downloadingIcons.contains(urlStr)) {
            downloadingIcons.add(urlStr);
            //System.out.println("[RadioMod] Rozpoczynam pobieranie obrazka: " + urlStr);
            CompletableFuture.runAsync(() -> {
                try {
                    URL url = URI.create(urlStr).toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    int code = conn.getResponseCode();
                    //System.out.println("[RadioMod] HTTP " + code + " dla obrazka: " + urlStr);
                    try (InputStream is = conn.getInputStream()) {
                        java.awt.image.BufferedImage buffered = javax.imageio.ImageIO.read(is);
                        if (buffered == null) {
                            iconCache.put(urlStr, Optional.empty());
                            System.err.println("[RadioMod] ImageIO nie rozpoznal formatu: " + urlStr);
                            return;
                        }
                        int w = buffered.getWidth();
                        int h = buffered.getHeight();
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(buffered, "png", baos);
                        NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(baos.toByteArray()));
                        Minecraft.getInstance().execute(() -> {
                            DynamicTexture texture = new DynamicTexture(image);
                            String safeHash = UUID.nameUUIDFromBytes(urlStr.getBytes(StandardCharsets.UTF_8))
                                    .toString().replace("-", "");
                            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("radiomod", "icon_" + safeHash);
                            Minecraft.getInstance().getTextureManager().register(id, texture);
                            iconCache.put(urlStr, Optional.of(id));
                            //System.out.println("[RadioMod] Obrazek zaladowany pomyslnie: " + id);
                        });
                    }
                } catch (Exception e) {
                    // POPRAWKA: Optional.empty() zamiast null - null rzucilby NPE!
                    iconCache.put(urlStr, Optional.empty());
                    System.err.println("[RadioMod] Blad pobierania obrazka [" + urlStr + "]: " + e.getMessage());
                }
            });
        }
        return null;
    }

    public static String fetchArtworkUrl(String songTitle) {
        if (songTitle == null || songTitle.isBlank()) return null;
        try {
            String query;
            int dashIdx = songTitle.indexOf(" - ");
            if (dashIdx > 0) {
                String artist = songTitle.substring(0, dashIdx).trim();
                String title  = songTitle.substring(dashIdx + 3).trim();
                query = artist + " " + title;
            } else {
                query = songTitle.trim();
            }

            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String apiUrl  = "https://itunes.apple.com/search?term=" + encoded + "&media=music&limit=1";
            //System.out.println("[RadioMod] Szukam okładki dla: \"" + query + "\"");

            URL url = URI.create(apiUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int code = conn.getResponseCode();
            //System.out.println("[RadioMod] iTunes API HTTP " + code);

            try (InputStream is = conn.getInputStream()) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray results = root.getAsJsonArray("results");

                if (results == null || results.size() == 0) {
                    //System.out.println("[RadioMod] iTunes: brak wynikow dla \"" + query + "\"");
                    return null;
                }

                JsonObject track = results.get(0).getAsJsonObject();
                String trackName  = track.has("trackName")  ? track.get("trackName").getAsString()  : "?";
                String artistName = track.has("artistName") ? track.get("artistName").getAsString() : "?";

                if (track.has("artworkUrl100")) {
                    String artworkUrl = track.get("artworkUrl100").getAsString()
                            .replace("100x100bb", "600x600bb");
                    //System.out.println("[RadioMod] Znaleziono okładke! Dopasowanie: \"" + artistName + " - " + trackName + "\"");
                    //System.out.println("[RadioMod] URL okładki: " + artworkUrl);
                    return artworkUrl;
                } else {
                    //System.out.println("[RadioMod] iTunes: brak artworkUrl100 dla \"" + query + "\"");
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            System.err.println("[RadioMod] Blad fetchArtworkUrl dla \"" + songTitle + "\": " + e.getMessage());
        }
        return null;
    }

    public String getCurrentArtworkUrl() { return currentArtworkUrl; }

    public static class FavoriteStation {
        public String name;
        public String url;
        public String favicon;
        public FavoriteStation(String name, String url, String favicon) {
            this.name = name; this.url = url; this.favicon = favicon;
        }
    }

    private final List<FavoriteStation> favorites = new ArrayList<>();

    public RadioModClient() { INSTANCE = this; }
    public static RadioModClient getInstance() { return INSTANCE; }
    public String getLastSongName() { return lastSongName; }

    @Override
    public void onInitializeClient() {
        loadConfig();
        toggleRadioKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("Otwórz Menu Radia", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "Moje Radio"));
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
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) return;
        stopRadio();
        this.currentStationName = name;
        this.currentStationUrl = url;
        this.currentStationFavicon = favicon;
        this.lastSongName = null;
        this.currentArtworkUrl = null;
        this.tickCounter = 190;
        currentPlayer = new RadioPlayer(url);
        currentPlayer.setVolume(globalVolume);
        Thread radioThread = new Thread(currentPlayer, "RadioMod-Player");
        radioThread.setDaemon(true);
        radioThread.start();
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(
                    tc("§eŁączenie z nową stacją...", "§eConnecting to new station..."), true);
    }

    public void stopRadio() {
        if (currentPlayer != null && currentPlayer.isPlaying()) {
            currentPlayer.stopRadio();
            this.currentStationUrl = "";
            this.lastSongName = "";
            this.currentArtworkUrl = null;
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(
                        tc("§cRadio wyłączone", "§cRadio stopped"), true);
        }
    }

    public float getVolume() { return globalVolume; }
    public void setVolume(float v) { this.globalVolume = v; if (currentPlayer != null && currentPlayer.isPlaying()) currentPlayer.setVolume(v); }
    public boolean isShowToast() { return showToast; }
    public void setShowToast(boolean v) { this.showToast = v; saveConfig(); }
    public boolean isShowActionBar() { return showActionBar; }
    public void setShowActionBar(boolean v) { this.showActionBar = v; saveConfig(); }
    public int getToastDuration() { return toastDuration; }
    public void setToastDuration(int d) { this.toastDuration = d; saveConfig(); }
    public int getToastSize() { return toastSize; }
    public void setToastSize(int size) { this.toastSize = size; saveConfig(); }
    public LinkedList<String> getSongHistory() { return songHistory; }
    public List<String> getBlacklist() { return blacklist; }
    public boolean isFavorite(String url) { return favorites.stream().anyMatch(f -> f.url.equals(url)); }
    public void toggleFavorite(String name, String url, String favicon) {
        if (isFavorite(url)) favorites.removeIf(f -> f.url.equals(url));
        else favorites.add(new FavoriteStation(name, url, favicon));
        saveConfig();
    }
    public List<FavoriteStation> getFavorites() { return favorites; }

    private boolean isBlacklisted(String song) {
        String lower = song.toLowerCase();
        return blacklist.stream().anyMatch(w -> lower.contains(w.toLowerCase()));
    }

    private String formatSongTitle(String title) {
        if (title == null || title.isEmpty()) return title;
        long up = title.chars().filter(Character::isUpperCase).count();
        long let = title.chars().filter(Character::isLetter).count();
        if (let > 0 && (double) up / let > 0.6) {
            String[] words = title.toLowerCase().split(" ");
            StringBuilder sb = new StringBuilder();
            for (String w : words) { if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" "); }
            return sb.toString().trim();
        }
        return title;
    }

    private void checkCurrentSong() {
        final String stationFavicon = this.currentStationFavicon;
        final String stationName   = this.currentStationName;
        final String stationUrl    = this.currentStationUrl;
        CompletableFuture.runAsync(() -> {
            String fetched = RadioPlayer.fetchCurrentSong(stationUrl);
            if (fetched != null) {
                String raw = fetched.trim().isEmpty() ? t("Audycja na żywo / Reklama", "Live stream / AD") : fetched.trim();
                String newSong = formatSongTitle(raw);
                if (!newSong.equals(lastSongName)) {
                    lastSongName = newSong;
                    // Dodaj do historii piosenek
                    if (!newSong.equals(t("Audycja na żywo / Reklama", "Live stream / AD"))) {
                        songHistory.remove(newSong); // unikaj duplikatow
                        songHistory.addFirst(newSong);
                        if (songHistory.size() > MAX_HISTORY) songHistory.removeLast();
                    }
                    //System.out.println("[RadioMod] Nowa piosenka wykryta: \"" + newSong + "\"");
                    if (isBlacklisted(newSong)) {
                        //System.out.println("[RadioMod] Piosenka na czarnej liscie, pomijam okładke.");
                        return;
                    }
                    currentArtworkUrl = null;
                    final String songForArtwork = newSong;
                    CompletableFuture.runAsync(() -> {
                        String artworkUrl = fetchArtworkUrl(songForArtwork);
                        if (songForArtwork.equals(lastSongName)) {
                            currentArtworkUrl = artworkUrl;
                            if (artworkUrl != null) {
                                //System.out.println("[RadioMod] Kickuje cache obrazka okładki...");
                                getIcon(artworkUrl);
                            } else {
                                //System.out.println("[RadioMod] Brak okładki — uzywam favicon stacji.");
                            }
                        } else {
                            //System.out.println("[RadioMod] Piosenka zmienila sie podczas szukania, pomijam okładke.");
                        }
                    });
                    Minecraft.getInstance().execute(() -> {
                        if (showToast) Minecraft.getInstance().getToastManager().addToast(
                                new RadioToast(stationName, newSong, toastDuration, stationFavicon, RadioModClient.this, toastSize));
                        if (showActionBar && Minecraft.getInstance().player != null)
                            Minecraft.getInstance().player.displayClientMessage(Component.literal("§b♪ " + newSong), true);
                    });
                }
            }
        });
    }

    public void saveConfig() {
        try {
            Path p = Path.of(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", "radiomod.json");
            Files.createDirectories(p.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("volume", globalVolume); json.addProperty("showToast", showToast);
            json.addProperty("toastSize", toastSize);
            json.addProperty("showActionBar", showActionBar); json.addProperty("toastDuration", toastDuration);
            JsonArray fa = new JsonArray();
            for (FavoriteStation fs : favorites) { JsonObject o = new JsonObject(); o.addProperty("name", fs.name); o.addProperty("url", fs.url); o.addProperty("favicon", fs.favicon); fa.add(o); }
            json.add("favorites", fa);
            JsonArray ba = new JsonArray(); for (String w : blacklist) ba.add(w); json.add("blacklist", ba);
            Files.writeString(p, json.toString());
        } catch (Exception e) { System.err.println("[RadioMod] Blad zapisu konfiguracji: " + e.getMessage()); }
    }

    private void loadConfig() {
        try {
            Path p = Path.of(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", "radiomod.json");
            if (Files.exists(p)) {
                JsonObject json = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                if (json.has("volume")) this.globalVolume = json.get("volume").getAsFloat();
                if (json.has("showToast")) this.showToast = json.get("showToast").getAsBoolean();
                if (json.has("showActionBar")) this.showActionBar = json.get("showActionBar").getAsBoolean();
                if (json.has("toastDuration")) this.toastDuration = json.get("toastDuration").getAsInt();
                if (json.has("toastSize")) this.toastSize = json.get("toastSize").getAsInt();
                favorites.clear();
                if (json.has("favorites")) for (JsonElement e : json.getAsJsonArray("favorites")) {
                    JsonObject o = e.getAsJsonObject();
                    String n = o.has("name") ? o.get("name").getAsString() : "Nieznana";
                    String u = o.has("url") ? o.get("url").getAsString() : "";
                    String f = o.has("favicon") ? o.get("favicon").getAsString() : "";
                    if (!u.isEmpty()) favorites.add(new FavoriteStation(n, u, f));
                }
                blacklist.clear();
                if (json.has("blacklist")) for (JsonElement e : json.getAsJsonArray("blacklist")) blacklist.add(e.getAsString());
                else { blacklist.add("reklama"); blacklist.add("audycja na żywo"); blacklist.add("ad"); }
            }
        } catch (Exception e) { System.err.println("[RadioMod] Blad odczytu konfiguracji: " + e.getMessage()); }
    }
}