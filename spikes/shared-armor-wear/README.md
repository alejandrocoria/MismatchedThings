# Shared incoming armor wear research

Disposable successor to the asymmetric Leggings/Boots bridge. Enable only with
`-PsharedWearSpike=true`. No production JAR or source is rewritten. The script
builds normal products, compiles an isolated Things source set, and patches only
disposable copies under each fixture's `build/shared-wear/` directory.

## Candidate boundary

- One Things-owned post-event NeoForge interceptor and final causal guard,
  mechanically adapted from Boots' existing verified flow. One optional transparent
  forwarding wrapper remains supported. No private product verifier is invoked.
- A slot registration table dispatches at most one handler. The core contains no
  Boots/Leggings imports, source schemas, FE binding or payment policy. It owns
  ordinary delegation when no participant is registered or the shared guard fails.
- The fixture registers each installed product through the same API. It invokes
  actual Boots payment and the previous Leggings research payment against their
  real production wallets. Reflection avoids fixture sibling compile dependencies;
  production registration belongs in each product's own initialization, using
  direct local callbacks. This is not implemented production lifecycle registration.
- Candidate Boots omits its old consumer mixin configuration. Both current products
  embed the exact same candidate core. Only disposable copies are altered.
- Candidate Things rejects optional Boots `<1.1.0` through native metadata:
  Fabric `breaks`, NeoForge `incompatible`. Boots is not a required dependency.
  The rule does not depend on JDT presence. Fabric's candidate Leggings repeats
  the optional conflict to identify the old root product in the native diagnostic.
  No saved data, public workbench API or wire protocol changes.

## Bounded commands

Run from the repository root, **sequentially**:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runSharedWearSpike -PsharedWearSpike=true -PsharedWearGraph=both --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runSharedWearSpike -PsharedWearSpike=true -PsharedWearGraph=boots --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runSharedWearSpike -PsharedWearSpike=true -PsharedWearGraph=leggings --offline --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:Fabric:runSharedWearSpike -PsharedWearSpike=true -PsharedWearGraph=both --offline --console=plain
```

`sharedWearGraph` selects `core`, `boots`, `leggings`, `both` (default), `legacy`
or `legacy-alone`. Both legacy profiles stage the unchanged Boots 1.0.0 JAR, checked against the
existing Leggings fixture pin and read from its existing frozen release input.
`legacy` includes real current Leggings, forcing Things 1.1.0, and **must fail
during native loader resolution**, naming the incompatible Boots version; a crash
or missing marker is not by itself evidence of correct rejection. `legacy-alone`
has no Leggings: Fabric can select the original embedded Things 1.0.0 instead,
which is safe and is checked as such. NeoForge rejects that profile if it selects
the candidate 1.1.0. Never claim that an ignored candidate rejected old Boots.
No new release fixture, download framework or release-graph driver is introduced.

NeoForge switches: `-PsharedWearRefused=true` corrupts only the candidate core's
unpublished guard, `-PsharedWearTransparent=true` adds a compatible forwarding
control, and `-PsharedWearAbsent=true` omits native JDT. `core` plus absence proves
that Boots is optional. `both` plus absence also checks product initialization
without JDT. Add `'-Pneoforge_version=26.1.2.99'` for the focused floor control.

Only dedicated servers run: no client windows, screenshots or owner worlds.
Normal successes require fresh `shared-wear.ok`, no `shared-wear.failure.txt`,
and NeoForge's `shared-wear.tsv` under the fixture's
`build/reports/<baseline>/shared-wear-<selection>/`. Runs use the corresponding
`build/run/<baseline>/shared-wear-<selection>/server/` directory. Fabric checks
native metadata/core selection only, not JDT gameplay (which is NeoForge-only).
Healthy final-export controls independently reject wrong routed slots and foreign
ownership on detached copies of the actual transformed CommonHooks. They never
change live permission. An intentionally refused common guard delegates both
slots; local participant refusal retains the other slot's FE conversion.

## Production handoff and retirement

Keep existing one/two-provider policy and transactional safeguards in each product;
replace Leggings' direct pinned source lookup with its existing resource binding's
wear query/current stamp. Test production initialization and optionality rather
than retaining fixture registration. No shared wallet or general composition
engine is proposed. Refused callbacks must delegate before spending, not catch
failures and apply fallback after a possible commit.

The final implementation must retire **both** research folders, the old optional
`justdire-wear-spike.gradle`/apply/plugin delegate, this script and both applies,
and reflective entry branches in SkeletonTestNeoForge/WorkbenchTestFabric. Retain
only useful maintained acceptance controls. Restore ordinary `.114` compilation
with both research flags omitted; no research entries may enter product artifacts.
