package com.quizforge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizResponse {

    private boolean success = true;
    private List<Question> questions;
    private String error;
    private String detectedLanguage;

    public QuizResponse() {}

    public QuizResponse(List<Question> questions) {
        this.success = true;
        this.questions = questions;
    }

    public QuizResponse(List<Question> questions, String detectedLanguage) {
        this.success = true;
        this.questions = questions;
        this.detectedLanguage = detectedLanguage;
    }

    public QuizResponse(boolean success, String error) {
        this.success = success;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    public void setDetectedLanguage(String detectedLanguage) {
        this.detectedLanguage = detectedLanguage;
    }
}
