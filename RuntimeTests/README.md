# Runtime tests

This directory contains test-only mods and their Gradle run configurations.
Production sources remain in `Common`, `Fabric`, and `NeoForge` at the
repository root. The fast Common JUnit suite remains in `Common/src/test/java`.

## Layout

- `Common/src/main/java`: shared loader-server/network acceptance scenarios and helpers.
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
are controls. Its six direct jump checks are followed by eight actual keyboard
flights: ordinary/native Ferricore height controls and Eclipse Alloy Negate +
Ferricore passive Jump, both orientations, charged/depleted/recharged. FE changes
in place on the equipped server root; normal equipment/attribute packets must
agree before each flight. The same mixed root must retain the native jump height
without mutating its stack during ascent. This does not implement powered Jump.
The small guard gate separately exercises compatible competing
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

## Just Dire Things Negate Fall Damage (8.4)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireNegateFall --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireNegateGuards --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireNegateFall "-Pneoforge_version=26.1.2.99" --console=plain
```

This opt-in server gate uses the same hash-pinned native JAR and isolated
smithing preparation. It exercises the actual production mixed adapter, never
an alternate wallet/ability or native classes compiled into production. Its
94 maintained native/mixed cases exercise original and mixed Celestigem/Eclipse Alloy
boots through the actual fall event bus, FE thresholds,
fractional distance, full/partial payment, Unbreaking and Unbreakable, Feather
Falling, tool settings/native config, Jump precedence and both AIRBURST hands.
Survival cases call actual `causeFallDamage` and require a positive unprotected
health-loss control. Fixture connections are explicitly marked client-loaded;
otherwise newly constructed server players reject damage. Creative/spectator
checks set the actual game mode and post the event on the native bus, because
physical falls in those modes bypass the LivingFallEvent consumer.

Equipped mixed roots use the production FE capability, never another provider.
Block A's exploratory mapped-slot/direct-access loops were retired: actual
mixed falls now prove full/partial commit, unchanged non-FE state and cursed-boot
payment. Two opt-in failure controls alter only the return amount after a real
production extraction, requiring transactional rollback, unchanged root FE and
ordinary health/Jump fallback. No alternate extraction or ability implementation
is retained. Mixed cases additionally check both root orientations,
non-owner provenance, two sources paying once, live root controls versus frozen
source toggles, forged installation rejection, Negate-before-passive-Jump and
hand precedence, root Unbreaking, actual cursed-root depletion/subsequent fall,
invalid count/data and non-player scope. Whole non-FE state is compared after
each fall. Exceptional negative/non-finite/overflowing bills leave the event
and root unchanged, with exactly one diagnostic across repeated cases.

The guard gate runs six small process variants: real compatible MixinExtras
wrappers, changed late native multiplier, forced final permission, unavailable
Negate binding, missing shared companion and actual missing production FE
capability. Native originals, repeated mixed falls with health/FE, final getter
values, actual Jump movement, independent storage/live armor and bounded warning
counts are checked without repeating the entire payment matrix. Optional
absence remains part of the existing `verifyJustDireJumpGuards` gate.

The process is bounded to 240 seconds and requires success/report markers.
Versioned logs and retained reports are under
`RuntimeTests/NeoForge/build/reports/just-dire-negate-fall/<neoforge-version>/`.
Preparation shares disposable smithing run/marker directories: run sequentially
with other JDT gates. Production behavior, selected access boundary, limitations
and remaining manual acceptance are tracked in local-docs. The lifecycle/client
outcomes belong to the extended resource gate below, not this server-only matrix.
Guard/native unit checks use
the same opt-in `justDireGuardJar`/`justDireTransformedClass` properties described
above, with an active production or compatible export.

## Just Dire Things Negate consolidation and manual handoff (8.4 C)

Run existing gates sequentially; no second lifecycle/client harness is needed:

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireResources :RuntimeTests:NeoForge:verifyJustDireNegateGuards :RuntimeTests:NeoForge:verifyJustDireJump :RuntimeTests:NeoForge:verifyJustDireJumpGuards :RuntimeTests:NeoForge:verifyJustDireAttributes :RuntimeTests:NeoForge:verifyJustDireResourcesGuards :RuntimeTests:NeoForge:verifyJustDireSmithing :RuntimeTests:NeoForge:verifyJustDireNegateFall :NeoForge:test "-PjustDireGuardJar=NeoForge/runs/client/mods/justdirethings-1.6.11.jar" "-PjustDireTransformedClass=RuntimeTests/NeoForge/build/run/justDireSmithing/.mixin.out/class/com/direwolf20/justdirethings/common/events/LivingEntityEvents.class" --console=plain
```

