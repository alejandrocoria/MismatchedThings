# Frozen workbench API-v1 consumers

`api-v1-fabric.jar` and `api-v1-neoforge.jar` are intentionally committed compiled
test fixtures. Each is a tiny independently packaged leggings consumer built
against the real core 1.0.0 from commit
`ac03ef20727e4d2c5db93fb2be183155333f3f12`, before the coexistence implementation.
They include that original core using the loader's native packaging mechanism.

The companion JSON records the artifact SHA-256, original embedded-core SHA-256,
core source commit, Minecraft/JDK/loader baseline and hashes of both consumer
source files under [Consumers](../../Consumers). No game worlds, third-party JARs
or Gradle caches are committed here. The source and embedded core are repository-
owned code under the repository license.

Normal `verifyWorkbenchCompatibility` runs these exact binaries, validates their
provenance/hash and never depends on their producer. Updating the current core or
compiling the maintained sibling must not regenerate this baseline: doing so
would conceal source/binary compatibility breaks.

For provenance, the original explicit recording command was:

```powershell
.\gradlew.bat :MismatchedThings:RuntimeTests:Consumers:Fabric:recordApiV1Baseline :MismatchedThings:RuntimeTests:Consumers:NeoForge:recordApiV1Baseline -PworkbenchConsumerVariant=baseline -PrecordWorkbenchBaseline=true --offline --console=plain
```

The producer requires core 1.0.0, explicit recording flags and absent destination
artifacts; it refuses to overwrite a historical fixture. It is not a repair step
for a failing compatibility test. Any deliberate future baseline replacement must
be reviewed as a change to the compatibility evidence, preserving the relevant
older binary when that API compatibility remains supported.
