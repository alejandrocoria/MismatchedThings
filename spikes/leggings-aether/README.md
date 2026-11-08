# Aether II leggings presentation feasibility

Only presentation feasibility remains here after production gameplay block A.
The native gameplay prototype, its transform-order helper, source tag mixin and
candidate omission rules have been retired. Maintained production coverage is
`verifyAetherGameplay`, documented in the
[Leggings runtime guide](../../mods/MismatchedLeggings/RuntimeTests/README.md#aether-ii-production-gameplay).
The original baseline/candidate measurements remain in the private exploration
and Git history; those old modes are no longer executable against changed defaults.

From the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyAetherSpike --offline --console=plain
```

Add `-PleggingsBoots=true` for current Boots. Run profiles sequentially and omit
`--offline` for the resolver's first pinned download. This profile builds packaged
products, seeds a disposable server world using the maintained production
gameplay checks, then runs the remaining hidden/muted client. No indev directory
is read or written. It creates no IDE run configuration or visible client window.

The remaining client verifies existing native layer/material/selected-leg
submissions and reload, writes a detached native source-layout preview and
constructs an installed-only native charm graphic. It does not install the
production source presentation adapter, claim final UI acceptance or copy assets.
All gameplay and default charm omission come from packaged production.

Reports are under
`mods/MismatchedLeggings/RuntimeTests/NeoForge/build/reports/<baseline>/aether-spike-presentation[-boots]/`;
the wrapper requires server/client completion and writes corresponding
`build/verification/.../matrix.ok`. Known native dedicated-server Toast ERRORs
remain visible and exactly counted; unexpected ERRORs/refusals fail. Clients
must have zero ERRORs and stay hidden from GLFW window creation.

Retire the remaining client, its renderer observation mixin/plugin and this
Gradle application when maintained B/C presentation/rendering checks replace it.
No part of this fixture belongs in a production JAR.
