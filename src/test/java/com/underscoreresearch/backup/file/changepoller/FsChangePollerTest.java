package com.underscoreresearch.backup.file.changepoller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FsChangePollerTest {
    @Test
    public void testPolling() throws IOException, FileChangePoller.OverflowException {
        PipedInputStream inputStream = new PipedInputStream();
        PipedOutputStream outputStream = new PipedOutputStream(inputStream);

        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 1000; ) {
            expected.add("test" + i);
            i++;
        }

        Thread thread = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                byte[] data = ("test" + i + " 2").getBytes(StandardCharsets.UTF_8);
                try {
                    outputStream.write(data);
                    outputStream.write(0);
                    Thread.sleep(1);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

        TestWatcherChangePoller poller = new TestWatcherChangePoller(inputStream);
        while (!expected.isEmpty()) {
            List<String> paths = poller.fetchPaths();
            for (String path : paths) {
                assertTrue(expected.remove(path));
            }
        }
    }

    private static class TestWatcherChangePoller extends FsChangePoller {
        public TestWatcherChangePoller(InputStream stream) throws IOException {
            inputStream = stream;
        }

        @Override
        public void registerPaths(List<Path> paths) throws IOException {
        }

        @Override
        public void close() throws IOException {
        }
    }
}