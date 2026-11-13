# Leggings JDT Decoy research

Disposable candidate, not production support. The private Decoy exploration
owns findings, policy proposals and actual execution results.

Production block A now replaces action/clock/import behavior. The commands below
describe the original research baseline, not maintained current-product gates:
its owner-only clock characterization and held client clocks predate production
import/ticking. Use the RuntimeTests README's maintained Decoy action profile.
Keep remaining native HUD controls as implementation references until the plan
retires this entire folder and its opt-in wiring; do not claim a fresh pass from
the historical results.

From the repository root, run sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyJustDirePassives -PleggingsJdtDecoySpike=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyJustDirePassives -PleggingsJdtDecoySpike=true -PleggingsJdtBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyJustDirePassives -PleggingsJdtDecoySpike=true '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

The shared resolver stages JDT 1.6.11 and packaged current Leggings/optional
Boots, never indev JARs. Candidate sources are included only in the runtime
fixture with this flag; no extra Gradle project or production source directory.
The native entity, registration/renderer and native HUD remain JDT's.

Reports use `mods/MismatchedLeggings/RuntimeTests/NeoForge/build/reports/<baseline>/jdt-decoy-spike[-boots]/`:
require `decoy-spike.txt`, `decoy-client.txt`, three `case-<n>.ok` markers,
`server.ok`, `client.ok`, `connected.txt` and no failure files. Driver logs and
`connected.ok` use the corresponding `build/verification/` directory. No owner
world, screenshots or audible/visible client is created. Clock-oracle loops
advance records directly, not real-world minutes of waiting; connected HUD
fixtures are deliberately held at three authoritative phases for observation.

No production Decoy support is enabled. The action/clock/import/HUD helpers are
research only; full native-screen/input integration, saved-world lifecycle,
malformed-record guards and final native-seam safety belong in the later plan.

Retirement removes this entire folder, `leggingsJdtDecoySpike` source/profile
wiring, reflective Skeleton entry points and dynamic `JustDireFaultPlugin`
research mixins together. Restore ordinary `.114` outputs after the floor check.