Repeat with `"-Pneoforge_version=26.1.2.99"`, then restore the ordinary .114
artifact and run `verifyPackagedDependencies`. The last Negate server process
supplies an active final export to the opt-in unit cases; an absent/denied export
must not be used. Root installation and source-wise tier smithing are rechecked because resource fixtures
now install upgrades and transform tiers on actually spent roots. Shared/Fabric
gameplay is not broadened when only NeoForge fixture coverage changes.

`verifyJustDireResources` now also checks two real Workbench roots/orientations:
Negate spends 450 from 3,000 FE; loaded Step Assist/tier smithing preserves 2,550;
after retaining a spent copy, an actual fall depletes the working root and
another fall must cause real damage.
Four charged/empty roots persist in the world barrel, compare against separately
saved expected stacks after a second process starts, and retain actual protection/
payment or rejection after restart. Source snapshots, projections and wear remain
unchanged across each ability debit.

The same hidden/muted client additionally moves the sole receiver to FEET,
uses survival falls, observes natural equipment/attribute packets and exact live
FE tooltips after payment/depletion, requires an unprotected positive-health-loss
control, recharges through a native Pocket Generator and proves protection returns.
No equipment-copy replacement or forced broadcast is used after ability debits.
Client armor/Step Assist must agree at charged, depleted and recharged states.
These are actual integrated-server consumers/packets, not manual visual, physical
keyboard or walking-off-a-platform acceptance. Ordinary-client review remains
the owner's separate checklist in local-docs; other powered abilities stay deferred.

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
and independent live root resources. Its 47 scenario groups exercise real FE
transfers, source debit, rollback, inventory-slot ItemAccess, retained-handler tier/count changes,
native static PoweredItem consumption and the EnergyTransmitterBE transfer
primitive, production recomposition and serialization. Actual Workbench previews,
takes in either order, blocked shift-take and committed/uncommitted close verify
source consumption and pending-inverse independence. Real native smithing recipes
and output takes preserve spent/zero/absent charge across upgrade and tier changes.
Four additional persisted false/absent Step/Negate roots verify live permissions
survive a real process restart without source reimport or unauthorized FE spending.
Configured attribute rejection remains effective, while generic FE `share` cannot
duplicate this dedicated resource. With block B present, it also checks
live powered-attribute depletion independently of FE storage. Native originals
are controls, not substituted formulas.

An actual second server process reloads spent, zero and absent FE from barrels
in the saved world. A hidden/muted NeoForge client independently assembles its
synchronized-rule preview, receives actual integrated-server Workbench outputs
and spent/absent inventory packets, and checks client capability/provenance.
The same client process also captures native normal/controlled-Shift tooltips:
one exact current root FE line, maximum tier capacity, one live root upgrade list,
qualified neutral intrinsic source lore, Shift hints only for retained details
(including native config-disabled Lava Repair), hidden FE/sources, inverse outputs
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
queries, root Step Assist controls/installation, frozen
policies, lifecycle rebuilding, custom display and same-identity ownership.
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

## Just Dire Things root-owned upgrades

