# BRO – handoff notes for the next Claude session

Owner: rachamadhav28 (B.Tech student, Telangana, iQOO phone, Android). Repo: rachamadhav28-spec/Bro-direct.
BRO = Android voice/chat assistant (Kotlin 2.0.21, Jetpack Compose, minSdk 26, targetSdk 35, AGP 8.7.3, Gradle 8.9).
The user does not build locally: every push is built by GitHub Actions (`.github/workflows/build.yml`, Java 17) into the
artifact `bro-debug-apk`. The APK is signed with the committed `app/debug.keystore` so updates install over old builds.
Build number = `github.run_number` (shown as "build N" next to the BRO title).

## How to work on it
- Edit -> commit -> push -> poll `gh run list`; on failure read `gh api repos/.../check-runs/<id>/annotations`
  (raw log download is blocked by the proxy). The workflow prints `::error::` annotations on failure.
- Gotchas: no two Kotlin files whose names differ only by case; HttpURLConnection cannot PATCH or send DELETE bodies;
  no suspend calls inside `joinToString` lambdas (use `buildString`); another Claude session may also be pushing - `git pull --rebase` first.
- Commit trailer format is given by the harness reminder (Co-Authored-By + Claude-Session).

## Code map (app/src/main/java/com/bro/assistant)
- ChatViewModel.kt: send() -> process-wide scope + BroTaskService (foreground) + notification when done; handle() order:
  SystemControls (volume/mute/game mode) -> GitHubCommands -> ActionPlanner -> TaskManager.
- task/ActionPlanner.kt: ExtraCommands -> "what can you do in X" playbook -> "agent/inside/operate <goal>" -> LocalCommandParser
  (offline, Telugu/Tenglish via TeluguNormalizer) -> code route (looksLikeCode) -> Gemini JSON planner (ai/IntentParser.kt).
- task/TaskManager.kt + BroAction.kt (ActionType incl. AGENT_TASK) + ActionValidator.kt.
- actions/AppAgent.kt: screen-reading agent loop (Accessibility tree -> Gemini -> click/type/scroll/back/home/open_app/open_url/
  long_press/notifications/recents/done/fail/ask). Safety: confirm for pay/buy/delete/transfer; confirm Send/Post if prefs.confirmBeforeSend;
  never types passwords/OTP/PIN; loop + 30 step + 6 min limits.
- actions/AppKnowledge.kt: phone-wide rules + ~25 app playbooks + installed-app list injected into every agent step.
- actions/UiFinder.kt, BroAccessibilityService.kt (tap/swipe gestures, quick settings), DeviceControl.kt (toggles), MessagingActions.kt (WhatsApp).
- ai/GeminiClient.kt: REST generateContent / streaming SSE, retries 429/5xx, on 404 auto-lists models and switches to a working one.
- GitHubCommands.kt + ai/GitHubClient.kt: repos, files, issues, PRs, merge, push, create-app (AppBuilder/AppTemplate), build status, fix build, download APK.
- voice/SpeechInput.kt (STT), SpeechOutput.kt (TTS, prefers male voice, pitch 0.7-0.85).
- ui/: ChatScreen.kt, Futuristic.kt (animated HUD background), DinoGame.kt (self-playing dino while thinking, 1/4 size), BroOrb.kt (only while mic listening; colour changes per build), CodeBlocks.kt, SettingsScreen.kt, TaskProgressCard.kt.
- Crash reporter: MainActivity stores the last uncaught exception and shows it in chat on next start.

## NEXT FEATURE REQUEST (user, verbatim intent): "BRO has to think on its own, without any API"
Goal: BRO works with NO cloud AI key (no Gemini, no quota limits). Plan:
1. Introduce `interface AiBackend { suspend fun generate(system, user, json, timeoutMs): String }` and make GeminiClient one
   implementation; route IntentParser, AppAgent, GitHubCommands edits and code answers through the chosen backend.
2. Add an on-device backend using Google AI Edge / MediaPipe `tasks-genai` `LlmInference` (or llama.cpp via JNI) running a small
   instruction model (e.g. Gemma 3 1B / Qwen2.5 1.5B in `.task`/`.litertlm` format, int4, ~0.5-1.5 GB). The user downloads it once
   in Settings (progress bar, resumable, stored in app files dir; some models need a Hugging Face token because they are gated -
   prefer a non-gated model, verify the URL works before shipping). Settings: "AI engine: Gemini (online) / On-device (offline) / Auto".
3. Small models are weak: keep prompts short, use fixed JSON schemas, rely on LocalCommandParser + AppKnowledge playbooks for most
   work, give the agent a reduced element list (<=40) and one instruction per step, validate JSON and retry once.
   Offline fallback order: LocalCommandParser -> on-device model -> (if key set and online) Gemini.
4. Be honest in the UI about quality/speed (a 1B model on a phone is slow and less accurate than Gemini).
5. Still verify on device; nothing in this repo has been tested on a physical phone by Claude.

## Other pending items
- Verify on the real phone: mic flow no longer closes the app (crash text shows in chat if it still does), agent on real apps, male voice.
- Unimplemented toggles: vibration, split-screen, font size, iQOO Share, Monster Mode, scan code, Office Kit, calculator, record audio/screen,
  screen mirroring, mini screen, switch SIM, global search, lock, wallet, Live Caption, Quick Share, Song Search, Select to Speak,
  Sound Notifications, Live Transcribe, Scan and Pay, storage, ChatGPT, speed up, super screenshot, VoWiFi, power off (could now go through AppAgent + Settings playbook).
- AttendTrack Telangana app (Compose + Room, Telangana holidays, never invent dates, 75% prediction, CSV export): not built yet.
- Restyle Settings/History screens to match the futuristic look; futuristic orb for the mic.
- BRO cannot yet delete files or close issues on GitHub.
- Gemini free tier rate limits make long agent runs slow; the offline engine above is the proper fix.
