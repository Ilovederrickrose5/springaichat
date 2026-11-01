package com.example.springaichat.service;

import com.example.springaichat.dto.MessageRequest;
import com.example.springaichat.dto.MessageResponse;
import com.example.springaichat.entity.Conversation;
import com.example.springaichat.exception.BusinessException;
import com.example.springaichat.exception.ForbiddenException;
import com.example.springaichat.exception.NotFoundException;
import com.example.springaichat.repository.ConversationRepository;
import com.example.springaichat.repository.MessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final TaskExecutor chatTaskExecutor;

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

    @Value("${rag.retrieval.similarity-threshold:0.30}")
    private double ragSimilarityThreshold;

    @Value("${rag.knowledge.default-tenant:asset}")
    private String ragTenant;

    @Autowired(required = false)
    private VectorStore vectorStore;

    private static final String CHAT_HISTORY_KEY_PREFIX = "chat:history:";

    /**
     * 缓存专用轻量消息结构。Spring AI 的 Message 是接口且实现类没有无参构造，
     * Jackson 无法直接反序列化，之前缓存实际从未命中。
     */
    private static final class CachedMessage {
        public String role;
        public String content;

        CachedMessage() {
        }

        CachedMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public ChatService(ChatClient.Builder chatClientBuilder, ConversationRepository conversationRepository,
            MessageRepository messageRepository, RedisTemplate<String, Object> redisTemplate,
            @Qualifier("chatTaskExecutor") TaskExecutor chatTaskExecutor) {
        this.chatClient = chatClientBuilder.build();
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.chatTaskExecutor = chatTaskExecutor;
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
                .orElseThrow(() -> new NotFoundException("会话不存在"));

        if (!conversation.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该会话");
        }

        return conversation;
    }

    /**
     * 删除消息
     */
    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        com.example.springaichat.entity.Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        Long conversationId = message.getConversationId();
        getConversation(userId, conversationId);

        messageRepository.delete(message);

        // 事务提交后再清理 Redis 缓存，避免回滚后缓存被误删、也避免在事务内 sleep 重试
        deleteChatHistoryAfterCommit(conversationId);
    }

    /**
     * 批量删除消息
     */
    @Transactional
    public void batchDeleteMessages(Long userId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new BusinessException("消息ID列表不能为空");
        }

        List<com.example.springaichat.entity.Message> messages = messageRepository.findAllById(messageIds);

        if (messages.isEmpty()) {
            throw new NotFoundException("未找到任何消息");
        }

        Long conversationId = messages.get(0).getConversationId();
        getConversation(userId, conversationId);

        for (com.example.springaichat.entity.Message message : messages) {
            if (!message.getConversationId().equals(conversationId)) {
                throw new BusinessException("所有消息必须属于同一个会话");
            }
        }

        messageRepository.deleteAll(messages);

        // 事务提交后再清理 Redis 缓存，避免回滚后缓存被误删、也避免在事务内 sleep 重试
        deleteChatHistoryAfterCommit(conversationId);
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
        deleteChatHistoryAfterCommit(conversationId);
    }

    /**
     * 获取会话的所有消息
     */
    public List<MessageResponse> getMessages(Long userId, Long conversationId) {
        return getMessages(userId, conversationId, null, null);
    }

    /**
     * 分页获取消息。limit 为空时保持旧行为（返回全部），避免影响已有调用方。
     * 返回结果始终按时间正序，beforeId 用于向上加载更早的历史。
     */
    public List<MessageResponse> getMessages(Long userId, Long conversationId, Long beforeId, Integer limit) {
        // 验证会话归属
        getConversation(userId, conversationId);

        if (limit == null && beforeId == null) {
            return messageRepository.findByConversationIdOrderByIdAsc(conversationId)
                    .stream()
                    .map(this::convertToMessageResponse)
                    .collect(Collectors.toList());
        }

        int pageSize = Math.min(Math.max(limit == null ? 100 : limit, 1), 500);
        Pageable pageable = PageRequest.of(0, pageSize);
        List<com.example.springaichat.entity.Message> page = beforeId == null
                ? messageRepository.findByConversationIdOrderByIdDesc(conversationId, pageable)
                : messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(conversationId, beforeId, pageable);

        List<com.example.springaichat.entity.Message> ordered = new ArrayList<>(page);
        Collections.reverse(ordered);

        return ordered.stream()
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
                List<CachedMessage> cached = objectMapper.readValue(cachedObj.toString(),
                        new TypeReference<List<CachedMessage>>() {
                        });
                List<Message> history = toMessages(cached);
                if (!history.isEmpty()) {
                    // 缓存里可能是历史版本写入的未裁剪数据，命中时再裁剪一次
                    trimChatHistory(history);
                    return history;
                }
            }
        } catch (Exception e) {
            // Redis 读取失败，继续从数据库读取或创建新列表
            logger.warn("read chat history cache failed, conversationId={}, cause={}",
                    conversationId, e.toString());
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
            // 上下文最终只保留 maxHistorySize 条，没必要把整个会话的消息都读进内存。
            // 按 id 倒序取最近一页再反转；多取 2 条用于裁剪时对齐 user/assistant 边界。
            Pageable recentPage = PageRequest.of(0, Math.max(maxHistorySize + 2, 10));
            List<com.example.springaichat.entity.Message> dbMessages = new ArrayList<>(messageRepository
                    .findByConversationIdOrderByIdDesc(conversationId, recentPage));
            Collections.reverse(dbMessages);

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
            logger.warn("load chat history from db failed, conversationId={}, cause={}",
                    conversationId, e.toString());
        }

        return history;
    }

    /**
     * 保存聊天历史到 Redis 缓存
     */
    private void saveChatHistory(Long conversationId, List<Message> chatHistory) {
        String cacheKey = CHAT_HISTORY_KEY_PREFIX + conversationId;

        try {
            List<CachedMessage> cached = new ArrayList<>(chatHistory.size());
            for (Message message : chatHistory) {
                String role = resolveRole(message);
                if (role != null && message.getContent() != null) {
                    cached.add(new CachedMessage(role, message.getContent()));
                }
            }
            String historyJson = objectMapper.writeValueAsString(cached);
            redisTemplate.opsForValue().set(cacheKey, historyJson, Duration.ofHours(cacheExpireHours));
        } catch (Exception e) {
            // 缓存写入失败只丢缓存，不阻断数据库落库与流式响应
            logger.warn("saveChatHistory failed, conversationId={}, cause={}", conversationId, e.toString());
        }
    }

    private List<Message> toMessages(List<CachedMessage> cached) {
        List<Message> history = new ArrayList<>();
        if (cached == null) {
            return history;
        }
        for (CachedMessage message : cached) {
            if (message == null || message.content == null) {
                continue;
            }
            if ("user".equals(message.role)) {
                history.add(new UserMessage(message.content));
            } else if ("assistant".equals(message.role)) {
                history.add(new AssistantMessage(message.content));
            }
        }
        return history;
    }

    private String resolveRole(Message message) {
        MessageType type = message.getMessageType();
        if (type == MessageType.USER) {
            return "user";
        }
        if (type == MessageType.ASSISTANT) {
            return "assistant";
        }
        return null;
    }

    /**
     * 缓存删除放到事务提交之后执行：事务回滚时缓存保持原样，重试的 sleep 也不会占用数据库连接。
     */
    private void deleteChatHistoryAfterCommit(Long conversationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteChatHistory(conversationId);
                }
            });
        } else {
            deleteChatHistory(conversationId);
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
     * 发送消息并获取AI响应（同步方式）。
     * 不开启事务：模型调用可能持续数秒，包在事务里会长期占用数据库连接。
     */
    public MessageResponse sendMessage(Long userId, MessageRequest request) {
        validateRequest(request);

        Long conversationId = request.getConversationId();

        // 如果没有会话ID，创建新会话
        if (conversationId == null) {
            Conversation newConversation = createConversation(userId, null);
            conversationId = newConversation.getId();
        } else {
            // 验证会话归属
            getConversation(userId, conversationId);
        }

        // 先加载历史，再保存当前用户消息，避免当前消息在历史中重复出现
        List<Message> chatHistory = getOrCreateChatHistory(conversationId);

        // 保存用户消息
        com.example.springaichat.entity.Message userMessage = new com.example.springaichat.entity.Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(request.getContent());
        messageRepository.save(userMessage);

        // 调用AI模型获取响应
        String aiResponse = callAiModel(chatHistory, request.getContent());

        // 更新上下文历史
        chatHistory.add(new UserMessage(request.getContent()));
        chatHistory.add(new AssistantMessage(aiResponse));

        // 限制历史记录数量
        trimChatHistory(chatHistory);

        // 先落库：数据库是唯一数据源
        com.example.springaichat.entity.Message aiMessage = new com.example.springaichat.entity.Message();
        aiMessage.setConversationId(conversationId);
        aiMessage.setRole("assistant");
        aiMessage.setContent(aiResponse);
        messageRepository.save(aiMessage);

        // 再写缓存，缓存失败不影响主流程
        saveChatHistory(conversationId, chatHistory);

        // 更新会话的更新时间和标题
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setTitle(generateTitle(request.getContent(), conversation.getTitle()));
            conversation.setUpdateTime(LocalDateTime.now());
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

        try {
            validateRequest(request);
        } catch (RuntimeException e) {
            return Flux.error(e);
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

        // 先同步读取历史，再落库用户消息：保证模型入参里的历史不包含当前这条消息
        List<Message> chatHistory = getOrCreateChatHistory(finalConversationId);

        // 单条本地 INSERT 是毫秒级操作，同步落库可以保证“问题一定存在、顺序稳定”，
        // 相对于模型首字延迟可以忽略
        saveUserMessage(finalConversationId, userContent);

        // 非关键步骤异步：使用专用线程池，避免阻塞 ForkJoinPool.commonPool()
        scheduleTitleUpdate(finalConversationId, userContent);

        return streamAiResponse(chatHistory, userContent, finalConversationId);
    }

    private void validateRequest(MessageRequest request) {
        if (request == null) {
            throw new BusinessException("请求不能为空");
        }
        String content = request.getContent();
        if (content == null || content.isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }
        if (content.length() > maxMessageLength) {
            throw new BusinessException("消息长度不能超过" + maxMessageLength + "个字符");
        }
    }

    private void saveUserMessage(Long conversationId, String content) {
        com.example.springaichat.entity.Message userMessage = new com.example.springaichat.entity.Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(content);
        messageRepository.save(userMessage);
        logger.debug("User message saved, conversationId={}", conversationId);
    }

    /**
     * 异步更新会话标题与更新时间。标题只在首条消息时生成，但 updateTime 每轮都要刷新，
     * 否则会话列表按 updateTime 排序时不会把最近聊过的会话顶到前面。
     */
    private void scheduleTitleUpdate(Long conversationId, String userContent) {
        try {
            chatTaskExecutor.execute(() -> {
                try {
                    updateConversationTitle(conversationId, userContent);
                } catch (Exception e) {
                    logger.error("Failed to update conversation title asynchronously", e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池满时降级为同步执行：标题更新不是关键路径，但不能因此丢更新
            logger.warn("chatTaskExecutor rejected title update, running inline, conversationId={}", conversationId);
            updateConversationTitle(conversationId, userContent);
        }
    }

    private void updateConversationTitle(Long conversationId, String userContent) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            String newTitle = generateTitle(userContent, conversation.getTitle());
            if (!newTitle.equals(conversation.getTitle())) {
                conversation.setTitle(newTitle);
            }
            conversation.setUpdateTime(LocalDateTime.now());
            conversationRepository.save(conversation);
            logger.debug("Conversation touched, conversationId={}, title={}", conversationId, newTitle);
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
        // 累计已向前端发出的字符数：一旦有内容发出就不再自动重试，避免重复内容拼接
        java.util.concurrent.atomic.AtomicInteger emittedChars = new java.util.concurrent.atomic.AtomicInteger(0);
        // RAG 检索（含一次 Embedding 网络调用）延迟到订阅时执行，且只做一次；
        // 重试时复用同一份请求消息，避免重复 Embedding 调用
        java.util.concurrent.atomic.AtomicReference<List<Message>> requestMessagesRef =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.function.Supplier<Flux<String>> attempt = () -> {
            List<Message> requestMessages = requestMessagesRef.get();
            if (requestMessages == null) {
                requestMessages = buildMessages(chatHistory, userContent);
                requestMessagesRef.set(requestMessages);
            }
            return doStreamAiResponse(requestMessages, chatHistory, userContent, conversationId, emittedChars);
        };

        // subscribeOn：把向量检索等阻塞调用从 Servlet 请求线程移到弹性线程池
        return Flux.defer(attempt)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    int currentRetry = retryCount.incrementAndGet();
                    boolean retryable = currentRetry <= maxRetries && isRetryableError(error);
                    if (!retryable) {
                        logger.error("streamAiResponse failed after {} attempts, conversationId={}",
                                currentRetry, conversationId, error);
                        return Flux.error(error);
                    }
                    if (emittedChars.get() > 0) {
                        logger.warn("streamAiResponse skip retry: {} chars already emitted, conversationId={}",
                                emittedChars.get(), conversationId, error);
                        return Flux.error(error);
                    }
                    logger.warn("streamAiResponse retry {}/{}, conversationId={}",
                            currentRetry, maxRetries, conversationId);
                    Flux<String> retryFlux = Flux.defer(attempt);
                    // 限流与服务端错误加退避，网络类错误保持立即重试
                    return isBackoffError(error) ? retryFlux.delaySubscription(Duration.ofMillis(400)) : retryFlux;
                });
    }

    /**
     * 实际执行流式调用。不修改 chatHistory，重试时不会重复追加用户消息。
     */
    private Flux<String> doStreamAiResponse(List<Message> requestMessages,
            List<Message> chatHistory, String userContent, Long conversationId,
            java.util.concurrent.atomic.AtomicInteger emittedChars) {
        StringBuilder fullResponse = new StringBuilder();
        AtomicBoolean persisted = new AtomicBoolean(false);

        return chatClient.prompt()
                .messages(requestMessages)
                .stream()
                .content()
                // 落库/写缓存是阻塞操作，切到弹性线程池，避免占用 Reactor Netty 的事件循环
                .publishOn(Schedulers.boundedElastic())
                .map(content -> {
                    fullResponse.append(content);
                    emittedChars.addAndGet(content.length());
                    return content;
                })
                .doOnComplete(() -> {
                    logger.info("streamAiResponse completed, conversationId={}, responseLength={}",
                            conversationId, fullResponse.length());
                    persistResponseOnce(persisted, conversationId, fullResponse, chatHistory, userContent);
                })
                .doOnError(error -> logger.error(
                        "streamAiResponse failed, conversationId=" + conversationId, error))
                .doFinally(signal -> {
                    // 客户端断开 / 上游异常时也把已经生成的部分回答落库，避免刷新后内容消失
                    if (signal != SignalType.ON_COMPLETE) {
                        persistResponseOnce(persisted, conversationId, fullResponse, chatHistory, userContent);
                    }
                });
    }

    private void persistResponseOnce(AtomicBoolean persisted, Long conversationId, StringBuilder fullResponse,
            List<Message> chatHistory, String userContent) {
        if (fullResponse.length() == 0 || !persisted.compareAndSet(false, true)) {
            return;
        }
        try {
            saveAiResponse(conversationId, fullResponse.toString(), chatHistory, userContent);
        } catch (Exception e) {
            logger.error("Failed to save AI response, conversationId={}", conversationId, e);
        }
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
     * 判断是否需要退避后重试：仅限流（429）与服务端 5xx，网络类错误立即重试
     */
    private boolean isBackoffError(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("429") || message.matches("(?s).*\\b5\\d\\d\\b.*");
    }

    /**
     * 保存AI响应到数据库
     */
    private void saveAiResponse(Long conversationId, String aiContent,
            List<Message> chatHistory, String userContent) {
        // 在副本上追加本轮问答，避免流式重试污染共享的历史列表
        List<Message> updatedHistory = new ArrayList<>(chatHistory);
        updatedHistory.add(new UserMessage(userContent));
        updatedHistory.add(new AssistantMessage(aiContent));
        trimChatHistory(updatedHistory);

        // 先落库：数据库是唯一数据源，不能被缓存故障阻断
        com.example.springaichat.entity.Message aiMessage = new com.example.springaichat.entity.Message();
        aiMessage.setConversationId(conversationId);
        aiMessage.setRole("assistant");
        aiMessage.setContent(aiContent);
        messageRepository.save(aiMessage);

        // 再写缓存，失败只丢缓存
        saveChatHistory(conversationId, updatedHistory);
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
            systemMessage.append("请参考以下内部资料回答用户问题：\n");
            systemMessage.append("------------------------\n");
            systemMessage.append(ragContext.isEmpty() ? "[暂无相关内部资料]" : ragContext);
            systemMessage.append("\n------------------------\n\n");
            systemMessage.append("要求：\n");
            systemMessage.append("1. 直接把答案讲清楚，不要提及“参考资料”“资料编号”“知识库”“来源”等字眼，也不要输出【参考资料1】这类引用标记。\n");
            systemMessage.append("2. 答案必须以上述内部资料为依据，禁止编造资料中没有的信息。\n");
            systemMessage.append("3. 如果内部资料中确实没有相关信息，直接说明“暂时没有查到相关内容”，并简要说明可以补充的方向。\n");
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
            // 多取一些候选再做租户过滤，避免过滤后命中数不足
            SearchRequest searchRequest = SearchRequest.query(query)
                    .withTopK(Math.max(ragTopK, ragTopK * 3))
                    .withSimilarityThreshold(ragSimilarityThreshold);

            List<Document> documents = vectorStore.similaritySearch(searchRequest);

            if (documents == null || documents.isEmpty()) {
                logger.debug("No RAG documents found for query: {}", query);
                return "";
            }

            // 当前 Redis 索引未把 tenant 设为可检索字段，因此在应用层做租户隔离；
            // 缺少 tenant 元数据的历史分块按默认租户处理，保持向后兼容
            List<Document> matched = new ArrayList<>();
            for (Document document : documents) {
                Object tenant = document.getMetadata().get("tenant");
                String documentTenant = tenant == null ? ragTenant : tenant.toString();
                if (ragTenant.equals(documentTenant)) {
                    matched.add(document);
                    if (matched.size() >= ragTopK) {
                        break;
                    }
                }
            }

            if (matched.isEmpty()) {
                logger.debug("No RAG documents matched tenant={} for query: {}", ragTenant, query);
                return "";
            }

            logger.debug("RAG retrieved {} documents for query: {}", matched.size(), query);

            StringBuilder context = new StringBuilder();
            for (Document document : matched) {
                context.append(document.getContent()).append("\n\n");
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
        if (chatHistory == null || chatHistory.isEmpty()) {
            return;
        }

        // 只有列表首位真正是 SystemMessage 时才保护它；
        // 从缓存/数据库加载的历史首位是普通消息，不能按旧逻辑跳过 index 0。
        int prefix = isSystemMessage(chatHistory.get(0)) ? 1 : 0;

        // 1. 按消息数量上限裁剪，从最旧的一条开始删
        while (chatHistory.size() - prefix > maxHistorySize) {
            chatHistory.remove(prefix);
        }

        // 2. 按 Token 上限裁剪：总量只算一次，之后逐条递减，避免 O(n²) 重复估算
        int totalTokens = estimateTokens(chatHistory);
        while (chatHistory.size() - prefix > 1 && totalTokens > maxTokens) {
            totalTokens -= estimateMessageTokens(chatHistory.get(prefix));
            chatHistory.remove(prefix);
        }

        // 3. 裁剪后必须以用户消息开头，否则会留下没有对应提问的孤立回答
        while (chatHistory.size() - prefix > 0 && !isUserMessage(chatHistory.get(prefix))) {
            chatHistory.remove(prefix);
        }
    }

    private boolean isSystemMessage(Message message) {
        return message.getMessageType() == MessageType.SYSTEM;
    }

    private boolean isUserMessage(Message message) {
        return message.getMessageType() == MessageType.USER;
    }

    /**
     * 估算消息列表的Token数量
     */
    private int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            total += estimateMessageTokens(msg);
        }
        return total;
    }

    private int estimateMessageTokens(Message message) {
        String content = message.getContent();
        if (content == null) {
            return 0;
        }
        // 粗略估算：中文按2字符=1token，英文按4字符=1token
        int chineseChars = content.replaceAll("[\\x00-\\xff]", "").length();
        int asciiChars = content.length() - chineseChars;
        // 每条消息额外加4token（角色等元数据）
        return (chineseChars / 2) + (asciiChars / 4) + 4;
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
