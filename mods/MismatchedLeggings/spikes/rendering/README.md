# Equipped leggings rendering spike

Test-only proof for Minecraft 26.1.2. This is not a Leggings product or a supported
armor API. Normal builds/publication do not include these projects; opt in with
`-PleggingsSpike=true`. There are no recipes, product registries or saved-data
contracts. The fixture marks disposable vanilla leggings stacks instead.

## Run

From the repository root, sequentially:

```powershell
.\gradlew.bat -PleggingsSpike=true :LeggingsSpike:Fabric:verifySpike --offline
.\gradlew.bat -PleggingsSpike=true :LeggingsSpike:NeoForge:verifySpike --offline
.\gradlew.bat -PleggingsSpike=true '-Pneoforge_version=26.1.2.99' :LeggingsSpike:NeoForge:verifySpike --offline
```

Repeat with `-PleggingsSpikeBoots=true` to stage the real packaged Boots JAR
(including its bundled core). The spike neither compiles against Boots nor loads
it in standalone profiles. The coexistence fixture constructs actual mixed boots
through reflection and observes both source-side submissions while leggings render.
No production source is patched by this fixture.

Optional real-mod model control:

```powershell
.\gradlew.bat -PleggingsSpike=true -PleggingsSpikeIceAndFire=true :LeggingsSpike:NeoForge:verifySpike --offline
```

This downloads/stages Ice and Fire, Uranus and Jupiter through Boots' existing
exact-artifact resolver and SHA-256 pins, **not** from indev. Omit `--offline` if
the pinned downloads are not cached. The bulk model control supplies a vanilla
asset explicitly because it runs without world registry/component initialization.
The comparison gallery separately supplies the inspected native silver/red-scale
asset keys, comparing real native and mixed geometry/textures. Neither check
claims complete gameplay or every item-default/asset combination.

Use `verifySpike`, not just `runSpikeClient`: a clean game exit without a fresh
`client.ok`, or with `failure.txt`, must fail Gradle verification.

## Isolation and evidence

Each loader owns `build/run/<baseline>/<scenario>` and matching
`build/reports/<baseline>/<scenario>` directories. Scenarios separate standalone,
Boots coexistence and optional Ice and Fire. Generated state is ignored by Git.
No client/server indev instance, existing world or external modpack is touched.

Clients start hidden through the test-only GLFW mixin, remain hidden, have master
volume zero and disable NeoForge's early window. Do not remove that contract to
inspect results: open the generated `waist-panel.png` instead. No IDE run
configurations are generated. The client stops after bounded checks and a real
resource reload; success evidence is in `client.ok`, failures in `failure.txt` or
the native run log.

The image uses real GUI entity rendering and the native armor/deferred feature
pipeline, not a software reconstruction. It covers armor stands front/back, a
crouching zombie, baby zombie, small posed stand, walking/seated player states and
dyed/trimmed/glinting leather. With Ice and Fire, the same capture instead compares
native silver/red-scale leggings with mixed pairs in both orders, front/back/side
views and an independently posed armor stand. It does not claim in-world
animation-mod acceptance.

## Hierarchical waist proof

`WaistHierarchy` reconstructs only the renderable waist branches and their
ancestors, including empty intermediary parts. Each descendant's initial pose is
composed into reference waist space. `WaistClipper` uses that transform only to
classify/intersect vertices; output positions, UVs and normals remain child-local.
The copied hierarchy receives all live transforms/flags after native `setupAnim`.

Ownership is anatomical-left/right in the **reference waist**, not in the child's
own coordinates or the final animated pose. Moving a child across the center does
not hand its geometry to the other source. The waist's own crouch/body pose is not
part of this reference clipping plane. No source part is temporarily reset or
hidden to inspect its base pose.

Focused checks cover rotated/scaled mirrored geometry, empty parents, nested
children, area conservation, UV/normal interpolation, visibility/`skipDraw`,
independent child animation, construction after another pose, queued A → B → A
reuse and rejection of singular reference transforms. The Ice and Fire control
requires all 30 pinned native replacements, including 13 decorated waists, to pass
preflight and native-area checks in standing/walking/crouched/seated/posed-stand
states. It also checks that those native descendant transforms remain equal to
their initial poses; the independently animated case is synthetic, not an invented
claim about Ice and Fire behavior.

## Deliberate implementation boundaries

- `WaistClipper` builds privately owned polygons using native cube compilation.
  `LeggingsView` keeps one complete native leg and delegates animation at draw time.
  Ordinary waist descendants are handled through their reference hierarchy;
  custom cube compilers, invalid reference transforms and renderable model-root
  geometry remain outside the demonstrated route.
- `BufferedEquipment` collects the standard equipment renderer's model submissions
  before either half is forwarded. Both final models must pass preflight. It is
  **not** a transaction wrapper for arbitrary custom renderers or their side effects.
- The fixture intercepts only marked `LEGS` stacks at `renderArmorPiece` entry,
  leaving Boots' existing redirect untouched. It does not register a product-wide
  Fabric `ArmorRenderer`; that alternative entry point remains untested.
- Fabric's synthetic custom armor renderer is rejected through an internal registry
  lookup. This characterizes the boundary; production must deliberately choose
  its integration, not treat this internal class as a stable public API.
- Geometry/views are short-lived per submission, not globally cached. Reload
  acceptance does not establish production cache performance or memory behavior.
- Rejection leaves the real destination untouched. A production fallback's visible
  appearance is deliberately not selected here; supported GUI cases fail loudly
  if they unexpectedly become unsupported.

The disposable source tree can be removed or promoted deliberately when a
production implementation is planned; it is not bundled into either released mod.
