package com.leetcodementor.repository;

import com.leetcodementor.entity.AiRequestMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiRequestMetadataRepository extends JpaRepository<AiRequestMetadata, UUID> {
    Optional<AiRequestMetadata> findByCacheKey(String cacheKey);
}
