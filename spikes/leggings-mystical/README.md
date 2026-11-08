# Leggings / Mystical Agriculture research fixture

Test-only candidate. No production mod contains these classes/resources and no
indev instance is an input. Native JARs use the shared hash-checked resolver.
The original NeoForge mod and unofficial Fabric port are separate targets.

Run sequentially from the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyMysticalSpike --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyMysticalSpike --offline --console=plain
```

Each task seeds a disposable dedicated world and starts a hidden/muted integrated
client in another JVM. Existing fixture hooks hide the window before GLFW
creation; NeoForge's early window is disabled. A fresh copy of the seed world
replaces only this profile's previous disposable client world. No screenshots
are required. Unexpected runtime ERRORs, failed/missing markers or timeouts fail
the driver, independently of a successful Gradle exit.

Options:

- `-PleggingsMaGraph=alone|current|published`: Leggings alone, current built Boots,
  or an unchanged Boots 1.0.0 binary. The published case requires
  `'-PleggingsFabricBootsJar=<path>'` or `'-PleggingsNeoForgeBootsJar=<path>'` and
  validates the committed fixture's SHA-256. There is no indev fallback.
- `-PleggingsMaOrder=before|after`: candidate mixin priority 1100/900 around
  Boots' default 1000, regenerated as a processResources input.
- `-PleggingsMaRefund=observer|router`: HEAD/RETURN observation sharing a
  per-invocation capture, or a second shrink router used as a comparison control.
- `-PleggingsMaFocus=full|lifecycle`: full normal-keyboard movement matrix, or
  focused real-player effects/refunds/cache/coexistence without repeating it.
- `-PleggingsMaAbsent=true`: no MA/Cucumber; only ordinary composition/quiet
  startup is claimed. Add `'-Pneoforge_version=26.1.2.99'` for a focused floor
  run; restore default `.114` afterward.

Reports: `mods/MismatchedLeggings/RuntimeTests/<Loader>/build/reports/<baseline>/mystical-spike-*`.
Process logs and `matrix.ok`: matching `build/verification/<baseline>/...`.
`movement.txt` distinguishes actual native positives from equality without a
positive. `samples.txt` is partial diagnostic output, never a success marker.
The exported final ItemStack transformation is under each run's `.mixin.out`.

## Deliberate prototype limits

This is not the production catalog, strict malformed-slot authorization or local
proof implementation. It exercises valid native installations, strongest-family
selection, representative worn callbacks, health cleanup and simple real break
refunds. It does not certify every eligible augment/tier, malicious mutations,
refund reentry/cancellation/overflow, menu editing, saves, dedicated multiplayer,
visual presentation or owner acceptance. A production observer would still need
its own bounded final-flow proof and transaction controls; successful coexistence
does not justify trusting arbitrary foreign hooks.

Speed is measured through the real client's KeyboardInput and native callbacks,
not by setting a server player's input axes. Server/client callback and nonzero
input counters are observations, not a replacement speed implementation.
No Fabric client workaround or native bug fix is included.

Once equivalent production gates exist, retire this directory and its test-module
Gradle applies, mixin registrations and entry-point branches together.
