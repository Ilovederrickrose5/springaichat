package com.example.springaichat.service;

import com.example.springaichat.dto.MessageRequest;
import com.example.springaichat.dto.MessageResponse;
import com.example.springaichat.entity.Conversation;
import com.example.springaichat.repository.ConversationRepository;
import com.example.springaichat.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天服务
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${chat.max-history-size:20}")
    private int maxHistorySize;

    @Value("${chat.max-message-length:4000}")
    private int maxMessageLength;

    @Value("${chat.cache-expire-hours:24}")
    private int cacheExpireHours;

    // Redis 缓存键前缀
    private static final String CHAT_HISTORY_KEY_PREFIX = "chat:history:";

    public ChatService(ChatClient.Builder chatClientBuilder, ConversationRepository conversationRepository,
            MessageRepository messageRepository, RedisTemplate<String, Object> redisTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 创建新会话
     */
    @Transactional
    public Conversation createConversation(Long userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title != null && !title.isEmpty() ? title : "新对话");

        return conversationRepository.save(conversation);
    }

    /**
     * 获取用户的所有会话
     */
    public List<Conversation> getConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdateTimeDesc(userId);
    }

    /**
     * 获取单个会话
     */
    public Conversation getConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));

        if (!conversation.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该会话");
        }

        return conversation;
    }

    /**
     * 删除消息
     */
    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("消息不存在"));

        Conversation conversation = getConversation(userId, message.getConversationId());

        messageRepository.deleteById(messageId);
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = getConversation(userId, conversationId);

        // 删除会话的所有消息
        messageRepository.deleteByConversationId(conversationId);

        // 删除会话
        conversationRepository.delete(conversation);

        // 清理 Redis 缓存
        deleteChatHistory(conversationId);
    }

    /**
     * 获取会话的所有消息
     */
    public List<MessageResponse> getMessages(Long userId, Long conversationId) {
        // 验证会话归属
        getConversation(userId, conversationId);

        return messageRepository.findByConversationIdOrderByCreateTimeAsc(conversationId)
                .stream()
                .map(this::convertToMessageResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取或创建聊天历史（从 Redis 缓存读取）
     */
    private List<Message> getOrCreateChatHistory(Long conversationId) {
        String cacheKey = CHAT_HISTORY_KEY_PREFIX + conversationId;

        try {
            // 尝试从 Redis 获取缓存
            Object cachedObj = redisTemplate.opsForValue().get(cacheKey);
            if (cachedObj != null) {
                String cachedJson = cachedObj.toString();
                List<Message> history = objectMapper.readValue(cachedJson,
                        new TypeReference<List<Message>>() {
                        });
                if (history != null && !history.isEmpty()) {
                    return history;
                }
            }
        } catch (Exception e) {
            // Redis 读取失败，继续从数据库读取或创建新列表
            // 不抛异常，保证服务可用性
        }

        // Redis 缓存不存在或读取失败，返回空列表（会从数据库加载历史）
        return new ArrayList<>();
    }

    /**
     * 保存聊天历史到 Redis 缓存
     */
    private void saveChatHistory(Long conversationId, List<Message> chatHistory) {
        String cacheKey = CHAT_HISTORY_KEY_PREFIX + conversationId;

        try {
            String historyJson = objectMapper.writeValueAsString(chatHistory);
            redisTemplate.opsForValue().set(cacheKey, historyJson, Duration.ofHours(cacheExpireHours));
        } catch (JsonProcessingException e) {
            // Redis 写入失败，不抛异常，保证服务可用性
        }
    }

    /**
     * 从 Redis 缓存删除聊天历史
     */
    private void deleteChatHistory(Long conversationId) {
        String cacheKey = CHAT_HISTORY_KEY_PREFIX + conversationId;
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            // Redis 删除失败，不抛异常
        }
    }

    /**
     * 发送消息并获取AI响应（同步方式）
     */
    @Transactional
    public MessageResponse sendMessage(Long userId, MessageRequest request) {
        // 校验消息长度
        if (request.getContent() != null && request.getContent().length() > maxMessageLength) {
            throw new RuntimeException("消息长度不能超过" + maxMessageLength + "个字符");
        }

        Long conversationId = request.getConversationId();

        // 如果没有会话ID，创建新会话
        if (conversationId == null) {
            Conversation newConversation = createConversation(userId, null);
            conversationId = newConversation.getId();
        } else {
            // 验证会话归属
            getConversation(userId, conversationId);
        }

        // 保存用户消息
        com.example.springaichat.entity.Message userMessage = new com.example.springaichat.entity.Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(request.getContent());
        messageRepository.save(userMessage);

        // 获取或创建聊天历史
        List<Message> chatHistory = getOrCreateChatHistory(conversationId);

        // 调用AI模型获取响应
        String aiResponse = callAiModel(chatHistory, request.getContent());

        // 更新上下文历史
        chatHistory.add(new UserMessage(request.getContent()));
        chatHistory.add(new AssistantMessage(aiResponse));

        // 限制历史记录数量
        trimChatHistory(chatHistory);

        // 保存到 Redis 缓存
        saveChatHistory(conversationId, chatHistory);

        // 保存AI响应
        com.example.springaichat.entity.Message aiMessage = new com.example.springaichat.entity.Message();
        aiMessage.setConversationId(conversationId);
        aiMessage.setRole("assistant");
        aiMessage.setContent(aiResponse);
        messageRepository.save(aiMessage);

        // 更新会话的更新时间和标题
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setTitle(generateTitle(request.getContent(), conversation.getTitle()));
            conversationRepository.save(conversation);
        });

        return convertToMessageResponse(aiMessage);
    }

    /**
     * 发送消息并流式返回AI响应（用于SSE）
     */
    @Transactional
    public Flux<String> streamMessage(Long userId, MessageRequest request) {
        logger.info("streamMessage service start, userId={}, conversationId={}, contentLength={}",
                userId,
                request != null ? request.getConversationId() : null,
                request != null && request.getContent() != null ? request.getContent().length() : null);
        // 校验消息长度
        if (request.getContent() != null && request.getContent().length() > maxMessageLength) {
            throw new RuntimeException("消息长度不能超过" + maxMessageLength + "个字符");
        }

        Long conversationId = request.getConversationId();

        // 如果没有会话ID，创建新会话
        if (conversationId == null) {
            Conversation newConversation = createConversation(userId, null);
            conversationId = newConversation.getId();
        } else {
            // 验证会话归属
            getConversation(userId, conversationId);
        }

        // 保存用户消息
        com.example.springaichat.entity.Message userMessage = new com.example.springaichat.entity.Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(request.getContent());
        messageRepository.save(userMessage);

        // 获取或创建聊天历史
        List<Message> chatHistory = getOrCreateChatHistory(conversationId);

        // 更新会话标题（如果需要）
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setTitle(generateTitle(request.getContent(), conversation.getTitle()));
            conversationRepository.save(conversation);
        });

        // 使用流式调用
        return streamAiResponse(chatHistory, request.getContent(), conversationId);
    }

    /**
     * 调用AI模型
     */
    private String callAiModel(List<Message> chatHistory, String userContent) {
        return chatClient.prompt()
                .messages(buildMessages(chatHistory, userContent))
                .call()
                .content();
    }

    /**
     * 流式调用AI模型
     */
    private Flux<String> streamAiResponse(List<Message> chatHistory,
            String userContent, Long conversationId) {
        StringBuilder fullResponse = new StringBuilder();

        // 添加用户消息到历史
        chatHistory.add(new UserMessage(userContent));

        return chatClient.prompt()
                .messages(buildMessages(new ArrayList<>(), userContent))
                .stream()
                .content()
                .map(content -> {
                    fullResponse.append(content);
                    return content;
                })
                .doOnComplete(() -> {
                    // 流结束后保存完整响应
                    logger.info("streamAiResponse completed, conversationId={}, responseLength={}",
                            conversationId, fullResponse.length());
                    saveAiResponse(conversationId, fullResponse.toString(), chatHistory, userContent);
                })
                .doOnError(error -> {
                    logger.error("streamAiResponse failed, conversationId=" + conversationId, error);
                    // 发生错误时清理
                    if (!chatHistory.isEmpty() && chatHistory.get(chatHistory.size() - 1) instanceof UserMessage) {
                        chatHistory.remove(chatHistory.size() - 1);
                    }
                });
    }

    /**
     * 保存AI响应到数据库
     */
    private void saveAiResponse(Long conversationId, String aiContent,
            List<Message> chatHistory, String userContent) {
        // 添加AI响应到历史
        chatHistory.add(new AssistantMessage(aiContent));

        // 限制历史记录数量
        trimChatHistory(chatHistory);

        // 保存到 Redis 缓存
        saveChatHistory(conversationId, chatHistory);

        // 保存AI响应到数据库
        com.example.springaichat.entity.Message aiMessage = new com.example.springaichat.entity.Message();
        aiMessage.setConversationId(conversationId);
        aiMessage.setRole("assistant");
        aiMessage.setContent(aiContent);
        messageRepository.save(aiMessage);
    }

    /**
     * 构建消息列表
     */
    private List<Message> buildMessages(
            List<Message> chatHistory, String userContent) {
        List<Message> messages = new ArrayList<>();

        // 添加系统消息
        messages.add(new SystemMessage("你是一个智能助手，善于回答各种问题。请用自然、友好的语言回答用户的问题。"));

        // 添加历史消息
        messages.addAll(chatHistory);

        // 添加当前用户消息
        messages.add(new UserMessage(userContent));

        return messages;
    }

    /**
     * 限制历史记录数量
     */
    private void trimChatHistory(List<Message> chatHistory) {
        while (chatHistory.size() > maxHistorySize) {
            if (chatHistory.size() > 1) {
                chatHistory.remove(1); // 保留系统消息
            } else {
                break;
            }
        }
    }

    /**
     * 生成会话标题
     */
    private String generateTitle(String userMessage, String currentTitle) {
        // 如果已经有自定义标题，保持不变
        if (currentTitle != null && !currentTitle.equals("新对话")) {
            return currentTitle;
        }

        // 使用用户的第一句话作为标题（最多30个字符）
        String title = userMessage.trim();
        if (title.length() > 30) {
            title = title.substring(0, 30) + "...";
        }
        return title;
    }

    /**
     * 转换为 MessageResponse
     */
    private MessageResponse convertToMessageResponse(com.example.springaichat.entity.Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getRole(),
                message.getContent(),
                message.getCreateTime());
    }
}
