# SIM IVR

A native Android app (Kotlin, Jetpack Compose, minSdk 26, targetSdk 34) that turns a phone's own
SIM into an incoming + outgoing IVR system, with CRM sync. It works by becoming the phone's
**default Dialer** so it can control real calls, and by playing prompts through the speaker while
listening to the microphone for DTMF tones — the "acoustic IVR" approach described below.

## Why it works this way (read this first)

Android gives **no API** for a third-party app to inject audio into a phone call or read the
caller's DTMF tones from the call stream itself. There is no hidden trick around this — it is an
intentional telephony/privacy restriction. SIM IVR works around it the only way a normal app can:

1. It becomes the **default Dialer** (`RoleManager.ROLE_DIALER`) with an `InCallService` +
   `CallScreeningService`, so it is allowed to auto-answer, control, and route real calls.
2. Once a call is active, it switches the call to **speakerphone**
   (`InCallService.setAudioRoute(ROUTE_SPEAKER)`) and plays prompts through the speaker at (near)
   max call volume.
3. It captures the **microphone** during the call (`MediaRecorder.AudioSource.VOICE_COMMUNICATION`
   — the only source available to third-party apps) and runs a **Goertzel DTMF detector** over the
   audio to recognize keypresses the far end plays back, exactly as if a human were listening on
   speakerphone.
4. For outgoing calls it uses the same technique after `Call.STATE_ACTIVE`, and can additionally
   send DTMF to the far end with `Call.playDtmfTone` / `stopDtmfTone`.

**This means real-world reliability depends heavily on the phone model.** Some OEMs mute, denoise,
or otherwise process the microphone during calls in ways that can block capture entirely. Always
run the in-app **Call Audio Test** screen on your target device before relying on this, and read
"Known device limitations" below.

## Project layout

```
sim-ivr/
  app/                  the Android app
    src/main/java/com/simivr/app/
      telecom/          IvrInCallService, IvrCallScreeningService, CallController, CallFlowActionHandler
      audio/            GoertzelDtmf, DtmfStreamDetector, TtsEngine, Player, MicCapture
      flow/             FlowEngine (state machine), flow.model (Play/Menu/Collect/Transfer/Voicemail/Webhook/Hangup), FlowRepository
      campaign/         CampaignRunner, CampaignWorker (WorkManager), ContactRepo (CSV import), CampaignEventReporter
      data/             Room entities/DAOs, SettingsRepository (DataStore), CallPolicyRepository (blocklist/DND)
      sync/             CrmApi (Retrofit), OutboxSyncWorker (retry/backoff), CrmEventPublisher
      di/               Hilt modules
      ui/               Compose screens: Dashboard, Flows (+editor), Campaigns, Call log, Settings, Audio test, Onboarding
    src/test/           GoertzelDtmfTest, FlowEngineTest (pure JVM, no Android dependency)
    src/main/assets/flows/   sample_incoming_flow.json, sample_outbound_flow.json
  crm-stub/             reference Node/Express CRM receiver (see below)
  crm-backend/          production CRM backend: Express + Postgres/Prisma + dashboard (see below)
  README.md             this file
```

## Build status in this environment (important)

I built the complete project here but **could not run `./gradlew assembleDebug` to completion in
this sandbox**, for a structural reason, not a code problem:

- No Android SDK is installed, and this environment's network policy blocks `dl.google.com`.
- Both ways to install the SDK go through that host: `sdkmanager` downloads platforms/build-tools
  directly from `dl.google.com`, and Ubuntu's `google-android-*-installer` packages (I checked —
  they're only tiny wrapper `.deb`s) also fetch from `dl.google.com` at install time via a
  Debian Makefile.
- Worse, **Google's Maven repository itself resolves through the same host** — `maven.google.com`
  returns an HTTP 301 redirect to `dl.google.com` for every artifact. I confirmed this directly.
  That means the Android Gradle Plugin and every `androidx.*` / Compose / Room / Hilt-Android
  dependency are unreachable too, not just SDK platform files. Running `./gradlew help` fails
  immediately with "could not resolve plugin artifact 'com.android.tools.build:gradle:8.5.2'"
  before it even gets to the SDK.
