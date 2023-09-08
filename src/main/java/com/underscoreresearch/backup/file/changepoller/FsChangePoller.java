package com.underscoreresearch.backup.file.changepoller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;

@Slf4j
public class FsChangePoller implements FileChangePoller {
    private static final String EXECUTABLE_LOCATION = "/usr/bin/fswatch";
    private final byte[] buffer = new byte[16384];
    protected InputStream inputStream;
    private Process process;
    private BufferedReader errorOutput;
    private boolean hasError = false;
    private int bufferPos;

    public static boolean isSupported() {
        return new File(EXECUTABLE_LOCATION).canExecute();
    }

    @Override
    public void registerPaths(List<Path> paths) throws IOException {
        List<String> args = Lists.newArrayList(EXECUTABLE_LOCATION,
                "-rxn0",
                "--event", "2", "--event", "4", "--event", "8");
        args.addAll(Lists.transform(paths, Path::toString));
        process = Runtime.getRuntime().exec(args.toArray(new String[args.size()]));
        errorOutput = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        inputStream = process.getInputStream();
    }

    @Override
    public List<Path> fetchPaths() throws IOException, OverflowException {
        if (errorOutput != null && errorOutput.ready()) {
            if (!hasError) {
                hasError = true;
                log.warn("Error monitoring file changes: {}", errorOutput.readLine());
            }
            while(errorOutput.ready()) {
                errorOutput.readLine();
            }
        }

        int read = inputStream.read(buffer, bufferPos, buffer.length - bufferPos);
        if (read <= 0) {
            if (process != null) {
                throw new IOException("File change monitor process ended");
            } else {
                return new ArrayList<>();
            }
        }

        bufferPos += read;


        int lastStart = 0;
        Set<String> paths = new HashSet<>();
        for (int i = 0; i < bufferPos; i++) {
            if (buffer[i] == 0) {
                String path = new String(buffer, lastStart, i - lastStart);
                int index = path.lastIndexOf(' ');
                if (index < 0)
                    throw new IOException("Failed to parse file change: " + path);
                paths.add(path.substring(0, index));
                lastStart = i + 1;
            }
        }
        System.arraycopy(buffer, lastStart, buffer, 0, bufferPos - lastStart);
        bufferPos -= lastStart;

        return paths.stream().map(Path::of).collect(Collectors.toList());
    }

    @Override
    public void close() throws IOException {
        if (process != null) {
            Process oldProcess = process;
            process = null;
            oldProcess.destroy();
        }
        if (inputStream != null) {
            inputStream.close();
        }
        if (errorOutput != null) {
            errorOutput.close();
        }
    }
}
