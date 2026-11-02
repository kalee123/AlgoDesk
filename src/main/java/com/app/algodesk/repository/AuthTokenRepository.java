package com.app.algodesk.repository;

import com.app.algodesk.entity.AuthTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthTokenEntity, Long> {
    Optional<AuthTokenEntity> findTopByUser_UserIdAndActiveTrueOrderByIssuedAtDesc(Long userId);

    List<AuthTokenEntity> findByUser_UserIdAndActiveTrue(Long userId);
}
