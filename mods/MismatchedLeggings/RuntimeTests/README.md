# Leggings runtime tests

Small fixtures for the current standalone product and structural source schema. They compile against
the real loader JARs, then load only the packaged Leggings artifact from disposable
`build/run/<baseline>/<server|client>/mods`. Things comes from the native nested
dependency, not loose core classes or a separately staged core JAR. Boots and
indev mods/configuration/worlds are excluded.

The additional client profiles can also stage the **current built** Boots JAR
for coexistence, or an explicitly supplied unchanged published Boots binary.
Neither route reads the indev directory. A viewer compatibility check against a
published binary does not replace the broader final-product release-graph gates.

## Final packaged product acceptance

Run from the repository root, supplying the unchanged Boots 1.0.0 releases:

```powershell
.\gradlew.bat :MismatchedLeggings:verifyProduct '-PleggingsFabricBootsJar=<published-fabric-boots.jar>' '-PleggingsNeoForgeBootsJar=<published-neoforge-boots.jar>' --offline --console=plain
```

The inputs must match [the release pins](fixtures/boots-1.0.0.json). They are
copied without modification, not rebuilt or discovered in indev. See the
[provenance note](fixtures/README.md) before replacing a pin. Prepare the optional
viewer and Ice and Fire artifacts with their targeted preparation tasks online
before an offline run.

The bounded matrix runs sequentially on Fabric and NeoForge `.99`/`.114`:

- Common tests for all three modules, packaging and corrupted-artifact controls.
- Standalone core endpoints and its compiled-consumer/version-evolution matrix,
  including saved Boots state and the refused incompatible-core-major control.
- Standalone Leggings schema/composition, workbench, equipped and item profiles.
- Workbench, equipped/item rendering, presentation, repair, grindstone, smithing,
  recovery and separate JEI/REI profiles with unchanged Boots.
- The optional native Ice and Fire waist profile on `.114`, then final packaging
  and NeoForge Mixin binary checks. Normal outputs are left on `.114`.

This does not run Boots' historical third-party compatibility matrix. Loader
metadata remains responsible for native dependency selection; passing this matrix
does not imply cross-loader support. Clients obey the hidden/muted contract below.
Do not run profiles or baselines concurrently: they share generated launch files.

Evidence is written to `../build/verification/product/evidence.json`, with frozen
input copies, per-case logs, completed cases and final product/core JAR hashes.
`matrix.ok` is written only after the full matrix succeeds; a partial evidence file
is not acceptance. Child profiles retain their own detailed reports. Publication
tasks do not depend on this matrix.

For a targeted rerun, `-PleggingsProductCases=legacy-repair,legacy-grindstone`
selects those case names on every baseline. A fully qualified selector such as
`NeoForge/26.1.2.114/legacy-recovery` restricts one case to one baseline. Case names
are the labels printed during a full run. Final packaging always runs to restore
normal `.114` outputs; unknown/unmatched selectors fail verification. Focused
evidence goes to `../build/verification/product-focused/<selection-hash>/`, with
`coverage: focused` and `subset.ok`, never a full `matrix.ok`. It does not overwrite
full-run evidence. Do not describe a focused rerun alone as full-matrix acceptance;
record the earlier coverage and which changed boundaries were repeated.

Owner acceptance is separate: use copied packaged JARs in disposable instances,
not indev while this matrix runs. Check both loaders, both extraction orders and
close cleanup, both visual orientations and item contexts, vanilla enchantments
and workstations, save/rejoin and matching-loader dedicated play, controlled
source removal/reinstallation, and both products together with optional viewers.

## Silent Gear foundation (block A)

Maintained dedicated-server checks load packaged production Leggings and optional
current Boots, with pinned SG `4.2.3` / Silent Lib `11.2.0` from the shared resolver.
No research candidate or Wielder hook is enabled. Run profiles sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:NeoForge:test --tests '*SilentGearFoundationTest' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgFoundationGraph=boots --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgFoundationAbsent=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgFoundationGraph=boots '-Pneoforge_version=26.1.2.99' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:Common:test :MismatchedLeggings:NeoForge:test :MismatchedLeggings:verifyPackaging :MismatchedBoots:NeoForge:assemble --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:runSkeletonServer --offline --console=plain
```

Automatic coverage passed on 2026-11-09: four focused unit cases; `.114` native
standalone/current Boots and standalone absence; focused `.99` native/current
Boots; the ordinary Fabric `0.19.5` dedicated skeleton also passes without SG.
The final regression covers all 26 Common and 37 NeoForge cases, including the
four foundation cases. NeoForge's modded unit-test runner reads the pre-existing
Ice and Fire sibling Mixin witness directly from the actual Boots JAR; no sibling
production change or substitute bytecode is used.
Normal `.114` product outputs are restored afterwards. Fresh `server.ok`,
`foundation.txt`, `matrix.ok`, successful bounded child exit and no failure marker
are required. Logs/reports are under `NeoForge/build/{verification,reports}/<baseline>/sg-foundation-<graph>[-absent]/`.
Only the pinned native optional Bonsai recipe ERROR is admitted; absence requires
zero ERRORs and SG refusal warnings fail either profile.

Controls cover paid Workbench extraction in either order and read-only previews,
native equipped Light/Flutter, durability/history conservation, Magic Armor's
default/explicit policy/native player limitation, one/two-sided independent
clocks, inverse orientation, already-oxidized inputs, native conditions, trim
preservation of live clocks distinct from source history, missing/copied state,
sticky managed overrides, typed root/network roundtrips and malformed native
payment/network negatives. Unit cases also cover exact-entry conflict metadata,
codec limits and existing explicit TOML policies. No live trait dispatch, client,
disk-reopen or missing-source recovery acceptance is inferred from block A.

## Silent Gear root wear and repair (block B)

The same `verifySgFoundation` gate now runs maintained production B checks after
A's paid composition controls. It uses pinned native SG/Lib, independently
seeded native item controls and the registered Leggings item callbacks. No
research candidate or Wielder dispatch is enabled. Run profiles sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:NeoForge:test --tests '*SilentGearEffectShapeTest' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation --offline --console=plain
foreach ($order in @('before', 'after')) {
    .\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgFoundationGraph=boots "-PsgEffectsOrder=$order" --offline --console=plain
}
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgFoundationGraph=boots -PsgEffectsOrder=after -PsgEffectsVariant=compatible --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgFoundationGraph=boots -PsgEffectsOrder=before -PsgEffectsVariant=repair --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgFoundationGraph=boots -PsgEffectsOrder=after -PsgEffectsVariant=wear --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgEffectsVariant=wear-delta --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgFoundationAbsent=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation -PsgFoundationGraph=boots -PsgEffectsOrder=after '-Pneoforge_version=26.1.2.99' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:Common:test :MismatchedLeggings:NeoForge:test :MismatchedLeggings:verifyPackaging :MismatchedBoots:NeoForge:assemble --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgFoundation --offline --console=plain
```

`EffectsChecks` covers Renew/Stellar chance/cadence, idle/worn eligibility,
healthy/creative/Unbreakable, duplicate and unequal levels, unmatched native
conditions and both orientations. Wear controls exercise all five definitions,
all combined Malleable/Brittle roll outcomes, zero/small wear, one real root debit,
Unbreaking, armor hits and ordinary exhaustion vs native broken survival. It also
checks the existing Eternal non-damageable projection and actual XP-orb Mending.
A calibrated native recalculation witness and complete root comparisons prove
read-only carriers/history and no full native gear recalculation in B dispatch.

Order profiles change only config priority in a disposable packaged Leggings
copy and assert the actual sorted mixin order; original products are untouched.
`compatible` inserts harmless NOPs and runs the full gate. `repair` makes only
Leggings' repair capture call opaque. `wear` alters only Leggings' proof body,
which must fail final publication. Both refuse twice with exactly one warning,
leave the other feature active, and prove actual Boots repair/wear on both roots.
`wear-delta` separately changes the shared native addition into subtraction and
requires wear refusal in standalone. All transformations have actual class
markers; these variants never set production disable flags directly.

Automatic B coverage passed on 2026-11-09 for every profile above. The complete
Common suite (26 cases), NeoForge suite (39 cases, including two new local-shape
cases), both-loader packaging and current Boots assembly also pass. Original
`.114` products were restored after `.99`, then standalone native and absent-SG
profiles passed again on final code.

The ordinary no-SG profile also exercises real item tick/wear dispatch and
native-independent state codecs. Reports/markers use
`NeoForge/build/{verification,reports}/<baseline>/sg-foundation-<graph>[-absent][-variant][-order]/`.
Only the pinned optional Bonsai recipe ERROR is admitted with SG. Absence requires
zero ERRORs; refusal modes admit exactly their expected single Leggings warning
and reject any sibling warning. C's live armor/oxidation, D's persistence/client
matrix and owner gameplay acceptance remain pending.

## Silent Gear bounded research gate

The [isolated spike guide](../../../spikes/silentgear-leggings/README.md) documents
the temporary dedicated-server numeric/live-state and native Stellar set probe,
its current-Boots transformation-order/refusal controls and `.99` boundary.
Use the shared pinned artifact resolver, not indev JARs. This is feasibility
evidence, not production SG support or the complete compatibility test matrix.

## Ice and Fire damage and Lightning (block A)

Maintained **server-only** production checks use real `hurtServer`, the native
Pre-event results and actual health loss. They enable no damage or Tide candidate.
Native dependencies come from the shared resolver's Ice and Fire `2.2-beta.3`,
Uranus `3.0-alpha.1` and Jupiter `2.4.3` pins; actual current Boots is optional.
Run profiles and baselines sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:NeoForge:test --tests '*IceAndFireDamageShapeTest' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafDamage --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafDamage -PiafDamageGraph=boots -PiafDamageOrder=after --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafDamage -PiafDamageGraph=boots -PiafDamageVariant=damage-flow --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafDamage -PiafDamageGraph=boots -PiafDamageOrder=after '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

`iafDamageGraph=alone|boots` selects actual packaged products.
`iafDamageOrder=before|after` instruments only the staged Leggings mixin config's
priority to 900/1100, around unchanged Boots' 1000. A test extension inspects the
actual sorted application set; requested flags alone do not establish order.
`iafDamageVariant=normal|compatible|damage-class|damage-flow|lightning-item|lightning-flow|absent`
selects the native graph, compatible executable/HEAD-result additions, a changed
LEGS Troll class/exact Lightning item, a runtime-identical opaque local predicate,
or quiet native absence. Mutations run after ordinary mixin selection, so the
final proof must independently refuse. Each refused family must warn once;
native positives and Boots' independent damage/set/Tide readiness remain checked.

All 31 sources, native/both orientations/duplicates, mixed Troll/Dragon sources,
three breaths/projectiles, invalid/suspended/foreign roots, incomplete/incorrect
Lightning sets and joint LEGS/FEET protection traverse the real event bus.
Source snapshots and actual equipment identity must remain unchanged. Focused
native-bytecode tests additionally refuse changed entity/slot/class/item/branch/
coefficient/consumer, missing/ambiguous targets and incomplete API, while allowing
relocated locals and other-slot result chains. Native balance constants remain
native; no copied formula, extra damage listener or global identity alias is used.

Require `server.ok`, `damage.txt` and
`NeoForge/build/verification/<baseline>/iaf-damage-<graph>-<order>-<variant>/matrix.ok`;
reports use the matching `build/reports` directory. Unexpected ERRORs, Boots
refusals, missing markers, bad exits and timeouts fail the gate. Empty native
config overrides preserve native defaults without first-run Jupiter errors.
Restore ordinary `.114` outputs afterward. These checks establish no Tide
activation, client/rendering/lifecycle or owner acceptance; see the B section below for Tide
checks and C for the remaining consolidation.

## Ice and Fire native Tide (block B)

Maintained server-only production checks use the resolver pins from block A and
real `ServerPlayer.doTick`, with no candidate tick or replacement effect engine:

