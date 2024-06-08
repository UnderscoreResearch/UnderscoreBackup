package com.underscoreresearch.backup.utils;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ProcessingStoppedException extends RuntimeException {
    public ProcessingStoppedException(String message) {
        super(message);
    }

    public ProcessingStoppedException(Throwable cause) {
        super(cause);
    }
}
