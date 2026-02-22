package net.anonbot.radiomod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        String on = RadioModClient.t("§aWŁĄCZONE", "§aON");
        String off = RadioModClient.t("§cWYŁĄCZONE", "§cOFF");

        this.addRenderableWidget(Button.builder(
                Component.literal(RadioModClient.t("Okienko w rogu (Toast): ", "Corner popup (Toast): ") + (modClient.isShowToast() ? on : off)),
                button -> {
                    modClient.setShowToast(!modClient.isShowToast());
                    if (this.minecraft != null) this.minecraft.setScreen(new RadioSettingsScreen(modClient, parentScreen));
                }
        ).bounds(centerX - 150, centerY - 55, 300, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(RadioModClient.t("Napis nad ekwipunkiem: ", "Action bar text: ") + (modClient.isShowActionBar() ? on : off)),
                button -> {
                    modClient.setShowActionBar(!modClient.isShowActionBar());
                    if (this.minecraft != null) this.minecraft.setScreen(new RadioSettingsScreen(modClient, parentScreen));
                }
        ).bounds(centerX - 150, centerY - 30, 300, 20).build());

        float initialValue = (modClient.getToastDuration() - 2) / 18.0f;
        this.addRenderableWidget(new DurationSlider(centerX - 150, centerY - 5, 300, 20, Component.empty(), initialValue));

        this.addRenderableWidget(Button.builder(RadioModClient.tc("⚙ Edytuj Czarną Listę Powiadomień", "⚙ Edit Notification Blacklist"), button -> {
            if (this.minecraft != null) this.minecraft.setScreen(new RadioBlacklistScreen(modClient, this));
        }).bounds(centerX - 150, centerY + 20, 300, 20).build());

        this.addRenderableWidget(Button.builder(RadioModClient.tc("Zapisz i Wróć", "Save & Return"), button -> {
            if (this.minecraft != null) this.minecraft.setScreen(parentScreen);
        }).bounds(centerX - 100, centerY + 55, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
    @Override public boolean isPauseScreen() { return false; }

    private class DurationSlider extends AbstractSliderButton {
        public DurationSlider(int x, int y, int width, int height, Component title, double value) {
            super(x, y, width, height, title, value); this.updateMessage();
        }
        @Override protected void updateMessage() {
            int seconds = Math.round((float)this.value * 18) + 2;
            this.setMessage(Component.literal(RadioModClient.t("Czas wyświetlania okienka: ", "Popup duration: ") + seconds + " sec"));
        }
        @Override protected void applyValue() {
            int seconds = Math.round((float)this.value * 18) + 2;
            modClient.setToastDuration(seconds);
        }
    }
}