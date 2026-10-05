# Runtime tests

This directory contains test-only mods and their Gradle run configurations.
Production sources remain in `Common`, `Fabric`, and `NeoForge` at the
repository root. The fast Common JUnit suite remains in `Common/src/test/java`.

## Layout

- `Common/src/main/java`: shared network acceptance scenarios and helpers.
  This is a shared source directory, not a separate Gradle project.
- `Fabric/src/gametest`: server and client GameTests, fixtures, and probes.
- `Fabric/src/viewerCompatibilityTest`: optional real JEI/REI reload and
  repeated-opening probes, separate from the clean core client suite.
- `Fabric/src/networkTest`: the auxiliary mod for real server/client network
  acceptance. It is separate from `gametest` so its entrypoints and fixtures
  do not run during GameTests, or vice versa. The `main` source set is empty.
- `NeoForge/src/main`: the auxiliary mod for network acceptance and targeted
  client geometry and tooltip checks. These use separate run directories and
  entry paths.

Runtime tests load the packaged production mod. Production output is available
only for compilation; test classes, metadata, and fixtures are not included in
the production artifacts. No runtime test infrastructure is installed in the
normal indev client/server runs.

## Commands

Run Gradle tasks sequentially from the repository root:

```powershell
.\gradlew.bat :Common:test
.\gradlew.bat :Fabric:verifyGameplay
.\gradlew.bat :RuntimeTests:Fabric:verifyDamageTagReload
.\gradlew.bat :Fabric:verifyPersistenceRestart
.\gradlew.bat :Fabric:verifyClient
.\gradlew.bat :RuntimeTests:NeoForge:verifyGeometryCharacterization
.\gradlew.bat :RuntimeTests:NeoForge:verifyTooltipCharacterization
.\gradlew.bat verifyLoaderNetworking
```

The established Fabric verification, GameTest run, and GameTest compilation
tasks delegate to `:RuntimeTests:Fabric`. Direct `:RuntimeTests:Fabric` commands
also work. `verifyGameplay` includes an isolated server run for changing a
damage-type tag and reloading datapacks; the direct task above runs just that
case. `build`/`check` still include the Fabric server GameTests, but do not
launch client, persistence-restart, geometry, or network acceptance runs.
The focused NeoForge tooltip characterization is likewise an explicit client
task rather than part of `build`/`check`.

Optional recipe-viewer regressions run individually:

```powershell
.\gradlew.bat :RuntimeTests:Fabric:verifyJeiRecipeReload
.\gradlew.bat :RuntimeTests:Fabric:verifyReiRecipeReload
```

These require the exact existing Fabric client JARs named and SHA-256-pinned in
[Fabric/build.gradle](Fabric/build.gradle). They copy those artifacts read-only
from `Fabric/runs/client/mods` into disposable test runs; they do not download
them, alter the indev client, or make viewers dependencies of ordinary tests.
Each run checks the actual viewer API and opening path, one aligned two-output
cycle, repeated reopening, and datapack removal/replacement/restoration, followed
by disconnect invalidation. Screens are closed before datapack reload; rendering
REI's entry registry during its asynchronous mutation is outside this check.
The replacement is a test-only serializer which opts into Fabric synchronization.
Third-party Fabric recipe serializers must likewise opt into that loader API.
This is canonical Fabric viewer coverage, not a claim of a second real NeoForge
viewer test or human visual acceptance.

`verifyLoaderNetworking` orchestrates real connections, prepares isolated
directories, and checks result markers. Select a pair with
`-PnetworkAcceptanceCase=fabric-fabric` or `neoforge-neoforge`; the default also
includes JourneyMap coexistence. The internal `runNetworkTestServer` and
`runNetworkTestClient` tasks require that preparation and are not standalone
verification entrypoints.

Same-loader network acceptance also checks the actual inventory/use/dispenser
routes: a bare invalid shell never equips, while a valid crafted pair still
equips. The server writes `equipment-success.txt` alongside the existing network
markers. Client previews use the recipes actually synchronized by the loader,
not a newly constructed default recipe.

Clients are hidden and muted by default; NeoForge auxiliary clients dismiss
warning-only loading screens. Fabric client GameTests can show their window
with `-PshowClientGameTestWindow=true`, but remain muted. Servers have no GUI.

Reports, screenshots, and run directories are under each runtime project's
`build` directory. Network acceptance result markers and process logs remain
under the root `build/network-acceptance` directory. Old `CrossLoader*` build
directories and old Fabric test reports are not migrated or deleted.

## Item geometry coverage

The Fabric client suite exercises both ordinary baked quads and real Fabric
Renderer API meshes. Synthetic fixtures check full-scale clipping, rotated
triangle/pentagon cuts, per-vertex attributes, quad properties, layer tint and
foil, bounds, and complete atomic fallback on late incompatibility. Submission
probes include Fabric's extended mesh overload, not just vanilla quad lists.
Both routes also render hands, ground rotations, frames, dyed/trimmed boots,
enchantment glint, simultaneous items, and resource reload. No external mod or
resource pack is required by these tests.

Structural assertions and tolerant pixel checks do not replace interactive
review of external models, oblique seams, or GUI ownership. Common unit tests
cover the pure clipping/provenance and shared tessellation convention; Fabric
client tests cover the real API's encoding, normals, and mesh submission.
