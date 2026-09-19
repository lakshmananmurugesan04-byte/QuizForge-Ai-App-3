package com.quizforge.controller;

import com.quizforge.dto.HistoryDTO;
import com.quizforge.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHistory() {
        List<HistoryDTO> history = historyService.getAllHistory();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("history", history);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveHistory(@RequestBody HistoryDTO dto) {
        HistoryDTO saved = historyService.saveAttempt(dto);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("attempt", saved);
        return ResponseEntity.ok(response);
    }
}
