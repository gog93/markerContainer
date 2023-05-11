package com.app.server.repositories;

import com.app.server.entities.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    Optional<Template> findByTag(String tag);

    Boolean existsByTag(String tag);

    void deleteByTag(String tag);
}
