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

    private final String stationName;
    private final String songName;
    private final String faviconUrl;
    private Visibility visibility = Visibility.SHOW;
    private long timeLeftMs;
    private long lastRenderTime;
    private boolean wasClicked = false;

    public RadioToast(String stationName, String songName, int durationSeconds, String faviconUrl) {
        this.stationName = stationName;
        this.songName = songName;
        this.faviconUrl = faviconUrl;
        this.timeLeftMs = durationSeconds * 1000L;
        this.lastRenderTime = net.minecraft.Util.getMillis();
    }

    @Override public Visibility getWantedVisibility() { return this.visibility; }
    @Override public void update(ToastManager toastManager, long timeSinceLastVisible) {}

    @Override
    public void render(GuiGraphics guiGraphics, Font font, long timeSinceLastVisible) {
        long now = net.minecraft.Util.getMillis();
        long delta = now - lastRenderTime;
        lastRenderTime = now;

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        Matrix4f matrix = guiGraphics.pose().last().pose();
        float toastX = matrix.m30();
        float toastY = matrix.m31();

        boolean isHovered = mouseX >= toastX && mouseX <= toastX + 160 && mouseY >= toastY && mouseY <= toastY + 32;

        if (!isHovered) timeLeftMs -= delta;
        if (timeLeftMs <= 0) this.visibility = Visibility.HIDE;

        // TŁO - czyste, ciemne, bez zielonych ramek!
        guiGraphics.fill(0, 0, 160, 32, 0xDD000000);
        int textX = 5;

        // IKONKA - logo stacji lub zastępcze logo moda
        if (this.faviconUrl != null && !this.faviconUrl.isEmpty()) {
            ResourceLocation icon = RadioModClient.getIcon(this.faviconUrl);
            if (icon != null) guiGraphics.blit(RenderType::guiTextured, icon, 5, 6, 0.0F, 0.0F, 20, 20, 20, 20);
            else guiGraphics.blit(RenderType::guiTextured, RadioModClient.FALLBACK_ICON, 5, 6, 0.0F, 0.0F, 20, 20, 20, 20);
            textX = 30;
        } else {
            guiGraphics.blit(RenderType::guiTextured, RadioModClient.FALLBACK_ICON, 5, 6, 0.0F, 0.0F, 20, 20, 20, 20);
            textX = 30;
        }

        // Tytuł - bezpiecznie skracany
        String safeStationName = font.plainSubstrByWidth(this.stationName, 160 - textX - 5);
        guiGraphics.drawString(font, safeStationName, textX, 5, 0x55FF55, false);

        // --- PRZYCISK BLOKADY PRZESUNIĘTY W DÓŁ (ZNAK ⛔) ---
        int btnX = 145;
        int btnY = 18;
        int btnW = 10;
        int btnH = 10;

        int maxTextWidth = 160 - textX - 20; // Rezerwujemy 20 pikseli z prawej na przycisk

        if (this.songName != null) {
            int textWidth = font.width(this.songName);
            if (textWidth <= maxTextWidth) {
                guiGraphics.drawString(font, this.songName, textX, 18, 0xFFFFFF, false);
            } else {
                double speed = 0.04;
                int overflow = textWidth - maxTextWidth;
                int totalScroll = overflow + 50;
                int offset = (int) ((now * speed) % (totalScroll * 2));
                int scrollX = offset;
                if (offset > totalScroll) scrollX = (totalScroll * 2) - offset;
                scrollX = Math.max(0, Math.min(scrollX, overflow));

                // Naprawiony Scissor (Teraz działa poprawnie i tekst nie znika)
                guiGraphics.enableScissor(textX, 18, textX + maxTextWidth, 18 + font.lineHeight);
                guiGraphics.drawString(font, this.songName, textX - scrollX, 18, 0xFFFFFF, false);
                guiGraphics.disableScissor();
            }
        }

        // Rysowanie znaczka blokady
        boolean isBtnHovered = mouseX >= toastX + btnX && mouseX <= toastX + btnX + btnW && mouseY >= toastY + btnY && mouseY <= toastY + btnY + btnH;
        if (isBtnHovered) guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xFFFF3333);
        else guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xAA880000);
        guiGraphics.fill(btnX + 2, btnY + (btnH / 2) - 1, btnX + btnW - 2, btnY + (btnH / 2) + 1, 0xFFFFFFFF);

        // Wykrywanie kliknięcia i blokowanie piosenki
        boolean isMouseLeftDown = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
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
}