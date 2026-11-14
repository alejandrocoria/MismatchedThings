# Leggings JDT Lava Repair spike

Opt-in research, not production support. Adapts Boots' accepted transient ritual
to strict mixed Leggings validation, through fixture-only item hooks. No product
source, registry ID, schema, version or published JAR is patched.

The [exploration](../../mods/MismatchedLeggings/local-docs/JUST-DIRE-THINGS-LAVA-REPAIR-EXPLORATION.md)
owns policies, findings, evidence and remaining production work.

From the root, run **sequentially**:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runLavaSpikeServer -PleggingsLavaSpike=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:runLavaSpikeClient -PleggingsLavaSpike=true --offline --console=plain
```

Repeat both with `-PleggingsLavaBoots=true` for ordinary current Boots coexistence.
The focused minimum-loader server uses that property plus
`'-Pneoforge_version=26.1.2.99'`. JDT comes from the shared downloaded/checksummed
artifact pin; no indev JARs are read. Omit `--offline` on first resolution.

The existing runtime fixture hides the client **before window creation** and
mutes it; the scenario asserts both. It samples actual live entities and native
particle factory calls, not screenshots. Its disposable saved world exists only
to launch the integrated-server test, not for owner acceptance.

`spike.gradle` generates `LavaCandidate.java` from the current Boots source with
product identity/strict-validation substitutions, only in fixture build outputs.
It compiles/runs without Boots, then optionally stages its ordinary current JAR.
`LavaItemMixin` adds candidate callbacks to the Leggings item only; the ordinary
production item/JAR still has no Lava Repair hook. The client particle probe is
observation-only. The server checks explicitly tick real ItemEntities; the
client uses natural world ticks and ordinary entity/particle packets.

Require `server.ok`/`server.txt` and, for the hidden client, `client.ok`/`client.txt`
under `mods/MismatchedLeggings/RuntimeTests/NeoForge/build/reports/<baseline>/lava-spike[-boots]/`,
with no matching failure marker. Logs are normal Gradle/game outputs.

After research, restore normal fixture outputs with:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:classes :MismatchedLeggings:NeoForge:assemble :MismatchedBoots:NeoForge:assemble --offline --console=plain
```

Retain this bounded candidate only until a production plan promotes the relevant
tests. That plan should remove the **whole** spike, conditional apply and reflective
entry branch; do not leave a second runnable ritual implementation as history.
