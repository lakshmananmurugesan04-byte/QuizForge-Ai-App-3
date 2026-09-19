package com.quizforge.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    private Long id = 1L; // Single user profile for prototype / local session

    private String name = "Student";
    private String email = "student@quizforge.ai";

    @Lob
    @Column(columnDefinition = "CLOB")
    private String photo;

    public UserProfileEntity() {}

    public UserProfileEntity(String name, String email, String photo) {
        this.id = 1L;
        this.name = name;
        this.email = email;
        this.photo = photo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }
}
