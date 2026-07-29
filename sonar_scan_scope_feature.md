# SonarQube scan scope — `sonar.inclusions` / `sonar.exclusions` in the shared library

New feature in `sonarqube.groovy` (shared library): the scan scope — **single file, multiple
files, single folder, multiple folders, or the full project** — can now be controlled through
parameters. Nothing needs to be hardcoded in a Jenkinsfile; the pipeline just passes job
parameters through to the shared library.

A ready-to-use test pipeline is in [`sonar_scope_test.jenkinsfile`](sonar_scope_test.jenkinsfile)
(default repo: `idirect_trading_watchlist`).

---

## 1. New parameters

| Parameter | `scan()` (CLI) | `scanDotNet()` (MSBuild) | Purpose |
| --- | --- | --- | --- |
| `inclusions` | ✅ | ✅ (`begin` only) | **Preferred.** Allowlist of path patterns; only matching files are analysed. |
| `exclusions` | ✅ | ✅ (`begin` only) | **Discouraged** (see §3). Denylist of path patterns dropped from the scope. |
| `sources` | ✅ | ❌ rejected | Moves the scan root (`sonar.sources`). Plain paths only, **no wildcards**. |
| `projectKeySuffix` | ✅ | ✅ | Routes a narrowed scan to its own project key. Also on `downloadReport()`. |

All of them accept either a comma-separated `String` (`'src/cmd/**,src/api/**'`) or a Groovy
`List` (`['src/cmd/**', 'src/api/**']`). Entries are trimmed (SonarQube treats a leading space
as part of the pattern — the classic reason patterns "don't work") and backslashes are
converted to `/`. Blank/empty values are treated as "not set", so wiring a blank Jenkins
string parameter straight through is safe: no empty property is ever sent to the scanner.

**Backward compatible:** calls that don't pass any of these behave exactly as before
(full-project scan, unchanged project key).

## 2. Scanning granularity — how to express each case

Everything is done with `inclusions` (positive selection covers every case):

```groovy
// Full project (unchanged - this stays the quality-gate scan)
sonarqube.scan(gitRepoName: 'idirect_trading_watchlist', branch: 'main')

// Single folder
sonarqube.scan(gitRepoName: 'idirect_trading_watchlist', branch: 'main',
    inclusions: 'src/cmd/**', projectKeySuffix: 'cmd')

// Multiple folders
sonarqube.scan(gitRepoName: 'idirect_trading_watchlist', branch: 'main',
    inclusions: ['src/cmd/**', 'src/api/**'], projectKeySuffix: 'cmd-api')

// Single file
sonarqube.scan(gitRepoName: 'idirect_trading_watchlist', branch: 'main',
    inclusions: 'src/cmd/main.go', projectKeySuffix: 'precheck')

// Multiple files
sonarqube.scan(gitRepoName: 'idirect_trading_watchlist', branch: 'main',
    inclusions: ['src/cmd/main.go', 'src/api/handler.go'], projectKeySuffix: 'precheck')

// The one good use of exclusions: code that should never be analysed
sonarqube.scan(gitRepoName: 'idirect_trading_watchlist', branch: 'main',
    exclusions: ['**/generated/**', '**/node_modules/**', '**/*.min.js'])
```

### Pattern syntax

Patterns are matched against paths **relative to the repo root** (the scanner's base
directory), using SonarQube's own wildcard syntax — not full glob:

| Wildcard | Meaning |
| --- | --- |
| `**` | any number of directory levels (also matches zero) |
| `*` | any number of characters, but never `/` |
| `?` | exactly one character, never `/` |

