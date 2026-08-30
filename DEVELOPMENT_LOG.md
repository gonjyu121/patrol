# PatrolSpectatorPlugin Development Log

## Current Status (2026-08-30)
- **Version**: 1.9.100
- **Branch**: `feature/patrol-logic-update`
- **Build Status**: Passed (`mvn clean package` with JDK 21)
- **External Plugins**: Updated all dependencies to latest versions via `build_v2.ps1`
- **Key Features & Fixes in v1.9.100**:
    - **Ender Dragon Spawning on End Recreation**:
        - Fixed missing Ender Dragon after End world recreation by ensuring `battle.generateEndPortal(false)`, `battle.resetCrystals()`, and `battle.setPreviouslyKilled(false)` are initialized, and explicitly spawning an `EnderDragon` entity (with `CIRCLING` phase) if not present.
        - Fixed `checkDragonAbsence()` to also trigger recreation if no Ender Dragon exists even if `hasBeenPreviouslyKilled()` is false (handling unspawned / corrupted states after grace period).
        - Updated `EndGameManager` to look up the End world dynamically instead of using a hardcoded `"world_the_end"` string.
        - Added `EndResetManagerTest` unit tests.
- **Key Features & Fixes in v1.9.99**:
    - **End World Absence & Expired Timer Immediate Recreation Fix**:
        - Fixed `checkDragonAbsence()` to use `DragonBattle.hasBeenPreviouslyKilled()` and respawn state directly, removing the blocking `players.isEmpty()` check that prevented End detection on empty/startup servers.
        - Added immediate End recreation (10-second warning) when a server starts up with an already-defeated Ender Dragon whose reset delay has elapsed.
        - Fixed scheduled countdown resume on startup if reset timer is within delay window.
    - **End World Recreation & Auto-Reset Fix (from v1.9.98)**:
        - Fixed `unloadWorld` failure by adding a 20-tick delay after player evacuation to allow cross-dimension transitions and chunk ticket clearing.
        - Fixed file deletion to completely clean up `region/`, `entities/`, `poi/`, `data/`, `level.dat`, `level.dat_old`, and `session.lock` (with Windows retry logic).
        - Removed erroneous `battle.initiateRespawn()` call on newly created worlds to ensure natural initial Ender Dragon generation.
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

