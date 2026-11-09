package edu.campus.circulation.repository;

import edu.campus.circulation.domain.Role;
import edu.campus.circulation.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    long insert(String username, String passwordHash, Role role, boolean enabled, Instant createdAt);
    Optional<User> findById(long id);
    Optional<User> findByUsername(String username);
    List<User> findAllStudents();
    boolean updateEnabled(long userId, boolean enabled);
}
