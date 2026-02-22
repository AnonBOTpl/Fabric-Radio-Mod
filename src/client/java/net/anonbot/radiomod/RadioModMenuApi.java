package net.anonbot.radiomod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class RadioModMenuApi implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Zwracamy nasze okienko ustawień, podając ModClienta oraz ekran-rodzica (żeby wiedział gdzie wrócić)
        return parent -> new RadioSettingsScreen(RadioModClient.getInstance(), parent);
    }
}