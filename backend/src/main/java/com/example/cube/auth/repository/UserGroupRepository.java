package com.example.cube.auth.repository;

import com.example.cube.auth.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {

    long countByGroupId(Long groupId);

    List<UserGroup> findByGroupIdIn(Collection<Long> groupIds);
}
