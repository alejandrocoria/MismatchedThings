# Neo Vitae Hellforged gameplay

Maintained, dedicated-server-only acceptance for both products. Use the current
packaged JARs and the shared catalog's `neovitae` dependency closure, never indev
mods, generated replacement classes or research init scripts. NeoForge `.114` is
the native baseline; the pinned Modonomicon requires `.109` or newer.

## Commands

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
Presentation/persistence and consolidated native-set/owner acceptance have separate
completion boundaries. No screenshots or automated client are launched here.
