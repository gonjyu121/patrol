---
trigger: always_on
---

# Fast Context Restoration & Token Efficiency Rule

1. **Quick Context Entrypoint**:
   - When asked to check current status or continue work, read `DEVELOPMENT_LOG.md` immediately in 1 step.
   - Do NOT run wide directory searches (`list_dir`), full commit logs, or broad unconstrained `git diff` unless specifically requested.

2. **Compact Commands**:
   - Use `git status -s` instead of full `git status`.
   - Inspect specific file diffs only when editing, rather than dumping all diffs.

3. **Mandatory End-of-Turn Log Maintenance**:
   - At the end of every feature implementation, bug fix, or significant task, ALWAYS update `DEVELOPMENT_LOG.md` with the latest version, status, and summary of changes so future sessions remain ultra-fast.

4. **Response Efficiency**:
   - Keep answers direct, concise, and focused on immediate action.
