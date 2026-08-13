package com.example.springaichat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class KnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final VectorStore vectorStore;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${rag.knowledge.directory:knowledge/}")
    private String knowledgeDirectory;

    @Value("${rag.chunk.max-size:800}")
    private int chunkMaxSize;

    @Value("${rag.chunk.overlap-size:200}")
    private int chunkOverlapSize;

    private static final String PROCESSED_FILES_KEY = "rag:processed_files";

    public KnowledgeBaseService(VectorStore vectorStore, RedisTemplate<String, Object> redisTemplate) {
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public ApplicationRunner knowledgeBaseLoader() {
        return args -> {
            logger.info("Starting knowledge base loading...");
            long startTime = System.currentTimeMillis();

            try {
                loadKnowledgeBase();
                long duration = System.currentTimeMillis() - startTime;
                logger.info("Knowledge base loading completed in {}ms", duration);
            } catch (Exception e) {
                logger.error("Knowledge base loading failed", e);
            }
        };
    }

    public void loadKnowledgeBase() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:" + knowledgeDirectory + "**/*.md");

        if (resources.length == 0) {
            logger.info("No markdown files found in classpath:{}", knowledgeDirectory);
            return;
        }

        List<Document> allDocuments = new ArrayList<>();

        for (Resource resource : resources) {
            String fileName = resource.getFilename();
            String filePath = resource.getURI().getPath();

            if (isFileAlreadyProcessed(filePath)) {
                logger.info("Skipping already processed file: {}", fileName);
                continue;
            }

            logger.info("Processing file: {}", fileName);

            List<Document> documents = processFile(resource, fileName);
            allDocuments.addAll(documents);

            markFileAsProcessed(filePath);
        }

        if (!allDocuments.isEmpty()) {
            vectorStore.add(allDocuments);
            logger.info("Added {} document chunks to vector store", allDocuments.size());
        }
    }

    private List<Document> processFile(Resource resource, String fileName) throws IOException {
        TextReader textReader = new TextReader(resource);
        List<Document> documents = textReader.get();

        if (documents.isEmpty()) {
            return new ArrayList<>();
        }

        Document originalDoc = documents.get(0);

        TextSplitter splitter = new TokenTextSplitter(chunkMaxSize, chunkOverlapSize, 5, 10000, true);
        List<Document> chunks = splitter.split(List.of(originalDoc));

        List<Document> enrichedChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("tenant", "asset");
            metadata.put("source", fileName);
            metadata.put("chunk_index", i);
            metadata.put("total_chunks", chunks.size());

            enrichedChunks.add(new Document(chunk.getContent(), metadata));
        }

        logger.info("Split file {} into {} chunks", fileName, enrichedChunks.size());
        return enrichedChunks;
    }

    private boolean isFileAlreadyProcessed(String filePath) {
        try {
            String fileHash = computeFileHash(filePath);
            String processedHash = (String) redisTemplate.opsForHash().get(PROCESSED_FILES_KEY, filePath);
            return fileHash.equals(processedHash);
        } catch (Exception e) {
            logger.warn("Error checking if file is processed: {}", e.getMessage());
            return false;
        }
    }

    private void markFileAsProcessed(String filePath) {
        try {
            String fileHash = computeFileHash(filePath);
            redisTemplate.opsForHash().put(PROCESSED_FILES_KEY, filePath, fileHash);
            logger.debug("Marked file as processed: {}", filePath);
        } catch (Exception e) {
            logger.warn("Error marking file as processed: {}", e.getMessage());
        }
    }

    private String computeFileHash(String filePath) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hashBytes = digest.digest(filePath.getBytes());

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public void clearProcessedFiles() {
        redisTemplate.delete(PROCESSED_FILES_KEY);
        logger.info("Cleared all processed file records");
    }

    public int getProcessedFileCount() {
        return redisTemplate.opsForHash().size(PROCESSED_FILES_KEY).intValue();
    }
}