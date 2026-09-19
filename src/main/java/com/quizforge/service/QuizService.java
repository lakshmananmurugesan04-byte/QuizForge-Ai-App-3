package com.quizforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizforge.dto.Question;
import com.quizforge.dto.QuizRequest;
import com.quizforge.dto.QuizResponse;
import com.quizforge.util.DuplicateDetector;
import com.quizforge.util.LanguageDetector;
import com.quizforge.util.LanguageDetector.DetectedLanguage;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class QuizService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_RETRY_ITERATIONS = 5;

    // The Gemini generateContent REST surface (v1beta) is still fully supported by Google,
    // but the specific *model name* rotates over time and old ones get retired (this is what
    // caused the previous "models/gemini-1.5-flash is not found" HTTP 404s). We never hardcode
    // a single model: it is always read from the GEMINI_MODEL environment variable, with a
    // currently-supported model used only as the default when that variable isn't set.
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";

    /**
     * Thrown when Gemini genuinely could not produce enough valid, non-duplicate questions
     * after all retries. Callers should surface a clear error to the user rather than silently
     * degrading to lower-quality content.
     */
    private static class InsufficientQuestionsException extends RuntimeException {
        final int achieved;
        final int requested;

        InsufficientQuestionsException(int achieved, int requested) {
            super("AI generated only " + achieved + " of " + requested + " valid, unique questions after retries.");
            this.achieved = achieved;
            this.requested = requested;
        }
    }

    public QuizResponse generateQuiz(QuizRequest request) {
        // 1. Validate notes
        if (request.getNotes() == null || request.getNotes().trim().length() < 15) {
            return new QuizResponse(false, "Please provide sufficient study notes (at least a few sentences) to generate a quiz.");
        }

        // 2. Validate question count (selected by user: 3 to 30)
        int numQuestions = request.getNumQuestions() != null ? request.getNumQuestions() : 5;
        if (numQuestions < 3) numQuestions = 3;
        if (numQuestions > 30) numQuestions = 30;

        // 3. Validate difficulty
        String difficulty = request.getDifficulty();
        if (difficulty == null || difficulty.trim().isEmpty()) {
            difficulty = "Medium";
        }

        String notes = request.getNotes().trim();

        // 4. Detect Language
        DetectedLanguage detectedLanguage = LanguageDetector.detectLanguage(notes);

        // Required backend logging (no secrets, no full note content)
        System.out.println("[QuizService] Request received - Extracted text length: " + notes.length()
                + " chars, Requested question count: " + numQuestions + ", Language: " + detectedLanguage.getLabel());

        // 5. Check API key (env var first, then legacy alias, then system property)
        String apiKey = resolveApiKey();

        if (apiKey != null && !apiKey.isEmpty()) {
            // An API key is configured, so the user expects real AI-generated questions.
            // If Gemini genuinely fails, we tell them clearly instead of silently swapping in
            // lower-quality local questions - that would hide a real problem from the user.
            try {
                return generateQuizWithGemini(notes, numQuestions, difficulty, detectedLanguage, apiKey);
            } catch (InsufficientQuestionsException e) {
                System.err.println("[QuizService] " + e.getMessage());
                if (e.achieved > 0) {
                    return new QuizResponse(false, "The AI could only generate " + e.achieved + " unique, high-quality question(s) from your notes (you requested " + e.requested + "). Try requesting fewer questions or adding more detail to your notes.");
                }
                return new QuizResponse(false, "The AI could not generate a valid quiz from your notes after several attempts. Please try again, or add more detailed study notes.");
            } catch (Exception e) {
                // Network error, HTTP failure, malformed response, etc.
                System.err.println("[QuizService] Gemini API call failed: " + safeMessage(e));
                return new QuizResponse(false, "We couldn't reach the AI quiz generator right now. Please check your connection and try again in a moment.");
            }
        }

        // 6. No API key configured at all (e.g. local/offline development or automated tests).
        // Use the local notes-based generator, which is held to the same validation and
        // duplicate-detection rules as the AI path - it never invents generic filler questions.
        System.out.println("[QuizService] No Gemini API key configured - using local notes-based fallback generator.");
        return generateFallbackQuiz(notes, numQuestions, difficulty, detectedLanguage);
    }

    private String resolveApiKey() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getenv("AI_API_KEY");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getProperty("GEMINI_API_KEY");
        }
        return apiKey == null ? null : apiKey.trim();
    }

    private String resolveModel() {
        String model = System.getenv("GEMINI_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = System.getProperty("GEMINI_MODEL");
        }
        if (model == null || model.trim().isEmpty()) {
            model = DEFAULT_GEMINI_MODEL;
        }
        return model.trim();
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        // Never let an API key leak into logs even if a lower-level exception message
        // happened to include the request URL.
        return msg.replaceAll("key=[^&\\s]+", "key=***");
    }

    private QuizResponse generateQuizWithGemini(String notes, int targetCount, String difficulty, DetectedLanguage language, String apiKey) throws Exception {
        List<Question> acceptedQuestions = new ArrayList<>();
        String model = resolveModel();

        // Generate extra candidates (targetCount + buffer) to account for duplicates
        int initialCandidateCount = targetCount + Math.max(3, (int) Math.ceil(targetCount * 0.3));
        if (initialCandidateCount > 35) initialCandidateCount = 35;

        // Initial Generation Prompt
        String systemPrompt = String.format(
            "You are QuizForge AI, an expert educational assessment generator. Your task is to generate a quiz based ONLY on the provided study notes.\n\n" +
            "LANGUAGE INSTRUCTION:\n" +
            "%s\n\n" +
            "CRITICAL REQUIRED QUESTION COUNT:\n" +
            "You MUST generate EXACTLY %d candidate questions. Do NOT generate fewer questions.\n\n" +
            "QUESTION DIVERSITY & QUALITY RULES:\n" +
            "1. Base EVERY question, correct answer, incorrect option, and explanation strictly and ONLY on facts present in the study notes below. Do NOT invent facts or use outside knowledge. Every option (correct and incorrect) must be something that could plausibly be evaluated against the notes - do not introduce named entities, numbers, or claims that never appear in the notes.\n" +
            "2. Ensure HIGH QUESTION DIVERSITY. Generate questions covering different parts and aspects of the notes (such as Definitions, Core Concepts, Functions, Examples, Applications, or Comparisons).\n" +
            "3. STRICTLY PREVENT DUPLICATES: Do NOT generate questions that test the exact same concept or reword another question.\n" +
            "4. Target difficulty level: %s.\n" +
            "5. Each question must have exactly 4 options in an array 'options'.\n" +
            "6. Specify 'correctAnswer' as the 0-based integer index (0, 1, 2, or 3) pointing to the correct option.\n" +
            "7. Provide a short explanation citing the specific note content.\n" +
            "8. Provide a 'concept' field: a short 2-4 word internal topic label describing exactly what underlying idea the question tests (e.g. \"RAM purpose\", \"CPU function\"). Two questions that test the same idea, even reworded, MUST share the same concept label so duplicates can be detected - and you must never emit two questions with the same concept label in one response.\n" +
            "9. Return ONLY valid raw JSON with NO markdown code block formatting (no ```json). Structure:\n" +
            "{\n" +
            "  \"questions\": [\n" +
            "    {\n" +
            "      \"question\": \"String\",\n" +
            "      \"options\": [\"Option 0\", \"Option 1\", \"Option 2\", \"Option 3\"],\n" +
            "      \"correctAnswer\": 0,\n" +
            "      \"explanation\": \"String\",\n" +
            "      \"concept\": \"String\"\n" +
            "    }\n" +
            "  ]\n" +
            "}\n\n" +
            "STUDY NOTES:\n---\n%s\n---",
            language.getPromptInstruction(), initialCandidateCount, difficulty, notes
        );

        List<Question> candidateBatch = fetchQuestionsFromGemini(systemPrompt, apiKey, model);
        filterAndAddUniqueQuestions(candidateBatch, acceptedQuestions, targetCount);

        // Automated Replacement & Regeneration Loop
        int retry = 0;
        while (acceptedQuestions.size() < targetCount && retry < MAX_RETRY_ITERATIONS) {
            retry++;
            int needed = targetCount - acceptedQuestions.size();
            int replacementCandidateCount = needed + Math.max(2, (int) Math.ceil(needed * 0.5));
            if (replacementCandidateCount > 25) replacementCandidateCount = 25;

            StringBuilder existingQuestionsSummary = new StringBuilder();
            for (int i = 0; i < acceptedQuestions.size(); i++) {
                existingQuestionsSummary.append(i + 1).append(". ").append(acceptedQuestions.get(i).getQuestion());
                if (acceptedQuestions.get(i).getConcept() != null && !acceptedQuestions.get(i).getConcept().isEmpty()) {
                    existingQuestionsSummary.append(" [concept: ").append(acceptedQuestions.get(i).getConcept()).append("]");
                }
                existingQuestionsSummary.append("\n");
            }

            String replacementPrompt = String.format(
                "You are QuizForge AI. We are generating a quiz from the study notes below.\n\n" +
                "LANGUAGE INSTRUCTION:\n%s\n\n" +
                "CRITICAL DUPLICATE AVOIDANCE:\n" +
                "We ALREADY have the following questions covering these concepts:\n" +
                "%s\n" +
                "Generate new questions from different concepts in the provided notes. Do not repeat, rephrase, or semantically duplicate any existing question or concept label listed above. The final questions must test information that has not already been tested.\n\n" +
                "Your task: Generate EXACTLY %d NEW, UNIQUE candidate question(s) testing DIFFERENT concepts, facts, or details in the notes.\n" +
                "Target difficulty: %s.\n" +
                "Every question, option, and explanation must be grounded strictly in the study notes (see rules above), and each must include a distinct 'concept' label not already used.\n" +
                "Return ONLY valid raw JSON without markdown code fences:\n" +
                "{\n" +
                "  \"questions\": [\n" +
                "    {\n" +
                "      \"question\": \"String\",\n" +
                "      \"options\": [\"Option 0\", \"Option 1\", \"Option 2\", \"Option 3\"],\n" +
                "      \"correctAnswer\": 0,\n" +
                "      \"explanation\": \"String\",\n" +
                "      \"concept\": \"String\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "STUDY NOTES:\n---\n%s\n---",
                language.getPromptInstruction(), existingQuestionsSummary.toString(), replacementCandidateCount, difficulty, notes
            );

            try {
                List<Question> replacementBatch = fetchQuestionsFromGemini(replacementPrompt, apiKey, model);
                filterAndAddUniqueQuestions(replacementBatch, acceptedQuestions, targetCount);
            } catch (Exception e) {
                System.err.println("[QuizService] Replacement generation iteration " + retry + " failed: " + safeMessage(e));
            }
        }

        // Final safety check: trim to exact requested count
        if (acceptedQuestions.size() > targetCount) {
            acceptedQuestions = new ArrayList<>(acceptedQuestions.subList(0, targetCount));
        }

        if (acceptedQuestions.size() < targetCount) {
            // Do NOT silently pad with generic filler questions - surface this to the caller
            // so it can show a clear, honest error instead.
            throw new InsufficientQuestionsException(acceptedQuestions.size(), targetCount);
        }

        return new QuizResponse(acceptedQuestions, language.getLabel());
    }

    private List<Question> fetchQuestionsFromGemini(String promptStr, String apiKey, String model) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

        String requestJson = String.format(
            "{\"contents\":[{\"parts\":[{\"text\":%s}]}]}",
            objectMapper.writeValueAsString(promptStr)
        );

        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        // Pass the key as a header rather than a URL query parameter, per Gemini's current
        // recommended auth method - keeps it out of server access logs and URL history.
        conn.setRequestProperty("x-goog-api-key", apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(25000);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestJson.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int status = conn.getResponseCode();
        BufferedReader br;
        if (status >= 200 && status < 300) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            br = new BufferedReader(new InputStreamReader(
                    conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String bodySnippet = sb.length() > 500 ? sb.substring(0, 500) + "..." : sb.toString();

            if (status == 404) {
                // This is exactly the failure mode reported in production: the configured
                // model name is not available on this API version. Fail loudly with a
                // message that points straight at the fix (GEMINI_MODEL), instead of the
                // caller silently falling back to lower-quality local questions.
                throw new RuntimeException("Gemini model '" + model + "' was not found for this API version (HTTP 404). "
                        + "Set the GEMINI_MODEL environment variable to a currently supported model. Response: " + bodySnippet);
            } else if (status == 401 || status == 403) {
                throw new RuntimeException("Gemini API rejected the request (HTTP " + status + "). Check that GEMINI_API_KEY is valid and has access to model '" + model + "'. Response: " + bodySnippet);
            } else if (status == 429) {
                throw new RuntimeException("Gemini API rate limit exceeded (HTTP 429). Response: " + bodySnippet);
            } else {
                throw new RuntimeException("Gemini API error (HTTP " + status + "): " + bodySnippet);
            }
        }

        StringBuilder responseSb = new StringBuilder();
        String responseLine;
        while ((responseLine = br.readLine()) != null) {
            responseSb.append(responseLine).append('\n');
        }

        JsonNode root = objectMapper.readTree(responseSb.toString());

        if (root.has("promptFeedback") && root.path("promptFeedback").has("blockReason")) {
            throw new RuntimeException("Gemini blocked the request: " + root.path("promptFeedback").path("blockReason").asText());
        }

        JsonNode candidatesNode = root.path("candidates");
        if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates in its response.");
        }

        JsonNode partsNode = candidatesNode.get(0).path("content").path("parts");
        if (!partsNode.isArray() || partsNode.isEmpty()) {
            throw new RuntimeException("Gemini response candidate had no content parts.");
        }
        String contentText = partsNode.get(0).path("text").asText();

        String jsonText = contentText.trim();
        if (jsonText.startsWith("```json")) {
            jsonText = jsonText.substring(7);
        } else if (jsonText.startsWith("```")) {
            jsonText = jsonText.substring(3);
        }
        if (jsonText.endsWith("```")) {
            jsonText = jsonText.substring(0, jsonText.length() - 3);
        }
        jsonText = jsonText.trim();

        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(jsonText);
        } catch (Exception parseEx) {
            String snippet = jsonText.length() > 300 ? jsonText.substring(0, 300) + "..." : jsonText;
            throw new RuntimeException("Gemini response was not valid JSON (model may have added commentary): " + snippet, parseEx);
        }
        if (parsed.has("error")) {
            throw new RuntimeException(parsed.get("error").asText());
        }

        JsonNode questionsArray = parsed.path("questions");
        List<Question> list = new ArrayList<>();
        if (questionsArray.isArray()) {
            for (JsonNode qNode : questionsArray) {
                String qText = qNode.path("question").asText();
                List<String> rawOptions = new ArrayList<>();
                for (JsonNode opt : qNode.path("options")) {
                    rawOptions.add(opt.asText());
                }
                int correct = qNode.path("correctAnswer").asInt(0);
                String expl = qNode.path("explanation").asText();
                String concept = qNode.path("concept").asText(null);

                if (qText != null && !qText.trim().isEmpty() && rawOptions.size() == 4) {
                    boolean allValid = true;
                    Set<String> uniqueOptions = new HashSet<>();
                    List<String> trimmedOptions = new ArrayList<>();
                    for (String opt : rawOptions) {
                        if (opt == null || opt.trim().isEmpty()) {
                            allValid = false;
                            break;
                        }
                        String tr = opt.trim();
                        trimmedOptions.add(tr);
                        uniqueOptions.add(tr.toLowerCase());
                    }

                    if (allValid && uniqueOptions.size() == 4 && correct >= 0 && correct < 4) {
                        String correctText = trimmedOptions.get(correct);

                        // Randomize correct answer position across A, B, C, D
                        List<String> shuffledOptions = new ArrayList<>(trimmedOptions);
                        Collections.shuffle(shuffledOptions);
                        int newCorrectIndex = shuffledOptions.indexOf(correctText);

                        list.add(new Question(qText.trim(), shuffledOptions, newCorrectIndex, expl.trim(),
                                concept != null ? concept.trim() : null));
                    }
                }
            }
        }
        return list;
    }

    private void filterAndAddUniqueQuestions(List<Question> candidates, List<Question> acceptedQuestions, int targetCount) {
        for (Question q : candidates) {
            if (acceptedQuestions.size() >= targetCount) {
                break;
            }
            if (!DuplicateDetector.isDuplicate(q, acceptedQuestions)) {
                acceptedQuestions.add(q);
            }
        }
    }

    // =========================================================================================
    // Local, notes-only fallback generator.
    //
    // Used ONLY when no Gemini API key is configured at all (e.g. local development, CI,
    // or an intentionally offline deployment). It must never invent generic filler wording
    // like "What is stated regarding X?" - every question, correct answer, and distractor is
    // derived directly from the extracted facts in the user's notes, and every candidate is
    // still run through the same DuplicateDetector used for AI-generated questions.
    // =========================================================================================

    /** A single fact extracted from one sentence/clause of the user's notes. */
    private static class Fact {
        final String sentence;
        final String subject;   // may be null if no definition pattern was found
        final String predicate; // may be null if no definition pattern was found
        final List<String> keywords; // significant, non-stopword tokens for cloze questions

        Fact(String sentence, String subject, String predicate, List<String> keywords) {
            this.sentence = sentence;
            this.subject = subject;
            this.predicate = predicate;
            this.keywords = keywords;
        }
    }

    private QuizResponse generateFallbackQuiz(String notes, int requestedCount, String difficulty, DetectedLanguage language) {
        boolean isTamil = language == DetectedLanguage.TAMIL || language == DetectedLanguage.MIXED_TAMIL_ENGLISH;

        List<Fact> facts = extractFacts(notes);
        if (facts.isEmpty()) {
            return new QuizResponse(false, "Could not extract any distinct facts from the provided notes to build a quiz. Please provide more detailed study notes.");
        }

        List<Question> candidates = buildCandidateQuestions(facts, isTamil);

        List<Question> accepted = new ArrayList<>();
        for (Question q : candidates) {
            if (accepted.size() >= requestedCount) break;
            if (!DuplicateDetector.isDuplicate(q, accepted)) {
                accepted.add(q);
            }
        }

        if (accepted.isEmpty()) {
            return new QuizResponse(false, "Could not build any valid, unique questions from the provided notes. Please provide more detailed study notes.");
        }

        // Honesty over inflation: if the notes simply don't contain enough distinct
        // material to reach the requested count without duplicating a concept, return
        // what was genuinely produced rather than padding with artificial questions.
        return new QuizResponse(accepted, language.getLabel());
    }

    private static final Set<String> ENGLISH_STOPWORDS = new HashSet<>(Arrays.asList(
        "the", "a", "an", "and", "or", "but", "if", "because", "as", "what", "which", "who", "whom",
        "this", "that", "these", "those", "am", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "having", "does", "did", "doing", "would", "should", "could",
        "ought", "you", "he", "she", "it", "we", "they", "of", "to", "in", "for", "with", "on",
        "at", "from", "by", "about", "against", "between", "into", "through", "during", "before",
        "after", "above", "below", "up", "down", "out", "off", "over", "under", "again", "further",
        "then", "once", "here", "there", "why", "how", "all", "any", "both", "each",
        "few", "more", "most", "other", "some", "such", "not", "only", "own", "same",
        "so", "than", "too", "very", "can", "will", "just", "now", "also"
    ));

    private List<Fact> extractFacts(String notes) {
        String[] rawSegments = notes.split("(?<=[.!?])\\s+|(?<=\\n)|(?<=[,;])\\s+");
        List<Fact> facts = new ArrayList<>();

        for (String raw : rawSegments) {
            String seg = raw.trim();
            if (seg.length() < 10) continue;

            String subject = null;
            String predicate = null;

            String[] splitMarkers = {" is ", " are ", " refers to ", " means ", " என்பது "};
            for (String marker : splitMarkers) {
                int idx = seg.indexOf(marker);
                if (idx > 2) {
                    String left = seg.substring(0, idx).trim();
                    String right = seg.substring(idx + marker.length()).trim().replaceAll("[.!?]+$", "");
                    // Keep the subject short (it becomes part of the question phrasing) and
                    // make sure the predicate is substantive enough to be a real answer.
                    if (!left.isEmpty() && left.split("\\s+").length <= 6 && right.length() >= 4) {
                        subject = left;
                        predicate = right;
                        break;
                    }
                }
            }

            List<String> keywords = new ArrayList<>();
            for (String token : seg.replaceAll("[.,!?;:()\\[\\]\"']", " ").split("\\s+")) {
                String t = token.trim();
                if (t.length() > 3 && !ENGLISH_STOPWORDS.contains(t.toLowerCase())) {
                    keywords.add(t);
                }
            }

            facts.add(new Fact(seg, subject, predicate, keywords));
        }
        return facts;
    }

    private List<Question> buildCandidateQuestions(List<Fact> facts, boolean isTamil) {
        List<Question> candidates = new ArrayList<>();

        // Pool every predicate and every keyword across the whole note set up front, so
        // distractors for one fact can be drawn from genuinely different facts elsewhere
        // in the notes rather than being invented.
        List<String> allPredicates = new ArrayList<>();
        List<String> allKeywords = new ArrayList<>();
        for (Fact f : facts) {
            if (f.predicate != null) allPredicates.add(f.predicate);
            allKeywords.addAll(f.keywords);
        }

        Random random = new Random(); // shuffling only affects option order/distractor pick, not correctness

        for (int i = 0; i < facts.size(); i++) {
            Fact fact = facts.get(i);

            // --- Definition-style question: "What is <subject>?" -> answer = predicate ---
            if (fact.subject != null && fact.predicate != null) {
                List<String> otherPredicates = new ArrayList<>();
                for (String p : allPredicates) {
                    if (!p.equalsIgnoreCase(fact.predicate)) otherPredicates.add(p);
                }
                Collections.shuffle(otherPredicates, random);

                List<String> options = new ArrayList<>();
                options.add(truncate(fact.predicate, 140));
                for (String p : otherPredicates) {
                    if (options.size() >= 4) break;
                    String candidate = truncate(p, 140);
                    if (!options.contains(candidate)) options.add(candidate);
                }
                fillGenericDistractors(options, isTamil);

                Collections.shuffle(options, random);
                int correctIndex = options.indexOf(truncate(fact.predicate, 140));

                String qText = isTamil
                        ? ("'" + fact.subject + "' என்றால் என்ன?")
                        : ("What is " + fact.subject + "?");
                String explanation = isTamil
                        ? ("குறிப்பிலிருந்து: \"" + fact.sentence + "\"")
                        : ("From your notes: \"" + fact.sentence + "\"");
                String concept = normalizeConcept(fact.subject);

                candidates.add(new Question(qText, options, correctIndex, explanation, concept));
            }

            // --- Cloze-style question: blank out the single most distinctive term in the
            // sentence. Only one per sentence - generating several near-identical blanks of
            // the same short sentence would just be a wordier version of the same duplicate
            // problem we're fixing, and the duplicate detector would (correctly) reject the
            // extras anyway since they'd share almost all of their words with each other.
            List<String> segmentKeywords = new ArrayList<>(new LinkedHashSet<>(fact.keywords));
            if (fact.subject != null) {
                segmentKeywords.removeIf(w -> w.equalsIgnoreCase(fact.subject.trim()));
            }
            segmentKeywords.sort((a, b) -> b.length() - a.length()); // longest = usually most distinctive

            if (!segmentKeywords.isEmpty()) {
                String answerWord = segmentKeywords.get(0);
                String blanked = replaceFirstWholeWord(fact.sentence, answerWord, "_____");

                if (!blanked.equals(fact.sentence)) {
                    List<String> distractorPool = new ArrayList<>();
                    for (String kw : allKeywords) {
                        if (!kw.equalsIgnoreCase(answerWord)) distractorPool.add(kw);
                    }
                    Collections.shuffle(distractorPool, random);

                    List<String> options = new ArrayList<>();
                    options.add(answerWord);
                    for (String kw : distractorPool) {
                        if (options.size() >= 4) break;
                        if (!containsIgnoreCase(options, kw)) options.add(kw);
                    }
                    fillGenericDistractors(options, isTamil);

                    Collections.shuffle(options, random);
                    int correctIndex = indexOfIgnoreCase(options, answerWord);

                    String qText = isTamil
                            ? ("காலியிடத்தை நிரப்பவும்: \"" + blanked + "\"")
                            : ("Fill in the blank based on your notes: \"" + blanked + "\"");
                    String explanation = isTamil
                            ? ("முழு வாக்கியம்: \"" + fact.sentence + "\"")
                            : ("Full statement from your notes: \"" + fact.sentence + "\"");
                    String concept = normalizeConcept(answerWord);

                    candidates.add(new Question(qText, options, correctIndex, explanation, concept));
                }
            }
        }

        // Interleave rather than leaving all of one fact's questions adjacent, for better
        // topic spread if only the first N candidates end up being used.
        Collections.shuffle(candidates, new Random(candidates.size()));
        return candidates;
    }

    private void fillGenericDistractors(List<String> options, boolean isTamil) {
        int n = 1;
        while (options.size() < 4) {
            String filler = isTamil
                    ? ("குறிப்பில் இது குறிப்பிடப்படவில்லை (" + n + ")")
                    : ("Not mentioned in your notes (" + n + ")");
            if (!options.contains(filler)) options.add(filler);
            n++;
            if (n > 10) break; // safety valve, should never trigger
        }
    }

    private String normalizeConcept(String text) {
        if (text == null) return null;
        String cleaned = text.trim().toLowerCase().replaceAll("[^\\p{L}\\p{N}\\s]", "").trim();
        String[] words = cleaned.split("\\s+");
        if (words.length <= 4) return cleaned;
        return String.join(" ", Arrays.copyOfRange(words, 0, 4));
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private int indexOfIgnoreCase(List<String> list, String value) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(value)) return i;
        }
        return -1;
    }

    private String replaceFirstWholeWord(String sentence, String word, String replacement) {
        return sentence.replaceFirst("(?i)\\b" + java.util.regex.Pattern.quote(word) + "\\b", java.util.regex.Matcher.quoteReplacement(replacement));
    }
}
