package com.quizforge.service;

import com.quizforge.dto.HistoryDTO;
import com.quizforge.entity.QuizAttemptEntity;
import com.quizforge.repository.QuizAttemptRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    private final QuizAttemptRepository repository;
    private final NotificationService notificationService;

    public HistoryService(QuizAttemptRepository repository, NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    public List<HistoryDTO> getAllHistory() {
        return repository.findAllByOrderByDateDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public HistoryDTO saveAttempt(HistoryDTO dto) {
        int total = dto.getTotal() != null ? dto.getTotal() : 5;
        int correct = dto.getCorrect() != null ? dto.getCorrect() : 0;
        int wrong = total - correct;
        int score = correct;
        int percentage = total > 0 ? (int) Math.round(((double) correct / total) * 100) : 0;
        String difficulty = dto.getDifficulty() != null ? dto.getDifficulty() : "Medium";

        QuizAttemptEntity entity = new QuizAttemptEntity(total, correct, wrong, score, percentage, difficulty);
        QuizAttemptEntity saved = repository.save(entity);

        // Add auto notification for achievement / completed quiz
        if (percentage >= 80) {
            notificationService.createNotification(
                "Great Performance!",
                String.format("You scored %d%% on your %s quiz!", percentage, difficulty),
                "Just now",
                "🏆",
                "linear-gradient(135deg, #b58bff, #7c4de0)"
            );
        } else {
            notificationService.createNotification(
                "Quiz Completed",
                String.format("You scored %d%% (%d/%d correct) on your %s quiz.", percentage, correct, total, difficulty),
                "Just now",
                "📝",
                "linear-gradient(135deg, #4a90ff, #3a63d8)"
            );
        }

        return toDTO(saved);
    }

    private HistoryDTO toDTO(QuizAttemptEntity entity) {
        return new HistoryDTO(
                entity.getId(),
                entity.getDate(),
                entity.getTotalQuestions(),
                entity.getCorrectAnswers(),
                entity.getWrongAnswers(),
                entity.getScore(),
                entity.getPercentage(),
                entity.getDifficulty()
        );
    }
}
