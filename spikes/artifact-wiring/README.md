# Artifact wiring spike

Opt-in build-tooling research, not production infrastructure. The private
[exploration](../../local-docs/ARTIFACT-WIRING-EXPLORATION.md) owns the findings.
Nothing here is applied by ordinary Gradle builds. Delete the complete folder
when maintained helpers/tests supersede this experiment.

The init script reads the real shared catalog and resolver. The JSON overlay
contains only two proposed dependency declarations; it does not duplicate pins
or define a second maintained catalog. No released Java source, regular test task
or development instance is changed.

## Run

From the repository root, with the usual JDK/cache:

```powershell
.\gradlew.bat help -I spikes/artifact-wiring/artifact-wiring.init.gradle --offline --console=plain
.\gradlew.bat verifyArtifactWiringSpike -I spikes/artifact-wiring/artifact-wiring.init.gradle --offline --console=plain
```

The first checks configuration without invoking the artifact resolver. The second
runs the maintained Roots bytecode guard tests from Boots and Leggings through
separate test tasks, without inheriting their usual artifact property/input
bindings. It keeps NeoForge's required JUnit bootstrap. All tests, including the
real pinned-archive checks, must pass without skips.

It also stages IAF/Uranus/Jupiter with their existing catalog hashes, checks
Oritech's client-only Athena edge, aliases/deduplication and absent selections,
and rejects unknown references/sides and cycles. Guard selection is direct;
runtime selection expands only the explicit roots. No Minecraft game is started.

The explicit override can be tested against the already prepared Roots file:

```powershell
.\gradlew.bat verifyArtifactWiringSpike :MismatchedBoots:NeoForge:artifactWiringSpikeGuard --rerun :MismatchedLeggings:NeoForge:artifactWiringSpikeGuard --rerun -I spikes/artifact-wiring/artifact-wiring.init.gradle '-PartifactSpikeGuardJar=mods/MismatchedThings/build/test-mods/NeoForge/RootsClassic-26.1.2-1.6.7.jar' --offline --console=plain
```

This must run both real guard suites without asking the default resolver for
Roots. An override pointing to a nonexistent file must fail with `Missing explicit
guard artifact`, not fall back to a downloaded pin. Overrides deliberately do not
enforce the catalog hash: fault tests may supply altered archives.

Evidence and disposable staging live under `build/artifact-wiring-spike/`.
No indev JAR, world, configuration or user mods folder is read or modified.
The separate tasks do not inherit all ordinary guard configuration, so this
does not certify migration of the other guard or runtime profiles.
