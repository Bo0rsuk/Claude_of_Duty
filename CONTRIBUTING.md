# Contributing

## Branching strategy (GitFlow-lite)

| Branch | Purpose | Merges into |
|---|---|---|
| `master` | Production. Always releasable; tagged releases only. | — |
| `develop` | Integration branch. Latest completed work for the next release. | `master` (via `release/*`) |
| `feature/<short-name>` | New features and non-urgent fixes. Branch from `develop`. | `develop` |
| `release/<version>` | Release stabilization. Branch from `develop`. | `master` and back into `develop` |
| `hotfix/<short-name>` | Urgent production fixes. Branch from `master`. | `master` and `develop` |

### Rules
- Never commit directly to `master` or `develop`. Use pull requests.
- Require at least one approving review and passing checks before merging.
- Use squash merge for feature branches to keep history readable.
- Tag every production release on `master` (e.g. `v1.0.0`).
- Delete branches after merging.

### Workflow
1. `git switch develop && git pull`
2. `git switch -c feature/my-thing`
3. Commit small, focused changes; push with `git push -u origin feature/my-thing`
4. Open a PR into `develop`.
5. Releases: cut `release/x.y.z` from `develop`, fix bugs on it, merge to `master` and tag, then merge back to `develop`.
6. Hotfixes: branch from `master`, PR into `master`, then back-merge into `develop`.
