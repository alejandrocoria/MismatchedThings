# Undergarden infusion spike

Bounded experiment for **both Boots and Leggings**, not production support.
At the time of the spike, the normal factories were unchanged. The candidate only initializes disposable
roots and enables test-only Infuser/source-tooltip adapters. Native code still
owns recipe timing, assembly, payment, infection, translation and item decoration.

Production block A now initializes and preserves the reservoir through Things.
The old baseline's OWNER expectations are historical and no longer apply to
current artifacts. Use the maintained
[production state profile](../../mods/MismatchedLeggings/RuntimeTests/README.md#undergarden-rogdorium-state)
for A; this executable prototype remains only until the plan's D retirement.

The test module compiles against the packaged product JARs. Runs stage only the
selected packaged products and the shared resolver's pinned
`The_Undergarden-26.1.2-0.9.8.jar`; Things is loaded from their nested dependency.
No indev artifacts, configuration or worlds are used. Never run graphs/baselines
concurrently: ModDev's generated launch files are shared.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyUndergardenSpike '-PundergardenGraph=both' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyUndergardenSpike '-PundergardenGraph=both' '-PundergardenCandidate=true' --offline --console=plain
```

Repeat candidate with `undergardenGraph=boots` and `leggings` for standalone
graphs; `'-Pneoforge_version=26.1.2.99'` selects the supported floor. The artifact
resolver can download missing pinned inputs when `--offline` is omitted.

Evidence lives under the NeoForge runtime module's
`build/reports/<baseline>/undergarden-<graph>-<baseline|candidate>` and
`build/verification/<baseline>/...`. The driver rejects failed markers, nonzero
exit, timeout or ERROR diagnostics. Clients are hidden before GLFW window
creation, muted and have NeoForge's early window disabled. No screenshots are
needed for the live-tooltip/decorator assertions; equipment checks inspect actual
native submissions, not visual/pixel acceptance.
The fixture disconnects cleanly before process shutdown; connection ERRORs are
not accepted merely because functional assertions already completed.

## Deliberate test boundaries

- The disposable world's local chunks use the real `undergarden:depths` biome,
  with its native lethality data. No resource changes the native data map.
  Protection compares measured native increments at zero/one/two charged pieces.
- Fake players are survival, have a loaded fake connection and use real armor
  inventory slots. Posting the registered native event exercises consumption;
  this is not a real dedicated-server/client session test.
- Infuser `serverTick` is run against a placed native block entity, real loaded
  recipe and paid input/fuel/output slots. No private `infuse` shortcut is used.
  The native menu's separate quick-move/property-set gate, ordinary input-slot
  placement and output extraction are also tested, not inferred from direct
  block-entity insertion.
- The candidate's initial charge is floor((A+B)/2) on each output, so an odd unit
  may be lost but no reserve is created. Capacity is max(A,B), with native 3600
  for absent components. Only ordinary native capacities are certified here.
- Candidate recipe admission identifies the **loaded native armor recipe object**
  and extends its ingredient query for structurally valid roots. Wrong fuel,
  timing, assembly and payment remain native. A second candidate hook extends
  the native menu's `canInfuse`, since a changed match does not update its
  precomputed accepted-input set. This proves local seams; a production
  implementation still needs reload-safe recipe/operand identification and guards,
  not this fixture's static object binding or a global armor-tag whitelist entry.
- Source-tooltip hiding changes only the temporary `TooltipDisplay` argument;
  histories remain intact. The native root appender's ordering is measured, not
  relocated by the candidate.
- The server saves roots in an actual chest; a second JVM reopens the copied world.
  Separate full-stack JSON/stream roundtrips are codec evidence, not packet transport.

Retire the executable spike and its small test-module hooks when relevant gates
are promoted to maintained production tests.

## Recorded technical result — 2026-11-08

Server/hidden-client checks passed for current baseline and candidate together
on NeoForge `.114`, each candidate product alone on `.114`, and the candidate
products together on `.99`. All final markers are present; `.114` outputs were
restored. Native root consumption/decoration already work. The candidate proves
conservative initial composition, paid local Infuser admission and native
menu transfer/extraction and root-only tooltip presentation without product
changes. This is not owner
visual acceptance or production certification.

The real native consumer processes `FEET=36` and `BODY=36` separately: boots
with ample charge spend twice per update, leggings once. The native Infuser
also spends six offered fuels when topping 3500/3600 up by only 100 units.
Both quirks remain native; no replacement formula/payment is introduced here.
