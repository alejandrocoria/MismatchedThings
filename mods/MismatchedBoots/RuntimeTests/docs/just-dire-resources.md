# Just Dire Things — resources and root upgrades

Use the shared [execution, path and artifact rules](../README.md) before running
these gates. All commands run from the repository root, not this directory;
non-command paths below remain relative to `mods/MismatchedBoots/` unless stated
otherwise. Explicit file-path `-P` overrides remain repository-relative or absolute.

## Index

- [Just Dire Things production energy storage (8.3 A)](#just-dire-things-production-energy-storage-83-a)
- [Just Dire Things production live attributes (8.3 B)](#just-dire-things-production-live-attributes-83-b)
- [Just Dire Things consolidation and manual handoff (8.3 C)](#just-dire-things-consolidation-and-manual-handoff-83-c)
- [Just Dire Things root-owned upgrades](#just-dire-things-root-owned-upgrades)

## Just Dire Things production energy storage (8.3 A)

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireResources --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireResourcesGuards --console=plain
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
The same client process also captures native normal/advanced/controlled-Shift
tooltips. The [native-style root-tooltip plan's A block](../../local-docs/JUST-DIRE-THINGS-TOOLTIP-IMPLEMENTATION.md)
now checks abbreviated/full current FE, native global status and rebound/unbound
Toggle Tool hints, supported missing/installed rows with individual switch colors,
intrinsic Lava last only with Shift, config-ineligible/dormant omission and empty-
hint refusal. Root display restrictions, hidden source details, inverse outputs
and unchanged vanilla lore/provenance remain covered. A test-only `Minecraft.hasShiftDown` mixin
is active only during capture; it does not emulate physical keyboard input.
`JustDireTooltipTestSupport` now shares that controlled capture/native FE and
name-only row observation with the native-screen/Lava/lifecycle client. Jump
intensity is checked on the root and actual movement, not printed in its row;
global-off does not repaint individual switches. Tier removal checks expanded
Lava absence instead of the retired standalone status line. No second driver,
state projection or replacement ticking is introduced by this consolidation.
The [tooltip B record](../../local-docs/JUST-DIRE-THINGS-TOOLTIP-IMPLEMENTATION.md#8-block-b-implementation--2026-10-17)
owns its .99/.114 resource/Live/lifecycle/Lava-settings passes and .114 absence,
storage-fault and packaging checks. The owner separately confirmed all five final
physical presentation checks on **2026-10-18** (plan section 6), completing the
bounded A/B plan, not establishing another automated run or manual loader matrix.
Its warning audit separates expected unknown native item/component
warnings from the absent client's previously JDT-populated disposable world;
this is not a tooltip or optional class-loading failure.
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
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireAttributes --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireAttributes "-Pneoforge_version=26.1.2.99" --console=plain
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
event results, foreign routing and readiness edits (`:MismatchedBoots:NeoForge:test`).
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
.\gradlew.bat :MismatchedBoots:Common:test :MismatchedBoots:NeoForge:test :MismatchedBoots:Fabric:verifyGameplay :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireSmithing :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireJump :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireJumpGuards :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireAttributes :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireResources :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireResourcesGuards verifyPackagedDependencies --console=plain
.\gradlew.bat :MismatchedBoots:NeoForge:test :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireAttributes :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireResources :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireResourcesGuards "-Pneoforge_version=26.1.2.99" --console=plain
.\gradlew.bat :MismatchedBoots:NeoForge:test :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireSmithing verifyPackagedDependencies --console=plain
```

The last command restores/verifies the normal .114 production artifact and its
native guard tests after the floor run. Smithing also retains `report.txt` beside
its process log, independently of later resets of shared markers.
Native artifacts, versioned reports, coverage and warning limits
remain owned by the individual gates; a successful matrix is not manual acceptance.
The ordinary-client checklist and closure criteria are in
[the block C record](../../local-docs/JUST-DIRE-THINGS-RESOURCES-IMPLEMENTATION.md#9-block-c-consolidation-and-manual-acceptance).
It retains ordinary root durability and excludes powered abilities, wear/payment
conversion, settings/bars, refunds and broader charging discovery.

## Just Dire Things root-owned upgrades

This production acceptance gate tests the root-owned installation contract in
[JUST-DIRE-THINGS-ROOT-UPGRADES-EXPLORATION.md](../../local-docs/JUST-DIRE-THINGS-ROOT-UPGRADES-EXPLORATION.md),
without replacing production methods. Run sequentially with the other JDT gates:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireRootUpgrades --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireRootUpgrades "-Pneoforge_version=26.1.2.99" --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireSmithing --console=plain
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
[block C](../../local-docs/JUST-DIRE-THINGS-ROOT-UPGRADES-EXPLORATION.md#9-block-c--consolidation-and-manual-handoff).
Final-export unit cases must run immediately after a normal
`verifyJustDireNegateFall` process, before another gate resets its shared run directory.
