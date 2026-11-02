# Historical Boots inputs

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
