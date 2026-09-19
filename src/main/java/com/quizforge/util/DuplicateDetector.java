package com.quizforge.util;

import com.quizforge.dto.Question;
import java.util.*;

public class DuplicateDetector {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "the", "a", "an", "and", "or", "but", "if", "because", "as", "what", "which", "who", "whom",
        "this", "that", "these", "those", "am", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "having", "do", "does", "did", "doing", "would", "should", "could",
        "ought", "i", "you", "he", "she", "it", "we", "they", "of", "to", "in", "for", "with", "on",
        "at", "from", "by", "about", "against", "between", "into", "through", "during", "before",
        "after", "above", "below", "up", "down", "out", "off", "over", "under", "again", "further",
        "then", "once", "here", "there", "why", "how", "all", "any", "both", "each",
        "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same",
        "so", "than", "too", "very", "can", "will", "just", "dont", "should", "now", "according",
        "notes", "provided", "following", "reflects", "statement", "true", "false", "correct"
    ));

    public static boolean isDuplicate(Question newQ, List<Question> existingQuestions) {
        if (newQ == null || newQ.getQuestion() == null || newQ.getQuestion().trim().isEmpty()) {
            return true;
        }

        for (Question existing : existingQuestions) {
            if (isDuplicatePair(newQ, existing)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDuplicatePair(Question q1, Question q2) {
        if (q1 == null || q2 == null) return false;

        String raw1 = q1.getQuestion();
        String raw2 = q2.getQuestion();

        // 1. Exact string match (case insensitive & whitespace normalized)
        String norm1 = normalize(raw1);
        String norm2 = normalize(raw2);
        if (norm1.equals(norm2)) {
            return true;
        }

        // 1b. Concept/topic match: if both questions carry an explicit internal concept
        // label (e.g. "RAM purpose") and they normalize to the same thing, they are
        // testing the same underlying idea regardless of how different the wording is.
        String concept1 = normalize(q1.getConcept());
        String concept2 = normalize(q2.getConcept());
        if (!concept1.isEmpty() && !concept2.isEmpty()) {
            if (concept1.equals(concept2)) {
                return true;
            }
            // Near-identical concept labels (e.g. "RAM purpose" vs "purpose of RAM") -
            // cheap stand-in for semantic similarity without needing an embeddings call.
            Set<String> conceptWords1 = extractKeyWords(concept1);
            Set<String> conceptWords2 = extractKeyWords(concept2);
            if (!conceptWords1.isEmpty() && !conceptWords2.isEmpty()
                    && jaccardSimilarity(conceptWords1, conceptWords2) >= 0.6) {
                return true;
            }
        }

        // 2. Levenshtein Similarity on cleaned question stems (stripping preamble boilerplate)
        String stem1 = stripQuestionBoilerplate(norm1);
        String stem2 = stripQuestionBoilerplate(norm2);
        double levSim = levenshteinSimilarity(stem1, stem2);
        if (levSim >= 0.85) {
            return true;
        }

        // 3. Extract correct answers
        String ans1 = getCorrectAnswerText(q1);
        String ans2 = getCorrectAnswerText(q2);
        String normAns1 = normalize(ans1);
        String normAns2 = normalize(ans2);

        // If correct answers are identical or very similar -> Check question stem overlap
        boolean answersMatch = !normAns1.isEmpty() && !normAns2.isEmpty() &&
                (normAns1.equals(normAns2) || levenshteinSimilarity(normAns1, normAns2) >= 0.80);

        // 4. Jaccard Word Set Overlap (excluding stop words)
        Set<String> words1 = extractKeyWords(norm1);
        Set<String> words2 = extractKeyWords(norm2);
        double jaccard = jaccardSimilarity(words1, words2);

        if (answersMatch) {
            // Same answer & same topic stem -> Definitely duplicate concept!
            if (jaccard >= 0.30) {
                return true;
            }
        } else {
            // Different answers / different details tested
            if (jaccard >= 0.85) {
                return true;
            }
        }

        return false;
    }

    public static String stripQuestionBoilerplate(String norm) {
        if (norm == null) return "";
        String cleaned = norm.trim();
        boolean changed = true;
        while (changed) {
            String before = cleaned;
            cleaned = cleaned.replaceAll("(?i)^(according to (your|the) (notes|study material|text)|which of the following (facts|statements)?|what (is|specific detail is)? (stated|mentioned)?|in (your|the) (notes|study material|text)|regarding|which statement (accurately )?describes|based on (your|the) notes)\\s*", "").trim();
            changed = !cleaned.equalsIgnoreCase(before);
        }
        return cleaned.isEmpty() ? norm : cleaned;
    }

    private static String getCorrectAnswerText(Question q) {
        if (q.getOptions() != null && q.getCorrectAnswer() != null &&
            q.getCorrectAnswer() >= 0 && q.getCorrectAnswer() < q.getOptions().size()) {
            return q.getOptions().get(q.getCorrectAnswer());
        }
        return "";
    }

    public static String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Set<String> extractKeyWords(String normalizedText) {
        Set<String> set = new HashSet<>();
        String[] tokens = normalizedText.split("\\s+");
        for (String t : tokens) {
            if (t.length() > 2 && !STOP_WORDS.contains(t)) {
                set.add(t);
            }
        }
        return set;
    }

    private static double jaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    private static double levenshteinSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        int distance = computeLevenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    private static int computeLevenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
