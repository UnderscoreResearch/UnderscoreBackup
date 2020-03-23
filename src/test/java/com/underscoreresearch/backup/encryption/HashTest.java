package com.underscoreresearch.backup.encryption;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class HashTest {
    @Test
    public void test() {
        String quickResult = Hash.hash("abcdef".getBytes());
        Hash hash = new Hash();
        hash.addBytes("abc".getBytes());
        hash.addBytes("def".getBytes());

        assertThat(quickResult, Is.is(hash.getHash()));
    }

    @Test
    public void testEncode() {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= 9; i++) {
            builder.append(i + "");
            assertThat(Hash.decodeBytes(Hash.encodeBytes(builder.toString().getBytes())),
                    Is.is(builder.toString().getBytes()));
        }
    }
}