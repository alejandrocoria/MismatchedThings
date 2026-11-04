# Boots rules protocol spike

Temporary opt-in experiment, **not production networking or final acceptance**.
Normal builds do not include these projects. No fixture code enters published JARs.

Historical pre-cutover fixture: block B removed the legacy production types that
this replacement experiment targets. Do not run it against current production or
use it to validate the migrated protocol. Maintained positive controls are in the
[Boots runtime guide](../../mods/MismatchedBoots/RuntimeTests/README.md#rules-synchronization).
Block C will retire this directory after final passive acceptance is in place.

```powershell
.\gradlew.bat -PbootsRulesSpike=true :RulesSpike:Fabric:verifySpike --offline
.\gradlew.bat -PbootsRulesSpike=true :RulesSpike:NeoForge:verifySpike -Pneoforge_version=26.1.2.99 --offline
.\gradlew.bat -PbootsRulesSpike=true :RulesSpike:NeoForge:verifySpike -Pneoforge_version=26.1.2.114 --offline
```

`-PrulesSpikeCases=seed,integrated,new-new,new-old,old-new` selects phases;
`integrated` requires a completed `seed`. Names identify **client-server** order.
Each loader/baseline writes disposable worlds and evidence under its own
`build/proof/<baseline>/<case>/`. Endpoints are sequential except for the two
processes required for an actual dedicated connection. Do not run matrices in
parallel: their child Gradle builds share packaged production outputs.

The legacy peer loads the unchanged published Boots 1.0.0 JAR and its nested
dependencies. SHA-256 pins come from the existing
[release fixture](../../mods/MismatchedLeggings/RuntimeTests/fixtures/boots-1.0.0.json).
By default the experiment reuses the frozen input under
`mods/MismatchedLeggings/build/verification/product/inputs/`. To supply it explicitly,
use `-PrulesSpikeFabricBootsJar=<path>` or `-PrulesSpikeNeoForgeBootsJar=<path>`.
It never substitutes a rebuild or reads indev mods. Its observer has no current
Boots/core API dependencies; only native window-hiding and read-only rejection-screen
access apply there, without changing the historical mod's networking or recipes.

The candidate peer loads the actual current packaged Boots/core. Test-only mixins
disable its old rules transport/lifecycle and redirect `assemblePreview` to a
nullable aggregate. Native adapters register revision-2 directional channels.
The snapshot reuses the existing strict codecs in length-delimited sections with
an explicit 512 KiB total budget. This is a feasibility prototype, not a choice
to keep section buffers or these exact channel names in production.

Fabric checks advertised revision-2 configuration receivers on both sides;
the client also checks server ready-channel support at play join before requesting
rules. NeoForge uses required registrations with registrar revision `2`.
This tests capability/protocol identity, not equality of release versions.

Checks cover real dedicated packets, direct preview before/after readiness,
server-produced stack versus preview, complete snapshot codec/bounds, actual
recipe-cache availability, disconnect invalidation and two integrated sessions
with changed server configuration. The listener ordering check models the cache
contract; it is **not** a real JEI/REI rendering/reload test. Final production
integration must adapt those subscribers, test recipe arrival orders, preserve
empty-recipe synchronization, check optional JDT transport and validate packaging.

Automated clients use the shared
[hidden/muted window contract](../../mods/MismatchedThings/RuntimeTests/README.md#client-window-contract).
No screenshots, visible diagnostic clients or full foreign-mod matrix are needed.
Results and design decisions belong in the private exploration, not this guide.
