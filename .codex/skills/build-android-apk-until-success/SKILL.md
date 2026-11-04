---
name: build-android-apk-until-success
description: Build this Android app through GitHub Actions, inspect failures, fix the workflow or project, rerun, and provide the APK only after a successful build.
---

# Build Android APK until successful

## Goal
Deliver a real, installable APK from this repository. Do not stop at uploading source code or starting a workflow.

## Procedure
1. Read the current Android Gradle project and workflow before changing anything.
2. Inspect the latest GitHub Actions run for the current commit.
3. If no run exists for the latest commit, trigger the workflow using an available GitHub Actions dispatch action; if dispatch is unavailable, make a small justified workflow commit that triggers `push`.
4. Wait for the run to reach a terminal state. Never report that the APK is ready while the run is queued or in progress.
5. If the run fails:
   - Inspect failed job steps and full logs.
   - Identify the first actionable root cause.
   - Fix the relevant workflow/project file in a new commit.
   - Verify the changed file and commit.
   - Start or allow a new run, then repeat from step 3.
6. Continue until the workflow succeeds, unless blocked by missing permissions, unavailable GitHub Actions, exhausted execution/time limits, or a failure that requires user input. If blocked, explain the exact blocker and do not claim completion.
7. After success, list artifacts for that exact successful run. Confirm the artifact contains the expected APK (`app-debug.apk` or the configured APK path).
8. Provide the direct GitHub Actions artifact/run link and, if the artifact download tool returns a usable file reference, provide that download too.
9. State clearly whether this is a debug APK or a signed release APK. Do not describe a debug APK as Play Store-ready.

## Reliability rules
- Poll the run status by making fresh status requests; do not assume elapsed time means completion.
- Use the run ID associated with the latest commit, not an older failed run.
- Never fabricate artifact IDs, URLs, or successful build status.
- Preserve existing app functionality while fixing build issues.
- Make changes sequentially; do not update the same file concurrently.
- Keep the user informed with concise Vietnamese updates, but do not send repetitive progress-only messages.
## Android SDK setup note
On GitHub-hosted Ubuntu runners, avoid asking `android-actions/setup-android` to install the obsolete `tools` package. Use the setup action with `packages: ''`, then install only current packages explicitly through `"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"`, for example `platform-tools`, `platforms;android-35`, and `build-tools;35.0.0`. The `sdkmanager` binary may not be on PATH unless the setup action has configured it; use the explicit SDK path.
