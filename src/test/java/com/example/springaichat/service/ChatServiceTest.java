package com.example.springaichat.service;

import com.example.springaichat.dto.MessageRequest;
import com.example.springaichat.entity.Conversation;
import com.example.springaichat.entity.Message;
import com.example.springaichat.repository.ConversationRepository;
import com.example.springaichat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatService 纯单元测试：仅使用 Mockito，不启动 Spring 上下文，
 * 不连接 MySQL / Redis / DashScope，可在任意环境离线运行。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatClientBuilder, conversationRepository, messageRepository, redisTemplate);
        // @Value 字段集中注入，后续字段改名只需改这一处
        ReflectionTestUtils.setField(chatService, "maxHistorySize", 20);
        ReflectionTestUtils.setField(chatService, "maxMessageLength", 4000);
        ReflectionTestUtils.setField(chatService, "maxTokens", 4096);
        ReflectionTestUtils.setField(chatService, "cacheExpireHours", 24);
        ReflectionTestUtils.setField(chatService, "ragEnabled", false);
        ReflectionTestUtils.setField(chatService, "ragTopK", 3);
        ReflectionTestUtils.setField(chatService, "ragSimilarityThreshold", 0.30);
    }

    @Test
    void streamMessage_rejectsOverlongContent() {
        String content = "x".repeat(4001);

        List<String> received = chatService.streamMessage(1L, new MessageRequest(1L, content))
                .onErrorResume(e -> Mono.just("ERR:" + e.getMessage()))
                .collectList()
                .block();

        assertThat(received).containsExactly("ERR:消息长度不能超过4000个字符");
        verify(conversationRepository, never()).findById(any());
    }

    @Test
    void deleteMessage_rejectsForeignConversation() {
        Message message = new Message(11L, 5L, "user", "hi", LocalDateTime.now());
        Conversation conversation = new Conversation(5L, 99L, "标题", LocalDateTime.now(), LocalDateTime.now());
        when(messageRepository.findById(11L)).thenReturn(Optional.of(message));
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> chatService.deleteMessage(1L, 11L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("无权访问该会话");

        verify(messageRepository, never()).delete(any());
    }

    @Test
    void deleteMessage_deletesOwnMessageAndCache() {
        Message message = new Message(11L, 5L, "user", "hi", LocalDateTime.now());
        Conversation conversation = new Conversation(5L, 1L, "标题", LocalDateTime.now(), LocalDateTime.now());
        when(messageRepository.findById(11L)).thenReturn(Optional.of(message));
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        chatService.deleteMessage(1L, 11L);

        verify(messageRepository).delete(message);
        verify(redisTemplate).delete("chat:history:5");
    }
}