- `repo.maven.apache.org` (Maven Central), `services.gradle.org` (Gradle distributions), and
  `plugins.gradle.org` **are** reachable, so anything that doesn't need Google's Maven works fine.

**To fix this**: open this environment's settings (cloud environment menu → Edit → Network access)
and either raise the network access level or explicitly allow `dl.google.com` (and ideally
`maven.google.com`, though it just redirects to the same host). Once that's done:

```bash
cd sim-ivr
# one-time SDK setup if Android Studio / an SDK isn't already present:
sdkmanager --sdk_root=$ANDROID_SDK_ROOT "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties

./gradlew assembleDebug
# APK lands at: app/build/outputs/apk/debug/app-debug.apk
```

### What I verified instead

Since the Android toolchain itself was unreachable, I could not compile the Android-specific 90%
of the code (anything touching `android.*`, Compose, Room, Hilt, WorkManager, Retrofit). What I
could do, and did, was pull the platform-independent core logic — `GoertzelDtmf`,
`DtmfStreamDetector`, the `FlowEngine` state machine, and the flow data model — into a standalone
Kotlin/JVM Gradle project (using only Maven Central, which is reachable) and ran the real unit
tests from `app/src/test/java` against them there. All 15 tests passed:

- `GoertzelDtmfTest` (7 tests): correctly detects all 16 DTMF digits from synthesized tones,
  rejects silence, rejects pure noise, still detects a digit under moderate background noise,
  rejects an off-grid single tone, confirms the sensitivity→threshold mapping, and confirms the
  stream detector's debounce (requires 2 consecutive windows to confirm a digit, then requires the
  tone to drop out before it will report the same digit again — this is what prevents one held
  keypress from being read as a rapid burst of repeats).
- `FlowEngineTest` (8 tests): happy-path traversal with `{name}` TTS variable substitution, menu
  routing by digit, timeout fallback, invalid-digit retry counting, giving up after max retries,
  Collect-digits storing a flow variable used by a later prompt, a malformed-flow infinite loop
  being cut off by `maxSteps`, and a missing node id being reported as a flow error.

Every other file (Room DAOs/entities, the InCallService/CallScreeningService, WorkManager workers,
Compose screens, Hilt modules, Retrofit/OkHttp sync) was written carefully against the real Android
APIs and cross-checked by hand for consistent field names, imports, and constructor signatures, but
**has not been compiled**. Treat it as a complete, ready-to-build implementation rather than a
verified-green build — the first `./gradlew assembleDebug` once SDK access is available is likely
to surface a handful of ordinary compile errors (typos, an import, a signature mismatch) that are
normal for ~5,000 lines of unbuilt Kotlin, and should be quick to fix.

## Setup

1. Allow network access to `dl.google.com` for this environment (see above), or build on a machine
   with Android Studio / the Android SDK already installed.
2. `sdk.dir=/path/to/Android/sdk` in `sim-ivr/local.properties` (or `ANDROID_SDK_ROOT` env var).
3. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
4. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Since this app requests the
   default-dialer role and call-log access, **Google Play restricts this category of app** — this
   is meant for sideloading (see "Distribution note" below).

## Setting SIM IVR as the default dialer

1. Launch the app — the Onboarding screen lists every required permission plus "Set as default
   Phone app" and battery optimization exemption.
2. Grant `READ_PHONE_STATE`, `CALL_PHONE`, `ANSWER_PHONE_CALLS`, `READ_CALL_LOG`, `RECORD_AUDIO`,
   `POST_NOTIFICATIONS`.
3. Tap "Set as default Phone app" — this calls `RoleManager.createRequestRoleIntent(ROLE_DIALER)`
   and shows the system's own confirmation dialog. You can also do this manually any time under
   **Settings → Apps → Default apps → Phone app**.
