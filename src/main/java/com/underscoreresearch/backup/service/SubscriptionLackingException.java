package com.underscoreresearch.backup.service;

import java.io.IOException;

public class SubscriptionLackingException extends IOException {
    public SubscriptionLackingException(String message) {
        super(message);
    }
}
