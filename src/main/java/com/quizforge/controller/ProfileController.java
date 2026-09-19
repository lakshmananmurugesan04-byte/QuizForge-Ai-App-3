package com.quizforge.controller;

import com.quizforge.dto.ProfileDTO;
import com.quizforge.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile() {
        ProfileDTO profile = profileService.getProfile();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("profile", profile);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody ProfileDTO dto) {
        ProfileDTO updated = profileService.updateProfile(dto);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("profile", updated);
        return ResponseEntity.ok(response);
    }
}