```powershell
.\gradlew.bat :MismatchedLeggings:NeoForge:test --tests '*IceAndFire*' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafTide --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafTide -PiafTideGraph=boots -PiafTideOrder=after --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafTide -PiafTideGraph=boots -PiafTideVariant=count --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafTide -PiafTideGraph=boots -PiafTideOrder=after '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

Run profiles/baselines sequentially. `iafTideGraph=alone|boots` selects actual
packaged products; `iafTideOrder=before|after` changes only the staged Leggings
Tide config's priority to 900/1100, around unchanged Boots' 1000. A test extension
checks Mixin's actual sorted application set. No production Boots dependency or
concrete sibling-handler recognition is introduced.

`iafTideVariant=normal|compatible|count|entry|base|publication|receiver|absent`
selects normal behavior, bounded compatible result chains on all four slots,
an altered own LEGS hook, opaque extra native entry work, extra base tick work,
a tampered true/final publication getter, a wrong-slot fixed receiver binding,
or quiet native absence. Count/publication/receiver refuse only Leggings Tide;
shared unsafe entry/base work refuses both independent Tide owners. Every native
profile retains real damage/Lightning `hurtServer` positives. Unit tests also
cover incomplete/changed API, missing/ambiguous predicates, native provenance,
carrier alias/mutation, effect/count/wetness/dispatch changes, missing publication
and overridden inherited base proof. Tests load actual compiled hooks and the
packaged Boots final proof in both edit orders.

Native-versus-mixed comparisons include both orientations, all seven Tide colors,
duplicate sources, native neighbors, mixed LEGS/FEET without originals, inventory/
wrong-slot/bare/malformed/suspended/foreign controls, actual immersion/drying,
rain without immersion, stronger external effects, controlled native effect veto,
removal and natural expiry. They compare ordered effect calls and the complete
codec state (including hidden effects), require one real worn-root tick and the
native per-piece refresh count, and preserve actual equipment/source contents.
Native wrong-slot class counting stays upstream-owned.

Require fresh `server.ok`, `tide.txt` and
`NeoForge/build/verification/<baseline>/iaf-tide-<graph>-<order>-<variant>/matrix.ok`;
reports use the matching `build/reports` directory. Bad exits/timeouts, ERRORs,
unexpected refusal diagnostics and missing markers fail verification. Refusals
emit one warning per affected owner. Absence leaves the Item checkpoint uninstalled.
Restore normal `.114` outputs after floor checks.

The entry proof intentionally rejects unfamiliar extra work, carrier access,
whole-method wrappers and nonempty base ticks, including harmless additions
outside its small grammar. Bounded result callbacks remain third-party behavior;
their purity or intentional-veto semantics are not certified. Final publication
observes Mixin export after MixinExtras, without claiming protection from later
loader transformers, mutating export extensions or redefinition. These tests
establish no client transport, saved-world lifecycle, rendering or owner acceptance;
block C and the final owner checklist remain separate.

## Ice and Fire presentation and lifecycle (block C)

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyEquipped -PleggingsIceAndFire=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyEquipped -PleggingsIceAndFire=true -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIceAndFire --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIceAndFire -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIceAndFire -PleggingsBoots=true '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

Run graphs/baselines sequentially; repeat the focused equipped profile with the
same `.99` override and restore normal `.114` outputs afterward. The equipped
profile automatically checks all 31 native sources whenever Ice and Fire is loaded:
30 replacement models, 13 decorated waists, 124 both-orientation moving/crouched
trimmed native/dyed-leather pairs, exact ordered native materials, live foil,
GUI/first/third-person item textures, and actual resource reload. Supported pairs
must use split `LeggingsView` submissions. Native normal/advanced descriptions
retain exact Component text/styles under their source, including duplicate-source
controls, with no root duplicate or mutation. No render-spike flag is needed.
Require fresh `ice-and-fire-render.txt` and `ice-and-fire-tooltip.txt` alongside
the equipped profile's server/client markers and `matrix.ok`.

`verifyIceAndFire` seeds a dedicated world with three paid native compositions
and six paid repairs/trims. A separate hidden/muted client JVM reads all six
complete roots from the saved barrel, exercises the real damage/set consumers,
then checks equipment/effect packets on the actual player. Both Tide orientations,
optional mixed Tide Boots without originals, disconnect/save/reopen, preserved
UUID/history, unequip/natural expiry, inventory inactivity, re-equip and drying
are covered. Snapshots for re-equipping are read from the reopened server's
registry lifetime. This is a connected integrated-server lifecycle gate;
packaged dedicated multiplayer belongs to the separate final owner checklist.

Require fresh `server.ok`, `client.ok`, `ice-and-fire-lifecycle.txt`,
`ice-and-fire-saved.json` and `ice-and-fire-session.txt` under
`NeoForge/build/reports/<baseline>/iceAndFire[-boots]`, plus the matching
`build/verification/<baseline>/iceAndFire[-boots]/matrix.ok`. ERRORs, adapter
refusals, missing evidence, timeouts and bad exits fail the gate. Native config
files contain empty override objects to retain defaults without first-run
missing-file errors. Inputs use the shared hash-pinned resolver; clients remain
hidden before GLFW creation, muted, with the NeoForge early window disabled.

The executable `spikes/leggings-ice-and-fire` candidate is retired. Historical
measurements remain in the [dated research record](../local-docs/ICE-AND-FIRE-COMPATIBILITY-SPIKE.md);
maintained A/B refusals and absence controls remain independent of C.

The 2026-11-09 C record passed the two `.114` lifecycle/equipped graphs and the
current-Boots `.99` graph, with fresh required reports and matrix markers.
Post-retirement `.114` damage/Lightning flow, Tide count/shared-entry refusals,
both native-absence graphs, ordinary NeoForge/Fabric dedicated startup and
both-loader packaging also passed. Normal `.114` outputs are restored. The owner
confirmed the final NeoForge tests passed on 2026-11-09, closing the bounded
plan; see the [accepted implementation checklist](../local-docs/ICE-AND-FIRE-COMPATIBILITY-IMPLEMENTATION.md#7-owner-checklist-and-closure).

## Mystical Agriculture Awakened set recognition

The joint server-only fixture exercises packaged Boots/Leggings on each loader,
with the existing shared resolver's exact MA/Cucumber pins. Run loaders and
baselines sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyMysticalAwakenedSetMatrix --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyMysticalAwakenedSetMatrix --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyMysticalAwakenedSet '-PawakenedGraph=both' '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

Restore normal `.114` outputs afterward. The matrix runs twelve isolated cases:
native armor, Boots alone, Leggings alone and both products; a compatible HEAD
observer; independently opaque/ambiguous FEET and LEGS predicates with both
products installed; and quiet MA/Cucumber absence for each nonempty product graph.
The native control installs no armor product or set adapter; standalone Things
only satisfies the existing fixture's core dependency. Production native
dependencies remain optional. No indev directories or client processes are used.

For a single case use `verifyMysticalAwakenedSet` with
`awakenedGraph=native|boots|leggings|both`,
`awakenedVariant=normal|compatible|boots-opaque|leggings-opaque|boots-ambiguous|leggings-ambiguous`
and optional `-PawakenedAbsent=true`. Refusal variants require their product;
absence requires the normal variant.

Both outputs/orientations, one/two matching sources, ordinary Supremium, invalid/
suspended/foreign roots, incorrect slots and inventory-only sources/roots are
compared through the native set predicate. An observer enters the original
growth consumer without cancelling its body: complete sets dispatch exactly at
ticks 20/40 during forty ordinary equipped-stack tick calls per piece. Missing/
incorrect HEAD/CHEST/LEGS/FEET and config-off controls refuse growth. No assertion
depends on one random crop-growth attempt succeeding.

Loaded per-product final-export proofs verify each local edit; Mixin's ordinary
debug export precedes these extensions and is not final-readiness evidence.
The joint normal case also applies the actual packaged adapters in both orders
to native classes, defines both resulting classes in isolated classloaders and
executes their set predicates and native tick/growth bodies throughout the
eligibility/cadence/config matrix. Receiver-free static test bridges preserve all
native tick instructions; the fixture verifies that the receiver is unused before
creating them. Installed growth also uses the ordinary production transformation
pipeline. Focused inherited Fire
Resistance/Luck and real-break left-owner refunds run for both outputs of each
installed product, including every refused set boundary.

Reports: `<Loader>/build/reports/<baseline>/awakened-set-<graph>-<variant>[-absent]`.
Require `server.ok`, `set.txt` and matching
`build/verification/<baseline>/awakened-set-<graph>-<variant>[-absent]/matrix.ok`.
Unexpected errors, unrelated augment/refund refusals, missing markers, bad exits
and timeouts fail the gate; each incompatible product must warn exactly once.
This is automated server evidence, separate from the
[owner handoff and dated results](../../../local-docs/MYSTICAL-AGRICULTURE-AWAKENED-SET-IMPLEMENTATION.md).
It does not certify native particles visually, config-file reload behavior,
runtime clients or owner acceptance.

## Mystical Agriculture inherited effects (block A)

This maintained profile uses packaged Leggings and the resolver-pinned original
NeoForge MA/Cucumber or independent Fabric port. Its mixed effects are production
code; it enables no research candidate. The production refund observer from B
is also present; this effects profile does not certify its refund matrix. The
executable research fixture and all its launch hooks have been retired.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyMysticalEffects --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyMysticalEffects --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyMysticalEffects '-PleggingsMaGraph=current' '-PleggingsMaFocus=lifecycle' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyMysticalEffects '-PleggingsMaGraph=current' '-PleggingsMaFocus=lifecycle' --offline --console=plain
```

Run profiles/loaders/baselines sequentially. `leggingsMaGraph=alone|current`
selects Leggings alone or current packaged Boots. `leggingsMaFocus=full|lifecycle|consolidation`
selects the real keyboard movement matrix or focused effects/cache checks.
`leggingsMaVariant=normal|compatible|armor|equipment|health` selects unmodified
native operations, an unrelated instruction, or a runtime-identical but unproved
local seam; use `lifecycle` and `alone` for refusal variants. Armor refusal affects
only Luck; equipment refusal affects Health Boost/Speed/Step Assist; health refusal
affects only Health Boost. Native items and independently proved families remain
positive controls. Each refusal must produce exactly one diagnostic per affected
family in the client process; the seed server does not initialize bindings.

`-PleggingsMaAbsent=true` omits both optional native artifacts and checks ordinary
composition/client startup with quiet absence. `-Pneoforge_version=26.1.2.99`
selects the supported floor; restore normal `.114` outputs afterward. No indev
instance or published Boots binary is needed by A; the unchanged published refund
matrix belongs to B.

Each profile seeds a disposable dedicated world, then starts a hidden, muted
integrated client before GLFW creation. Native input/movement/attribute packets
connect that real client player to the integrated server. Source/selection checks
cover all 22 IDs, all six source tiers/capacities, strongest selection in both
orientations, duplicate occupied purchases, unknown/unsupported IDs, malformed
slots/roots and unchanged histories. Live armor checks cover all seven armor
families (Absorption III, Fire Resistance, Luck III, four debuff removals), native
and both mixed orientations, carried/hand/wrong-slot controls and the shared
exclusive 9600-tick Absorption boundary. Equipment-list order is compared to native.

Normal/compatible lifecycle checks exercise Health Boost I/III/V, Step Assist,
crouching, native-to-mixed same-rank replacement, stronger-to-weaker replacement,
unequip and genuine near-exhausted Health V breaks with full health. Native and
mixed Boots controls retain their lower entitlement after Leggings removal/break;
the client checks synchronized attributes and liveness independently. Refusal
variants run only the short native/mixed/removal control sequence.

Full movement measures forward/strafe/sprint/crouch/ordinary-airborne scenes with
empty native, Speed I/III and both mixed orientations. NeoForge must demonstrate a
native positive in the four grounded scenes and mixed/native equality; the
pinned Fabric port must preserve its native lack of acceleration. Equality alone
is not functional Speed acceptance. Water and creative flight are not certified.

Reports: `<Loader>/build/reports/<baseline>/mystical-effects-<graph>-<variant>`
with optional `-absent`/`-lifecycle` suffixes. Require both phase `.ok` markers,
`movement.txt`, and `matrix.ok` in the matching `build/verification/...` directory.
Partial samples are diagnostic only. Unexpected runtime ERRORs, binding/refund
refusals, bad exit, missing markers and timeouts fail verification. This profile
establishes no refunds, native table support, actual saved mixed-item reopening,
dedicated-client transport, rendering or owner acceptance.

## Mystical Agriculture conserved refunds (block B)

This maintained **server-only** profile loads packaged production Leggings with
resolver-pinned original NeoForge MA/Cucumber or the independent Fabric port.
It enables no research candidate or second shrink router. Run profiles, loaders
and baselines sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyMysticalRefunds --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyMysticalRefunds '-PleggingsMaGraph=current' '-PleggingsMaOrder=after' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyMysticalRefunds '-PleggingsMaGraph=published' '-PleggingsNeoForgeBootsJar=<unchanged-1.0.0.jar>' --offline --console=plain
```

`leggingsMaGraph=alone|current|published` selects Leggings alone, current built
Boots or the unchanged loader-specific 1.0.0 binary. Published inputs require
`leggingsFabricBootsJar` or `leggingsNeoForgeBootsJar` and must match the committed
[release pins](fixtures/boots-1.0.0.json); there is no rebuild or indev fallback.
`leggingsMaOrder=before|after` selects observer priority 1100/900 around Boots'
1000. The second order instruments **only the observer annotation in the staged
Leggings copy**; production outputs and every Boots byte remain unchanged. The
fixture checks actual exported merged-method priorities and live final readiness.

`leggingsMaRefundVariant=normal|compatible|flow` selects the normal final code,
unrelated NOP instructions, or an opaque but runtime-equivalent broken predicate.
The last must refuse only mixed Leggings refunds with one expected diagnostic:
ordinary consumption, the native refund positive and independent selection stay
active. Use that control alone. `-PleggingsMaAbsent=true` checks ordinary actual
breaks and quiet optional absence. `-Pneoforge_version=26.1.2.99` selects the floor;
restore normal `.114` outputs afterward.

The fixture uses actual `ServerPlayer` instances with test packet listeners in a
disposable dedicated world. It exercises both orientations, weaker owned purchases,
two equal purchases in distinct valid slots, empty/ordinary sources and unchanged
history. High-tier break fixtures remove only the live root's `UNBREAKABLE`
component to exercise damageable survival destruction; source snapshots remain
exact. Real native inventory insertion and accepted `ItemEntity` overflow spawns
are observed. Timing witnesses reject delivery before consumption. Malformed slots,
unknown IDs, insufficient tiers, bare roots and copied root components cannot
invent refunds. Cancelled/throwing calls, pre/post source mutation, replacement,
movement between slots, damage mutation, creative/spectator changes, direct
deletion, removal, nonterminal wear, same-root/nested-root reentry and cancelled/
throwing delivery are checked. Aborted calls must not leak into later genuine
breaks; dispatched purchases are never retried. Both products are worn and broken
on the same player in coexistence cases, preserving each purchase separately.

Reports: `<Loader>/build/reports/<baseline>/mystical-refunds-<graph>-<order>-<variant>`
with optional `-absent`; require `server.ok`, `refunds.txt` and `matrix.ok` in the
matching `build/verification/...` directory. Unexpected runtime ERRORs, any Boots
refund refusal, unexpected Leggings/effect/binding refusals, bad exits, missing
markers and timeouts fail verification. This is bounded actual-break evidence,
not dedicated-client transport, saved-item reopening, arbitrary drop pipelines,
Tinkering Table support or owner acceptance; broader consolidation is covered below.

## Mystical Agriculture presentation and consolidation (block C)

The maintained effects driver also owns production-only consolidation. Run each
loader/graph/baseline sequentially with exact shared resolver pins:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyMysticalEffects '-PleggingsMaFocus=consolidation' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyMysticalEffects '-PleggingsMaFocus=consolidation' '-PleggingsMaGraph=current' --offline --console=plain
```

