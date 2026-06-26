# TG Photo Backup

An Android app (Kotlin) that backs up every photo on your phone to Telegram,
using a **Telegram bot** + a **private channel** as the storage backend — like
a free, self-hosted Google Photos.

## How it works

```
App scans photos (MediaStore)
   │   keeps a local SQLite index of what's uploaded (dedup by SHA-256)
   ▼
Telegram bot → sendDocument → your private channel  (= the "database")
```

- Photos are sent as **documents**, so Telegram does **not** recompress them.
- A local Room database remembers which photo maps to which Telegram message,
  so re-running a backup only uploads new photos.
- Backups run in a **foreground service** via WorkManager (manual or every ~12h).

## Limits to know

| Thing | Limit |
|------|-------|
| File size per upload | **50 MB** (standard Bot API). Large videos/RAW will fail unless you run a self-hosted Bot API server (→ 2 GB). |
| Storage | Effectively unlimited (Telegram channels). |
| Security | The bot token is embedded in the APK. Fine for **personal** use; do **not** publish this APK. |

## One-time Telegram setup

1. In Telegram, message **@BotFather** → `/newbot` → copy the **bot token**.
2. Create a **private channel** (this is your photo store).
3. Add your bot to the channel as an **admin** (needs "Post messages").
4. Get the channel id (starts with `-100…`):
   - Easiest: forward any channel message to **@userinfobot**, or use
     `@getidsbot`. The channel id looks like `-1001234567890`.
5. Open the app → **Settings** → paste the token + channel id → **Test
   connection** → **Save**.
6. Grant photo permission → **Back up now**.

## Building the APK

You said Android Studio is ready, so:

1. **File → Open** → select this `TelegramPhotoBackup` folder.
2. Let Gradle sync (it downloads Gradle 8.9 + dependencies on first run).
   - If it complains the Gradle wrapper jar is missing, accept Android Studio's
     offer to generate it, or run `gradle wrapper` once in the folder.
3. Plug in your phone (USB debugging on) and press **Run** ▶, or build an APK:
   **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

> First device install: allow "Install from unknown sources" for whichever app
> transfers the APK.

## Project layout

```
app/src/main/java/com/example/tgphotobackup/
  TgPhotoApp.kt              Application + notification channel
  MainActivity.kt           Compose UI (home + settings)
  ui/MainViewModel.kt       State, settings, triggers backup
  data/                     Room index DB + DataStore settings
  telegram/TelegramClient.kt  Bot API (getMe, sendDocument, streaming upload)
  backup/
    MediaStoreScanner.kt    Lists all device photos
    BackupManager.kt        Dedup + upload loop
    BackupWorker.kt         Foreground WorkManager job + progress notification
    BackupScheduler.kt      Manual + periodic scheduling
```

## Ideas for later

- Back up videos too (the scanner only queries images right now).
- Periodically upload the SQLite index to the channel so it survives a wipe.
- Self-hosted Bot API server for >50 MB files.
- Encrypt files before upload (the channel admin/token holder can see them).
- Restore/download flow (read `file_id`s back from the index).
# TelegramPhotoBackup
