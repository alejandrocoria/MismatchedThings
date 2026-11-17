# Historical inputs

[boots-1.0.0.json](boots-1.0.0.json) pins the owner-supplied, unchanged Boots 1.0.0
artifacts used for packaged sibling/core acceptance. JARs are not committed.
Callers supply explicit paths; tests freeze copies and verify SHA-256 before and
after execution. They never infer a release from a current build or indev folder.

The NeoForge pin identifies the corrected production artifact with scalar
`@Redirect.at`, compatible with MixinExtras 0.5.4. An earlier local JAR carried
the same 1.0.0 filename but the pre-fix array encoding; it is not an interchangeable
baseline. The corrected hash was also recorded with the original binary fix.

Do not update these pins merely to make a locally rebuilt JAR pass. A future
historical baseline needs the actual published artifact and recorded provenance.

## Pending automatic download

Once the Boots 1.0.0 releases are publicly available on Modrinth, replace required
local JAR paths with automatic resolution of those exact Fabric/NeoForge files.
Reuse the compatibility-test artifact resolver/cache pattern: pinned version/file
identifiers, checksum validation and offline reuse after preparation, never a
floating latest release or an indev/rebuilt substitute. Verify the published bytes
against the existing pins; investigate mismatches rather than silently changing
the accepted baseline. Until implemented, both explicit path properties remain
required. This is a test-infrastructure follow-up, not a product release blocker.

## Aether II alpha.4.1 mixed data

[aether-alpha.4.1-mixed.json](aether-alpha.4.1-mixed.json) freezes 12 complete
Boots/Leggings factory outputs from the isolated alpha.4.2 spike's `old` phase.
They were generated before the production migration, with the generator committed
as `93070465`, and captured on 2026-11-16. These are native alpha.4.1 format
inputs, **not** a claim that the current development factories are published
Boots 1.0.0 binaries.

Native input: `aether_ii-26.1.2-alpha.4.1-neoforge.jar`, Modrinth `soZ6Sulf`,
SHA-256 `97760dd75e2dcdf4ecde83a0006d5b3682090e104a3c7bd6d7e4681e33b3f0e6`.
The frozen fixture's SHA-256 is
`14f3eb26e696d35930a534b9827d490e412ffa91b69323015be45c980670d36c`.

Both products/orientations include a vanilla partner, unequal sets with an
explicit source override and a duplicate set, native charms/tier, root/source
names, damage, repair cost, merged attributes and opaque custom NBT. Maintained
production persistence tests read these bytes directly, including without Aether;
they never regenerate them with a current codec or use ignored spike reports.
Expected alpha.4.2 shape is asserted independently of the production converter.
