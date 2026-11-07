# Oritech equipped-rendering spike

Opt-in NeoForge research against packaged Leggings, not shipped compatibility.
Enable only for the commands below; normal product sources and JARs are unchanged.
Artifacts come from the shared pinned/cached resolver, never indev.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyOritechSpike -PleggingsOritechSpike=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyOritechSpike -PleggingsOritechSpike=true -PleggingsBoots=true --offline --console=plain
```

Use `'-Pneoforge_version=26.1.2.99'` for the minimum-baseline control. Run profiles
sequentially. The inherited harness runs a dedicated server, then a hidden/muted
client with the loading window disabled. Omit `--offline` for initial fixture
downloads. No IDE configurations or indev files are created/changed.

The client reproduces the unmodified complete-owner/neutral fallback before
enabling a test-only render hook. The bridge obtains the actual wearer, creates
independent source state and uses the original Gecko renderer/model/texture with
the opposite leg masked through native bone snapshots. A two-lane preflight
keeps ordinary equipment and native custom geometry separate until submission.

Evidence: `RuntimeTests/NeoForge/build/reports/<baseline>/oritechSpike[-boots]/`
contains `oritech-spike.txt`, before/after/reload panels and normal completion
markers. The wrapper requires all evidence plus successful bounded processes;
a successful raw `run...Client` exit alone is not acceptance. Logs remain under
`build/verification/<baseline>/oritechSpike[-boots]/`. The exact pre-existing
Oritech missing-block data-map error stays visible; other errors fail the gate.

Research shortcuts must not be copied wholesale into production: reflective
public-field state cloning, a weak wearer lookup and a collector limited to the
observed custom-geometry submission. Production needs an explicit extraction-
time snapshot boundary, optional binding/local guards and rejection of unknown
submissions before committing either side. This is not generic GeckoLib support.

Results, native presentation limits and remaining production checks are in the
[Leggings exploration](../../local-docs/ORITECH-COMPATIBILITY-EXPLORATION.md#7-rendering-spike--2026-11-07).
Retire this code when equivalent maintained production tests replace it.