This production acceptance gate tests the root-owned installation contract in
[JUST-DIRE-THINGS-ROOT-UPGRADES-EXPLORATION.md](../local-docs/JUST-DIRE-THINGS-ROOT-UPGRADES-EXPLORATION.md),
without replacing production methods. Run sequentially with the other JDT gates:

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireRootUpgrades --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireRootUpgrades "-Pneoforge_version=26.1.2.99" --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireSmithing --console=plain
```

The final command restores the normal loader artifact and verifies the maintained
real-menu root-installation matrix. The gate
reuses the pinned native JAR/hash and disposable `prepareJustDireSmithing`
directory; it does not modify the development client's mods, config or world.
Each child process is bounded to 240 seconds. Versioned process logs/reports are
under `RuntimeTests/NeoForge/build/reports/just-dire-root-upgrades/`.

`mismatchedboots.runtimetest.justDireRootUpgradeTest=true` selects only the test
harness. Block B retired the prototype adapter, its plugin/configuration and
five replacement mixins; their block A evidence remains recoverable from Git.
Lifecycle, smithing, attributes, Jump, FE and Negate are production implementations.
No fixture is packaged in the production mod.

The 12 live scenario groups cover unchanged snapshots/live state, native tier
transformation, config-disabled creation/use, eligible union and dormant retention, actual equipped Step
Assist for powered/non-powered tiers, explicit attribute edits, persistent/
network codecs, cross-tier actual Jump and survival Negate with one root debit.
The added policy case covers conflicting initial switches/parameters, owner
selection, preservation even when equal to the old projection, explicit absence
and native tier defaults instead of historical sliders.

The complete 14-native-recipe/six-ineligible installation matrix, recipe identity,
duplicate/template/config rejection, 28 ordinary/shift-click takes and blocked
extraction live only in `verifyJustDireSmithing`, with versioned logs/reports under
`RuntimeTests/NeoForge/build/reports/just-dire-smithing/<version>/`.
They are not repeated by the root lifecycle gate.

The root gate additionally runs two isolated opt-in failures: missing required
installed-component binding and a transformed native recipe returning an extra
component. Each checks both orientations, repeated rejected preview/take without
source-wise fallback/payment, native assembly controls, preserved stored root
flags/FE/wear/provenance, safe Jump/Negate permissions and exactly one warning.
`JustDireRootUpgradeGuardPlugin` enables its two test-only mixins solely for
these variants; it does not replace ownership or native consumers. Ordinary
startup, absent-JDT and other fixture processes do not apply them.

The other existing gates own independent optional-API/competing-mixin guards,
saved-world restart and client presentation. The resource gate now also saves
root-only Step and false/absent live Step/Negate flags in both orientations,
then verifies a genuinely new JVM cannot resurrect historical upgrades or spend
FE through denied Negate. Ordinary-client conversion acceptance remains separate;
this gate alone does not establish it. Settings UI, powered Jump, other active
abilities and refunds remain deferred. Do not maintain a second ownership implementation.

The conversion's full .114/.99 matrix and ordinary-client handoff are recorded in
[block C](../local-docs/JUST-DIRE-THINGS-ROOT-UPGRADES-EXPLORATION.md#9-block-c--consolidation-and-manual-handoff).
Final-export unit cases must run immediately after a normal
`verifyJustDireNegateFall` process, before another gate resets its shared run directory.

## Retired Just Dire Things active-ability prototypes

Block E retired the executable native/action/UI/input prototypes and their
duplicate packets, listener, screen mixin and task registrations. Their findings
and original commands remain historical evidence in
[the exploration](../local-docs/JUST-DIRE-THINGS-ACTIVE-ABILITIES-EXPLORATION.md).
Use the production gates below; no old spike task is a maintained entry point.
Useful native comparison/construction helpers are retained without a second
mixed quote, payment, timer, screen or permission implementation.

## Just Dire Things Earthquake research spike

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireEarthquakeSpike --console=plain
```

