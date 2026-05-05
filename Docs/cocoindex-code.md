# CocoIndex Code (`ccc`) in this repo

The semantic index is configured for the local-first parity workflow:

- `CodexMobile/**/*.swift`
- `shared/**/*.swift`
- `android/**/*.kt`, `android/**/*.kts`, Android manifest and string XML
- `phodex-bridge/src/**/*.js`, `phodex-bridge/test/**/*.js`, `phodex-bridge/bin/**/*.js`

Generated outputs, dependency folders, and build directories stay excluded.

## Commands

Run from the repo root:

```bash
ccc index
ccc status
ccc doctor
```

After changing `.cocoindex_code/settings.yml`, run:

```bash
ccc index
```

If the embedding model changes or the database looks stale, rebuild the local DB:

```bash
ccc reset -f
ccc index
```

## Useful Searches

Path filters are expected to work on this Windows setup:

```bash
ccc search --path "android/**" "active thread persisted reconnect running turn"
ccc search --path "CodexMobile/**" "active turn id protected running fallback"
ccc search --path "phodex-bridge/**" "thread read include turns running recovery"
ccc search --path "shared/**" "thread read timeline decoder completed item"
```

Language filters are useful for side-by-side parity checks:

```bash
ccc search --lang swift "workspace checkpoints revert state"
ccc search --lang kotlin "workspace checkpoints revert state"
ccc search --lang javascript "workspace checkpoints revert state"
```

Use `--limit` and `--offset` to page when the first results are relevant:

```bash
ccc search --path "android/**" --limit 5 --offset 5 "timeline streaming item scoped assistant rows"
```
