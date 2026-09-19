package com.quizforge.util;

public class LanguageDetector {

    public enum DetectedLanguage {
        ENGLISH("English", "Generate all questions, options, and explanations strictly in English."),
        TAMIL("Tamil", "Generate all questions, options, and explanations strictly in Tamil. You may keep standard technical terms (such as CPU, RAM, Database, Hard Disk, Monitor) in English if that is standard technical usage."),
        MIXED_TAMIL_ENGLISH("Mixed Tamil & English", "The study notes contain a mixture of Tamil and English. Generate the quiz primarily in Tamil, maintaining commonly used technical terms (such as CPU, RAM, Operating System, Database, Spring Boot, etc.) in English where natural.");

        private final String label;
        private final String promptInstruction;

        DetectedLanguage(String label, String promptInstruction) {
            this.label = label;
            this.promptInstruction = promptInstruction;
        }

        public String getLabel() {
            return label;
        }

        public String getPromptInstruction() {
            return promptInstruction;
        }
    }

    public static DetectedLanguage detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return DetectedLanguage.ENGLISH;
        }

        int tamilCharCount = 0;
        int latinCharCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Check Tamil Unicode range (U+0B80 to U+0BFF)
            if (c >= '\u0B80' && c <= '\u0BFF') {
                tamilCharCount++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latinCharCount++;
            }
        }

        int totalLetters = tamilCharCount + latinCharCount;
        if (totalLetters == 0) {
            return DetectedLanguage.ENGLISH;
        }

        double tamilRatio = (double) tamilCharCount / totalLetters;

        if (tamilRatio > 0.40) {
            return DetectedLanguage.TAMIL;
        } else if (tamilCharCount > 5 && latinCharCount > 5 && tamilRatio >= 0.10) {
            return DetectedLanguage.MIXED_TAMIL_ENGLISH;
        } else {
            return DetectedLanguage.ENGLISH;
        }
    }
}
