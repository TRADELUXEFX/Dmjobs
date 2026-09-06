# DM Jobs Worker (native Android app)

A standalone native Android app replicating the Worker Portal's message-sending
flow: phone + job code login, contact locking, WhatsApp handoff, countdown
timer, and mark-as-messaged. Talks directly to your Supabase project via REST
— it does **not** load your website.

## What's included
- `LoginActivity` — phone + job code entry, job lookup, ban/status checks
- `PreviewActivity` — job details before accepting
- `SendActivity` — the core loop: lock next contact → open WhatsApp → countdown → mark sent → repeat
- `StatusActivity` — pending / blocked / banned / done screens with WhatsApp-to-admin buttons
- `Supabase.kt` — minimal REST client using the same anon key as your website
- `Session.kt` — in-memory session state for the current worker

## Get the APK (no Android Studio needed)

1. Push this whole folder to a new GitHub repo.
2. Go to the repo's **Actions** tab — it will build automatically on push (or click **Run workflow** manually).
3. Once the run finishes, open it and download the **dm-jobs-worker-debug-apk** artifact (a zip containing `app-debug.apk`).
4. Transfer that APK to your phone and install it (you'll need to allow "install from unknown sources" for your file manager/browser).

## Build it yourself in Android Studio instead
Just open this folder as a project — Android Studio will handle the Gradle wrapper automatically the first time you open it.

## Notes / things to know
- This is a **debug** build (unsigned, fine for personal testing/sideloading). For Play Store distribution you'd need a signed release build — say the word if you want that set up.
- Supabase URL/anon key/admin phone are hardcoded in `Supabase.kt`, matching your website. Update them there if you rotate keys.
- Validation (11-digit phone, job code lookup, ban check, daily limit, race-condition-safe contact locking) matches the web version exactly.
