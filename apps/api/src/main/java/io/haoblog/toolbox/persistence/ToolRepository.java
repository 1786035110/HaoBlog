package io.haoblog.toolbox.persistence;

import io.haoblog.toolbox.domain.Tool;
import io.haoblog.toolbox.domain.ToolStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ToolRepository extends JpaRepository<Tool, UUID>, JpaSpecificationExecutor<Tool> {
    boolean existsByCategoryId(UUID categoryId);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    List<Tool> findAllByStatus(ToolStatus status, Sort sort);
    List<Tool> findAllByStatus(ToolStatus status, Pageable pageable);
}