4. Tap "Disable battery optimization" so Android doesn't kill the app's foreground services
   between calls/campaign runs.
5. Once default, SIM IVR's incoming-call UI (from `IvrInCallService`) replaces the stock dialer's
   for ringing/active calls.

## Testing on a real SIM

You need a **second phone** (or a colleague) to call in — the emulator's built-in phone/SIM does
not exercise real telecom audio or DTMF.

1. **Incoming**: from Settings, pick a sample flow for SIM 1's incoming line (seeded automatically
   as `sample-incoming` — "Press 1 Sales, 2 Support, 3 Callback request, 9 Repeat"), set rings
   before answer, then call the SIM 1 number from another phone. It should auto-answer after N
   rings, greet you over speaker, and respond to your keypresses.
2. **Audio Test screen** (Settings → "Open Call Audio Test"): run this on the target device before
   trusting the above — it plays a tone and listens for DTMF using the same code path, without
   needing a second phone, so you can sanity-check the mic isn't being blocked.
3. **Outgoing / campaigns**: create a campaign, import a CSV (`name,phone,...custom columns`),
   pick a flow and SIM, and press Start. Call the destination phone yourself to verify the far end
   actually hears the greeting and that your keypresses register (watch the Call log screen for the
   digits captured).
4. Test on more than one device/OEM if you can — see limitations below.

## Known device limitations

- **Mic-during-call capture is not guaranteed.** Several OEMs (some Samsung, Xiaomi/MIUI, and
  others) apply aggressive noise suppression or partially mute the mic during a call for their own
  echo-cancellation, which can prevent DTMF detection from working at all on those devices. The
  in-app Audio Test screen surfaces this — if it can't detect its own test tone/DTMF, real calls
  won't work either.
- **No true carrier call transfer.** The public Telecom API has no SIP REFER / IMS ECT equivalent
  for third-party apps. The `Transfer` flow node does a best-effort `Call.conference()` merge after
  dialing the destination number on the same SIM — this depends on carrier/OEM conference-calling
  support and will not work on every network.
- **Speaker volume, not a clean line signal.** Because prompts are played out loud and picked back
  up acoustically, ambient noise, speaker distortion at high volume, and the far end's own
  environment all affect DTMF detection accuracy — this is inherent to the acoustic-IVR approach,
  not a bug.
- **Ring count is approximate.** `InCallService` has no direct "this is the 3rd ring" callback, so
  "answer after N rings" is implemented as a fixed delay (~4s/ring) from when the call starts
  ringing, which can differ slightly from actual carrier ring cadence.
- **Dual-SIM support depends on the device correctly exposing two `PhoneAccountHandle`s** via
  `TelecomManager` — true on essentially all dual-SIM phones, but some odd OEM configurations may
  only expose one until a SIM is used once from the stock dialer.

## Compliance (India / TRAI-TCCCP)

