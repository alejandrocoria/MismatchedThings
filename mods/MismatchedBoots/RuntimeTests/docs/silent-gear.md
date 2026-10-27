# Silent Gear runtime tests

Use the shared [execution, path and artifact rules](../README.md) before running
these gates. All commands run from the repository root, not this directory;
non-command paths below remain relative to `mods/MismatchedBoots/` unless stated
otherwise. Explicit file-path `-P` overrides remain repository-relative or absolute.

## Index

- [Silent Gear native stat measurement](#silent-gear-native-stat-measurement)
- [Parts and recipes audit](#parts-and-recipes-audit)
- [Source tooltips](#source-tooltips)
- [Default attribute and state controls](#default-attribute-and-state-controls)
- [Wearer effects](#wearer-effects)
- [Brilliant and Moonwalker recognition](#brilliant-and-moonwalker-recognition)
- [Magnetic and Greedy](#magnetic-and-greedy)
- [Heat Resistant and Bounce](#heat-resistant-and-bounce)
- [Void Ward, Snow Walker and cures](#void-ward-snow-walker-and-cures)
- [State faults and JDT coexistence](#state-faults-and-jdt-coexistence)
- [Run and report paths](#run-and-report-paths)
- [Connected lifecycle and persistence](#connected-lifecycle-and-persistence)
- [Known native diagnostics and acceptance records](#known-native-diagnostics-and-acceptance-records)

## Silent Gear native stat measurement

Opt-in native controls and **production attributes/state** acceptance, not full
SG compatibility. Native jars are neither compile nor publication dependencies.
The production entry point is now `verifySilentGear`; the former
`verifySilentGearStatsSpike` and `verifySilentGearFoundationAbsent` names were
retired, not retained as aliases. Run Gradle tasks sequentially:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGearLifecycle --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGearAbsent --console=plain
```

Repeat with `'-Pneoforge_version=26.1.2.99'` for the supported floor; restore the
ordinary .114 artifacts before packaging. Preparation resolves and verifies
Silent Gear 4.2.3 and Silent Lib 11.2.0 from the test artifact catalog.
Only disposable fixture-project build directories
are cleared; development clients/mods/configs/worlds stay untouched.
No new dependency of `build`/`check` is installed.

## Parts and recipes audit

For the separate **diagnostic-only** parts/recipes investigation (no candidate
adapter), run:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear '-PsilentGearEffectVariant=parts-audit' --console=plain
```

Repeat with `'-Pneoforge_version=26.1.2.99'`, then restore .114. This scope records
27 report lines: real paid native lining, nine native/mixed crafting controls,
two paid native unchanged-output smithing controls, four safe mixed smithing
refusals, iron/copper production recomposition, ordinary donor flags, six
anvil **preview observations** (including native material/upgrades and mixed
rejections), and raw native/root tag memberships. Reports are under
`build/reports/silent-gear-effects/<version>/parts-audit/`. It does not implement
part editing, execute paid refunds/salvaging or claim manual/connected acceptance.
Details and proposed boundaries belong to the
[parts/recipes exploration](../../local-docs/SILENT-GEAR-PARTS-AND-RECIPES-EXPLORATION.md).

## Source tooltips

The separate **production source-tooltip acceptance**, without extra source
headings, has three isolated scopes:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGearTooltip --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGearTooltip '-PsilentGearTooltipVariant=binding' --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGearTooltip '-PsilentGearTooltipVariant=formatter' --console=plain
```

It reuses the disposable remaining-effects flat-world seed/preparation and its
versioned run directories, but a different client driver. Repeat all three with
`'-Pneoforge_version=26.1.2.99'`. Durable logs/reports are under
`build/reports/silent-gear-tooltip/<version>/<scope>-*`; transient markers are
`silent-gear-tooltip-<scope>-*` in the remaining-effects report directory.
Run sequentially, not concurrently
with `verifySilentGearRemaining`; preparation replaces those build-owned paths.
The hidden/muted connected client adds the pinned JDT artifact for coexistence
and compares native structured components against source insertion on ten paid
Workbench roots. The research source appender, mutable flag bridge, candidate
and old spike task alias are removed. A fixture-only suppression hook supplies the no-SG-block baseline;
an independent native gear-event control supplies expected components, not a
second candidate renderer. Production itself never replays the native event.
Controlled window polling exercises actual SG keybindings,
both native tooltip styles, rebinding, advanced mode, compact/expanded visibility,
Broken/missing/malformed cache, root/JDT preservation and a recalculation witness.
JDT's shared controlled Shift support separately proves actual compact/expanded
root energy precision, hint/ability output and source suppression. The normal
scope expects exactly one bounded malformed-source diagnostic and continued
healthy-source presentation. Binding and post-population formatter faults each
expect exactly one presentation-disable diagnostic, no partial block or repeated
attempt, and intact root/JDT details and an independent completed SG trait query.
These are narrow tooltip faults, not gameplay guard compatibility or transaction
rollback tests. No physical OS input or screenshot/visual acceptance is claimed.
Results and owner handoff belong to the
[implementation record](../../local-docs/SILENT-GEAR-TOOLTIP-IMPLEMENTATION.md#8-block-b-execution--2026-10-21);
the preceding [spike evidence](../../local-docs/SILENT-GEAR-TOOLTIP-EXPLORATION.md#7-bounded-client-spike--2026-10-21)
remains historical. Restore habitual .114 artifacts after a .99 run.

The owner separately accepted all manual groups on **2026-10-21**, including tall
tooltips readable at **1080p with GUI scale 2**, completing the bounded tooltip
plan. That report does not turn these automated gates into pixel/physical-input
tests or certify every scale/resolution.

## Default attribute and state controls

The default server gate retains native construction/lining and equipment
controls for five material profiles, with eight representative paid output
measurements (lined/vanilla, leather/vanilla, SG/SG and identical).
It additionally verifies:

- Light/Moonwalker/Flutter numeric contributions on nine equipped native/mixed
  subjects, including native exact duplicates and configured mixed arithmetic.
- Production initialization, inverse orientation, codecs, supplied policies,
  immutable provenance, paid-preview refusals and explicit state/lane absence.
- Native Renew/Stellar repair and Flexible/Malleable/Sturdy/Bending/Brittle
  wear via real mixed callbacks, independently seeded rolls, Unbreaking,
  creative/Unbreakable, actual armor hits and normal destructive mixed breakage.
  Bending is an explicitly supplied native instance, not a stock boot recipe.
- Next-tick Chipping/Crushing armor under average/maximum/separate policies;
  real wear and XP Mending, changed root maximum, persistent external overrides,
  unrelated metadata and unchanged-tick/query no-write/no-recalculation checks.
- Unequal/coated/already-oxidized/pending oxidation lanes and selected/idle
  eligibility against independently evolving native items. Six accelerated
  native boundaries and an unseeded 50,000-call timeline retain the native
  repeated Oxidized-I reset. The latter is synchronous callback simulation,
  not elapsed server ticks. A supplied terminal native phase checks pause,
  not stock copper reachability.

The superseded mutable-state writer/CUSTOM_DATA lanes and executable wearer
`research` variant are retired. Production replaces the old Brilliant/Moonwalker/
Wielder candidates; independent native helpers and the recalculation witness
remain available without a test-only recognition engine.

## Wearer effects

The separate [wearer plan](../../local-docs/SILENT-GEAR-WEARER-IMPLEMENTATION.md)
introduced A's optional binding and paired-predicate proof. C now promotes the
same fixture scopes to production Wielder acceptance, retaining the native loop
and its two local predicate result extensions. B's recognition scopes are below;
D extends the same lifecycle/coexistence gates below; the owner accepted all
final manual checks on 2026-10-20, separately from automation.

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=wearer --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=wearer-compatible --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=wearer-type --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=wearer-trait --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=wearer-effect --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=wearer-definition --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=wearer-throw --console=plain
```

`wearer` extends the maintained native fixture with independent Adamant/Aquatic/
Flame Ward/Stellar definitions, native 0..4-piece controls and both paid output
orientations. It checks actual mixed item ticks, native-chest callers, direct
carrier binding controls, hands versus idle, ten-tick cadence, exact potion data,
natural expiry, unchanged gear and a calibrated native recalculation tripwire.
Duplicate/different sources and held copies count no imaginary armor members;
seeded Stellar heal/nonheal occurs once alongside potions. Actual server-player
ticks check continuous refresh followed by expiry in idle inventory, not a connected
client/restart. Brilliant/Moonwalker production acceptance is separate;
their old candidate checks are retired. `wearer-compatible` adds an independent HEAD result
extension; `wearer-type` / `wearer-trait` alter one native predicate each and
require the complete pair plus optional dispatch to refuse before actor writes.
`wearer-effect` preserves native actor behavior through an uninspected alias and
requires refusal; `wearer-definition` changes an admitted definition and proves
no publication/retry, retention of an existing potion and revocation of both
mixed predicates. Each refusal logs once, preserving B and representative prior
writers; the full previous state matrix stays on the ordinary gate.
`wearer-throw` (D) lets a test actor publish native Speed/Jump effects and then
throw; it checks retained actor effects, no replay across later mixed ticks,
revoked native-caller mixed set counting and independent B/repair/state behavior.
Native callers retain their ordinary behavior under refusal. Native material
construction without available ingredients is explicitly labeled; an actual
iron/prismarine coating recipe supplies the obtainable Aquatic control.

Reports: `RuntimeTests/NeoForge/build/reports/silent-gear-effects/<version>/<variant>/`;
child logs: `RuntimeTests/NeoForge/build/reports/silent-gear-stats-<version>-<variant>-process.log`.
Append `'-Pneoforge_version=26.1.2.99'` for the supported floor; restore ordinary
.114 afterward. [C's record](../../local-docs/SILENT-GEAR-WEARER-IMPLEMENTATION.md#10-block-c-execution-record--2026-10-20)
owns current production evidence/limits; A's earlier record is historical foundation
evidence, not the current scope of these commands.

## Brilliant and Moonwalker recognition

B's production recognition scopes use the same gate with research recognition off:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=recognition --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=recognition-compatible --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=recognition-brilliant --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=recognition-moonwalker --console=plain
```

These exercise both paid orientations, actual vanilla/native piglin armor checks,
the registered native fall event, maximum source/native-armor levels, ordinary
true results, unequipped/invalid/plain roots, cancellation/short falls and native
Bounce isolation (rebound suppressed on the mixed Moonwalker/Bounce pair since
defensive C; C's own matrix below owns rebound/cost acceptance). Complete gear
snapshots and a calibrated native-recalculation
witness protect the read-only calls. `recognition-compatible` adds a real chained
query-result mixin with controlled optional-query values 2/5, **not actual Curios
runtime acceptance**. The last two scopes replace one native consumer each,
requiring one bounded diagnostic and independent survival of the other feature,
seeded repair, native-compared dynamic armor and a copper clock advance. Full old
state matrices remain on the ordinary gate rather than repeating in each scope.
Independent C Wielder remains functional. Output locations/floor flag are the
same as A; [B's record](../../local-docs/SILENT-GEAR-WEARER-IMPLEMENTATION.md#9-block-b-execution-record--2026-10-19)
owns exact executed technical evidence and the separate owner manual checklist.
The owner accepted B's Brilliant/Moonwalker smoke on 2026-10-20, including the
native Moonwalker advancement. D's automated lifecycle/consolidation and the
owner Wielder/lifecycle/JDT checklist accepted on 2026-10-20 are in
[its record](../../local-docs/SILENT-GEAR-WEARER-IMPLEMENTATION.md#11-block-d-execution-record--2026-10-20).

## Magnetic and Greedy

The [Magnetic/Greedy production gate](../../local-docs/SILENT-GEAR-MAGNET-IMPLEMENTATION.md#8-block-b-execution-record--2026-10-20)
uses actual mixed-item inventory ticks against independent native controls.
The former `magnet-spike` command/candidate is retired:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=magnet-server --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=magnet-compatible --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=magnet-effect --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=magnet-queue --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=magnet-definition --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=magnet-throw --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=magnet-reload --console=plain
```

Run sequentially; repeat with `'-Pneoforge_version=26.1.2.99'` for the floor. It uses native
installation/Blaze Gold controls, actual native inventory callbacks and real
server ItemEntities, publishing impulses through the native level-post tracker.
Both paid inverses cover selected/idle, crouch config, radius/filter/pickup-delay/
anti-movement controls, duplicate/unequal levels and overlapping held/worn
copies. A scoped counter at our binding's private invocation boundary catches
double dispatch hidden by native
strongest-vector arbitration; gear snapshots and the calibrated recalculation
witness protect root/source/carrier/equipment. The callback counter mixin is
enabled only in `magnet-*` scopes and never patches the admitted native body.
`magnet-compatible` adds harmless methods/NOPs and renames real lambda targets;
`magnet-effect`/`magnet-queue` preserve native behavior through separate unproved
aliases, requiring one bounded refusal each before guarded calls publish work.
Native and existing feature controls survive. `magnet-definition` rejects a
whole batch before its valid first trait runs; `magnet-throw` observes one owned
call before a post-queue exception, preserving disjoint native-peer/owned requests
without retry or cleanup. `magnet-reload` completes four asynchronous datapack
reloads on pre-existing roots: live strength/tag changes, stale receiver rejection,
invalid definition revocation and no silent rearming after a valid reload.
Only `magnet-server` additionally loads the pinned JDT artifact for scoped FE/
master/settings/binding/cooldown/projection coexistence; other variants stay SG-only.
No connected-client motion, real-time pickup, lifecycle or mixed
upgrade-editing acceptance is implied. Reports use the existing
`silent-gear-effects/<version>/<magnet-variant>` directories, not another runner.
The subsequent [A/B/C production plan](../../local-docs/SILENT-GEAR-MAGNET-IMPLEMENTATION.md)
has A/B/C implemented. C's [record](../../local-docs/SILENT-GEAR-MAGNET-IMPLEMENTATION.md#9-block-c-execution-record--2026-10-20)
owns connected motion/pickup and save/rejoin via the existing lifecycle command
below. The owner separately confirmed all five manual groups on 2026-10-20,
completing this bounded magnet plan; [section 9.4](../../local-docs/SILENT-GEAR-MAGNET-IMPLEMENTATION.md#94-owner-manual-acceptance--2026-10-20)
records manual acceptance independently from automated evidence.

## Heat Resistant and Bounce

The bounded [Heat Resistant/Bounce exploration/spike](../../local-docs/SILENT-GEAR-DEFENSIVE-EFFECTS-EXPLORATION.md)
is historical: all candidate gameplay methods/mixins and the `defense-spike`
entry point are retired. Production A/B/C use the same pinned optional native
gate and bounded runner. Append `'-Pneoforge_version=26.1.2.99'` for the supported
floor. Reports remain in `build/reports/silent-gear-effects/<version>/<variant>`.
The two old deliberate native-writer EventBus errors are not expected results.

Production [A's contracts/native-peer wear](../../local-docs/SILENT-GEAR-DEFENSIVE-EFFECTS-IMPLEMENTATION.md#7-block-a-execution-record--2026-10-20)
use `verifySilentGear -PsilentGearEffectVariant=defense-foundation`, or
`defense-compatible`, `defense-fall`, `defense-incoming`, `defense-negate`,
`defense-retaliation`. The same .99 floor flag applies. These variants do not
enable research hooks: they check guarded direct native math/admission, separate
final transformed proofs and exact production wear on both SG/vanilla and
entirely vanilla mixed orientations across real native damage bands. Creative,
Unbreakable, normal breakage and malformed mixed targets retain safe behavior.
One unproved fall-charge alias refuses before queued motion/sound/payment, with
exactly one bounded diagnostic; native boots and independent Moonwalker remain
available. Other aliases/predicate changes refuse only their corresponding
incoming/negate/retaliation proof. Harmless edits, renamed native lambdas and an
independent controlled optional-query contribution remain admitted (not an
installed Curios test). Reports reuse the existing versioned effect directories.
B's [production incoming record](../../local-docs/SILENT-GEAR-DEFENSIVE-EFFECTS-IMPLEMENTATION.md#8-block-b-execution-record--2026-10-20)
extends foundation/compatible and incoming/negate refusals with native attacking-
tool/common-base arithmetic, max/duplicate traits, real health outcomes, and
unchanged equipment/source/carrier snapshots. New `defense-data`, `defense-throw`
and `defense-reload` variants check independent unsupported Heat refusal vs
working Bounce, atomic mixed-piece reduction after a controlled post-native
failure and real asynchronous datapack/scale/tag reloads on existing roots.
Their expected warnings are bounded by the Gradle gate. Incoming candidate hooks,
dispatcher, selector and duplicate spike incoming assertions are retired.

C's [production Bounce record](../../local-docs/SILENT-GEAR-DEFENSIVE-EFFECTS-IMPLEMENTATION.md#9-block-c-execution-record--2026-10-20)
uses these scopes (not another runner):

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=defense-bounce --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=defense-bounce-compatible --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=defense-bounce-query --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGear -PsilentGearEffectVariant=defense-joint --console=plain
```

Primary/compatible scopes check real stock slime lining, paid inverse/duplicate
sources, native/optional higher query contributions, native sound parameters,
one root wear bill and actual next-server-pre-tick additive motion. Malleable,
Unbreaking, zero-bill Moonwalker, exemptions/destruction and actual post-hit
knockback/projectile/environmental negatives retain their native/ordinary
owners. No Curios installation or physical connected-client landing is claimed.
The recognition fault leaves native-peer safe wear available; existing A/B
refusals also check independent fall/retaliation behavior. `defense-joint` adds
only pinned JDT, observes real registered handlers and checks exact FE/wear and
complete root/source/settings/tables, including live JDT controls when Bounce
is suppressed. [D's record](../../local-docs/SILENT-GEAR-DEFENSIVE-EFFECTS-IMPLEMENTATION.md#10-block-d-execution-record--2026-10-20)
owns connected gameplay/rejoin and its separately recorded automated/manual exits.
The owner accepted all five ordinary-client groups on 2026-10-20, completing the
bounded Heat Resistant/Bounce plan; this is separate from the .99/.114 automated
matrix and does not close broader Silent Gear compatibility.

## Void Ward, Snow Walker and cures

The remaining-effects gate now uses **production Void Ward, Snow Walker and
Cure Poison/Wither**, with no research gameplay fallback:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGearRemaining --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGearRemaining '-Pneoforge_version=26.1.2.99' --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGearRemaining '-PsilentGearVoidVariant=compatible' --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifySilentGearRemaining '-PsilentGearVoidVariant=refused' --console=plain
```

It creates a fresh SG/Silent Lib-only server seed and one hidden/muted integrated
client under `build/run/silentGearRemaining/<version>`, not owner worlds or the
production lifecycle world. The gate requires seed/success markers, no failure
marker and 23 report lines under `build/reports/silent-gear-remaining/<version>`:
12 synchronous production Void Ward plus 25 cure controls, then 3 connected rescues,
11 natural powder-snow collision scenes and 5 ordinary inventory-cancellation
scenes, actual definition reload and independent Poison-target refusal.
Sources/complete stacks are checked, native mixed-root recalculation is
guarded by a calibrated witness, and cure acquisition is explicitly synthetic.
The former query/item candidates and gameplay switch are removed. Test-only
witnesses count native rescues, selected mixed ticks and production cure dispatch;
they do not change collision, remove potions or modify the guarded native cure
method. The normal client expects exactly one deliberate Poison-definition
warning, no seed/other adapter warnings, and no retry after restoring that target.
The `compatible`/`refused` variants run **Void-only** matrices in sibling
`<version>-void-compatible`/`<version>-void-refused` directories and require 5/3
lines, respectively. Repeat them with `-Pneoforge_version=26.1.2.99` for the floor.
The first preserves proof through a same-result identity peer and unrelated
edits; the second aliases the native query without changing its result, declines
only mixed Void Ward, requires one bounded warning and verifies native rescue
plus completed Brilliant/Moonwalker. Other adapter warnings are rejected.
[A's production record](../../local-docs/SILENT-GEAR-REMAINING-EFFECTS-IMPLEMENTATION.md#6-block-a-execution-record--2026-10-21)
owns the executed matrix; the [original spike](../../local-docs/SILENT-GEAR-REMAINING-EFFECTS-EXPLORATION.md#9-bounded-connected-spike--2026-10-21)
is historical. [B's production record](../../local-docs/SILENT-GEAR-REMAINING-EFFECTS-IMPLEMENTATION.md#7-block-b-execution-record--2026-10-21)
owns Snow Walker/cures and their .99/.114 checks.
[C's consolidation record](../../local-docs/SILENT-GEAR-REMAINING-EFFECTS-IMPLEMENTATION.md#8-block-c--consolidation-lifecycle-and-manual-handoff)
retires the `*RemainingSpike` class/task/property names without aliases. The
current driver/helper are `SilentGearRemainingClientTest`/`SilentGearRemainingFixture`;
only optional witnesses remain. Process logs now end in `-seed`/`-client`, not
`-spike`. Magic Armor is outside this gate.

The existing `verifySilentGearLifecycle` gate also saves **12** barrel roots:
the original nine plus three damaged, actually used Ward/Snow Walker/distinct-cure
pairs. Restart compares their complete data before invoking callbacks. Client
and separate-JVM Rejoin each reuse the normal driver's observation/Session code
for only three scenes: native rescue motion/potions, natural powder-snow support/
descending, and distinct-cure cancellation/effect packets. Player slots 13–15,
same UUID and complete used roots are compared before other scenes clear the
test inventory; no recrafting/reseed is allowed. Each phase requires three
`silent-gear-remaining-{client,rejoin}-report.txt` lines plus the player manifest.
Existing state/wearer/magnet/defensive lifecycle evidence remains required.

`silent-gear-remaining-manual.txt` in the version-qualified lifecycle report
directory contains three full `/give @p` source commands, verified through the
vanilla parser and production Workbench/cure dispatch. Paste into a command
block in a **test world** (longer than normal chat), activate near the player,
then combine the supplied original boots. Synthetic cure sources are not stock
survival recipes; no production debug command or native resource change is added.

The owner confirmed all four ordinary-client manual groups passed on 2026-10-21:
Void Ward, mixed Snow Walker/descending, single/distinct cure selection and actual
save/rejoin. This accepts C and completes the bounded remaining-effects plan;
automated evidence, native limitations and broader SG scope remain separate.

## State faults and JDT coexistence

Additional variants of `verifySilentGear` use
`-PsilentGearEffectVariant=repair`, `wear`, `compatible`, `state` or `joint`:

- Repair-consumer replacement and wear sum-to-subtraction must actually transform
  native classes, refuse twice with exactly one dependent-feature warning, and
  retain the other effect. Harmless NOP edits run B with zero warnings.
- `state` computes a valid first-lane transition then refuses the invalid second
  lane atomically with one diagnostic; B repair/wear still work afterwards.
- `joint` adds pinned JDT 1.6.11: native armor/Step arithmetic, depleted/funded
  FE, damage/repair and Step off/on, one shared identity, preserved table groups
  and overrides, no extra FE bill or FE-driven SG recalculation. D also checks
  both Stellar/Eclipse orientations under depleted/funded FE and JDT global
  off/on: native wearer effects remain independent, without writes to FE,
  controls, tables, SG state or sources, while native Step arithmetic is retained.
  Pure-JDT regressions remain `verifyJustDireAttributes` and
  `verifyJustDireResources`.

## Run and report paths

Default run/report roots remain `build/run/silentGearStats/<version>` and
`build/reports/silent-gear-stats/<version>`; scoped variants use
`silentGearEffects/<version>/<variant>` and
`silent-gear-effects/<version>/<variant>`. Process logs are siblings named
`silent-gear-stats-<version>[-<variant>]-process.log`.
The SG-absent task uses the default roots' `absent` subdirectories and a
`silent-gear-absent-<version>-process.log`. It checks paid ordinary outputs,
neutral callbacks and our own state codec without native classes.
Run it after the default gate if both sets of reports should remain.

## Connected lifecycle and persistence

`verifySilentGearLifecycle` sequentially launches Save, Restart, a hidden/muted
integrated Client, and a separate Rejoin client JVM. The second JVM compares twelve complete actual-world
barrel stacks before callbacks: unequal transitioned copper inverses, worn
quartz inverses with captured maximum policy, one explicit armor removal and
foreign luck/name, damaged Stellar/Brilliant inverses and Moonwalker/copper
inverses plus one native grid-upgraded Magnetic V + diamond root, and three used
Void Ward/inverse Snow Walker/distinct synthetic-cure roots described above.
It checks no offline progress/reseeding, selected pauses,
continuation, native worn arithmetic, paid trim/crafting repair and policy
recapture only on recomposition; wearer effects, one seeded Stellar heal,
Brilliant recognition and Moonwalker resume on the saved inverses.
The actual connected client checks complete
item/component/source packets and vanilla equipped attributes, wear refresh,
hand/offhand/idle moves, paid trim/repair and read-only client queries.
Both wearer inverses additionally exercise real refreshed potion packets and
local jump against an independent selected-native-item control, offhand refresh
and natural idle expiry. Actual server-player tick progression is checked, with
focus pause disabled. A damaging vanilla fall control calibrates equal native/
mixed Moonwalker health loss; composed gravity and its removal arrive normally.
Fixture Unbreakable markers freeze incidental repair during packet comparisons;
ordinary seeded healing is checked separately, including after player rejoin.
Only JSON expectations cross threads, not shared ItemStack references.
Rejoin retains the actual client world/player save and fixed test username, checks
different PID/same UUID and complete saved roots/damage/selected copper clocks,
then resumes effects/one heal and normal idle expiry. It does not recopy the
original barrel world or recraft the roots.
The same driver now adds seven ordinary native/mixed Magnetic/Greedy scenarios:
server and client movement, natural pickup of exactly three diamonds and four
gold ingots, two rejected dirt, normal 60-tick pickup delay and selected-to-idle
stop (native and mixed). FEET/main/offhand and both Greedy paid inverses are
represented; B retains exact vector/radius/duplicate arbitration coverage.
No fixture queue flush, magnet call, client velocity write, item teleport or
explicit pickup is used. The actual used Magnetic root is saved in player slot
11 and compared through its decoded complete ItemStack in Rejoin before ordinary
attraction/pickup resumes without crafting. Client and Rejoin additionally require
`silent-gear-magnet-client-report.txt` (seven scenarios) and
`silent-gear-magnet-rejoin-report.txt` (one), in the same lifecycle reports folder.
The driver also includes the pinned JDT 1.6.11 fixture for four defensive
landings: native slime-lined Crimson Iron, both mixed/diamond orientations and
an SG/Eclipse Alloy mixed pair with funded Negate and Ground Stomp/binding.
Initial positioning above a stone platform is followed by ordinary gravity and
registered native handlers: actual client/server rise, native sound-engine
dispatch, one authoritative server bill, complete used-root packets and immutable
histories are checked. No synthetic fall, manual queue drain, client velocity
write or teleport-as-rebound proof is used. Native local-client recognition is
enabled; client payment is not. Four further hurt/packet controls compare plain,
native and both mixed outputs for fire/wall damage and real zombie retaliation,
temporarily neutralizing actor numeric armor and freezing incidental item wear.
Goal-free attackers keep normal travel/tracking, unlike NoAI entities.
The actually worn SG/JDT root is saved in player slot 12 after its live state
settles. Another JVM compares its decoded full stack/same UUID before unrelated
magnet test cleanup, retains that actual loaded object and repeats ordinary
landing/rebound/sound/wear. It never substitutes a barrel copy or recrafts it.
The gate additionally requires `silent-gear-defense-client-report.txt` (four),
`silent-gear-defense-health-report.txt` (four),
`silent-gear-defense-rejoin-report.txt` (one) and `silent-gear-defense-player.json`.
This is integrated transport, not a separate dedicated-server client matrix
or rendering/tooltip acceptance.

Lifecycle run/report roots are
`build/run/silentGearLifecycle/<version>/{server,client}` and
`build/reports/silent-gear-lifecycle/<version>`; process logs are siblings
`silent-gear-lifecycle-<version>-{save,restart,client,rejoin}-process.log`.
Save clears only its server/report tree. Restart reuses that world without
Save preparation; Client copies the completed world into its own disposable
save. Rejoin uses that Client save without cleanup/repreparation. Markers,
world/player manifests and reports record each phase independently.
Save also emits `silent-gear-wearer-manual.txt`: chat-sized vanilla commands for
Nickel/Refined Iron plates, with each actual native iron-armor main-part swap
and resulting Adamant/Stellar trait verified. They are explicit debug fixtures,
not stock ingredient acquisition or mixed-root part-editing support. The final
[manual checklist](../../local-docs/SILENT-GEAR-WEARER-IMPLEMENTATION.md#114-ordinary-client-checklist--accepted)
records the owner's acceptance on 2026-10-20 and explains usable native controls.

## Known native diagnostics and acceptance records

Native logs retain the known optional Bonsai recipe-serializer error. Client
logs also expose SG's own model/texture notices and missing stone-anvil subtitle
translations (confirmed in the pinned native resources), not adapter failures.
Exact executed versions, warning counts, packaging, manual acceptance and
remaining exclusions belong to the
[state implementation record](../../local-docs/SILENT-GEAR-STATE-IMPLEMENTATION.md).
The owner confirmed all five manual smoke groups on 2026-10-19, completing this
bounded attributes/state plan; that confirmation is separate from automated evidence.
Wearer/event traits, part editors, tag closure, magic-armor native availability
and SG-style tooltips are not closed by this attributes/state gate.