Use both `alone` and `current` on each loader, with focused current-Boots
`-Pneoforge_version=26.1.2.99`, then restore normal `.114` artifacts. Consolidation
requires native MA/Cucumber and the normal variant. Keep the existing lifecycle
absence/refusal controls and B's unchanged-published-Boots refund evidence separate.

The seed dedicated JVM exercises all six Essence families in both orientations:
paid workbench extraction in either order, paid-partner close and unpaid return,
paid donor anvil/book, native enchanting candidates, crafting/grindstone donor
repair/disenchanting, paid trims preserving native augment history, and actual
native Tinkering Table slot rejection with original armor as the positive control.
It stores four fully composed roots in a world chest and records their full codec
values plus process ID. The second JVM opens that saved world, verifies a different
process and exact root/source history, strongest unions, worn Fire Resistance and
actual connected-player breaks returning precisely each left owner's purchases.

The real client connection retains A's activation/removal, attribute synchronization,
Health V actual-break liveness and lower native/mixed Boots entitlement. The
extended sequence also removes lower Boots first, retaining the stronger mixed
LEGS contribution, then removes Leggings. Consolidation omits the unchanged full
movement matrix: A's NeoForge native Speed positive and Fabric native limitation
remain the relevant evidence.

Native augment component trees (including style) must occur beneath their original
source, including weaker purchases, with no effective root list. Checks include
ordinary/F3+H, crouch/Shift key state, hidden sources/root and null tooltip player.
All six families render in both orientations and all item contexts with original
textures, live glint and dyed/trimmed leather partners. Native equipment submissions
must retain materials, dye, trims/glint, anatomical leg and nonempty clipped waist
while walking/crouching. Assertions repeat after a real resource reload with a new
native renderer and geometry-cache generation; `presentation-panel.png` supports
bounded visual QA. No new production renderer, tooltip producer or native assets
are added.

