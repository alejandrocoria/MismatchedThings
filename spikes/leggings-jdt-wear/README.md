# Leggings JDT incoming wear research

**Superseded architecture:** the owner selected the
[symmetric shared-core spike](../shared-armor-wear/README.md) instead of the
optional Boots bridge. Keep this only as native/payment/conflict evidence until
the production plan retires both research folders; do not promote the bridge.

Disposable candidate, not production support. The private
`JUST-DIRE-THINGS-ARMOR-WEAR-EXPLORATION.md` owns findings and the eventual plan.
No product code or native originals are modified by enabling this source set.
Do not substitute these research passes for maintained production acceptance.

From the repository root, run **sequentially**, with cached catalog JDT 1.6.11:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runJustDireWearSpike -PleggingsJdtWearSpike=true -PleggingsJdtWearVariant=baseline --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runJustDireWearSpike -PleggingsJdtWearSpike=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runJustDireWearSpike -PleggingsJdtWearSpike=true -PleggingsJdtWearBoots=current -PleggingsJdtWearVariant=naive --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runJustDireWearSpike -PleggingsJdtWearSpike=true -PleggingsJdtWearBoots=current --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runJustDireWearSpike -PleggingsJdtWearSpike=true -PleggingsJdtWearBoots=published -PleggingsJdtWearTransform=transparent --offline --console=plain
```

`leggingsJdtWearBoots` selects `alone` (default), `current` or `published`.
Published uses the existing frozen Boots 1.0.0 NeoForge input under Leggings'
`build/verification/product/inputs/`, checked against the existing release pin.
Alternatively pass `-PleggingsJdtWearBootsJar=<unchanged-release.jar>`. It is never
rebuilt, discovered in indev or downloaded implicitly by this spike.

`leggingsJdtWearVariant` selects `candidate` (default), `baseline`, `naive` or
`refused`. Naive deliberately duplicates the conditional consumer on CommonHooks:
it succeeds only as a characterization of lost dual permission, not as a working
coexistence implementation. Refused corrupts only the unpublished own getter,
requiring ordinary Leggings wear with installed Boots still healthy. It is not a
manual override for a production guard. `leggingsJdtWearTransform=transparent`
adds the bounded compatible forwarding control. Add
`'-Pneoforge_version=26.1.2.99'` for the narrow floor check.

Reports are under
`mods/MismatchedLeggings/RuntimeTests/NeoForge/build/reports/<baseline>/jdt-wear-spike-<graph>-<variant>-<transform>/`.
Require fresh `server.ok`, `wear-spike.tsv` and no `server.failure.txt`.
The table records native/current/candidate actual incoming hits, direct calls,
integer event quantization, cancellation, partial-receipt rollback, stale
equipment and independent LEGS/FEET payments. Healthy bridge profiles also test
wrong-slot and foreign-ownership negatives on the actual exported class.
The candidate pays 140 FE for 2 wear with Unbreaking III, matching the checked
native formula; ordinary fallback retains vanilla probabilistic Unbreaking
instead of a fabricated deterministic result.

Only dedicated servers run: no client window, screenshots or prepared owner
world. Raw transformed classes are in each disposable server's `.mixin.out/`.
The bridge gates LEGS only, validates its local hook and reuses the installed
Boots caller verifier **on a detached ASM clone**. It never changes Boots' live
permission. This proves the selected pins, not arbitrary future private APIs.

The standalone shape/router are research adaptations of the existing Boots
implementation, not newly shipped copies. The candidate uses direct pinned JDT
identities and production FE capabilities. A production plan must replace this
test binding with a local query/stamp from Leggings' existing storage binding
and private-verifier reflection with a bounded local caller proof. Retain scoped
refusal when an optional routing API changes. No shared Things
framework or mandatory Boots dependency is selected by this source set.

Retirement removes **this entire folder**, `justdire-wear-spike.gradle`, its
conditional apply, Skeleton reflective entry point and JustDireFaultPlugin
delegate. Promote only useful maintained controls and restore ordinary `.114`
outputs with the research flag omitted. Do not leave an alternate consumer path.
