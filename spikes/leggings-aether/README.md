# Aether II leggings feasibility fixture

Temporary feasibility code for native Aether II leggings integration.
These sources belong only to the NeoForge runtime-test module, never a product
JAR. They are intended to be retired when maintained production coverage replaces
the spike. No native assets are copied and no indev directory is read or written.

From the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAetherSpike -PleggingsAetherMode=baseline --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAetherSpike --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAetherSpike -PleggingsBoots=true --offline --console=plain
```

Default mode is `candidate`. Run variants sequentially: they share generated
NeoForge launch files. Omit `--offline` for the shared pinned resolver's first
download. `-PleggingsAetherServerOnly=true` restricts the wrapper to a dedicated
server; use `'-Pneoforge_version=26.1.2.99'` for a focused floor check and restore
routine `.114` outputs afterwards. The current Boots JAR is built for ASM-order
proofs even when not installed in the runtime graph.

The native baseline does not install candidate hooks or change product defaults.
The candidate adds only a missing LEGS contribution to native set counts,
extends the audited native tier predicates, contains owner-root tooltip/tier
producers, enables an any-source Alkahest tag
and supplies explicit `charms=omit` rules when making fixture pairs. Production
defaults remain untouched. The set-return and tier edits are feasibility code,
**not final production shape guards**. Guard design, unknown peer modifications
and refusal behavior remain implementation work.

Reports live under
`mods/MismatchedLeggings/RuntimeTests/NeoForge/build/reports/<baseline>/aether-spike-<mode>[-boots]/`:

- `aether-server.txt`: native set/attribute/charm/fluid measurements. Charms and
  reinforcement tiers are prepared through native components, not paid Forge
  transactions; exclusions are not tested features.
- `aether-transform-orders.txt`: both orders of actual packaged Boots ASM edits
  and the local set/tier/tooltip candidates on pinned native methods, followed by ASM analysis.
  These are transformation proofs, not two separately launched loader orderings.
- `aether-client.txt`: native equipment submissions, selected-leg geometry,
  source materials/dye/style and resource reload with the production renderer.
- `aether-tooltip-preview.txt`: current root text and a detached source-layout
  feasibility preview using native translations/counts, plus native charm
  graphical factory measurements. This is **not** an installed production
  tooltip adapter or a final visual acceptance claim.

`server.ok` / `client.ok` and `build/verification/<baseline>/<variant>/matrix.ok`
require successful execution. A server-only run writes `server-only.ok`, never
full-client acceptance. Both use isolated copied worlds. Clients are hidden at
GLFW creation by the existing test mixin and muted in options; do not replace
that with hiding after the first frame. No screenshot is needed for the
submission/geometry feasibility assertions. Owner visual review belongs to
production acceptance.

The wrapper leaves Aether's two known dedicated-server `Toast` distribution
ERRORs visible, checks their exact count and rejects every other ERROR. Client
logs must have zero ERRORs. See the Boots exploration's native diagnostic note;
this spike does not repair or claim clean native dedicated logs.
