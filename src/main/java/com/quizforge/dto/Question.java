package com.quizforge.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Question {

    private String question;
    private List<String> options;

    @JsonProperty("correctAnswer")
    @JsonAlias({"correct_answer", "correctAnswer"})
    private Integer correctAnswer;

    private String explanation;

    /**
     * Short (2-4 word) internal topic/concept label describing what the question tests,
     * e.g. "RAM purpose". Used by DuplicateDetector to catch reworded duplicates that test
     * the same underlying concept even when the wording is very different. Optional: may be
     * null for older callers / questions where no concept could be derived.
     */
    private String concept;

    public Question() {}

    public Question(String question, List<String> options, Integer correctAnswer, String explanation) {
        this(question, options, correctAnswer, explanation, null);
    }

    public Question(String question, List<String> options, Integer correctAnswer, String explanation, String concept) {
        this.question = question;
        this.options = options;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
        this.concept = concept;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public Integer getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(Integer correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getConcept() {
        return concept;
    }

    public void setConcept(String concept) {
        this.concept = concept;
    }
}
