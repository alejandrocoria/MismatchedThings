# Aquaculture leggings buoyancy feasibility spike

The production gravity query now supersedes this prototype. Its pre-integration
`native`/`refused` baselines intentionally assumed no production buoyancy and are
not valid against the current product. Use the maintained
[Aquaculture runtime profile](../../mods/MismatchedLeggings/RuntimeTests/README.md#aquaculture-2-compatibility)
instead. Keep this isolated prototype only until final integration acceptance,
then remove it and its opt-in settings registration.

Opt-in NeoForge fixture, not production compatibility. Normal builds do not
include this project. The maintained artifact resolver supplies the exact Boots
catalog pin for Aquaculture 2.9.2; no indev JAR is used. Production Leggings is
loaded from its packaged JAR with nested Things, not loose product classes.

## Run

From the repository root:

```powershell
.\gradlew.bat :LeggingsAquacultureSpike:verifySpike -PleggingsAquacultureSpike=true --offline --console=plain
```

Omit `--offline` for initial downloads. Use `-PaquaSpikeModes=candidate` to select
one mode, or a comma-separated subset. Modes run sequentially in separate JVMs:

- `native`: no candidate Mixin; native behavior and missing mixed buoyancy baseline.
- `candidate`: read-only gravity query for eligible worn mixed leggings.
- `refused`: candidate Mixin deliberately not applied; native behavior unchanged.
  This is a disabled-hook control, not a mutated-native-API or production-guard test.
- `absent`: candidate Mixin applied, but Aquaculture is not staged.
- `candidateBoots`: candidate plus the current packaged Boots, including native
  and mixed Neptunium feet without an original leggings anchor.

Add `-Pneoforge_version=26.1.2.99` for the supported-floor feasibility check.
Return to `.114` afterward. Reports, success/failure markers and bounded process
logs are under `build/reports/<baseline>/<mode>/`; worlds and mods live under
`build/run/<baseline>/<mode>/`. Never point these tasks at an indev instance.

## Candidate and evidence boundary

The prototype changes only the result of `Entity.getGravity()` for a Player with
strictly valid Neptunium-source mixed leggings in LEGS. It mirrors the native
eye-water/crouch/jump/swim/spectator conditions, but never calls `setNoGravity`,
ticks source snapshots, swaps gear or alters the native armor tick. Native armor
can still write its ordinary flag; it cannot cancel this conditional zero-gravity
query. Pre-existing zero gravity remains unchanged.

Real Player ticks initialize fluid/eye state. Disconnected ServerPlayers then
exercise native `travel(Vec3.ZERO)` movement, comparing vertical positions and
velocity with the original armor's tick/flag behavior. Candidate profiles perform
128 movement comparisons across both outputs and the eight condition cases.
Duplicate sources, armor carried/worn, independent head/chest effects, actual
`hurtAndBreak`, invalid/suspended/wrong-slot/non-player boundaries and leaving water
are also checked. Player entity save/load verifies no candidate flag is persisted.

This is **not** an interactive client, network reconnection or complete server
restart test. No client window or screenshot is generated. The candidate is shared
query logic usable on both logical sides; actual client prediction, equipment
synchronization and server acceptance remain production-plan validation gates.
The small native condition is deliberately mirrored, not invoked; upstream changes
require native fixture comparisons rather than assuming permanent equivalence.

On 2026-11-05, native/candidate/refused/absence/current-Boots controls passed on
NeoForge `.114`; the candidate also passed on `.99`. A native-only control confirms
that removing the final native leggings leaves `NoGravity=true` after a Player
tick and in serialized entity data. The prototype introduces no such state, but
does not repair a flag already stuck before it was equipped or arbitrate foreign
writers. It also does not promise `isNoGravity()` will report the same value as
native armor: the tested equivalence is gravity and movement.

After a production implementation and maintained regression coverage replace this
spike, remove this directory and the
single opt-in block in `settings.gradle`; no prototype belongs in shipped JARs.
