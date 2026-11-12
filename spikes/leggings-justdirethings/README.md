# Leggings / Just Dire Things bounded spike

Opt-in research candidate, **not production compatibility**. Normal builds do not
include this project. Do not install its JAR in a player instance.

This checks native Walk/Run/Swim callback reuse, root-driven live Phase and native
settings-screen coexistence with current Boots. It does not implement upgrade
inheritance, resource storage/capabilities, Decoy, Lava Repair, refunds or tooltip
presentation. Leggings settings edits are deliberately local to the client;
authoritative edit networking and stale-selection rejection remain implementation
work. Native and Boots settings retain their existing routes.

Run from the repository root, **sequentially**:

```powershell
.\gradlew.bat :LeggingsJdtSpike:runSpikeServer -PleggingsJdtSpike=true --offline
.\gradlew.bat :LeggingsJdtSpike:runSpikeClient -PleggingsJdtSpike=true --offline
```

Repeat with `-PleggingsBoots=true` for current packaged Boots. Omit `--offline`
when the shared fixture pin or loader artifacts have not yet been cached. No
indev mods/worlds are read. No IDE configurations are generated. Client runs reuse
the maintained pre-GLFW hiding hook, mute audio and disable the early loader
window. The saved server world is only an internal disposable automatic fixture,
not a required manual-test world.

Evidence is under `build/reports/<neoforge_version>/alone` or `boots`. The run
tasks require their success markers and reject failure markers; successful game
shutdown alone is not acceptance. No screenshots are needed by these checks.

Retire the candidate and opt-in settings include when maintained production
coverage supersedes it. Nothing here should be bundled into product artifacts.
