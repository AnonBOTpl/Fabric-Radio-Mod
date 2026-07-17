package net.anonbot.radiomod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class RadioModMenuApi implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // POPRAWKA: null-check gdy mod nie zainicjalizował się poprawnie
        return parent -> {
            RadioModClient instance = RadioModClient.getInstance();
            if (instance == null)
                return null;
            return new RadioSettingsScreen(instance, parent);
        };
    }
}