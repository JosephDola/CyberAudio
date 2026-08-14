# NULLGate — Fabric 1.21.11

NULLGate gives selected Nether portals precise fast-travel routing instead of vanilla portal linking.

## Features
- Bind any active Nether portal to exact XYZ coordinates.
- Target the Overworld, Nether, or End.
- Walking into the bound portal overrides vanilla routing.
- Routes persist in `config/nullgate-links.json`.
- Whole connected portals are linked, not one block.
- Anti-bounce cooldown prevents portal loops.
- Safe-arrival search near the requested point.
- Emergency obsidian landing pad if no safe destination exists.
- Portal particles, sounds, and HUD feedback.

## Commands
Stand within 6 blocks of an active Nether portal.

```text
/null bind overworld 1250 80 -340
/null bind nether 200 70 900
/null bind end 0 70 0
/null info
/null unbind
/null count
/null help
```

## Requirements
- Minecraft Java Edition 1.21.11
- Fabric Loader 0.19.x
- Fabric API 0.141.6+1.21.11
- Java 21

This is an original gameplay implementation inspired by precise portal fast-travel mechanics.
