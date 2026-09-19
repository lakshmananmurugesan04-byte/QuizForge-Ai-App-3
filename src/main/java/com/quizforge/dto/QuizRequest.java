package com.quizforge.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class QuizRequest {

    private String notes;

    @JsonProperty("numQuestions")
    @JsonAlias({"questionCount", "question_count", "num_questions", "numQuestions"})
    private Integer numQuestions = 5;

    private String difficulty = "Medium";

    public QuizRequest() {}

    public QuizRequest(String notes, Integer numQuestions, String difficulty) {
        this.notes = notes;
        this.numQuestions = numQuestions;
        this.difficulty = difficulty;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getNumQuestions() {
        return numQuestions;
    }

    public void setNumQuestions(Integer numQuestions) {
        this.numQuestions = numQuestions;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
