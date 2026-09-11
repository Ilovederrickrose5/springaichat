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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    @Value("${chat.max-tokens:4096}")
    private int maxTokens;

    @Value("${chat.cache-expire-hours:24}")
    private int cacheExpireHours;

    @Value("${rag.enabled:true}")
    private boolean ragEnabled;

    @Value("${rag.retrieval.top-k:3}")
    private int ragTopK;

    @Value("${rag.retrieval.similarity-threshold:0.45}")
    private double ragSimilarityThreshold;

    @Autowired(required = false)
    private VectorStore vectorStore;

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
        com.example.springaichat.entity.Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("消息不存在"));

        Long conversationId = message.getConversationId();
        getConversation(userId, conversationId);

        messageRepository.delete(message);

        // 清理 Redis 缓存，下次访问时从数据库重新加载
        deleteChatHistory(conversationId);
    }

    /**
     * 批量删除消息
     */
    @Transactional
    public void batchDeleteMessages(Long userId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new RuntimeException("消息ID列表不能为空");
        }

        List<com.example.springaichat.entity.Message> messages = messageRepository.findAllById(messageIds);

        if (messages.isEmpty()) {
            throw new RuntimeException("未找到任何消息");
        }

        Long conversationId = messages.get(0).getConversationId();
        getConversation(userId, conversationId);

        for (com.example.springaichat.entity.Message message : messages) {
            if (!message.getConversationId().equals(conversationId)) {
                throw new RuntimeException("所有消息必须属于同一个会话");
            }
        }

        messageRepository.deleteAll(messages);

        // 清理 Redis 缓存，下次访问时从数据库重新加载
        deleteChatHistory(conversationId);
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
     * 获取或创建聊天历史（从 Redis 缓存读取，缓存未命中时从数据库加载并预热缓存）
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
        }

        // Redis 缓存不存在或读取失败，从数据库加载历史
        List<Message> historyFromDb = loadChatHistoryFromDb(conversationId);

        // 如果数据库有历史记录，预热到 Redis
        if (!historyFromDb.isEmpty()) {
            saveChatHistory(conversationId, historyFromDb);
        }

        return historyFromDb;
    }

    /**
     * 从数据库加载聊天历史
     */
    private List<Message> loadChatHistoryFromDb(Long conversationId) {
        List<Message> history = new ArrayList<>();

        try {
            List<com.example.springaichat.entity.Message> dbMessages = messageRepository
                    .findByConversationIdOrderByCreateTimeAsc(conversationId);

            for (com.example.springaichat.entity.Message dbMsg : dbMessages) {
                if ("user".equals(dbMsg.getRole())) {
                    history.add(new UserMessage(dbMsg.getContent()));
                } else if ("assistant".equals(dbMsg.getRole())) {
                    history.add(new AssistantMessage(dbMsg.getContent()));
                }
            }

            // 限制历史数量
            trimChatHistory(history);
        } catch (Exception e) {
            // 数据库读取失败，返回空列表
        }

        return history;
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
     * 从 Redis 缓存删除聊天历史（带重试机制）
     */
    private void deleteChatHistory(Long conversationId) {
        String cacheKey = CHAT_HISTORY_KEY_PREFIX + conversationId;
        int maxRetries = 2;
        int retryDelayMs = 100;

        for (int i = 0; i <= maxRetries; i++) {
            try {
                redisTemplate.delete(cacheKey);
                if (i > 0) {
                    logger.info("Redis cache delete succeeded after {} retries, key={}", i, cacheKey);
                }
                return;
            } catch (Exception e) {
                if (i < maxRetries) {
                    logger.warn("Redis cache delete attempt {} failed, key={}, retrying...", i + 1, cacheKey);
                    try {
                        Thread.sleep(retryDelayMs * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    logger.error("Redis cache delete failed after {} attempts, key={}", maxRetries + 1, cacheKey, e);
                    // 删除失败不影响主流程，缓存会通过以下方式自动恢复：
                    // 1. 24小时自动过期
                    // 2. 下次读时如果Redis失败，从数据库重新加载并覆盖缓存
                    // 3. 下次写操作会覆盖缓存
                }
            }
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
     * 优化首字响应：将非关键步骤异步化
     */
    public Flux<String> streamMessage(Long userId, MessageRequest request) {
        logger.info("streamMessage service start, userId={}, conversationId={}, contentLength={}",
                userId,
                request != null ? request.getConversationId() : null,
                request != null && request.getContent() != null ? request.getContent().length() : null);

        if (request.getContent() != null && request.getContent().length() > maxMessageLength) {
            return Flux.error(new RuntimeException("消息长度不能超过" + maxMessageLength + "个字符"));
        }

        Long conversationId = request.getConversationId();

        if (conversationId == null) {
            Conversation newConversation = createConversation(userId, null);
            conversationId = newConversation.getId();
        } else {
            getConversation(userId, conversationId);
        }

        final Long finalConversationId = conversationId;
        final String userContent = request.getContent();

        // 异步保存用户消息，不阻塞首字响应
        CompletableFuture.runAsync(() -> {
            try {
                saveUserMessageAsync(finalConversationId, userContent);
            } catch (Exception e) {
                logger.error("Failed to save user message asynchronously", e);
            }
        });

        // 异步更新会话标题，不阻塞首字响应
        CompletableFuture.runAsync(() -> {
            try {
                updateConversationTitleAsync(finalConversationId, userContent);
            } catch (Exception e) {
                logger.error("Failed to update conversation title asynchronously", e);
            }
        });

        // 立即获取缓存并调用模型，尽快返回首字
        List<Message> chatHistory = getOrCreateChatHistory(finalConversationId);

        return streamAiResponse(chatHistory, userContent, finalConversationId);
    }

    /**
     * 异步保存用户消息
     */
    @Transactional
    public void saveUserMessageAsync(Long conversationId, String content) {
        com.example.springaichat.entity.Message userMessage = new com.example.springaichat.entity.Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(content);
        messageRepository.save(userMessage);
        logger.debug("User message saved asynchronously, conversationId={}", conversationId);
    }

    /**
     * 异步更新会话标题
     */
    @Transactional
    public void updateConversationTitleAsync(Long conversationId, String userContent) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            String newTitle = generateTitle(userContent, conversation.getTitle());
            if (!newTitle.equals(conversation.getTitle())) {
                conversation.setTitle(newTitle);
                conversationRepository.save(conversation);
                logger.debug("Conversation title updated asynchronously, conversationId={}, title={}", conversationId,
                        newTitle);
            }
        });
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
     * 流式调用AI模型（带重试）
     */
    private Flux<String> streamAiResponse(List<Message> chatHistory,
            String userContent, Long conversationId) {
        int maxRetries = 2;
        java.util.concurrent.atomic.AtomicInteger retryCount = new java.util.concurrent.atomic.AtomicInteger(0);

        return doStreamAiResponse(chatHistory, userContent, conversationId)
                .onErrorResume(error -> {
                    int currentRetry = retryCount.incrementAndGet();
                    if (currentRetry <= maxRetries && isRetryableError(error)) {
                        logger.warn("streamAiResponse retry {}/{}, conversationId={}",
                                currentRetry, maxRetries, conversationId);
                        return doStreamAiResponse(chatHistory, userContent, conversationId);
                    }
                    logger.error("streamAiResponse failed after {} retries, conversationId={}",
                            currentRetry, conversationId, error);
                    return Flux.error(error);
                });
    }

    /**
     * 实际执行流式调用
     */
    private Flux<String> doStreamAiResponse(List<Message> chatHistory,
            String userContent, Long conversationId) {
        StringBuilder fullResponse = new StringBuilder();

        Flux<String> responseFlux = chatClient.prompt()
                .messages(buildMessages(chatHistory, userContent))
                .stream()
                .content()
                .map(content -> {
                    fullResponse.append(content);
                    return content;
                })
                .doOnComplete(() -> {
                    logger.info("streamAiResponse completed, conversationId={}, responseLength={}",
                            conversationId, fullResponse.length());
                    saveAiResponse(conversationId, fullResponse.toString(), chatHistory, userContent);
                })
                .doOnError(error -> {
                    logger.error("streamAiResponse failed, conversationId=" + conversationId, error);
                    if (!chatHistory.isEmpty() && chatHistory.get(chatHistory.size() - 1) instanceof UserMessage) {
                        chatHistory.remove(chatHistory.size() - 1);
                    }
                });

        chatHistory.add(new UserMessage(userContent));

        return responseFlux;
    }

    /**
     * 判断是否为可重试的错误
     */
    private boolean isRetryableError(Throwable error) {
        String message = error.getMessage();
        return message != null && (message.contains("Connection reset") ||
                message.contains("connect timed out") ||
                message.contains("timeout") ||
                message.contains("429") ||
                message.contains("502") ||
                message.contains("503") ||
                message.contains("504"));
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
     * 构建消息列表（集成RAG检索）
     */
    private List<Message> buildMessages(List<Message> chatHistory, String userContent) {
        List<Message> messages = new ArrayList<>();

        String ragContext = "";
        if (ragEnabled && vectorStore != null) {
            ragContext = retrieveRagContext(userContent);
        }

        String systemMessageContent = buildSystemMessage(ragContext);
        messages.add(new SystemMessage(systemMessageContent));

        messages.addAll(chatHistory);
        messages.add(new UserMessage(userContent));

        return messages;
    }

    /**
     * 构建系统消息（包含RAG约束）
     */
    private String buildSystemMessage(String ragContext) {
        StringBuilder systemMessage = new StringBuilder();
        systemMessage.append("你是一个企业资产管理系统的智能助手。\n\n");

        if (ragEnabled) {
            systemMessage.append("回答必须完全基于以下参考资料：\n");
            systemMessage.append("------------------------\n");
            systemMessage.append(ragContext.isEmpty() ? "[暂无相关参考资料]" : ragContext);
            systemMessage.append("\n------------------------\n\n");
            systemMessage.append("如果参考资料中没有相关信息，请明确告知\"根据知识库内容，我无法回答这个问题。\"，禁止编造任何信息。\n");
            systemMessage.append("回答时要引用知识库中的具体内容作为依据。\n");
        } else {
            systemMessage.append("请用自然、友好的语言回答用户的问题。\n");
        }

        return systemMessage.toString();
    }

    /**
     * 从向量存储检索相关文档
     */
    private String retrieveRagContext(String query) {
        try {
            SearchRequest searchRequest = SearchRequest.query(query)
                    .withTopK(ragTopK)
                    .withSimilarityThreshold(ragSimilarityThreshold)
                    .withFilterExpression("tenant == 'asset'");

            List<Document> documents = vectorStore.similaritySearch(searchRequest);

            if (documents == null || documents.isEmpty()) {
                logger.debug("No RAG documents found for query: {}", query);
                return "";
            }

            logger.debug("RAG retrieved {} documents for query: {}", documents.size(), query);

            StringBuilder context = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                context.append("【参考资料").append(i + 1).append("】\n");
                context.append("来源：").append(doc.getMetadata().getOrDefault("source", "unknown")).append("\n");
                context.append("内容：").append(doc.getContent()).append("\n\n");
            }

            return context.toString();
        } catch (Exception e) {
            logger.warn("RAG retrieval failed: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 限制历史记录数量（同时考虑消息数量和Token数量）
     */
    private void trimChatHistory(List<Message> chatHistory) {
        // 先按消息数量限制
        while (chatHistory.size() > maxHistorySize) {
            if (chatHistory.size() > 1) {
                chatHistory.remove(1);
            } else {
                break;
            }
        }

        // 再按Token数量限制（中文约2字符=1token，英文约4字符=1token）
        while (chatHistory.size() > 1) {
            int totalTokens = estimateTokens(chatHistory);
            if (totalTokens <= maxTokens) {
                break;
            }
            chatHistory.remove(1);
        }
    }

    /**
     * 估算消息列表的Token数量
     */
    private int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            String content = msg.getContent();
            if (content != null) {
                // 粗略估算：中文按2字符=1token，英文按4字符=1token
                int chineseChars = content.replaceAll("[\\x00-\\xff]", "").length();
                int asciiChars = content.length() - chineseChars;
                total += (chineseChars / 2) + (asciiChars / 4) + 4; // 每条消息额外加4token（角色等元数据）
            }
        }
        return total;
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
