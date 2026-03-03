package net.anonbot.radiomod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class RadioToast implements Toast {

    // Rozmiary: 0=small(160x32,icon20), 1=medium(190x46,icon36), 2=large(220x60,icon50)
    private static final int[] WIDTHS  = {160, 190, 220};
    private static final int[] HEIGHTS = { 32,  46,  60};
    private static final int[] ICONS   = { 20,  36,  50};

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
        this.size = Math.max(0, Math.min(2, size));
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
    public void render(GuiGraphics guiGraphics, Font font, long timeSinceLastVisible) {
        long now = net.minecraft.Util.getMillis();
        if (lastRenderTime < 0) { lastRenderTime = now; return; }
        long delta = now - lastRenderTime;
        lastRenderTime = now;

        int W = WIDTHS[size];
        int H = HEIGHTS[size];
        int ICON = ICONS[size];

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        Matrix4f matrix = guiGraphics.pose().last().pose();
        float toastX = matrix.m30();
        float toastY = matrix.m31();

        boolean isHovered = mouseX >= toastX && mouseX <= toastX + W
                && mouseY >= toastY && mouseY <= toastY + H;

        if (!isHovered) timeLeftMs -= delta;
        if (timeLeftMs <= 0) this.visibility = Visibility.HIDE;

        guiGraphics.fill(0, 0, W, H, 0xDD000000);

        // Ikona - okładka lub favicon
        int iconY = (H - ICON) / 2;
        String artworkUrl = (modClient != null) ? modClient.getCurrentArtworkUrl() : null;
        if (artworkUrl != null && !artworkUrl.isEmpty()) {
            ResourceLocation artworkIcon = RadioModClient.getIcon(artworkUrl);
            if (artworkIcon != null) {
                guiGraphics.blit(RenderType::guiTextured, artworkIcon, 5, iconY, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
            } else {
                renderFaviconOrFallback(guiGraphics, ICON, iconY);
            }
        } else {
            renderFaviconOrFallback(guiGraphics, ICON, iconY);
        }

        // Teksty
        int textX = ICON + 10;
        int maxTextW = W - textX - 15;
        int stationY = H / 2 - font.lineHeight - 2;
        int songY = H / 2 + 2;

        String safeStation = font.plainSubstrByWidth(this.stationName, maxTextW);
        guiGraphics.drawString(font, safeStation, textX, stationY, 0x55FF55, false);

        if (this.songName != null) {
            int textWidth = font.width(this.songName);
            if (textWidth <= maxTextW) {
                guiGraphics.drawString(font, this.songName, textX, songY, 0xFFFFFF, false);
            } else {
                double speed = 0.04;
                int overflow = textWidth - maxTextW;
                int totalScroll = overflow + 50;
                int offset = (int) ((now * speed) % (totalScroll * 2));
                int scrollX = offset > totalScroll ? (totalScroll * 2) - offset : offset;
                scrollX = Math.max(0, Math.min(scrollX, overflow));
                guiGraphics.enableScissor(textX, songY, textX + maxTextW, songY + font.lineHeight);
                guiGraphics.drawString(font, this.songName, textX - scrollX, songY, 0xFFFFFF, false);
                guiGraphics.disableScissor();
            }
        }

        // Przycisk blokady - dolny prawy rog
        int btnW = 10, btnH = 10;
        int btnX = W - btnW - 3;
        int btnY = H - btnH - 3;
        boolean isBtnHovered = mouseX >= toastX + btnX && mouseX <= toastX + btnX + btnW
                && mouseY >= toastY + btnY && mouseY <= toastY + btnY + btnH;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, isBtnHovered ? 0xFFFF3333 : 0xAA880000);
        guiGraphics.fill(btnX + 2, btnY + (btnH / 2) - 1, btnX + btnW - 2, btnY + (btnH / 2) + 1, 0xFFFFFFFF);

        boolean isMouseLeftDown = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),
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

    private void renderFaviconOrFallback(GuiGraphics guiGraphics, int iconSize, int iconY) {
        if (this.faviconUrl != null && !this.faviconUrl.isEmpty()) {
            ResourceLocation icon = RadioModClient.getIcon(this.faviconUrl);
            if (icon != null) {
                guiGraphics.blit(RenderType::guiTextured, icon, 5, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
                return;
            }
        }
        guiGraphics.blit(RenderType::guiTextured, RadioModClient.FALLBACK_ICON, 5, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
    }
}