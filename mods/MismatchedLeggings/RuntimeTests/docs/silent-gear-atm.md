# ATM: Compat / Silent Gear materials

Bounded NeoForge server acceptance for both current packaged armor products.
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

All controlled data mutations are restored in `finally`. Exactly one native
missing `bonsaitrees2:sapling` recipe error is expected per run; fault modes expect
only their named one-time lane warnings. No other error/SG refusal is accepted.
Legacy synthetic Poison/Wither, optional absence and actual resource-reload
controls retain their separate [entry point](../README.md#silent-gear-cure-contracts).

## Evidence and boundaries

Reports are under `NeoForge/build/reports/<baseline>/sg-atm-<variant>/`, relative
to `RuntimeTests/`: `materials.txt`, `server.ok`, or `server.failure.txt`.
Verification logs and fresh `matrix.ok` are under
`NeoForge/build/verification/<baseline>/sg-atm-<variant>/`.
Only `NeoForge/build/run/<baseline>/sg-atm-<variant>/server/` is used for game data.
These are automatic disposable fixtures, not owner manual worlds.

The baseline defaults to habitual `.114`; the existing
`'-Pneoforge_version=26.1.2.99'` property selects the floor. Run baselines
sequentially and restore `.114` compilation outputs afterward. No client or
screenshot is launched by this server gate. Native container reopen, presentation,
reload/connected acceptance and owner confirmation must be recorded separately;
this gate alone does not certify those behaviors or SG part editing.
