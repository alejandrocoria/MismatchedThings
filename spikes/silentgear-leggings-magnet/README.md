# Silent Gear leggings magnet spike

This is an opt-in, test-only candidate for a future Magnetic/Greedy leggings adapter. It exercises the pinned Silent Gear 4.2.3/Silent Lib 11.2.0 APIs through focused runtime fixtures; it does not enable production dispatch or prove transformed-hook coexistence.

Run sequentially from the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgLeggingsMagnetSpike -PleggingsSgMagnetSpike=true -PleggingsSgMagnetGraph=alone '-Pneoforge_version=26.1.2.114' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgLeggingsMagnetSpike -PleggingsSgMagnetSpike=true -PleggingsSgMagnetGraph=boots '-Pneoforge_version=26.1.2.114' --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifySgLeggingsMagnetSpike -PleggingsSgMagnetSpike=true -PleggingsSgMagnetGraph=alone '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

The candidate is compiled only when `-PleggingsSgMagnetSpike=true` is set. When
maintained production tests replace it, remove this tree, the
`RuntimeTests/silentgear-leggings-magnet.gradle` include, its NeoForge build apply
and the property-gated `SkeletonTestNeoForge` entry. Do not promote the candidate
directly to production.