Require `server.ok`, `client.ok`, `lifecycle.txt`, `saved.json`, `reopened.txt`,
`movement.txt`, `presentation.txt`, `presentation-panel.png` and `matrix.ok` under
the effects profile paths with `-consolidation`. Missing markers, unexpected runtime
ERRORs and any effect/refund/binding refusal fail the gate. The saved-world handoff
uses a dedicated seed and an integrated client in separate JVMs; it does not claim
dedicated-client transport or owner gameplay/presentation acceptance. The final
[owner checklist](../local-docs/MYSTICAL-AGRICULTURE-AUGMENTS-IMPLEMENTATION.md#7-owner-checklist)
remains separate and was confirmed complete by the owner on both loaders on
2026-11-09, closing the bounded integration. Ordinary no-flag `verifySkeleton`
profiles verify prototype retirement.

## Undergarden Rogdorium state

This maintained **server-only** profile exercises the packaged Boots/Leggings
factories and recomposers, with the small native-state service in their selected
nested Things, plus their independent production Infuser adapters. It does not
activate candidate hooks or change tooltips. The executable infusion spike has
been retired; all checks use maintained fixtures. The fixture module
can stage each product alone or both, never loose product classes or indev
JARs/configuration/worlds.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumState --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumState '-PleggingsUndergardenGraph=boots' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumState '-PleggingsUndergardenGraph=leggings' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumState '-PleggingsUndergardenAbsent=true' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumState '-PleggingsUndergardenVariant=compatible' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumState '-PleggingsUndergardenVariant=recipe' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumState '-PleggingsUndergardenVariant=menu' --offline --console=plain
```

Default graph is `both`. Add `'-Pneoforge_version=26.1.2.99'` for the supported
floor; restore `.114` with a final default run. Run profiles/baselines sequentially.
The shared resolver downloads/caches the exact Undergarden 0.9.8 pin when needed
and permitted; omit `--offline` to prepare a missing artifact.

Coverage: odd/equal/unequal/empty native creation in both orientations, paid native
infusion of vanilla sources, exact live-state recomposition (including absence,
zero, root-only installation and old-projection equality), real native spending,
native recipe copy, paid workbench output and unpaid malformed/binding refusal,
plus full-stack persistent/stream codecs. The native Infuser is also exercised
through paid mixed processing, manual/Shift-click input, extraction, partial top-up,
old OWNER layouts and absent/zero reservoirs. Bare, suspended, quarantined,
count-two and unrelated items do not gain admission. Slot/fuel restrictions remain
native. Recipe removal, changed ingredients and equivalent replacement/time changes
use the real recipe-manager apply/finalize path with new menus/machines.
Normal native runs also save six roots per installed product in a real chest:
natively spent and paid-refilled balances, synthetic former OWNER/absent-inverse
layouts, explicit zero and a root-only custom capacity. `saved-roots.nbt` is an
independently encoded full-stack oracle. OWNER samples reproduce the old layout;
they are not saves produced by a published 1.0.0 binary. Coexistence compares
native infection and slot spending with both products worn together. Four ordinary
Leggings families additionally exercise actual equipped armor/Froststeel speed.
Codec roundtrips are not multiplayer transport; menu calls do not establish
ordinary-client prediction or visual acceptance.

`compatible` introduces an unrelated instruction and a final boolean peer chain.
`recipe` and `menu` each replace one operand with a runtime-identical but unproved
supplier, testing independent refusal without disabling the other hook or native
armor. Three focused `InfuserShapeTest` unit tests additionally exercise the actual
packaged Boots transformation and Leggings transformation in both orders, duplicate
application, compatible peers and unknown recipe/slot/menu operands atomically.

Reports: `NeoForge/build/reports/<baseline>/rogdorium-state-<graph>[-absent][-variant]/`;
driver logs/`matrix.ok`: matching `NeoForge/build/verification/.../`.
One bounded `Mixed Rogdorium infusion creation refused` warning is expected from
the normal/compatible present-native negative controls; absence must be quiet.
Each isolated refusal expects one guard warning per installed product and no state
warning. Errors, bad exit,
timeouts, failed/missing markers and unexpected refusal counts fail verification.
This state gate opens no client window and establishes no owner visual acceptance.

### Native Rogdorium presentation

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumPresentation --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumPresentation '-PleggingsUndergardenGraph=boots' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumPresentation '-PleggingsUndergardenGraph=leggings' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumPresentation '-PleggingsUndergardenAbsent=true' --offline --console=plain
```

The presentation driver first runs the corresponding state/Infuser server, then
copies its disposable world and exact staged product/native JARs into a client.
The second JVM reopens the actual saved chest on its integrated server thread,
requiring exact full-stack NBT before recomposition and exact live infusion
presence/value, source history and custom name afterward. Derived repair ingredient
ordering is not a live-state invariant. Native absence skips the native lifecycle
fixture without loading its optional classes.
It requires the normal variant; add `'-Pneoforge_version=26.1.2.99'` for a focused
floor check, and restore `.114` afterward. Run graphs/baselines sequentially.
The existing test-only window hook hides the client **before GLFW creation**;
options mute it and disable the NeoForge early display. No screenshots are needed.

Actual normal/F3+H tooltip producers must emit exactly one native live-root line
with native text/style, after the source sections, or no line when zero, absent
or hidden. Both orientations, unequal historical reserves, source display flags,
root-only installation and ordinary native items are covered. Actual registered
decoration rectangles are checked at zero/partial/full charge, with/without the
durability bar; hiding the component tooltip must not hide the independent native
bar. Tooltip/decorator calls must not mutate roots or histories. Assertions repeat
after resource reload; absence must remain quiet.

When Leggings is installed with Undergarden, the four ordinary families also
exercise both item orientations in GUI/each hand, native equipped materials and
trim layers, glint, selected moving legs and nonempty clipped waists while
standing/crouched. These submission assertions repeat after resource reload;
they are not pixel-level or owner visual certification.

Evidence shares the state profile's report/log directories: `presentation.txt`,
`client.ok`, `client.log` and `presentation.ok`. Unexpected runtime ERRORs or adapter
refusals fail the driver; the client disconnects cleanly before stopping.
This establishes actual saved-item reopening, not multiplayer transport, manual
presentation or native menu prediction; those remain separate owner gates.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRogdoriumArtifacts --offline --console=plain
```

The artifact gate assembles all six product/core JARs. It rejects bundled native,
test or spike classes and Fabric references to native Undergarden classes, checks
the small Things service/provider boundary, and requires each product to embed
the exact current loader-specific core. This packaging gate does not replace the
core's focused unchanged API-v1 consumer evolution check.

## Aether II production gameplay

```powershell
.\gradlew.bat :MismatchedLeggings:NeoForge:test :MismatchedLeggings:RuntimeTests:NeoForge:verifyAetherGameplay --offline --console=plain
```

Dedicated-server-only coverage of packaged production set recognition, default
charm omission and Alkahest queries. Pinned Aether comes from the shared resolver;
no spike routing, explicit fixture-only omission rules or indev files are used.
Add `-PleggingsBoots=true` for current Boots coexistence or
`'-Pneoforge_version=26.1.2.99'` for a focused floor run. Run sequentially and
restore normal `.114` outputs afterwards; omit `--offline` for initial downloads.

`-PleggingsAetherVariant=sets`, `fluid` or `armor` introduces one unknown local
native operand/result contract in a separate standalone JVM. Native behavior is
unchanged, but only that product domain must decline with one diagnostic;
the other domains and ordinary controls remain functional. `absent` omits Aether
and runs the normal standalone composition server. Default is `normal`; unknown
variants and sibling fault combinations are rejected.

Guard unit tests additionally apply the real packaged Boots set/tier edits in
both orders and reject count scaling/clamping, changed enumeration, receivers
and suppliers before partial edits. Runtime checks cover six sets, native gloves
and transient effect removal, all five armor charms and explicit policies,
complete source histories, unequal tiers, native dropped/both-hand/worn fluid
calls, freeze immunity and invalid/foreign/suspended/count-two negatives.
Native charm/tier fixtures use components, not paid Forge operations.

Evidence is `NeoForge/build/reports/<baseline>/aetherGameplay[-boots][-variant]/`
(`aether-server.txt` for native runs, plus `server.ok`) and corresponding
`build/verification/.../matrix.ok`. The two known native dedicated `Toast` ERRORs
remain visible and exactly counted; all other ERRORs/unexpected refusals fail.
No client window or screenshot is created. This profile does not establish
source graphic placement, visual acceptance or a separate-process save restart.
Keep the early fault plugin ASM-only: its Minecraft-typed receiver belongs in
the separate helper, avoiding premature class loading before common tag mixins.

## Aether II production tooltips

```powershell
.\gradlew.bat :MismatchedLeggings:NeoForge:test :MismatchedLeggings:RuntimeTests:NeoForge:verifyAetherTooltips --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAetherTooltips -PleggingsBoots=true --offline --console=plain
```

This maintained profile stages packaged Leggings and resolver-pinned Aether, with
optional current Boots; it never activates spike routing/hooks or reads indev data.
A dedicated gameplay server seeds its disposable world, then a hidden/muted client
checks the real tooltip/event/gather/native-factory paths. NeoForge's early window
is disabled and GLFW visibility is false before creation. No IDE run is generated.
Do not run profiles/baselines concurrently. A focused floor run accepts
`'-Pneoforge_version=26.1.2.99'`; restore `.114` outputs afterwards.

Coverage includes six families/both orientations, normal/F3+H identities, lore and
source IDs, native tiers once, Arkenium hint omission, actual idle/incomplete/full
counts and no-player safety. Native transient attributes belong only to the exact
worn LEGS object, once per distinct family after ordinary attributes, never a held
or inventory copy. Installed-only source graphics use actual native client factories;
duplicate instances/order, empty capacity, oversized native-name fallback, independent
visibility, live-root-holder overrides and foreign payloads are controls. Scoped
nested/exceptional calls restore exact source identity. Local formatting/graphics
failure controls preserve proven native containment and unrelated presentation.
The optional sibling case checks native mixed LEGS/FEET counts and both tooltip paths.

Reports are `NeoForge/build/reports/<baseline>/aetherTooltips[-boots]/`, including
`aether-tooltips.txt`, `aether-tooltip.png`, `server.ok` and `client.ok`; bounded
process logs and `matrix.ok` are under `build/verification/<baseline>/` with the same
label. The screenshot is only relevant integrated tooltip QA, not equipment acceptance.
Exactly the two known native dedicated Toast ERRORs are allowed; other ERRORs and
unexpected Aether refusals fail. Client logs must have zero ERRORs. Unit guards use
the real native producer bytecode and packaged Boots edits in both orders, including
unsupported local producers and atomic refusal of changed source-detail branches.
This does not establish owner acceptance, paid Forge editing or save/restart coverage.

### Aether II consolidated acceptance

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAetherTooltips -PleggingsAetherConsolidation=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAetherTooltips -PleggingsAetherConsolidation=true -PleggingsBoots=true --offline --console=plain
```

The consolidation switch reuses the same pinned, hidden/muted server/client
driver and isolates its worlds/reports under `aetherCompatibility[-boots]`.
It adds paid workbench extraction in both orders, paid/unpaid close cleanup,
native donor anvil payment, crafting/grindstone admission and source preservation,
paid trims retaining native tiers/charms, and continued native Forge installation
exclusion. A NeoForge FakePlayer avoids fictitious attachment networking when
native smithing advancements are awarded; ordinary offline ServerPlayers have no
real channel and are unsuitable for that native callback.

Both charmed/reinforced output orientations are written to a real chest, saved
by the dedicated server and compared against full registered-codec snapshots
after its copied world is reopened by the integrated server. A registered stack
stream-codec round trip separately checks network-serializable state; it is not a
claim that a real client connected to that dedicated endpoint. Native matching-loader
dedicated synchronization remains in the explicit owner checklist.

The client compares actual native submissions/materials and independently emitted
leg/waist vertices for all six families, both orientations and walking/crouching,
with native styles, dye, trims and root glint. A real resource reload must replace
the native renderer/invalidate product geometry, then pass both those comparisons
and B's integrated tooltip checks again. Only the existing relevant tooltip capture
is made; visual equipment acceptance stays with the owner. Additional evidence is
`aether-lifecycle.txt`, `aether-saved-0.json`/`-1.json` and the expanded
`aether-tooltips.txt`. No executable spike, alternate renderer or asset copy remains.

On 2026-11-08 the owner separately confirmed the complete NeoForge acceptance
checklist: both output/extraction orders and close cleanup, native sets including
mixed LEGS/FEET together, normal/F3+H source presentation, equipment/item appearance
and reload, freeze/Alkahest behavior, ordinary workstations, save/reopen and actual
same-loader dedicated play with Boots present. This completes A/B/C acceptance;
it does not extend automatic coverage or the deferred native transaction scope.

## Roots Classic production rendering

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRootsRendering --offline --console=plain
```

This maintained profile replaces the rendering spike. It stages pinned Roots
Classic 1.6.7 through the shared artifact resolver/cache, runs a dedicated server
control, then an early-hidden/muted client without IDE configurations. Add
`-PleggingsBoots=true` for current Boots coexistence or
`'-Pneoforge_version=26.1.2.99'` for the floor; run variants sequentially and restore
routine `.114` outputs afterwards. Omit `--offline` for initial downloads.

The packaged production selector is exercised without a test routing hook.
Independent native selected-leg geometry/UV/material controls cover eight ordered
Roots/iron pairs, moving/crouched players, posed/small stands, adult/baby wearers,
queued native models, trim/glint, atomic complete-owner/neutral fallback and real
resource reload. Resource fixtures verify requested-layer priority and missing
alternate layers; a temporarily replaced native extension is restored in `finally`
and must produce one bounded compatibility warning, not an ERROR exemption.
The same pre/post-reload assertions preserve native perk text in each source
section without a duplicate root block, and native item textures in GUI/hand
contexts for all eight pairs.

Reports under `NeoForge/build/reports/<baseline>/rootsRendering[-boots]/` require
`roots-rendering.txt`, one representative `roots-pairs.png` and normal completion
markers. Corresponding bounded logs and `matrix.ok` are under `build/verification/`.
The original native LEGS route is deliberately unchanged and empty in the pinned
graph; explicit native HUMANOID controls are separate references. This profile
does not establish repair/Terra, full-set gameplay or owner acceptance.

## Roots Classic native ticks

```powershell
.\gradlew.bat :MismatchedLeggings:NeoForge:test :MismatchedLeggings:RuntimeTests:NeoForge:verifyRootsTicks --offline --console=plain
```

This dedicated-server-only profile stages packaged Leggings and pinned Roots
Classic through the shared resolver/cache. It uses scoped native RNG controls,
not a replacement repair/Terra algorithm. Assertions cover actual root damage and
the native player attachment, independent repair/Terra rolls, one Sylvan-preferred
receiver per dispatch, immutable sources, healthy/capped Terra, non-player repair,
real equipped/held/backpack inventory dispatch, and invalid/suspended negatives.
No periodic player-regeneration event is posted during controlled callback cases.

Add `-PleggingsBoots=true` for current packaged Boots: both products' distinct
final receiver/helper proofs must exist, each repairs its own root and contributes
Terra independently. The Leggings unit task resolves the same pinned artifact
and tests local stack-consumer guard mutations and owned proof publication.

The profile also invokes both real native staff consumers, checking Sylvan's
single set bonus, Crystal Staff's separate base bonus, Terra payment and uses.
Wildwood healing runs through the native event bus with controlled hit/miss,
health/food/missing-piece controls and no original LEGS/FEET anchors in the
coexistence graph. A compatible independent HEAD wrapper remains active.
Unit tests apply the actual packaged Boots FEET edit before/after Leggings'
LEGS edit on each pinned consumer. The sibling JAR is a test-only dependency.
Normal mode additionally checks repaired inventory/slot-packet round trips,
the native Terra attachment serializer and paid anvil/trim menus, preserving
native Roots-donor crafting/grindstone vetoes. Actual world re-entry is an owner
check; serialization tests are not claimed as a separate-process restart.

Use `-PleggingsRootsTickVariant=receiver` or `helper` for late native stack-consumer
faults: originals still work, mixed calls are refused before mutation, and repeat
attempts must not repeat diagnostics. `absent` omits Roots entirely and requires
quiet ordinary mixed ticks. Default is `normal`; unknown values are rejected.
The additional `staff`, `crystal` and `healing` variants introduce a late unknown
LEGS item consumer only in that native method. Exactly one set adaptation must
be refused, originals and the other consumers must remain functional, and root
tick support must remain enabled.
Variants run in separate JVMs/directories. Use
`'-Pneoforge_version=26.1.2.99'` for the floor and restore `.114` afterwards.

Evidence is `NeoForge/build/reports/<baseline>/rootsTicks[-boots][-variant]/roots-ticks.txt`
plus `server.ok` and corresponding `build/verification/.../matrix.ok`. Unexpected
ERRORs or refusal counts fail the wrapper. This profile creates no client window
or screenshot. Full owner acceptance remains separate from these automatic gates.

## Oritech production compatibility

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyOritechCapture --offline --console=plain
```

This maintained profile replaces the retired research spike. It stages pinned
Oritech/Athena/GeckoLib through the shared resolver, then runs the dedicated
workbench control and a hidden/muted client. Add `-PleggingsBoots=true` for current
Boots coexistence; omit `--offline` for initial downloads. No IDE configurations
are created; one representative equipped panel is captured by the hidden client.

The native original armor renderer is the independent control for production
full/masked source captures. Assertions cover geometry/UV/normals/material/light,
actual wearer/partial tick, owned rig poses, queued A/B/A, stale/reused/changed
context, unsupported pose isolation, API shape guards and rejected submissions.
A controlled missing-leg resource failure is restored in `finally`; real reload
must recover the bridge and reject old captures. The client requires one bounded
compatibility warning despite repeating the resource-failure check. The exact
known Oritech `fluxite` data-map error stays visible; other ERRORs fail verification.

Production pair checks cover Exo on either side, two Exo and ordinary controls,
standing and posed. They verify native leg selection, the ordinary leg/half-waist,
complete Exo owner versus neutral fallback, missing request context and failed
resource epochs. A missing right asset rejects the pair after the left split has
prepared: no partial output may reach the destination before full-owner fallback.
The panel compares native Exo, both orientations and two Exo through the real
production renderer, with mixed Boots equipped when coexistence is enabled.

Consolidated controls compare native armor-layer output with actual moving/
crouched players, small posed armor stands and adult/baby zombies, retaining
queued geometry across rig changes. Native decorated controls preserve Exo's
single custom-geometry pass (no additional equipped trim/glint/outline pass);
the ordinary half retains trim, live-root glint and its outline field. Actual
reload must replace the baked native model as well as recover refused captures.
GUI/hand item geometry and the native source description are checked too.

The dedicated server checks equipped armor and movement-speed values, unequip,
servo selection, unbreakability, source immutability and actual fall damage with
native/mixed Boots positives. Vanilla players have no `flying_speed` attribute:
the native +20% declaration is preserved/averaged to +10% for one source, but it
is not a player creative/Elytra flight-speed feature. Leggings must not acquire
helmet night vision or Boots fall protection by themselves.

Evidence: `NeoForge/build/reports/<baseline>/oritechCapture[-boots]/oritech-capture.txt`
plus `oritech-gameplay.txt` and `oritech-pairs.png`, with bounded logs under
`build/verification/<baseline>/oritechCapture[-boots]/`. Add
`'-Pneoforge_version=26.1.2.99'` for the supported floor; restore `.114` after it.
Run profiles sequentially. Owner acceptance is recorded separately from these
automatic gates; this is bounded Oritech support, not a generic GeckoLib adapter.

## Neo Vitae recognition and direct root operations

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitae --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitae -PleggingsBoots=true --offline --console=plain
```

This bounded **dedicated-server-only** profile uses the current packaged products
and Things' shared artifact resolver: Neo Vitae 1.1.31, Modonomicon 2.16.0 and
GeckoLib 5.5.2. Prefetch the catalog before the first offline run. Use NeoForge
`.114`; the pinned Modonomicon requires `.109` or newer. There is no claim of a
native `.99` run with these dependencies.

`neovitae-server.txt`, `neovitae-composition-server.txt`,
`neovitae-lifecycle-server.txt`, `neovitae-saved.json`,
`neovitae-restart-server.txt` and fresh `server.ok` are required alongside clean
error gates in both JVMs. The recognition/direct-root checks
exercise both source orientations and native leggings controls:

- Native full-set recognition, equipment-change chest-attribute recalculation,
  removal/inventory-only and nearly broken chestplate controls. With Boots,
  neither the feet nor legs slot contains an original Sentient item.
- The actual Hellfire Forge tick, ingredient consumption and Spiritus fuel
  payment for direct root infusion and Blood Mending. Ordinary non-Sentient
  mixed armor is eligible too, without granting Sentient set membership.
- Root-only simulated/committed Spiritus fill, incompatible positive-type
  refusal, draining, empty-container retyping and immutable source history.
- Bare invalid roots do not acquire any of the three projected behavior tags.

The separate composition marker exercises the actual Leggings factory/recomposer
with native components: maximum capacity, conservative compatible charge and
owner retention on positive type conflicts; empty/defaulted/orphan fields and
presence-based Blood Mending, including false markers. Both input orders and
orientations, repeated previews and immutable history are checked. Recomposition
preserves exact live values/presence for spent, empty, retyped, removed and
root-only state, even when equal to old projections. Synthetic source replacements,
conflicting generic `omit`/`share` overrides and malformed refusal verify the
product boundary, not a duplicate of Things' arithmetic unit tests.

The lifecycle check adds four real paid Workbench transactions, both extraction
orders, immutable history and a malformed no-payment control. Native forge ticks
install infusion/Blood Mending with ingredient/fuel debits. Native transfers
exercise simulation, draining, depletion/retyping and incompatible-type refusal.
Real trim/tier smithing, paid anvil, crafting repair and grindstone takes preserve
the live tuple/marker without importing historical source balances. This includes
an infusion installed only after mixing, not just inherited fields.

The first server stores nine complete roots in a world barrel and writes an
independent expectation manifest. The verifier starts a **second JVM** on that
same world, without reseeding it: PID and full stack/history equality are checked,
then native drain, paid trim, empty-container retyping and later infusion run
again. `server.log` and `restart.log` are retained; `matrix.ok` is written only
after both pass. `leggingsNeoVitaeRestart` is an internal child-launch flag, not a
replacement reload command or an independent acceptance gate.

Human set effects/training, equipped EV repair and client tooltips/bars/rendering
remain separate acceptance. The [tag-only checklist](../local-docs/NEO-VITAE-COMPATIBILITY-EXPLORATION.md#7-tag-only-implementation-and-owner-handoff)
and [composition checklist](../local-docs/NEO-VITAE-INFUSION-COMPOSITION-IMPLEMENTATION.md#focused-owner-checklist)
distinguish the two scopes.
No automated client or screenshot is launched by this profile.

## Forbidden Arcanus Aureal and modifier-state validation

Current Boots modifier/adoption checks reuse this packaged harness with
`-PleggingsBoots=true -PleggingsForbiddenBoots=true`; their commands, frozen data,
extra markers and scope are maintained in the
[Boots runtime guide](../../MismatchedBoots/RuntimeTests/docs/forbidden-arcanus.md).
The default profile still validates Leggings independently; the current-Boots
flag is distinct from the historical published-Boots spike below.

Maintained Eternal/Soulbound state, smithing, presentation and recovery checks
replace the retired lifecycle spike. That old experiment has no remaining
property, extra native compile dependency or executable assertion path. The
separate published-Boots load experiment below is explicitly opt-in.

This NeoForge-only profile stages Forbidden Arcanus 2.7.2 and Valhelsia Core
1.1.6 through Boots' shared fixture catalog, with pinned download URLs, cached
artifacts and SHA-256 verification. It does not read indev JARs or configuration
and exercises the local authority/transaction adapter over Things' optional native
service. The shared flag value retains the product component ID and its persistent
fields/wire order; moving Java ownership is not a saved-data migration. The
packaged Leggings JAR supplies nested
Things; optional coexistence uses the current built Boots artifact.
The client also stages the existing pinned JEI fixture: Forbidden Arcanus 2.7.2
otherwise logs a native `ClientRecipeSync` / missing `IModPlugin` error on recipe
synchronization. The dedicated endpoint remains JEI-free. This is a fixture-only
upstream workaround, not a new production requirement or viewer acceptance claim.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyForbiddenArcanus --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyForbiddenArcanus -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyForbiddenArcanus -PleggingsForbiddenServerOnly=true '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

After initial downloads, the first command also accepts `--offline`. Run profiles
and baselines sequentially; restore normal `.114` packaging after the floor run.
Omit `-PleggingsForbiddenServerOnly=true` on the floor command to include the
hidden client/presentation/reopen gate, as executed during modifier block C.
Reports use `build/reports/<baseline>/forbiddenArcanus[-boots][-server-only]/`;
launch logs and `matrix.ok` use the corresponding `build/verification/` path.
Fresh `server.ok`/`client.ok` and `forbidden-server.txt`/`forbidden-client.txt` are
required for the phases actually run. The `-server-only` variant cannot establish
client, rendering or saved-world acceptance.

The ordinary server profile additionally requires fresh
`forbidden-modifiers-server.txt`. It checks all none/Eternal/Soulbound input
combinations and both outputs through the actual workbench, authoritative union,
immutable sources, exact native projections, real armor wear and native
death/`PlayerList.respawn`, codecs, root-only state and recomposition. Eternal
removes live durability; Soulbound remains the native scalar projection when both
effects are active. A separate fresh `forbidden-modifiers-smithing.txt` is also
required: real native menus cover independent installation in either order,
both orientations, named/unnamed roots, normal/shift payment and recipe credit,
duplicate/invalid-input refusal, live-vs-historical admission, recipe/tag rebinding
and stale-result rejection. Subsequent trim, anvil rename, grindstone and finite
Soulbound crafting repair preserve the live effects. Native originals remain
controls. The normal hidden client additionally requires
`forbidden-modifiers-tooltip.txt` and `forbidden-modifiers-tooltip.png`. It checks
native translated Eternal/Soulbound root prefixes, custom/source names, ordinary/
Shift/F3+H and EMPTY/null-player contexts, with unchanged root/source components.
The screenshot is one relevant final dual-effect tooltip frame. Actual native
background extraction is observed at `TooltipRenderUtil`, not a synthetic texture
event without an extractor. Native controls, each single effect and both together
pass through the real frame renderer.
With `leggingsForbiddenBoots=true`, the same checks also exercise Boots' own
listener/state and require fresh `forbidden-boots-tooltip.txt`. Invalid/suspended
Boots never gain prefixes, both installed listeners stay product-local, and the
single relevant screenshot ends on a dual-effect Boots tooltip. See the
[Boots modifier guide](../../MismatchedBoots/RuntimeTests/docs/forbidden-arcanus.md).
Inherited and paid single/dual roots in both orientations arrive through real
equipment **and inventory** packets without client-side item/attribute writes.
Save/disconnect/reopen retains installed roots, flags, native scalar projection,
names, prior work, custom state and complete source history in worn/inventory slots.

The optional-absence `verifySkeleton` checks on both loaders reject source-carried
root authority. The existing `verifyRecovery` profile now carries the own flags
through its actual source-removal/reinstallation cycle without loading Forbidden
Arcanus; this checks opaque state preservation, not native effects without the mod.

For actual Forbidden Arcanus source removal and reinstallation, run:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRecovery -PleggingsForbiddenRecovery=true '-PleggingsRecoveryStages=write,absent,absentagain,restore,reloaded' --offline --console=plain
```

The isolated `recovery-forbidden` variant stages pinned FA/Valhelsia/JEI artifacts
itself, removes FA/Valhelsia between independent JVMs, and reinstalls them on
restore. A real barrel holds paid Soulbound and dual-effect roots in both
orientations with a native Draco source. Native world saves/loads must suspend
them inertly, preserve repeated absent archives and restore complete paid state/
source history, not only own flag codecs. Each stage requires fresh
`forbidden-recovery.txt`; the existing TCP/death/respawn generic recovery checks
still run. No indev JARs/worlds are used and no new screenshot is added.
On the absent server, NeoForge can report exactly `Encountered unknown or
non-serializable data attachment forbidden_arcanus:aureal. Skipping.` when loading
the returning player. That native player attachment is not an item archive and
is outside the source-removal contract. Only that exact attachment/logger/stage
diagnostic is accepted; any other error-level server/client message fails the
native recovery variant. Whole-item restored-state equality remains mandatory.

On 2026-11-06, modifier C passed `.114` standalone/current-Boots and `.99`
standalone dedicated/hidden-client profiles, the five-stage native-source recovery
cycle above, Common unit tests and both-loader absence profiles. Normal `.114`
packaging/runtime outputs were restored afterward. The relevant dual-effect
tooltip frame was inspected, with native title/background and source separators;
the owner separately confirmed the consolidated NeoForge modifier checklist on
2026-11-06, closing the bounded Eternal/Soulbound integration. Optional manual
dedicated-reconnection/coexistence permutations were not separately reported.

The dedicated fixture compares both native tiers, plain controls, each mixed
orientation, duplicate tiers and Draco/Tyr combinations. It verifies equipped
attributes, immutable source inputs, canonical holders, codec/stream/hash round
trips, unchanged-source recomposition, and unequip/inventory cleanup. It dispatches
one real `PlayerTickEvent.Pre` per world tick for disconnected ServerPlayers,
using the installed mod's event handler and attachment rather than reimplementing
regeneration. Actual resource deltas and the 100-tick cadence are checked over
205 world ticks with empty native storage/headroom. Explicit fractional-policy
controls distinguish attribute composition from native integer truncation.
Coexistence additionally checks additive mixed Boots and Leggings contributions.
`forbidden-native-save.txt` records a native-only control for Valhelsia's
unregistered attribute holder during entity saving. Original Tyr alone reproduces
it; a fresh mixed-only player saves cleanly. Equipping natives before mixed
retains the old native AttributeInstance and its warning. The fixture verifies
that warmed player's real Aureal too; it does not repair upstream entity state.

The hidden/muted client receives actual server equipment/attribute packets for
the native/mixed cases; it never sets client equipment or attributes. Native
base/trim submissions, split waist/leg selection, GUI/hand/fixed item textures and
normal/advanced Aureal tooltip lines are compared before/after a real reload.
A named, damaged Tyr mixed control in inventory and a trimmed dual-effect Tyr
root worn on the player survive native disconnect/save/reopen with a new server/
player, retained UUID, complete source data and Aureal on both endpoints.
Only the relevant modifier tooltip frame is captured; final gameplay, animation,
glint and visual usability remain owner checks. The inherited early-hidden GLFW,
muting and disabled NeoForge early-window contract applies unchanged.
The client intentionally exercises native items before mixed, so its player-save
log can contain that controlled native `attributes` serialization warning even
though the complete mixed item and its sources persist. Unrelated native block-
model missing-texture and NeoForge shader sampler warnings are not armor-render
failures; native/mixed armor material checks remain mandatory. Actual error-level
diagnostics fail this profile, including the JEI linkage error described above.

Eternal/Soulbound effects, smithing and presentation have their own maintained
checks above. Hephaestus Forge transmutation of mixed armor remains outside scope.

On 2026-11-06 the owner confirmed the ordinary armor/Aureal manual checklist,
including regeneration/removal, presentation, normal workstations and save/reopen.
Together with the focused automatic evidence, this closes that bounded review;
it does not extend acceptance to the optional modifiers or mixed forge upgrades.

## Forbidden Arcanus Boots persistence

The [maintained Boots modifier guide](../../MismatchedBoots/RuntimeTests/docs/forbidden-arcanus.md)
covers standalone Boots (`-PleggingsForbiddenBootsOnly=true`) and coexistence
(`-PleggingsBoots=true -PleggingsForbiddenBoots=true`) through this same harness.
It loads checksum-pinned published-writer data through native barrel storage,
then checks complete root/history, real packets and save/reopen without repeated
adoption. A separate actual native-mod removal/reinstallation profile covers
paid effects on both armor products. The executable legacy spike and its codec
replacement have been removed; the frozen writer data remains an active fixture.

## Aquaculture 2 compatibility

The NeoForge-only profile resolves `Aquaculture-26.1.1-2.9.2.jar` through Boots'
maintained artifact catalog, download cache and SHA-256 pin. It uses packaged
Leggings with nested Things and optionally current built Boots, never indev JARs.
The normal profile runs a dedicated endpoint followed by the hidden/muted client.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAquaculture --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAquaculture -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAquaculture -PleggingsAquacultureServerOnly=true '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

Omit `--offline` for initial downloads. Evidence uses
`build/{reports,verification}/<baseline>/aquaculture[-boots][-server-only]/`:
`server.ok`, `client.ok`, explicit failure traces, `aquaculture-server.txt`,
`aquaculture-client.txt`, `aquaculture-tooltip.txt`, `aquaculture-render.txt`,
`aquaculture-render-after-reload.txt`, `aquaculture-panel.png`,
`aquaculture-session.txt` and `matrix.ok`. The
`-server-only` variant has its own
directory/marker and deliberately requires no client evidence; do not report it
as a full client matrix. Run profiles/baselines sequentially and restore normal
`.114` packaging after a floor run.

Disconnected ServerPlayers undergo real inventory/fluid priming and native
`travel` comparisons: 128 vertical-motion samples across both outputs and eight
water/input/game-mode conditions. Strict-root, duplicate-source, wrong-slot,
inventory/non-player, actual break, removal/exit-water, foreign `NoGravity` and
non-default gravity controls exercise production queries, not just a predicate.
Native armor carried/worn cannot cancel the effect; native head/chest positives
stay independent. Current-Boots controls include mixed Neptunium feet without
original leggings. An entity serialization roundtrip checks absence of saved
candidate state, not an actual reconnect/restart. Native-only flag leakage is
recorded separately and is not repaired by this integration.

The early client uses a real integrated server and its normal local network
connection. Only the server sets initial equipment/position once per scene;
keyboard input and ordinary client physics then drive movement. Received equipment,
client/server queries, server-tick progress and bounded position differences are
checked for ordinary sinking, both buoyant orientations, crouch/jump and release,
and unequipping. Native carried armor continues its real tick. The fixture never
forces client position/velocity/gravity. Later phases below add relevant rendering
and real saved-world session checks; this movement phase alone does not replace
dedicated multiplayer or owner acceptance.

The hook targets the fixed Minecraft gravity getter with the ordinary required
injection boundary, not an optional native method. No Aquaculture class is linked;
absence is checked through the existing Fabric and NeoForge `verifySkeleton`
profiles. There is no native-body fingerprint or pretend incompatible-API control.

The client also compares actual native tooltip components/styles for both output
orientations and duplicate Neptunium sources, normal/advanced flags and physical
left/right Shift. The test-only input probe overrides keys only during synchronous
captures. Source IDs stay dark gray before the contribution, hidden source/root
details stay hidden, custom names/lore remain, and descriptions never move to the
root. Captures use an empty context/null player and must post exactly one final
root tooltip event, without replaying source events or changing history.

Live tag removal/restoration uses the native registry tag-reload application,
including a native negative control and an unrelated tagged item. This is not a
datapack-network reload acceptance claim. Current-Boots descriptions are checked
before and after the Leggings adapter's deliberately faulting final invocation.
That last check changes only the fixture JVM's private reflected binding: partial
output/mutations must remain isolated, later calls must not retry and exactly one
`Aquaculture leggings source tooltips disabled:` warning is **expected** in each
present-profile client log, zero on the dedicated endpoint. Unexpected errors or
warning counts fail the profile. Run presentation checks before this intentional
disable if extending the same JVM. The dedicated endpoint separately exercises
the bridge's distribution gate without invoking native client code.

The dedicated endpoint also checks paid Neptunium-ingot repair, Mending/Unbreaking/
Swift Sneak books and trim smithing on native leggings and both mixed orientations,
including real XP/material/template consumption, immutable inputs and no repeat
take. It does not invent an additional Aquaculture workstation or item-icon trim.

After movement, the client compares actual native base/trim/foil submissions,
both split legs/waist orientations, posed geometry, a dyed leather partner and
GUI/hand/fixed item textures. The native item stays a static texture even when
trimmed. Checks repeat after a real resource reload, requiring cache invalidation
and a fresh renderer. One relevant eight-cell panel is generated at 1600x1000;
only the disposable profile's vanilla tutorial is disabled to avoid hiding it.
Clients remain hidden before GLFW and muted throughout.

The final session phase equips a named/damaged/trimmed root, disconnects with
native saving, reopens the same world and requires new integrated-server/client-
player instances with the same UUID. Complete root/source equality and client/
server buoyancy must survive; no native armor is left to mask a stale flag.
An actual unequip update must restore ordinary gravity and sinking. This is a
real in-JVM world/server restart and reconnection, not merely an entity codec
roundtrip, but it does not test an external dedicated server's anti-flight rules.
That check and final gameplay/visual acceptance are separate owner gates; passing
the automatic profile alone does not close the compatibility.

On 2026-11-06 the owner confirmed the complete NeoForge checklist, including
native/mixed movement/coexistence, sustained dedicated buoyancy, reconnection and
server restart, presentation/reload, tooltips and repair/enchantment/trim workflows.
Together with the 2026-11-05 automatic evidence, this closes the bounded integration.

## AllTheModium compatibility

The NeoForge-only gameplay profile resolves AllTheModium 4.1.1, All the Ores
4.0.4 and GeckoLib 5.5.2 through Boots' maintained artifact catalog, download
cache and SHA-256 pins. It never reads indev JARs. The normal profile runs a
dedicated endpoint followed by the hidden/muted client. The deliberately broken
tick control remains server-only. Owner acceptance is separate.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAllTheModium --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAllTheModium -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAllTheModium '-Pneoforge_version=26.1.2.99' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAllTheModium -PleggingsAllTheModiumBroken=true --offline --console=plain
```

Omit `--offline` for initial downloads. Reports and bounded process logs use
`build/{reports,verification}/<baseline>/allTheModium[-boots][-broken]/`, with
`server.ok`, `client.ok`, explicit failure traces and `matrix.ok`; the `-broken`
variant requires only `server.ok`. A's earlier gameplay-only evidence is recorded
in the private implementation plan, not equivalent to the expanded normal profile.

Consecutive server callbacks advance disconnected ServerPlayers through the real
Player inventory/effect tick, with a controlled player-tick counter and a forced
fixture chunk for genuine fluid detection. Native controls cover all three tiers,
both mixed outputs with iron/diamond, duplicate/differing qualifying sources,
20-player-tick cures, unrelated effects, water activation and the dry/unequipped
Dolphin's Grace tail. A test-only optional native-method probe checks exactly one
call on the live stack and no calls for idle, wrong-slot, non-player or invalid
roots. Real piglin and damage/snow consumers check pacification, preserved gold
positives, absence of other armor-piece entitlements and optional independent
current-Boots protection. Root and source snapshots must remain unchanged.

The separate `-broken` process removes the native tick declaration through a
test-only Mixin plugin. It requires zero tick effects, preserved piglin safety and
exactly one production guard warning; it does not patch production readiness or
retry another source. Neither test Mixin applies to ordinary absence profiles.

The dedicated endpoint also checks paid native upgrades from netherite through
all three tiers, both extraction orientations, unrelated root/source-state
preservation, real vanilla/custom trim recipes and the existing average durability,
SAME Unbreakable and INTERSECTION fire-resistance policies. The client compares
native base/trim submissions, split waist/legs, walking/crouching, GUI/hand item
textures and normal/advanced tooltips for all tiers and both orientations before
and after a real resource reload. It reuses the native renderer probe and eight-cell
gallery, producing `allthemodium-render.txt`, `allthemodium-render-after-reload.txt`
and one relevant `allthemodium-panel.png`. Fresh evidence is required. Only the
disposable copied world may have its native backup/join confirmation accepted.

Use the existing Fabric and NeoForge `verifySkeleton` profiles for optional-absence
validation. Routine consolidated presentation/workstation coverage is `.114`;
A's `.99` gameplay boundary remains separate evidence. Do not rerun the historical
Boots compatibility matrix or imply a Fabric-present AllTheModium integration.

On 2026-11-05 the expanded standalone/current-Boots server and hidden-client
profiles passed on `.114`, including paid upgrades/trims and before/after-reload
proofs; the representative panel was inspected. NeoForge `.114` and Fabric
`0.19.5` skeleton absence profiles also passed. The owner separately confirmed the
final NeoForge gameplay/visual checklist on 2026-11-05, closing the bounded plan.
A's earlier `.99` gameplay coverage is not a B client-matrix claim.

## Advanced Netherite compatibility

The optional profile resolves Advanced Netherite 2.4.1 through exact Modrinth
coordinates and verifies SHA-256 before staging; no indev JAR is read. It tests
both ordered outputs, native equipped-player pacification, invalid/inventory-only
controls, hook-only sources and defensive provenance, real datapack tag
removal/restoration on already-crafted roots, native tier-upgrade smithing and
source tooltips in the hidden client.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyAdvancedNetherite --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAdvancedNetherite '-Pneoforge_version=26.1.2.99' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAdvancedNetherite '-Pneoforge_version=26.1.2.114' --offline --console=plain
```

Omit `--offline` for the first artifact download. Add `-PleggingsBoots=true` for
current-Boots coexistence. Evidence is under each loader's
`build/verification/<baseline>/advancedNetherite[-boots]/` (bounded process logs
and `matrix.ok`) and `build/reports/<baseline>/advancedNetherite[-boots]/`
(`server.ok`, `client.ok` or explicit failure traces).

This reuses the existing sequential server/client profile harness and its early
hidden-window/muted contract, without screenshots or another launch framework.
Run `verifySkeleton` without Advanced Netherite for optional-absence acceptance;
the seven focused Common plugin tests cover incompatible API shapes and symbolic
linkage. The owner confirmed the separate Shift-held/config-disabled presentation,
armor geometry and mob provocation/retaliation checks on both loaders on
2026-11-04; this is not a manual matrix across both NeoForge baselines.
Material-colored native durability bars are intentionally not forwarded.

## Better Nether compatibility

The Fabric-only profile resolves Better Nether, BCLib and WorldWeaver 26.101.4
through pinned Modrinth Maven coordinates, verifies SHA-256 and uses Gradle's
download cache. It never reads indev JARs or stages Better End.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:test :MismatchedLeggings:RuntimeTests:Fabric:verifyBetterNether --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyBetterNether -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifySkeleton --offline --console=plain
```

Omit `--offline` for initial downloads. Dedicated and hidden/muted integrated
clients check all three native leggings, both mixed outputs, native on/off/on
configuration changes on existing roots, vanilla-positive preservation,
invalid/suspended/unrelated/wrong-slot/inventory negatives and source non-mutation.
The coexistence variant checks Boots and Leggings separately and together without
an original armor anchor. It reuses the existing profile harness and adds no
screenshots. Evidence uses `betterNether[-boots]` under the usual
`build/{reports,verification}/<baseline>/` directories, with `server.ok`,
`client.ok` and `matrix.ok`. Plain `verifySkeleton` provides native-mod absence.

The dedicated endpoint also runs the [native Ruby Fire regressions](Common/src/main/java/games/alejandrocoria/mismatchedleggings/test/BetterNetherRubyChecks.java):
initialized native controls, both composition orientations, paid native Fireruby
upgrades, source-aware book application, retaliation with item wear, live-state
preservation and no resurrection of removed source enchantments. Standalone runs
cover Leggings; current-Boots coexistence additionally covers Boots. Historical
Boots binaries are excluded from the new-grant assertions. Expected root Ruby
Fire I is mandatory, not a configurable pre-fix observation mode. This replaces
the retired `spikes/leggings-betternether` harness without changing production.

The disposable client's WorldWeaver configuration marks its first-launch welcome
as already seen and disables upstream update checks, so quick play can reach the
test world without an interactive startup dialog. Indev settings are untouched;
loading errors are not hidden or bypassed.

Eighteen loader-local unit cases cover the used API/injection guard, unrelated
upstream additions, preserved positives, live reflection reads and once-only
disable/logging on broken boundaries. Packaging verifies optional Fabric mixin
ownership and rejects its leakage into NeoForge. This is not present-mod
NeoForge support, mob-provocation simulation or visual acceptance. Native Ruby
Fire effect checks use the dedicated server's enchantment dispatcher, not a new
connected-player combat simulation. The ordinary smithing suite still owns the
general new-enchantment regression.

The owner separately confirmed the requested Fabric piglin checks for all three
families/both outputs and the appearance, movement and tooltip review on
2026-11-04. This does not turn the automatic profiles into a live mob-AI test or
claim present-mod NeoForge support.

## Create Fly compatibility

The Fabric-only profile resolves Create Fly 6.0.9-4 from its exact Modrinth Maven
coordinate, caches the download and verifies SHA-256 before staging. Neither the
indev instance nor another distribution with mod ID `create` is an input.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:test :MismatchedLeggings:RuntimeTests:Fabric:verifyCreateFly --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyCreateFly -PleggingsBoots=true --offline --console=plain
```

Omit `--offline` for the first download. The sequential dedicated and early-hidden,
muted client endpoints exercise native Cardboard recognition, both mixed outputs,
one/two Cardboard sources, strict invalid/suspended/spoofed controls, equipped
versus inventory presence, full/incomplete sets, crouching/flight, scaled box
dimensions and independent coexistence with current mixed Boots. The dedicated
endpoint additionally exercises native Full Stealth, mob target/attacker clearing
and the real Belt passenger gate, distinct from its full-set transport helper.
No original feet/legs anchor is required, and the native effect implementations
are not replaced. A real smithing transaction additionally compares native and
mixed Cardboard trim results in both orientations, including output extraction
and ingredient payment; it does not require the excluded Arts and Crafts award.

The client also compares native and source-local descriptions in normal/Shift
and advanced modes with Create's tooltip config on/off/on, including both mixed
orientations, hidden source tooltips, absent players, source IDs, separators,
unrelated lore/attributes, non-mutation and exactly one final tooltip callback.
The coexistence profile covers Cardboard and both Diving Boots as well as
Cardboard Leggings. Controlled test-only window-key input feeds the real
`Minecraft.hasShiftDown()` and native modifier, following the same input boundary
as Fabric client GameTests; it never substitutes Create's description output.

Presentation uses a focused Cardboard gallery, not the unrelated general render
matrix: native/mixed ordinary and trimmed leggings, both orientations, walking/
crouching, native full-set boxes, current mixed feet when present, root foil,
GUI icons and native hand rendering. Assertions compare native equipment
materials and Create trim sprites, require split views with the correct leg,
resolve item contexts and exercise native box/overlay consumers. A real resource
reload must replace the native renderer and clear the geometry epoch before
repeating those checks. Clients remain early-hidden and muted.

Fresh `cardboard-render{,-after-reload}.txt` and
`cardboard-panel{,-after-reload}.png` reports are required before `matrix.ok` can
be written. Only these integration-specific panels are captured; owner gameplay/
visual review remains separate from the automated assertions and panel inspection.

Evidence uses `createFly[-boots]` beneath the usual
`build/{reports,verification}/<fabric-loader>/` directories (`server.ok`,
`client.ok`, `matrix.ok` or failure traces). Plain `verifySkeleton` checks
optional absence; packaging checks Fabric-only mixin ownership, no required
Create dependency and no bundled Create classes. Loader-local unit tests cover
the required API/semantic anchor without fingerprinting unrelated mixin code.
The profile does not substitute for owner acceptance. Fuel inheritance and Arts and
Crafts on mixed smithing outputs are excluded from the initial integration.

The owner confirmed the separate Fabric item/equipped, movement, native/mixed
disguise, reload, tooltip/config and trim-smithing checklist on 2026-11-05,
closing this bounded integration. This does not extend present-mod support to
NeoForge or other Create distributions/versions.

## Better End compatibility

The Fabric-only profile resolves Better End, BCLib and WorldWeaver 26.101.4
through pinned Modrinth Maven coordinates and verifies SHA-256. Downloads use
Gradle's cache; indev JARs/settings are never fixture inputs. No Better Nether
artifact is staged by this profile.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyBetterEnd --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyBetterEnd -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifySkeleton --offline --console=plain
```

Omit `--offline` for the first download. The dedicated server checks both ordered
outputs, native Crystalite full-set dispatch/Regeneration I, actual equipped
maximum health under default composition rules, ordinary-tier negatives,
invalid/suspended/unrelated/wrong-slot/inventory negatives, source non-mutation,
live datapack tag removal/restoration and preservation of native tag positives.
It also checks paid native Terminite-to-Aeternium smithing. Current-Boots
coexistence covers both output orientations of both pieces completing the set
without an original legs/feet anchor.

The hidden/muted client checks that native leggings retain their fixed health
description while mixed sources omit it and its extra spacer, with one or two
Crystalite sources. Effective root health text must match native attribute
rendering. It also checks exactly one native root set-bonus line, equipped
complete/incomplete-set colors, unequipped and null-player tooltips, in normal
and advanced modes. It reuses the WorldWeaver first-launch configuration above.
The Fabric client also directly exercises the production Crystalite capture
adapter against native renderer callbacks: geometry/UVs/normals/materials,
complementary split and selected leg, regular/slim/crouched/seated/baby/posed rigs,
visibility, queued pose independence, bounded cache reuse, root foil and render
fields, unsupported requests and source non-mutation. Native decorated controls
also verify that Crystalite still ignores its own trim/foil components. It repeats these checks
after a real resource reload, without manually resetting the adapter. Fresh
`crystalite-capture.txt` and `crystalite-capture-after-reload.txt` are mandatory.
The same fixture now exercises production dispatch for both orientations, double
Crystalite, root foil and ordinary equipment, including a known custom source
without a standard equipment asset. An unknown renderer registered during client
initialization must never be called. Controls check safe complete Crystalite /
standard owners versus neutral, root/source non-mutation and bare invalid data.
Fixture-only mixins drop one probe callback and reject the second split after the
first prepared successfully: the actual guard/fallback must reject partial output,
disable retries for the failed epoch and recover in a new epoch. The injected
incompatible probe intentionally produces one adapter warning, not warning spam.
No production testing switches or upstream-mod renderer mixins are used.
The two fault-injection mixins use optional string targets so Boots-only profiles
remain runnable without Leggings; the reduced no-viewer Boots-only gate passed.
The real armor-layer/deferred renderer draws one bounded native/mixed gallery:
both orientations, double Crystalite on a posed small stand, native/mixed crouch,
walking and root foil with a dyed/trimmed ordinary half. The hidden client captures
`crystalite-panel.png` and `crystalite-panel-after-reload.png`; both fresh nonempty
panels and both proof files are mandatory before `matrix.ok`. In coexistence,
the gallery also requires both native Boots halves to render before/after reload.
These focused screenshots do not add unrelated compatibility pictures. Agent
inspection is separate from owner visual acceptance.
The owner confirmed the final Fabric model checklist on 2026-11-05: both
orientations, movement/posed presentation, root foil and ordinary-side trim,
resource reload, and unchanged icon/tooltips. The rendering plan is closed;
this does not certify arbitrary shaders or a present-mod NeoForge integration.
Evidence uses `betterEnd[-boots]` beneath the usual
`build/{reports,verification}/<baseline>/` directories, including `server.ok`,
`client.ok` and `matrix.ok`. No present-mod NeoForge support is claimed.

## Optional JEI/REI and user-guide acceptance

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:prepareViewerArtifacts
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:prepareViewerArtifacts
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyViewers -PleggingsViewer=jei -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyViewers -PleggingsViewer=rei -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyViewers -PleggingsViewer=none --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyViewers -PleggingsViewer=jei -PleggingsViewerBootsOnly=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyViewers -PleggingsViewer=jei -PleggingsBoots=true '-PleggingsBootsJar=<published-boots.jar>' --offline --console=plain
```

Use the same `verifyViewers` commands on NeoForge, sequentially on .99 and .114.
Pass `-Pneoforge_version=26.1.2.99` for the floor and leave normal outputs on .114.
Boots can be omitted or supplied as the explicit unchanged binary with the usual
`-PleggingsBootsJar=<path>` switch. JEI and REI are tested individually, never
together. Run each viewer with no Boots flag (Leggings alone), `-PleggingsBoots=true`
(both current products), `-PleggingsViewerBootsOnly=true` (Boots alone, no Leggings
product staged), and the explicit published JAR. The no-viewer control covers both
standalone products. Paths use `viewers-<none|jei|rei>[-boots|-previous-boots|-boots-only]`. The disposable
client world is reset before copying the dedicated server's seed. Clients stay
hidden before GLFW creation, muted, with NeoForge's early window disabled. No
screenshots or indev JARs are needed.

The pinned NeoForge REI JAR still uses deprecated `@OnlyIn` annotations. In this
isolated REI profile only, `neoforge.warnings.onlyin.hide=true` prevents NeoForge's
development-only warning screen from blocking quick play. Its diagnostics remain
in logs; actual loading errors and other warnings are not suppressed. Production
code and normal indev run settings are unchanged.

With pinned NeoForge REI 26.1.819, the dedicated dev fixture also logs its own
`DefaultRuntimePlugin`/`DefaultPlugin` failures loading client-only `LocalPlayer`,
followed by failed server-side recipe display filling (including ordinary vanilla
recipes). These are REI registration failures, not successful coverage of those
REI server facilities. They remain visible in `server.log`; product server checks
and actual client category/query/reload checks are separate gates. REI's reload
task/DisplayRegistry warnings also remain visible. This suite does not repair
upstream REI or infer that every server-side viewer feature works.

The profile starts packaged dedicated and integrated endpoints. Actual JEI/REI
queries and screens verify one linked cycle, both orientations, repeated opening
while already open, closing/reopening, and independent product contributions in
one Things-owned category. It rejects separate current-product categories and
checks the shared cycle count against installed products. Published Boots keeps
its old category, which is explicitly checked rather than treated as a duplicate.
For current products, REI additionally obtains both output slots from the actual
category widgets, collects their hovered tooltips and invokes the installed
platform tooltip renderer. It requires the two source boundaries and rendered
text, and rejects a boundary classified as both text and image (which would append
duplicate separators). These checks repeat across recipe reloads and reconnect;
the Boots-only control also renders both tooltips. Published Boots' own legacy
separator is outside this Things-marker regression, not claimed as hover coverage.
A real datapack overrides the Leggings recipe with an ordinary crafting recipe, then a
fixture workbench recipe that reverses its results, then restores the native
recipe. Fresh examples must disappear/change/return accordingly, without touching
the production cache directly, and Boots' contribution must remain available.
After disconnect, authority must be empty; the
same world then reopens with a different server policy, and the displayed armor
values must follow that policy. The no-viewer case exercises real recipe/rule
synchronization and reload without resolving optional APIs at runtime.

The Boots-only control deliberately omits the Leggings product: its dedicated
endpoint checks Boots' loaded workbench recipe and rejects a Leggings route; its
hidden client checks only Boots examples, repeated opening and world reconnect.
Fixture loader metadata therefore requires Things, with explicit product-graph
assertions selecting the appropriate profile. Server and client checks stay in
separate classes so dedicated startup never resolves client screen classes.

For an explicit previous binary, the profile checks its SHA-256 before/after the
run and stages the current core without modifying the input JAR. Record provenance
and the hash with acceptance evidence; a locally rebuilt 1.0.0 is not the published
release. Published binary paths are supplied explicitly, not inferred from indev.

Both loaders use Things' shared SHA-256-pinned
[catalog and resolver](../../MismatchedThings/RuntimeTests/README.md#third-party-test-artifacts).
The local [viewer profiles](viewer-fixtures.gradle) select JEI/REI and their dependencies. Run
`prepareViewerArtifacts` online once before offline verification. Optional APIs
are compile-only and are never bundled in the production product. Human appearance
and discoverability acceptance remain separate; the player workflow is in the
[product README](../README.md).

### Native mixed-usage regression

Normal JEI viewer profiles query bare and valid mixed Leggings (both orientations),
plus current Boots when installed. Generic smithing usages must be absent while
ordinary armor retains them. The check repeats after reconnect. Historical Boots
keeps its separate published-consumer checks; tag-information pages are not
transformation examples and remain available.

REI's development registry can lack static smithing displays even for ordinary
armor, so an empty query would be an invalid success criterion. Its profile instead
invokes the installed upstream trim-display factory on a real loaded recipe,
requires ordinary boots/leggings entries and rejects mixed inputs/outputs. These
displays are inspected separately, not injected or claimed as registered queries.
No dynamic source-aware usages are implemented. The temporary contrast datapack,
opt-in investigation flag and verbose sample logs were retired after the spike.
Enchanting and gameplay coverage lives in the presentation and smithing profiles,
not duplicated here. Clients retain the hidden/muted contract without screenshots.

## Missing-source preservation and recovery

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyRecovery --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRecovery --offline --console=plain
```

Use the sequential NeoForge .99/.114 baselines and optional `-PleggingsBoots=true`
with `-PleggingsBootsJar=<unchanged jar>` as below. Artifacts come from packaged
outputs/that explicit read-only binary, never indev. Paths use isolated
`recovery[-boots|-previous-boots]`; `write` resets only that disposable profile.
Clients are hidden before window creation, muted, and NeoForge's early window is
disabled. There are no screenshots. The separate launcher is
[recovery.gradle](recovery.gradle).

One actual barrel/world crosses independent dedicated JVMs: `write`, `absent`,
`absentagain`, `partial`, `sources`, `restore`, `reloaded`. A temporary source item
and foreign paid-wallet registration disappear/return through fixture registration,
not an alternate decoder. `partial` registers both sources but fails the foreign
component's codec; `sources` returns both sources without the wallet component.
Only full native readiness may activate the result. Source snapshots, root-only
foreign state, paid longs above double precision, typed NBT, live damage,
enchantments, passive naming/removal, repeated saves and ordinary/bare controls
are checked. Policies change after the first save to detect accidental recomposition.
Optional unchanged Boots exercises its own suspended/recovered root in the same
barrel, proving both early wrappers participate without cross-capturing products.

`write`, `absent` and `restore` also use independent TCP clients, not integrated
servers. Full equipment/inventory snapshots are compared after synchronization.
The absent phase exercises native death, a real client respawn packet, pickup and
re-equipping; the other root persists as a native item entity and must recover
after reconnect. Focused actual-codec checks cover early source checks, conflict/
quarantine stability, native JSON and safety refusal; native menus cover safe
rename versus blocked mutations, combat wear and Mending. `refused` and `bypassed`
alter the real recursive-codec factory/operation and require refusal before world
creation (not just a nonzero exit). Common unit tests cover defensive archive
encoding, network length/heap bounds and nested-history admission.

`-PleggingsRecoveryStages=<comma-separated stages>` selects an ordered subset for
investigation; later stages require earlier saved evidence. A subset marker is
not a full-matrix result. These controlled registrations do not claim a particular
third-party version-upgrade guarantee. Owner normal-play acceptance remains separate.

## Source-wise smithing and recomposition

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifySmithing --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySmithing --offline --console=plain
```

Use the sequential baselines and optional Boots/current-or-unchanged-JAR switches
below. Isolated paths use `smithing[-boots|-previous-boots]`. Only packaged
products are staged, never indev files. The disposable client world is reset;
the client stays hidden/muted and NeoForge's early window is disabled. This gate
adds no screenshots.

Dedicated checks use native menus/recipes and normal/shift extraction: one/both
eligible sources, both orientations, netherite, source-wise trims including a
one-sided trim and repeated no-op trim, native slot admission, exact payment and
unpaid close. Native transform-recipe fixtures installed through recipe-manager
apply cover two distinct template-free recipes, unsupported/no-op/wrong-slot/
multi-count results and dedicated root precedence. The original recipe graph is
restored afterwards. This tests native apply/finalization, not filesystem datapack
discovery. Actual crafted-recipe calls are observed by a test-only probe: both
distinct recipes are credited on take, while two sources using one recipe are
not credited twice. Removed/replaced recipes and newly loaded dedicated root
priority must block stale normal/shift extraction without spending inputs.

Assertions distinguish derived capacity/attributes/materials from live root
damage/enchantments/name/prior work and spent/removed opaque resources. They also
check an unchanged projected field following its new source, a live override
winning, and paid damage clamping under a smaller new capacity. Native items and
optional Boots remain controls at shared vanilla hook boundaries. The hidden
client opens the actual smithing table by packet, compares complete synchronized
previews and paid outputs, and upgrades then trims that same live result using
normal and shift pickup. Manual appearance acceptance remains separate.

The server also tests new-enchantment grants from source transformations: both
orientations, independent second-source and duplicate grants without a composition bonus, stronger live levels,
deliberately removed source enchantments, live name/damage/prior work/resources,
input non-mutation and actual normal/shift pickup/payment. Add `-PleggingsBoots=true`
to run those cases for current packaged Boots as well; unchanged historical Boots
is excluded from the new-grant assertions. These controlled native recipe fixtures
do not require Better Nether or any indev JARs.

### Smithing reload regression

Add `-PleggingsSmithingReload=true -PleggingsBoots=true` to `verifySmithing`,
using current products, not a historical Boots JAR. The isolated
`smithing-boots-reload` variant extends the normal hidden packet-driven client
with 24 cases: Boots, Leggings and vanilla equipment; normal/shift result
clicks; no change, recipe removal, replacement and equivalent-body reload.

Recipes come from a disposable filesystem datapack applied by native resource
reload while the menu stays open. The fixture verifies actual loaded recipe bodies
and captures authoritative state after the server handles each result-click
packet, then waits for the client's slots, cursor and inventory to match that
authoritative snapshot. Original packs are restored afterward. It does not force preview refresh,
alter production code, use indev JARs or take screenshots.

Both current mixed products must reject an existing source-wise preview after
removal, replacement or equivalent-body reload, without spending inputs. No-reload
positive controls must succeed. Native equipment retains its own behavior and
is not patched by this guard. The ordinary server profile additionally checks
Boots' distinct source-recipe credits, fallback beneath a native empty result,
and newly added dedicated-root priority while preserving source holder identity.
Root-owned JDT installation and terminal rejection have their own Boots gates;
this fixture does not claim coverage of arbitrary paid compatibility handlers.

## Grindstone and bounded XP

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyGrindstone --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyGrindstone --offline --console=plain
```

Use the same sequential baselines, `-PleggingsBoots=true` and optional unchanged
`-PleggingsBootsJar=<path>` controls as repair below. Paths use isolated
`grindstone[-boots|-previous-boots]` variants, with a fresh disposable client world.
Only packaged artifacts are staged; client windows stay hidden/muted and
NeoForge's early window stays disabled. This profile adds no screenshots.

Dedicated tests use actual menus, normal/shift extraction and real XP orbs.
They prepare the fixture chunk before acceptance so its entities are observable,
then check a native XP-award control. Tests cover either single-input slot, both
root orientations and donor identities/orders, unchanged provenance/live state,
root capacity versus a larger donor, native curse priority/name/prior work,
unenchanted and curse-only inputs, invalid/bare/malformed/count/cross-product
negatives, cancelled previews and exactly-once payment. Orb totals include merged
orb counts, not just entity count. A test-only invoker observes the native
per-input XP helper after the product hook; the real payout must also lie in the
bounded vanilla random range. Protection I + I proves the nonlinear merged-cost
cap, an odd one-sided baseline proves downward rounding, and later paid root
enchantments/ordinary donor value retain their full positive contribution.

Ordinary items and optional Boots remain pass-through controls at the shared
menu/XP hooks. NeoForge additionally exercises its native combine-repair veto.
The hidden client opens the native screen through a block-use packet, compares
the full synchronized preview and confirms authoritative extraction, input
consumption and XP rather than relying on client pickup prediction. Owner manual
acceptance remains separate. Smithing has its own gate above.

## Anvil and crafting donor repair

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyRepair --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRepair --offline --console=plain
```

Use the sequential baseline rules and optional `-PleggingsBoots=true` switch
below; `-PleggingsBootsJar=<path>` can retain an unchanged earlier Boots binary.
Runs/reports/verification use isolated `repair[-boots|-previous-boots]` paths.
The profile uses packaged products, never indev JARs, and its client must stay
hidden/muted with NeoForge's early loading window disabled. It adds no screenshot.

Dedicated checks exercise actual native anvil previews and normal/shift extraction:
both source identities/orientations, material count and XP payment, rename/name
removal, prior-work/cost limits, compatible/conflicting/wrong-slot books, native
level caps, unpaid close and invalid/bare/malformed/cross-product inputs.
Crafting checks use actual sparse 2x2/3x3 menus and a real Crafter's native
dispense method with barrel output. They check input order/consumption, the
five-percent bonus/capacity bound, curse/name/prior-work rules, no player XP
payment and preserved provenance/opaque live components. Native ordinary repair
and optional Boots hooks remain controls, rather than isolated policy calls.
NeoForge additionally uses a fixture item that vetoes combine repair.

Repair-material reload checks apply native pending item tags (including holder
memberships), restoring them afterwards. Newly composed roots use the new tag;
existing roots retain the union stored during composition, matching Boots. This
is the native tag-apply boundary, not filesystem datapack discovery coverage.
The client opens the real anvil with a block-use packet, checks full synchronized
previews and paid output/XP, then extracts an inventory-grid repair through a real
container packet. Owner manual acceptance remains separate. Grindstone has its
own gate above, as does source-wise smithing.

## Tooltips and vanilla leggings semantics

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyPresentation --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyPresentation --offline --console=plain
```

Use the same sequential baseline rules and optional `-PleggingsBoots=true`
coexistence switch as the profiles below. Disposable paths use
`presentation[-boots]`. The dedicated server reuses the existing source/equipment
and native combat-wear/Unbreaking/Mending controls, without rerunning the separate
composition-arithmetic fixture under this profile's nondefault workbench config.
Controls check narrow armor/leggings enchantment tags without broad slot/trim
membership, supported/primary eligibility for every loaded enchantment against
native iron leggings, native table
candidates and survival-anvil source admission/conflicts, invalid roots and native
pass-through. Actual equipment updates apply/remove Swift Sneak's sneaking-speed
attribute, with a wrong-slot control. Native piglin-safe armor and freezing queries
exercise gold/leather behavior in both orientations, plus absent-source,
malformed/foreign-root and unrelated-tag negatives. Optional Boots checks exercise
its own tag and book-admission paths alongside Leggings' locally gated hooks.

The hidden client checks ordered source headers, natural/custom names, native
trim/lore/blank lines, effective-root attributes/enchantments, live state versus
provenance, advanced source IDs, diagnostics and native hide-tooltip settings.
It exercises ordinary and advanced tooltips through the real item-tooltip route
and captures one relevant `tooltip.png` showing native separator rendering.
Source providers retain their normal/Shift behavior; no artificial Shift mode or
optional-mod tooltip bridge is introduced. Owner readability acceptance remains
separate. Suspended-state diagnostics and their registered schema belong to the
recovery gate above. The workstation gates above cover H–J.

## Item rendering

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyItems --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyItems --offline --console=plain
```

Use the same sequential loader/baseline rules and `-PleggingsBoots=true` option
as equipped rendering. This profile has isolated `items[-boots]` run/report paths.
The shared server seeds a disposable world; the hidden client resolves actual
factory-created products in every display context and exercises native GUI,
third-person hands, ground/frame entity renderers and actual first-person hands.
Controlled source geometry checks anatomical orientation, half area/full scale,
both source orders, live-root foil, preserved provenance and existing output layers.
Native leather/trim layers, special-renderer pair-wide fallback, empty/missing
assets, bare diagnostics, unrelated-item pass-through and stable GUI identities
have focused controls. Fabric repeats resolution with API-emitted mesh-only sources.
The warning for `leggings_test:absent` is deliberate missing-asset coverage;
other model/mixin errors must still be investigated.
The client really reloads resources, requires a new native model identity and
repeats assertions and actual submissions. One `items-panel.png` is relevant
visual evidence; automated/agent inspection is not owner manual acceptance.

For an unchanged earlier Boots binary, pass `-PleggingsBoots=true` and
`-PleggingsBootsJar=<path>`. It is copied read-only and hash-checked after the
run; this variant uses `items-previous-boots`. It also stages the candidate core
explicitly so an equally numbered earlier unpublished nested build cannot win
selection. This focused coexistence check does not replace the final published-
consumer evolution matrix. Normal profiles still load Things only from Leggings.

### Opt-in empty-source item-model regression

Add `-PleggingsEmptyItems=true -PleggingsBoots=true` to `verifyItems`, using both
current products, not historical Boots. The isolated `items-boots-empty-model`
variant uses the fixture resource definition `minecraft:empty` through source
`item_model` components and requires truly zero native layers. Eight pairs cover
normal controls, empty left/right and both empty for each product, in all display
contexts, before and after native resource reload. No indev mods or external packs
are read. Default item-profile behavior is unchanged.

The relevant `empty-item-models.png` replaces the ordinary item panel. It renders
the actual GUI hook and native third-person hands. The actual first-person player
hands also cycle opposite source orders for both products; submission observations
must repeat after reload. Clients retain the hidden/muted contract.

Both products must select their complete diagnostic geometry for any zero-layer
source, including both-empty pairs. Actual hand submissions must contain both
diagnostic materials, not a surviving source half. Root foil overrides are checked
with and without glint in every context; FIXED controls preserve preceding output
layers for normal and diagnostic pairs. Rendering must not change root components
or source provenance. The ordinary item assertions still run alongside these checks.
No equipped-armor or arbitrary transparency guarantee is implied.

## Equipped rendering

The maintained [Ice and Fire block C checks](#ice-and-fire-presentation-and-lifecycle-block-c)
run automatically with the native equipped profile; historical spike evidence is
retained separately from production and owner acceptance.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyEquipped --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyEquipped --offline --console=plain
```

As with the workbench profiles below, optional `'-PleggingsBoots=true'` stages
current packaged Boots. For NeoForge, `'-PleggingsIceAndFire=true'` additionally
resolves the existing exact pinned Ice and Fire/Uranus/Jupiter artifacts through
Boots' hash-checked resolver, never from indev. Add `'-Pneoforge_version=26.1.2.99'`
for the floor and restore .114 afterwards. Loader/baseline runs remain sequential.

The client profiles share [client-profiles.gradle](client-profiles.gradle);
they do not share run/report state. Equipped paths use
`build/{run,reports,verification}/<baseline>/equipped[-boots][-ice-and-fire]`.
The server checks the packaged item/recipe setup and saves the disposable world;
the hidden client loads a copy and renders **real factory-created mixed leggings**
through the production armor mixin, not the old spike marker/interceptor.

Focused checks retain the spike's native vertex-area/UV/normal, mirrored/rotated
child, empty ancestor, independently animated child, crouch/seated/player/slim/
baby/stand and queued A → B → A controls. They also verify all buffered native
material fields/pose/state, source dye/trim, live-root foil on both sides,
pair-wide rejection of unknown submissions, complete-owner/neutral fallbacks and
native admission of valid pairs whose owner has no asset. Fabric registers a
synthetic custom renderer that must never be called by mixed rendering. NeoForge
checks final replacement resolution once per source, unsupported models and,
optionally, all 30 pinned Ice and Fire replacements/13 decorated waists.

Cache controls require only two geometry builds across 30 repeated two-side
frames with at least 60 hits, bound retention to 128 native waist identities under
fresh-model churn, preserve an already queued view after eviction, and require a
real resource reload to clear/rebuild templates and recreate native renderers.
Fresh success/failure markers and process exit remain mandatory. The profile
saves one relevant `equipped-panel.png` for visual inspection; it does not capture
unrelated UI screens. Agent visual inspection is not owner manual acceptance.
No product/Things version changes or native-mod gameplay compatibility are implied.
The optional pinned graph can warn about absent `AutoConfig`/`AutoConfigClient`
targets from Jupiter's `jupiter.mixins.json` when that integration is not installed;
these are foreign integration warnings, not Leggings renderer/mixin failures.

## Workbench and synchronization

Run sequentially from the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyWorkbench --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyWorkbench --console=plain
```

Add `'-PleggingsBoots=true'` to either command to stage both packaged products.
For NeoForge's floor, add `'-Pneoforge_version=26.1.2.99'`; afterwards rerun without
that override to restore the normal .114 outputs. Optional `--offline` and both
properties propagate to the bounded child Gradle launches. Do not run loader
suites or NeoForge baselines concurrently: they share production build outputs.

Each command starts a separate dedicated server and hidden client. The client
loads a copy of the saved fixture world, then uses actual block-use and container
packets against its integrated server. Following
[Boots' composition-rule client test](../../MismatchedBoots/RuntimeTests/Fabric/src/gametest/java/games/alejandrocoria/mismatchedboots/gametest/CompositionRulesClientGameTest.java),
it verifies two real sessions with different config files. The first starts
with maximum attribute rules and separate durability; editing the files during
play must not alter either authority or client previews, including a paid inverse.
After a real disconnect and same-world reopen, normal server-start hooks must load
minimum attribute rules and average durability and the new connection must receive
both together. Native join/ready/S2C handlers deliver the complete snapshot; the
fixture never overrides server-start loading or directly installs successful
client rules. No production command or testing-only production hook is needed.
Full `ItemStack` equality checks both previews and paid inventory outputs on each
side, not merely their item IDs. The client also checks disconnect reset and the
absence of fabricated previews before synchronization.

Dedicated checks cover source admission, loaded product routes, rejection of
cross-slot inputs, either output taken first, exactly-once payment, input locks,
shift-click and paid/unpaid close cleanup. A test-only native recipe-manager apply
invoker exercises removal and holder replacement, including stale pickup/shift
rejection and preservation of a paid inverse. This is the apply boundary of a
reload, not a separate filesystem datapack-discovery test. Rule codec checks cover
registered/absent numeric declarations, full atomic round trips, trailing data,
invalid counts, duplicate IDs and protected structural fields.

Disposable runs are under `build/run/<baseline>/workbench[-boots]/<server|client>`.
Fresh markers are under `build/reports/<baseline>/workbench[-boots]/`; subprocess
logs and `matrix.ok` are under `build/verification/<baseline>/workbench[-boots]/`.
The normal skeleton profiles remain separate and exclude Boots. The client-window
contract below applies to both suites; no screenshots are required for these
transaction/state assertions.

## Skeleton commands

Run sequentially from the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:verifySkeleton --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySkeleton '-Pneoforge_version=26.1.2.99' --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySkeleton --console=plain
```

The aggregate checks packaging and both loaders sequentially at the selected baseline. The
last command restores and checks the usual NeoForge .114 artifact after testing
the .99 floor. Fabric uses the root Loader/API versions. Add `--offline` when
dependencies are cached; child runs inherit that flag and the NeoForge version.
Do not run both NeoForge versions concurrently: build outputs are shared.

`verifyPackaging` checks identity, dependency bounds, exact nested-core bytes,
required product resources, absence of Boots/test/spike content, and independent
indev classpaths. Each runtime suite then starts a dedicated server and client in
separate bounded JVMs, requiring fresh success markers and successful process exit.
The client opens a copy of the disposable server world so item components are
bound by real world/datapack loading, not synthesized at the title screen.
They check standalone loader selection, packaged class origin, inert bare-item
registration and the shared station. The server also exercises defensive source
snapshots, same-item component distinctions, ordered persistent/network root
round trips (including explicit component removals), missing items, invalid
counts/native components, disguised/nested sources and hostile recursive wire
input. A successful round trip after rejection checks recursion-guard cleanup.
Equipment checks cover the owner's flags/asset, custom sound selection, strict
root classification and actual player use/slot/dispenser queries, with vanilla
leggings as a pass-through control. The client checks the bare diagnostic tooltip,
round-trips the registered pair component and draws its current item model for
several frames. Factory checks additionally cover asymmetric/same-item pairs,
owner/unknown/removed fields, durability rounding and numeric resource bounds,
prior work, repair material union, enchantments, identity/operation/slot arithmetic,
invalid modifiers, explicit policies and TOML schemas. Native combat-wear entry,
seeded armor Unbreaking and real XP pickup exercise both roots without changing
their provenance. NeoForge injects a test-only effective modifier and mutates the
query copy to verify the native boundary and source isolation. This does not claim
workbench, synchronization, mixed rendering or missing-mod recovery acceptance.

Per-phase logs live in each loader's `build/verification/<baseline>/`; assertion
reports live in `build/reports/<baseline>/`. No test IDE configurations are generated.
Development authentication/profile lookup warnings, the loopback server's explicit
offline-mode warning and native loader/Gradle warnings are not skeleton failures.
Inspect new errors rather than treating a zero exit code alone as acceptance.

## Client window contract

Automated clients must be hidden before window creation, not hidden on the first
tick. The test-only [window mixin](Common/src/main/java/games/alejandrocoria/mismatchedleggings/test/mixin/HiddenWindowMixin.java)
sets `GLFW_VISIBLE=false` before `glfwCreateWindow` and checks initial visibility.
Tick checks assert it stays hidden without silently hiding it again. Disposable
options mute master volume and disable narration; NeoForge's early loading window
is disabled. Keep this contract for new client fixtures and focused runs. These
hooks must never enter production artifacts or ordinary indev configurations.

For native block interactions, server-fixture completion does not mean the client
has received its teleport and block updates. Wait for the actual client block and
nearby player, plus native server load/teleport acknowledgments, before the opening
click. The test-only `ServerInteractionAccessor` observes the pending teleport;
it does not clear it or bypass admission. Retain the normal interaction packets
and bounded failure timeout rather than opening menus directly or retrying blindly.
