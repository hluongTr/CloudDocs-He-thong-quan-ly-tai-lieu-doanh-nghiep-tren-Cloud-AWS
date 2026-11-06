package com.clouddocs.favorite;

import com.clouddocs.auth.User;
import com.clouddocs.document.Document;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "document_favorites",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_document_favorite_user",
                        columnNames = {"user_id", "document_id"}))
public class DocumentFavorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Document getDocument() {
        return document;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setDocument(Document document) {
        this.document = document;
    }
}
