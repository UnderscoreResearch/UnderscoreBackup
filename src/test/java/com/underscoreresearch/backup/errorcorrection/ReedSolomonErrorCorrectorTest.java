package com.underscoreresearch.backup.errorcorrection;

import com.underscoreresearch.backup.errorcorrection.implementation.ReedSolomonErrorCorrector;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReedSolomonErrorCorrectorTest {
    private byte[] data;
    private BackupBlock block;
    private BackupBlockStorage storage;

    @BeforeEach
    public void setup() {
        storage = new BackupBlockStorage();
        block = new BackupBlock();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 100, 1024})
    public void even(int length) throws Exception {
        data = new byte[length];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) i;

        ReedSolomonErrorCorrector corrector = new ReedSolomonErrorCorrector(8, 3);
        ReedSolomonErrorCorrector decodeCorrector = new ReedSolomonErrorCorrector(12, 4);

        List<byte[]> parts = corrector.encodeErrorCorrection(storage, data);
        byte[] result = decodeCorrector.decodeErrorCorrection(storage, parts);

        assertThat(result, Is.is(data));
        assertThat(storage.getEc(), Is.is("RS"));
        assertThat(parts.size(), Is.is(11));

        for (int i = 0; i < 2; i++) {
            parts.get(i)[0] = 1;
        }

        result = corrector.decodeErrorCorrection(storage, parts);
        assertThat(result, Is.is(data));

        parts.set(0, null);
        parts.set(1, null);
        parts.set(2, null);

        result = corrector.decodeErrorCorrection(storage, parts);
        assertThat(result, Is.is(data));

        parts.set(3, null);
        assertThrows(IOException.class, () -> corrector.decodeErrorCorrection(storage, parts));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 100, 1024})
    public void redo(int length) throws Exception {
        data = new byte[length];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) i;

        ReedSolomonErrorCorrector corrector = new ReedSolomonErrorCorrector(8, 3);

        List<byte[]> parts = corrector.encodeErrorCorrection(storage, data);

        List<byte[]> otherParts = corrector.encodeErrorCorrection(storage,
                corrector.decodeErrorCorrection(storage, parts));
        List<byte[]> mixedParts = new ArrayList<>();

        for (int i = 0; i < parts.size(); i++) {
            if (i % 2 == 0) {
                mixedParts.add(parts.get(i));
            } else {
                mixedParts.add(otherParts.get(i));
            }
        }

        byte[] result = corrector.decodeErrorCorrection(storage, mixedParts);
        assertThat(result, Is.is(data));
    }
}