# Mixin binary compatibility

Run commands from the repository root. This checks the actual NeoForge
publication, rather than inferring compatibility from a rebuild against an older
loader. It covers the armor-rendering Redirect format rejected by MixinExtras
0.5.4 when newly compiled against Fabric Mixin 0.17.4.

## Binary regression

```powershell
.\gradlew.bat :MismatchedBoots:NeoForge:verifyMixinBinaryCompatibility --console=plain
```

The isolated `mixinBinaryCompatibility` source set scans the published JAR and
passes its Redirects through the real MixinExtras 0.5.4 transformer. Its negative
control changes a copy to the newer array-form annotation and requires rejection.
No retained broken JAR or user instance is required. The task prints the artifact's
SHA-256 and fails if no Redirect was exercised or its format is incompatible.

This check is included in NeoForge `check`/`build` and Boots'
`verifyPackagedDependencies`. The old Mixin/MixinExtras used by the check are
test-only Maven dependencies. They do not affect game runtime resolution or
enter either published loader artifact.

Main compilation in Boots Common and NeoForge uses the compatible Mixin 0.17.3
annotation API. Fabric compilation and every loader's runtime libraries remain
unchanged. See [the local compile constraint](../../build.gradle) and
[the isolated verification tasks](../../gradle/mixin-binary-compatibility.gradle).

## Unchanged-publication runtime checks

First build and freeze the normal publication using the default NeoForge baseline:

```powershell
.\gradlew.bat :MismatchedBoots:NeoForge:captureMixinCompatibilityArtifact --console=plain
```

Then run sequentially without recapturing the artifact:

```powershell
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyGeometryCharacterization '-PgeometryProductionJar=mods/MismatchedBoots/build/mixin-binary-compatibility/published.jar' '-Pneoforge_version=26.1.2.99' --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyGeometryCharacterization '-PgeometryProductionJar=mods/MismatchedBoots/build/mixin-binary-compatibility/published.jar' '-Pneoforge_version=26.1.2.109' --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyGeometryCharacterization '-PgeometryProductionJar=mods/MismatchedBoots/build/mixin-binary-compatibility/published.jar' --console=plain
```

The existing geometry fixture starts hidden and muted, disables NeoForge's early
window, and asserts its unattended contract. No screenshot review is required
for this binary-format regression. It checks the standard/native equipment
routes, model ownership, queued animation/decorations and item clipping/fallback.

`geometryProductionJar` is an explicit publication override; by default the
fixture still uses its normal locally built JAR. Preparing a run compares source
and staged SHA-256 values and writes `geometry-artifact-sha256.txt` beside
`geometry-success.txt` and `geometry-report.txt` in
`RuntimeTests/NeoForge/build/geometry-markers/`. The hash must be identical in all
three runs. Capture the report/log after each run if retained versioned evidence
is needed; the next run replaces the fixture directories.

Fixtures can rebuild loader-specific classes to compile their own drivers, but
the game loads the frozen publication, not those freshly compiled Boots classes.
The final default-version invocation restores normal .114 build outputs without
changing the captured JAR. This gate does not launch or modify ATM11/indev data,
and does not replace manual acceptance in an external modpack.
