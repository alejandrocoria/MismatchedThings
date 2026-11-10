# Silent Gear leggings wearer contracts and isolated set proof

Block A **dedicated-server** native wearer controls and isolated Wielder/set proof with SG 4.2.3 /
Silent Lib 11.2.0 on Minecraft 26.1.2. Numeric/state candidates and prototype
CUSTOM_DATA were retired by attributes/state block D. This extends the remaining Stellar proof to all four selected Wielder families;
it never activates production wearer effects. No client is launched.

## Run

From the repository root, run profiles sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:NeoForge:test --tests '*SilentGearWearerFoundationTest' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSetProof -PsgSetProofMode=baseline --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSetProof --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSetProof -PsgSetProofGraph=boots --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSetProof -PsgSetProofGraph=boots -PsgSetProofOrder=after --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSetProof -PsgSetProofGraph=boots -PsgSetProofOrder=after -PsgSetProofMode=compatible --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSetProof -PsgSetProofGraph=boots -PsgSetProofOrder=after -PsgSetProofMode=refuse --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSetProof -PsgSetProofGraph=boots -PsgSetProofOrder=after -PsgSetProofMode=refuse-type --offline --console=plain
.\gradlew.bat :MismatchedLeggings:NeoForge:test --tests '*SilentGearWearerFoundationTest' :MismatchedLeggings:RuntimeTests:NeoForge:verifySgSetProof -PsgSetProofGraph=boots -PsgSetProofOrder=after '-Pneoforge_version=26.1.2.99' --offline --console=plain
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
`mods/MismatchedLeggings/RuntimeTests/NeoForge/build/reports/<baseline>/sg-set-proof-<graph>-<order>-<mode>/`.
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

- Native Adamant, Aquatic, Flame Ward and Stellar: independent potion expectations
  at 0..4 armor pieces, non-armor count/type branches, selected equipment/hands,
  idle inventory, ten-tick cadence and natural 38-tick expiry. Companion effects
  are inspected and excluded, never invoked as wearer work.
- Production-local optional bindings and computed, condition-resolved source view:
  maximum duplicate level, stable ID order, different traits, matching/unmatched
  native conditions, missing live state and strict invalid/foreign/suspended/
  quarantined/wrong-slot rejection. Disposable carriers retain native leggings
  identity; queries do not mutate sources/history/root or recalculate native gear.
  The recalculation tripwire is positively calibrated in every run.
- Independent native Brilliant controls, both effective carrier orientations and
  duplicates, with production Brilliant/Wielder explicitly still inactive.
- Final transformed native Brilliant, Wielder, potion, non-gear query and gear
  identity boundaries. Changed definition branches refuse before invocation and
  restored definitions are re-read. Product-local pair helpers validate both
  result routes, their original-true behavior and their default-false shared gate.
- All four families through the existing isolated LEGS candidate, 1..4 pieces and
  duplicate sources. With Boots installed, mixed LEGS/FEET count as two without
  original armor anchors; native head/chest reach four. Boots uses its actual
  production dispatcher. LEGS remains an explicit research call.

`baseline` installs neutral result hooks and disables LEGS set recognition.
Other modes enable
paired LEGS recognition only after the final local producer/consumer slice is
checked. The test mixin priority is 900/1100 around current Boots' 1000; the
sorted actual application set is inspected, not inferred from flags.
`compatible` inserts an identity result chain with the same inputs. `refuse` / `refuse-type`
change the trait / type callback provenance without changing its runtime meaning:
the final LEGS gate must refuse while native/Boots recognition stays usable.
This bounded grammar and definition revalidation establish A's inert foundation,
not production dispatch, actual resource-reload acceptance or arbitrary third-party
mixin compatibility. B/C must install and publish their own independent final
proofs and bounded refusal handling before enabling gameplay.

## Verified manual native fixtures

Each successful run emits `wearer-manual.txt` alongside `silentgear.txt`. All
20 chat-sized `/give` commands are executed by the real server, then their parts
are applied with the native part-swap recipe and the resulting trait is checked.
Use original SG iron armor from its creative examples, then craft each original
piece with the corresponding given plate **before** combining mixed leggings.
The file includes legs, feet, head and chest for all five families.

These are API-created material fixtures, not a claim of survival acquisition.
Nickel (Adamant), lead (Aquatic) and refined iron (Stellar) ingredient tags are
empty in the minimal SG/Lib profile; the report records actual tag contents.
Gold (Brilliant) and crimson steel (Flame Ward) also receive verified commands.
No production debug/reload command or extra ingredient provider is installed.

## Retirement

Production item/tick/mixin wiring stays inactive through A. When a maintained
wearer implementation replaces this remaining set proof,
remove this folder and its three test-only seams: the apply in RuntimeTests
NeoForge's build, the early branch in `SkeletonTestNeoForge`, and the mixin entry
in the test mod metadata. Product JARs never include these sources/resources.
