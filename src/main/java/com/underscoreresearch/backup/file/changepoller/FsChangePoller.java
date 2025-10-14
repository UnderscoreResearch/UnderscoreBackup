package com.underscoreresearch.backup.file.changepoller;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

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

/**
 * Implementation of FileChangePoller that uses the fswatch utility on Unix-like systems.
 * This class monitors file system changes by spawning an fswatch process and
 * reading its output to detect changes.
 */
@Slf4j
public class FsChangePoller implements FileChangePoller {
    private static final String EXECUTABLE_LOCATION = "/usr/bin/fswatch";
    private final byte[] buffer = new byte[16384];
    protected InputStream inputStream;
    private Process process;
    private BufferedReader errorOutput;
    private boolean hasError = false;
    private int bufferPos;

    /**
     * Checks if fswatch is supported on the current system.
     *
     * @return true if fswatch is available and executable, false otherwise
     */
    public static boolean isSupported() {
        return new File(EXECUTABLE_LOCATION).canExecute();
    }

    /**
     * Registers paths to be monitored for changes by starting an fswatch process.
     *
     * @param paths List of paths to monitor
     * @throws IOException If there's an error starting the fswatch process
     */
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

    /**
     * Fetches paths that have changed since the last call by reading from the fswatch process output.
     *
     * @return List of changed paths as strings
     * @throws IOException If there's an error reading from the fswatch process
     * @throws OverflowException If there are too many changes to report
     */
    @Override
    public List<String> fetchPaths() throws IOException, OverflowException {
        if (errorOutput != null && errorOutput.ready()) {
            if (!hasError) {
                hasError = true;
                log.warn("Error monitoring file changes: \"{}\"", errorOutput.readLine());
            }
            while (errorOutput.ready()) {
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
                    throw new IOException("Failed to parse file change: \"" + path + "\"");
                paths.add(path.substring(0, index));
                lastStart = i + 1;
            }
        }
        System.arraycopy(buffer, lastStart, buffer, 0, bufferPos - lastStart);
        bufferPos -= lastStart;

        return new ArrayList<>(paths);
    }

    /**
     * Closes the fswatch process and releases resources.
     *
     * @throws IOException If there's an error closing the process or streams
     */
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
