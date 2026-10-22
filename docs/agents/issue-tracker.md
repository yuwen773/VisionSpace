# Issue tracker: GitHub

Issues and specs live in GitHub Issues for `yuwen773/VisionSpace`.
Use the `gh` CLI from this repository; infer the repository from its remote.

## Conventions

- Create: `gh issue create --title "..." --body-file <path>`
- Read: `gh issue view <number> --comments`
- List: `gh issue list --state open --json number,title,body,labels,comments`
- Comment: `gh issue comment <number> --body-file <path>`
- Add labels: `gh issue edit <number> --add-label "..."`
- Remove labels: `gh issue edit <number> --remove-label "..."`
- Close: `gh issue close <number> --comment "..."`

Write multiline bodies to a temporary UTF-8 file and use `--body-file`.
Use the label vocabulary in `docs/agents/triage-labels.md`.

Existing files in `docs/issues/` remain reference material.
New tracked issues and specs belong in GitHub Issues.

## Pull requests as a triage surface

**PRs as a request surface: no.**

When enabled, use the equivalent `gh pr` commands and include only external
contributors in the PR triage queue, excluding owners, members, and collaborators.

GitHub shares issue and PR numbers. When the type is ambiguous, try
`gh pr view <number>` and fall back to `gh issue view <number>`.

## Skill operations

- "Publish to the issue tracker": create a GitHub issue.
- "Fetch the relevant ticket": read the issue and its comments.

## Wayfinding operations

- Map: one issue labelled `wayfinder:map`, containing Notes,
  Decisions-so-far, and Fog.
- Child tickets: link as GitHub sub-issues. If unavailable, use a task list
  in the map and `Part of #<map>` in each child's body.
- Ticket types: `wayfinder:research`, `wayfinder:prototype`,
  `wayfinder:grilling`, and `wayfinder:task`.
- Blocking: prefer native GitHub issue dependencies. If unavailable,
  record `Blocked by: #<number>` in the child body.
- Frontier: the first open child in map order with no open blockers
  and no assignee.
- Claim: `gh issue edit <number> --add-assignee @me`.
- Resolve: comment with the result, close the ticket, then add a brief
  result and link to the map's Decisions-so-far.
