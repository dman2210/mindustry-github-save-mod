# Mindustry GitHub Save Sync

A Mindustry v8 Java mod that synchronizes Mindustry's complete game-data export between desktop and Android through a GitHub repository.

## Safety behavior

- Uses Mindustry's own full-data export/import implementation.
- Preserves locally installed mods instead of copying or overwriting their ZIP/JAR files; this avoids Android storage errors and cross-platform mod incompatibilities.
- Keeps GitHub credentials in `github-save-sync/config.json`, outside the exported archive.
- Creates a dated local backup before every download/import.
- Lists up to 30 commits affecting the synchronized save and lets you restore any listed version.
- Detects remote changes since the device's last sync and asks before overwriting them.
- Requires explicit confirmation before replacing local data.
- Exits after import so Mindustry reloads the replaced settings and saves cleanly.
- Refuses uploads larger than GitHub's 100 MiB Contents API limit.

The synchronized archive includes settings, saves, campaign sectors, schematics, custom maps, and save-related cached assets. Installed mods remain local to each device. Install matching gameplay/content mods on both devices when a save depends on them.

## Install

1. Download the cross-platform `mindustry-github-sync.jar` from this project's Releases or build it locally.
2. In Mindustry, open **Mods → Import Mod → Import File** and select the JAR.
3. Back out of that menu.
4. Restart Mindustry when prompted. 
5. Select **GitHub Save Sync** on the main menu, then **Connection settings**.

Install the same mod JAR on both devices before downloading data on either one.

## GitHub setup

1. Create a private GitHub repository. It can be empty.
2. In GitHub, create a fine-grained personal access token restricted to that repository. I recommend preventing the token from expiring at all. 
3. Grant only **Repository permissions → Contents → Read and write**.
4. Enter the repository owner, repository name, branch, remote path, and token in the mod's settings dialog.

Use the repository name only (for example `mindustry-save`), not its full URL. The default remote path is `mindustry-data.zip`.

## Recommended workflow

1. When finished on device A, return to Mindustry's main menu and press **Upload game data**.
2. On device B, press **Download game data**, choose the GitHub commit to restore, confirm the replacement, and reopen Mindustry.
3. Play on device B, return to the main menu, and upload before switching back.

Do not play independently on both devices without uploading/downloading between sessions. This is whole-archive synchronization, not a merge system.

```text
github-save-sync/backups/
```

Restore one through **Settings → Game Data → Import Data**.