Bulk/promotional outbound calling in India is regulated (TRAI's TCCCP framework): telemarketers
must be registered, use 140-series headers, scrub the National DND registry, and honor opt-outs.
SIM IVR:

- Ships a sample outbound flow with **"Press 9 to opt out"** as a real default — pressing 9 on any
  campaign call adds that number to the app's own DND list (`CampaignEventReporter`) and the
  contact is marked `OPTED_OUT`; future campaigns skip DND-listed numbers automatically
  (`CallPolicyRepository.isOnDndList`).
- Shows a Settings toggle requiring you to confirm registered-telemarketer/compliant use before
  relying on bulk calling — **this app does not scrub the National DND registry for you**; that
  integration is CRM/carrier-side and out of scope here.
- Keeps call/voicemail recording **off by default**; when turned on, a consent line is always
  played before recording starts (`CallFlowActionHandler.recordVoicemail`).

## CRM API contract

Configure the CRM base URL + API key under **Settings → CRM integration**. The API key is sent as
both `Authorization: Bearer <key>` and `X-Api-Key: <key>` so it fits most CRMs' conventions.

**`POST {base}/api/ivr/events`** — sent for every completed call and every callback request
(digit 3 in the sample flow) or opt-out (digit 9), queued durably in a local Room outbox and
retried with exponential backoff by WorkManager whenever offline:

```json
{
  "event": "call_result",
  "direction": "incoming",
  "number": "+919000000001",
  "sim": 0,
  "flow": "sample-incoming",
  "digits": ["1"],
  "durationMs": 18234,
  "startedAt": 1732600000000,
  "endedAt": 1732600018234,
  "campaignId": null,
  "vars": { "name": "Asha" }
}
```

`event` is `"call_result"` or `"callback_request"`.

**`GET {base}/api/ivr/campaigns/{id}/contacts`** — pulled on demand to seed a campaign's contact
list from the CRM instead of (or in addition to) a CSV import:

```json
[
  { "name": "Asha Rao", "phone": "+919000000001", "vars": { "city": "Hyderabad" } }
]
```

### crm-stub — reference receiver

`./crm-stub` is a minimal Node/Express server implementing exactly this contract, so you can watch
events arrive before wiring up Chat247, TeleCRM, or anything else:

```bash
cd crm-stub
npm install
npm start          # listens on :8080
# optional: API_KEY=secret npm start
```

It logs every event to the console and appends it to `crm-stub/data/events.log`; `GET /api/ivr/events`
lets you inspect what's been received. `GET /api/ivr/campaigns/:id/contacts` serves
`data/campaigns/<id>.json` if present (a `sample-campaign.json` is included), otherwise a small
built-in sample list. Point the app's Settings → CRM base URL at
`http://<this-machine's-LAN-IP>:8080` (the Android device needs network access to that host —
`http://` requires `usesCleartextTraffic`/network security config allowances for a plain-HTTP local
server, or run it behind HTTPS for anything beyond local testing).

### crm-backend — the real backend

`./crm-backend` is a production-grade implementation of the same contract: TypeScript + Express +
PostgreSQL (Prisma), with campaign/contact management, automatic Lead creation on callback
requests, automatic DND opt-out handling (digit 9), idempotent event ingestion (safe against the
app's offline-outbox retries), a small admin dashboard, Docker deployment, and 29 passing
integration tests. Use `crm-stub` for a five-minute contract smoke test; use `crm-backend` for
anything you're actually going to run. See `crm-backend/README.md` for setup, the full API, and
Docker deployment instructions.

## Distribution note

Default-dialer role and call-log access place this app in a restricted category on the Google Play
Store, so it's intended for **sideloaded APK distribution** (direct install / internal MDM /
enterprise channel), not a public Play listing. A debug-signed keystore
(`sim-ivr-release.keystore`, password `simivr123`, alias `simivr`) is included so `assembleRelease`
works out of the box for personal/internal use — replace it with your own keystore before
distributing this beyond yourself.

## What's tested vs. what needs a physical phone

**Tested in this session:**
- `GoertzelDtmf` / `DtmfStreamDetector` — full digit set, noise rejection, debounce — via real
  JVM unit tests (`app/src/test/.../GoertzelDtmfTest.kt`), run standalone since the Android
  toolchain was unreachable.
- `FlowEngine` state machine — branching, timeouts, retries, variable interpolation, error/loop
  handling — via `app/src/test/.../FlowEngineTest.kt`, same standalone run.
- CSV parsing logic (`ContactRepo`) and the CRM outbox/retry design were reviewed by hand but have
  no automated test in this pass (would need Robolectric/instrumentation for Context-dependent
  parts).

**Can only be verified on a physical phone with a real SIM:**
- Actually becoming the default dialer and receiving real `InCallService` callbacks.
- Whether a given device's microphone stays usable during an active call (this is the one item
  most likely to vary — test with the in-app Audio Test screen first).
- Real DTMF detection accuracy over an actual speaker→air→mic acoustic path, and behavior on
  dual-SIM hardware.
- Whether `Call.conference()` succeeds for the Transfer node on your carrier.
- End-to-end CRM delivery, including outbox retry after toggling airplane mode mid-call.
