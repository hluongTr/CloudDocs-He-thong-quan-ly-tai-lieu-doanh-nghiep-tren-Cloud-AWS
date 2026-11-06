package com.clouddocs.notification;

import com.clouddocs.auth.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "notifications",
        indexes = @Index(name = "idx_notifications_created_at", columnList = "created_at"))
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "recipient_id")
    private Long recipientId;
    @Column(name = "work_task_id")
    private Long workTaskId;
    public Long getRecipientId(){return recipientId;}
    public void setRecipientId(Long value){recipientId=value;}
    public Long getWorkTaskId(){return workTaskId;}
    public void setWorkTaskId(Long value){workTaskId=value;}

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }
}
