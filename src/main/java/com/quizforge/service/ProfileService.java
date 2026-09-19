package com.quizforge.service;

import com.quizforge.dto.ProfileDTO;
import com.quizforge.entity.UserProfileEntity;
import com.quizforge.repository.UserProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final UserProfileRepository repository;

    public ProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    public ProfileDTO getProfile() {
        UserProfileEntity entity = repository.findById(1L).orElseGet(() -> {
            UserProfileEntity defaultProfile = new UserProfileEntity("Student", "student@quizforge.ai", null);
            return repository.save(defaultProfile);
        });
        return new ProfileDTO(entity.getName(), entity.getEmail(), entity.getPhoto());
    }

    public ProfileDTO updateProfile(ProfileDTO dto) {
        UserProfileEntity entity = repository.findById(1L).orElseGet(() -> new UserProfileEntity("Student", "student@quizforge.ai", null));

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            entity.setName(dto.getName().trim());
        }
        if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty()) {
            entity.setEmail(dto.getEmail().trim());
        }

        if (Boolean.TRUE.equals(dto.getRemovePhoto())) {
            entity.setPhoto(null);
        } else if (dto.getPhoto() != null) {
            entity.setPhoto(dto.getPhoto());
        }

        UserProfileEntity saved = repository.save(entity);
        return new ProfileDTO(saved.getName(), saved.getEmail(), saved.getPhoto());
    }
}
