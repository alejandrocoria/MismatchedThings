# Runtime tests

This directory contains test-only mods and their Gradle run configurations.
Production sources remain in `Common`, `Fabric`, and `NeoForge` at the
repository root. The fast Common JUnit suite remains in `Common/src/test/java`.

## Layout

- `Common/src/main/java`: shared network acceptance scenarios and helpers.
  This is a shared source directory, not a separate Gradle project.
- `Fabric/src/gametest`: server and client GameTests, fixtures, and probes.
- `Fabric/src/test`: fast loader-local reflection/bytecode-boundary JUnit regressions
  against the packaged Fabric classes, with fake optional APIs and no game launch.
- `Fabric/src/viewerCompatibilityTest`: optional real JEI/REI reload and
  repeated-opening probes, separate from the clean core client suite.
- `Fabric/src/networkTest`: the auxiliary mod for real server/client network
  acceptance. It is separate from `gametest` so its entrypoints and fixtures
  do not run during GameTests, or vice versa. The `main` source set is empty.
- `NeoForge/src/main`: the auxiliary mod for network acceptance, targeted
  client geometry/tooltip checks, and opt-in real Ice and Fire damage/set
  acceptance. These use separate run directories and entry paths.
- `NeoForge/src/iceAndFireTideTest`: separately packaged counters and competing
  mixins for opt-in acceptance of the production Tide adapter, not a second owner.
- `NeoForge/src/iceAndFireTideHead`: an independent test-only HEAD owner with its
  own data/tick/guard, used for optional composition and rejection checks.

Runtime tests load the packaged production mod. Production output is available
only for compilation; test classes, metadata, and fixtures are not included in
the production artifacts. No runtime test infrastructure is installed in the
normal indev client/server runs.

## Commands

Run Gradle tasks sequentially from the repository root:

```powershell
.\gradlew.bat :Common:test
.\gradlew.bat :RuntimeTests:Fabric:test
.\gradlew.bat :Fabric:verifyGameplay
.\gradlew.bat :RuntimeTests:Fabric:verifyDamageTagReload
.\gradlew.bat :Fabric:verifyPersistenceRestart
.\gradlew.bat :Fabric:verifyClient
.\gradlew.bat :Fabric:verifyGameTestContextGuard
.\gradlew.bat :Fabric:verifyQuiltSmoke
.\gradlew.bat :RuntimeTests:NeoForge:verifyGeometryCharacterization
.\gradlew.bat :RuntimeTests:NeoForge:verifyTooltipCharacterization
.\gradlew.bat verifyLoaderNetworking
.\gradlew.bat verifyPackagedDependencies
.\gradlew.bat verifyPackagedDependencyChecks
```

The established Fabric verification, GameTest run, and GameTest compilation
tasks delegate to `:RuntimeTests:Fabric`. Direct `:RuntimeTests:Fabric` commands
also work. `verifyGameplay` includes an isolated server run for changing a
damage-type tag and reloading datapacks; the direct task above runs just that
case. `build`/`check` include the Fabric server GameTests and loader-local JUnit, but do not
launch client, persistence-restart, geometry, or network acceptance runs.
The focused NeoForge tooltip characterization is likewise an explicit client
task rather than part of `build`/`check`.

The NeoForge geometry client also exercises native equipment model replacement:
side ownership after extension resolution, cached models shared across queued
entities, custom animation, ordinary/default/non-humanoid controls, exactly one
resolution per source, source immutability, native dye/overlay/trim/foil,
extension texture, lighting, and outline parameters. These are actual native
deferred submissions, not only the earlier recording-renderer fixture.

