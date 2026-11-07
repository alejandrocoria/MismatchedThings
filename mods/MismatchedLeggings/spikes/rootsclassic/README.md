# Roots Classic leggings rendering spike

Explicit NeoForge research opt-in, not production compatibility. Uses packaged
Leggings and the shared resolver's pinned/hash-checked Roots Classic 1.6.7 JAR;
it never copies mods or settings from indev.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRootsSpike -PleggingsRootsSpike=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyRootsSpike -PleggingsRootsSpike=true -PleggingsBoots=true --offline --console=plain
```

Omit `--offline` for first downloads. Use `'-Pneoforge_version=26.1.2.99'` for the
floor and restore `.114` afterwards. Run profiles/baselines sequentially.
The inherited harness runs a dedicated server, then an early-hidden/muted client
with NeoForge's loading window disabled. It creates no IDE configurations.

The original `HumanoidArmorLayer.renderArmorPiece` is invoked without changing its
layer request. An explicit HUMANOID equipment-layer call is a separate alternate
control, never described as the original native rendering. The prototype changes
only the layer argument for exact Roots leg sources inside mixed source capture;
production pair preflight, native models/animations/textures and fallbacks stay in
use. There are no copied assets, native item/model replacements or gameplay hooks.

Evidence under `RuntimeTests/NeoForge/build/reports/<baseline>/rootsSpike[-boots]/`:
`roots-spike.txt`, `roots-before.png`, `roots-after.png` and normal completion
markers. Bounded logs and `matrix.ok` live under the matching `build/verification/`
path. The wrapper requires fresh evidence, successful processes and no ERRORs.
A raw client exit or the gallery alone is not acceptance.

Research limits and results belong to the
[Leggings exploration](../../local-docs/ROOTS-CLASSIC-COMPATIBILITY-EXPLORATION.md).
No root ticks/set recognition, optional binding guards, production layer policy
or owner acceptance is supplied by this spike. Retire it after maintained
production tests replace its evidence.
