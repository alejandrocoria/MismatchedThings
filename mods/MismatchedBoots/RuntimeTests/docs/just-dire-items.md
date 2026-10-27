# Just Dire Things — Lava Repair, wear, refunds and item bars

Use the shared [execution, path and artifact rules](../README.md) before running
these gates. All commands run from the repository root, not this directory;
non-command paths below remain relative to `mods/MismatchedBoots/` unless stated
otherwise. Explicit file-path `-P` overrides remain repository-relative or absolute.

## Index

- [Just Dire Things Lava Repair production](#just-dire-things-lava-repair-production)
- [Just Dire Things armor wear / FE production](#just-dire-things-armor-wear--fe-production)
- [Conserved upgrade credits and actual-break refunds — production](#conserved-upgrade-credits-and-actual-break-refunds--production)
- [Dynamic JDT FE/durability item bars — production](#dynamic-jdt-fedurability-item-bars--production)

## Just Dire Things Lava Repair production

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireLavaRepair --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireLavaRepairGuards --console=plain
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
[the correction](../../local-docs/JUST-DIRE-THINGS-LAVA-REPAIR-IMPLEMENTATION.md#86-accepted-healthy-ritual-and-client-motion-correction--2026-10-14).
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

Block B's `:MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireLavaRepairSettings` reuses the native
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

The ordinary `verifyJustDireResources` client matrix now follows the separate
[native-style root-tooltip plan](../../local-docs/JUST-DIRE-THINGS-TOOLTIP-IMPLEMENTATION.md):
intrinsic Lava is the last expanded row, colored by its saved switch, without an
installation requirement or non-Shift ability line. Hidden provenance/root display
restrictions, native config exclusion and no source duplicates remain covered.
The preceding standalone-Lava layout is retained only as dated evidence. Synthetic input and tooltip
structure are not physical/manual screen readability acceptance. The owner
separately confirmed B's focused controls/bindings/tooltip checklist on 2026-10-14,
closing [section 9.3](../../local-docs/JUST-DIRE-THINGS-LAVA-REPAIR-IMPLEMENTATION.md#93-focused-owner-checklist--accepted).

Block C adds a scheduler-owned entity gate:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireLavaLifecycle --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireGroundStompLifecycle --console=plain
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
[section 10.3](../../local-docs/JUST-DIRE-THINGS-LAVA-REPAIR-IMPLEMENTATION.md#103-owner-handoff--accepted);
it is not inferred from synthetic input or the automated .114/.99 matrix.

## Just Dire Things armor wear / FE production

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireArmorWear "-Pneoforge_version=26.1.2.99" --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireArmorWear --console=plain
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
.\gradlew.bat :MismatchedBoots:NeoForge:test --tests "*JustDireArmorWearShapeTest" "-PjustDireArmorWearTransformedClass=mods/MismatchedBoots/RuntimeTests/NeoForge/build/reports/just-dire-armor-wear-production/26.1.2.114/CommonHooks.class" --console=plain
```

Supply the matching .99 export and loader property for minimum-version validation.
Run Live and both Wrapper variants before this command. Without the export property,
four final-shape tests are explicitly skipped; such
a run does not establish actual transformed-class acceptance. The existing
attribute gate's player-hit expectation now follows funded wear conversion rather
than the superseded ordinary-wear baseline; attribute functionality and prices
remain owned by their existing adapters.

The [production plan](../../local-docs/JUST-DIRE-THINGS-ARMOR-WEAR-IMPLEMENTATION.md)
owns the separate A/B evidence, C's consolidated lifecycle record and accepted
ordinary-client checklist. The [joint native-menu lifecycle](just-dire-abilities.md#just-dire-things-ground-stomp-production-lifecycle-85-e)
and Lava lifecycle commands above own real restart/packet/repair evidence, not these isolated faults.
Final C regression passed on .99 and restored .114, including 11 focal shape
units/version, both loader builds and packaged-dependency verification. The
plan's section 10 records exact commands, scoped retry, warnings and exercised/
skipped/cache boundaries. The owner separately confirmed all final ordinary-client
checks on 2026-10-15 (section 10.3), accepting C and completing the bounded
armor-wear/FE A/B/C plan.

### Conserved upgrade credits and actual-break refunds — production

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireRefunds --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireResources --console=plain
.\gradlew.bat :MismatchedBoots:NeoForge:test --tests '*JustDireRefund*Test' "-PjustDireRefundTransformedClass=mods/MismatchedBoots/RuntimeTests/NeoForge/build/reports/just-dire-refunds-production/26.1.2.114/Live-ItemStack.class" --console=plain
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
The maintained [native-menu lifecycle gate](just-dire-abilities.md#just-dire-things-ground-stomp-production-lifecycle-85-e) also snapshots positive credits
before actual widget/input changes and checks exact credit preservation through
its existing tier/recharge comparisons. Its manual Earthquake-removal fixture
discards that purchase credit before a real paid reinstall, which must add it
exactly once; retaining the removed credit would instead be a duplicate purchase.
The owner separately confirmed all final ordinary-client checks on 2026-10-16,
accepting C and completing the bounded upgrade-refund A/B/C plan; this is not
inferred from these automated gates. See sections 6.1/10.3 of
[the production plan](../../local-docs/JUST-DIRE-THINGS-UPGRADE-REFUNDS-IMPLEMENTATION.md).

### Dynamic JDT FE/durability item bars — production

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireItemBars "-Pneoforge_version=26.1.2.99" --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireItemBars --console=plain
```

The [A/B plan](../../local-docs/JUST-DIRE-THINGS-ITEM-BARS-IMPLEMENTATION.md) owns
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

Live additionally takes six actual GPU framebuffer captures: GUI scales 1/2,
each with the same displayed root healthy, worn, then repaired. One compact scene
shows FE alone, both bars, full healthy/worn, empty FE, damaged Unbreakable, native
and vanilla controls. Each decorated icon is compared pixel-by-pixel (including
scaled physical subpixels and outer padding) against an undecorated model reference
with only the expected bars overlaid. A translated/scissored icon and clean
following control prove inherited pose/scissor and restoration; the transitioning
root proves old rows do not persist. A count override is shown for visual review,
not a font-pixel oracle or a promise to avoid arbitrary viewer/decorator overlap.
No test decorator, replacement item model or extra client JVM is needed.

Reports/logs and `live-scale-{1,2}-{healthy,worn,repaired}.png` are under
`build/reports/just-dire-item-bars-production/<version>/`;
worlds/mods/config are disposable in `build/run/justDireItemBarsProduction/<version>/`,
never the owner's indev run. The pinned JDT artifact is hash-checked. Individual
client runs need the verifier's server-prepared world. The verifier rejects
missing/stale captures; screenshots are genuine production rendering in a
**synthetic screen, not owner ordinary-screen visual acceptance**. The final
inventory/hotbar/carried/Workbench/smithing/viewer checklist belongs to the A/B plan.
The owner confirmed that checklist passed on 2026-10-16, completing bounded A/B
acceptance; [the manual record](../../local-docs/JUST-DIRE-THINGS-ITEM-BARS-IMPLEMENTATION.md#83-owner-handoff--accepted)
is separate from the automated framebuffer evidence.

The former `verifyJustDireItemBarsSpike`, flag, candidate and four-layout comparison
screen were retired in A to prevent duplicate decoration or stale fixed-row tests.
Historical findings and PNG/report links remain in the
[exploration](../../local-docs/JUST-DIRE-THINGS-ITEM-BARS-EXPLORATION.md#9-opt-in-renderingquery-spike--2026-10-16)
under `build/reports/just-dire-item-bars/<version>/`, not production acceptance.