So `src/cmd/**` = everything under `src/cmd`, `**/*.go` = every Go file anywhere, and a plain
path like `src/cmd/main.go` matches exactly that file. Multiple patterns are comma-separated.
Reference: [Narrowing the focus / analysis scope](https://docs.sonarsource.com/sonarqube-server/10.8/project-administration/analysis-scope)
and [path-matching patterns](https://docs.sonarsource.com/sonarqube-server/2026.1/project-administration/adjusting-analysis/setting-analysis-scope/excluding-files-based-on-patterns).

## 3. Why inclusions, not exclusions (best practice)

**Use `inclusions` to narrow a scan. Treat `exclusions` as a last resort.**

- An inclusion is an **explicit allowlist**: the job log states exactly what was analysed,
  and anyone reading the SonarQube dashboard can reason about the scope.
- An exclusion **silently hides code from analysis**. Nobody looking at a green dashboard can
  tell that entire folders were skipped, and a stale exclusion pattern keeps hiding code as
  the repo evolves. For a tool whose whole point is visibility of code quality and security
  issues, invisible blind spots are the worst possible failure mode.
- Both `sonar.inclusions` and `sonar.exclusions` are *filters*: per the official docs they
  "only ever reduce the number of files in the analyzable set, they never add to the set".
  If both are set, a file must match an inclusion AND not match an exclusion.
- SonarSource's own guidance is that inclusions/exclusions "should not be part of the initial
  analysis configuration" and should be set only to solve specific problems. The one
  legitimate standing use of `exclusions` is code that should never be analysed: generated
  code, vendored dependencies, minified assets.

## 4. ⚠️ The critical rule: always pair a narrowed scan with `projectKeySuffix`

A narrowed scan is **not a partial update** of the existing project — SonarQube treats every
file outside the scope as deleted:

- Their issues are auto-closed as `Fixed` (confirmed by
  [the issue lifecycle docs](https://docs.sonarsource.com/sonarqube-server/10.0/user-guide/issues)
  and [SonarSource staff](https://community.sonarsource.com/t/history-issues-auto-closed-if-set-the-inclusions/15775)).
- When the next full scan re-includes those files, the old issues resurface **counted as new
  code**, blowing up the new-code quality gate.
- Closed issues are purged after ~30 days, so manual triage (Accepted / False Positive) on
  out-of-scope files is permanently lost.
- LOC, coverage and ratings are recomputed from the narrowed set.

Therefore:

- **Full-project scans** → normal project key (`<repo>-main`). This stays the single source
  of truth for the quality gate.
- **Any scan with `inclusions`/`exclusions`/`sources`** → pass `projectKeySuffix` so it lands
  on a throwaway key like `idirect_trading_watchlist-main-precheck`.
- The shared library prints a `WARNING` at runtime if you narrow the scope without a suffix.
- Pass the same suffix to `downloadReport()` to fetch that scan's report.

(The clean solution for per-change feedback would be branch/PR analysis, but that requires
Developer Edition — our Community Build only supports `main`, which is why the library
hardcodes the branch.)

## 5. Non-.NET vs .NET — the differences that matter

### Non-.NET (`scan()` — sonar-scanner CLI, Linux nodes)

Straightforward: `-Dsonar.inclusions=...` / `-Dsonar.exclusions=...` are appended to the
`sonar-scanner` command line only when supplied. Command-line properties are never persisted
server-side, so they affect only that one analysis. Note that anything configured on the
server under *Project Settings → General Settings → Analysis Scope* still applies and
combines silently with these parameters.

### .NET (`scanDotNet()` — SonarScanner for .NET / MSBuild, Windows nodes)

Three researched caveats:

1. **`sonar.sources` is not supported** — the MSBuild integration discovers files from the
   build itself, and the scanner rejects `sonar.sources`/`sonar.tests`
   ([docs](https://docs.sonarsource.com/sonarqube-server/2026.2/analyzing-source-code/scanners/dotnet/using.md)).
   The shared library errors clearly if you pass `sources` to `scanDotNet()`. Folder/file
   granularity must be expressed with `inclusions`, e.g. `inclusions: 'src/Api/**'`.
2. **Properties go on the `begin` step only.** The `end` step accepts just the token; the
   library warns and ignores scope params passed to `end`.
3. **Filtering does not speed up the build.** Documented known issue: for C#/VB the scanner
   "can't filter the excluded files/folders from the analysis, which happens during the
   build" — the patterns only filter what is *uploaded* during `end`
   ([docs](https://docs.sonarsource.com/sonarqube-server/2026.2/analyzing-source-code/scanners/dotnet/configuring.md)).
   The Roslyn analyzers still run over everything. If you need build-time filtering, set
   `<SonarQubeExclude>true</SonarQubeExclude>` in the `.csproj` instead.

   Also note: the official .NET scanner docs discuss `sonar.exclusions` but never explicitly
   mention `sonar.inclusions`. It is implemented symmetrically here, but do one trial run on
   a throwaway `projectKeySuffix` before relying on it for .NET.

## 6. Testing with `sonar_scope_test.jenkinsfile`

1. Create a new Pipeline job in Jenkins and paste the contents of
   [`sonar_scope_test.jenkinsfile`](sonar_scope_test.jenkinsfile) into the *Pipeline script*
   box. Run it once so Jenkins registers the parameters, then use **Build with Parameters**.
2. Defaults: repo `idirect_trading_watchlist`, branch `main`, `PROJECT_KEY_SUFFIX=scope-test`
   (keep a suffix for every test run — see §4).
3. Test matrix:

| Test | `INCLUSIONS` | Expected result on SonarQube |
| --- | --- | --- |
| Full project | *(blank)* | all repo files under project `…-main-scope-test` |
| Single folder | `src/cmd/**` | only that folder's files appear under *Code* |
| Multi folder | `src/cmd/**,src/api/**` | only those folders |
| Single file | `src/cmd/main.go` | exactly one file |
| Multi file | `src/cmd/main.go,src/api/handler.go` | exactly those files |

   (Adjust the paths to real ones in `idirect_trading_watchlist`.)
4. After each run, open the project on `https://sonarqube.iseccorp.in` and check the file
   list under **Code** matches the inclusions, and LOC shrinks accordingly.
5. For a .NET repo: check `IS_DOTNET`, point `AGENT_LABEL` at a Windows agent that has
   MSBuild and the `sonar-scanner-dotnet` tool, and replace the placeholder
   `dotnet build …` line with the repo's real build command.
6. Cleanup: delete the throwaway `…-scope-test` projects from SonarQube when done
   (*Administration → Projects → Management*).

## 7. Quick reference — what to remember

- Prefer `inclusions`. Use `exclusions` only for generated/vendored code.
- Never narrow a scan on the real project key — always `projectKeySuffix`.
- Patterns are relative to repo root; `**` crosses folders, `*` doesn't.
- No leading spaces inside comma-separated patterns (the library trims them anyway).
- .NET: `inclusions`/`exclusions` on `begin`, no `sources`, no build-time speedup.
- Blank parameters = full project scan, byte-for-byte identical to the old behaviour.
