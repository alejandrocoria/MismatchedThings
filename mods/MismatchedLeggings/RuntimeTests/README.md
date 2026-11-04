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

Fabric shares Boots' existing checksum-pinned download definitions. NeoForge's
exact URLs and upstream SHA-512 values are in [mod-artifacts.json](mod-artifacts.json),
resolved by [viewer-fixtures.gradle](viewer-fixtures.gradle). Run
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
