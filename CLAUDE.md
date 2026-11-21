# CLAUDE.md

Shared instructions for all agents working in this repo (multiple users).

## General rules
- Reply in Polish or English, matching the language of the user's prompt.
- Never use em dash "—", always use hyphen "-".

## Commit workflow
- Never add yourself as a coauthor of a commit.
- Never commit directly to `master` or `develop`. Branch first:
  - `feature/<name>` from `develop`
  - `hotfix/<name>` from `master`
  - `release/<x.y.z>` from `develop`
- Make small, focused commits with clear messages (imperative mood, subject ≤ 72 chars).
- Push with `git push -u origin <branch>`, then open a PR into `develop` (hotfixes into `master`).
- Do not commit secrets (`.env`, credentials, `.claude/settings.local.json`).
- Do not force-push shared branches.
- Full strategy: see `CONTRIBUTING.md`.
- Never perform terraform apply automatically, it is always performed manually
