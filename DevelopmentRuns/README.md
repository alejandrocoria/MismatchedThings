# Joint development runs

Persistent development clients and servers with current Mismatched Boots,
Mismatched Leggings and the shared Things core, plus pinned compatibility mods.
Each family mod loads from its project output, once; rebuilding/copying release
JARs manually is not required. This directory is tooling, not a published mod.

## Launch

Import the repository root Gradle project in IntelliJ. Four additional native
run configurations are generated alongside the existing per-product runs:

| IntelliJ configuration | Gradle task |
| --- | --- |
| MismatchedThings — Fabric Client | `:DevelopmentRuns:Fabric:runClient` |
| MismatchedThings — Fabric Server | `:DevelopmentRuns:Fabric:runServer` |
| MismatchedThings — NeoForge Client | `:DevelopmentRuns:NeoForge:runClient` |
| MismatchedThings — NeoForge Server | `:DevelopmentRuns:NeoForge:runServer` |

The separate [Fabric media client](media/README.md) adds
**MismatchedThings — Fabric Media** (`:DevelopmentRuns:Fabric:runMedia`) for
family-wide screenshots and Replay Mod. It uses `Fabric/runs/media`, all current
family outputs and the photo packs, without this environment's compatibility
collection. Its appearance generator still targets boots only.

Run Gradle commands from the repository root, for example:

```powershell
.\gradlew.bat :DevelopmentRuns:Fabric:runClient
.\gradlew.bat :DevelopmentRuns:NeoForge:runServer
```

Both IDE and Gradle launches prepare their selected external artifacts and build
current family classes/resources first. Clients are interactive and visible;
servers run without a GUI. Fabric retains its usual `eula.txt` requirement;
NeoForge retains its native development behavior. Connect clients and servers
using the same loader. These content runs use the normal repository baselines,
not a reduced minimum-loader compatibility profile.

On the first Fabric launch, WorldWeaver may present its native welcome screen;
acknowledge it to continue to the menu. Preparation does not skip that screen or
override a developer's mod settings.

Worlds, configuration, options and logs live in `Fabric/runs/client`,
`Fabric/runs/server`, `NeoForge/runs/client` and `NeoForge/runs/server` relative
to this directory. They are persistent and ignored by Git. Preparation does not
reset them or synchronize their `mods` folders. External mods are supplied on
the runtime classpath: leave their manually copied duplicates out of `mods`.

## Included external mods

[mods.json](mods.json) selects loader/side roots and intentional extras. It references IDs
in the shared [artifact catalog](../mods/MismatchedThings/RuntimeTests/mod-artifacts.json),
which owns exact publications, SHA-256 hashes and audited external dependency
edges. Preparation expands these roots per side. Required nested libraries
remain supplied by their upstream JARs; they are not separate catalog entries.

- Fabric: Advanced Netherite, Better End, Better Nether, Create Fly and the
  unofficial Mystical Agriculture port, plus BCLib, WorldWeaver and Cucumber.
- NeoForge: Advanced Netherite, AllTheModium, Aquaculture 2, Forbidden Arcanus,
  Ice and Fire Community Edition, Just Dire Things, Mystical Agriculture,
  Neo Vitae, Oritech, Roots Classic, Silent Gear, The Aether II and The Undergarden,
  with their required libraries. ATM: Compat supplies AllTheModium's Silent Gear
  materials and plates on both sides; it is an explicit optional integration,
  not a required dependency of either source mod.
- Both clients add JEI and Legendary Tooltips with Iceberg and Prism. NeoForge
  adds Athena only in the client, as required by Oritech. There is no REI.

The shared pins also serve maintained tests; Boots and Leggings use the same
BetterX publications as these runs. A complete collection starting successfully
does not certify every ability interaction or replace standalone/product-packaging
acceptance.

## Preparation and checks

Downloads happen on launch/preparation, not just from importing these projects.
The existing shared resolver verifies SHA-256 and reuses its staging/cache.
See its [artifact guide](../mods/MismatchedThings/RuntimeTests/README.md#third-party-test-artifacts).
To prepare all four runs without starting Minecraft:

```powershell
.\gradlew.bat :DevelopmentRuns:prepare
```

After preparation, use `--offline` for cached builds/runs. To check pins and
client/server classpath separation without touching persistent run data:

```powershell
.\gradlew.bat :DevelopmentRuns:Fabric:verifyRuntime :DevelopmentRuns:NeoForge:verifyRuntime --offline
```

Classpath reports are written to `<loader>/build/development/`. No fixture mods
or gameplay test code is loaded in the joint environment.

## Extending the environment

Add a future family product to `familyProjects` in [runtime.gradle](runtime.gradle)
after its loader projects exist. Each loader groups that product's main output
for native discovery; Common remains compiled into the loader output. Keep core
runtime libraries and access rules consistent with the product projects, without
adding their packaged JARs or nested core copies to the same run.

For another compatibility mod, add/reuse a shared pinned catalog entry, select
its ID in `mods.json`, audit its external dependency edges in the catalog,
keep deliberate extras explicit rather than inventing native dependencies,
and repeat preparation/classpath checks plus the relevant actual startup. Do not
make it a production dependency or read JARs from a developer's installation.
