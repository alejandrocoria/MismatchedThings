# Shared MA refund spike

Disposable candidate for the
[joint exploration](../../local-docs/MYSTICAL-AGRICULTURE-SHARED-REFUNDS-EXPLORATION.md).
Not production integration or a new maintained runtime suite.
The [joint implementation plan](../../local-docs/MYSTICAL-AGRICULTURE-SHARED-REFUNDS-IMPLEMENTATION.md)
requires complete executable-spike retirement in C after maintained tests replace it.

Run from the repository root with the normal JDK/cache permissions:

```powershell
.\spikes\shared-ma-refunds\run.ps1
.\spikes\shared-ma-refunds\run.ps1 -Case nf-both,fabric-both,nf-joint
```

`run.ps1` invokes the existing server tasks through the explicit `init.gradle`.
It requires fresh success markers and rejects runtime errors, archives per-case
evidence under `build/evidence`, and never reads indev JARs. Runs are sequential;
the floor profile uses `.99`. Restore normal `.114` fixture compilation afterward.
There are no clients, screenshots or prepared manual worlds.

## Isolation and evidence limits

- Production sources and built product/core JARs are not rewritten. Only the
  existing disposable runtime staging copies are patched, including their nested
  Things JARs. Baseline timing instead loads unchanged current production JARs.
- The source generator derives the candidate observer/flow proof from the
  maintained Leggings implementation. It removes the exact Boots-router branch;
  Fabric accepts native consumption, NeoForge also accepts the existing core JDT
  chain. The candidate core contains no product class reference.
- The staged products' old MA mixins are filtered out. Their now-unused refund
  entry/final proofs are stubbed only in those candidate copies, leaving their
  other MA effect proofs intact. Actual refund permission comes from the shared
  candidate's entry/final proof, not the stubbed old proof. The current core JDT
  proof's obsolete Boots MA-router branch is also removed from staged copies.
- The shared API and proof/mixin/plugin are added to the selected nested core,
  not the fixture classpath. Fixture adapters/probes stay in fixture output.
- Fixture-time registration calls each actual product's private physical-purchase
  reader reflectively. It uses the same receipt implementation for both roots,
  with invocation-local ownership/source/durability checks and normal-return
  delivery. This is a witness, not proposed reflective production coupling or
  proof of product bootstrap/optional binding lifecycle.
- The maintained Leggings adversarial suite runs unchanged except its old-hook
  witness is replaced by one-core/four-stage/no-product-hook assertions. A
  mechanically adapted copy also exercises Boots, replacing leggings source IDs,
  data/factory access and Speed with boots-compatible Jump Boost. Production
  tests are not silently rewritten to accept a candidate in ordinary runs.
- Generated fixture class files are instrumented in `build` for this explicit
  invocation. After finishing, restore ordinary fixture output without the init
  script, sequentially, using the task-specific rerun option (not a broad clean):

  ```powershell
  .\gradlew.bat :MismatchedLeggings:RuntimeTests:Fabric:compileJava --rerun --offline --console=plain
  .\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:compileJava --rerun --offline --console=plain
  ```
- An obsolete Boots 1.0.0 staging copy, if encountered, is preserved under this
  spike's ignored evidence directory. It is not a supported positive graph.

Remove the complete spike sources, init script and launch instructions when
maintained production coverage replaces them. Do not retain a second behavior
path or wire the init script into the product build.
