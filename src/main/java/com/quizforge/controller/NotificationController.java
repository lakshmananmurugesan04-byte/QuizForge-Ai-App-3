package com.quizforge.controller;

import com.quizforge.dto.NotificationDTO;
import com.quizforge.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications() {
        List<NotificationDTO> list = notificationService.getNotifications();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("notifications", list);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/read")
    public ResponseEntity<Map<String, Object>> markRead(@RequestBody(required = false) Map<String, Object> payload) {
        Long id = null;
        if (payload != null && payload.containsKey("id") && payload.get("id") != null) {
            id = Long.valueOf(payload.get("id").toString());
        }
        notificationService.markAsRead(id);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
}
