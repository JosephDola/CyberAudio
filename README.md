# CyberAudio

CyberAudio is a client-side Fabric mod for **Minecraft Java Edition 1.21.11** that keeps music controls, direct audio, playlists, and optional web-media playback inside Minecraft.

## CyberAudio 0.2.1

### Crash-safe native UI
Pressing **M** always opens CyberAudio's own Minecraft UI first. The main player screen has no direct MCEF class dependency, so MCEF is not loaded merely by opening CyberAudio.

The UI includes:
- Media URL input
- Optional track name input
- Play URL
- Pause / resume
- Stop
- Independent volume controls
- Named playlists
- Add current URL to playlist
- Previous / next playlist
- Delete playlist
- Select tracks from a visible list
- Playlist paging
- Play selected
- Remove selected
- Move track up / down
- Previous / next track
- Playback state and downloaded-byte information

### Persistent playlists
Playlists are stored in `config/cyberaudio-playlists.json`.

CyberAudio creates a default `My Playlist` automatically. You can create additional named playlists and store direct-audio, YouTube, YouTube Music, and Spotify URLs in them. Playlist order and track names persist between launches.

### Native direct-audio mode
Direct HTTP/HTTPS audio URLs use CyberAudio's lightweight Java audio pipeline:

`URL -> resolver -> HTTP stream -> buffered input -> decoder -> PCM -> audio output`

Features:
- MP3 and OGG/Vorbis decoder providers bundled with CyberAudio
- Play, pause/resume, stop, and independent volume
- Network/audio work kept off Minecraft's render thread
- Persistent `config/cyberaudio.json`
- Downloaded-byte and startup-latency metrics

### YouTube and Spotify mode
CyberAudio recognizes normal links from `youtube.com`, `youtu.be`, `music.youtube.com`, `open.spotify.com`, and `spotify.link`.

For these links, CyberAudio can use **MCEF** to show the service's embedded web player inside Minecraft. MCEF is loaded only when a supported web-media item is actually opened.

Supported routing includes YouTube videos, Shorts, live-video URLs and playlists, plus Spotify tracks, albums, playlists, artists, shows and episodes.

CyberAudio does not extract protected audio streams, bypass DRM, remove service restrictions, or bypass account requirements.

## Install
1. Use **Minecraft 1.21.11**.
2. Install **Fabric Loader 0.18.4 or newer**.
3. Install **Fabric API 0.141.6+1.21.11** or a compatible 1.21.11 Fabric API build.
4. Remove older CyberAudio JARs from `.minecraft/mods`.
5. Put the latest CyberAudio JAR in `.minecraft/mods`.
6. For YouTube/Spotify playback, optionally install a compatible **MCEF 1.21.11 Fabric** build.
7. Launch Minecraft and press **M**.

## Compatibility
- Minecraft 1.21.11
- Fabric Loader 0.18.4+
- Java 21
- Fabric API
- Optional MCEF for YouTube/Spotify
- No OptiFine dependency
- Sodium/Iris are not required

## Building
The release build uses Java 21, Fabric Loom 1.17.x and Gradle 9.5.0.

```text
gradle build
```

The distributable file is `build/libs/cyberaudio-<version>.jar`.

## Project layout
```text
src/main/java/com/cybertron/cyberaudio/
  audio/             Native direct-audio engine
  config/            Persistent settings
  playlist/          Playlist models + JSON persistence
  resolver/          Direct resolver + media URL routing
  util/              Performance metrics

src/client/java/com/cybertron/cyberaudio/client/
  CyberAudioClient.java
  WebMediaLauncher.java
  gui/
    AudioPlayerScreen.java
    McefMediaScreen.java
```

## Current limitations
- Native seeking/progress is not implemented yet.
- Automatic next-track playback when a native stream naturally finishes is still planned.
- YouTube/Spotify availability is controlled by the service and media owner/settings.
- Web playback requires MCEF.
- Real-device FPS/CPU/memory measurements still need representative hardware testing.

## Roadmap
- Automatic playlist continuation
- Shuffle and repeat modes
- Favorites
- Better metadata and thumbnails
- Native seek/progress bar
- Mini-player HUD
- Search/filter inside large playlists
- Playlist import/export
- Optional synchronized multiplayer playback
