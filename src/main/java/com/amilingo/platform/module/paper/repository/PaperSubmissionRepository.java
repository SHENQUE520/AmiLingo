package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaperSubmissionRepository extends JpaRepository<PaperSubmission, Long> {
    @org.springframework.data.jpa.repository.Query("select s.paperId from PaperSubmission s where s.id = :id")
    Optional<Long> findPaperIdById(@org.springframework.data.repository.query.Param("id") Long id);
    List<PaperSubmission> findByPaperIdOrderByRoundNoDesc(Long paperId);
    Optional<PaperSubmission> findFirstByPaperIdOrderByRoundNoDesc(Long paperId);
}
