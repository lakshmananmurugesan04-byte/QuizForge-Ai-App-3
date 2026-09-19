package com.quizforge.dto;

import java.time.LocalDateTime;

public class HistoryDTO {

    private Long id;
    private LocalDateTime date;
    private Integer total;
    private Integer correct;
    private Integer wrong;
    private Integer score;
    private Integer percentage;
    private String difficulty;

    public HistoryDTO() {}

    public HistoryDTO(Long id, LocalDateTime date, Integer total, Integer correct, Integer wrong, Integer score, Integer percentage, String difficulty) {
        this.id = id;
        this.date = date;
        this.total = total;
        this.correct = correct;
        this.wrong = wrong;
        this.score = score;
        this.percentage = percentage;
        this.difficulty = difficulty;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getCorrect() {
        return correct;
    }

    public void setCorrect(Integer correct) {
        this.correct = correct;
    }

    public Integer getWrong() {
        return wrong;
    }

    public void setWrong(Integer wrong) {
        this.wrong = wrong;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getPercentage() {
        return percentage;
    }

    public void setPercentage(Integer percentage) {
        this.percentage = percentage;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
