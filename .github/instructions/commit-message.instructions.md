# Commit Message Instructions

Style: imperative capitalized title, blank line, short paragraph and
bullets, plus an optional trailing `Note:` block.

## Title
- One line, imperative: Add, Fix, Refactor, Change, Remove, Update.
- Max ~70 characters, no trailing period, no `feat:`-style prefixes.
- Use `Refactor:` only when behavior does not change.

## Body
- Blank line after the title, then a short paragraph and/or bullets.
- Wrap lines at ~76 characters. Say what changes and why, not how.
- New option: name it, give its default, and say what happens when it is off
  (the exact no-op when that applies).
- Bug fix: the root cause in one sentence.
- Refactor: state "no behavior change" and which test proves it.
- Mention added tests and updated docs (README, docs/es, language keys) only
  if they really changed.
- End with `Note:` or `Known limitations:` only if a player or developer
  must know it.

## Do not
- Mention AI tools or agents.
- Include mod_version unless the commit is the version bump.
- List IDE files (.idea) or gradlew.bat.

## Commits
One commit per stage or feature; never mix stages.

## Examples from this history

d3b4e8f (feature, tests and docs):
Add magma layer and mineral core to mining satellites (stage 2)
Satellites now have four concentric regions: shell, stone, magma and core.
- With satellites_enabled = false nothing changes.
- Add tests for layer geometry and update README and docs/es/.

94a0c4d (refactor):
Refactor: give each sphere its own centerY (no behavior change)
- Every sphere gets params.centerY(), so generation is unchanged.
- Add an equivalence test over a grid of cells for each configuration.

d73e308, nested in 9820c74 (fix): "Fix biosphere heightmap chunk checks"
plus one sentence naming the root cause.
