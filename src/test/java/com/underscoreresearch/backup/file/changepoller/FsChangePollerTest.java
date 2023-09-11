package com.underscoreresearch.backup.file.changepoller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

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
            List<Path> paths = poller.fetchPaths();
            for (Path path : paths) {
                assertTrue(expected.remove(path.toString()));
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