# PatrolSpectatorPlugin Development Log

## Current Status (2026-08-21)
- **Version**: 1.9.98
- **Branch**: `feature/patrol-logic-update`
- **Build Status**: Passed (`mvn clean package` with JDK 21)
- **External Plugins**: Updated all dependencies to latest versions via `update_plugins.ps1`
- **Key Features & Fixes in v1.9.98**:
    - **End World Recreation & Auto-Reset Fix**:
        - Fixed `unloadWorld` failure by adding a 20-tick delay after player evacuation to allow cross-dimension transitions and chunk ticket clearing.
        - Fixed file deletion to completely clean up `region/`, `entities/`, `poi/`, `data/`, `level.dat`, `level.dat_old`, and `session.lock` (with Windows retry logic).
        - Removed erroneous `battle.initiateRespawn()` call on newly created worlds to ensure natural initial Ender Dragon generation.
        - Refined dragon absence detection by checking `DragonBattle.hasBeenPreviouslyKilled()` to prevent false positives when chunk (0,0) is unloaded.
        - Preserved `scheduledResetTime` state on unload retries rather than prematurely wiping it.
    - **`/patrol` Command Access**: Restricted all `/patrol` subcommands and tab completions strictly to OP (`sender.isOp()`).
    - **`/patrol back` (Manual Start Recovery)**: Saves pre-patrol inventory, armor, and location to `last_manual_start_state.yml` and restores upon `/patrol back`.
    - **Dungeon Built Flag**: Added `built: true` in `dungeon_config.yml` to prevent duplicate auto-generation on restarts.
    - **Performance Overrides**: Forced `forceViewDistance` and `forceSimulationDistance` per world on startup.
    - **Legacy Stats Expansion**: Extended legacy importer to include `playerKills` and `eventPoints`.
    - **`/patrol travel` Improvement**: Uses player's current location to find distant villages (3000-8000 blocks away).

## System Notes
- **OneComme Integration**: Discontinued/Not used in production. Chat integration operates via Discord / Minecraft built-in listener.

## Fast Context Restoration Protocol
- Next session entrypoint: Read this file (`DEVELOPMENT_LOG.md`) and run `git status -s` first.
- Avoid large unconstrained `git diff` or full directory scans unless specific details are needed.

