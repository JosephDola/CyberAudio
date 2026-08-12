package com.cybertron.cyberaudio.client;

import com.cybertron.cyberaudio.CyberAudio;
import com.cybertron.cyberaudio.audio.AudioManager;
import com.cybertron.cyberaudio.client.gui.AudioPlayerScreen;
import com.cybertron.cyberaudio.config.ConfigManager;
import com.cybertron.cyberaudio.playlist.PlaylistManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class CyberAudioClient implements ClientModInitializer {
    public static final AudioManager AUDIO = new AudioManager();
    public static final ConfigManager CONFIG = new ConfigManager();
    public static final PlaylistManager PLAYLISTS = new PlaylistManager();

    private KeyMapping openKey, pauseKey, stopKey;

    @Override
    public void onInitializeClient() {
        CONFIG.load();
        PLAYLISTS.load();
        AUDIO.setVolume(CONFIG.config().volume);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(CyberAudio.MOD_ID, "controls")
        );
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cyberaudio.open", InputConstants.Type.KEYSYM, InputConstants.KEY_M, category));
        pauseKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cyberaudio.play_pause", InputConstants.Type.KEYSYM, InputConstants.KEY_P, category));
        stopKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cyberaudio.stop", InputConstants.Type.KEYSYM, InputConstants.KEY_O, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                try {
                    client.setScreen(new AudioPlayerScreen(client.screen));
                } catch (Throwable error) {
                    CyberAudio.LOGGER.error("Unable to open CyberAudio UI", error);
                }
            }
            while (pauseKey.consumeClick()) AUDIO.togglePause();
            while (stopKey.consumeClick()) AUDIO.stop();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CONFIG.config().volume = AUDIO.volume();
            CONFIG.save();
            PLAYLISTS.save();
            AUDIO.close();
        });
        CyberAudio.LOGGER.info("CyberAudio client initialized");
    }

    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
