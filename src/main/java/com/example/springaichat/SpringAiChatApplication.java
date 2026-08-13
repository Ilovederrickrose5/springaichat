
package com.example.springaichat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.ai.autoconfigure.chat.client.ChatClientAutoConfiguration;

/**
 * Spring AI 对话问答应用启动类
 * 提供基于 Spring Boot + Spring AI 的对话接口服务
 */
@SpringBootApplication
@EnableAutoConfiguration(exclude = {ChatClientAutoConfiguration.class})
public class SpringAiChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiChatApplication.class, args);
    }
}
