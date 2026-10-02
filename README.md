# DeathCompass

A Fabric mod for Minecraft 1.21.1. **After you die, an arrow under your crosshair points back to where you died**, with how far away it is.

```
        +
   ↗ 143m  ▼12  ☠
```

- The arrow turns as you turn, so just walk the way it points.
- `▲` / `▼` shows how far up or down your death spot is (only when it's more than 6 blocks).
- If you died in another dimension, it tells you where instead: *☠ Died in the Nether at 120, 45, -80*.
- When you get within 4 blocks, the arrow disappears and you get a *"You made it back!"* popup.
- Chat also tells you the exact coordinates when you respawn.

It's **client-side only** and works on any server, including vanilla ones. It uses the death position Minecraft already sends to your game (the same one the Recovery Compass uses), so it's always exact.

## Commands

| Command | What it does |
|---|---|
| `/deathcompass` | Shows where you last died, and turns the arrow back on if you hid it |
| `/deathcompass hide` | Hides the arrow |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
3. Download `deathcompass-x.x.x.jar` from [**Releases**](../../releases) and put it in `mods`.

## Building

```
./gradlew build
```

The jar ends up in `build/libs/`.

To ship an update, bump `mod_version` in `gradle.properties` and push. GitHub Actions builds it and publishes a release automatically.
