# Shared workbench runtime tests

These fixtures compile against the actual loader JAR but load it only from
their disposable `mods` directory. Boots, indev JARs/configuration/worlds and
research spike sources are not dependencies. Test mods never enter core artifacts.

## Commands

Run from the repository root, sequentially:

```powershell
.\gradlew.bat :MismatchedThings:Common:test --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:Fabric:verifyWorkbench --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:NeoForge:verifyWorkbench '-Pneoforge_version=26.1.2.99' --console=plain
.\gradlew.bat :MismatchedThings:RuntimeTests:NeoForge:verifyWorkbench --console=plain
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

No IDE configurations or screenshots are produced. Clients hide their own
window and mute sound. Dedicated servers bind loopback, use a random port and
offline mode for isolated local tests. Intentional offline/native/JDK notices
are distinct from workbench failures; inspect accepted process logs as well as
the markers.

This suite does not yet certify Boots integration, nested-core selection among
multiple consumers, historical binary compatibility, JEI/REI or dedicated TCP
client/server behavior. Those are later coordinated implementation gates.
