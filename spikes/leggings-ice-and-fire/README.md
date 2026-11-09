# Leggings Ice and Fire research spike

Test-only candidate for the product-local LEGS dragon/Troll/Lightning predicates
and Tide count/activation. Production Boots, Leggings and Things are unchanged.
The private Leggings exploration owns conclusions and dated evidence; this file
owns reproducible commands and the prototype's limits.

## Runs

From the repository root, sequentially (launch artifacts are shared):

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafSpike --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafSpike -PiafGraph=boots -PiafOrder=before --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafSpike -PiafGraph=boots -PiafOrder=after --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafSpike -PiafGraph=boots -PiafVariant=damage --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafSpike -PiafGraph=boots -PiafVariant=lightning --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafSpike -PiafGraph=boots -PiafVariant=absent --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyIafSpike -PiafGraph=boots -PiafOrder=after -Pneoforge_version=26.1.2.99 --offline --console=plain
```

The `before`/`after` flags set the candidate mixin priority to 1100/900 around
unchanged packaged Boots' 1000; the loaded native Tide method's `MixinMerged`
annotation is asserted, not just the requested flag. The graph stages current
packaged products and shared hash-pinned native JARs, never indev files.

Server output is under
`mods/MismatchedLeggings/RuntimeTests/NeoForge/build/{reports,verification}/<baseline>/iaf-spike-<graph>-<order>-<variant>`.
Success requires `server.ok`, `spike.txt`, exit zero and `matrix.ok`, with no
failure file or unexpected runtime ERROR/Boots guard refusal. Empty native config
override objects avoid Jupiter's first-run missing-file errors while retaining
native defaults. Ordinary skeleton tests do not select these candidate mixins.

For additional native render pairs, reuse the maintained hidden/muted equipped
fixture, not a second client launcher:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyEquipped -PleggingsIceAndFire=true -PleggingsIafRenderSpike=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyEquipped -PleggingsIceAndFire=true -PleggingsIafRenderSpike=true -PleggingsBoots=true --offline --console=plain
```

Use the existing runtime guide's equipped report paths and pre-GLFW hidden-window
contract. The opt-in extra report is `ice-and-fire-spike-render.txt`; require it
alongside the maintained markers. This profile does not enable candidate gameplay
mixins. Restore normal `.114` packaging after floor runs.

## Interpretation and limits

- Damage uses real `hurtServer`, native event-bus consumers and actual health loss,
  not synthetic event posting or a duplicate damage formula listener.
- Tide compares ordinary connected-listener-backed server players' `doTick`,
  actual immersion/dry state, ordered native `addEffect` calls and complete effect
  codec values (including hidden effects) against original equipment. It checks
  per-piece activation and immutable roots. These are dedicated-server fixtures,
  not real multiplayer transport or owner gameplay acceptance.
- `damage` changes the native LEGS Troll class operand; the candidate's grouped
  damage selector refuses while native Scale/Steel, FEET, Lightning and Tide
  remain independently testable. `lightning` changes only the native LEGS item
  operand; the set candidate refuses, without changing Boots' local predicate.
  Neither is a complete production final-consumer/provenance proof.
- The receiver is a fixed native Tide leggings item with a disposable carrier.
  Candidate eligibility/slot checks are not a production final entry/base
  readiness implementation. Boots' real final entry/base readiness is checked
  when installed; its production guard is never relaxed for the candidate.
- Rendering adds all 31 real sources, both orientations, moving/crouched poses,
  dyed/trimmed leather partners and live root glint, requiring split views rather
  than accepting fallback. It builds on maintained native decorated-waist,
  material, queued-pose and actual resource-reload checks. No arbitrary renderer,
  addon, pack or exhaustive item-context acceptance is implied.

Retire this directory and its RuntimeTests Gradle/TOML/server-entry/render hooks
together after useful checks have maintained production equivalents. Do not ship
the candidate, retain it as an alternate production mode or add a Things engine
merely because Boots has similar code.
