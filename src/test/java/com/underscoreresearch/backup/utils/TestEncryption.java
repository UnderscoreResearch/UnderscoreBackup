package com.underscoreresearch.backup.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;

@Slf4j
class TestEncryption {
    @Test
    public void testEncryption() throws Exception {
        PBEKeySpec spec = new PBEKeySpec("newPassword".toCharArray(), Base64.decodeBase64("Zis1NrHtBQEC2G7GDwCmhrjP8GS5ia314hWvLsWxoyc="), 64 * 1024, 32 * 8);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] bytes = skf.generateSecret(spec).getEncoded();

        log.info(Base64.encodeBase64String(bytes));

        bytes[0] = (byte) (bytes[0] | 7);
        bytes[31] = (byte) (bytes[31] & 63);
        bytes[31] = (byte) (bytes[31] | 128);

        log.info(Base64.encodeBase64String(bytes));

    }

}