# Just Dire Things — movement and cooldown feedback

Use the shared [execution, path and artifact rules](../README.md) before running
these gates. All commands run from the repository root, not this directory;
non-command paths below remain relative to `mods/MismatchedBoots/` unless stated
otherwise. Explicit file-path `-P` overrides remain repository-relative or absolute.

## Index

- [Just Dire Things passive Jump Boost](#just-dire-things-passive-jump-boost)
- [Just Dire Things Negate Fall Damage (8.4)](#just-dire-things-negate-fall-damage-84)
- [Just Dire Things Negate consolidation and manual handoff (8.4 C)](#just-dire-things-negate-consolidation-and-manual-handoff-84-c)
- [Just Dire Things native cooldown HUD production](#just-dire-things-native-cooldown-hud-production)

## Just Dire Things passive Jump Boost

These optional gates use the production NeoForge JAR and the locally supplied
`justdirethings-1.6.11.jar` (SHA-256 verified, no external classes compiled into
production). Override its location with `-PjustDireThingsTestJar=...` for direct
run preparation. They are detached from ordinary `build`/`check`:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireJump
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireJumpGuards
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireSmithing
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
[the production plan](../../local-docs/JUST-DIRE-THINGS-POWERED-JUMP-IMPLEMENTATION.md).
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

Native-bytecode guard unit checks download their pinned JAR automatically;
`-PjustDireGuardJar=<path>` is an optional explicit development override.
The additional final-pair test accepts `-PjustDireTransformedClass=...` pointing
at an active production/compatible MixinExtras export, not the absent/rejected
variant. The API checks include powered wallet read-only dependencies, rejecting
foreign wallet reads or payments. The design, exclusions and manual handoff are maintained in
local-docs, not inferred from successful installation recipes.

## Just Dire Things Negate Fall Damage (8.4)

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireNegateFall --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireNegateGuards --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireNegateFall "-Pneoforge_version=26.1.2.99" --console=plain
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
outcomes belong to the [extended resource gate](just-dire-resources.md#just-dire-things-production-energy-storage-83-a), not this server-only matrix.
Guard/native unit checks use
the automatic pinned JDT artifact and the opt-in `justDireTransformedClass`
property described above, with an active production or compatible export.

## Just Dire Things Negate consolidation and manual handoff (8.4 C)

Run existing gates sequentially; no second lifecycle/client harness is needed:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireResources :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireNegateGuards :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireJump :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireJumpGuards :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireAttributes :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireResourcesGuards :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireSmithing :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireNegateFall :MismatchedBoots:NeoForge:test "-PjustDireTransformedClass=mods/MismatchedBoots/RuntimeTests/NeoForge/build/run/justDireSmithing/.mixin.out/class/com/direwolf20/justdirethings/common/events/LivingEntityEvents.class" --console=plain
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

## Just Dire Things native cooldown HUD production

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireCooldownHud verifyPackagedDependencies --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyJustDireCooldownHud verifyPackagedDependencies "-Pneoforge_version=26.1.2.99" --console=plain
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
[the feedback plan](../../local-docs/JUST-DIRE-THINGS-COOLDOWN-FEEDBACK-IMPLEMENTATION.md).
