package net.anonbot.radiomod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // === Współdzielony pool wątków i HttpClient ===
    private static final ExecutorService RADIO_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "RadioMod-Worker");
        t.setDaemon(true);
        return t;
    });
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(RADIO_EXECUTOR)
            .build();

    private RadioPlayer currentPlayer = null;
    private float globalVolume = 0.5f;
    private boolean showToast = true;
    private boolean showActionBar = true;
    private int toastDuration = 5;
    private int toastSize = 1;
    private boolean autoReconnect = true;
    private final LinkedList<String> songHistory = new LinkedList<>();
    private static final int MAX_HISTORY = 20;
    private final List<String> blacklist = new ArrayList<>();

    private String currentStationName = "";
    private String currentStationUrl = "";
    private String currentStationFavicon = "";
    private String lastSongName = "";
    private volatile String currentArtworkUrl = null;
    private int tickCounter = 0;

    public static final Identifier FALLBACK_ICON = Identifier.fromNamespaceAndPath("radio-mod", "icon.png");

    // === Cache ikon z obsługą retry przy przejściowych błędach ===
    private static final Map<String, Optional<Identifier>> iconCache = new ConcurrentHashMap<>();
    private static final Set<String> downloadingIcons = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, Integer> iconFailures = new ConcurrentHashMap<>();
    private static final Map<String, Long> iconFailureTime = new ConcurrentHashMap<>();
    private static final int MAX_ICON_RETRIES = 3;
    private static final long COOLDOWN_RETRY_MS = 60_000;   // 1 min między pojedynczymi próbami
    private static final long COOLDOWN_MAXED_MS = 300_000;  // 5 min po wyczerpaniu limitu (max martwych linków)

    public static Identifier getIcon(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) return null;

        // 1. Permanentny cache sukcesów — raz pobrana ikona zostaje na zawsze
        Optional<Identifier> cached = iconCache.get(urlStr);
        if (cached != null) {
            return cached.orElse(null); // Optional.empty() = permanent failure (martwy link/zły format)
        }

        // 2. Sprawdź limity prób i cooldowny
        Integer failCount = iconFailures.get(urlStr);
        if (failCount != null) {
            Long lastFail = iconFailureTime.get(urlStr);
            long elapsed = System.currentTimeMillis() - (lastFail != null ? lastFail : 0);
            if (failCount >= MAX_ICON_RETRIES) {
                // Wyczerpany limit prób — dłuższy cooldown, potem reset
                if (elapsed < COOLDOWN_MAXED_MS) return null;
                iconFailures.remove(urlStr);
                iconFailureTime.remove(urlStr);
            } else {
                // Pojedyncza próba — krótszy cooldown
                if (elapsed < COOLDOWN_RETRY_MS) return null;
            }
        }

        // 3. Sprawdź czy już trwa pobieranie
        if (downloadingIcons.contains(urlStr)) {
            return null;
        }

        downloadingIcons.add(urlStr);
        RADIO_EXECUTOR.submit(() -> {
            try {
                URL url = URI.create(urlStr).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                int code = conn.getResponseCode();
                try (InputStream is = conn.getInputStream()) {
                    java.awt.image.BufferedImage buffered = javax.imageio.ImageIO.read(is);
                    if (buffered == null) {
                        // ImageIO nie rozpoznało formatu — to permanentny błąd (martwy link)
                        iconCache.put(urlStr, Optional.empty());
                        iconFailures.remove(urlStr);
                        iconFailureTime.remove(urlStr);
                        downloadingIcons.remove(urlStr);
                        System.err.println("[RadioMod] ImageIO nie rozpoznal formatu: " + urlStr);
                        return;
                    }
                    int w = buffered.getWidth();
                    int h = buffered.getHeight();
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(buffered, "png", baos);
                    NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(baos.toByteArray()));
                    Minecraft.getInstance().execute(() -> {
                        DynamicTexture texture = new DynamicTexture(() -> "radiomod_icon", image);
                        String safeHash = UUID.nameUUIDFromBytes(urlStr.getBytes(StandardCharsets.UTF_8))
                                .toString().replace("-", "");
                        Identifier id = Identifier.fromNamespaceAndPath("radiomod", "icon_" + safeHash);
                        Minecraft.getInstance().getTextureManager().register(id, texture);
                        // Sukces — permanentny cache
                        iconCache.put(urlStr, Optional.of(id));
                        // Wyczyść failure tracking + dopiero teraz odblokuj URL
                        iconFailures.remove(urlStr);
                        iconFailureTime.remove(urlStr);
                        downloadingIcons.remove(urlStr);
                    });
                }
            } catch (Exception e) {
                downloadingIcons.remove(urlStr);
                // Przejściowa porażka — zliczamy próbę i damy retry później
                iconFailures.merge(urlStr, 1, Integer::sum);
                iconFailureTime.put(urlStr, System.currentTimeMillis());
                System.err.println("[RadioMod] Blad pobierania obrazka [" + urlStr + "]: " + e.getMessage());
            }
        });
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

            URL url = URI.create(apiUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int code = conn.getResponseCode();

            try (InputStream is = conn.getInputStream()) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray results = root.getAsJsonArray("results");

                if (results == null || results.size() == 0) {
                    return null;
                }

                JsonObject track = results.get(0).getAsJsonObject();

                if (track.has("artworkUrl100")) {
                    String artworkUrl = track.get("artworkUrl100").getAsString()
                            .replace("100x100bb", "600x600bb");
                    return artworkUrl;
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

    public boolean isAutoReconnect() { return autoReconnect; }
    public void setAutoReconnect(boolean v) { this.autoReconnect = v; saveConfig(); }

    @Override
    public void onInitializeClient() {
        loadConfig();
        toggleRadioKey = new KeyMapping(
                "Otwórz Menu Radia", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, Category.MISC);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleRadioKey.consumeClick()) {
                if (client.gui.screen() == null) client.setScreenAndShow(new RadioScreen(this));
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
        currentPlayer.setAutoReconnect(autoReconnect);
        final RadioPlayer capturedPlayer = currentPlayer;
        currentPlayer.setOnDisconnected(() -> {
            Minecraft.getInstance().execute(() -> {
                // Sprawdź, czy to wciąż ten sam player (użytkownik mógł przełączyć stację)
                if (currentPlayer == capturedPlayer && !capturedPlayer.isPlaying()) {
                    currentStationUrl = "";
                    currentStationName = "";
                    currentStationFavicon = "";
                    lastSongName = "";
                    currentArtworkUrl = null;
                    if (Minecraft.getInstance().player != null)
                        Minecraft.getInstance().gui.hud.setOverlayMessage(
                                tc("§cUtracono połączenie ze stacją", "§cLost connection to station"), false);
                }
            });
        });
        RADIO_EXECUTOR.submit(currentPlayer);
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().gui.hud.setOverlayMessage(
                    tc("§eŁączenie z nową stacją...", "§eConnecting to new station..."), false);
    }

    public void stopRadio() {
        if (currentPlayer != null && currentPlayer.isPlaying()) {
            currentPlayer.stopRadio();
            this.currentStationUrl = "";
            this.lastSongName = "";
            this.currentArtworkUrl = null;
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().gui.hud.setOverlayMessage(
                        tc("§cRadio wyłączone", "§cRadio stopped"), false);
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
        RADIO_EXECUTOR.submit(() -> {
            String fetched = RadioPlayer.fetchCurrentSong(stationUrl);
            if (fetched != null) {
                String raw = fetched.trim().isEmpty() ? t("Audycja na żywo / Reklama", "Live stream / AD") : fetched.trim();
                String newSong = formatSongTitle(raw);
                if (!newSong.equals(lastSongName)) {
                    lastSongName = newSong;
                    if (!newSong.equals(t("Audycja na żywo / Reklama", "Live stream / AD"))) {
                        songHistory.remove(newSong);
                        songHistory.addFirst(newSong);
                        if (songHistory.size() > MAX_HISTORY) songHistory.removeLast();
                    }
                    if (isBlacklisted(newSong)) {
                        return;
                    }
                    currentArtworkUrl = null;
                    final String songForArtwork = newSong;
                    RADIO_EXECUTOR.submit(() -> {
                        String artworkUrl = fetchArtworkUrl(songForArtwork);
                        if (songForArtwork.equals(lastSongName)) {
                            currentArtworkUrl = artworkUrl;
                            if (artworkUrl != null) {
                                getIcon(artworkUrl);
                            }
                        }
                    });
                    Minecraft.getInstance().execute(() -> {
                        if (showToast) Minecraft.getInstance().gui.toastManager().addToast(
                                new RadioToast(stationName, newSong, toastDuration, stationFavicon, RadioModClient.this, toastSize));
                        if (showActionBar && Minecraft.getInstance().player != null)
                            Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("§b♪ " + newSong), false);
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
            json.addProperty("volume", globalVolume);
            json.addProperty("showToast", showToast);
            json.addProperty("showActionBar", showActionBar);
            json.addProperty("toastDuration", toastDuration);
            json.addProperty("toastSize", toastSize);
            json.addProperty("autoReconnect", autoReconnect);
            JsonArray fa = new JsonArray();
            for (FavoriteStation fs : favorites) {
                JsonObject o = new JsonObject();
                o.addProperty("name", fs.name);
                o.addProperty("url", fs.url);
                o.addProperty("favicon", fs.favicon);
                fa.add(o);
            }
            json.add("favorites", fa);
            JsonArray ba = new JsonArray();
            for (String w : blacklist) ba.add(w);
            json.add("blacklist", ba);
            Files.writeString(p, json.toString());
        } catch (Exception e) {
            System.err.println("[RadioMod] Blad zapisu konfiguracji: " + e.getMessage());
        }
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
                if (json.has("autoReconnect")) this.autoReconnect = json.get("autoReconnect").getAsBoolean();
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
        } catch (Exception e) {
            System.err.println("[RadioMod] Blad odczytu konfiguracji: " + e.getMessage());
        }
    }
}
