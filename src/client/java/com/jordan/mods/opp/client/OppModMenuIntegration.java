package com.jordan.mods.opp.client;

import com.jordan.mods.opp.client.screen.OppMenuScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class OppModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return OppMenuScreen::new;
    }
}