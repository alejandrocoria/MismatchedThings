# Leggings runtime tests

Small fixtures for the current standalone product and structural source schema. They compile against
the real loader JARs, then load only the packaged Leggings artifact from disposable
`build/run/<baseline>/<server|client>/mods`. Things comes from the native nested
dependency, not loose core classes or a separately staged core JAR. Boots and
indev mods/configuration/worlds are excluded.

## Commands

Run sequentially from the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:verifySkeleton --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySkeleton '-Pneoforge_version=26.1.2.99' --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySkeleton --console=plain
```

The aggregate checks packaging and both loaders sequentially at the selected baseline. The
last command restores and checks the usual NeoForge .114 artifact after testing
the .99 floor. Fabric uses the root Loader/API versions. Add `--offline` when
dependencies are cached; child runs inherit that flag and the NeoForge version.
Do not run both NeoForge versions concurrently: build outputs are shared.

`verifyPackaging` checks identity, dependency bounds, exact nested-core bytes,
required product resources, absence of Boots/test/spike content, and independent
indev classpaths. Each runtime suite then starts a dedicated server and client in
separate bounded JVMs, requiring fresh success markers and successful process exit.
The client opens a copy of the disposable server world so item components are
bound by real world/datapack loading, not synthesized at the title screen.
They check standalone loader selection, packaged class origin, inert bare-item
registration and the shared station. The server also exercises defensive source
snapshots, same-item component distinctions, ordered persistent/network root
round trips (including explicit component removals), missing items, invalid
counts/native components, disguised/nested sources and hostile recursive wire
input. A successful round trip after rejection checks recursion-guard cleanup.
Equipment checks cover the owner's flags/asset, custom sound selection, strict
root classification and actual player use/slot/dispenser queries, with vanilla
leggings as a pass-through control. The client checks the bare diagnostic tooltip,
round-trips the registered pair component and draws its temporary model for
several frames. This is structural acceptance, not mixed-rendering or numerical
composition acceptance; it does not claim missing-mod recovery.

Per-phase logs live in each loader's `build/verification/<baseline>/`; assertion
reports live in `build/reports/<baseline>/`. No test IDE configurations are generated.
Development authentication/profile lookup warnings, the loopback server's explicit
offline-mode warning and native loader/Gradle warnings are not skeleton failures.
Inspect new errors rather than treating a zero exit code alone as acceptance.

## Client window contract

Automated clients must be hidden before window creation, not hidden on the first
tick. The test-only [window mixin](Common/src/main/java/games/alejandrocoria/mismatchedleggings/test/mixin/HiddenWindowMixin.java)
sets `GLFW_VISIBLE=false` before `glfwCreateWindow` and checks initial visibility.
Tick checks assert it stays hidden without silently hiding it again. Disposable
options mute master volume and disable narration; NeoForge's early loading window
is disabled. Keep this contract for new client fixtures and focused runs. These
hooks must never enter production artifacts or ordinary indev configurations.
