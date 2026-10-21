# Domain docs

This repository uses a single domain context.

## Before exploring

- Read the root `GLOSSARY.md` when it exists.
- Read relevant records in `docs/adr/` when they exist.
- If these files do not exist, continue without treating their absence as a blocker. Create or update domain records only when terminology or a lasting architectural decision is actually resolved.

## Vocabulary and decisions

- Use glossary terms consistently in code, tests, documentation, and issues.
- If a needed term is absent, decide whether it belongs in the domain or whether existing vocabulary is sufficient before adding a new term.
- Surface conflicts with existing ADRs explicitly instead of silently overriding them.
