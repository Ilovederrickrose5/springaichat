package com.example.springaichat.service;

import com.example.springaichat.dto.MessageRequest;
import com.example.springaichat.entity.Conversation;
import com.example.springaichat.entity.Message;
import com.example.springaichat.repository.ConversationRepository;
import com.example.springaichat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
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
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        lenient().when(chatClientBuilder.build()).thenReturn(chatClient);
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

    @Test
    void streamMessage_doesNotRetryAfterPartialOutput() {
        stubConversation(5L, 1L);
        when(messageRepository.findByConversationIdOrderByCreateTimeAsc(5L)).thenReturn(List.of());
        stubStreaming(Flux.just("A").concatWith(Flux.error(new RuntimeException("Connection reset"))));

        List<String> received = new ArrayList<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        chatService.streamMessage(1L, new MessageRequest(5L, "你好"))
                .doOnNext(received::add)
                .onErrorResume(e -> {
                    errorRef.set(e);
                    return Mono.empty();
                })
                .then()
                .block();

        // 已输出 "A" 后失败：不得重试，否则前端会收到重复内容
        assertThat(received).containsExactly("A");
        assertThat(errorRef.get()).hasMessageContaining("Connection reset");
        verify(streamResponseSpec, times(1)).content();
    }

    @Test
    void streamMessage_persistsAiMessageWhenRedisWriteFails() {
        stubConversation(5L, 1L);
        when(messageRepository.findByConversationIdOrderByCreateTimeAsc(5L)).thenReturn(List.of());
        stubStreaming(Flux.just("回答"));

        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations).set(anyString(), any(), any(Duration.class));

        List<String> received = chatService.streamMessage(1L, new MessageRequest(5L, "你好"))
                .collectList()
                .block();

        // 缓存写失败不应影响流式响应
        assertThat(received).containsExactly("回答");

        // 数据库落库必须仍然发生
        ArgumentCaptor<com.example.springaichat.entity.Message> captor =
                ArgumentCaptor.forClass(com.example.springaichat.entity.Message.class);
        verify(messageRepository, timeout(2000).atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(m -> "assistant".equals(m.getRole()) && "回答".equals(m.getContent()));
    }

    private void stubConversation(Long conversationId, Long ownerId) {
        Conversation conversation = new Conversation(conversationId, ownerId, "新对话",
                LocalDateTime.now(), LocalDateTime.now());
        lenient().when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
    }

    private void stubStreaming(Flux<String> flux) {
        lenient().when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        lenient().when(chatClientRequestSpec.messages(anyList())).thenReturn(chatClientRequestSpec);
        lenient().when(chatClientRequestSpec.stream()).thenReturn(streamResponseSpec);
        lenient().when(streamResponseSpec.content()).thenReturn(flux);
    }
}
