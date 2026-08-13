package com.example.springaichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Configuration
public class OpenAiChatConfig {

  private static final Logger logger = LoggerFactory.getLogger(OpenAiChatConfig.class);

  @Value("${spring.ai.openai.api-key:}")
  private String apiKey;

  @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
  private String baseUrl;

  @Value("${spring.ai.openai.chat.options.model:qwen-turbo}")
  private String model;

  @Value("${spring.ai.openai.chat.options.temperature:0.7}")
  private Double temperature;

  @Bean
  public OpenAiApi openAiApi() {
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    logger.info("OpenAI compatible base url: {}", normalizedBaseUrl);
    return new OpenAiApi(normalizedBaseUrl, apiKey);
  }

  @Bean
  public OpenAiChatOptions chatOptions() {
    return OpenAiChatOptions.builder()
        .withModel(model)
        .withTemperature(temperature)
        .withMaxTokens(2048)
        .withTopP(0.9)
        .withFrequencyPenalty(0.0)
        .withPresencePenalty(0.0)
        .build();
  }

  @Bean
  public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions chatOptions) {
    return new OpenAiChatModel(openAiApi, chatOptions);
  }

  @Bean
  @Primary
  public ChatClient.Builder chatClientBuilder(OpenAiChatModel chatModel,
          @Autowired(required = false) QuestionAnswerAdvisor questionAnswerAdvisor) {
    ChatClient.Builder builder = ChatClient.builder(chatModel)
        .defaultOptions(chatOptions());

    if (questionAnswerAdvisor != null) {
      builder.defaultAdvisors(List.of(new SimpleLoggerAdvisor(), questionAnswerAdvisor));
      logger.info("RAG enabled, using QuestionAnswerAdvisor with SimpleLoggerAdvisor");
    } else {
      builder.defaultAdvisors(new SimpleLoggerAdvisor());
      logger.info("RAG disabled, using only SimpleLoggerAdvisor");
    }

    return builder;
  }

  @Bean
  public ApplicationRunner aiConnectionWarmup(ChatClient.Builder chatClientBuilder) {
    return args -> {
      logger.info("Starting AI connection warmup...");
      long startTime = System.currentTimeMillis();

      try {
        ChatClient chatClient = chatClientBuilder.build();
        chatClient.prompt()
            .messages(new UserMessage("ping"))
            .stream()
            .content()
            .collectList()
            .block();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("AI connection warmup completed in {}ms", duration);
      } catch (Exception e) {
        logger.warn("AI connection warmup failed (will retry on first request): {}", e.getMessage());
      }
    };
  }

  private String normalizeBaseUrl(String rawBaseUrl) {
    if (rawBaseUrl == null) {
      return "https://dashscope.aliyuncs.com/compatible-mode";
    }

    String normalized = rawBaseUrl.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }

    if (normalized.endsWith("/v1")) {
      normalized = normalized.substring(0, normalized.length() - 3);
    }

    return normalized;
  }
}