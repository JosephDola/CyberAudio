package com.cybertron.cyberaudio.config;

import com.cybertron.cyberaudio.CyberAudio;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("cyberaudio.json");
    private CyberAudioConfig config = new CyberAudioConfig();
    public CyberAudioConfig config() { return config; }
    public void load() {
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            CyberAudioConfig loaded = GSON.fromJson(reader, CyberAudioConfig.class);
            if (loaded != null) config = loaded;
        } catch (Exception e) { CyberAudio.LOGGER.warn("Unable to read CyberAudio config; using defaults", e); }
        config.volume = Math.clamp(config.volume, 0.0f, 1.0f);
    }
    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) { GSON.toJson(config, writer); }
        } catch (IOException e) { CyberAudio.LOGGER.warn("Unable to save CyberAudio config", e); }
    }
}
