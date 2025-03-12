package com.underscoreresearch.backup.errorcorrection;

import com.underscoreresearch.backup.errorcorrection.implementation.NoneErrorCorrector;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoneErrorCorrectorTest {
    private byte[] data;
    private BackupBlockStorage storage;

    @BeforeEach
    public void setup() {
        data = new byte[1024];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) i;

        storage = new BackupBlockStorage();
    }

    @Test
    public void multiPart() throws Exception {
        NoneErrorCorrector noneErrorCorrector = new NoneErrorCorrector(128);

        List<byte[]> parts = noneErrorCorrector.encodeErrorCorrection(storage, data);
        byte[] result = noneErrorCorrector.decodeErrorCorrection(storage, parts);

        assertThat(result, Is.is(data));
        assertThat(storage.getEc(), Is.is("NONE"));
        assertThat(parts.size(), Is.is(8));

        parts.set(0, null);
        assertThrows(IOException.class, () -> noneErrorCorrector.decodeErrorCorrection(storage, parts));
    }

    @Test
    public void singlePart() throws Exception {

        NoneErrorCorrector noneErrorCorrector = new NoneErrorCorrector(1024);

        List<byte[]> parts = noneErrorCorrector.encodeErrorCorrection(storage, data);
        byte[] result = noneErrorCorrector.decodeErrorCorrection(storage, parts);

        assertThat(result, Is.is(data));
        assertThat(storage.getEc(), Is.is("NONE"));
        assertThat(parts.size(), Is.is(1));
    }
}