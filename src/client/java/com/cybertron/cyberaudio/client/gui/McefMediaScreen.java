package com.cybertron.cyberaudio.client.gui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.example.ExampleScreen;
import com.cybertron.cyberaudio.CyberAudio;
import com.cybertron.cyberaudio.resolver.MediaUrlRouter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;

/**
 * Optional MCEF-backed web-media screen. Nothing outside the reflection-based
 * WebMediaLauncher directly references this class, so CyberAudio's normal UI
 * remains safe when MCEF is absent or fails to initialize.
 */
public final class McefMediaScreen extends ExampleScreen {
    private static final Field BROWSER_FIELD = findBrowserField();

    private final Screen parent;
    private final MediaUrlRouter.Route route;
    private boolean routed;
    private boolean closing;

    public McefMediaScreen(Screen parent, MediaUrlRouter.Route route) {
        super(Component.literal("CyberAudio - " + route.label()));
        this.parent = parent;
        this.route = route;
    }

    @Override
    protected void init() {
        super.init();
        routeBrowserWhenReady();
    }

    @Override
    public void tick() {
        super.tick();
        if (!routed) routeBrowserWhenReady();
    }

    private void routeBrowserWhenReady() {
        if (routed || !MCEF.isInitialized()) return;

        try {
            MCEFBrowser browser = browser();
            if (browser == null) return;
            browser.loadURL(route.playbackUrl());
            browser.setFocus(true);
            routed = true;
            CyberAudio.LOGGER.info("Opened {} media in CyberAudio web player", route.label());
        } catch (ReflectiveOperationException | RuntimeException e) {
            CyberAudio.LOGGER.error("Unable to route CyberAudio MCEF browser", e);
        }
    }

    private MCEFBrowser browser() throws IllegalAccessException {
        return (MCEFBrowser) BROWSER_FIELD.get(this);
    }

    private static Field findBrowserField() {
        try {
            Field field = ExampleScreen.class.getDeclaredField("browser");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        try {
            super.onClose();
        } catch (Throwable error) {
            CyberAudio.LOGGER.warn("MCEF browser cleanup reported an error", error);
        } finally {
            if (minecraft != null) minecraft.setScreen(parent);
        }
    }
}
