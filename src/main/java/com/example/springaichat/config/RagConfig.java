package com.example.springaichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.RedisVectorStore;
import org.springframework.ai.vectorstore.RedisVectorStore.RedisVectorStoreConfig;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import redis.clients.jedis.JedisPooled;

@Configuration
public class RagConfig {

        private static final Logger logger = LoggerFactory.getLogger(RagConfig.class);

        @Value("${rag.retrieval.top-k:3}")
        private int topK;

        @Value("${rag.retrieval.similarity-threshold:0.1}")
        private double similarityThreshold;

        @Value("${spring.ai.vectorstore.redis.index:chat_knowledge_index}")
        private String indexName;

        @Value("${spring.ai.vectorstore.redis.prefix:rag:vector:}")
        private String prefix;

        @Value("${spring.data.redis.host:localhost}")
        private String redisHost;

        @Value("${spring.data.redis.port:6379}")
        private int redisPort;

        @Bean
        @ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
        public JedisPooled jedisPooled() {
                logger.info("Creating JedisPooled connection to {}:{}", redisHost, redisPort);
                return new JedisPooled(redisHost, redisPort);
        }

        @Bean
        @ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
        public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
                logger.info("Creating RedisVectorStore with constructor...");
                logger.info("Index name: {}, prefix: {}", indexName, prefix);

                RedisVectorStoreConfig config = RedisVectorStoreConfig.builder()
                                .withIndexName(indexName)
                                .withPrefix(prefix)
                                .build();

                RedisVectorStore vectorStore = new RedisVectorStore(config, embeddingModel, jedisPooled, true);

                logger.info("RedisVectorStore created successfully");
                return vectorStore;
        }

        @Bean
        @ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
        public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
                SearchRequest searchRequest = SearchRequest.query("")
                                .withTopK(topK)
                                .withSimilarityThreshold(similarityThreshold);

                QuestionAnswerAdvisor advisor = new QuestionAnswerAdvisor(vectorStore, searchRequest);

                logger.info("QuestionAnswerAdvisor initialized - topK: {}, similarityThreshold: {}",
                                topK, similarityThreshold);
                return advisor;
        }
}