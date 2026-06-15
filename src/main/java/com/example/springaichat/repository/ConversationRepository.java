package com.example.springaichat.repository;

import com.example.springaichat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话数据访问接口
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * 根据用户ID查找所有会话，按更新时间倒序
     */
    List<Conversation> findByUserIdOrderByUpdateTimeDesc(Long userId);

    /**
     * 检查会话是否属于指定用户
     */
    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * 删除用户的所有会话
     */
    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}