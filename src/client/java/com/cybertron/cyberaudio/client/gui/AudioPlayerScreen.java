package com.cybertron.cyberaudio.client.gui;

import com.cybertron.cyberaudio.audio.AudioManager;
import com.cybertron.cyberaudio.client.CyberAudioClient;
import com.cybertron.cyberaudio.client.WebMediaLauncher;
import com.cybertron.cyberaudio.playlist.PlaylistManager;
import com.cybertron.cyberaudio.playlist.PlaylistTrack;
import com.cybertron.cyberaudio.resolver.MediaUrlRouter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AudioPlayerScreen extends Screen {
    private static final int TRACK_ROWS = 6;

    private final Screen parent;
    private final AudioManager audio = CyberAudioClient.AUDIO;
    private final PlaylistManager playlists = CyberAudioClient.PLAYLISTS;

    private EditBox urlBox;
    private EditBox trackNameBox;
    private EditBox playlistNameBox;
    private final Button[] trackButtons = new Button[TRACK_ROWS];

    private int selectedTrack = -1;
    private int pageOffset;
    private String statusMessage = "Paste a link, play it, or save it to a playlist.";
    private int statusColor = 0xA0E8FF;

    public AudioPlayerScreen(Screen parent) {
        super(Component.literal("CyberAudio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(320, Math.min(660, width - 20));
        int x = (width - panelWidth) / 2;
        int top = 16;

        urlBox = new EditBox(font, x, top + 38, panelWidth, 20, Component.literal("Media URL"));
        urlBox.setMaxLength(2048);
        String saved = CyberAudioClient.CONFIG.config().lastUrl;
        if (saved != null) urlBox.setValue(saved);
        addRenderableWidget(urlBox);

        int addWidth = 138;
        trackNameBox = new EditBox(font, x, top + 64, panelWidth - addWidth - 6, 20, Component.literal("Track name (optional)"));
        trackNameBox.setMaxLength(120);
        addRenderableWidget(trackNameBox);
        addRenderableWidget(Button.builder(Component.literal("Add to Playlist"), button -> addCurrentToPlaylist())
                .bounds(x + panelWidth - addWidth, top + 64, addWidth, 20).build());

        int gap = 5;
        int controlWidth = (panelWidth - gap * 4) / 5;
        addRenderableWidget(Button.builder(Component.literal("Play URL"), button -> playEnteredUrl())
                .bounds(x, top + 90, controlWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Pause / Resume"), button -> audio.togglePause())
                .bounds(x + (controlWidth + gap), top + 90, controlWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Stop"), button -> audio.stop())
                .bounds(x + (controlWidth + gap) * 2, top + 90, controlWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Vol -"), button -> changeVolume(-0.05f))
                .bounds(x + (controlWidth + gap) * 3, top + 90, controlWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Vol +"), button -> changeVolume(0.05f))
                .bounds(x + (controlWidth + gap) * 4, top + 90, controlWidth, 20).build());

        int arrowWidth = 34;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> previousPlaylist())
                .bounds(x, top + 118, arrowWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> nextPlaylist())
                .bounds(x + arrowWidth + 4, top + 118, arrowWidth, 20).build());

        int deleteWidth = 92;
        int newWidth = 70;
        int nameX = x + arrowWidth * 2 + 12;
        int nameWidth = panelWidth - (nameX - x) - deleteWidth - newWidth - 10;
        playlistNameBox = new EditBox(font, nameX, top + 118, Math.max(80, nameWidth), 20, Component.literal("New playlist name"));
        playlistNameBox.setMaxLength(80);
        addRenderableWidget(playlistNameBox);
        int newX = nameX + Math.max(80, nameWidth) + 5;
        addRenderableWidget(Button.builder(Component.literal("New"), button -> createPlaylist())
                .bounds(newX, top + 118, newWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), button -> deletePlaylist())
                .bounds(newX + newWidth + 5, top + 118, deleteWidth, 20).build());

        int trackTop = top + 158;
        for (int row = 0; row < TRACK_ROWS; row++) {
            final int rowIndex = row;
            Button trackButton = Button.builder(Component.literal("—"), button -> selectTrack(pageOffset + rowIndex))
                    .bounds(x, trackTop + row * 23, panelWidth, 20).build();
            trackButtons[row] = trackButton;
            addRenderableWidget(trackButton);
        }

        int bottomY = trackTop + TRACK_ROWS * 23 + 3;
        int bottomWidth = (panelWidth - gap * 5) / 6;
        addRenderableWidget(Button.builder(Component.literal("Play Selected"), button -> playSelected())
                .bounds(x, bottomY, bottomWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), button -> removeSelected())
                .bounds(x + (bottomWidth + gap), bottomY, bottomWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Move Up"), button -> moveSelected(-1))
                .bounds(x + (bottomWidth + gap) * 2, bottomY, bottomWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Move Down"), button -> moveSelected(1))
                .bounds(x + (bottomWidth + gap) * 3, bottomY, bottomWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Page <"), button -> previousPage())
                .bounds(x + (bottomWidth + gap) * 4, bottomY, bottomWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Page >"), button -> nextPage())
                .bounds(x + (bottomWidth + gap) * 5, bottomY, bottomWidth, 20).build());

        int navY = bottomY + 25;
        int navWidth = (panelWidth - gap) / 2;
        addRenderableWidget(Button.builder(Component.literal("Previous Track"), button -> playRelative(-1))
                .bounds(x, navY, navWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next Track"), button -> playRelative(1))
                .bounds(x + navWidth + gap, navY, navWidth, 20).build());

        normalizeSelection();
        updateTrackButtons();
    }

    private void playEnteredUrl() {
        playMedia(urlBox.getValue());
    }

    private void playMedia(String input) {
        String url = input == null ? "" : input.trim();
        MediaUrlRouter.Route route = MediaUrlRouter.route(url);
        if (!route.valid()) {
            setStatus(route.error(), 0xFF7777);
            return;
        }

        CyberAudioClient.CONFIG.config().lastUrl = route.originalUrl();
        CyberAudioClient.CONFIG.save();

        if (route.kind() == MediaUrlRouter.Kind.DIRECT_AUDIO) {
            setStatus("Opening direct audio stream...", 0x77FFCC);
            audio.play(route.playbackUrl()).exceptionally(error -> null);
            return;
        }

        audio.stop();
        WebMediaLauncher.LaunchResult result = WebMediaLauncher.open(this, route);
        setStatus(result.message(), result.success() ? 0x77FFCC : 0xFFD36A);
    }

    private void addCurrentToPlaylist() {
        MediaUrlRouter.Route route = MediaUrlRouter.route(urlBox.getValue());
        if (!route.valid()) {
            setStatus(route.error(), 0xFF7777);
            return;
        }
        selectedTrack = playlists.addTrack(trackNameBox.getValue(), route.originalUrl());
        pageOffset = Math.max(0, (selectedTrack / TRACK_ROWS) * TRACK_ROWS);
        trackNameBox.setValue("");
        updateTrackButtons();
        setStatus("Added to " + playlists.current().name + ".", 0x77FFCC);
    }

    private void createPlaylist() {
        if (!playlists.createPlaylist(playlistNameBox.getValue())) {
            setStatus("Enter a playlist name first.", 0xFFD36A);
            return;
        }
        playlistNameBox.setValue("");
        selectedTrack = -1;
        pageOffset = 0;
        updateTrackButtons();
        setStatus("Created playlist: " + playlists.current().name, 0x77FFCC);
    }

    private void deletePlaylist() {
        String oldName = playlists.current().name;
        if (!playlists.deleteCurrentPlaylist()) {
            setStatus("Keep at least one playlist.", 0xFFD36A);
            return;
        }
        selectedTrack = -1;
        pageOffset = 0;
        updateTrackButtons();
        setStatus("Deleted playlist: " + oldName, 0x77FFCC);
    }

    private void previousPlaylist() {
        playlists.previousPlaylist();
        selectedTrack = -1;
        pageOffset = 0;
        updateTrackButtons();
    }

    private void nextPlaylist() {
        playlists.nextPlaylist();
        selectedTrack = -1;
        pageOffset = 0;
        updateTrackButtons();
    }

    private void selectTrack(int index) {
        if (index < 0 || index >= playlists.trackCount()) return;
        selectedTrack = index;
        PlaylistTrack track = playlists.track(index);
        if (track != null) {
            urlBox.setValue(track.url);
            trackNameBox.setValue(track.name == null ? "" : track.name);
        }
        updateTrackButtons();
    }

    private void playSelected() {
        PlaylistTrack track = playlists.track(selectedTrack);
        if (track == null) {
            setStatus("Select a track first.", 0xFFD36A);
            return;
        }
        urlBox.setValue(track.url);
        playMedia(track.url);
    }

    private void playRelative(int delta) {
        int count = playlists.trackCount();
        if (count == 0) {
            setStatus("This playlist is empty.", 0xFFD36A);
            return;
        }
        if (selectedTrack < 0) selectedTrack = delta >= 0 ? 0 : count - 1;
        else selectedTrack = (selectedTrack + delta + count) % count;
        pageOffset = (selectedTrack / TRACK_ROWS) * TRACK_ROWS;
        updateTrackButtons();
        playSelected();
    }

    private void removeSelected() {
        if (!playlists.removeTrack(selectedTrack)) {
            setStatus("Select a track to remove.", 0xFFD36A);
            return;
        }
        selectedTrack = Math.min(selectedTrack, playlists.trackCount() - 1);
        normalizeSelection();
        updateTrackButtons();
        setStatus("Removed track from playlist.", 0x77FFCC);
    }

    private void moveSelected(int delta) {
        int moved = playlists.moveTrack(selectedTrack, delta);
        if (moved == selectedTrack) return;
        selectedTrack = moved;
        pageOffset = (selectedTrack / TRACK_ROWS) * TRACK_ROWS;
        updateTrackButtons();
    }

    private void previousPage() {
        pageOffset = Math.max(0, pageOffset - TRACK_ROWS);
        updateTrackButtons();
    }

    private void nextPage() {
        if (pageOffset + TRACK_ROWS < playlists.trackCount()) pageOffset += TRACK_ROWS;
        updateTrackButtons();
    }

    private void normalizeSelection() {
        int count = playlists.trackCount();
        if (count == 0) {
            selectedTrack = -1;
            pageOffset = 0;
            return;
        }
        if (selectedTrack >= count) selectedTrack = count - 1;
        if (pageOffset >= count) pageOffset = ((count - 1) / TRACK_ROWS) * TRACK_ROWS;
    }

    private void updateTrackButtons() {
        normalizeSelection();
        for (int row = 0; row < TRACK_ROWS; row++) {
            Button button = trackButtons[row];
            if (button == null) continue;
            int index = pageOffset + row;
            PlaylistTrack track = playlists.track(index);
            if (track == null) {
                button.setMessage(Component.literal("—"));
                button.active = false;
            } else {
                String prefix = index == selectedTrack ? "▶ " : "";
                button.setMessage(Component.literal(clip(prefix + (index + 1) + ". " + track.displayName(), 78)));
                button.active = true;
            }
        }
    }

    private void changeVolume(float delta) {
        audio.setVolume(audio.volume() + delta);
        CyberAudioClient.CONFIG.config().volume = audio.volume();
        CyberAudioClient.CONFIG.save();
        setStatus("Volume: " + Math.round(audio.volume() * 100) + "%", 0xA0E8FF);
    }

    private void setStatus(String message, int color) {
        statusMessage = message == null ? "" : message;
        statusColor = color;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int panelWidth = Math.max(320, Math.min(660, width - 20));
        int x = (width - panelWidth) / 2;
        int top = 16;
        int center = width / 2;

        graphics.drawCenteredString(font, Component.literal("CYBERAUDIO 0.2.1"), center, top, 0x55FFFF);
        graphics.drawCenteredString(font, Component.literal("Music Player + Persistent Playlists"), center, top + 14, 0xD0D0D0);
        graphics.drawString(font, "Playlist: " + playlists.current().name + "  (" + (playlists.selectedPlaylistIndex() + 1) + "/" + playlists.playlistCount() + ")", x, top + 143, 0xFFFFFF, false);
        graphics.drawString(font, "Tracks: " + playlists.trackCount() + "   Page: " + (pageOffset / TRACK_ROWS + 1), x + panelWidth - 118, top + 143, 0xA0A0A0, false);

        int infoY = top + 347;
        graphics.drawString(font, "State: " + audio.state(), x, infoY, 0xD0D0D0, false);
        graphics.drawString(font, "Volume: " + Math.round(audio.volume() * 100) + "%", x + 132, infoY, 0xD0D0D0, false);
        graphics.drawString(font, "Downloaded: " + formatBytes(audio.performance().downloadedBytes()), x + 245, infoY, 0xA0A0A0, false);

        String displayStatus = !audio.lastError().isBlank() ? "Audio error: " + audio.lastError() : statusMessage;
        int color = !audio.lastError().isBlank() ? 0xFF7777 : statusColor;
        if (!displayStatus.isBlank()) {
            graphics.drawCenteredString(font, Component.literal(clip(displayStatus, 96)), center, infoY + 15, color);
        }
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format("%.1f KiB", bytes / 1024.0);
        return String.format("%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public void onClose() {
        playlists.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
