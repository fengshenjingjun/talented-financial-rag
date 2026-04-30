package com.financial.rag.evaluation;

import com.financial.rag.evaluation.AuditLogger.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findByUserId(String userId);

    List<AuditLogEntry> findByTimestampBetween(Instant from, Instant to);

    @Query("SELECT a FROM AuditLogEntry a WHERE a.complianceValidated = false ORDER BY a.timestamp DESC")
    List<AuditLogEntry> findUnvalidatedEntries();
}
