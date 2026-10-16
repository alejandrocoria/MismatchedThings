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

The maintained Lava Repair gates and retired research boundary are documented under
[Just Dire Things Lava Repair production](#just-dire-things-lava-repair-production).

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
are controls. The maintained server has 122 non-powered/powered cases, including
native 0/4/5-FE thresholds, strongest usable tier, independent passive fallback,
root controls, actual fall distance/health and no Jump payment. Its twelve direct
client checks are followed by twenty actual keyboard flights: native ordinary/
Ferricore/Blazegold/Celestigem/Eclipse height controls, Eclipse + Ferricore in both
orientations, Celestigem + vanilla, and Eclipse Negate + Ferricore coexistence.
Charged/depleted/recharged FE changes
in place on the equipped server root; normal equipment/attribute packets must
agree before each flight. The same mixed root must follow the currently usable
native tier height without mutating its stack during ascent. These normal gates
use the production resolver/archive, never the powered research-copy path.
Powered Jump native settings/input are implemented by block B of
[the production plan](../local-docs/JUST-DIRE-THINGS-POWERED-JUMP-IMPLEMENTATION.md).
The small guard gate separately exercises compatible competing
WrapOperations, a replaced fall read disabling both hooks, and quiet optional
absence (9/9/2 movement/fall cases, including powered native/mixed controls).
Smithing retains its independent recipe/menu acceptance.
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
variant. The API checks include powered wallet read-only dependencies, rejecting
foreign wallet reads or payments. The design, exclusions and manual handoff are maintained in
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

The executable candidate, listener, run and verification task were retired during
Earthquake D consolidation on **2026-10-13**. Its fourteen passing research groups
remain dated evidence in [the exploration](../local-docs/JUST-DIRE-THINGS-EARTHQUAKE-EXPLORATION.md),
not a maintained task or production acceptance. Per-target native controls,
payment/failure boundaries, two-phase clocks and paid coexistence are covered by
the production state/server/client/transport gates below; actual persistence and
tier/root-upgrade transactions belong to the joint lifecycle gate. Native
constructors/controls remain shared test helpers, without another mixed action.

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
read-only server preparation and C's supported metadata/input boundary, without
executing either. B's action/ticker acceptance belongs to the separate gate below.
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
settings at zero FE; this server fixture itself does not exercise native widgets.

Independent API-missing/query-failure/absence processes deny only Earthquake and
retain older routes. Error logs fail the gate; expected unsupported-clock/API/query
diagnostics are counted, not blanket-ignored. Versioned reports/process logs:
`RuntimeTests/NeoForge/build/reports/just-dire-earthquake-server/<version>/`.

The joint transport gate additionally holds a genuine paid-only Ground Stomp reply
while normal Earthquake timer packets recreate the client stack, then feeds it to
the production input listener and verifies one bill/replay rejection. A separate
held free-passive reply also survives actual transactional recharge with Ground
Stomp ready on another key, without acquiring a paid action. After C, the same
fixture uses production equipped Earthquake input through real CommonNetworking and the actual registered
PlayerList ticker through full lockout, normal FE/clock equipment packets and a real
server-side mob effect. Ordinary mob effect maps are not synchronized to arbitrary
clients; the authoritative effect assertion is separate from the equipment packets.
The transitional shared-button client gate is removed by C. This transport gate
is server/wire acceptance, not native Earthquake UI, physical
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
action and its automatic two-phase clock; C promotes its EQ input to the production
listener, rather than a test-only prepare/activate driver. See
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

Requires the disposable server world prepared by the B server gate. Eight
sequential hidden/muted client JVMs cover live production, high/low compatible
native getter wrappers, lost required callbacks, missing core settings API,
independently missing Earthquake API, unsupported Earthquake settings and quiet
absence. Native widgets/capture use production callbacks and transport; no
prototype gameplay listener is installed. A transparent reply observer defers
selected genuine paid replies, then feeds them back into production validation.
Both mixed
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

Powered Jump B extends the same gate/profile, not a new screen or packet.
The live context adds six Celestigem/vanilla, Eclipse/vanilla and Eclipse/Ferricore
primary/inverse configurations: permanent 3/5 slider ranges at zero FE/off,
native keyboard/mouse capture and free equipped toggles, in-place 4/5/0-FE packets,
unchanged widget/value/bindings, current root tooltip and actual client Jump.
High/low contexts retain joint controls with Eclipse's permanent Jump maximum;
lost UI/API contexts additionally check powered Jump's independent operation.
The companion server gate adds six native-menu edit/range/uninstalled/movement/
fall-health groups (21 live groups total). The bundled transport gate shares
Step/powered Jump's free reply across charging/peer clocks with no extra bill.
Exact per-loader validation and the focused manual checklist accepted by the
owner on 2026-10-14 belong to
[the powered plan](../local-docs/JUST-DIRE-THINGS-POWERED-JUMP-IMPLEMENTATION.md#8-block-b--native-configurationinput-implementation-and-validation).

Earthquake C extends this existing gate with fixed-range/no-slider controls,
zero-FE editing, particles, install/tier exclusion, native capture cancellation,
distinct/shared keyboard and mouse flights in both orientations, one root bill
and real authoritative Slowness. Timer packets retain widget/capture identity and
the live selected root. Held paid EQ replies survive legal Ground Stomp progression
but reject recharge, GUI opening and actual equal-value server root replacement;
a real disconnect/rejoin clears paid intent and rejects the old nonce. Independent
EQ failures leave older settings/action intact; unsupported GUI metadata does not
freeze an already spent EQ clock. Assertions run after production Post callbacks.
Native originals keep their own route. These cases are not Earthquake active/
inactive restart, rendered feedback or physical-input acceptance; D owns those.

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
Earthquake's current evidence and focused manual retry (owner-confirmed on
2026-10-13) are in
[its separate C record](../local-docs/JUST-DIRE-THINGS-EARTHQUAKE-IMPLEMENTATION.md#9-block-c--native-screen-and-equipped-input).

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
particles, keyboard Ground Stomp, shared Step/Jump mouse bindings and
Negate binding removal/explicit disabled mode. Production input spends FE and
moves an actual non-hostile world target; spent roots, wear and positive paused
timers are stored in a barrel. The second JVM compares whole disk stacks,
performs real Celestigem-to-Eclipse preview and normal/shift-click take, verifies
preserved controls/resources/source history and single template/addition/base
consumption, then exercises shared passive input during cooldown, natural expiry
and fresh keyboard/mouse action.

Armor-wear C extends the same first two roots, not a new restart suite. Funded and
unfunded actual survival hits arrive through ordinary in-place equipment/slot
packets before save (5000 -> 4800 -> 100 FE, prior wear 31 -> 33). Four FE and
combat wear survive the second JVM; another real hit spends no FE and adds two
wear points instead of consulting historical source charge. Both native
Celestigem-to-Eclipse and Blazegold-to-Celestigem previews/normal and shift takes
preserve the live state. The latter changes one provider to two: four FE then
zero FE suppress further ordinary wear. Actual native-donor recharge restores
powered availability without repairing wear, while XP Mending repairs durability
without refilling the wallet. The pre-existing controls/HUD/Jump/EQ/reconnect
matrix remains in this same driver.

Powered Jump C extends these same four roots/two JVMs, not a separate lifecycle
suite. Celestigem/Blazegold sources deliberately retain a different Jump value;
the actual spent wallet is depleted to four FE before save. Reload, inventory pause
and actual Celestigem-to-Eclipse preview/take preserve that balance, wear, root
installation/value/bindings and history. Real client movement and isolated server
movement/fall-health checks observe Blazegold fallback, recharge, disabled Jump,
and the new permanent maximum five through native widgets and ordinary packets.
Eclipse/Celestigem roots retain explicit false installation or absent scalar/toggle/
binding mode: tier smithing must not recreate source preferences. The existing
two reconnects additionally retain depleted pure-powered Jump and an edited mouse
binding; toggling off, recharging and toggling on must yield zero/zero/five without
a bill or historical resource recovery. Server Jump checks disable Negate only on
a defensive test copy to isolate its fall companion; the actual equipped root is
unchanged. Hidden/synthetic checks do not grant the final owner acceptance in
[the powered plan](../local-docs/JUST-DIRE-THINGS-POWERED-JUMP-IMPLEMENTATION.md#5-block-c--lifecycle-consolidation-and-manual-handoff).
The final maintained runtime/package matrix and focused shape units passed on
.114/.99 on 2026-10-14; normal .114 artifacts/units are restored. Exact execution,
log audit and the owner's final five-point manual acceptance on 2026-10-14 belong to
[C's record](../local-docs/JUST-DIRE-THINGS-POWERED-JUMP-IMPLEMENTATION.md#9-block-c--production-lifecycle-and-consolidation).
That separate owner confirmation accepts C and completes the bounded powered-Jump
A/B/C plan; synthetic/hidden tests alone do not establish it or a manual loader matrix.

Earthquake D extends these same two JVMs with Eclipse + Celestigem in both
orientations, one saved active EQ phase and one inactive phase, spent FE/wear,
particles, key/mouse bindings and a pending Ground Stomp clock. Removed EQ
installation/global-off and a disabled EQ toggle remain stored; real tier preview/
normal and shift takes do not restore historical state. A real optional-template
root reinstallation preserves the spent lockout/resources. Worn-only automatic
phase progression/expiry permits one fresh EQ action even after both sources
become Eclipse; a later grounded mob receives no repeated effect before that
fresh input. Saved paid consumed EQ and unconsumed passive packets reject in the
new JVM. The existing reconnect then uses the active EQ root, preserving a
bounded remaining lockout, FE/controls and rejecting old authorization.

Real tooltips verify one current root FE line/capacity and one EQ inventory entry,
without showing its unused numeric field. The resource-client gate independently
checks that presentation in normal/advanced, Shift/non-Shift and both orientations.
No new source section, support disclaimer or lifecycle gameplay adapter is added.

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
acceptance. Ground Stomp's original acceptance belongs to
[block E](../local-docs/JUST-DIRE-THINGS-GROUND-STOMP-IMPLEMENTATION.md#12-block-e--consolidation-lifecycle-and-final-handoff);
the Earthquake extension's executed evidence and owner-accepted final checklist belong to
[block D](../local-docs/JUST-DIRE-THINGS-EARTHQUAKE-IMPLEMENTATION.md#10-block-d--lifecycle-consolidation-and-final-handoff).

Cooldown-feedback C extends these same Save/Restart processes with observation
of native draws and committed completion sounds. Exact disk/tier counters reach
the HUD through ordinary equipment packets; inventory pause retains the complete
root and emits nothing. Tier/root-upgrade preview/take is silent; natural worn
GS/EQ boundaries each emit once and remove/advance their indicators. Both actual
rejoins retain the spent-clock HUD; the final paid EQ finishes once afterward,
without a load-only cue. Observer-only helpers never replace native rendering,
production ticking or audio. Its separate final manual acceptance belongs to
[feedback C](../local-docs/JUST-DIRE-THINGS-COOLDOWN-FEEDBACK-IMPLEMENTATION.md#9-block-c--production-lifecycle-and-consolidation).

## Just Dire Things native cooldown HUD production

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireCooldownHud verifyPackagedDependencies --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireCooldownHud verifyPackagedDependencies "-Pneoforge_version=26.1.2.99" --console=plain
```

Six isolated client JVMs use the **production** HUD bridge, never the research
view or completion-emission wrappers: live, compatible high/low equipment-read
wrappers, discarded bridge output, unavailable Earthquake-only HUD parameters
and quiet absence. Native blit/world-sound observers do not cancel or emit.
The parameter fault retains GS icons and native settings/server clock progression.
The same maintained gate also exercises production completion sounds around
automatic worn ticks: native controls, both mixed orientations/EQ phases,
accepted simultaneous-expiry order, disabled/uninstalled/depleted continuation
and unequipped pause. At-emission snapshots prove root publication precedes audio;
FE, wear and source history remain unchanged, without repeated activation sounds.
The live JVM additionally checks zero/negative/duplicate/foreign/unsupported
records and caught post-commit world-audio faults without clock reset or retry.
Its two unsupported-timer warnings are expected, once each; the other contexts
remain quiet except the deliberate HUD/parameter failures.
Both mixed orientations/phases preserve native cross-slot order/progress; invalid
display records retain stored evidence, and ordinary packets expose actual live
root state/FE. F1/no-player/config offsets retain native behavior.

Versioned reports/logs and live/high/low final class exports are under
`RuntimeTests/NeoForge/build/reports/just-dire-cooldown-hud/<version>/`.
`justDireCooldownHudTransformedClass` selects the live export for focused NeoForge
unit characterization, including the adjacent high/low exports. These tests do
not claim physical/audible acceptance. Real saved-world restart, tier/root-upgrade
preview/take and reconnect presentation are consolidated into the existing
`verifyJustDireGroundStompLifecycle` gate, rather than duplicating codec-only
loading here. Its four spent roots retain native GS/EQ indicators through ordinary
packets and inventory pause; load/preview/take cannot emit a completion cue, while
subsequent worn boundaries emit exactly once. Focused owner visual/audible checks
and the final combined checklist are recorded separately in
[the feedback plan](../local-docs/JUST-DIRE-THINGS-COOLDOWN-FEEDBACK-IMPLEMENTATION.md).

## Just Dire Things cooldown HUD / sound research spike

The executable spike, candidate view/emission mixins and six research run
configurations are retired. Native draw/world-sound observers are retained in
production acceptance only; there is no second feedback implementation.
The [dated bounded record](../local-docs/JUST-DIRE-THINGS-COOLDOWN-FEEDBACK-EXPLORATION.md#8-test-only-native-hud--owned-clock-sound-spike)
retains its historical commands/evidence and accepted same-tick sound-order
distinction. Use the maintained HUD and lifecycle gates above for current proof.

## Just Dire Things powered Jump research spike

The executable resolver, JAR-copy patcher/driver, opt-in flags, preparation/
verifier tasks and four research run configurations are retired by production C.
Useful assertions now belong to the maintained Jump, native settings/input and
real lifecycle gates above; no alternate resolver or duplicated physics suite
remains. The [dated exploration](../local-docs/JUST-DIRE-THINGS-POWERED-JUMP-EXPLORATION.md)
retains historical commands/results and the research-copy bootstrap distinction.
Use [the production plan](../local-docs/JUST-DIRE-THINGS-POWERED-JUMP-IMPLEMENTATION.md)
for current automated evidence and the separate final ordinary-client checklist.

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

## Just Dire Things Lava Repair production

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireLavaRepair --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireLavaRepairGuards --console=plain
```

Repeat each command with `"-Pneoforge_version=26.1.2.99"`, then restore the default
.114 artifacts. The production gate uses the normal packaged mod, no research
item-hook override or candidate call. Its 22 server groups retain native/mixed
damage, consumption, live FE/history, transient cancellation and actual effect
observations; it adds creation/recomposition sanitization and false/absent Lava
preference preservation. Independent component protection remains effective beside
the extra fire exception. A refused obsidian write awards no repair or sound.

The subsequent hidden/muted client uses actual spawned native/mixed ItemEntities,
ordinary particle packets and JDT's registered particle factory, not fixture-made
particles. It observes levitation, source consumption, native visual metadata and
completion audio emission, plus the repaired mixed stack arriving by ordinary
entity data synchronization. This proves delivery/factory use, not subjective
visual/audibility acceptance or the later native settings/tooltip scope.
After the owner's presentation follow-up, the client runs damaged and healthy
native/mixed cases. It retains `productionClient-trajectory.csv` and requires all
eight server/client ascent series to avoid material downward steps/velocities,
not merely reach a higher point. This rejects the diagnosed gravity/correction
oscillation. Client-only prediction controls additionally cover recovery,
cancellation, timeout, stale coordinates and no root/block mutation; see
[the correction](../local-docs/JUST-DIRE-THINGS-LAVA-REPAIR-IMPLEMENTATION.md#86-accepted-healthy-ritual-and-client-motion-correction--2026-10-14).
Healthy roots now consume source lava in the native-style ritual; switched-off
roots remain fireproof without consuming it. Native particles/rendering stay reused.

The guard gate starts five isolated servers: absent JDT, missing gameplay API,
throwing read-only capability query, missing particle API and throwing particle
construction. Optional failures log once; visual failures do not remove item fire
resistance or repair, and unrelated upgrades remain available. Root preference
preservation is registry-only even after optional failure.

Both gates reuse the pinned JDT artifact and disposable preparation. Reports/logs
are retained under `RuntimeTests/NeoForge/build/reports/just-dire-lava-repair/<version>/`,
with `production`, `productionClient` and per-guard files. The real lifecycle gate
below complements the focused explicit-tick scenarios; it does not replace them.

Block B's `:RuntimeTests:NeoForge:verifyJustDireLavaRepairSettings` reuses the native
settings client harness with focused `Lava`/`LavaSettingsFailure` variants. It
checks actual original widgets/selection/capture and production packets, one/two
providers in both orientations without installations, root absent/false switches,
equipped keyboard/additional-mouse toggles, native shared-passive order, unsupported
edit rejection, dormant/restored eligibility and configured actual dropped-item
ticks. The injected unsupported numeric metadata disables only Lava settings;
older widgets/edits and Lava gameplay/root lore survive. Reports are `Lava*` in
the same versioned Lava directory. A third process reuses the existing common
settings-API fault (`CommonSettingsMissing` reports), requiring one common warning
and no unnecessary Lava binding attempt/diagnostic after the shared API fails.
Preparation clears this harness's old markers
before each process; stale results cannot veto or satisfy the current run.

The ordinary `verifyJustDireResources` client matrix now asserts one intrinsic
root Lava line in normal/advanced and Shift/non-Shift modes, current root controls,
hidden provenance/root display restrictions, native config exclusion, unchanged
five-upgrade inventory and no source Lava duplicate/qualifier/empty Shift hint.
These replace the earlier source-Lava expectations. Synthetic input and tooltip
structure are not physical/manual screen readability acceptance. The owner
separately confirmed B's focused controls/bindings/tooltip checklist on 2026-10-14,
closing [section 9.3](../local-docs/JUST-DIRE-THINGS-LAVA-REPAIR-IMPLEMENTATION.md#93-focused-owner-checklist--accepted).

Block C adds a scheduler-owned entity gate:

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireLavaLifecycle --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireGroundStompLifecycle --console=plain
```

Repeat on .99 as above. `verifyJustDireLavaLifecycle` runs two dedicated-server
JVMs against the same disposable world, with no reset between Save and Restart.
Only the real server scheduler ticks its spawned entities. It covers pinned
adjacent/deep sources, removal/new contact, native pickup delay/playerTouch/drop,
ordinary age/despawn, legal vanilla merging beside nonstackable mixed controls,
actual chunk unload/reload and cross-dimension entity replacement. Actual saved
entity UUIDs and full independent stack expectations are reopened in the second
JVM; stale native working fields grant no repair credit and completed roots do
not replay a receipt. Reports are `lifecycleSave`/`lifecycleRestart` beside the
other versioned Lava reports. Despawn age is advanced only to exercise the native
boundary without waiting five minutes; no fixture manually ticks an entity.

Armor-wear C extends the first scheduled episode with an actual funded survival
hit before the drop: the root spends 200 FE, then Lava Repair repairs its prior
wear without refunding that wallet debit. The full-stack comparison permits
only the expected durability change, not a reset of controls or source history.

The existing native-menu lifecycle gate additionally saves explicit false and
absent Lava preferences, native keyboard bindings and unrelated spent resources/
clocks. After restart it uses actual source-wise tier preview/take in both
orientations, ordinary equipment packets and native widgets/tooltips to check
dormant eligibility. Provider restoration uses production recomposition with the
retained source snapshots and current live controls/clocks/resources, not a
synthetic native downgrade recipe or an older root-state rollback. The older GS/EQ/Jump lifecycle coverage
remains in that one harness, rather than a second copy of its matrix.

The owner confirmed all final manual tests on 2026-10-14, accepting C and closing
the bounded Lava Repair A/B/C plan. This separate manual evidence is recorded in
[section 10.3](../local-docs/JUST-DIRE-THINGS-LAVA-REPAIR-IMPLEMENTATION.md#103-owner-handoff--accepted);
it is not inferred from synthetic input or the automated .114/.99 matrix.

## Just Dire Things Lava Repair research spike

The executable candidate, native-reuse driver, research flag/runs/plugin and
item-hook mixin were retired in production block C. The narrowly gated refused-
world-write fault remains only in the maintained production server gate, with
no candidate dependency. No old spike task is a maintained entry point.
The dated 23-group-per-version proof and its limits remain historical evidence in
[the exploration](../local-docs/JUST-DIRE-THINGS-LAVA-REPAIR-EXPLORATION.md#8-executable-server-spike--2026-10-14),
not a substitute for production or manual acceptance.

## Just Dire Things armor wear / FE production

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireArmorWear "-Pneoforge_version=26.1.2.99" --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireArmorWear --console=plain
```

Run sequentially and restore default .114 afterward. The maintained gate uses the
normal packaged production JAR, real root capability/transactions and pinned JDT
in disposable servers. No candidate adapter is enabled; no readiness property or
alternate FE provider is used. The final owned production getter must be true in live/
transaction/binding-failure contexts and false/unavailable for unsafe flow/absence.

The same maintained gate covers A's Live/Missing/Absent and B's isolated faults. Select a
bounded subset with, for example, `"-ParmorWearVariants=Live"`. Live records 77
production observations: actual NORMAL-difficulty player hits/equipment ticks,
both powered tiers/orientations, full/partial/empty one/two-provider branches,
Unbreaking/zero bill, late cancellation/effective fractional modifiers, mode and
item immunity, actual breakage, protected-entry revival and unchanged provenance.
Direct wear, actual XP Mending, recomposition and existing GS ordinary/powered,
Negate and free Jump consumers remain separate. Observations are not JUnit tests
or manual gameplay cases.
The consolidated native controls include both tiers' empty/partial/full budgets
and direct two-FE/Unbreakable/mode behavior; native direct wear must not gain the
armor-event x100 rate.

The additional variants are:

- WrapperBefore/WrapperAfter: actual MixinExtras single-forwarding wrappers at
  priorities 1400/600, an unrelated method and a surrounding callback. Both nesting
  orders retain one funded bill; counters prove native/ordinary delegation really
  traverses the competing wrapper. Late cancellation/fractional edits still win.
- Amount/Entry/Slot/Entity/Cancellation/Association/Router/Publication: real later
  transformations alter distinct flow/publication invariants. Missing removes the
  actual target earlier. Both provider counts/orientations retain ordinary wear,
  unchanged FE and one wear-flow diagnostic without a hard injector failure.
  Safe storage, actual live armor changes, Jump/Negate/GS and intrinsic Lava
  eligibility/protection remain independent.
- Transactions: seven real staged-extraction/commit faults in both orientations:
  short receipt, extra staged debit, reentry, equipment replacement, independent
  wallet/component edits and an exception immediately after commit. Aborts roll
  back staging without overwriting foreign edits; stale roots/replacements are not
  damaged/retried. Every case is followed by a valid request on the same player.
  A post-commit exception propagates with exactly one debit and no fallback/refund.
- QueryFailure/StorageMissing: a wear-only read failure and the existing failed
  native storage binding, respectively. Repeated hits retain ordinary wear with
  one scoped query/storage diagnostic, not another wear-flow warning.
- Absent: JDT omitted, ordinary wear and quiet optional linking.
- Abilities: the maintained full production Earthquake/shared-GS server matrix,
  including actual effects/payments/modes/clocks, with an actual funded equipped
  wear-conversion witness in that same process. No prototype consumer is loaded.

Forwarder acceptance is deliberately bounded to one straight-line wrapper with
one unchanged four-argument Operation call, inside or outside the owned router.
It is not a universal wrapper analyzer or a promise to execute callbacks nested
inside an ordinary operation suppressed by funded conversion. Unknown relevant
flows refuse wear conversion only. On refusal the owned router is also neutralized:
an adulterated constant-true argument cannot bypass the false getter.

Reports/logs are retained under
`RuntimeTests/NeoForge/build/reports/just-dire-armor-wear-production/<version>/`.
Live and both wrappers retain actual final `CommonHooks.class` exports for focused
preflight/final-publication guard tests:

```powershell
.\gradlew.bat :NeoForge:test --tests "*JustDireArmorWearShapeTest" "-PjustDireArmorWearTransformedClass=RuntimeTests/NeoForge/build/reports/just-dire-armor-wear-production/26.1.2.114/CommonHooks.class" --console=plain
```

Supply the matching .99 export and loader property for minimum-version validation.
Run Live and both Wrapper variants before this command. Without the export property,
four final-shape tests are explicitly skipped; such
a run does not establish actual transformed-class acceptance. The existing
attribute gate's player-hit expectation now follows funded wear conversion rather
than the superseded ordinary-wear baseline; attribute functionality and prices
remain owned by their existing adapters.

The [production plan](../local-docs/JUST-DIRE-THINGS-ARMOR-WEAR-IMPLEMENTATION.md)
owns the separate A/B evidence, C's consolidated lifecycle record and accepted
ordinary-client checklist. The joint native-menu lifecycle and Lava lifecycle
commands above own real restart/packet/repair evidence, not these isolated faults.
Final C regression passed on .99 and restored .114, including 11 focal shape
units/version, both loader builds and packaged-dependency verification. The
plan's section 10 records exact commands, scoped retry, warnings and exercised/
skipped/cache boundaries. The owner separately confirmed all final ordinary-client
checks on 2026-10-15 (section 10.3), accepting C and completing the bounded
armor-wear/FE A/B/C plan.

### Conserved upgrade credits and actual-break refunds — production

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireRefunds --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireResources --console=plain
.\gradlew.bat :NeoForge:test --tests '*JustDireRefund*Test' "-PjustDireRefundTransformedClass=RuntimeTests/NeoForge/build/reports/just-dire-refunds-production/26.1.2.114/Live-ItemStack.class" --console=plain
```

Repeat on .99 and restore default .114. This maintained production gate uses the
normal NeoForge JAR and `mismatchedboots:jdt_refundable_upgrades`, not the spike's
component, lifecycle helpers or mixins. A stores conserved entitlement; B now
returns its items only after guarded actual equipped-player durability breakage.
No tooltip changes are made. For .99, also change the export path and supply
`"-Pneoforge_version=26.1.2.99"` to both commands.

Live checks actual Workbench pair transactions in both orders (single/shared/
disjoint/no purchases), repeated previews, blocked takes and inverse shift-click;
all five paid root upgrade takes; inherited duplicate rejection; native tier
smithing, dormant/empty/absent preservation, disabled-config creation, projection
override and complete stack persistent/network codecs with invalid credit
decoders. Network codecs are not actual packet/reconnect acceptance. ItemsMissing/
MetadataMissing break only the production refund binding, requiring one scoped diagnostic
and working creation/paid installation/GS quote/preservation. Absent omits JDT,
requiring quiet linking, own component registration and registry-only preservation.
Live additionally observes actual combat/direct/adventure/GS drops, inverse
conservation, cancelled/Unbreaking survival and invalid-context refusal,
activity-independent credits, existing powered-wear behavior and independent
vanilla/native controls. Binding failures still break normally without refunds.

Inner/Outer use real forwarding mixins at both chain placements. Inner includes
late equipment/credit/native-list edits, count restoration, before-consumption
reentry and foreign exceptions, with local-guard recovery. Missing/Extra/Count/
Entity/Operation/Publication/Router each require scoped final refusal, actual
ordinary consumption and its original callback, without speculative items.
Extra proves multiple injection targets do not hard-fail startup. Absent is quiet.
Use `"-PrefundVariants=Inner,Extra"` for a focused subset; default runs all 13.
The full matrix runs only once per version; fault JVMs retain focused controls.

Reports are `build/reports/just-dire-refunds-production/<version>/`, with separate
variant logs/reports and actual exported ItemStack classes. Six focal shape tests
use Live/Inner/Outer exports, alongside A's three credit units; supply the property
above to avoid skipping export-dependent checks.

The existing resources gate also saves six production credited/empty/absent roots
from actual Workbench and paid upgrade/tier smithing takes. It checks ordinary
wear, actual Mending, off controls, unequip, recharge and recomposition without
early refunds. A second JVM opens the saved barrel, compares independent whole
roots and observes exact drops from six genuine equipped breaks. A later paid
Jump credit is absent from both historical sources. Extra refund save/restart
reports are under `build/reports/just-dire-resources/<version>/`. The hidden/muted
client checks actual open Workbench menu packets with unequal inverse allocation,
actual smithing base/result packets and a paid Ground Stomp result equipped through
ordinary synchronization. Existing recharge/depletion packets also retain credits.
These are not codec-only assertions and do not add custom transport or tooltips.
The maintained native-menu lifecycle gate above also snapshots positive credits
before actual widget/input changes and checks exact credit preservation through
its existing tier/recharge comparisons. Its manual Earthquake-removal fixture
discards that purchase credit before a real paid reinstall, which must add it
exactly once; retaining the removed credit would instead be a duplicate purchase.
The owner separately confirmed all final ordinary-client checks on 2026-10-16,
accepting C and completing the bounded upgrade-refund A/B/C plan; this is not
inferred from these automated gates. See sections 6.1/10.3 of
[the production plan](../local-docs/JUST-DIRE-THINGS-UPGRADE-REFUNDS-IMPLEMENTATION.md).

### Dynamic JDT FE/durability item bars — production block A

```powershell
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireItemBars "-Pneoforge_version=26.1.2.99" --console=plain
.\gradlew.bat :RuntimeTests:NeoForge:verifyJustDireItemBars --console=plain
```

The [A/B plan](../local-docs/JUST-DIRE-THINGS-ITEM-BARS-IMPLEMENTATION.md) owns
production scope and remaining visual/manual gates. Tests use the normal production
JAR and its actual registration/decorator, never a candidate decorator or second
capability. Cyan root FE is at y+10 when the unchanged vanilla bar is visible,
otherwise y+13; full FE hides and zero retains its empty backing.

The task starts a dedicated server and checks that neither the production client
decorator nor client test class was loaded. It checks production archive isolation,
then hidden/muted Live, Absent, StorageMissing, QueryFailure, InvalidWidth and
LinkageFailure JVMs. StorageMissing reuses the existing binding failure witness.
The last three arm one native Celestigem visibility/width fault on mixed arguments
only, proving one warning, no partial FE geometry and a presentation-only latch
with a committed 100 FE debit and intact audited metadata/native original control.
No production test seam, mixin or failure property is introduced.

Live/StorageMissing cover both tiers/orientations, one/two powered sources, maximum
root capacity, healthy/damaged/Unbreakable roots, zero/tiny/partial/full/missing FE,
invalid count/data/charge, forged passive FE and vanilla/native controls. Same-root
damage/repair/Unbreakable transitions inspect actual `itemDecorations` deferred
rectangle bounds/colors and whole-stack preservation. Ten ordinary inventory slot
packets update charge, damage/repair and Unbreakable with unchanged provenance,
including full/zero visibility, without a custom rendering payload or cache.

Reports/logs are under `build/reports/just-dire-item-bars-production/<version>/`;
worlds/mods/config are disposable in `build/run/justDireItemBarsProduction/<version>/`,
never the owner's indev run. The pinned JDT artifact is hash-checked. Individual
client runs need the verifier's server-prepared world. Block A checks production
deferred extraction, **not GPU pixels or owner ordinary-screen visual acceptance**;
block B adds the compact production framebuffer/visual handoff.

The former `verifyJustDireItemBarsSpike`, flag, candidate and four-layout comparison
screen were retired in A to prevent duplicate decoration or stale fixed-row tests.
Historical findings and PNG/report links remain in the
[exploration](../local-docs/JUST-DIRE-THINGS-ITEM-BARS-EXPLORATION.md#9-opt-in-renderingquery-spike--2026-10-16)
under `build/reports/just-dire-item-bars/<version>/`, not production acceptance.

### Upgrade refund research and spike — retired

`verifyJustDireRefundResearch` and `verifyJustDireRefundSpike`, their runs/dispatch
and candidate mixin registration were retired in B; C removed the dormant
sources, fixture component and mixin/config files. Useful native controls and
bounded foreign-wrapper cases now belong to the production gate above. Do not
layer the historical candidate over production or use pre-A no-refund expectations
as current acceptance.

The [exploration](../local-docs/JUST-DIRE-THINGS-UPGRADE-REFUNDS-EXPLORATION.md)
retains dated native findings, feasibility scenarios, commands, results and their
limitations. Historical reports remain in
`build/reports/just-dire-refunds-research/<version>/` and
`build/reports/just-dire-refunds-spike/<version>/`; they do not prove production
lifecycle or ordinary-client acceptance.

### Historical characterization and consumer spike

The [exploration](../local-docs/JUST-DIRE-THINGS-ARMOR-WEAR-EXPLORATION.md)
retains dated native/pre-A mixed characterization (84 observations/version) and
prototype feasibility (67 live observations plus isolated faults and reused EQ).
Their executable research driver, candidate adapter/bridge, plugin/configuration,
old flags/witness and run/verify tasks were removed in C. Useful native controls
now live in the production gate. Do not layer a historical prototype over the
real adapter or expect funded roots to retain pre-A ordinary wear. Historical reports remain in
`build/reports/just-dire-armor-wear/<version>/` and
`build/reports/just-dire-armor-wear-spike/<version>/`; they are not current
production or manual acceptance.
