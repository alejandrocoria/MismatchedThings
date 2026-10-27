# Shared workbench research spike

Opt-in, test-only nested mods for the family workbench ownership decision.
These projects are not a production shared library or a second armor mod. The
normal Gradle model, indev instances and Boots publication remain unchanged.

## What is exercised

- A `mismatchedthings` core v1.0 containing a real block, block item, menu,
  station recipe type, resources and a small contribution contract.
- A v1.1 core with the same v1.0 API plus a new class required by the sibling.
- A Boots integration stand-in compiled against v1.0, invoking the actual
  packaged Boots factory. It bundles v1.0, not the newer API.
- A synthetic helmet contributor compiled against v1.1, bundling v1.1.
- An independent observer and actual dedicated-server/client processes.

The real Boots JAR and the stand-in consumer are frozen and SHA-256 checked
before staging subsequent runs. The consumer is **not** a converted production
Boots release: Boots still owns its old station outside these disposable runs.
The synthetic helmet outputs are renamed vanilla items, not mixed helmets.

Core/API classes are compile-only for consumers and the observer. Runtime must
discover the core from the nested JARs; API, consumer and observer packages are
disjoint. Packaging checks reject loose API copies and production leakage.

## Commands

Run sequentially from the repository root:

```powershell
.\gradlew.bat -PsharedWorkbenchSpike=true :WorkbenchSpike:Fabric:verifySharedWorkbenchSpike --console=plain
.\gradlew.bat -PsharedWorkbenchSpike=true :WorkbenchSpike:NeoForge:verifySharedWorkbenchSpike --console=plain
.\gradlew.bat -PsharedWorkbenchSpike=true :WorkbenchSpike:NeoForge:verifySharedWorkbenchSpike '-Pneoforge_version=26.1.2.99' --console=plain
```

Restore the normal .114 production artifact after a .99 run. Do not run the two
NeoForge baselines concurrently: their production compilation outputs are shared.
The suite forwards the selected baseline and `--offline` to its nested runs.
No third-party indev JARs, user worlds or manual downloads are required.

To repeat just the old-world/new-core control:

```powershell
.\gradlew.bat -PsharedWorkbenchSpike=true :WorkbenchSpike:Fabric:verifySharedWorkbenchUpgrade --console=plain
.\gradlew.bat -PsharedWorkbenchSpike=true :WorkbenchSpike:NeoForge:verifySharedWorkbenchUpgrade --console=plain
```

Both commands regenerate a disposable old-core server world, then start a new
client process with both consumers and the newer core. The old Boots root's
complete encoded data is compared before any new crafting.

## Profiles and evidence

The full verifier runs:

1. Core alone: one registration owner and no contributors/recipes.
2. Boots alone: core v1.0, real Boots output and no sibling route.
3. Sibling alone: core v1.1, no installed Boots item/classes required.
4. Both: one selected v1.1 core and two independent recipe contributions.
5. An old v1.0/Boots world reopened with v1.1 and the new sibling, without
   rebuilding or replacing the frozen Boots consumer or actual Boots artifact.
6. Incompatible ranges: loader refusal before a working game session.

The three consumer profiles also launch hidden clients against copies of their
server-created worlds. Normal block-use and container packets open the family
screen, shift-click inputs, pay once and obtain two outputs. Both complete
authoritative output stacks are compared to the client inventory. These are
integrated-client packets, not a separate dedicated TCP client/server matrix or
physical input/pixel acceptance. No screenshots are required by this spike.

Each loader project writes only under its own `build/`:

- `frozen/<baseline>/hashes.json`: exact consumer/production JAR fingerprints.
- `reports/<baseline>/<case>/`: server/client markers, failure details and the
  old Boots root snapshot. `both/upgrade-client.ok` is the upgrade witness.
- `matrix/<baseline>/`: bounded child logs and aggregate `matrix.ok` or focused
  `upgrade.ok`. Expected incompatible-run errors are separate from positive runs.
- `run/<baseline>/<case>/`: disposable mods, configs and worlds.

The verifier stages genuine nested mod JARs. It explicitly generates fixture
metadata in Fabric's and NeoForge's formats; it does **not** claim to verify the
future production project wiring. Separate package-only controls verify native
Loom inclusion and ModDevGradle project-artifact identity/range generation:

```powershell
.\gradlew.bat -PsharedWorkbenchSpike=true -PworkbenchPackagingProof=true :WorkbenchSpike:Fabric:verifyNativeCorePackaging --console=plain
.\gradlew.bat -PsharedWorkbenchSpike=true :WorkbenchSpike:NeoForge:verifyNativeCorePackaging --console=plain
```

The Fabric proof flag is package-only; do not combine it with runtime cases,
which must discover the version from their staged consumers rather than force
the newer core onto a development classpath. Production must use these native
dependency mechanisms and inspect emitted coordinates/ranges, not copy the
research metadata generator.
