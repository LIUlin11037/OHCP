# Agent instructions

## Task branches and staged commits

- At the start of each new request or independent task, including features, fixes, refactors, and documentation updates, check the current branch, working tree changes, and baseline. Before editing files, create and switch to a new branch for that task. Base it on the latest main branch by default, using the repository's actual default branch. If the task depends on another task, base it on that dependency branch and explain the dependency. If Git is not initialized, initialize Git and establish the main baseline before creating the task branch.
- Use one branch per task, named by purpose as `feat/<task>`, `fix/<task>`, `refactor/<task>`, `docs/<task>`, or `chore/<task>`. Follow existing project naming conventions when present. Reuse the branch when continuing the same task. Develop and commit on the task branch.
- Preserve existing uncommitted changes before creating or switching branches and identify which task owns them. Commit only files or hunks belonging to the current task; do not mix in other work.
- Prefer multiple focused commits per task, committing independently reviewable and verifiable stages such as core implementation, edge cases and tests, and documentation. Usually make 2–6 commits. A small task that cannot reasonably be split may use one commit. Split by real stages; do not create empty or mechanical commits to reach a count. Keep each task to 6 commits or fewer where possible; narrow the scope or split independent work if it would exceed that, and explain when more are necessary. Preserve meaningful stages when cleaning history, and follow the user's authorization before rewriting published commits.

## Agent skills

### Issue tracker

Issues for this repository live in GitHub Issues at `java-ohcp/front-end`. Follow `docs/agents/issue-tracker.md`.

### Domain docs

This is a single-context repository. Read the root `GLOSSARY.md` and relevant decisions in `docs/adr/` when they exist. Follow `docs/agents/domain.md`.
