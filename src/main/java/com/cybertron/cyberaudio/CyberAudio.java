package com.cybertron.cyberaudio;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CyberAudio implements ModInitializer {
    public static final String MOD_ID = "cyberaudio";
    public static final Logger LOGGER = LoggerFactory.getLogger("CyberAudio");

    @Override
    public void onInitialize() {
        LOGGER.info("CyberAudio common initialization complete");
    }
}
