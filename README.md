# QuizForge AI

AI-powered quiz generator built with Java 17 + Spring Boot, backed by the Google Gemini API.

## Configuration

QuizForge AI is configured entirely through environment variables - no secrets are stored in
code or committed to the repository.

| Variable          | Required | Description                                                                                     |
|-------------------|----------|---------------------------------------------------------------------------------------------------|
| `GEMINI_API_KEY`  | No       | Your Gemini API key. If unset, the app runs in local/offline mode and generates quizzes using a notes-only fallback generator instead of calling Gemini. `AI_API_KEY` is accepted as a legacy alias. |
| `GEMINI_MODEL`    | No       | The Gemini model to call (e.g. `gemini-2.5-flash`). Defaults to a currently supported model if unset. Change this if Google retires the default model and the app starts returning "model not found" (HTTP 404) errors - no code changes or redeploy of application logic are needed, just update this variable. |

### Local development / running without a Gemini key

If `GEMINI_API_KEY` is not set, quiz generation falls back to a local generator that builds
questions directly from facts extracted out of the notes you provide. It never invents
generic filler questions to hit a requested question count - if your notes don't contain
enough distinct material, you'll get fewer (but genuinely accurate) questions instead.

### Render deployment

Set `GEMINI_API_KEY` (and optionally `GEMINI_MODEL`) as environment variables in your Render
service settings. Nothing else needs to change.

## Running locally

```bash
export GEMINI_API_KEY=your-key-here
mvn spring-boot:run
```

Then open `http://localhost:8080`.
