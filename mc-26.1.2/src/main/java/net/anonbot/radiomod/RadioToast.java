package net.anonbot.radiomod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class RadioToast implements Toast {

    // Rozmiary: 0=small(200x32,icon20), 1=medium(230x46,icon36), 2=large(260x60,icon50), 3=XL(290x60,icon50)
    private static final int[] WIDTHS  = {200, 230, 260, 290};
    private static final int[] HEIGHTS = { 32,  46,  60,  60};
    private static final int[] ICONS   = { 20,  36,  50,  50};

    private final String stationName;
    private final String songName;
    private final String faviconUrl;
    private final RadioModClient modClient;
    private final int size; // 0/1/2
    private Visibility visibility = Visibility.SHOW;
    private long timeLeftMs;
    private long lastRenderTime;
    private boolean wasClicked = false;

    public RadioToast(String stationName, String songName, int durationSeconds,
                      String faviconUrl, RadioModClient modClient, int size) {
        this.stationName = stationName;
        this.songName = songName;
        this.faviconUrl = faviconUrl;
        this.modClient = modClient;
        this.size = Math.max(0, Math.min(3, size));
        this.timeLeftMs = durationSeconds * 1000L;
        this.lastRenderTime = -1;
    }

    @Override
    public int width() { return WIDTHS[size]; }

    @Override
    public int height() { return HEIGHTS[size]; }

    @Override
    public Visibility getWantedVisibility() { return this.visibility; }

    @Override
    public void update(ToastManager toastManager, long timeSinceLastVisible) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long timeSinceLastVisible) {
        long now = System.currentTimeMillis();
        if (lastRenderTime < 0) { lastRenderTime = now; return; }
        long delta = now - lastRenderTime;
        lastRenderTime = now;

        int W = WIDTHS[size];
        int H = HEIGHTS[size];
        int ICON = ICONS[size];

        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        double mouseX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();

        var pose = graphics.pose();
        float toastX = pose.m20();
        float toastY = pose.m21();

        boolean isHovered = mouseX >= toastX && mouseX <= toastX + W
                && mouseY >= toastY && mouseY <= toastY + H;

        if (!isHovered) timeLeftMs -= delta;
        if (timeLeftMs <= 0) this.visibility = Visibility.HIDE;

        graphics.fill(0, 0, W, H, 0xDD000000);

        // Ikona - artwork (album art) lub favicon
        int iconY = (H - ICON) / 2;
        String artworkUrl = (modClient != null) ? modClient.getCurrentArtworkUrl() : null;
        Identifier iconToDraw = null;
        if (artworkUrl != null && !artworkUrl.isEmpty()) {
            iconToDraw = RadioModClient.getIcon(artworkUrl);
        }
        if (iconToDraw == null && this.faviconUrl != null && !this.faviconUrl.isEmpty()) {
            iconToDraw = RadioModClient.getIcon(this.faviconUrl);
        }
        if (iconToDraw != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, iconToDraw, 5, iconY, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
        } else {
            renderFaviconOrFallback(graphics, ICON, iconY);
        }

        // Teksty
        int textX = ICON + 10;
        int maxTextW = W - textX - 15;
        int stationY = H / 2 - font.lineHeight - 2;
        int songY = H / 2 + 2;

        var collector = graphics.textRenderer();

        String safeStation = font.plainSubstrByWidth(this.stationName, maxTextW);
        collector.accept(textX, stationY, Component.literal("\u00a7a" + safeStation));

        if (this.songName != null) {
            int textWidth = font.width(this.songName);
            if (textWidth <= maxTextW) {
                collector.accept(textX, songY, Component.literal("\u00a7f" + this.songName));
            } else {
                // Gdy tekst za dlugi - przycinamy z ellipsis (zamiast scrollowania ktore w toast nie dziala)
                String cut = font.plainSubstrByWidth(this.songName, maxTextW - font.width("...")) + "...";
                collector.accept(textX, songY, Component.literal("\u00a7f" + cut));
            }
        }

        // Przycisk blokady - dolny prawy rog
        int btnW = 10, btnH = 10;
        int btnX = W - btnW - 3;
        int btnY = H - btnH - 3;
        boolean isBtnHovered = mouseX >= toastX + btnX && mouseX <= toastX + btnX + btnW
                && mouseY >= toastY + btnY && mouseY <= toastY + btnY + btnH;
        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, isBtnHovered ? 0xFFFF3333 : 0xAA880000);
        graphics.fill(btnX + 2, btnY + (btnH / 2) - 1, btnX + btnW - 2, btnY + (btnH / 2) + 1, 0xFFFFFFFF);

        long handle = Minecraft.getInstance().getWindow().handle();
        boolean isMouseLeftDown = GLFW.glfwGetMouseButton(handle,
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (isMouseLeftDown && !wasClicked) {
            wasClicked = true;
            if (isBtnHovered) {
                RadioModClient.getInstance().getBlacklist().add(this.songName);
                RadioModClient.getInstance().saveConfig();
                this.visibility = Visibility.HIDE;
            }
        } else if (!isMouseLeftDown) {
            wasClicked = false;
        }
    }

    private void renderFaviconOrFallback(GuiGraphicsExtractor graphics, int iconSize, int iconY) {
        if (this.faviconUrl != null && !this.faviconUrl.isEmpty()) {
            Identifier icon = RadioModClient.getIcon(this.faviconUrl);
            if (icon != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon, 5, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
                return;
            }
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, RadioModClient.FALLBACK_ICON, 5, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
    }
}