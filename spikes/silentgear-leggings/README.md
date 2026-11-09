# Silent Gear leggings research gate

Research-only **dedicated-server** probe of SG 4.2.3 / Silent Lib 11.2.0 on
Minecraft 26.1.2. It does not implement released Leggings compatibility or test
all armor traits. No client is launched and no screenshot is required.

## Run

From the repository root, run profiles sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSpike -PsgSpikeMode=baseline --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSpike --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSpike -PsgSpikeGraph=boots --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSpike -PsgSpikeGraph=boots -PsgSpikeOrder=after --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSpike -PsgSpikeGraph=boots -PsgSpikeOrder=after -PsgSpikeMode=compatible --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSpike -PsgSpikeGraph=boots -PsgSpikeOrder=after -PsgSpikeMode=refuse --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSpike -PsgSpikeGraph=boots -PsgSpikeOrder=after '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

The shared [artifact catalog/resolver](../../mods/MismatchedThings/RuntimeTests/README.md#third-party-test-artifacts)
owns downloads, cache and hashes. Omit `--offline` once if the pinned artifacts
are not cached. Profiles stage packaged Leggings and, optionally, current Boots;
Things comes from their nested dependencies. No indev paths are used. Restore
ordinary `.114` product outputs after running the `.99` boundary:

```powershell
.\gradlew.bat :MismatchedBoots:NeoForge:assemble :MismatchedLeggings:NeoForge:assemble --offline --console=plain
```

Reports are under
`mods/MismatchedLeggings/RuntimeTests/NeoForge/build/reports/<baseline>/sg-spike-<graph>-<order>-<mode>/`.
Acceptance additionally requires a fresh `matrix.ok` under the matching
`build/verification` directory, successful child exit, `server.ok` and no failure
marker. Failed runs may retain partial `silentgear.txt` diagnostics; that report
alone is not a pass. Each profile has a five-minute timeout.

The pinned SG JAR logs one native optional Bonsai recipe ERROR for
`silentgear:sapling/netherwood` / missing `bonsaitrees2:sapling`. The gate requires
exactly that known ERROR and rejects others; it does not hide or fix it. Boots
SG adapter refusal warnings also fail the gate. Ordinary loader/command warnings
remain in the full child log.

## What is measured

- Current numeric composition versus a test-only exact-entry-normalized query
  and proposed Magic Armor average policy. Iron, phantom lining, feather,
  quartz, bone and copper cover material/lining stats, duplicate entries and
  both orientations. Light also traverses actual equipment attribute updates;
  summing duplicate raw entries is not the player's effective speed.
- Existing stale armor after wear versus a disposable-native-copy projection
  for Chipping/Crushing. Only the root's base armor entries may change.
- Separate copper clocks/stages on the root, including differently aged SG
  inputs, both orientations and a coating control. Compare with native full
  item ticks on independent originals. The prototype persists only scalar lane
  data in `CUSTOM_DATA`; it is **not** a proposed production component/schema.
  Its typed NBT codec roundtrip is not a saved-world reopen or missing-mod test.
- One representative native Wielder family, Stellar: direct native piece
  counts and potion parameters for one through four pieces, duplicate SG
  sources, and mixed LEGS/FEET without original armor anchors. Native sources,
  carriers and actual equipment identities remain unchanged. Real current
  Boots dispatch is used for mixed FEET; LEGS dispatch is an explicit candidate
  call, not a production inventory-tick implementation.

`baseline` installs neutral result hooks but never enables the LEGS candidate;
numeric/state candidates still run explicitly for comparison. Other modes enable
paired LEGS recognition only after the final local producer/consumer slice is
checked. The test mixin priority is 900/1100 around current Boots' 1000; the
sorted actual application set is inspected, not inferred from flags.
`compatible` inserts an identity result chain with the same inputs. `refuse`
changes our trait callback's provenance without changing its runtime meaning:
the final LEGS gate must refuse while native/Boots recognition stays usable.
This small grammar is feasibility evidence, not a complete production guard,
API admission, reload or arbitrary-third-party-mixin guarantee.

## Retirement

Keep production sources untouched. When maintained tests replace the spike,
remove this folder and its three test-only seams: the apply in RuntimeTests
NeoForge's build, the early branch in `SkeletonTestNeoForge`, and the mixin entry
in the test mod metadata. Product JARs never include these sources/resources.
