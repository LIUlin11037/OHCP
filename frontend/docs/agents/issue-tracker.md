# Issue tracker: GitHub

Issues and specs for this repository live in GitHub Issues at `java-ohcp/front-end`. Use the GitHub CLI from this repository when it is available and authenticated; if it is unavailable, use the connected GitHub tools.

## Conventions

- Create an issue with `gh issue create --title "..." --body "..."`.
- Read an issue with `gh issue view <number> --comments` and include labels when needed.
- List issues with `gh issue list` and suitable state and label filters.
- Comment with `gh issue comment <number> --body "..."`.
- Apply or remove labels with `gh issue edit <number> --add-label "..."` or `--remove-label "..."`.
- Close with `gh issue close <number> --comment "..."`.
- **PRs as a triage surface: no.** Pull requests are not incoming feature requests for triage.

## When a skill says to publish or fetch work

- Publish a ticket as a GitHub issue.
- Fetch a ticket with `gh issue view <number> --comments`.
- Infer the repository from the `java-ohcp/front-end` remote; verify the issue title and state before changing an issue.
