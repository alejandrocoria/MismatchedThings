# ATM: Compat material/cure spike

Superseded by production block A. Do not rerun the old candidate generator or
baseline expectations against the extended production dispatcher. Use the
[maintained material gate](../../mods/MismatchedLeggings/RuntimeTests/docs/silent-gear-atm.md)
instead. This source remains temporarily as the reference for B's container
acceptance; B removes the complete executable spike after replacing it.
Commands/results below describe the original pre-A research state.

Opt-in research for NeoForge 26.1.2. It reuses Leggings' existing dedicated
fixture, native SG construction, packaged family mods and paid workbench. It is
not a new production adapter or a normal runtime profile. No client is launched.

The shared catalog pins ATM: Compat 1.2.0; this profile explicitly selects
`atmcompat`, `allthemodium` and `silentgear` and their required runtime closures.
The addon is not made a required dependency of another catalog entry. Persistent
development runs and their worlds/configuration remain untouched.

## Commands

From the repository root, sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runAtmSpike --init-script spikes/atm-compat/init.gradle --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runAtmSpike --init-script spikes/atm-compat/init.gradle '-PatmSpikeMode=candidate' --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runAtmSpike --init-script spikes/atm-compat/init.gradle '-PatmSpikeMode=candidate' '-PatmSpikeStage=reopen' --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runAtmSpike --init-script spikes/atm-compat/init.gradle '-PatmSpikeMode=refusal' --console=plain
```

After the initial verified download, add `--offline`. Repeat candidate write
and reopen with `'-Pneoforge_version=26.1.2.99'` for the focused floor. Never run
these baselines concurrently; fixture/product compilation outputs are shared.
Reopen requires the preceding write of the same mode/baseline. Write overwrites
only the spike's designated test container and expectation file, not owner data.

## What changes in the candidate

The init script adds only this fixture source/resource tree. Its mixin selects
the bounded driver instead of the ordinary SG foundation cases. Without the
init script these research sources are not part of the fixture build.

The candidate generator checks and mechanically changes three exact fragments
of Leggings' current `SilentGearCures` source in a generated build copy:

- Add explicit Nausea/Levitation IDs and targets.
- Keep the early all-disabled check consistent with the four independent lanes.
- Leave the native callback, concrete definition/target proof and source view intact.

It compiles and substitutes only that class and its enum in a disposable copy
of the packaged Leggings JAR. Original product sources/JARs and Boots are not
patched. There is no injected implementation of potion removal.

## Evidence boundaries

- AllTheModium, Vibranium and Unobtainium: real registered Silent plates and
  native SG parts/properties/trait definitions, for FEET and LEGS.
- Both paid mixed orientations per material/slot: armor/toughness contributions,
  ordinary average durability, native Crushing projection at half damage,
  Brilliant piglin recognition, immutable source provenance and item codec.
  Numeric checks inspect installed modifiers before vanilla attribute clamping.
- Natural leggings acquisitions: native vs baseline/candidate cures, both
  mixed orientations, worn/mainhand/offhand/inactive selection, unrelated
  Poison/Luck preservation and unchanged complete root components.
- Refusal: change only Nausea's native target to Luck in memory, restore it in
  `finally`, and require one local refusal with working Wither/Levitation.
- Separate-JVM reopen: all 18 native/mixed stacks must match their complete
  encoded expectations after native barrel/chunk loading, retaining Brilliant.

This does not execute plate smithing, part editing, a real connected client,
visual/tooltips acceptance, or resource reload. It does not newly certify every
SG wearer effect or permanently incorporate ATM: Compat into development runs.
Only the exact existing missing `bonsaitrees2:sapling` native recipe error is
accepted; the fault variant alone expects one Nausea-disable warning.

Reports/worlds are under
`mods/MismatchedLeggings/RuntimeTests/NeoForge/build/atm-spike/<NeoForge>/<mode>/`:
`reports/<stage>/materials.txt`, `server/atm`, and `expected.json`. These are
disposable automatic fixtures, not prepared owner manual worlds. The private
[exploration](../../local-docs/ATM-COMPAT-SILENT-GEAR-EXPLORATION.md) owns the
dated execution results and recommended handoff. Retire this executable spike
when bounded maintained acceptance replaces its useful cases.
