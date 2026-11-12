package edu.campus.circulation.repository;

import edu.campus.circulation.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    long insert(String name, boolean enabled, Long avoidedGrams, String factorSource);
    boolean update(long id, String name, boolean enabled, Long avoidedGrams, String factorSource);
    Optional<Category> findById(long id);
    Optional<Category> findByName(String name);
    List<Category> findAll(boolean onlyEnabled);
}
