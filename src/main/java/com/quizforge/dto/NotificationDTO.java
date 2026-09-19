package com.quizforge.dto;

public class NotificationDTO {

    private Long id;
    private String title;
    private String body;
    private String time;
    private boolean unread;
    private String icon;
    private String color;

    public NotificationDTO() {}

    public NotificationDTO(Long id, String title, String body, String time, boolean unread, String icon, String color) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.time = time;
        this.unread = unread;
        this.icon = icon;
        this.color = color;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public boolean isUnread() {
        return unread;
    }

    public void setUnread(boolean unread) {
        this.unread = unread;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
