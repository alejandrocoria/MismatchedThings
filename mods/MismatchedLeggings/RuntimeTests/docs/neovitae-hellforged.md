# Neo Vitae Hellforged gameplay and presentation

Maintained acceptance for both products. The gameplay selection is dedicated-server-only;
the presentation selection adds a separate-JVM reopen and a hidden/muted client. Use the current
packaged JARs and the shared catalog's `neovitae` dependency closure, never indev
mods, generated replacement classes or research init scripts. NeoForge `.114` is
the native baseline; the pinned Modonomicon requires `.109` or newer.

## Gameplay commands

From the repository root, with Java 25 and prepared artifact caches:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaeHellforged --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaeHellforged -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyNeoVitaeComposition --offline --console=plain
```

Run sequentially. The first two use the existing Leggings profile runner, selecting
standalone Leggings or Leggings/current Boots. The last retains Boots' existing
standalone infusion/lifecycle/restart gate and adds a small Hellforged registration/
inheritance control; it is not another full ritual matrix.

Focused default/config and native marker-read tests:

```powershell
.\gradlew.bat :MismatchedBoots:Common:test --tests '*HellforgedDefaultsTest' :MismatchedLeggings:Common:test --tests '*HellforgedDefaultsTest' :MismatchedThings:NeoForge:test --tests '*HellforgedMixinPluginTest' --offline --console=plain
```

The native guard test obtains its hash-checked artifact through the shared artifact
bindings. Optional absence can reuse Boots' `verifyNeoVitaeCompositionAbsent` and
Leggings' ordinary `runSkeletonServer`; these runs must not stage Neo Vitae or its
dependencies. No native-present `.99` result is implied.

## Evidence and boundaries

Leggings' profiles write below
`NeoForge/build/reports/26.1.2.114/neoVitaeHellforged[-boots]/` and
`NeoForge/build/verification/26.1.2.114/neoVitaeHellforged[-boots]/`.
Fresh `server.ok`, `neovitae-hellforged-server.txt` and the runner's `matrix.ok`
are required, without `server.failure.txt` or runtime `ERROR` diagnostics.

The maintained cases exercise:

- Native positive controls; actual paid workbench extraction in both orders and
  companion return; neither/one/both upgraded inputs; raw/effective averaged bonus
  identities, creation fire intersection and direct root's full native bonus.
- Actual ritual layout/master, activation and separate operation EV payment,
  exact gem/ingot consumption and refusal of another operation. Activation is not
  refunded when the ritual has no eligible target.
- Native direct root forging preserves Spiritus charge, Blood Mending, damage and
  source history; ordinary recomposition does not inflate bonuses or reset state.
- A representative inverse created under `OWNER`, later queried under `SHARE`,
  is recognized before/after recomposition without writing its absent marker.
  Explicit false, `OWNER`/`OMIT`, malformed and bare items do not gain fallback
  recognition. Suppressed inherited upgrades cannot buy a useless repeat operation.
- Logical client-thread checks exercise unsynchronized, synchronized `OWNER` and
  synchronized `SHARE` views without replacing server authority. These are rule/
  native-helper checks, not a graphical client or TCP claim.
- A refused optional binding declines only new mixed ritual eligibility and the
  legacy reader; native items, existing set tags and separate Spiritus binding work.

The ritual fixture waits for an actual entity-query witness in its forced chunk,
with a bounded timeout, rather than guessing a fixed startup delay. The witness is
discarded before operation checks. No owner worlds or manual fixtures are created.

Boots' existing report directory additionally contains
`neo-vitae-hellforged-smoke.txt`. Its other composition/lifecycle markers retain
their existing meanings.

This gameplay gate does not certify direct-root icon tint, tooltips, native set
upgrade progression, ritual animation/cadence or a new saved-container reopen.
No screenshots or automated client are launched by the gameplay selection.

## Presentation and persistence

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaePresentation --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaePresentation -PleggingsBoots=true --offline --console=plain
```

Use the same prepared artifacts; run sequentially. This extends the existing
packaged profile runner, not the research init script or another release graph.
Evidence is under `NeoForge/build/reports/26.1.2.114/neoVitaePresentation[-boots]/`
and the corresponding `build/verification/` directory. The runner requires fresh
`server.ok`, `client.ok`, `matrix.ok`, plus:

- `neovitae-hellforged-lifecycle.txt` and `neovitae-hellforged-saved.json`:
  12 full samples per slot in a real barrel: native control, both orientations of
  one inherited/direct source, both inherited/direct sources, legacy inverse,
  explicit false, and ordinary full-model fallback. Direct/inherited roots retain
  charge, Blood Mending, damage and glint through paid trim smithing and anvil rename;
  repeated recomposition retains bonus identities/amounts.
- `neovitae-hellforged-restart.txt`: another game JVM loads that same barrel and
  compares complete saved roots/history before querying native recognition and
  effective attributes. No recipe recreation or silent replacement is used.
- `neovitae-hellforged-client.txt`: a client opens a disposable copy of the saved
  world, waits for actual synchronized product rules, and reads its barrel. Native
  model resolution compares GUI/both-hand tint submissions and split/fallback GUI
  states. Direct-root views add only eligible temporary native markers; inherited
  asymmetry, explicit false and synchronized `OWNER`/missing-rule cases are retained.
  Native equipped layer textures/colors/trims/glint and normal/Shift/F3+H root
  tooltips are checked without changing complete root/source state. All checks run
  again after actual resource reload.

The client reuses the test-only pre-GLFW hidden-window hook, muted disposable options
and disabled NeoForge early window. No screenshot gallery or owner world is created.
The client/reopen evidence is not a TCP/pixel certification or the owner's consolidated
acceptance. Native set-upgrade/Deus regression, applicable floor checks, owner review
and full spike retirement remain separate closeout work.