Reuses the pinned JDT artifact, disposable server preparation and existing native
lookup/construction helpers. Fourteen test-only groups compare original Eclipse
boots and a local mixed candidate: per-target FE/Slowness, thresholds/exhaustion,
Unbreaking/exempt modes, live root controls, actual entity/tag/multipart/immune
filters, transactional partial-extraction failure and unchanged provenance.
The original integration-boundary case demonstrated Ground Stomp rejection after
an unrelated Earthquake clock tick. After production B, that maintained probe
expects successful consumed-once activation across legal peer-clock progression;
the dated pre-B limitation remains in the exploration, not a current restriction.
The real server clock covers the complete 200-active/800-cooldown timeline,
unequipped pause, disabled/depleted/removed-upgrade continuation, codec replacement
in each phase and no repeated effect/debit on a late eligible target.

Reports/process logs are under
`RuntimeTests/NeoForge/build/reports/just-dire-earthquake-spike/<version>/`.
Repeat with `"-Pneoforge_version=26.1.2.99"`, then restore normal .114 artifacts.
The candidate remains private to RuntimeTests; normal Earthquake client input is
still gated until C. The spike itself installs no packets, mixins or client listener. This
is feasibility evidence, not production/visual/manual acceptance; policies and
limits belong to [the exploration](../local-docs/JUST-DIRE-THINGS-EARTHQUAKE-EXPLORATION.md).

## Just Dire Things Earthquake production state (block A)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireEarthquakeState --console=plain
```

Uses production quotes/creation/preservation, never the research candidate's
action/ticker. Reuses pinned smithing preparation, Workbench preview/ordinary and
shift-take assertions, native constructors and real tier-smithing helpers.
Eleven live groups cover both orientations, one/two Eclipse sources, longest
total pending lockout with retained phase, deterministic ties/overflow, unrelated
owner timers and independent state. Forged tiers, native config/root controls,
live FE rather than charged history, base eligibility despite discounts/exemptions,
unsupported/duplicate retention and actual tier preview/take are checked.

Missing-API, failing-query and absent-mod variants run sequentially, with bounded
expected diagnostics and intact Ground Stomp/storage/installation and registry-only
presence/absence preservation. The gate rejects errors and absent optional-class
loading warnings. Versioned logs/reports are under
`RuntimeTests/NeoForge/build/reports/just-dire-earthquake-state/<version>/`.
Repeat with `"-Pneoforge_version=26.1.2.99"`, then restore .114 artifacts.
The A gate executes no action/ticking. Its maintained boundary now checks B's
read-only server preparation while normal Earthquake client metadata/input stays
hidden. B's action/ticker acceptance belongs to the separate gate below.
See [the production record](../local-docs/JUST-DIRE-THINGS-EARTHQUAKE-IMPLEMENTATION.md#7-block-a--implementation-and-validation).

## Just Dire Things Earthquake production server (block B)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireEarthquakeServer --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireGroundStompTransport --console=plain
```

Reuses pinned/disposable JDT server preparation and the existing bundled transport
fixture, not the research candidate. Twelve live server groups compare real native
Eclipse effects/FE/feedback against production in both orientations, including
budget/discount/exempt-mode thresholds, actual AABB/ground/tag/multipart and immune
targets, empty activation and two Eclipse inputs. Fault-only mixins count actual
sound/particle calls and inject short debit receipts, equipment/control replacement
and post-commit exceptions; shared reentry, prior committed bills/effects and
immutable provenance are asserted. PartEntity exclusion is delegated to the actual
native query/predicate, not a separate spawned PartEntity effect fixture.

