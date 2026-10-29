# Fabric media client

An isolated photo/replay client, not a gameplay or compatibility test instance.
Run commands from the repository root:

```powershell
.\gradlew.bat :MismatchedBoots:Fabric:prepareMediaClient
```

Reload the Gradle project in IntelliJ, then select **MismatchedBoots — Fabric Media**.
Preparation is explicit for IntelliJ: repeat it after a fresh checkout, deleting
generated files, or updating these sources. Alternatively,
`:MismatchedBoots:Fabric:runMedia` prepares and launches in one command.

## World setup

The run directory is [Fabric/runs/media](../Fabric/runs/media). Its worlds,
settings, recordings, downloaded JARs and generated packs are ignored by Git.
The normal `runs/client` and `runs/server` directories are not used or modified.
Boots, its bundled core and normal required Fabric dependencies come from Loom;
the only additional mod placed in `media/mods` is the pinned Replay Mod.

1. Create a creative world with commands enabled. In **Data Packs**, use **Open
   Pack Folder**, copy `runs/media/packs/mismatchedmedia-datapack.zip` into that
   folder, enable it and finish creating the world. For an existing media world,
   copy the ZIP to that world's `datapacks` folder and run `/reload`.
2. The **MismatchedBoots photo stand-ins** resource pack is enabled on first
   preparation. If you have changed resource packs since, enable it manually.
   Preparation preserves existing `options.txt` and other user settings.
3. Run `/function mismatchedmedia:all` for a labeled shulker box per mod and a
   workbench. Place a box to take out its boots. This keeps the full catalog from
   overflowing your inventory. `/function mismatchedmedia:mods/justdirethings`,
   for example, gives that mod's boots directly; `catalog/justdirethings` gives
   its box instead. Individual variants are under `boots/`, with command completion.
4. Combine the boots normally at the workbench. Equip either output, move or run
   to create the desired pose, and leave the world to finish the recording.
5. Open **Replay Viewer** from the main menu. In playback, pause at the desired
   pose and move the camera independently. See the [Replay Mod documentation](https://www.replaymod.com/docs/)
   for camera paths, playback controls and rendering/export requirements.

Keep this pack enabled during replay playback: recordings contain the item
components, but do not embed these local textures. Keep a copy of the pack with
recordings you want to retain after changing its assets.

After expanding/updating the catalog, prepare again and **replace the installed
datapack ZIP in each media world's `datapacks` folder**, then run `/reload`.
The copy in `runs/media/packs` is an installation source, not a globally enabled
datapack. Reload resource packs with F3+T or restart the client as well.

## Scope

These are **iron boots with photo-only equipment and item-model components**,
not ports of the original mods. They keep iron stats and the `minecraft:iron_boots`
registry ID; neither the original mods nor their gameplay code are loaded.
The source snapshots in mixed boots preserve their distinct appearances.

## Catalog

The catalog has **41 appearances from 13 mods**, including Fabric mods whose
original JARs are intentionally not loaded here. These presets use standard
adult-player humanoid equipment layers and untrimmed item icons. Native texture
layers, item tint definitions and any PNG animation metadata are retained.

| Command suffix (`mods/` or `catalog/`) | Appearances |
| --- | --- |
| `advancednetherite` | Netherite Iron, Gold, Emerald, Diamond |
| `allthemodium` | AllTheModium, Vibranium, Unobtainium |
| `aquaculture` | Neptunium |
| `forbidden_arcanus` | Draco Arcanus, Tyr |
| `iceandfire` | Sheep |
| `justdirethings` | Ferricore, Blazegold, Celestigem, Eclipse Alloy |
| `mysticalagriculture` | Inferium, Prudentium, Tertium, Imperium, Supremium, Awakened Supremium |
| `neovitae` | Sentient |
| `aether_ii` | Arkenium, Beast Pelt, Burrukai Plate, Gravitite, Neptune, Sentry, Zanite |
| `undergarden` | Cloggrum, Froststeel, Utherium |
| `betterend` | Thallasium, Terminite, Aeternium |
| `betternether` | Cincinnasite, Nether Ruby, Flaming Ruby |
| `create` | Cardboard, Copper Diving, Netherite Diving |

The six Mystical Agriculture equipped PNGs are byte-identical between the
inspected Fabric port and NeoForge original, so they share one appearance group.

Individual function IDs normally combine the mod ID and material, for example
`/function mismatchedmedia:boots/justdirethings_eclipsealloy`. The original
`boots/neptunium` and `boots/unobtainium` commands and model IDs remain valid,
including in older recordings.

Excluded: Ice and Fire's other 30 boots, Roots Classic's two boots, Oritech Exo
Boots and Better End Crystalite Boots require custom equipped geometry/renderers;
Silent Gear's material-dependent appearance and generated item colors are not a
single fixed preset. Broken Aether II item variants are not additional wearable
appearances. No original gameplay, armor abilities or custom Java rendering is
ported. Baby-mob equipment, other armor slots and native trim item-icon dispatch
are outside this photo catalog's scope.

## Maintenance

[artifacts.json](artifacts.json) pins original download URLs and hashes.
It also selects the boot items for each mod, with optional `equipment` and `id`
overrides. Preparation verifies the JARs and reads their selected equipment
layers, untrimmed base item models and native item names. It copies only the
referenced textures/animation metadata and generates the adapted resource JSON,
translations and commands under `build/media`. [resourcepack](resourcepack) and
[datapack](datapack) provide the pack metadata. `build/media/catalog.json` lists
the generated IDs and source components for validation.

To add a preset, add the pinned artifact and its `boots` entry after verifying
that its native equipped model is standard; having an equipment JSON alone
does not prove that. The generator rejects missing assets and unsupported item
models rather than substituting a different appearance.

Original textures remain the property of their respective authors. Their bytes
and the downloaded JARs are **not committed or included in published mods**.
This local photo setup does not grant permission to redistribute those assets;
check the original projects' licenses before sharing the generated packs.

Preparation updates only its own resource pack, datapack ZIP and pinned Replay
JAR, without resetting worlds or recordings. Additional user-installed JARs
(for example JEI) are preserved and reported in preparation output, not deleted
or included in the automated test's dependency set.

## Targeted validation

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:verifyMediaClient
```

This opt-in test uses a disposable, hidden and muted client under
`RuntimeTests/Fabric/build/run/mediaClient`, not the photo world's save folder.
Its JARs and packs come from the pinned downloads staged in `build/media`,
never from the user's `runs/media/mods` or ordinary indev instance.
It loads the actual packs, runs the catalog command, checks all 41 contained
boot variants and their inventory models, combines each with Neptunium in both
orientations and checks client synchronization. It records with Replay Mod,
and opens the resulting recording to check its equipped source components.
The two targeted screenshots are `media-equipped` and `media-replay-equipped`
in the client GameTest screenshot output. It does not validate video export,
FFmpeg, elaborate camera paths or every Replay Mod control.

The initial two-appearance acceptance passed on Minecraft 26.1.2 with Replay
Mod 2.6.28 on 2026-10-28, including visual inspection in the actual replay.
The owner also confirmed that mixed boots appear correctly in playback.
Expanded-catalog acceptance also passed on 2026-10-28: all 41 variants arrived
in their 13 boxes on server and client, all inventory models resolved without
missing sprites, each variant retained its source data in both mixed
orientations, and the Neptunium/Unobtainium recording/playback gate passed.
This is not an exhaustive manual visual review of every equipped preset.
