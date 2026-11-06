# Forbidden Arcanus modifier lifecycle spike

Opt-in NeoForge experiment for Eternal/Soulbound, not production compatibility
and not part of ordinary runtime compilation. Uses the maintained pinned
Forbidden Arcanus/Valhelsia fixtures; the client also uses pinned JEI for the
documented upstream optional-JEI linkage problem.

From the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyForbiddenArcanus -PleggingsForbiddenModifiersSpike=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyForbiddenArcanus -PleggingsForbiddenModifiersSpike=true -PleggingsForbiddenServerOnly=true '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

Omit `--offline` when preparing fixture downloads for the first time. Run
sequentially. After a floor run, restore normal `.114` packaging/compilation
without the opt-in property. No indev JAR, world or config is read.

This reuses the existing Forbidden Arcanus launch/hidden-window/muting harness,
with separate `forbiddenArcanus[-server-only]-modifiers-spike` disposable run,
report and verification paths. It additionally requires fresh
`modifiers-server.txt` / `modifiers-client.txt` for the phases selected. Normal
Aureal checks still run; their success is not modifier production acceptance.

The server observes all none/Eternal/Soulbound source combinations in both
orientations, real durability wear, native modifier smithing and subsequent trim,
actual slot payment, codec/stream persistence and small root-only prototypes.
It uses actual fatal damage and `PlayerList.respawn`, with native attachment
selection/copy/restore, not substitute death/respawn events. Actors have a loaded,
unnegotiated in-memory connection; no real remote client receives those packets.

The client records native source/root names and normal/advanced tooltip text,
and dispatches the native tooltip texture event before/after the harness's real
resource reload. The texture event has no GUI extractor and is only a subscription
check, not a rendered-frame/UI acceptance test. No screenshot is produced.

The prototype calls native modifier application on a **copy**, restores the
structural root `ITEM_NAME`, and shows native Eternal can be reapplied after
recomposition. Its admission copy uses a native source item identity with live
root components; it is never returned as an item. This is not a production
recipe/menu interception or a decision about inheritance/balance/presentation.

Current unsupported malformed outputs are observed explicitly: their persistent
decode can suspend them while network decode preserves the malformed state.
Only valid/native outputs are required to round-trip unchanged. Reports must not
be interpreted as approval of the observed bugs.

Findings and proposed decisions are in the private Leggings compatibility docs.
The 2026-11-06 experiment passed `.114` server/hidden-client and focused `.99`
server checks. Normal `.114` compilation/profile was restored without the flag.
Remove this source directory and its opt-in hooks once maintained production
tests replace the experiment.
