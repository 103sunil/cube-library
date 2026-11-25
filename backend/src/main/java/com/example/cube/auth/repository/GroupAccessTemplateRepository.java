package com.example.cube.auth.repository;

import com.example.cube.auth.entity.GroupAccessTemplate;
import com.example.cube.auth.entity.GroupAccessTemplateId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupAccessTemplateRepository extends JpaRepository<GroupAccessTemplate, GroupAccessTemplateId> {

    List<GroupAccessTemplate> findByIdGroupId(Long groupId);

    List<GroupAccessTemplate> findByIdAccessId(Long accessId);

    boolean existsByIdAccessId(Long accessId);
}
