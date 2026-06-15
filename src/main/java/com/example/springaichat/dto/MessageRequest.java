package com.example.springaichat.dto;

/**
 * 消息请求DTO
 */
public class MessageRequest {

    private Long conversationId;
    
    private String content;

    public MessageRequest() {
    }

    public MessageRequest(Long conversationId, String content) {
        this.conversationId = conversationId;
        this.content = content;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
