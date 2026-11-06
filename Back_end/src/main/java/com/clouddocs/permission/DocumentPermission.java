package com.clouddocs.permission;

import com.clouddocs.auth.User;
import com.clouddocs.document.Document;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "document_permissions",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_document_permission_user",
                    columnNames = {"document_id", "user_id"})
        },
        indexes = {
            @Index(name = "idx_document_permissions_user", columnList = "user_id"),
            @Index(name = "idx_document_permissions_document", columnList = "document_id")
        })
public class DocumentPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "document_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_document_permissions_document"))
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_document_permissions_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentPermissionType permission;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public User getUser() {
        return user;
    }

    public DocumentPermissionType getPermission() {
        return permission;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setPermission(DocumentPermissionType permission) {
        this.permission = permission;
    }
}
