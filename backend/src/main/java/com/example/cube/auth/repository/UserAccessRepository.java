package com.example.cube.auth.repository;

import com.example.cube.auth.entity.UserAccess;
import com.example.cube.auth.entity.UserAccessId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAccessRepository extends JpaRepository<UserAccess, UserAccessId> {

    List<UserAccess> findByIdUserId(Long userId);

    List<UserAccess> findByIdAccessId(Long accessId);

    boolean existsByIdAccessId(Long accessId);
}
