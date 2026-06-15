package com.example.springaichat.repository;

import com.example.springaichat.entity.Message;
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
     * 根据会话ID查找所有消息，按创建时间正序
     */
    List<Message> findByConversationIdOrderByCreateTimeAsc(Long conversationId);

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