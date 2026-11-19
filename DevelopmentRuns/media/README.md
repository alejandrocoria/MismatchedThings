# Fabric media client

An isolated photo/replay client, not a gameplay or compatibility test instance.
Run commands from the repository root:

```powershell
.\gradlew.bat :DevelopmentRuns:Fabric:prepareMediaClient
```

Reload the Gradle project in IntelliJ, then select **MismatchedThings — Fabric Media**.
Both IntelliJ and `:DevelopmentRuns:Fabric:runMedia` prepare the packs, pinned
Replay Mod and current family outputs before launching.

## World setup

The run directory is [Fabric/runs/media](../Fabric/runs/media). Its worlds,
settings, recordings, downloaded JARs and generated packs are ignored by Git.
The normal `runs/client` and `runs/server` directories are not used or modified.
Things, Boots and Leggings load from their current project outputs with their
required runtime libraries. The joint client's compatibility collection and
viewers are not loaded automatically. Preparation adds only the pinned Replay
Mod to `media/mods`, preserving any manually installed extras.

1. Create a creative world with commands enabled. In **Data Packs**, use **Open
   Pack Folder**, copy `runs/media/packs/mismatchedmedia-datapack.zip` into that
   folder, enable it and finish creating the world. For an existing media world,
   copy the ZIP to that world's `datapacks` folder and run `/reload`.
2. The **MismatchedThings photo stand-ins** resource pack is enabled on first
   preparation. If you have changed resource packs since, enable it manually.
   Preparation preserves existing `options.txt` and other user settings.
3. Run `/function mismatchedmedia:all` for a labeled shulker box per mod and a
   workbench. Each box contains that mod's boots first and leggings starting on
   the next row. Place a box to take out its pieces. This keeps the full catalog from
   overflowing your inventory. `/function mismatchedmedia:mods/justdirethings`,
   for example, gives both kinds directly; `catalog/justdirethings` gives
   its box instead. Individual variants are under `boots/` and `leggings/`,
   with command completion.
4. Combine two boots or two leggings normally at the workbench. Equip either
   output and use the free camera to capture your desired pose. Leaving the world
   finishes the recording.
