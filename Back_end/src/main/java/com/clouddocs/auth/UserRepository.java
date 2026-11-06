package com.clouddocs.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where upper(u.role) = 'ADMIN' order by u.id")
    List<User> lockAdmins();

    @Query("select distinct u from CognitoIdentity c join c.user u where upper(u.status) <> 'DELETED' order by u.createdAt desc")
    List<User> findCognitoUsers();

    @Query("select count(distinct u.id) from CognitoIdentity c join c.user u where upper(u.role) = 'ADMIN' and upper(u.status) = 'ACTIVE'")
    long countActiveCognitoAdmins();

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForStorageUpdate(
            @org.springframework.data.repository.query.Param("id") Long id);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findAllByStatusIgnoreCaseAndIdNotOrderByFullNameAsc(String status, Long id);

    @Query(
            "select distinct u.department from User u where u.department is not null and trim(u.department) <> ''")
    List<String> findDistinctDepartments();
}
