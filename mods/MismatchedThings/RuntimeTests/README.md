# Shared workbench runtime tests

These fixtures compile against the actual loader JAR but load it only from
their disposable `mods` directory. Standalone profiles exclude Boots; the
compatibility matrix uses a frozen copy of the current packaged Boots artifact.
Indev JARs/configuration/worlds are never dependencies. Test mods and the additive
test API never enter released core artifacts.

## Client window contract

Automated clients must start hidden and muted, including focused runs and new
fixtures/spikes. Set `GLFW_VISIBLE` to `GLFW_FALSE` before `glfwCreateWindow` using
the test-only [window mixin](Common/src/main/java/games/alejandrocoria/mismatchedthings/test/mixin/WindowVisibilityMixin.java).
Hiding the window on the first tick is too late: it can already have appeared
during initialization. Assert the initial visibility and that it stays hidden;
do not silently hide it again in tick checks, masking a regression. Keep master
volume zero in disposable options and NeoForge's early loading window disabled.
These hooks belong only to fixtures, never production or indev launches. A visible
diagnostic run is an explicit opt-in, not the normal automated test behavior.

## Commands

Run from the repository root, sequentially:

```powershell
.\gradlew.bat :MismatchedThings:Common:test --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:Fabric:verifyWorkbench --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:NeoForge:verifyWorkbench '-Pneoforge_version=26.1.2.99' --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:NeoForge:verifyWorkbench --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:Fabric:verifyWorkbenchCompatibility --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:NeoForge:verifyWorkbenchCompatibility '-Pneoforge_version=26.1.2.99' --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:NeoForge:verifyWorkbenchCompatibility --console=plain
```

The final NeoForge command restores the normal .114 artifact after floor
acceptance. Do not run its two baselines concurrently: compilation outputs are
shared. `:MismatchedThings:verifyWorkbench` aggregates Common tests and both
loaders at the selected baseline. Add `--offline` when pinned dependencies are
already cached; that flag and the selected NeoForge baseline reach child runs.

Each loader suite checks the emitted artifact and runs four bounded profiles:

- Core server: literal registry IDs, standalone origin, block/item linkage,
  acquisition recipe/advancement, axe tag and no implicit armor recipe.
- Core client: actual station use, localized screen, empty admission and
  placed-block/item persistence across separate server/client JVMs.
- Recipe server: a test-only serializer/data recipe and input contributor,
  rejected unrelated inputs and incomplete/ordered pair previews.
- Recipe client: normal container packets, two ordered results, exactly-once
  payment, inverse retention/input locking and complete server/client output
  stack equality after both real server transfers. Closing a preview returns
  only the original inputs; closing after payment preserves the remaining result.

The synthetic recipe names vanilla helmet outputs; it is not a sibling product.
Its serializer, data and admission are enabled only in the recipe profiles.
Common JVM tests cover mutable-result isolation, incomplete pairs, duplicate
owners, defensive single-item admission and concurrent consumer registration.

## Native coexistence and evolution

`verifyWorkbenchCompatibility` runs five server/client graphs, an additional
saved-world upgrade client, then one intentionally refused server launch:

| Graph | Installed consumers | Selected core |
| --- | --- | --- |
| Core | None; direct production core JAR | Current production version |
| Boots | Real packaged Boots, with its normal nested dependencies | Current production version |
| Baseline | Committed API-v1 compiled consumer | Original 1.0.0 |
| Sibling | Synthetic helmet consumer, no Boots classes/items | Next compatible minor |
| Both | Boots, frozen baseline and sibling | Next compatible minor |
| Upgrade | Same joint graph, reading the Boots-only server world | Next compatible minor |
| Incompatible | Synthetic consumer requiring API major 2 | Native loader refusal |

Consumers use Loom `include` or ModDevGradle `jarJar`, not hand-written nested-JAR
metadata. The candidate is made from the real production core JAR, with changed
test version metadata and one additive `WorkbenchExtensions` class. The sibling
links that genuinely new class; selecting the old core cannot pass. No station
implementation is copied into a consumer.

All positive graphs check literal registry IDs, exactly one loader core container
and actual registration initialization (a test-only mixin counter), expected
contributors, rejected unrelated/cross-domain pairs and complete previews. Clients
open the real shared screen and exercise ordinary container packets for every
installed recipe route. Both complete paid outputs are compared with authoritative
server results, followed by preview/paid close cleanup. Sibling/core/baseline-only
graphs also reject the presence of the Boots item and implementation classes.

The upgrade seed contains a real composed Boots root with named/damaged sources
and additional root state. Its entire encoded stack is compared before client
crafting or inventory cleanup. All frozen inputs and the pinned historical fixture
are hash-checked before and after every child run; the snapshot is captured once,
not refreshed between stages. Incompatible-major acceptance requires a nonzero
native launch, the relevant version-refusal diagnostic and no observer success.

The two committed [API-v1 fixtures](fixtures/api-v1/README.md) are deliberate narrow
binary test inputs, not a cache. Normal verification never recompiles or replaces
them. `Consumers` contains their source provenance and the maintained synthetic
sibling; its source sets and metadata remain test-only. The family-level
`:MismatchedThings:verifyWorkbenchCompatibility` task aggregates both selected
loader baselines, independently of the four-profile standalone verifier.

## Focused runs and evidence

For a single profile, run `runWorkbenchServer`, then `runWorkbenchClient` on
the same loader. Add `-PworkbenchTestRecipes=true` to both for recipe acceptance;
omit it for core-only checks. A client requires its matching saved server seed.
Use the aggregate verifier for enforced success-marker/exit-code checks.

All generated state is under each loader fixture's `build/`:

- `run/<baseline>/<core|recipes>/{server,client}`: discarded mods/config/worlds.
- `reports/<baseline>/<core|recipes>/{server.ok,client.ok,failure.txt}`: current
  success markers or assertion details.
- `matrix/<baseline>/`: child logs and `matrix.ok` only after all four pass.
- `run/<baseline>/compatibility/<case>/{server,client}` and matching `reports/`:
  separate coexistence worlds and markers; `both-upgrade/client` reads a copied
  Boots-only seed, never modifies its original server world.
- `compatibility/<baseline>/{artifacts,logs}`: frozen consumer/core inputs,
  `sha256.json`, bounded child logs and final `matrix.ok`.

For a focused compatibility rerun, retain the matrix's frozen artifacts and run
`runWorkbenchServer` then `runWorkbenchClient` with `-PworkbenchCase=core`, `boots`,
`baseline`, `sibling` or `both`, plus `'-PworkbenchExpectedVersion=<version>'`
(quote dotted property values in PowerShell).
An upgrade client additionally uses `-PworkbenchUpgrade=true` with case `both`;
it requires the existing Boots-only server seed. Prefer the matrix for final
acceptance: focused runs alone do not enforce the across-stage hash guard.

Focused server reruns clear their matching live report directory. To reuse an
earlier matrix's acceptance, retain its `matrix.ok`, child process logs and frozen
hash manifest; do not assume all older live profile markers still coexist.

No IDE configurations or screenshots are produced. Clients hide their own
window and mute sound. Dedicated servers bind loopback, use a random port and
offline mode for isolated local tests. Intentional offline/native/JDK notices
are distinct from workbench failures; inspect accepted process logs as well as
the markers.

This suite does not replace Boots gameplay/JEI/REI tests, dedicated TCP networking
or the owner's ordinary-client visual acceptance. Those remain separate gates.
