# Leggings Better Nether Ruby Fire probe

Disposable Fabric server characterization, not a production fix or permanent
compatibility suite. Uses current packaged Leggings/Boots with nested Things and pinned
Better Nether/BCLib/WorldWeaver 26.101.4. Modrinth Maven downloads are cached by
Gradle and verified against the exploration's SHA-256 pins; no indev files are read.

From the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:runRubyFireProbe --init-script spikes/leggings-betternether/init.gradle --console=plain
```

Add `--offline` after the first download. The init script adds this Java source
directory to the existing Fabric fixture and replaces **generated** fixture
entrypoints for this invocation only. Normal source/metadata files are untouched.
The new run is server-only, loopback/offline with an ephemeral port, no IDE run
configuration or client window. Its launch timeout is 240 seconds.

Evidence lives under `mods/MismatchedLeggings/RuntimeTests/Fabric/build/`:

- `reports/ruby-fire-probe/observations.txt`: measured outcomes.
- `reports/ruby-fire-probe/probe.ok`: all characterization checks completed.
- `reports/ruby-fire-probe/probe.failure.txt`: failure trace, if any.
- `run/ruby-fire-probe/server/`: disposable server and native logs/config/world.

The probe requires root Ruby Fire I after an upgrade. For pre-fix characterization,
`-PrubyFireExpected=0` requires the old missing-root result; `-1` only records it.
It checks native paid smithing, both mixed
orientations, native setup/composition, paid book application, native enchantment
post-attack dispatch, live state preservation and no resurrection of removed
Protection. It is not connected-client, visual, piglin or full combat acceptance.

After using the init script, regenerate the ordinary fixture outputs before
normal testing (without the init script):

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:classes --offline --console=plain
```

Observed before the correction on 2026-11-04: native smithing adds Ruby Fire I;
both mixed upgrade orientations, in both products, receive it only on the source,
leaving root level 0 and no retaliation. After correction, both products receive
root Ruby Fire I and execute retaliation. Initial enchanted-input composition and
book application work before/after; live state and deliberate root enchantment
removals survive the upgrade.
Retire this opt-in harness once useful cases have been moved to maintained
acceptance tests.
