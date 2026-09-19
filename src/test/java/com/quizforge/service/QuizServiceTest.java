package com.quizforge.service;

import com.quizforge.dto.Question;
import com.quizforge.dto.QuizRequest;
import com.quizforge.dto.QuizResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests exercise the LOCAL notes-only fallback path (no GEMINI_API_KEY is set in this
 * test environment). The fallback must never fabricate generic filler questions just to hit
 * the requested count - it should return only genuinely distinct, notes-grounded questions,
 * even if that means returning fewer than requested for short notes.
 */
public class QuizServiceTest {

    private QuizService quizService;

    private static final String[] BANNED_GENERIC_PHRASES = {
        "what is stated regarding",
        "which statement accurately describes",
        "what key detail is highlighted",
        "study note item #"
    };

    @BeforeEach
    public void setUp() {
        quizService = new QuizService();
    }

    private void assertHonestQuiz(QuizResponse response, int requested) {
        assertTrue(response.isSuccess(), "Quiz generation should succeed");
        assertNotNull(response.getQuestions(), "Questions list should not be null");
        assertFalse(response.getQuestions().isEmpty(), "Should return at least one real question");
        assertTrue(response.getQuestions().size() <= requested,
                "Should never return more questions than requested");

        Set<String> seenQuestionText = new HashSet<>();
        Set<String> seenConcepts = new HashSet<>();
        for (Question q : response.getQuestions()) {
            assertNotNull(q.getQuestion());
            assertEquals(4, q.getOptions().size(), "Every question must have exactly 4 options");
            assertTrue(q.getCorrectAnswer() >= 0 && q.getCorrectAnswer() < 4);

            String normalized = q.getQuestion().trim().toLowerCase();
            for (String banned : BANNED_GENERIC_PHRASES) {
                assertFalse(normalized.contains(banned),
                        "Fallback must not use generic filler wording: " + q.getQuestion());
            }

            assertTrue(seenQuestionText.add(normalized), "No two questions should be identical: " + q.getQuestion());
            if (q.getConcept() != null && !q.getConcept().isEmpty()) {
                assertTrue(seenConcepts.add(q.getConcept().toLowerCase()),
                        "No two questions should share the same concept label: " + q.getConcept());
            }
        }
    }

    @Test
    public void testGenerateQuizFromDetailedNotes() {
        String notes = "Photosynthesis is the process by which green plants convert light energy into chemical energy. " +
                "Chlorophyll is the green pigment in plants that absorbs light. " +
                "Stomata are small pores on leaves that allow gas exchange. " +
                "Glucose is produced during photosynthesis to nourish the plant. " +
                "Oxygen is released as a byproduct into the atmosphere.";

        QuizRequest request = new QuizRequest(notes, 5, "Medium");
        QuizResponse response = quizService.generateQuiz(request);

        assertHonestQuiz(response, 5);
    }

    @Test
    public void testGenerateQuizReachesRequestedCountWhenEnoughDistinctFactsExist() {
        String notes = "The Central Processing Unit (CPU) executes instructions. " +
                "RAM is volatile primary memory used for fast data access. " +
                "Hard Disk Drives provide persistent secondary storage. " +
                "The motherboard connects all hardware components together. " +
                "The Operating System manages hardware resources and user applications. " +
                "GPUs specialize in parallel processing for graphics and AI models.";

        QuizRequest request = new QuizRequest(notes, 3, "Medium");
        QuizResponse response = quizService.generateQuiz(request);

        assertHonestQuiz(response, 3);
        assertEquals(3, response.getQuestions().size(), "Should reach the requested count when enough distinct facts exist");
    }

    @Test
    public void testGenerateQuizDoesNotFabricateQuestionsForShortNotes() {
        // Deliberately short notes with only a couple of distinct facts, but the user asks
        // for far more questions than the notes actually support. The fix for Problem 2
        // means we must NOT pad this out with generic template questions.
        String notes = "Java is an object-oriented programming language. The JVM executes Java bytecode.";

        QuizRequest request = new QuizRequest(notes, 30, "Medium");
        QuizResponse response = quizService.generateQuiz(request);

        assertHonestQuiz(response, 30);
        assertTrue(response.getQuestions().size() < 30,
                "Should not fabricate filler questions to reach an unsupported count");
    }

    @Test
    public void testGenerateQuizTamilNotes() {
        String notes = "கணிப்பொறி என்பது தரவுகளை செயலாக்கும் ஒரு மின்னணு சாதனமாகும். " +
                "CPU என்பது மத்திய செயலகம் ஆகும். " +
                "RAM என்பது தற்காலிக நினைவகம் ஆகும். " +
                "வன்பொருள் மற்றும் மென்பொருள் கணிப்பொறியின் இரு முக்கிய கூறுகள்.";

        QuizRequest request = new QuizRequest(notes, 5, "Medium");
        QuizResponse response = quizService.generateQuiz(request);

        assertHonestQuiz(response, 5);
    }

    @Test
    public void testGenerateQuizRejectsInsufficientNotes() {
        QuizRequest request = new QuizRequest("too short", 5, "Medium");
        QuizResponse response = quizService.generateQuiz(request);

        assertFalse(response.isSuccess());
        assertNotNull(response.getError());
    }
}