The owned production ticker is invoked once per real server Post for observer
players outside PlayerList, alongside a native original control: full 200/800 phases,
unequipped pause, dormant/disabled/depleted/uninstalled/config-disabled clocks,
no late-target repeated effects and independent Ground Stomp expiry. Paid permits
allow bounded unrelated progression/phase transition/expiry, but reject reset,
insertion, reorder, premature removal, unsupported/unknown edits, resource/control
changes and equal-value server equipment replacement. Original native active order
is proven as Ground Stomp, Earthquake, then supported passive switches. The admitted
subset cannot grow after preparation. Native-menu tokens validate server Earthquake
settings at zero FE; no client UI/metadata is enabled.

Independent API-missing/query-failure/absence processes deny only Earthquake and
retain older routes. Error logs fail the gate; expected unsupported-clock/API/query
diagnostics are counted, not blanket-ignored. Versioned reports/process logs:
`RuntimeTests/NeoForge/build/reports/just-dire-earthquake-server/<version>/`.

The joint transport gate additionally holds a genuine paid-only Ground Stomp reply
while normal Earthquake timer packets recreate the client stack, then feeds it to
the production input listener and verifies one bill/replay rejection. A separate
held free-passive reply also survives actual transactional recharge with Ground
Stomp ready on another key, without acquiring a paid action. The test-only
Earthquake packet driver exercises real CommonNetworking and the actual registered
PlayerList ticker through full lockout, normal FE/clock equipment packets and a real
server-side mob effect. Ordinary mob effect maps are not synchronized to arbitrary
clients; the authoritative effect assertion is separate from the equipment packets.
Normal Earthquake input and any imported shared Earthquake button remain temporarily
gated until C. This is server/wire acceptance, not native Earthquake UI, physical
keyboard/mouse, rendered feedback or restart acceptance. Repeat gates sequentially
with `"-Pneoforge_version=26.1.2.99"`, then restore normal .114 artifacts. See
[the B record](../local-docs/JUST-DIRE-THINGS-EARTHQUAKE-IMPLEMENTATION.md#8-block-b--server-action-clocks-and-shared-permissions).

## Just Dire Things Ground Stomp production state (8.5 block A)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireGroundStompState --console=plain
```

Uses the pinned JDT artifact and disposable server preparation from the smithing
gate. Runs production state/query acceptance plus opt-in missing-API, failing-
query and absent-mod variants sequentially; reports and process logs are under
`RuntimeTests/NeoForge/build/reports/just-dire-ground-stomp-state/<version>/`.
Repeat with `"-Pneoforge_version=26.1.2.99"`, then restore the normal loader.

Actual Workbench previews and normal/shift-click inverse takes check maximum
eligible Ground Stomp cooldown import, independent records and unchanged inputs.
Both orientations cover strongest-usable effective-value quotes, deterministic
tier ties, root-only authorization/controls, native defaults, FE/durability
thresholds, depletion fallback, dormant tiers and config disabling. Real tier
smithing and changed-projection recomposition retain live equal/absent fields,
unrelated records, spent FE and wear through production finalization.

Typed active/negative Ground Stomp timers remain stored and deny queries with
one bounded diagnostic, not conversion/reset. Failure variants deny read-only
quotes without poisoning installation/storage or registry-only preservation;
absence is quiet. This gate implements no action, payment, tick, UI or input
and claims no ordinary-client active-gameplay acceptance. See
[the block A record](../local-docs/JUST-DIRE-THINGS-GROUND-STOMP-IMPLEMENTATION.md#7-block-a--implementation-and-validation).

## Just Dire Things Ground Stomp production server (8.5 block B)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireGroundStompServer :RuntimeTests:NeoForge:verifyJustDireGroundStompTransport --console=plain
```

Run sequentially: the server gate prepares the disposable world needed by the
hidden/muted transport client. Both reuse the pinned smithing artifact. Reports
and process logs are under
`RuntimeTests/NeoForge/build/reports/just-dire-ground-stomp-server/<version>/`.
Repeat with `"-Pneoforge_version=26.1.2.99"`, then restore the normal loader.

The server gate calls production permits/action/edits/timers, not the action
spike. Fifteen live groups cover native effects/AABB/multipart filtering, both
orientations, strongest-usable/tie/fallback selection, one FE or actual durability
bill, enchantments/exemptions/base eligibility, FEET break/reentry, identity/state/
mutable-list changes, consumed/superseded/expired/replayed permissions, actual
native-menu settings/passive edits and supported timer retention, plus all
additional mouse buttons with device/invalid/stale/replay rejection. Native
controls compare shared passive toggle semantics. Supported Step/passive
Jump/Negate bindings exercise free switches even disabled/depleted, shared input
with one active bill, live consumer effects and root-only mode/removal preservation.
Free permits tolerate ordinary wear/charging/ticks but cannot upgrade to a paid
action; paid permits retain strict resource snapshots. Disposable
mixins fault the real FE extraction and post-commit effect path; no production
test switches exist. Four separate JVMs check missing action API, native query
failure, missing settings API and absence, including exact bounded diagnostics
and independent storage/installation readiness; active-only API/query failure
does not disable independent passive bindings.

The original temporary client driver sends production payloads through bundled
CommonNetworking and verifies ordinary stack synchronization, one action bill,
natural worn/paused/disabled/depleted cooldown expiry, replay rejection after
expiry, actual native menu open/close and token-protected root edits. It installs
no native widget bridge and grants no physical/manual acceptance. Earthquake B
extends this same gate with a delayed production GS input flight, Earthquake
packet-driven action and its automatic two-phase clock; see
[the joint B coverage](#just-dire-things-earthquake-production-server-block-b).
The
retired prototypes no longer register listeners, packets or screen mixins.
Block E reuses the production client gate for restart/reconnect. Three focused
codec units cover wire identity/preferences, trailing data and bounded strings.
See [the block B record](../local-docs/JUST-DIRE-THINGS-GROUND-STOMP-IMPLEMENTATION.md#8-block-b--server-action-timers-and-transport).

## Just Dire Things production native screen/input (8.5 block C)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireGroundStompClient --console=plain
```

Requires the disposable server world prepared by the B server gate. Six
sequential hidden/muted client JVMs cover live production, high/low compatible
native getter wrappers, lost required callbacks, missing settings API and quiet
absence. Native widgets/capture use production callbacks and transport; no
prototype listener or replacement reply handler is installed. Both mixed
orientations cover supported controls and per-ability ranges, rapid edits,
global Toggle Tool (capture takes priority), particles, keyboard/mouse assignment,
reopen and Escape removal, disabled mode/inventory controls, GUI/release/repeat/wrong-input
and imported unsupported preference denial, fallback/recharge and one action
bill, unchanged sources, tool switches and selected-slot removal. D.1 adds
production mouse-event flights, left/right/out-of-range capture/input rejection,
native inventory mouse controls and device identity in activation permits. Originals,
empty-hand menu and internal-slot refresh remain native controls.
During mixed capture, ordinary left/right tool-slot clicks retain native
selection/capture cancellation without changing either boot's bindings; invalid
additional buttons remain rejected.
D.2 adds native passive keyboard/mouse capture, independent and shared toggles,
global-off/depleted eligibility, live equipped Step attributes, shared Ground
Stomp/cooldown behavior, reopened preferences and per-ability Escape removal.

Reports/process logs and the live final screen export are retained under
`RuntimeTests/NeoForge/build/reports/just-dire-ground-stomp-client/<version>/`.
Repeat with `"-Pneoforge_version=26.1.2.99"`, then restore the normal loader.
The final-export unit uses
`-PjustDireSettingsTransformedClass=RuntimeTests/NeoForge/build/reports/just-dire-ground-stomp-client/<version>/ToolSettingScreen.class`;
native entry units use the existing `justDireGuardJar` opt-in.
Synthetic event-bus input is not a physical/manual acceptance. D.1 mouse input
and D.2 passive-toggle bindings are implemented. Block E owns consolidated restart/
reconnect and the final ordinary-client handoff; see
[the C record](../local-docs/JUST-DIRE-THINGS-GROUND-STOMP-IMPLEMENTATION.md#9-block-c--native-settings-and-equipped-keyboard).

## Just Dire Things Ground Stomp production lifecycle (8.5 E)

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireGroundStompLifecycle --console=plain
```

Requires the disposable server-world prerequisite from the production server gate.
Two hidden/muted client JVMs reuse one saved world: Save prepares it once; Restart
opens that exact world without copying/resetting it. Both modes use the existing
production native-screen/client fixture, not an alternate lifecycle screen or
action driver. Reports/process logs remain under
`RuntimeTests/NeoForge/build/reports/just-dire-ground-stomp-lifecycle/<version>/`.
Repeat with `"-Pneoforge_version=26.1.2.99"`, then restore the normal loader.

Native widgets configure both Celestigem + Blazegold orientations, live sliders,
particles, keyboard Ground Stomp, shared Step/passive Jump mouse bindings and
Negate binding removal/explicit disabled mode. Production input spends FE and
moves an actual non-hostile world target; spent roots, wear and positive paused
timers are stored in a barrel. The second JVM compares whole disk stacks,
performs real Celestigem-to-Eclipse preview and normal/shift-click take, verifies
preserved controls/resources/source history and single template/addition/base
consumption, then exercises shared passive input during cooldown, natural expiry
and fresh keyboard/mouse action.

The fixture only observes production replies or defers a genuine permit; it
does not implement validation, payment, effects or ticking. Old consumed and
unconsumed packets reject through real CommonNetworking before/after expiry.
An actual disconnect and same-world rejoin in the second JVM verifies new
server/player identity, cleared production client intent/selection, persistent
FE/controls, rejection of an old free permit and fresh native selection/input.
Server player-replacement/logout cases remain in the existing server matrix.
The lifecycle case also defers a genuine free-input reply across an actual timer
slot packet that recreates the client stack. Production pending intent must
survive unchanged filtered settings; equal server equipment replacement still
rejects. Action-only client intent and all paid server checks stay strict.

Equal/absent changed-projection and unrelated record preservation remain owned
by the existing production state gate, not a duplicated lifecycle matrix.
Synthetic input and automated disk checks do not establish final manual
acceptance. Exact executed evidence and the final checklist belong to
[block E](../local-docs/JUST-DIRE-THINGS-GROUND-STOMP-IMPLEMENTATION.md#12-block-e--consolidation-lifecycle-and-final-handoff).

## Vanilla armor classification

```powershell
.\gradlew.bat verifyLoaderServerStartup --console=plain
```

The existing self-terminating Fabric/NeoForge server gates now also run shared
production armor regressions without JDT or a candidate datapack. Actual equipped
Unbreaking wear is compared in both orientations against armor and non-armor
controls; enchantments and source snapshots remain unchanged. Vanilla table
candidate selection covers general armor and primary restrictions, preserving
NeoForge's existing source-support hook rather than asserting identical
foot-enchantment candidates across loaders; it is not a rendered table screen.
Real survival anvils preview/take books, pay levels and
consume inputs only for accepted enchantments: one/two-source support, source
rejection despite native root-tag admission, bare roots, vanilla
conflicts and ordinary-item controls. The full Fabric gameplay gate separately
retains its existing donor, cost, level and workstation regressions.

Select one loader with `-PserverStartupLoader=fabric` or `neoforge`; repeat
NeoForge with `"-Pneoforge_version=26.1.2.99"` and restore the default afterward.
`vanilla-armor-classification-report.txt` is written alongside the existing
startup/network markers under `build/network-acceptance/`. The temporary
baseline/candidate probe/datapack were retired; historical evidence remains in
[the bounded record](../local-docs/VANILLA-ARMOR-TAG-EXPLORATION.md).
