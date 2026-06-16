package com.example.springaichat.controller;

import com.example.springaichat.dto.BatchDeleteRequest;
import com.example.springaichat.dto.ConversationResponse;
import com.example.springaichat.dto.MessageRequest;
import com.example.springaichat.dto.MessageResponse;
import com.example.springaichat.entity.Conversation;
import com.example.springaichat.entity.User;
import com.example.springaichat.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聊天控制器
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
    public ResponseEntity<?> getConversations(@AuthenticationPrincipal User user) {
        try {
            // 检查用户是否已认证
            if (user == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "用户未登录或Token无效");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            List<Conversation> conversations = chatService.getConversations(user.getId());

            List<ConversationResponse> responseList = conversations.stream()
                    .map(this::convertToConversationResponse)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", responseList);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 创建新会话
     */
    @PostMapping("/conversations")
    public ResponseEntity<?> createConversation(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            String title = request != null ? request.get("title") : null;
            Conversation conversation = chatService.createConversation(user.getId(), title);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "会话创建成功");
            response.put("data", convertToConversationResponse(conversation));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取单个会话详情
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<?> getConversation(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId) {
        try {
            Conversation conversation = chatService.getConversation(user.getId(), conversationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", convertToConversationResponse(conversation));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<?> deleteConversation(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId) {
        try {
            chatService.deleteConversation(user.getId(), conversationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "会话删除成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(
            @AuthenticationPrincipal User user,
            @PathVariable Long messageId) {
        try {
            chatService.deleteMessage(user.getId(), messageId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "消息删除成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 批量删除消息
     */
    @PostMapping("/messages/batch-delete")
    public ResponseEntity<?> batchDeleteMessages(
            @AuthenticationPrincipal User user,
            @RequestBody BatchDeleteRequest request) {
        logger.info("批量删除请求 - 用户ID: {}, 请求: {}", user != null ? user.getId() : "null", request);

        try {
            List<Object> rawMessageIds = request.getMessageIds();
            logger.info("原始消息ID列表: {}", rawMessageIds);

            if (rawMessageIds == null || rawMessageIds.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "请选择要删除的消息");
                return ResponseEntity.badRequest().body(response);
            }

            List<Long> messageIds = rawMessageIds.stream()
                    .map(id -> {
                        if (id instanceof Long) {
                            return (Long) id;
                        } else if (id instanceof String) {
                            return Long.parseLong((String) id);
                        } else if (id instanceof Number) {
                            return ((Number) id).longValue();
                        }
                        throw new RuntimeException("无效的消息ID格式: " + id);
                    })
                    .collect(Collectors.toList());

            logger.info("转换后的消息ID列表: {}", messageIds);

            chatService.batchDeleteMessages(user.getId(), messageIds);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "批量删除成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("批量删除失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取会话的所有消息
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<?> getMessages(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId) {
        try {
            List<MessageResponse> messages = chatService.getMessages(user.getId(), conversationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", messages);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 发送消息
     */
    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal User user,
            @RequestBody MessageRequest request) {
        try {
            MessageResponse response = chatService.sendMessage(user.getId(), request);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "消息发送成功");
            result.put("data", response);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 流式发送消息（SSE）
     * 使用 Server-Sent Events 返回实时响应
     */
    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamMessage(
            @AuthenticationPrincipal User user,
            @RequestBody MessageRequest request) {
        logger.info("streamMessage called, user={}, conversationId={}",
                user != null ? user.getUsername() : null,
                request != null ? request.getConversationId() : null);
        // 检查用户是否已认证
        if (user == null) {
            logger.warn("streamMessage rejected because authentication principal is null");
            return Flux.just("{\"error\": \"用户未登录或Token无效\"}");
        }

        return chatService.streamMessage(user.getId(), request)
                .doOnError(error -> {
                    logger.error("streamMessage controller error, user={}, conversationId={}",
                            user.getUsername(),
                            request.getConversationId(),
                            error);
                })
                .onErrorResume(error -> Flux.just("{\"error\": \"" + escapeJson(error.getMessage()) + "\"}"))
                .doOnComplete(() -> {
                    // 流结束时发送完成信号
                });
    }

    /**
     * 流式发送消息（JSON Lines 格式）
     * 适合前端更容易解析的格式
     */
    @PostMapping(value = "/messages/stream/json", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<String> streamMessageJson(
            @AuthenticationPrincipal User user,
            @RequestBody MessageRequest request) {
        return chatService.streamMessage(user.getId(), request)
                .map(content -> "{\"content\": \"" + escapeJson(content) + "\"}")
                .doOnError(error -> {
                    // 发送错误信息
                });
    }

    /**
     * JSON 转义
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