An optional Ice and Fire CE probe uses the pinned installed candidate JARs:

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyGeometryCharacterization -PiceAndFireTest=true
```

It copies only Ice and Fire 2.2-beta.3, Uranus 3.0-alpha.1, and Jupiter
2.4.3 into the disposable geometry client, verifies their SHA-256 pins before
copying, and leaves the development mods directory unchanged. Source JARs
default to `NeoForge/runs/client/mods`; override with
`-PiceAndFireTestModsDirectory=<directory>` if needed. Integration 0.2 is nested
in Ice and Fire, not another source JAR. The ordinary run needs none of these mods.

The probe checks all 30 custom-model boot variants and Sheep Boots, each in
left/vanilla, vanilla/right, and same-item pairs (93 ordered pairs). It uses
real registered items and their model/texture extensions with controlled native
equipment assets, because item defaults are not bound before opening a world.
It does not validate actual default components, gameplay, complete material
appearance, unusual animations, or pixel-level visual quality. Current artifacts
and manual acceptance remain in [COMPATIBILITY.md](../local-docs/COMPATIBILITY.md#ice-and-fire-community-edition).

The independent damage/set gate opens a real dedicated-server world with the
same pinned artifacts and does not need a client:

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyIceAndFireDamageCompatibility
.\gradlew.bat :NeoForge:test '-PiceAndFireGuardJar=NeoForge/runs/client/mods/iceandfire-2.2-beta.3.jar'
```

The first task copies only the three SHA-256-verified mod JARs plus the packaged
production mod into `NeoForge/build/run/iceAndFireDamageCompatibility`. It uses
an explicit flat world, localhost binding and an ephemeral port, stops itself,
and has a 240-second process bound. Source files and development clients are not
modified. `-PiceAndFireTestModsDirectory=<directory>` also overrides its source
artifact directory. Markers and the report are under
`NeoForge/build/ice-and-fire-damage-markers`; the process log is under
`NeoForge/build/reports/ice-and-fire-damage` (both relative to this directory).

It creates mixed boots through the production factory with real bound item
defaults and observes, without modifying, the native Pre damage event before
and after upstream handlers. It checks actual `hurtServer` health loss for 15
dragon variants and all three damage types, ordinary/inverse/two-source cases,
all eight combinations of other dragon armor pieces, malformed/bare/spoofed
roots, and unchanged equipment identity/source snapshots. It also covers all
three Troll variants and additive other-piece masks, exact Lightning set
participation with complete/missing/wrong pieces, and ordinary lightning versus
dragon lightning. Finally it reloads a fixture-only datapack marking dragon fire
as a projectile, proving native sequential Troll/dragon branch composition.
The initial matrix uses unmodified native tags; only the overlap cases use this
explicit tag extension in the disposable server world.
The gate is opt-in, not part of ordinary `build`/`check`; those need no Ice and
Fire installation. The second command additionally enables the real-JAR
bytecode guard characterization in NeoForge JUnit. Without that property, artifact-dependent
characterizations are skipped; artifact-independent guard regressions still run.
This is dragon/projectile damage and Lightning-set acceptance, not Tide effect
acceptance or full Ice and Fire gameplay/visual approval.

The local-guard coexistence gate adds two real fixture mixins around the same
FEET operations (priorities 1500/500 around production 1000), an independent
fixture helmet source container, and an unrelated executable call inserted
before injection preparation:

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyIceAndFireMixinCoexistence
.\gradlew.bat :RuntimeTests:NeoForge:verifyIceAndFireMixinCoexistence '-Pneoforge_version=26.1.2.99'
```

It runs the 340-case baseline plus 11 helmet/boots composition cases with the
helmet adapter at each priority. A third run deliberately replaces the native
FEET Troll target: six real damage cases prove the damage adapter stays inert
while other-slot native behavior and the independently guarded Lightning adapter
remain usable. Injector count debugging is enabled, and the verifier checks zero
guard warnings in compatible cases versus one bounded warning in the negative
case. Logs and copied reports are in
`NeoForge/build/reports/ice-and-fire-coexistence`. These mixins are property-gated
and test-only; they are not actual future mixed-helmet/chestplate/leggings mods
and do not establish all 16 subsets or Tide activation.

### Production Tide FEET acceptance

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyIceAndFireTideProduction --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyIceAndFireTideProduction '-Pneoforge_version=26.1.2.99' --console=plain
```

