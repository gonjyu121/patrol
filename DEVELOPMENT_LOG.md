# PatrolSpectatorPlugin Development Log

## Current Status (2026-05-10)
- **Version**: 1.9.81
- **Stability**: 24/7 stream is stable on Minefort. Chat & Promotions added.
- **Recent Progress**: 
    - **OneComme Optimization**: Refactored Discord chat format to `💬 Player: Message` for cleaner TTS and overlay.
    - **Engagement System**: Added `EngagementBroadcaster` for periodic in-game YouTube promotions.
    - **Ranking System**: (Previous) Consolidated rankings into single Discord message.
    - **Missing Data**: (Previous) Added "Continuous Survival", "PK Count", etc.

- [x] **Chat & YouTube Integration**:
    - [x] **Consolidate TTS**: Optimized for OneComme (わんコメ) unified chat experience.
    - [x] **Plugin Tweaks**: Modified `DiscordListener.java` for cleaner message formatting.
    - [x] **YouTube Engagement Strategy**:
        - Implemented `EngagementBroadcaster` for periodic in-game promotion messages.
- [ ] **Next Steps**:
    - [ ] Monitor YouTube analytics and adjust broadcast frequency/content if needed.
    - [ ] Consider adding "Selective YouTube Relay" if direct API posting becomes necessary.

## Reference for Next Session
1. Read this file immediately to restore state.
2. **Current Context**: The user wants to bridge Minecraft chat to YouTube to increase engagement and monetization, while keeping the technical overhead low via OneComme + Discord.
3. The user's ultimate goal is a **100% Free** public 24/7 server with a growing YouTube community.
