package com.underscoreresearch.backup.encryption;

import static org.hamcrest.MatcherAssert.assertThat;

import java.security.SecureRandom;

import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

class HashSha3Test {
    @Test
    public void equality() {
        for (int i = 0; i < 100; i++) {
            byte[] bytes = new byte[1024];
            new SecureRandom().nextBytes(bytes);
            assertThat(HashSha3.hash(bytes), Is.is(HashSha3.hash(bytes)));
        }
    }

    @Test
    public void nonEquality() {
        for (int i = 0; i < 100; i++) {
            byte[] bytes1 = new byte[1024];
            new SecureRandom().nextBytes(bytes1);
            byte[] bytes2 = new byte[1024];
            new SecureRandom().nextBytes(bytes2);
            assertThat(HashSha3.hash(bytes1), Matchers.not(HashSha3.hash(bytes2)));
        }
    }
}