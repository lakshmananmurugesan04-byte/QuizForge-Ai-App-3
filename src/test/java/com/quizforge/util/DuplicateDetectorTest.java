package com.quizforge.util;

import com.quizforge.dto.Question;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DuplicateDetectorTest {

    @Test
    public void testExactDuplicates() {
        Question q1 = new Question("What is the function of CPU?", Arrays.asList("Option A", "Option B", "Option C", "Option D"), 0, "Expl 1");
        Question q2 = new Question("What is the function of CPU?", Arrays.asList("Option A", "Option B", "Option C", "Option D"), 0, "Expl 2");

        assertTrue(DuplicateDetector.isDuplicate(q2, Arrays.asList(q1)));
    }

    @Test
    public void testRewordedDuplicates() {
        Question q1 = new Question("What is the main function of the CPU?", Arrays.asList("Processes instructions", "Stores files", "Renders graphics", "Powers device"), 0, "Expl 1");
        Question q2 = new Question("What is the primary function of the CPU?", Arrays.asList("Processes instructions", "Stores files", "Renders graphics", "Powers device"), 0, "Expl 2");

        assertTrue(DuplicateDetector.isDuplicate(q2, Arrays.asList(q1)));
    }

    @Test
    public void testDistinctQuestionsNotFlaggedAsDuplicates() {
        Question q1 = new Question("According to your notes, what is stated regarding: 'Photosynthesis'?", Arrays.asList("Process using sunlight", "Unrelated 1", "Unrelated 2", "Unrelated 3"), 0, "Expl 1");
        Question q2 = new Question("According to your notes, what is stated regarding: 'Chloroplast'?", Arrays.asList("Site where photosynthesis occurs", "Unrelated 1", "Unrelated 2", "Unrelated 3"), 0, "Expl 2");

        assertFalse(DuplicateDetector.isDuplicate(q2, Arrays.asList(q1)));
    }

    @Test
    public void testSameConceptLabelFlaggedAsDuplicateEvenWithDifferentWording() {
        // Mirrors the exact example from the spec: differently-worded questions that test
        // the same concept must be caught via the explicit concept label, even though the
        // wording/word-overlap heuristics alone might not catch it.
        Question q1 = new Question("What is the main function of RAM?",
                Arrays.asList("Temporary fast storage for running programs", "Long-term storage", "Rendering graphics", "Cooling the CPU"),
                0, "Expl 1", "RAM purpose");
        Question q2 = new Question("What is RAM primarily used for?",
                Arrays.asList("Temporary fast storage for running programs", "Long-term storage", "Rendering graphics", "Cooling the CPU"),
                0, "Expl 2", "RAM purpose");

        assertTrue(DuplicateDetector.isDuplicate(q2, Arrays.asList(q1)));
    }

    @Test
    public void testDifferentConceptLabelsNotFlaggedAsDuplicates() {
        Question q1 = new Question("What is RAM?", Arrays.asList("Fast temporary memory", "A", "B", "C"), 0, "Expl 1", "RAM purpose");
        Question q2 = new Question("What is a CPU?", Arrays.asList("Processes instructions", "D", "E", "F"), 0, "Expl 2", "CPU function");

        assertFalse(DuplicateDetector.isDuplicate(q2, Arrays.asList(q1)));
    }

    @Test
    public void testStripQuestionBoilerplate() {
        String input = "according to your notes what is stated regarding photosynthesis";
        String cleaned = DuplicateDetector.stripQuestionBoilerplate(input);
        assertEquals("photosynthesis", cleaned);
    }
}
