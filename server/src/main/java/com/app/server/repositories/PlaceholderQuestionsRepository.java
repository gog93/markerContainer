package com.app.server.repositories;

import com.app.server.entities.PlaceholderQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface PlaceholderQuestionsRepository extends JpaRepository<PlaceholderQuestion, Long> {

    PlaceholderQuestion findByPlaceholderId(Long placeholderTag);
}
