# Simple World Downloader

Save a multiplayer world as a local singleplayer world. Downloads are stored next to your regular singleplayer saves.

## What this fork adds

- A fast interactive chunk map on `U`, with saved, queued, loaded, and missing chunk states.
- Proxy-aware world selection for networks using Velocity or multiple backend servers.
- Separate download sessions for the same host on different ports.
- Static player NPC snapshots with skins and equipment, without duplicate name labels.
- An option to exclude mobs, armor stands, item frames, and other entities from the saved world.
- All download controls and settings inside the `U` menu, keeping the Escape menu clean.

The Mod downloads:
- Blocks
- BlockEntities
- Resourcepacks
- Player Inventory
- Entities
- Enderchest Inventory
- Containers (Chests, Barrels, ...)
- Villagers
- Advancements
- Statistics

Download on [Modrinth](https://modrinth.com/mod/simple-world-downloader), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-world-downloader) or [Github](https://github.com/J0KER2J0KER/SimpleWorldDownloader/releases/latest).

# Containers
Container inventories (Enderchest, Chests, Barrels, ...) can only be saved if you open the inventory at least once while the world download is running. This is also true for Entitiy Inventories (like Villagers, Chestboats, Horses, ...).

# Advancements & Statistics
Advancements and Statistics can only be saved if the corresponding UI's are opened at least once while the download is running.

# Fabric API
The Fabric API is required for this Mod to work. It can be downloaded [here](https://modrinth.com/mod/fabric-api).

# Building
Run `./gradlew buildAllVersions` to build the Minecraft 1.21.11 and 26.2 releases together.
The finished JAR files are copied to `build/versions`.

# Config
Press `U`, then select **Settings**. The Escape menu is not modified by this fork.

### Chunk Map
Press `U` to open the chunk map. Green chunks are saved, yellow chunks are queued, blue chunks are loaded by the client, and gray chunks have not been loaded yet. Drag to move the map and use the mouse wheel to zoom.

The key can be changed in Minecraft's key bindings. Mod Menu also provides access to the mod config screen.

### Auto Downloading
Set whether worlds should be downloaded automatically on server joining.

### Save world to
Set the name of the saved world. If a world with this name already exists, the new chunks overwrite parts of that world.

# Flashback and Replay Mod
Open the replay, press `U`, and use the same download controls as on a live server.

# Images
<img width="2560" height="1440" alt="screenshot" src="https://github.com/user-attachments/assets/1a0f8344-ac26-4c8d-918b-1615f5816d4e" />
