package com.underscoreresearch.backup.file.implementation;

import com.underscoreresearch.backup.file.LogFileRepository;
import com.underscoreresearch.backup.utils.AccessLock;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static com.underscoreresearch.backup.encryption.EncryptionIdentity.RANDOM;

public class LogFileRepositoryImpl implements LogFileRepository {
    private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);
    private final AccessLock accessLock;

    public LogFileRepositoryImpl(Path filename) throws IOException {
        this.accessLock = new AccessLock(filename.toString());
        this.accessLock.lock(true);

        seekToEnd();
    }

    public static List<String> trimLogFiles(List<String> files) {
        boolean completed = false;
        for (int i = files.size() - 1; i > 0; i--) {
            String fileName = files.get(i);
            if (fileName.endsWith("-c.gz")) {
                completed = true;
            } else if (fileName.endsWith("-ic.gz") || (completed && fileName.endsWith("-i.gz"))) {
                return files.subList(i, files.size());
            }
        }
        return files;
    }

    public void close() throws IOException {
        this.accessLock.close();
    }

    @Override
    public synchronized void addFile(String file) throws IOException {
        byte[] bytes = file.getBytes(StandardCharsets.UTF_8);
        long ret = accessLock.getLockedChannel().write(new ByteBuffer[]{ByteBuffer.wrap(bytes), ByteBuffer.wrap(NEWLINE)});
        if (ret != bytes.length + 1) {
            throw new IOException("Failed to write file to log");
        }
    }

    @Override
    public synchronized void resetFiles(List<String> files) throws IOException {
        accessLock.getLockedChannel().position(0);
        for (String file : files) {
            addFile(file);
        }
        accessLock.getLockedChannel().truncate(accessLock.getLockedChannel().position());
    }

    @Override
    public synchronized List<String> getAllFiles() throws IOException {
        accessLock.getLockedChannel().position(0);
        // Intentionally not closing and discarding the buffered reader here.
        BufferedReader br = new BufferedReader(Channels.newReader(accessLock.getLockedChannel(), StandardCharsets.UTF_8));
        return br.lines().toList();
    }

    @Override
    public synchronized String getRandomFile() throws IOException {
        try {
            accessLock.getLockedChannel().position(RANDOM.nextInt((int) accessLock.getLockedChannel().size()));
            // Intentionally not closing and discarding the buffered reader here.
            BufferedReader br = new BufferedReader(Channels.newReader(accessLock.getLockedChannel(), StandardCharsets.UTF_8));
            // Discard the first line
            br.readLine();
            // Next line is the answer.
            String line = br.readLine();
            if (line == null) {
                accessLock.getLockedChannel().position(0);
                br = new BufferedReader(Channels.newReader(accessLock.getLockedChannel(), StandardCharsets.UTF_8));
                return br.readLine();
            }
            return line;
        } finally {
            // All calls must leave file pointer to the end.
            seekToEnd();
        }
    }

    private void seekToEnd() throws IOException {
        accessLock.getLockedChannel().position(accessLock.getLockedChannel().size());
    }
}
