package com.app.server.repositories;

import com.app.server.entities.Placeholder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface PlaceholderRepository extends JpaRepository<Placeholder, Long> {
    Placeholder findByTemplateId(Long templateId);

    List<Placeholder> findAllByTemplateId(Long templateId);

    void deleteAllByTemplateId(Long templateId);

}
