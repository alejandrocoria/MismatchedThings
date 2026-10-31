# Leggings runtime tests

Small fixtures for the current standalone product and structural source schema. They compile against
the real loader JARs, then load only the packaged Leggings artifact from disposable
`build/run/<baseline>/<server|client>/mods`. Things comes from the native nested
dependency, not loose core classes or a separately staged core JAR. Boots and
indev mods/configuration/worlds are excluded.

The additional workbench profile can also stage the **current built** Boots JAR
for coexistence. It does not use an indev directory or claim acceptance against
the frozen published Boots release; that release graph remains a final-product gate.

## Workbench and synchronization

Run sequentially from the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:verifyWorkbench --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyWorkbench --console=plain
```

Add `'-PleggingsBoots=true'` to either command to stage both packaged products.
For NeoForge's floor, add `'-Pneoforge_version=26.1.2.99'`; afterwards rerun without
that override to restore the normal .114 outputs. Optional `--offline` and both
properties propagate to the bounded child Gradle launches. Do not run loader
suites or NeoForge baselines concurrently: they share production build outputs.

Each command starts a separate dedicated server and hidden client. The client
loads a copy of the saved fixture world, then uses actual block-use and container
packets against its integrated server. Following
[Boots' composition-rule client test](../../MismatchedBoots/RuntimeTests/Fabric/src/gametest/java/games/alejandrocoria/mismatchedboots/gametest/CompositionRulesClientGameTest.java),
it verifies two real sessions with different config files. The first starts
with maximum attribute rules and separate durability; editing the files during
play must not alter either authority or client previews, including a paid inverse.
After a real disconnect and same-world reopen, normal server-start hooks must load
minimum attribute rules and average durability and the new connection must receive
both together. Native join/ready/S2C handlers deliver the complete snapshot; the
fixture never overrides server-start loading or directly installs successful
client rules. No production command or testing-only production hook is needed.
Full `ItemStack` equality checks both previews and paid inventory outputs on each
side, not merely their item IDs. The client also checks disconnect reset and the
absence of fabricated previews before synchronization.

Dedicated checks cover source admission, loaded product routes, rejection of
cross-slot inputs, either output taken first, exactly-once payment, input locks,
shift-click and paid/unpaid close cleanup. A test-only native recipe-manager apply
invoker exercises removal and holder replacement, including stale pickup/shift
rejection and preservation of a paid inverse. This is the apply boundary of a
reload, not a separate filesystem datapack-discovery test. Rule codec checks cover
registered/absent numeric declarations, full atomic round trips, trailing data,
invalid counts, duplicate IDs and protected structural fields.

Disposable runs are under `build/run/<baseline>/workbench[-boots]/<server|client>`.
Fresh markers are under `build/reports/<baseline>/workbench[-boots]/`; subprocess
logs and `matrix.ok` are under `build/verification/<baseline>/workbench[-boots]/`.
The normal skeleton profiles remain separate and exclude Boots. The client-window
contract below applies to both suites; no screenshots are required for these
transaction/state assertions.

## Skeleton commands

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
several frames. Factory checks additionally cover asymmetric/same-item pairs,
owner/unknown/removed fields, durability rounding and numeric resource bounds,
prior work, repair material union, enchantments, identity/operation/slot arithmetic,
invalid modifiers, explicit policies and TOML schemas. Native combat-wear entry,
seeded armor Unbreaking and real XP pickup exercise both roots without changing
their provenance. NeoForge injects a test-only effective modifier and mutates the
query copy to verify the native boundary and source isolation. This does not claim
workbench, synchronization, mixed rendering or missing-mod recovery acceptance.

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
