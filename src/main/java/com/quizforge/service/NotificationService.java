package com.quizforge.service;

import com.quizforge.dto.NotificationDTO;
import com.quizforge.entity.NotificationEntity;
import com.quizforge.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
        initDefaultNotifications();
    }

    private void initDefaultNotifications() {
        if (repository.count() == 0) {
            repository.save(new NotificationEntity(
                "Welcome to QuizForge AI!",
                "Upload a PDF or paste notes to generate custom AI quizzes in seconds.",
                "Today",
                true,
                "✨",
                "linear-gradient(135deg, #8b9bff, #5b6ee8)"
            ));
            repository.save(new NotificationEntity(
                "Daily Learning Reminder",
                "Consistent practice improves long-term memory retention.",
                "Yesterday",
                true,
                "💡",
                "linear-gradient(135deg, #ff9d4d, #e2732a)"
            ));
        }
    }

    public List<NotificationDTO> getNotifications() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(n -> new NotificationDTO(
                        n.getId(),
                        n.getTitle(),
                        n.getBody(),
                        n.getTimeDisplay(),
                        n.isUnread(),
                        n.getIcon(),
                        n.getColor()
                ))
                .collect(Collectors.toList());
    }

    public void markAsRead(Long id) {
        if (id != null) {
            repository.findById(id).ifPresent(n -> {
                n.setUnread(false);
                repository.save(n);
            });
        } else {
            // Mark all as read
            List<NotificationEntity> all = repository.findAll();
            all.forEach(n -> n.setUnread(false));
            repository.saveAll(all);
        }
    }

    public void createNotification(String title, String body, String timeDisplay, String icon, String color) {
        repository.save(new NotificationEntity(title, body, timeDisplay, true, icon, color));
    }
}
