# ATM: Compat / Silent Gear materials

Bounded NeoForge material, persistence and presentation acceptance for both current packaged armor products.
The shared [catalog](../../../MismatchedThings/RuntimeTests/mod-artifacts.json)
owns exact publications/hashes. The profile explicitly selects `atmcompat`,
`allthemodium` and `silentgear` plus their audited required dependency closures.
It neither reads indev JARs nor changes the smaller ordinary SG profiles.

Run sequentially from the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgAtmMaterials --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgAtmMaterials '-PsgAtmVariant=nausea-target' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgAtmMaterials '-PsgAtmVariant=levitation-target' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgAtmMaterials '-PsgAtmVariant=old-targets' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgAtmPersistence --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgState '-PsgFoundationGraph=boots' '-PsgTooltipClient=true' '-PsgAtmPresentation=true' --offline --console=plain
```

Omit `--offline` for the initial verified download. Neither the spike init script
nor a candidate JAR is used. `runSgAtmMaterials` is the underlying dedicated run;
the `verify` task additionally checks bounded completion, reports and diagnostics.

## Scope

- Real registered Silent plates and native SG construction/properties/traits for
  AllTheModium, Vibranium and Unobtainium, on FEET and LEGS.
- Both paid mixed orientations and companion return per material/slot, actual
  installed armor/toughness before vanilla clamping, ordinary averaged durability,
  nonzero native Crushing projection at half damage, Brilliant, immutable sources
  and a complete item-codec round trip. Ordinary SG gold and native ATM leggings
  remain independent piglin-positive controls.
- Natural Leggings acquisitions: none for AllTheModium; Wither/Nausea for
  Vibranium; Wither/Nausea/Levitation for Unobtainium. Compare native/paid roots,
  worn/mainhand/offhand/inactive placement, overlapping SG/SG sources and intact
  Poison/Luck/root state. The actual private binding rejects unsupported new
  effect lists, targets, conditions and maximum levels without a public test API.
- Each new target fault must disable only its own lane once, before removing
  unrelated Luck. Restoring the native definition does not retry a disabled lane.
  `old-targets` uses the established synthetic Poison/Wither acquisition to disable
  both old lanes, then requires the naturally acquired new cures to keep working.
- `verifySgAtmPersistence` writes all 18 real native/mixed stacks (three materials,
  both products and orientations, damaged roots) to a native barrel, then starts
  another game JVM against that saved world. It compares complete encoded
  components with separate expectations, inspects loaded material caches and
  checks Brilliant on the actual loaded items, never rebuilding/replacing them.
- The existing hidden-from-creation/muted TCP tooltip client adds 12 paid ATM
  roots to its ordinary SG controls. It compares structured native source details
  and unchanged root/history in normal/Shift/F3+H modes, native item textures and
  equipment tint/layers for both products/orientations, before and after one
  actual resource reload. No screenshot gallery or manual world is generated.

All controlled data mutations are restored in `finally`. Exactly one native
missing `bonsaitrees2:sapling` recipe error is expected per server JVM; fault modes
expect only their named one-time lane warnings. Server gates accept no other
error/SG refusal.
The presentation client retains the ordinary tooltip profile's one deliberate
unresolved-source warning; each resource pass reports the same two known native
SG missing hammer/knife subtitle translations, exactly four errors across reload.
ATM: Compat 1.2.0 also unconditionally ships
`assets/allthemodium/models/block/alloyed_infusion_pillar.json` with loader
`thaumaturge:mesh`, although Thaumaturge is optional and absent from this SG graph.
The client reports that exact model failure plus its exact missing-loader cause
once per resource pass (twice here). Raw diagnostics remain in the logs; the gate
classifies only that asset/cause/count, not arbitrary model failures. No native
assets are replaced and no unrelated bridge dependencies are added to hide it.
Legacy synthetic Poison/Wither, optional absence and actual resource-reload
controls retain their separate [entry point](../README.md#silent-gear-cure-contracts).

## Evidence and boundaries

Reports are under `NeoForge/build/reports/<baseline>/sg-atm-<variant>/`, relative
to `RuntimeTests/`: `materials.txt`, `server.ok`, or `server.failure.txt`.
Verification logs and fresh `matrix.ok` are under
`NeoForge/build/verification/<baseline>/sg-atm-<variant>/`.
Persistence uses the analogous `sg-atm-persistence/` directories: `write/` and
`reopen/` reports, independent server logs, one saved `server/materials/` world
and a separate `expected.json`. Presentation uses the existing state runner's
`sg-state-boots-tooltip-atm/` directories, stage `write/`, including
`atm-presentation.txt`, `tooltip-client.txt` and both endpoint artifact hashes.
These are automatic disposable fixtures, not owner manual worlds. Neither path
uses the retired spike or changes persistent joint development instances.

The baseline defaults to habitual `.114`; the existing
`'-Pneoforge_version=26.1.2.99'` property selects the floor. Run baselines
sequentially and restore `.114` compilation outputs afterward. At the floor, run
only normal materials and persistence, not all fault/presentation variants.
The two server gates launch no client; presentation is the explicit last command.
Automatic acceptance does not certify manual pixels, the complete joint mod
collection, optional-addon removal/recovery or SG part editing. Owner checks use
ordinary joint client/server worlds and must be recorded separately.
