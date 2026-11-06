package com.clouddocs.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CognitoIdentityRepository extends JpaRepository<CognitoIdentity, Long> {
    boolean existsByUserId(Long userId);
    Optional<CognitoIdentity> findByIssuerAndSubject(String issuer, String subject);
}
