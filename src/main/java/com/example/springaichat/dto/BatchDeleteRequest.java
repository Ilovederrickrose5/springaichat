package com.example.springaichat.dto;

import java.util.List;

public class BatchDeleteRequest {
    private List<Object> messageIds;

    public List<Object> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<Object> messageIds) {
        this.messageIds = messageIds;
    }
}