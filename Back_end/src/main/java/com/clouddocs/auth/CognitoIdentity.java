package com.clouddocs.auth;
import jakarta.persistence.*;
@Entity
@Table(name = "cognito_identities", uniqueConstraints = {
    @UniqueConstraint(name = "uk_cognito_identity", columnNames = {"issuer", "subject"})})
public class CognitoIdentity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String issuer;
    @Column(nullable = false, length = 36)
    private String subject;
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    protected CognitoIdentity() {}
    public CognitoIdentity(String issuer, String subject, User user) {
        this.issuer = issuer; this.subject = subject; this.user = user;
    }
    public User getUser() { return user; }
}