5. Open **Replay Viewer** from the main menu. In playback, pause at the desired
   pose and move the camera independently. See the [Replay Mod documentation](https://www.replaymod.com/docs/)
   for camera paths, playback controls and rendering/export requirements.

Keep this pack enabled during replay playback: recordings contain the item
components, but do not embed these local textures. Keep a copy of the pack with
recordings you want to retain after changing its assets.

Use matching Mismatched builds when recording and playing back. Stable photo
asset IDs do not guarantee replay compatibility across mod/network changes.
The playback checks cover equipment, not walking-animation fidelity; capture
walking poses during the live session rather than relying on later playback.

After expanding/updating the catalog, prepare again and **replace the installed
datapack ZIP in each media world's `datapacks` folder**, then run `/reload`.
The copy in `runs/media/packs` is an installation source, not a globally enabled
datapack. Reload resource packs with F3+T or restart the client as well.

## Scope

These are **iron boots and leggings with photo-only equipment and item-model components**,
not ports of the original mods. They keep iron stats and the `minecraft:iron_boots`
or `minecraft:iron_leggings` registry ID; neither the original mods nor their
gameplay code are loaded.
The source snapshots in mixed items preserve their distinct appearances. The
generator targets these two pieces only; other slots are not inferred from the
presence of an armor material.

## Catalog

The catalog has **80 appearances (41 boots and 39 leggings) from 13 mods**, including Fabric mods whose
original JARs are intentionally not loaded here. These presets use standard
adult-player humanoid equipment layers and untrimmed item icons. Native texture
layers, item tint definitions and any PNG animation metadata are retained.

| Command suffix (`mods/` or `catalog/`) | Both pieces | Boots only | Leggings only |
| --- | --- | --- | --- |
| `advancednetherite` | Netherite Iron, Gold, Emerald, Diamond | None | None |
| `allthemodium` | AllTheModium, Vibranium, Unobtainium | None | None |
| `aquaculture` | Neptunium | None | None |
| `forbidden_arcanus` | Draco Arcanus, Tyr | None | None |
| `iceandfire` | Sheep | None | None |
| `justdirethings` | Ferricore, Blazegold, Celestigem, Eclipse Alloy | None | None |
| `mysticalagriculture` | Inferium, Prudentium, Tertium, Imperium, Supremium, Awakened Supremium | None | None |
| `neovitae` | Sentient | None | None |
| `aether_ii` | Arkenium, Beast Pelt, Burrukai Plate, Gravitite, Neptune, Zanite | Sentry | None |
| `undergarden` | Cloggrum, Froststeel, Utherium | None | Ancient |
| `betterend` | Thallasium, Terminite, Aeternium | None | None |
| `betternether` | Cincinnasite, Nether Ruby, Flaming Ruby | None | None |
| `create` | Cardboard | Copper Diving, Netherite Diving | None |

The six Mystical Agriculture equipped boot PNGs are byte-identical between the
inspected Fabric port and NeoForge original, so they share one appearance group.

Individual function IDs normally combine the mod ID and material, for example
`/function mismatchedmedia:boots/justdirethings_eclipsealloy`. Neptunium and
Unobtainium use the shorter suffixes `neptunium` and `unobtainium` under both
`boots/` and `leggings/`.
Equipment asset IDs are shared by material; the generated asset carries the
separate `humanoid` and `humanoid_leggings` texture layers where selected.

Excluded: Ice and Fire's other 30 boots, Roots Classic's two boots, Oritech Exo
Boots and Better End Crystalite Boots require custom equipped geometry/renderers;
Silent Gear's material-dependent appearance and generated item colors are not a
single fixed preset. Broken Aether II item variants are not additional wearable
appearances. No original gameplay, armor abilities or custom Java rendering is
ported. The equivalent custom-geometry leggings are omitted too; new entries
are explicitly selected, not discovered from the mere presence of equipment JSON.
Baby-mob equipment, other armor slots and native trim item-icon dispatch
are outside this photo catalog's scope.

## Maintenance

[artifacts.json](artifacts.json) pins original download URLs and hashes.
It separately selects `boots` and `leggings` for each mod, with optional `equipment` and `id`
overrides. Preparation verifies the JARs and reads their selected equipment
layers, untrimmed base item models and native item names. It copies only the
referenced textures/animation metadata and generates the adapted resource JSON,
translations and commands under `DevelopmentRuns/build/media`. [resourcepack](resourcepack) and
[datapack](datapack) provide the pack metadata. `DevelopmentRuns/build/media/catalog.json` lists
the generated IDs and source components for validation.

To add a preset, add the pinned artifact and its `boots` or `leggings` entry after verifying
that its native equipped model is standard; having an equipment JSON alone
does not prove that. The generator rejects missing assets and unsupported item
models rather than substituting a different appearance. Each mod's selection
must fit in 27 slots, with the two piece kinds starting on separate rows;
generation fails instead of silently dropping overflow.

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
.\gradlew.bat :DevelopmentRuns:Fabric:verifyMediaRuntime
.\gradlew.bat :DevelopmentRuns:Fabric:verifyMediaClient
```

This opt-in test uses a disposable, hidden and muted client under
`mods/MismatchedBoots/RuntimeTests/Fabric/build/run/mediaClient`, not the photo world's save folder.
Its JARs and packs come from the pinned downloads staged in `DevelopmentRuns/build/media`,
never from the user's `runs/media/mods` or ordinary indev instance.
It loads the actual packs, runs the catalog command, checks all 80 contained
variants, mod/row grouping and inventory models, combines each with Neptunium of
the same piece in both orientations and checks client synchronization. It records with Replay Mod,
and opens the resulting recording to check its equipped source components.
The two targeted screenshots are `media-equipped` and `media-replay-equipped`
in the client GameTest screenshot output. Playback acceptance asserts recorded
item/source components, not screenshot framing. It does not validate video export,
FFmpeg, elaborate camera paths or every Replay Mod control.

The shared fixture lives under [src/test](src/test) and uses the Fabric
client-GameTest bootstrap from Boots' runtime test project with the packaged
Boots and Leggings artifacts. `verifyMediaRuntime` separately checks all current family outputs and
the absence of the joint compatibility selection on the interactive media classpath.

These checks are not an exhaustive manual visual review of every equipped
preset. Walking-animation fidelity and replays from incompatible older builds
are not covered.
