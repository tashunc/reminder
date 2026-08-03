# Data Model

Tasks are not stored in a database we run. They live in a single JSON file,
`tasks.json`, inside each signed-in user's own Google Drive **App Data
folder** (a hidden, per-app storage area — see `docs/drive-storage.md` for
how that folder works and how clients read/write it).

## File: `tasks.json`

```json
{
  "version": 1,
  "tasks": [
    {
      "id": "3f2e1a90-1234-4d21-9abc-8b6a1c2d3e4f",
      "title": "Pay rent",
      "description": "Bank transfer to landlord",
      "day": "2026-08-05",
      "time": "09:00",
      "recurrence": null,
      "createdAt": "2026-08-03T10:15:00Z",
      "updatedAt": "2026-08-03T10:15:00Z",
      "completed": false,
      "source": "android"
    }
  ]
}
```

### Top-level fields

| Field     | Type   | Notes                                                                 |
|-----------|--------|------------------------------------------------------------------------|
| `version` | number | Schema version of this file. Bump when the task object shape changes, and write a migration in each client's repository layer. |
| `tasks`   | array  | Every task for this account, unsorted. Clients sort/group for display. |

### Task object fields

| Field         | Type              | Required | Notes |
|---------------|-------------------|----------|-------|
| `id`          | string (UUID v4)  | yes | Generated client-side at creation time. Never reused. |
| `title`       | string            | yes | Non-empty. |
| `description` | string \| null    | no  | Optional free text. |
| `day`         | string (`YYYY-MM-DD`) | yes | The assigned day, in the user's local calendar date — **not** a UTC-shifted value. |
| `time`        | string (`HH:mm`, 24h) \| null | no | If set, this is a **time-based reminder** (exact alarm on Android). If `null`, it's an **all-day reminder** (randomized prompts on Android). |
| `recurrence`  | string \| null    | no  | One of `null`, `"daily"`, `"weekly"`, `"monthly"`. No custom RRULEs in v1. |
| `createdAt`   | string (ISO 8601, UTC) | yes | Set once at creation. |
| `updatedAt`   | string (ISO 8601, UTC) | yes | Set on every write; used for conflict diagnostics (see `docs/sync.md`). |
| `completed`   | boolean           | yes | Defaults to `false`. |
| `source`      | string            | yes | Which client created the task: `"android"`, `"web"`, `"assistant"`, or `"bixby"`. Informational only — does not affect sync behavior. |

## Why one file instead of one-file-per-task

Personal task lists are small (tens to low hundreds of items), so a single
JSON file keeps reads/writes to one Drive API call each, and makes
optimistic-concurrency conflict handling straightforward (one file version to
check, see `docs/sync.md`). One-file-per-task would need folder listing plus
N file fetches on every load, with no real benefit at this scale.

## Schema evolution

When the task shape changes:
1. Bump `version`.
2. Add a migration step in each client's repository layer that upgrades an
   older file to the current shape on load (never destructively — write the
   migrated file back only after a successful read).
3. Update this doc and `docs/CHANGELOG.md` in the same change.
