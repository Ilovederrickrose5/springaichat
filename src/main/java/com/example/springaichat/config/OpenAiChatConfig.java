package com.example.springaichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI OpenAI 配置类
 * 配置阿里云百炼（DashScope）OpenAI 兼容接口
 */
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

  /**
   * 创建 OpenAI API 客户端
   */
  @Bean
  public OpenAiApi openAiApi() {
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    logger.info("OpenAI compatible base url: {}", normalizedBaseUrl);
    return new OpenAiApi(normalizedBaseUrl, apiKey);
  }

  /**
   * 创建聊天模型配置
   */
  @Bean
  public OpenAiChatOptions chatOptions() {
    return OpenAiChatOptions.builder()
        .withModel(model)
        .withTemperature(temperature)
        .withMaxTokens(2048)
        .build();
  }

  /**
   * 创建 OpenAiChatModel
   */
  @Bean
  public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions chatOptions) {
    return new OpenAiChatModel(openAiApi, chatOptions);
  }

  /**
   * 创建 ChatClient.Builder
   */
  @Bean
  public ChatClient.Builder chatClientBuilder(OpenAiChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultOptions(chatOptions())
        .defaultAdvisors(new SimpleLoggerAdvisor());
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
