package com.example.springaichat.controller;

import com.example.springaichat.dto.BatchDeleteRequest;
import com.example.springaichat.dto.ConversationResponse;
import com.example.springaichat.dto.MessageRequest;
import com.example.springaichat.dto.MessageResponse;
import com.example.springaichat.entity.Conversation;
import com.example.springaichat.entity.User;
import com.example.springaichat.exception.BusinessException;
import com.example.springaichat.service.ChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聊天控制器
 *
 * <p>业务异常统一由 GlobalExceptionHandler 处理，这里只负责参数接收与响应组装。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 获取用户的所有会话
     */
    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> getConversations(@AuthenticationPrincipal User user) {
        List<ConversationResponse> responseList = chatService.getConversations(user.getId()).stream()
                .map(this::convertToConversationResponse)
                .collect(Collectors.toList());

        return ok(null, responseList);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> createConversation(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) Map<String, String> request) {
        String title = request != null ? request.get("title") : null;
        Conversation conversation = chatService.createConversation(user.getId(), title);

        return ok("会话创建成功", convertToConversationResponse(conversation));
    }

    /**
     * 获取单个会话详情
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversation(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId) {
        Conversation conversation = chatService.getConversation(user.getId(), conversationId);

        return ok(null, convertToConversationResponse(conversation));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, Object>> deleteConversation(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId) {
        chatService.deleteConversation(user.getId(), conversationId);

        return ok("会话删除成功", null);
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Map<String, Object>> deleteMessage(
            @AuthenticationPrincipal User user,
            @PathVariable Long messageId) {
        chatService.deleteMessage(user.getId(), messageId);

        return ok("消息删除成功", null);
    }

    /**
     * 批量删除消息
     */
    @PostMapping("/messages/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeleteMessages(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody BatchDeleteRequest request) {
        logger.info("批量删除请求 - 用户ID: {}, 消息数: {}", user.getId(), request.getMessageIds().size());

        List<Long> messageIds = request.getMessageIds().stream()
                .map(this::toMessageId)
                .collect(Collectors.toList());

        chatService.batchDeleteMessages(user.getId(), messageIds);

        return ok("批量删除成功", null);
    }

    /**
     * 获取会话的所有消息（支持分页：limit / beforeId）
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        List<MessageResponse> messages = chatService.getMessages(user.getId(), conversationId, beforeId, limit);

        return ok(null, messages);
    }

    /**
     * 发送消息（同步）
     */
    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody MessageRequest request) {
        MessageResponse response = chatService.sendMessage(user.getId(), request);

        return ok("消息发送成功", response);
    }

    /**
     * 流式发送消息（SSE）
     * 正常内容使用 message 事件，异常使用 error 事件，前端不再需要通过内容前缀猜测错误。
     */
    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamMessage(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody MessageRequest request) {
        logger.info("streamMessage called, user={}, conversationId={}",
                user != null ? user.getUsername() : null,
                request.getConversationId());

        if (user == null) {
            return Flux.just(ServerSentEvent.<String>builder("用户未登录或Token无效").event("error").build());
        }

        return chatService.streamMessage(user.getId(), request)
                .doOnError(error -> logger.error("streamMessage controller error, user={}, conversationId={}",
                        user.getUsername(), request.getConversationId(), error))
                .map(content -> ServerSentEvent.<String>builder(content).event("message").build())
                .onErrorResume(error -> Flux.just(ServerSentEvent.<String>builder(errorMessage(error))
                        .event("error")
                        .build()));
    }

    private String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "服务异常，请稍后重试" : message;
    }

    private Long toMessageId(Object id) {
        if (id instanceof Long value) {
            return value;
        }
        if (id instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new BusinessException("无效的消息ID: " + value);
            }
        }
        if (id instanceof Number value) {
            return value.longValue();
        }
        throw new BusinessException("无效的消息ID格式");
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        if (message != null) {
            response.put("message", message);
        }
        if (data != null) {
            response.put("data", data);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 转换为 ConversationResponse
     */
    private ConversationResponse convertToConversationResponse(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getUserId(),
                conversation.getTitle(),
                conversation.getCreateTime(),
                conversation.getUpdateTime());
    }
}
