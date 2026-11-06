package com.p03.inventory.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select count(c) > 0 from Category c where lower(c.name) = lower(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query("select count(c) > 0 from Category c where lower(c.name) = lower(:name) and c.id <> :excludeId")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("excludeId") Long excludeId);

    List<Category> findAllByOrderByNameAsc();

    List<Category> findAllByActiveTrueOrderByNameAsc();
}
