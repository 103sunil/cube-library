package com.example.cube.knowledge.repository;

import com.example.cube.knowledge.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    Optional<Keyword> findByKeywordName(String keywordName);

    List<Keyword> findByKeywordNameIgnoreCaseOrderByKeywordIdAsc(String keywordName);
}
