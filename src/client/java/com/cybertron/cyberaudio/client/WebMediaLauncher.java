package com.cybertron.cyberaudio.client;

import com.cybertron.cyberaudio.CyberAudio;
import com.cybertron.cyberaudio.resolver.MediaUrlRouter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;

/**
 * Deliberately contains no compile-time reference to MCEF classes. This keeps
 * CyberAudio's main menu safe to open even when MCEF is missing, broken or
 * unsupported on the current machine.
 */
public final class WebMediaLauncher {
    private static final String SCREEN_CLASS = "com.cybertron.cyberaudio.client.gui.McefMediaScreen";

    private WebMediaLauncher() {}

    public record LaunchResult(boolean success, String message) {}

    public static LaunchResult open(Screen parent, MediaUrlRouter.Route route) {
        if (route == null || !route.valid() || !route.webMedia()) {
            return new LaunchResult(false, "This item is not supported web media.");
        }
        if (!FabricLoader.getInstance().isModLoaded("mcef")) {
            return new LaunchResult(false, route.label() + " needs MCEF for Minecraft 1.21.11. Direct audio still works without MCEF.");
        }

        try {
            ClassLoader loader = WebMediaLauncher.class.getClassLoader();
            Class<?> rawClass = Class.forName(SCREEN_CLASS, true, loader);
            if (!Screen.class.isAssignableFrom(rawClass)) {
                return new LaunchResult(false, "CyberAudio web player class is invalid.");
            }

            Constructor<?> constructor = rawClass.getConstructor(Screen.class, MediaUrlRouter.Route.class);
            Screen screen = (Screen) constructor.newInstance(parent, route);
            Minecraft.getInstance().setScreen(screen);
            return new LaunchResult(true, "Opening " + route.label() + " in CyberAudio web player...");
        } catch (Throwable error) {
            CyberAudio.LOGGER.error("CyberAudio could not start the optional MCEF web player", error);
            String detail = error.getMessage();
            if (detail == null || detail.isBlank()) detail = error.getClass().getSimpleName();
            return new LaunchResult(false, "MCEF web player failed: " + detail + ". Direct audio and playlists remain available.");
        }
    }
}
