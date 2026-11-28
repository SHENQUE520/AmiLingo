package com.amilingo.platform.module.paper.repository;

import com.amilingo.platform.module.paper.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 论文台账数据访问层。
 * 提供按用户 ID 查询该用户所有论文的方法。
 */
@Repository
public interface PaperRepository extends JpaRepository<Paper, Long>, JpaSpecificationExecutor<Paper> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Paper p where p.id = :id")
    java.util.Optional<Paper> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    /** 查询某用户的全部论文，按创建时间倒序 */
    List<Paper> findByUserIdOrderByCreatedAtDesc(Long userId);
}
