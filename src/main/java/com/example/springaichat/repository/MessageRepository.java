package com.example.springaichat.repository;

import com.example.springaichat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息数据访问接口
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * 按自增主键排序：同一秒内写入的消息也能保证稳定顺序（create_time 精度可能只有秒）。
     */
    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    /**
     * 查询最新的一页消息（倒序取，调用方再反转）
     */
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    /**
     * 查询某个消息之前的一页消息（用于向上翻历史）
     */
    List<Message> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId, Pageable pageable);

    /**
     * 根据会话ID删除所有消息
     */
    @Modifying
    @Query("DELETE FROM Message m WHERE m.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 统计会话中的消息数量
     */
    long countByConversationId(Long conversationId);
}