This is the sole maintained Tide runtime gate. The earlier logical-slot spike,
transformation observer and prototype guard/owner/API suites were retired after
production acceptance. Their dated findings remain in
[the Tide record](../local-docs/ICE-AND-FIRE-TIDE-IMPLEMENTATION.md#6-current-validation-record);
their old task names are historical, not current commands.

Nineteen bounded, sequential processes use the **unmodified production JAR**,
the three SHA-256-pinned optional artifacts and a test-only adversary/counter JAR.
There is no prototype FEET owner, dispatcher, item registry or modified production
copy. Only the production plugin grants FEET readiness. Instrumentation checks
the fixed blue receiver, one call per qualified tick, fresh unchanged carriers,
cached bindings and bounded warnings without adding work to the native entry/base.

The Both process retains the full 15-armor-mask, dry/wet, inverse/one/two-source
matrix and seven colors, real rain, stronger/hidden/infinite effects,
transitions/expiry, effect veto, mobs and component/native-slot disagreement.
It also runs all 340 real dragon/Troll/Lightning damage cases in the same JVM.
Feet proves physical HEAD absence and both source orientations/counts.
Other processes use four representative armor masks plus native full sets and
invalid roots, checking exact effect-hook traces/states and immutable equipment.
Each present-Ice process outside Both adds six damage coexistence controls rather
than repeating the 340-case matrix.

Distinct processes retain chains above/below production priority, late wrapping,
base read/mutation, extra work, changed/replaced/ambiguous FEET predicates,
altered publication, optional API absence/change and each owner's wrong-class/
linkage receiver failures. Rejections verify that recognition and activation
are inert together; independently healthy owners and original armor still work.
The production guard JUnit tests cover missing verification/publication/hooks
and consumer provenance directly, without keeping a second FEET implementation.

HEAD remains one physically independent test JAR with its own source schema,
receiver, tick, verifier and API inspection. Its sources are checked in directly;
there is no generated/relocated guard pipeline or production dependency.
The gate inspects its archive for forbidden references. It is not a released
mixed-helmet integration or a claim about four optional armor mods.

Runs use the disposable flat world in `NeoForge/build/run/iceAndFireTideTest`,
localhost with an ephemeral port, automatic shutdown and a 240-second child bound.
Markers under `NeoForge/build/tide-production-markers` are cleared per process.
Logs/copied reports remain under
`NeoForge/build/reports/tide-production/<loader-version>`.
ApiField changes only a build-directory copy of the native mod; original
development JARs, gameplay worlds and production archives are never rewritten.
This gate is opt-in, not part of ordinary `build`/`check`. Use .114 routinely;
repeat the supported .99 floor for loader/Mixin-boundary changes and release checks.
Manual confirmation and limits are tracked in
[COMPATIBILITY.md](../local-docs/COMPATIBILITY.md#ice-and-fire-community-edition).

The Create Fly verifier includes a real `BeltBlock.entityInside` passenger-map
test with a second `ItemStack.is(DIAMOND)` inserted directly into the method by a
test-only mixin plugin. It proves Cardboard recognition remains local, the
unrelated predicate retains its own true/false results, both orientations work,
and ordinary/invalid/spoofed/crouching/flying/Diving controls retain native
behavior. It does not require a powered belt or claim actual belt movement.
Run it with `:RuntimeTests:Fabric:verifyCreateFlyCompatibility`; the three local
shape JUnit regressions are part of `:RuntimeTests:Fabric:test`.

The broad server GameTest XML reports mark context-only no-ops as skipped rather
than successful coverage. `verifyGameTestContextGuard` deliberately starts one
selected case without its required property and verifies the expected failure;
its negative XML/log stay under `Fabric/build/context-guard`, outside positive
GameTest reports. The focused fixture tasks enable their actual contexts.

The Fabric JUnit task verifies Better Nether's live configuration and item-set
queries, one warning and permanent boundary disablement after a reflective/API
failure, and no per-tick retry. The real optional-mod gate remains
`:RuntimeTests:Fabric:verifyBetterXCompatibility`; fake APIs do not replace it.

`verifyQuiltSmoke` uses real Quilt Loader 0.31.0-beta.4, its pinned launcher
libraries, Fabric API, and the packaged production Fabric JAR in a disposable
server run. It selects the existing Workbench transaction cases, not the full
Fabric suite; it does not claim Quilt client/rendering or optional-mod coverage.
There is no production Quilt module. The verifier bounds the child process and
checks actual loader identity and XML results. `runQuiltSmoke` is its internal
launch task. No Quilt library enters production artifacts or ordinary tests.

`verifyPackagedDependencies` declares and builds its final packaged JAR inputs.
Its companion negative-control task checks corrupted copies in the root build
directory, never modifies release JARs, and verifies rejection diagnostics.

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

To validate the declared minimum NeoForge loader, add
`'-Pneoforge_version=26.1.2.99'` to the selected startup/network verifier.
Explicit `neoforge_version`, `fabric_loader_version`, and `fabric_api_version`
overrides propagate to its child Gradle runs. Normal indev NeoForge stays .114.

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

## Just Dire Things passive Jump Boost

These optional gates use the production NeoForge JAR and the locally supplied
`justdirethings-1.6.11.jar` (SHA-256 verified, no external classes compiled into
production). Override its location with `-PjustDireThingsTestJar=...` for direct
run preparation. They are detached from ordinary `build`/`check`:

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireJump
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireJumpGuards
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireSmithing
```

The jump gate first runs a bounded server movement/actual-fall-health matrix,
then a hidden/muted client in a copy of its disposable world. Native originals
are controls. The small guard gate separately exercises compatible competing
WrapOperations, a replaced fall read disabling both hooks, and quiet optional
absence. Smithing retains its independent recipe/menu acceptance.
Every server variant marks its fixture connections client-loaded, sets actual
survival mode and requires a nine-block unprotected control to lose six health
points. Each scenario's nine-block comparison requires a nonzero ordinary
control, preventing immune-player zero-versus-zero passes. Hand precedence uses Blazejet Wands,
contrasted with a non-AIRBURST Ferricore Sword and removal in both hands.
Each process is bounded to 240 seconds. Runs/markers reuse the isolated smithing
fixture directories; do not run these gates concurrently. Logs are under
`RuntimeTests/NeoForge/build/reports/just-dire-jump/` (smithing has its own report
directory). A successful marker is required, not merely a zero process exit.

Native-bytecode guard unit checks are opt-in with
`-PjustDireGuardJar=NeoForge/runs/client/mods/justdirethings-1.6.11.jar`.
The additional final-pair test accepts `-PjustDireTransformedClass=...` pointing
at an active production/compatible MixinExtras export, not the absent/rejected
variant. The design, exclusions and manual handoff are maintained in
local-docs, not inferred from successful installation recipes.

## Just Dire Things Negate Fall Damage characterization (8.4 A)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireNegateFall --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireNegateFall "-Pneoforge_version=26.1.2.99" --console=plain
```

This opt-in server gate uses the same hash-pinned native JAR and isolated
smithing preparation. It does **not** enable mixed Negate Fall Damage or compile
native classes into production. Its 43 characterization cases exercise original
Celestigem/Eclipse Alloy boots through the actual fall event bus, FE thresholds,
fractional distance, full/partial payment, Unbreaking and Unbreakable, Feather
Falling, tool settings/native config, Jump precedence and both AIRBURST hands.
Survival cases call actual `causeFallDamage` and require a positive unprotected
health-loss control. Fixture connections are explicitly marked client-loaded;
otherwise newly constructed server players reject damage. Creative/spectator
checks set the actual game mode and post the event on the native bus, because
physical falls in those modes bypass the LivingFallEvent consumer.

Equipped mixed roots exercise the existing production FE capability, not another
provider: mapped FEET slot access, rollback and partial commit in both output
orientations, unchanged wear/provenance/sibling/main inventory, and cursed boots.
The slot exchange path rejects Curse of Binding; direct access to the actual
equipped stack transactionally consumes FE without removing armor, as native
powered boots do. The final mixed control takes actual fall damage and spends
no FE, proving that this block is characterization only.

The process is bounded to 240 seconds and requires success/report markers.
Versioned logs and retained reports are under
`RuntimeTests/NeoForge/build/reports/just-dire-negate-fall/<neoforge-version>/`.
Preparation shares disposable smithing run/marker directories: run sequentially
with other JDT gates. Production behavior, selected access boundary, limitations
and remaining blocks are tracked in local-docs.

## Just Dire Things production energy storage (8.3 A)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireResources --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireResourcesGuards --console=plain
```

These opt-in acceptance gates use the same hash-pinned native JAR and isolated
preparation as the smithing/jump gates. They exercise the production factory,
recomposer and energy capability from the real mod JAR. The old fixture storage
provider and wrappers have been retired; there is no second resource implementation.
Production storage is active in ordinary NeoForge clients when native binding succeeds.

The adapter uses maximum native source capacity, floor-average initial charge
and independent live root resources. Its 41 scenario groups exercise real FE
transfers, source debit, rollback, inventory-slot ItemAccess, retained-handler tier/count changes,
native static PoweredItem consumption and the EnergyTransmitterBE transfer
primitive, production recomposition and serialization. Actual Workbench previews,
takes in either order, blocked shift-take and committed/uncommitted close verify
source consumption and pending-inverse independence. Real native smithing recipes
and output takes preserve spent/zero/absent charge across upgrade and tier changes.
Configured attribute rejection remains effective, while generic FE `share` cannot
duplicate this dedicated resource. With block B present, it also checks
live powered-attribute depletion independently of FE storage. Native originals
are controls, not substituted formulas.

An actual second server process reloads spent, zero and absent FE from barrels
in the saved world. A hidden/muted NeoForge client independently assembles its
synchronized-rule preview, receives actual integrated-server Workbench outputs
and spent/absent inventory packets, and checks client capability/provenance.
The same client process also captures native normal/controlled-Shift tooltips:
one exact current root FE line, maximum tier capacity, qualified neutral source
abilities with retained missing-upgrade details, hidden FE/sources, inverse outputs
and unchanged vanilla lore/provenance. A test-only `Minecraft.hasShiftDown` mixin
is active only during capture; it does not emulate physical keyboard input.
The actual native Pocket Generator inventory tick transfers precharged FE to the
mixed pair as its sole receiver, checking sender/receiver conservation and the
updated real client packet/tooltip. Fuel burning/efficiency and Curios are not tested.
The guards gate checks absent-mod quiet fallback and an injected missing native
component binding, with one warning and unchanged native FE/non-powered Jump.

The transfer primitive is not full automatic machine discovery, wireless
charging, settings or powered ability acceptance. Ability costs, armor wear,
refunds, bars and active/passive powered consumers remain outside block A.
The selected design, exact evidence and next production decisions are recorded
in local-docs/JUST-DIRE-THINGS-RESOURCES-IMPLEMENTATION.md.

Each subprocess is bounded to 240 seconds and requires a success marker.
The main gate runs server, world-restart server and client sequentially; the
guards gate runs absent and incompatible-API servers separately.
Versioned reports are under `RuntimeTests/NeoForge/build/reports/just-dire-resources/`.
Preparation resets the shared disposable smithing run/marker directories;
do not run these gates concurrently. Repeat with `"-Pneoforge_version=26.1.2.99"`
to exercise the supported loader floor.

## Just Dire Things production live attributes (8.3 B)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireAttributes --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireAttributes "-Pneoforge_version=26.1.2.99" --console=plain
```

This opt-in gate uses production lifecycle callbacks, component registration and
the production pre-event CommonHooks transformation. Nine frozen projections
reuse the public pure Common composer during creation/recomposition, not during
live queries. Metadata is one bounded NeoForge-owned persistent/network component;
it is not another energy wallet. The old fixture preparation/dispatch implementation
has been removed, recoverable from Git. Test support only supplies native controls
and a competing default-input transformation.

The live run checks empty creation/recharge/depletion, actual entity equipment
ticks and damage mitigation, both source orders/inverse outputs, native threshold
queries, root powered Step Assist controls, source-installed provenance, frozen
policies, source-wise rebuilding, custom display and same-identity ownership.
A real earlier competing mixin and later attribute event exercise coexistence.
The second process modifies the actual readiness getter; final export denies
permission and requires generic fresh-root
fallback. It also loads a charged root saved by the live process and requires
its persisted depleted-attribute baseline: no permanent powered protection when
the hook is missing, including a tier rebuild without new dynamic metadata.
Removing the last powered source still permits ordinary recomposition.
A third process injects a missing attribute component binding
without changing native JDT, production storage or the independent passive Jump
adapter. Each process requires its
own success marker; each is bounded to 240 seconds.

Reports are under `RuntimeTests/NeoForge/build/reports/just-dire-attributes/`.
The gate shares disposable smithing preparation, so run it sequentially with
the other Just Dire Things gates. The attribute-test property selects only its
fixture harness; production live attributes do not depend on that property.
The 19 live scenario groups also cover
explicit managed edits through tier change/storage removal and actual native
smithing upgrade/output extraction; each fallback process has two scenario groups.
Warning counts are asserted. Four local bytecode regression tests exercise native
entry/final owned flow, earlier transforms, wrong root/defaults, discarded router/
event results, foreign routing and readiness edits (`:NeoForge:test`).
The separate resource gate above now includes non-default frozen attribute rules
through a real world restart and actual client equipped armor/Step Assist at
charged/depleted FE, in addition to production Workbench and packet agreement.
Powered abilities, damage-to-FE conversion and manual 8.3 acceptance are not claimed.

## Just Dire Things consolidation and manual handoff (8.3 C)

There is one production FE provider and one production attribute projection
implementation. The former research providers/wrappers/dispatch have been retired;
native controls and competing/failure fixtures remain in the maintained gates above.
No additional production behavior or general resource framework is introduced by C.

Repeat the bounded regression matrix sequentially (never use `--parallel` for
these gates, which share disposable run/marker directories):

```powershell
.\gradlew.bat :Common:test :NeoForge:test :Fabric:verifyGameplay :RuntimeTests:NeoForge:verifyJustDireSmithing :RuntimeTests:NeoForge:verifyJustDireJump :RuntimeTests:NeoForge:verifyJustDireJumpGuards :RuntimeTests:NeoForge:verifyJustDireAttributes :RuntimeTests:NeoForge:verifyJustDireResources :RuntimeTests:NeoForge:verifyJustDireResourcesGuards verifyPackagedDependencies --console=plain
.\gradlew.bat :NeoForge:test "-PjustDireGuardJar=NeoForge/runs/client/mods/justdirethings-1.6.11.jar" :RuntimeTests:NeoForge:verifyJustDireAttributes :RuntimeTests:NeoForge:verifyJustDireResources :RuntimeTests:NeoForge:verifyJustDireResourcesGuards "-Pneoforge_version=26.1.2.99" --console=plain
.\gradlew.bat :NeoForge:test "-PjustDireGuardJar=NeoForge/runs/client/mods/justdirethings-1.6.11.jar" :RuntimeTests:NeoForge:verifyJustDireSmithing verifyPackagedDependencies --console=plain
```

The last command restores/verifies the normal .114 production artifact and its
native guard tests after the floor run. Smithing also retains `report.txt` beside
its process log, independently of later resets of shared markers.
Native artifacts, versioned reports, coverage and warning limits
remain owned by the individual gates; a successful matrix is not manual acceptance.
The ordinary-client checklist and closure criteria are in
[the block C record](../local-docs/JUST-DIRE-THINGS-RESOURCES-IMPLEMENTATION.md#9-block-c-consolidation-and-manual-acceptance).
It retains ordinary root durability and excludes powered abilities, wear/payment
conversion, settings/bars, refunds and broader charging discovery.
