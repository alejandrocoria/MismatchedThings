# Neo Vitae 1.1.53 compatibility research

Opt-in, disposable research for the five new Sentient upgrades, Deus evolution
and Hellforging in both current armor products. This is not a production adapter
or a permanent release-graph test profile. Decisions and measured results belong
in the private `local-docs/NEO-VITAE-1.1.53-COMPATIBILITY-EXPLORATION.md`.

## Run

From the repository root, with the usual Java 25 environment:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaeSpike --init-script spikes/neovitae-update/init.gradle --offline
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaeSpike --init-script spikes/neovitae-update/init.gradle --offline -PnvSpikeMode=share
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaeSpike --init-script spikes/neovitae-update/init.gradle --offline -PnvSpikeMode=recognize
```

Run `baseline` before either candidate: they read representative owner-policy
items actually written by the baseline. Omit `--offline` if required dependencies
are not cached. The shared artifact resolver selects Neo Vitae and its dependency
closure; the run uses the current packaged Boots and Leggings with nested Things.
It does not replace, patch or publish any production JAR.

The init script selects research sources/resources and substitutes only the
ordinary test entry point. Without that init script the spike is not compiled
or registered. `share` adds one exact tag-query hook and disposable component
configuration; `baseline` leaves production semantics intact.
`recognize` additionally tries a native `isHellforged` reader for valid mixed
roots whose marker is absent but a source has the native true marker. Explicit
root false is not overridden. This tests a local legacy fallback, not migration.

## Evidence and limits

The task sequentially starts a dedicated server to write real barrel contents,
a fresh server JVM to reopen that world, then a hidden/muted client using its own
copy of the saved world. Each child has a five-minute process timeout. No owner
world or indev configuration is touched. Runs and reports are below:

`mods/MismatchedLeggings/RuntimeTests/NeoForge/build/neovitae-update/26.1.2.114/`

Each mode has `reports/write`, `reports/reopen`, `reports/client`, `expected.json`
and, after all gates pass, `matrix.ok`. Child logs live beside the mode directories.
`graph.txt` records actual artifact hashes. Creation-hidden GLFW and muted audio
are required, including during resource reload; no screenshots or owner test
world are generated.

- Native chestplate upgrades are compared with all nine native/mixed feet/legs
  combinations and missing-set negatives. Progression uses a representative native
  XP helper, not a claim that every tome UI interaction was exercised.
- Rituals use native layouts, the master block, activation and actual ingredient/
  Anima payment. Their native operation is called explicitly; automatic cadence,
  particles and ritual UX are not certified by this fixture.
- Workbench extraction, trim smithing, anvil payment and native container reopen
  exercise real item state. Direct root upgrades retain source history.
- Client checks use native tooltips and item/equipment render submissions, before
  and after resource reload. They do not certify pixels or TCP synchronization.
  Encoded server samples are read through the client registry codec.
- Candidate gaps are measurements, not silently repaired behavior: the report
  records old owner-negative roots after ordinary recomposition, native operation
  payment/refusal for those roots, and direct-root Hellforged items whose original
  source icons remain untinted. The legacy reader fixes recognition, not tint.

Only NeoForge `.114` is in this research scope. The selected Neo Vitae dependency
closure does not support `.99`; no optional-absence or release matrix is implied.
After research, restore ordinary fixture outputs with a normal invocation without
the init script:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:classes --offline
```

## Retirement

A follow-up production plan should keep only focused maintained regression tests
for the selected behavior. Retire this entire directory, including its alternate
entry point, tag hook, client observers and init/runtime scripts, once those tests
replace the evidence. Do not turn the research modes into a general test framework.
