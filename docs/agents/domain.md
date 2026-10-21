# Domain Docs

This repository uses a single-context domain documentation layout.

## Before exploring

- Read `CONTEXT.md` at the repository root.
- Read relevant architectural decision records in `docs/adr/`.

If these files do not exist, proceed silently. Do not suggest creating
them upfront. The domain-modeling skill creates them when domain terms
or architectural decisions are resolved.

## File structure

- `CONTEXT.md`: shared domain terminology and context.
- `docs/adr/`: architectural decision records.

## Use the glossary's vocabulary

Use the terms defined in `CONTEXT.md` in issue titles, proposals,
hypotheses, and test names. Avoid synonyms the glossary explicitly rejects.

If a needed concept is absent, reconsider whether it belongs to the domain.
Record genuine terminology gaps for the domain-modeling skill.

## Flag ADR conflicts

Explicitly identify any proposal that contradicts an existing ADR.
Reference the ADR and explain why the decision should be reconsidered.
