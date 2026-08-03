# Drive-Backed Storage

## What this is

There is no backend server or database for this app. Each user's tasks live
in **their own Google Drive**, in a special hidden area called the **App
Data folder** (`appDataFolder`). Files there:

- are created by our app under the OAuth scope `https://www.googleapis.com/auth/drive.appdata`
- do **not** appear in the user's normal "My Drive" view or count against
  their visible file list
- are only readable/writable by the app that created them (using the same
  OAuth client)
- are deleted if the user uninstalls the app via their
  [Google Account's connected apps page](https://myaccount.google.com/permissions)

We store a single file there, `tasks.json` (schema in `docs/data-model.md`).

## Why this instead of Firebase/Firestore

No backend to run, host, or pay for — the user's own Drive storage is the
database. Trade-off: no real-time listeners (see `docs/sync.md`), and all
reads/writes are plain REST calls the client makes directly to Google's
Drive API.

## One-time manual setup (cannot be automated by Claude)

These steps require your own Google account and must be done by you in
[Google Cloud Console](https://console.cloud.google.com/):

1. Create (or reuse) a Google Cloud project for this app.
2. **APIs & Services → Library** → enable the **Google Drive API**.
3. **APIs & Services → OAuth consent screen** → configure it (app name,
   support email, scopes — add `.../auth/drive.appdata`). While in testing
   mode, add your own Google account as a test user.
4. **APIs & Services → Credentials** → create OAuth client IDs:
   - **Android**: needs the app's package name (`com.reminder.app` —
     placeholder, change if you pick a different applicationId) and the
     SHA-1 of your signing certificate (`./gradlew signingReport` in the
     Android project once it exists, or `keytool -list -v -keystore
     ~/.android/debug.keystore` for the debug cert).
   - **Web application**: needs the authorized JavaScript origin(s) and
     redirect URI(s) for the Next.js app (e.g. `http://localhost:3000` for
     local dev).
5. Paste the resulting client IDs into:
   - Android: `app/google-services-config` — *(placeholder path; created
     when the Android project is scaffolded)*
   - Web: `.env.local` as `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`
     *(placeholder; created when the Next.js app is scaffolded)*

Nothing in this app will work end-to-end until these are in place — the
scaffolded code will build, but sign-in will fail without a real OAuth
client ID.

## API calls used

All calls go to `https://www.googleapis.com/drive/v3/files` (metadata) and
`https://www.googleapis.com/upload/drive/v3/files` (content), scoped to
`spaces=appDataFolder`.

- **Find the file**: `GET files?spaces=appDataFolder&q=name='tasks.json'`
  → returns the file `id` if it exists, empty list on first run.
- **Read**: `GET files/{fileId}?alt=media`
- **Create** (first run only): `POST upload/drive/v3/files?uploadType=multipart`
  with `parents: ["appDataFolder"]`, `name: "tasks.json"`.
- **Update**: `PATCH upload/drive/v3/files/{fileId}?uploadType=media`,
  sending the full new JSON body. Use `ifMatch`/version checks per
  `docs/sync.md` to avoid clobbering concurrent writes.

## Per-client implementation

- **Android**: `TaskRepository` (see `docs/sync.md` and Android module docs
  once written) obtains an access token via Credential Manager / Google
  Identity Services Authorization API requesting `drive.appdata`, then calls
  the endpoints above with OkHttp/Retrofit.
- **Web**: the Drive access token is kept **server-side** (Next.js API
  route + session), never sent to the browser. The browser calls our own
  `/api/tasks` route, which calls Drive on the user's behalf. See
  `docs/web-limitations.md`.

## Manual test

1. Complete the Google Cloud Console setup above.
2. Sign in on a client.
3. Create a task; confirm `tasks.json` round-trips by checking
   `GET files?spaces=appDataFolder&q=name='tasks.json'` returns a file, and
   its content matches what was written (can be inspected via the
   [Drive API Explorer](https://developers.google.com/drive/api/reference/rest/v3/files/list)
   using the same account's OAuth token).
4. Sign in on the second client with the same Google account; confirm the
   task appears after a refresh (not instantly — see `docs/sync.md`).
