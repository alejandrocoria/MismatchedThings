# Runtime tests

This directory contains test-only mods and their Gradle run configurations.
Production sources remain in `Common`, `Fabric`, and `NeoForge` at the
mod root (`mods/MismatchedBoots/`). The fast Common JUnit suite remains in
`Common/src/test/java` within that subtree.

Run all commands below from the **repository root**, where the Gradle wrapper
lives. Boots projects use the `:MismatchedBoots` namespace; the earlier root-level
project aliases no longer exist. Unless otherwise specified,
`Common/`, `Fabric/`, `NeoForge/`, `RuntimeTests/` and `gradle/` paths in this guide
are relative to the mod root, including `build/test-mods/` and
`build/network-acceptance/`.
Explicit file-path `-P` overrides are repository-relative or absolute, as before.

The station now belongs to the bundled `mismatchedthings` core. Fixtures compile
against its API but game runtime classpaths exclude that loose core artifact:
the loader must discover it inside the real Boots JAR. Existing composition
datapack overrides remain under `data/mismatchedboots/recipe/`. Core-only tests
have a separate [guide](../../MismatchedThings/RuntimeTests/README.md).

Automated clients must be hidden from window creation and muted by default, not
hidden after startup. When adding fixtures, follow the shared
[client window contract](../../MismatchedThings/RuntimeTests/README.md#client-window-contract).

## Guide index

All guides share the working-directory, path and artifact rules stated here.
Detailed commands and acceptance boundaries live in one guide, not in duplicate
README summaries.

### Shared setup and validation

- [Third-party test artifacts](#third-party-test-artifacts): pinned downloads, caching and fixture isolation.
- [Layout](#layout): source sets, packaged-test boundaries and applied Gradle scripts.
- [Commands](#commands): core suites, packaging, networking and optional geometry controls.
- [Build-transition acceptance](#build-transition-acceptance-2026-10-26): dated consolidated validation, not an additional suite.

### Dedicated guides

- [Mixin binary compatibility](docs/mixin-binary-compatibility.md): publication-format regression and unchanged-JAR
  checks against older NeoForge runtimes.
- [Media client](../media/README.md#targeted-validation): opt-in photo pack and Replay Mod acceptance.
- [Silent Gear](docs/silent-gear.md): state, traits, tooltips, scoped faults and connected lifecycle.
- Just Dire Things:
  - [Movement and cooldown feedback](docs/just-dire-movement.md): Jump, Negate, HUD and completion sounds.
  - [Resources and root upgrades](docs/just-dire-resources.md): FE, live attributes and root-owned installation.
  - [Active abilities, settings and lifecycle](docs/just-dire-abilities.md): Earthquake, Ground Stomp, native screen/input and inventory activation.
  - [Item behavior and presentation](docs/just-dire-items.md): Lava Repair, wear, refunds and FE/durability bars.
  - [Retired research](docs/just-dire-history.md): historical boundaries and links; not maintained test entry points.

### Suites documented below

- [Missing-mod recovery](#missing-mod-recovery--production-capture): capture, suspension and recovery.
- [Mystical Agriculture augments](#mystical-agriculture-augments--production-effects): effects, refunds and lifecycle.
- Aether II:
  - [Installed charms](#aether-ii-installed-charms--production-block-a).
  - [Sets and Sentry](#aether-ii-sets-and-sentry--production-block-b).
  - [Connected lifecycle](#aether-ii-lifecycle-and-connected-smoke--production-block-c).
  - [Alkahest](#aether-ii-alkahest--production).
- Roots Classic:
  - [Equipment rendering](#roots-classic-production-equipment-rendering).
  - [Intrinsic ticks and set consumers](#roots-classic-intrinsic-ticks-and-set-consumers).
- Ice and Fire:
  - [Damage and set probes](#ice-and-fire-damage-and-set-probes).
  - [Production Tide acceptance](#production-tide-feet-acceptance).
- [Neo Vitae infusion](#neo-vitae-infusion-composition--production-creation-gate): production composition and lifecycle.
- [Oritech](#oritech-exo-fall-recognition), [AllTheModium](#allthemodium-boot-recognition) and [Undergarden](#undergarden-boot-recognition): native boot recognition.
- [Aquaculture source tooltips](#aquaculture-source-tooltips): native descriptions, physical Shift and optional-client boundaries.
- [Tooltip separators](#shared-tooltip-separators) and [item geometry](#item-geometry-coverage): shared rendering checks.
- [Vanilla armor classification](#vanilla-armor-classification): shared startup/server regression.

## Third-party test artifacts

Maintained tests do not depend on JARs in the indev instances. Third-party mods
are downloaded on demand from exact Modrinth/CurseForge publications and checked
against their SHA-256 pins. The formerly local fixtures are registered in
[mod-artifacts.json](mod-artifacts.json), resolved through Gradle's artifact
cache and staged in `mods/MismatchedBoots/build/test-mods/<loader>/`. Other
existing test configurations retain their exact Maven coordinates.

To prefetch and verify the entire fixture catalog:

```powershell
.\gradlew.bat :MismatchedBoots:prepareTestModArtifacts
.\gradlew.bat :MismatchedBoots:verifyTestModArtifacts --offline
```

Each suite copies only its required artifacts into its disposable run. It never
reads or changes indev mods/configs/worlds by default. Existing explicit local
path overrides remain available for deliberate development experiments, but
are not required for normal tests. Native guard JUnit checks also resolve their
default JARs automatically; transformed-class witnesses still come from their
dedicated runtime checks. After all required Gradle/game artifacts are cached,
tests can run offline. Adding a maintained compatibility test requires a pinned,
automatically resolved artifact, not an implicit dependency on an indev JAR.

The mod parent owns these services and the canonical `verifyPackagedDependencies`,
`verifyPackagedDependencyChecks`, `verifyLoaderServerStartup` and
`verifyLoaderNetworking` tasks, all under `:MismatchedBoots:`. The repository root
keeps explicit delegators for those four tasks and the two catalog tasks above.
There is no root publication delegator; use `:MismatchedBoots:publishMod`.
Nested Gradle processes still use the repository wrapper and working directory;
explicit file-path overrides retain repository-relative semantics. Previous root
build evidence/cache trees are neither migrated nor read as staging defaults.

## Build-transition acceptance (2026-10-26)

The consolidated post-monorepo matrix passed on the configured Fabric baseline
and NeoForge .114/.99, with **241 runtime gates/profiles passed**. Neo Vitae Live
on .99 is the one blocked combination: pinned Modonomicon 2.16.0 requires
NeoForge .109+. It passed on .114; its absence control passed on .99. No upstream
requirement was bypassed. These are maintained acceptance profiles, not every
possible property combination or disjoint sets of assertions.

Accepted JUnit suites contain **637 cases with no failures/errors/skips**:
Common 326, Fabric fixtures 9, and NeoForge 151 on each baseline. Required final
class exports were freshly generated for both versions. An initial .114 attempt
failed three tests because the local runner omitted sibling variant exports;
completing that evidence and retrying only JUnit resolved it without changing
production code, tests or timeouts. Preserve sibling class exports alongside
the principal file when archiving witnesses: some tests resolve them relative
to the supplied path.

Canonical Fabric GameTests discover 97 cases: 91 exercised/6 context skips
normally, 92 exercised/5 skips with attribute rules. Dedicated contexts, reloads,
clients, persistence/recovery, Quilt, viewers and same-loader networking passed.
Packaging, all 12 corruption controls and the 30-artifact integrity gate passed.
Normal .114 artifacts and all configured runtime fixture source sets were
restored. The owner confirmed both indev clients preserve their local data and
pass representative workbench/craft/equip/presentation/save-rejoin checks.

## Layout

- `docs/`: focused execution guides for the larger suites; retired JDT research
  is separate from maintained production commands.
- `Common/src/main/java`: shared loader-server/network acceptance scenarios and helpers.
  This is a shared source directory, not a separate Gradle project.
- `TooltipSeparator/src/main`: shared production-separator scenarios and read-only
  probes, with legacy spike names until consolidation; never packaged in production.
- `MysticalAugments/src/main`: production MA effect/refund probes, competing-mixin
  controls and read-only delivery witnesses; never packaged in production.
- `MissingMods/src/main`: shared opt-in recovery-model proof and compatible/
  incompatible codec wrappers; production capture remains disconnected in A.
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
  Mod-specific mixins are grouped under `runtimetest/neoforge/mixin/compatibility/<mod>`;
  general instrumentation stays directly in `mixin`. Dedicated shared suites
  already scoped by mod/purpose keep their own local `mixin` package.
- `NeoForge/src/iceAndFireTideTest`: separately packaged counters and competing
  mixins for opt-in acceptance of the production Tide adapter, not a second owner.
- `NeoForge/src/iceAndFireTideHead`: an independent test-only HEAD owner with its
  own data/tick/guard, used for optional composition and rejection checks.

Runtime tests load the packaged production mod. Production output is available
only for compilation; test classes, metadata, and fixtures are not included in
the production artifacts. No runtime test infrastructure is installed in the
normal indev client/server runs.

### Fabric Gradle scripts

[Fabric/build.gradle](Fabric/build.gradle) keeps plugins, dependency configurations,
source sets, Loom/Fabric API setup, core GameTests (including persistence,
reloads and context/report handling) and network runs. Optional compatibility
suites use four applied scripts, each with its runs, preparation and verification:

- [fabric-recipe-viewer-tests.gradle](../gradle/fabric-recipe-viewer-tests.gradle):
  real JEI/REI recipe reload and repeated reopening, with pinned viewer artifacts.
- [fabric-advanced-netherite-tests.gradle](../gradle/fabric-advanced-netherite-tests.gradle):
  Advanced Netherite gameplay acceptance and its isolated fixture JAR.
- [fabric-betterx-tests.gradle](../gradle/fabric-betterx-tests.gradle): BetterX
  enabled/disabled piglin recognition and production-packaging checks.
- [fabric-create-fly-tests.gradle](../gradle/fabric-create-fly-tests.gradle):
  Create Fly server/client acceptance, fault controls and packaging checks.

Apply these after the shared Loom/Fabric API setup and source-set declarations.
Their providers remain local, without a new global context object; existing
task names, commands, artifact coordinates and run/report paths are unchanged.

### NeoForge Gradle scripts

[NeoForge/build.gradle](NeoForge/build.gradle) keeps the project/source-set setup,
network runs and the remaining geometry, tooltip, Ice and Fire and Advanced
Netherite suites. JDT suites use the existing applied-script pattern under the
root `gradle/` directory; each keeps its run configurations, preparation and
verification together:

- [just-dire-items-tests.gradle](../gradle/just-dire-items-tests.gradle): smithing,
  root upgrades, refunds and armor wear; owns `prepareJustDireSmithing`.
- [just-dire-resources-tests.gradle](../gradle/just-dire-resources-tests.gradle):
  energy storage, attributes and resource-client save/restart checks.
- [just-dire-movement-tests.gradle](../gradle/just-dire-movement-tests.gradle):
  Jump, Negate Fall Damage, cooldown HUD and completion sounds; owns
  `prepareJustDireJumpClient`.
- [just-dire-abilities-tests.gradle](../gradle/just-dire-abilities-tests.gradle):
  Ground Stomp, Earthquake, native settings/input and connected lifecycle;
  owns `prepareJustDireGroundStompStateAbsent`.
- [just-dire-lava-repair-tests.gradle](../gradle/just-dire-lava-repair-tests.gradle):
  Lava Repair, native settings and save/restart checks.
- [just-dire-item-bars-tests.gradle](../gradle/just-dire-item-bars-tests.gradle):
  production FE/durability rendering and its isolated server/client fixtures.

Providers remain local to each script, without a new global context object.
Shared JDT paths intentionally name the same disposable worlds and markers;
preparation is registered once and reused by task name. Keep the application
order in `NeoForge/build.gradle`: items and movement provide preparation used
by later suites, and abilities registers client variants configured by Lava
Repair. Existing task names, commands, overrides and run/report paths are unchanged.

## Missing-mod recovery — production capture

**Current implementation: C native readiness, with D lifecycle coverage.** Check raw source-item
registration before native decoding; otherwise accept a complete native success
with strict root/source validation. Missing sources and failed/partial reads
remain inactive with their protected source/root state. Successful native
normalization/conversion/filtering is accepted, without foreign round-trip
equality or a generic reference audit. Historical strict A/B evidence is separate.
The owner confirmed manual acceptance on Fabric and NeoForge on 2026-10-25; see
[D's record](../local-docs/MISSING-MOD-PERSISTENCE-IMPLEMENTATION.md#104-block-d--lifecycle-and-network-consolidation--2026-10-24).

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyMissingModsRecovery :MismatchedBoots:RuntimeTests:NeoForge:verifyMissingModsClients
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:verifyMissingModsRecovery :MismatchedBoots:RuntimeTests:Fabric:verifyMissingModsClients
```

Each disposable candidate world crosses seven independent servers: `write`,
`absent`, `absentagain`, `partial`, `sources`, `restore`, `reloaded`. The original
native-loss control retains its three phases; required-hook refusals stop before
world creation. Use `-Pneoforge_version=26.1.2.99` for the supported floor and rerun
without that override to restore habitual .114 artifacts.
The explicit native control demonstrates loss; the candidate uses only the
packaged production ItemStack hook and owned model. Eleven slots compare complete
encoded stacks, current edits, typed NBT, paid-root FE, unknown attribute/equip
sound/enchantment references, vanilla controls and a non-equippable bare shell. Candidate
stacks also exercise the real network codec while absent and restored.

The required capture is installed independently of optional mods on both loaders.
No fixture supplies a candidate decoder or archive registration. Only the
fixture's explicit control bypasses production decoding; production has no
disable switch. The model uses
defensive binary NBT, complete-state readiness, original/current conflict
retention and a non-wearing feet shell. Bounded malformed archive values stay
opaque in quarantine; real native barrel loading must preserve both the affected
item and adjacent slot. Foreign throwing/read-error codecs remain suspended;
successful native conversions/filtering and stable reload are checked separately.
Candidate writes also check native JSON round-trips of active root/source
booleans, suspended shells with opaque typed NBT payloads, and restoration with
an enchantment requiring registry context. Active JSON is not transcoded through
NBT; archived values decode as NBT with the original registry lookup.
An actual compatible operation wrapper participates in
decode; an altered factory or competing wrapper which bypasses capture must
refuse startup before world creation. Loom can
exit 0 after that bootstrap exception, so the refusal checks the exact diagnostic
and absent world/result rather than exit code alone. Runs and reports are under
`<loader>/build/{run,reports}/missing-mods/<baseline>/`.
Artifact pins and the bounded runner are in
[missing-mods-spike.gradle](../gradle/missing-mods-spike.gradle).
Optional `'-PmissingModsCases=candidate,refused,bypassed'` and
`'-PmissingModsStages=write,absent,absentagain,partial,sources,restore,reloaded'` select proof phases; later phases
require their earlier world/baseline. `write` resets only that disposable run.
Fabric uses the separate MA port plus a controlled temporary boot registration
and foreign counter. Its second dependency is a registry fixture, not another
third-party Fabric distribution. NeoForge combines real MA and JDT sources.
`partial` returns MA/Cucumber only; `sources` returns both source registrations
while the foreign root counter remains absent. Neither may activate a dual-source
root. The unchanged versions must restore all eleven stacks completely, without
archive growth, duplicate resources or reset controls/cooldowns/refund credits.
Native JDT FE and controls are NeoForge-only. `verifyMissingModsSpike` is a
compatibility command alias, not an executable old recovery candidate.

The connected gate requires that completed candidate server matrix and copies
its world under the runtime build directory. Three hidden/muted, independent
client JVMs (`Clientwrite`, `Clientabsent`, `Clientrestore`) move a primary/inverse
pair from the real barrel into player equipment/inventory, reload with mods
absent, edit it, die/respawn through native packets and pick up/re-equip the
inverse. The primary stays as a real saved item entity. After reinstatement and
reconnect, both complete used roots must recover, with current edits and paid-root
FE intact and no refund/duplication. Native death creates the drops; only then
does the fixture pin their position/delay to avoid random throws escaping its
small test platform. These integrated connections prove lifecycle/packet handling,
not independent wire serialization. Select phases with
`-PmissingModsClientStages=Clientwrite,Clientabsent,Clientrestore`.

The existing `verifyLoaderNetworking` now also sends production-suspended boots
through real separate-process TCP inventory/equipment packets. Server/client
compare the entire persisted shell/archive, including opaque long arrays and a
paid counter above double's exact integer range, and check inactive armor/source
behavior. Use `-PnetworkAcceptanceCase=fabric-fabric` or
`-PnetworkAcceptanceCase=neoforge-neoforge`, optionally with the .99 override.

The write phase additionally checks actual production anvil rename with a
conflict-free persistent reload, denied donor/material/book operations, smithing,
grindstone, source predicates and recomposition; a connected test wearer receives
unprotected damage without combat wear, and actual Mending XP remains XP rather
than repairing the parked enchantment. NeoForge checks a real active FE capability,
its disappearance after suspension and retained-handler insert/extract refusal
with no archived-state mutation. These remain separate focused checks in addition
to D's real-player lifecycle and independent TCP gate.
Explicit direct-wear destruction must not refund parked MA/JDT upgrades; normal
combat must not wear the shell. Known readable non-boots, malformed component
shapes and roots without an original feet projection cannot gain equipability.

C replaces equality-specific assertions with successful native normalization,
root/source conversion on initially active and returning roots, and a controlled
successful filtering case. A decoder counter checks that a missing source skips
foreign root/source decoding. Native read errors, missing source IDs/root component
types and invalid roots/sources still suspend. Ordinary same-version
absent/reinstall cases retain exact-state comparisons; conversion cases compare
against native interpretation, not historical encoding. No per-mod recovery
implementation or general reference scanner is needed. The bounded check and full
production/control/refused/bypassed matrices passed on Fabric and NeoForge .114,
as did 325 Common tests, ordinary startup and packaging. These are controlled
codec transitions, not a particular third-party version-upgrade guarantee. See
[C's record](../local-docs/MISSING-MOD-PERSISTENCE-IMPLEMENTATION.md#103-block-c--source-presence-and-native-readiness--2026-10-24).

D's final process/control/refusal, connected-client and TCP gates passed on Fabric
and NeoForge .99/.114. All eleven candidate roots recover; native controls lose
the nine affected slots. Common retains 325 passing tests; startup/packaging
passed and habitual .114 artifacts were restored. Separately, owner normal-play/
presentation acceptance passed on Fabric and NeoForge on 2026-10-25, completing D.
No actual newer-third-party-release upgrade is claimed.

## Mystical Agriculture augments — production effects

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyMysticalAugments
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyMysticalAugments '-Pneoforge_version=26.1.2.99'
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:verifyMysticalAugments
```

These targeted tasks run five isolated dedicated servers per loader/baseline,
using the exact downloaded MA/Cucumber artifacts pinned in
[mystical-augments-tests.gradle](../gradle/mystical-augments-tests.gradle).
Runs/reports are isolated under each runtime project's
`build/run/mysticalAugments/<baseline>/` and
`build/reports/mystical-augments/<baseline>/`.

`Production` exercises the packaged adapter without effect candidate mixins;
`Compatible` adds harmless final-transform changes and chained equipment/effect
operations. `NoEquipment` hides the native equipment lookup and verifies only
Health/Step/fall support declines; `NoJump` introduces an unsafe native stack
write and verifies only Jump declines. Both retain native controls and unaffected
mixed channels. `Absent` checks quiet optional absence. Expected bounded refusal
warnings are checked separately from behavior assertions.

Optional `'-PmysticalEffectVariants=Production,Absent'` selects a focused subset
without weakening the complete default gate. B has its separate refund selector.

Coverage includes all 23 built-in IDs in both orientations, all 11 family
behaviors, strongest/union selection, held rejection, malformed sources/roots,
Health/Step cache cleanup, native armor coexistence, actual fall damage and
timed Absorption. Native equipment order and the shared-timestamp winner are
compared with original boots. General augment lookup and Tinkering Table admission
remain unchanged. Since B, actual mixed breaks return only valid left-source
purchases after consumption; effect-union selection never determines refunds.

Lifecycle probes dispatch registered native loader callbacks on disconnected
server players, save/load player NBT for reconnect-state evidence, and construct
a fresh player after a death callback. They are not connected-client login,
network synchronization or actual respawn acceptance. See the
[implementation record](../local-docs/MYSTICAL-AGRICULTURE-AUGMENTS-IMPLEMENTATION.md#8-execution-record)
for results and the remaining owner acceptance boundary. Historical effect/refund
candidate registrations have been retired rather than layered over production.

### Owner-side refunds — production consumption gate

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyMysticalAugmentsRefunds
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyMysticalAugmentsRefunds '-Pneoforge_version=26.1.2.99'
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:verifyMysticalAugmentsRefunds
```

The production adapter observes the one-item `ItemStack.applyDamage` consumption,
not MA/Cucumber's earlier break event. Capture retains only the left-source
purchase entitlement and original player/inventory/root identity. After confirmed
consumption and validation it inserts native-bound augment items through
`Inventory.placeItemBackInInventory`, including native overflow dropping.
Transient root-identity guards cover shrink and delivery reentry; no persistent
credit, synthetic event, native handler replay or retry is introduced.

Variants: `Refunds`, `RefundInner`, `RefundOuter`, `RefundMissing`, `RefundExtra`,
quiet `Absent`, plus NeoForge `RefundCoexist`. The inner/outer peers exercise
unchanged forwarding and nested calls. Missing/ambiguous final local proofs
refuse only MA refunds, retaining ordinary consumption and armor effects.
The NeoForge coexistence run pins JDT `1.6.11` by SHA-256 in the Gradle fixture,
checks both production observer markers and actually breaks a credited JDT root.
Optional `'-PmysticalRefundVariants=RefundInner,RefundOuter'` restricts diagnosis
to named variants; omit it for the complete gate. Class export is limited to
`ItemStack` under each isolated run's `.mixin.out/class/` for local-proof diagnosis.

The eight pair scenarios cover one/two MA inputs, different/identical augments,
four installed physical augments including a weaker family variant, duplicate
occupied slots, empty/vanilla controls and full inventory. Both orientations
round-trip through NBT before actual breaks, with per-owner and total physical
counts, no early/sequential repeat refunds and immutable input checks. A
read-only accepted-spawn witness verifies native overflow delivery independently
of immediate world-index visibility.

Additional controls observe every mixed inventory delivery after consumption,
nonbreaking wear/repair, prevented shrink, direct deletion, malformed/missing
data, unbreakable/creative/spectator/detached/count-two stacks, inventory/offhand
breaks, ownership/entitlement edits, unrelated names, throwing shrink and
cancelled/throwing delivery. Reentry covers the same root and a different valid
root before shrink and during delivery. External cancellation/failure is not a
rollback guarantee; a dispatched purchase is never retried.

The [production plan](../local-docs/MYSTICAL-AGRICULTURE-AUGMENTS-IMPLEMENTATION.md)
records separate A/B/C production evidence. The owner confirmed the NeoForge
checklist and final Health V correction retest on 2026-10-24; separate Fabric
manual acceptance remains unconfirmed. The old `verifyMysticalAugmentsRefundSpike`, its
`Refund` run, `AugmentRefundMixin` and candidate ownership flag/helper are retired.
Historical allocation/native-delivery research remains in the exploration;
it is not substituted for the new consumption/reentry proof.

### Presentation and connected lifecycle — C

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyMysticalAugmentsLifecycle
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:verifyMysticalAugmentsLifecycle
```

Use NeoForge `.114` for this client gate; `.99` A/B server evidence remains
separate. Each loader runs three bounded JVMs with the same exact MA/Cucumber
pins: `Seed` writes four recipe-produced named/damaged roots to a world barrel;
`Client` opens that world with a real integrated connection; `Rejoin` restarts
the saved client world with the same player UUID. Reports and manifests live in
`RuntimeTests/<loader>/build/reports/mystical-lifecycle/<baseline>/`; process logs
are adjacent `mystical-lifecycle-<baseline>-<phase>-process.log` files. Runs are
under `build/run/mysticalLifecycle/<baseline>/`. Neither normal indev mods nor
worlds are changed. Seed resets only these disposable run/report directories.

For diagnosis, `'-PmysticalLifecyclePhases=Client,Rejoin'` reuses the existing
successful Seed world. Client starts from a fresh disposable copy; Rejoin must
follow a successful Client save. The full default still runs all three phases.

The shared client compares native Health III/Step with both inverses, then checks
actual client/server attribute packets and unequip cleanup. Normal keyboard Jump
III/No Fall movement is measured on both connected actors against native boots.
After Health/Step have activated on both actors, lethal damage plus the client's
real respawn packet replaces both player objects;
complete kept roots/purchase data must match before/after, with no stale modifiers.
The production recomposer preserves used/name state and the two purchase sources.
Rejoin checks full player inventory data against the previous JVM's manifest
before new actions, repeats connected effects and actually breaks all four roots:
exactly five original physical purchases, owned per left source, must be returned.
This is integrated networking, not a dedicated-network/cross-loader matrix.

Real client tooltips compare native translated heading/indent/components in the
existing source sections, including weaker/stronger original purchases, and
require no duplicate augment list at root. Normal/advanced flags, both orientations,
names, separators, empty unions, hidden component/root/source and read-only
history are checked. Only `mystical-native.png` and `mystical-mixed.png` are
captured per loader at 1920×1080 / GUI scale 2. Clients are hidden and muted.
No unrelated screenshot/mod suite is run. The owner's final checklist remains
in section 7.2 of the implementation plan, separate from automated acceptance.

The maintained shared fixture uses `MysticalNativeTestApi`, not a candidate
bridge. Its small Gradle script, fixture JAR and test mixin config no longer have
spike names. Adversarial production A/B controls remain; historical candidate
producers/owners do not. Dated spike evidence stays in the exploration.

#### Health Boost V breakage — controlled regression, manual acceptance separate

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:diagnoseMysticalAugmentsHealthBreak
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:diagnoseMysticalAugmentsHealthBreak
```

This explicit hidden/muted client regression resets the disposable MA lifecycle
Seed/client fixture and reports, not development saves/mods. It is not part of
the ordinary C gate and captures no screenshots. Actual physical damage breaks
native boots and both mixed inverses at one remaining durability with Health V
active. Reports compare server/client health, minimum client health and death
screen appearance. It separately emulates an attribute snapshot lacking the
bonus before equipment/cache cleanup; **controlled ordering is not evidence
that ordinary packets arrived in that order**. Mixed controls assert no zero
health/death screen and agreement with the server, at full and partial boosted
health, including a direct native-to-mixed transfer of the same cached augment.
On NeoForge, a final native-only control at 20/40 health still reproduces the
upstream client-only death and ineffective respawn request, proving the fix is
not global. Fabric's native handler only runs server-side: that control must
remain alive, not manufacture a client cache. Completion is automated regression
evidence, not owner acceptance of the original development-modpack incident.
The owner subsequently confirmed that original NeoForge case on 2026-10-24;
that manual acceptance is separate from the automated marker and does not
establish a separate Fabric manual run. The later approved source-only tooltip
simplification is implemented; current tooltip checks assert no duplicate root
list, independently of these gameplay regressions.
The owner confirmed the source-only layout's final visual check on 2026-10-24
(plan 8.7); this does not extend the separate Fabric manual acceptance scope.
See the [correction record](../local-docs/MYSTICAL-AGRICULTURE-AUGMENTS-IMPLEMENTATION.md#85-mixed-health-boost-cleanup-correction--2026-10-24).

## Commands

Run Gradle tasks sequentially from the repository root:

The maintained Lava Repair gates are documented under
[Just Dire Things Lava Repair production](docs/just-dire-items.md#just-dire-things-lava-repair-production);
the retired research boundary is in the [historical guide](docs/just-dire-history.md#just-dire-things-lava-repair-research-spike).

```powershell
.\gradlew.bat :MismatchedBoots:Common:test
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:test
.\gradlew.bat :MismatchedBoots:Fabric:verifyGameplay
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:verifyDamageTagReload
.\gradlew.bat :MismatchedBoots:Fabric:verifyPersistenceRestart
.\gradlew.bat :MismatchedBoots:Fabric:verifyClient
.\gradlew.bat :MismatchedBoots:Fabric:verifyGameTestContextGuard
.\gradlew.bat :MismatchedBoots:Fabric:verifyQuiltSmoke
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyGeometryCharacterization
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyTooltipCharacterization
.\gradlew.bat verifyLoaderNetworking
.\gradlew.bat verifyPackagedDependencies
.\gradlew.bat verifyPackagedDependencyChecks
```

The established Fabric verification, GameTest run, and GameTest compilation
tasks delegate to `:MismatchedBoots:RuntimeTests:Fabric`. Direct `:MismatchedBoots:RuntimeTests:Fabric` commands
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

An optional Ice and Fire CE probe uses the pinned downloaded candidate JARs:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyGeometryCharacterization -PiceAndFireTest=true
```

It copies only Ice and Fire 2.2-beta.3, Uranus 3.0-alpha.1, and Jupiter
2.4.3 into the disposable geometry client, verifies their SHA-256 pins before
copying, and leaves the development mods directory unchanged. Source JARs
default to the test artifact cache; override with
`-PiceAndFireTestModsDirectory=<directory>` if needed. Integration 0.2 is nested
in Ice and Fire, not another source JAR. The ordinary run needs none of these mods.

The probe checks all 30 custom-model boot variants and Sheep Boots, each in
left/vanilla, vanilla/right, and same-item pairs (93 ordered pairs). It uses
real registered items and their model/texture extensions with controlled native
equipment assets, because item defaults are not bound before opening a world.
It does not validate actual default components, gameplay, complete material
appearance, unusual animations, or pixel-level visual quality. Current artifacts
and manual acceptance remain in [COMPATIBILITY.md](../local-docs/COMPATIBILITY.md#ice-and-fire-community-edition).

## Aether II installed charms — production block A

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyAetherIICharms '-Pneoforge_version=26.1.2.99' --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyAetherIICharms --console=plain
```

For an optional-free fixture control, reuse `:MismatchedBoots:RuntimeTests:NeoForge:verifyOritechFallAbsent`
without competing recognition controls; no duplicate Aether absence driver is needed.
Both production archives must remain free of native Aether classes, fixture
drivers and experimental query mixins/config. The identifier-only Common default
is intentionally part of production, not a native dependency leak.

The opt-in server fixture downloads and hash-checks alpha.4.1 (including its
bundled Cumulus/Nitrogen) using the test artifact catalog.
Ordinary compilation has no Aether dependency. This run uses **normal server
rules**, no per-creation omission candidate, and never enables the observation/
operand-fault mixins. `aether_ii:charms=omit` is now the configurable production
default; explicit overrides remain supported outside the snapshot-only contract.

Coverage: all five shipped armor charms, native and empty-holder baselines,
paid Workbench inverse outputs, identical charmed sources, two Health holders,
normal-default baked/effective/equipped contributions, native server Forge
cursor/slot/Apply/locked/take and mixed rejection without charm consumption.
Vanilla equipment updates measure real player attributes, not manually installed
expected modifiers. Repeat generic source-wise recomposition changes an ordinary
source name and checks complete histories/no bonus accumulation. Native config
controls verify registered missing-default completion and explicit-owner
retention; Common tests cover the dormant declaration and payload without Aether.
Registry-aware root/source serialization detects mutable-payload changes.

Reports, markers and raw process logs are under
`RuntimeTests/NeoForge/build/reports/aether-ii-charms/<version>/`; disposable
worlds/mods/config use `build/run/aetherIICharms/<version>/` within the NeoForge
fixture module. Each run refreshes its world and markers.
[A's execution record](../local-docs/AETHER-II-COMPATIBILITY-IMPLEMENTATION.md#71-block-a--installed-charm-composition--2026-10-21)
owns actual results/limits. This is not connected client-screen/packet,
native editing-recipe, all-effect, save/restart or owner manual acceptance.
This gate does not certify set/Sentry effects, reinforcement, Altar/Hourglass,
presentation or broken-item lifecycle; B's recognition gate is separate below.

The pinned native server emits two client-only `Toast` loading ERRORs during
startup before driver execution; they remain visible and are not treated as
clean logs. Section 8.5 records native guidebook bytecode evidence and the limit
of that diagnosis. The verifier checks scoped reports/assertions, not a blanket
error-free native-mod environment.

## Aether II sets and Sentry — production block B

```powershell
.\gradlew.bat :MismatchedBoots:NeoForge:test --tests '*AetherRecognitionShapeTest' :MismatchedBoots:RuntimeTests:NeoForge:verifyAetherIIRecognition --console=plain
```

Repeat with `'-Pneoforge_version=26.1.2.99'`, then restore .114. The four native
shape tests are opt-in artifact characterization, explicitly skipped without
the property, not claims that ordinary unit tests loaded Aether. Run all Gradle
tasks sequentially. The runtime aggregate runs Live, Chains, SetChanged and
SentryChanged in that order, with fresh isolated worlds/reports per variant.
Individual verifiers append those names to `verifyAetherIIRecognition`.

Production final-export proofs edit only the native per-stack set comparison
and Sentry FEET boolean consumer, after ordinary mixins/MixinExtras. They preserve
true native/peer results and reuse the exact original stack, without equipment swaps,
whole-method hashes or a second effect engine. Compatible expression-result
chains are admitted. Unknown/opaque operand replacements or relocated/merged
query flows are not universally provable: refuse only that dependent extension
with one warning, retaining original native behavior, the other domain and A.

Coverage: six complete families at two/three pieces with real native gloves
(native lookup excludes FakePlayers); paid inverse outputs; duplicate once and
simultaneous distinct sets; native Zanite activation/repeat/removal; held/idle/
malformed/spoof negatives; Sentry native/inverse/duplicate 7.99/8 event controls
separate from numeric modifiers. Real `causeFallDamage` subjects prove short
suppression/refusal and partial long-fall reduction for Sentry, plus the distinct
Gravitite set-fall consumer. Root/history serialization and equipment identity
remain unchanged. Each variant includes paid Health/diamond +1/+1 sanity.

Chains uses actual MixinExtras expression wrappers with observation counters,
not a source-recognition candidate. SetChanged/SentryChanged add opaque supplier/
holder substitutions that keep native operands/values intact but cannot pass the
local identity proof. Expect one refusal in only that domain; native controls
remain functional, including original owner-root set counts. No fault directly
sets production readiness, grants an ability or alters source data.

Reports/markers/raw process logs are in
`build/reports/aether-ii-recognition/<version>/<variant>/`, disposable runs in
`build/run/aetherIIRecognition<Variant>/<version>/`, both within the NeoForge
fixture module. The verifier checks report completion, exact own warnings and
rejects ERRORs except the documented two native Toast startup diagnostics.
[B's execution record](../local-docs/AETHER-II-COMPATIBILITY-IMPLEMENTATION.md#72-block-b--local-production-recognition--2026-10-21)
owns actual versions/results and limits. This is server/query/damage evidence,
not physical client falling, jump input, all effect bodies, save/rejoin or owner
manual acceptance. C's separate lifecycle/connected gate is below; native
styles/tooltips/transactions remain separate decisions.

The shared driver keeps independent production scopes. Both the per-creation
charm candidate and the two query-recognition candidates/plugin/config/flag are
removed. `verifyAetherIISpike` is retired without an alias. Dated pre-A +3/+1 and
pre-B asymmetric/query-candidate observations remain historical in
[the exploration](../local-docs/AETHER-II-COMPATIBILITY-EXPLORATION.md#8-native-measurements-and-local-candidate--2026-10-21),
not current production expectations or an executable fallback.

## Aether II lifecycle and connected smoke — production block C

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyAetherIILifecycle --console=plain
```

Repeat with `'-Pneoforge_version=26.1.2.99'`, then restore .114. This follows the
existing SG barrel/separate-JVM/integrated-client/rejoin pattern, with no new
generic lifecycle framework. Four sequential phases: Save, Restart, Client,
Rejoin. Save alone refreshes the disposable server world; Client copies that
completed world into its own save; Rejoin reuses the actual connected player's
save without rerunning preparation. The native artifact is hash-checked; it and
the generated production/fixture JARs are copied into fixture-only directories,
not the owner's client installation. The fixture uses the native skip-backup/
join button if Minecraft asks to confirm its disposable world copy.

Six paid used/named roots cover both orientations of Health-charmed Neptune,
Sentry and Gravitite with diamond. Registry-aware whole-stack comparisons check
root wear, projected attributes and complete source/charm histories before
callbacks in a different JVM. Shared production probes repeat Health +1,
no live holders/root Forge installation, Sentry -.375 and short-fall boundary,
Gravitite threshold/fall and immutable root state.

The hidden/muted integrated client receives normal equipment/attribute packets:
both Health/Neptune inverses grant Health +1 and native Neptune oxygen, then
lose only the set effect when another matching piece is removed. Four actual
keyboard flights compare native Gravitite, both used mixed orientations and a
missing-third-piece control. Only normal key presses initiate jumping; native
attachment consumption/ground reset, actual apex and server-received movement
are observed without attachment/velocity/equipment substitution. A second client
JVM matches the same UUID's six actual saved player roots, then repeats these
small scenes. Only immutable JSON expectations cross game threads; no custom
item sync packet or production dependency is added.

Reports/manifests/raw logs: `build/reports/aether-ii-lifecycle/<version>/`;
disposable server/client runs: `build/run/aetherIILifecycle/<version>/`, within
the NeoForge fixture module. The verifier requires all phase markers/reports,
four packet/four flight observations per connection, and no own refusal or
unexpected ERROR. The known dedicated-server Toast errors remain visible.
[C's execution record](../local-docs/AETHER-II-COMPATIBILITY-IMPLEMENTATION.md#73-block-c--lifecycle-connected-smoke-and-handoff--2026-10-21)
owns actual results and the separate owner-confirmed gameplay acceptance on
2026-10-21. The bounded A/B/C plan is complete; the reported missing active
set-attribute tooltip lines (notably Burrukai) remain separate presentation work.
This smoke
does not certify every native effect body, physical input, multiplayer server
topologies, native Forge screen UX or deferred presentation/transactions.

## Aether II Alkahest — production

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyAetherIIAlkahest --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyAetherIIAlkahestGuards --console=plain
```

Repeat the normal aggregate with `'-Pneoforge_version=26.1.2.99'`, then restore
.114; focused competing/failure variants normally run on habitual .114. The normal
aggregate runs Live/Absent sequentially with the packaged production mod and a
hash-pinned Aether artifact only in Live. It enables **no research or peer mixins**.
Native dropped lifespan/expiry, both-hand wear, actual armor/health damage and
full histories use paid Forge/Workbench inputs. Either reinforced source protects
both outputs without copying tiers. Tag-only Arkenium protects items, not armor
wear. Each native/mixed profile also takes two actual physical hits on a fresh
subject without prior Alkahest contact: ordinary armor wear must remain 1/1,
contrasting the measured last-Alkahest carryover, with full history preservation.
Two completed asynchronous tag reloads exercise membership changes on the
same existing roots; absence uses a normal paid pair without Aether.

Guards runs Chains, FluidReceiver, FluidSupplier, ArmorReceiver, ArmorSupplier,
FluidBinding and ArmorBinding; append those suffixes to `verifyAetherIIAlkahest`
for an individual verifier. Chains adds actual local receiver/supplier/result
wrappers. Opaque-operand variants preserve native values but must independently
refuse the unprovable domain. Binding variants throw once inside the selected
production binding read. Each small profile set checks native positives, repeated
inverse queries, tag-only outputs, the other domain and full histories. The gate
requires one warning only for a selected refusal, none in Live/Chains/Absent.

Reports: `NeoForge/build/reports/aether-ii-alkahest/<version>/<mode>/`,
relative to this directory. The known two native dedicated Toast ERRORs remain
visible and narrowly allowed; unexpected errors fail, absence is quiet. Direct
native contact calls are not automated movement/collision proof. Aether's measured
previous-damage-source timing defect is preserved, not fixed. Scope, execution
records and owner ordinary-client acceptance belong to
[the Alkahest plan](../local-docs/AETHER-II-ALKAHEST-IMPLEMENTATION.md).
C's automated closure passed with the existing gates; its real-contact checklist
is separate from these direct native-body/event tests.
The owner confirmed all real-contact checks passed on 2026-10-23, completing
the bounded Alkahest plan. That manual evidence does not add another automated
run or manual loader matrix; the native timing defect and explicit deferrals remain.

The executable Baseline/Tags/Candidate/Spike tasks and all protection prototypes
are retired without aliases. Historical results remain in the exploration and
`aether-ii-alkahest-spike` reports; do not expect pre-B owner-only tier behavior
or layer old candidate hooks over production. The driver/script are now
`AetherAlkahestTest` / `aether-ii-alkahest-tests.gradle`.

## Roots Classic production equipment rendering

The opt-in native-model check runs in the disposable geometry client:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyGeometryCharacterization -ProotsRenderingTest=true -PiceAndFireTest=true --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyGeometryCharacterization -ProotsRenderingTest=true -PiceAndFireTest=true '-Pneoforge_version=26.1.2.99' --console=plain
```

`rootsRenderingTest` downloads `RootsClassic-26.1.2-1.6.7.jar` and verifies its
SHA-256 before copying it from the test artifact cache; no external
library is needed. `iceAndFireTest` additionally runs the existing 93 ordered
Ice and Fire pairs through the same production adapter. Neither option edits those JARs
or the development client. The ordinary geometry command needs neither mod.

The client creates its own flat integrated world and waits for synchronized rules.
Eight ordered pairs use actual default Roots equipment assets and production
composition/splitting, with native vertex emission, walking/crouching states,
queued shared models, unchanged source snapshots and texture/trim/glint nodes.
The entire matrix repeats after an actual resource reload. Focused controls cover
missing foot roots, unknown models with similarly named children and unmarked
native models; they do not mutate the extension's cached models. Existing vanilla,
replacement and Ice and Fire controls run in the same client before/after reload.
No test adapter or selector mixin remains; the old `rootsRenderingSpike` flag is
retired. The production code delegates native animation without visibility edits.

These are actual deferred-node and emitted-vertex checks, not GPU screenshots or
the complete player/armor-stand layer path. Qualitative trim/material alignment,
entity poses and gameplay acceptance remain separate. Native Roots models may
legitimately retain their own static cache across reload; no fresh native model
identity is assumed. Current evidence and manual status belong to
[Roots block A](../local-docs/ROOTS-CLASSIC-COMPATIBILITY-IMPLEMENTATION.md#7-block-a-execution-record).

## Roots Classic intrinsic ticks and set consumers

The focused production B/C gates use the pinned Roots Classic artifact from the
rendering check, with no external library. They never alter the owner's mods:

```powershell
.\gradlew.bat :MismatchedBoots:NeoForge:test --tests '*Roots*ShapeTest' --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyRootsTicks --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyRootsTicksAbsent --console=plain
```

Repeat these commands with `'-Pneoforge_version=26.1.2.99'` for the support floor;
restore the ordinary .114 build afterwards. `verifyRootsTicks` sequentially runs
Live, Receiver, Helper, Staff, Crystal and Healing isolated servers. Their test-only
mixins count native callbacks and force only native repair/Terra/healing random
rolls while explicitly armed.
The negative processes add a foreign stack consumer via late test mixins in one
Sylvan callback or the shared repair helper. Final inspection must refuse their
proofs; native controls must still work and unrelated valid receiver support must
remain enabled. Three set-negative processes insert an identity-preserving but
unrecognized item consumer into just one final FEET predicate; native equipment,
the other set consumers and intrinsic ticks must remain functional. One bounded
set diagnostic is expected in each, none in Live/Absent. Absence loads no Roots
code and expects no diagnostic. Guards are also checked against the real JAR and
bounded instruction mutations in units.

Real item dispatch and a NeoForge offline test player's native `aiStep` cover
worn, selected main hand, offhand and ordinary backpack behavior. Native controls
establish the engine's dispatch count; no per-game-tick deduplication is invented.
Periodic Roots entity-tick regeneration is not posted in these scoped controls,
so the extra boot Terra delta is measured independently. These are not a connected
player/client presentation or full natural-world probability measurement.

Set cases cast both real native staffs: Nature's Cure converts poison to native
regeneration, whose amplifier measures the exact potency, including Crystal's
independent base bonus. Native Terra payment/staff uses remain asserted. Wildwood
healing executes through the actual entity-tick event bus with scoped success/
failure rolls, incomplete sets, both orientations, duplicates, cross-set pairs,
health/hunger and invalid-root controls. A separate HEAD predicate wrapper accepts
a diamond helmet without editing FEET. Live additionally round-trips an actually
repaired root through inventory save/load and the ordinary slot packet codec;
this is not a new-JVM world restart or connected-client synchronization test.

Reports/logs live under `RuntimeTests/NeoForge/build/reports/roots-ticks/<version>/`
and worlds/mod copies under `build/run/rootsTicks/<version>/`. B/C introduce no
packet or persisted field. Exact executed results and separate manual acceptance
belong to the [B record](../local-docs/ROOTS-CLASSIC-COMPATIBILITY-IMPLEMENTATION.md#8-block-b-execution-record)
and [C record](../local-docs/ROOTS-CLASSIC-COMPATIBILITY-IMPLEMENTATION.md#9-block-c-execution-record).

## Ice and Fire damage and set probes

The independent damage/set gate opens a real dedicated-server world with the
same pinned artifacts and does not need a client:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyIceAndFireDamageCompatibility
.\gradlew.bat :MismatchedBoots:NeoForge:test
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
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyIceAndFireMixinCoexistence
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyIceAndFireMixinCoexistence '-Pneoforge_version=26.1.2.99'
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

## Production Tide FEET acceptance

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyIceAndFireTideProduction --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyIceAndFireTideProduction '-Pneoforge_version=26.1.2.99' --console=plain
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
Run it with `:MismatchedBoots:RuntimeTests:Fabric:verifyCreateFlyCompatibility`; the three local
shape JUnit regressions are part of `:MismatchedBoots:RuntimeTests:Fabric:test`.

The broad server GameTest XML reports mark context-only no-ops as skipped rather
than successful coverage. `verifyGameTestContextGuard` deliberately starts one
selected case without its required property and verifies the expected failure;
its negative XML/log stay under `Fabric/build/context-guard`, outside positive
GameTest reports. The focused fixture tasks enable their actual contexts.

The Fabric JUnit task verifies Better Nether's live configuration and item-set
queries, one warning and permanent boundary disablement after a reflective/API
failure, and no per-tick retry. The real optional-mod gate remains
`:MismatchedBoots:RuntimeTests:Fabric:verifyBetterXCompatibility`; fake APIs do not replace it.

`verifyQuiltSmoke` uses real Quilt Loader 0.31.0-beta.4, its pinned launcher
libraries, Fabric API, and the packaged production Fabric JAR in a disposable
server run. It selects the existing Workbench transaction cases, not the full
Fabric suite; it does not claim Quilt client/rendering or optional-mod coverage.
There is no production Quilt module. The verifier bounds the child process and
checks actual loader identity and XML results. `runQuiltSmoke` is its internal
launch task. No Quilt library enters production artifacts or ordinary tests.

`verifyPackagedDependencies` declares and builds its final packaged JAR inputs.
Its companion negative-control task checks corrupted copies in the mod's build
directory, never modifies release JARs, and verifies rejection diagnostics.

Those entry points also run `verifySharedWorkbenchPackaging` and
`verifySharedWorkbenchPackagingChecks`: one native nested core, API-major
bounds, real core byte equality, no loose/legacy station content, indev runtime
availability and six corrupted-core-package controls. NeoForge project JiJ
metadata has an open `[bundledVersion,)` range; the required mod dependency
separately bounds supported API major 1 with `[1.0.0,2.0.0)`.

Optional recipe-viewer regressions run individually:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:verifyJeiRecipeReload
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:verifyReiRecipeReload
```

These resolve the exact Fabric viewer JARs and dependencies from the test
artifact catalog, verify their SHA-256 pins and copy them into disposable test
runs. They do not alter the indev client or make viewers dependencies of
ordinary tests.
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
includes incompatible-loader rejection and JourneyMap coexistence. The internal `runNetworkTestServer` and
`runNetworkTestClient` tasks require that preparation and are not standalone
verification entrypoints.

To validate the declared minimum NeoForge loader, add
`'-Pneoforge_version=26.1.2.99'` to the selected startup/network verifier.
Explicit `neoforge_version`, `fabric_loader_version`, and `fabric_api_version`
overrides propagate to its child Gradle runs. Normal indev NeoForge stays .114.

Cross-loader play is unsupported. The focused
`-PnetworkAcceptanceCase=neoforge-fabric-refused` case connects a hidden/muted
Fabric client to the actual packaged NeoForge server. It requires Things' exact
loader-mismatch message on the client, no player entering either world and no
server crafting result; a timeout or unrelated disconnect is not success.
The server stops normally after the refusal marker, and both endpoints must exit
cleanly. Run on .99 and .114; the same-loader positive cases remain required
controls. Fabric API already refuses NeoForge clients through registry sync.

On 2026-10-30, both the refusal and NeoForge/NeoForge positive controls passed
on .99 and .114. The standalone Things server/client persistence and screen
control also passed on .114, including its integrated singleplayer connection.

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
under `mods/MismatchedBoots/build/network-acceptance`. Old `CrossLoader*` build
directories and old Fabric test reports are not migrated or deleted.

## Neo Vitae infusion composition — production creation gate

```powershell
.\gradlew.bat :MismatchedBoots:NeoForge:test --tests '*NeoVitaeInfusionCompatibilityTest' --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyNeoVitaeComposition --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyNeoVitaeCompositionAbsent --console=plain
.\gradlew.bat :MismatchedBoots:NeoForge:test --tests '*NeoVitaeInfusionCompatibilityTest' :MismatchedBoots:RuntimeTests:NeoForge:verifyNeoVitaeCompositionAbsent '-Pneoforge_version=26.1.2.99' --console=plain
```

Seven local policy tests cover the seven composition cases, both orientations,
presence-based Blood Mending, fractional/overflow/underflow conservation,
malformed tuple rejection and unchanged inputs/unrelated output state, plus
exact live tuple/marker presence/value preservation and invalid-live rejection. Their
local component fixture does not claim a real optional binding.

The native gate uses the **packaged production mod**, real Neo Vitae components
and the public mixed factory; it checks binding/finalizer wiring, one-sided,
compatible/conflicting and empty containers, orphan charge, marker union,
swapped inputs/repeated previews, immutable snapshots and malformed pair rejection.
B extends the same gate with eight live states in both orientations (spent,
zero, refilled type, equality with old projection, larger container, removed
state, defaulted fields and present-false marker), repeated production
recomposition and controlled synthetic source replacement. That replacement
exposes generic three-way equality mistakes; it is **not a native recipe**.
Root-only later installation and no-container/no-resurrection controls are
included. C replaces B's duplicate positive trim/tier previews with actual menu
takes; the synthetic equality controls remain because ordinary recipes do not
expose every three-way merge boundary.
It installs no fake components or candidate adapter. The Absent gate starts with
no Neo Vitae/dependencies and checks ordinary composition/provenance/durability
and the loaded tier recomposition preview.

Live now runs **two game JVMs**. Save makes three real Workbench transactions
(six outputs, one-time input consumption), runs the pinned native Hellfire Forge
tick for Spiritus infusion and Blood Mending, and checks ingredient/fuel payment.
Native `SpiritusHelper` performs fill/drain, simulation, conflicting-type rejection,
depletion and refill with another aspect. Three actual smithing menu takes
(trim/tier/later-infused tier) preserve root state and appropriate source history.
Six complete roots (compatible, empty, two conflicting, later-infused and absent)
are written to a world barrel with independent serialized expectations.

Restart opens that same world in another JVM, checks its different PID and all
six complete stacks/provenance **before mutation**, then exercises native drain,
another paid trim, empty-container refill/type switching and direct infusion of
the previously uninfused inverse. No candidate forge, simulated payment engine,
source-copy substitute for a menu take or in-memory reload is used.
The native tick is driven synchronously to completion; this does not test its
wall-clock timing or rendered effects. The standard NeoForge offline player
handles native advancement/research notifications without a real connection.

Six vanilla slot-packet codec round trips include the native components and
mixed provenance. They are **not a connected-client/reconnect test**. C changes
no production transport path or custom packet. The owner separately confirmed all
six final manual checks passed on 2026-10-18, including native tooltip/bar
presentation and ordinary-client behavior, accepting C and closing the bounded
A/B/C plan. This does not establish a manual .99/.114 matrix.
A/B's earlier creation-only/preview evidence remains historical; current C results
and per-phase archived reports are in plan section 10.

Live downloads and hash-checks Neo Vitae 1.1.31, GeckoLib 5.5.2 and Modonomicon
2.16.0 from the test artifact catalog, copying them into disposable
fixture directories. Pinned Modonomicon needs NeoForge .109+, so Live is checked
on .114, **not .99**; Absent and local policy tests run on both .99/.114. Never
bypass upstream metadata to fill that matrix cell. No new mandatory dependency,
wrapper, JDK, production configuration or indev world change is required.

Reports/markers/process logs live in
`NeoForge/build/reports/neo-vitae-composition/<version>/{live,absent}/` relative to
this RuntimeTests directory (process logs are alongside the variant directories);
worlds/configs/mod copies are in
`NeoForge/build/run/neoVitaeComposition/<version>/{live,absent}/`.
The verifiers always run bounded child processes and reject failure/missing
markers. Live also requires the lifecycle and Restart success reports; standalone
`runNeoVitaeCompositionRestart` requires a successful Save and must not reset the
world or markers. The runs/tasks are isolated in
[neo-vitae-composition-tests.gradle](../gradle/neo-vitae-composition-tests.gradle).
The [implementation plan](../local-docs/NEO-VITAE-INFUSION-COMPOSITION-IMPLEMENTATION.md)
owns dated automated evidence and the owner-accepted final manual exit (sections
7/10.4). The heading/task names are retained
from A; the existing gate is extended, not duplicated into a second suite.

## Aquaculture source tooltips

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyAquacultureTooltips '-Pneoforge_version=26.1.2.99' --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyAquacultureTooltips --console=plain
```

Run sequentially, restoring .114 last. This bounded gate downloads and hash-checks
Aquaculture 2.9.2 through the fixture catalog; it never reads the indev instance.
It runs a dedicated seed server with Aquaculture, a hidden/muted integrated
client using that disposable world, and a hidden/muted absent-mod client.
Both clients load separate copies of the world so 26.1.2 item components are
bound before constructing test stacks; the seed contains no native item stacks.

The production source callback is compared to the real native handler using
structured Components/styles with no Shift, left Shift and right Shift. Controls
cover both orientations, repeated sources/captures, F3+H, custom names, hidden
source/root tooltips, live holder-tag removal/restoration, unrelated tagged
items, unchanged root/source components, and exactly one final root event.
The tag control rebinds the actual holder in memory and restores it in `finally`;
it is not a full datapack reload test. A final injected native-method failure
adds text and mutates only the defensive event stack before throwing, proving
atomic contribution discard and session-local refusal without damaging normal
tooltip sections. Its single warning is intentional.

Reports and process logs are under
`RuntimeTests/NeoForge/build/reports/aquaculture-tooltips/<version>/`; disposable
runs are under that project's `build/run/aquaculture-tooltips/<version>/`.
Each phase requires its report and no failure marker, with a 240-second process
bound. No screenshots are required; ordinary-client visual acceptance remains
separate from structured tooltip checks.
The owner confirmed the final native/mixed text, Shift, source placement/F3+H
and Spanish presentation checks on 2026-10-27, closing the visual handoff.

## Oritech Exo fall recognition

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyOritechFall :MismatchedBoots:RuntimeTests:NeoForge:verifyOritechFallAbsent --console=plain
```

The opt-in server gate hash-checks the installed Oritech 2.0.0-exp6, GeckoLib 5.5.2
and Athena 4.7.3 and copies them into isolated versioned build directories. Native
damage dispatch checks Exo, mixed/inverse/two-Exo roots, repeated falls, non-fall
damage, wrong slots and malformed/spoofed roots. Native sound-event observation
checks one servo cue per protected landing with unchanged parameters. Source
provenance is retained. The later shared equip-sound rule is asserted in both
Exo + vanilla orientations: both roots select Exo's sole custom sound. Absent runs
without these mods and checks an ordinary inverse mixed pair still takes damage.
There is no replacement Oritech handler or production damage listener.

Commands can use `-Pneoforge_version=26.1.2.99` for the support floor. Reports and
markers are under `RuntimeTests/NeoForge/build/reports/oritech-fall/<version>/`;
disposable runs are under that project's `build/run/oritechFall/<version>/`.
Both processes are bounded and require report/success markers. The
[implementation record](../local-docs/ORITECH-COMPATIBILITY-EXPLORATION.md#7-local-fall-predicate-implementation--2026-10-18)
owns executed results and the accepted ordinary-client checklist: server sound
events are not client audibility or general sound-composition acceptance.
Guard units (three each), native damage (15 cases each) and absent controls (two
each) passed on .114/.99 on 2026-10-18; .114 compilation/assembly and packaging
were restored and verified. Optional-incompatible instruction controls are units,
not a separately claimed real competing-mixin run. Upstream Oritech resource
warnings/data-map error are recorded separately. The owner confirmed all final
manual checks passed on 2026-10-18, accepting this bounded fall-protection and
landing-feedback fix (section 7.2). This is separate manual evidence, not an
inferred .99/.114 manual matrix or acceptance of general equip-sound composition.
The later [shared equip-sound record](../local-docs/EQUIP-SOUND-COMPOSITION.md#5-ordinary-client-handoff--accepted)
separately records the owner's successful manual confirmation on 2026-10-18,
closing that bounded change without adding an automated run or manual loader matrix.

## AllTheModium boot recognition

```powershell
.\gradlew.bat :MismatchedBoots:NeoForge:test --tests '*AllTheModium*' :MismatchedBoots:RuntimeTests:NeoForge:verifyAllTheModium --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyAllTheModiumChains --console=plain
```

Use `-Pneoforge_version=26.1.2.99` for the supported floor. Pinned AllTheModium,
All the Ores and GeckoLib are resolved and hash-checked by the maintained artifact
catalog, never copied from indev. Live/Absent are separate disposable servers;
Chains adds a real cooperating native-slot return modifier. The fixture observes
native fall/fire events, the fire-clearing side effect, piglin hooks and powder-snow
decisions; it does not measure physical movement, health loss or natural piglin AI.
Three tiers/both orientations, native/vanilla controls, wrong slots, malformed
roots, unchanged equipment/history and item-component policies are covered.
No game ERROR or own compatibility-refusal warning is allowed; Absent also
rejects missing optional-target diagnostics. Reports/process logs are under
`RuntimeTests/NeoForge/build/reports/allthemodium/<version>/<mode>/`; disposable
worlds/mods are under that project's `build/run/allthemodium/`.
The [integrated record and owner checklist](../local-docs/ALLTHEMODIUM-COMPATIBILITY-EXPLORATION.md)
separate technical evidence from final ordinary-client acceptance.
On 2026-10-26, 2 guard units and Live 37/Absent 5 cases passed on .99/.114;
.114 additionally passed Chains 37 cases with 629 cooperating return observations.
The default .114 output was restored; shared Undergarden/SG snow regression,
focused units, packaging/negative controls and pinned artifact checks passed.
The owner separately confirmed all four ordinary-client gameplay groups passed
on 2026-10-26, completing the bounded boot review. This acceptance does not add
an automated run or a manual loader-version matrix.

## Undergarden boot recognition

```powershell
.\gradlew.bat :MismatchedBoots:NeoForge:test --tests '*UndergardenGooShapeTest' :MismatchedBoots:RuntimeTests:NeoForge:verifyUndergarden --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyUndergardenGuards --console=plain
```

Use `-Pneoforge_version=26.1.2.99` for the support floor. The native/absent
aggregate uses disposable versioned servers; the guard aggregate runs actual
compatible result peers and a changed native holder predicate. Live variants
hash-check Undergarden 0.9.8 and Silent Gear 4.2.3/Silent Lib 11.2.0, the latter
for a real Snow Walker regression at the shared hook. Absent loads none of them.

The fixture pays through the Workbench for both orientations, checks strict
root boundaries and unchanged whole stacks/history, invokes real thin/full goo
handlers on players and a non-player control, and observes the resulting native
stuck multiplier. Gooey and airborne/wrong-slot controls remain native. Real
powder-snow decisions and surface/crouch collision checks cover Froststeel,
ordinary/leather controls and SG Snow Walker. This is native-handler/collision
evidence, not natural client movement, tooltip or visual acceptance.

Reports and logs are under `RuntimeTests/NeoForge/build/reports/undergarden/<version>/<mode>/`;
disposable worlds/mods are under that project's `build/run/undergarden/`.
Each native process retains exactly one known upstream SG ERROR for
`silentgear:sapling/netherwood` referencing absent `bonsaitrees2:sapling`;
it also occurs in pre-existing SG-only logs. The gate admits only that exact
diagnostic, no other ERROR; Absent requires zero. Refused requires exactly one
own goo warning and verifies independent Froststeel/SG snow behavior and native
positives. Normal/Chains/Absent require no own goo refusal; Absent also rejects
missing optional-target warnings. The
[integrated implementation record](../local-docs/UNDERGARDEN-COMPATIBILITY-EXPLORATION.md#8-production-execution-record--2026-10-23)
owns executed matrix results and the separate manual checklist.
On 2026-10-23, four pinned guard units and Live 117/Absent 3 cases passed on
.99/.114; .114 also passed Chains/Refused 117 cases each. The owner separately
[confirmed all five manual checks](../local-docs/UNDERGARDEN-COMPATIBILITY-EXPLORATION.md#84-owner-manual-acceptance-and-closure--2026-10-23)
on 2026-10-23, including movement, source description and visual presentation,
closing the reviewed scope without a new automated run or manual loader matrix.
Default .114 compilation/tests and loader assemblies were restored; dependency
checks, all 12 corrupted-artifact negatives and production archive isolation passed.

## Shared tooltip separators

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyTooltipSeparators :MismatchedBoots:RuntimeTests:Fabric:verifyTooltipSeparators --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyTooltipSeparators '-Pneoforge_version=26.1.2.99' '-PtooltipSeparatorPlainOnly=true' --console=plain
```

These maintained commands observe production, not the
[earlier experiment](../local-docs/TOOLTIP-SEPARATORS-EXPLORATION.md). The
[implementation record](../local-docs/TOOLTIP-SEPARATORS-IMPLEMENTATION.md) owns dated
technical results and the separate owner checklist. No executable spike aliases
remain. `tooltipSeparatorPlainOnly` restricts the gate to Seed/Plain and disables
screenshots; the property is forwarded to its bounded child Gradle processes.
The formatter, shared solid row and loader conversions belong to production;
the fixture contains scenarios and read-only `extractImage` observers. Two narrow
scenario input hooks emulate another mod copying all tooltip text or supplying
the actual native `BundleContents` image from a bundle. No test-owned separator
marker, factory or renderer is present; bundle drawing also remains native.
Test marker insertion, drawing, gather/factory and conversion replacements were
removed. Each task generates an isolated seed world and runs Plain and Legendary
clients, hidden and muted. The dedicated seed server must also collect real
production mixed tooltips and emit its own success marker.
The three optional presentation JARs are downloaded and hash-checked using the
corresponding loader's test artifact catalog; no indev JAR is required and no
owner config/world is copied or edited.

Six scenes per client check primary/inverse/advanced/ordinary controls, width
changes/resizing, unchanged text/root/history, multiline source boundaries,
blank-gap fallback after text copying and coexistence/order/non-overlap with
the native bundle image. They also require advanced-only dark-gray source IDs
directly below their names, exactly once per source. Real Iceberg wrapping
retains both Fabric markers.
Plain Fabric's overlong unwrapped-lore overflow is retained, not fixed.
Reports, strict ERROR gates and one relevant screenshot per client are under
`<loader>/build/reports/tooltip-separators/<baseline>/{plain,legendary}/`, with wrapper
logs beside those directories. Runs/worlds stay under
`<loader>/build/run/tooltipSeparators/<baseline>/`. The baseline is `26.1.2` on
Fabric and the full NeoForge version on NeoForge, preserving floor/default
evidence separately. One focused baseline capture combines multiline spacing
and graphical coexistence; the .99 plain-only smoke makes no screenshots.
Historical spike/A commands and paths in dated records are not current entry
points. Owner visual acceptance is separate from this technical gate.
The owner [approved that visual handoff on 2026-10-23](../local-docs/TOOLTIP-SEPARATORS-IMPLEMENTATION.md#73-owner-visual-acceptance--2026-10-23),
retaining the implemented spacing/tone and closing the bounded plan. Recording
that confirmation did not require another automatic run or screenshot suite.

## Item geometry coverage

The opt-in [whole-model fallback visual trial](../local-docs/ITEM-FALLBACK-ROTATION-EXPLORATION.md)
runs only the hybrid structural suite and five task-specific fallback captures:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:Fabric:runClientGameTest -I mods/MismatchedBoots/RuntimeTests/Fabric/item-fallback-visual.init.gradle --console=plain
```

It declares otherwise ordinary boot geometry unsupported through the test probe,
then exercises production fallback in both hands and an unsafe GUI pose, plain
and enchanted. Native boots are GUI size controls. Generated metadata is restored
by a finalizer; the normal client suite does not run these opt-in visual scenes.
Genuine special-renderer fallback is checked in every display context and both
orientations independently of that visual forcing.
Actual data-less mixed stacks (plain `/give`, no forced failure) also exercise
the declarative item fallback in every display context and both held slots,
with and without foil; they appear as the extra GUI column. This separate path
must not be inferred to work from the valid-pair fallback checks alone.

Item source identities remain anatomical: source left owns the canonical/viewer
right half and source right owns the left half in the front-view convention.
This mapping also applies to screen scissors and whole-model yaw, while stored
source/submission order and equipped legs remain unchanged. Item-level ownership
and pixel assertions test that mapping; pure clipping algorithms retain ordinary
negative-X LEFT / positive-X RIGHT semantics.

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
