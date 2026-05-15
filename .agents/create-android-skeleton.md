# Task Brief: Create Minimal Android Skeleton

## Objective

Create the smallest useful Android project skeleton that can be built reproducibly and validated by GitHub Actions.

This task moves the repository from documentation-only bootstrap toward a minimal buildable Android project.

## Required Reading

Before making changes, read:

1. README.md
2. docs/project-state.md
3. docs/roadmap.md
4. docs/architecture.md
5. AGENTS.md
6. .agents/README.md
7. .agents/bootstrap-phase-0.md
8. this file

## Scope

Create a minimal Android application project suitable for future server-oriented development.

The goal is not to implement LLM inference, OpenAI-compatible endpoints, Garden integration, or server behavior yet.

## Allowed Work

- Add minimal Gradle Android project files.
- Add minimal Android app module.
- Add a minimal MainActivity only if required for a buildable APK.
- Add AndroidManifest.xml.
- Configure package/application id.
- Configure compile SDK, min SDK, and target SDK using stable public Android Gradle Plugin conventions.
- Add or update GitHub Actions workflow to build the APK.
- Update docs/project-state.md with the new build status.

## Not Allowed

- Do not integrate Google Garden.
- Do not import code from Garden or other open source projects.
- Do not implement an HTTP server yet.
- Do not implement OpenAI-compatible endpoints yet.
- Do not implement LLM runtime loading.
- Do not add a chat UI beyond what is strictly needed for Android project validity.
- Do not add broad abstractions for future runtime providers.
- Do not perform large refactors.

## Expected Repository Shape

The exact structure may vary if justified, but prefer a standard minimal Android layout similar to:

```text
settings.gradle.kts
build.gradle.kts
app/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/java/.../MainActivity.kt
.github/workflows/android-ci.yml
```

Use Kotlin or Java consistently. Prefer the simplest maintainable option.

## CI Expectations

Create or update a GitHub Actions workflow that:

1. checks out the repository
2. sets up JDK
3. sets up Gradle if appropriate
4. runs a real Gradle build command
5. uploads APK artifact if the APK is generated

Do not claim CI is working unless it is configured and the relevant local command was executed or the workflow result is visible.

## Validation

Run the most relevant local command available in the execution environment, for example:

```text
./gradlew assembleDebug
```

If local execution is impossible, explain why and ensure the GitHub Actions workflow is syntactically reasonable.

## Documentation Update

Update docs/project-state.md after the task.

State clearly whether:

- Android project skeleton exists
- local build was executed
- GitHub Actions build is configured
- APK artifact generation is configured
- any blocker remains

Do not mark Phase 2 as complete unless the APK build and artifact have actually succeeded.

## End Report Template

```text
Task result: OK or KO

Summary:
- ...

Files changed:
- ...

Validation:
- Command: ...
- Result: ...

Build/CI status:
- ...

Open issues:
- ...

Next recommended step:
- ...
```
