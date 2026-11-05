# Crystalite leggings rendering spike

Opt-in Fabric 26.1.2 / Better End 26.101.4 investigation. No production rendering
change. The ordinary test fixture does not compile/load this source set.

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyBetterEnd -PleggingsCrystaliteSpike=true --offline --console=plain
```

Run from the repository root. The existing profile resolves/hash-checks the same
pinned Better End/BCLib/WorldWeaver fixtures, never indev JARs. Omit `--offline`
only for the initial fixture download. The opt-in flag is forwarded to both child
processes, with isolated `betterEnd-crystalite-spike` run/report directories.
The server retains the maintained gameplay checks. The additional client-only
fixture replaces the gameplay client's tick checks with this rendering experiment.
Windows remain hidden before creation and muted; this adds one relevant panel,
not unrelated test screenshots. Normal profiles and product JARs exclude the spike.

## Experiment

- Obtain geometry from Better End's public `CrystaliteLeggingsModel.createModel`
  factory, rather than copying cuboid coordinates or reading private attachments.
- Detach the three geometry anchors exactly as upstream does, on our own model.
  Freeze the actual humanoid rig's root/body/leg poses in queued views.
- Feed a stable native humanoid model with a frozen per-submission pose through
  the unchanged `BufferedEquipment` / `LeggingsView` waist cache and clipper;
  keep the complete selected leg. No new Things API or package-private access.
- Capture the real registered native renderer's two translucent render types.
  Compare the factory geometry's positions, UVs, normals, color, light and overlay
  with its actual custom-geometry callbacks for regular/slim players, crouch,
  sitting, baby zombies and posed/small armor stands.
- Pair-wide preparation before any flush; unknown custom renderers and forced
  second-stage failure must keep the current complete-owner/neutral fallback.
- Native Crystalite ignores trims and glint. The spike separately tries one
  armor-glint pass for live-root foil, without inventing trim support.
- Repeat geometry/submission checks after a real resource reload. Check queued
  pose independence and only two waist-template builds across 30 repeated frames.

The prototype uses exact renderer identity and known six-callback ordering to
probe the pinned implementation. It is not a production API guard or a promise
to support other custom renderers/versions. Any implementation must retain an
optional, fail-closed boundary and resource-lifecycle ownership.

Completed on 2026-11-05: the full dedicated/hidden-client profile and fresh
before/after-reload proofs passed. Agent panel inspection passed. The normal
`verifyBetterEnd` profile passed afterward without the experimental mod or its
source set in the launch classpath. This does not replace owner acceptance of
a future production model integration. The focused
[exploration](../../mods/MismatchedLeggings/local-docs/BETTER-END-COMPATIBILITY-EXPLORATION.md#9-crystalite-rendering-spike)
owns findings and recommended next steps.

## Evidence

Under `mods/MismatchedLeggings/RuntimeTests/Fabric/build/reports/0.19.5/betterEnd-crystalite-spike/`:

- `client.ok` / `client.failure.txt`: fresh completion/failure, enforced by the profile.
- `crystalite-proof.txt` / `crystalite-after-reload.txt`: focused machine assertions.
- `crystalite-panel.png`: native/current-fallback/prototype comparison, including
  source orientation, movement, posed waist and the experimental root-foil pass.

The corresponding `build/verification/.../matrix.ok` is only written after the
dedicated server and hidden client exit successfully. These are spike results,
not owner gameplay/visual acceptance or a production compatibility claim.
Promote useful checks and remove this source set and opt-in wiring when the
chosen implementation reaches its final acceptance block.
