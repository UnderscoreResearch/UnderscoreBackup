package com.underscoreresearch.backup.utils.state;

import com.underscoreresearch.backup.file.changepoller.FileChangePoller;
import com.underscoreresearch.backup.file.changepoller.OsxChangePoller;
import com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl;
import com.underscoreresearch.backup.service.api.model.ReleaseFileItem;
import com.underscoreresearch.backup.service.api.model.ReleaseResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Slf4j
public class OsxState extends MachineState {
    public OsxState(boolean pauseOnBattery) {
        super(pauseOnBattery);
    }

    @Override
    public boolean getOnBattery() {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{
                    "pmset", "-g", "ac"
            });

            try (BufferedReader stdInput = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {

                String s;
                while ((s = stdInput.readLine()) != null) {
                    if (s.contains("No adapter")) {
                        return true;
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    @Override
    public ReleaseFileItem getDistribution(List<ReleaseFileItem> files) {
        Optional<ReleaseFileItem> ret = files.stream().filter(file -> file.getName().endsWith(".dmg")).findAny();
        return ret.orElse(null);
    }

    @Override
    public void lowPriority() {
        try {
            Process process = Runtime.getRuntime()
                    .exec(new String[]{
                            "renice", "+10", "-p", Long.toString(ProcessHandle.current().pid())
                    });
            try {
                if (process.waitFor() != 0) {
                    throw new IOException();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted changing process to low priority", e);
            }
        } catch (IOException e) {
            log.warn("Can't change process to low priority", e);
        }
    }

    @Override
    public boolean supportsAutomaticUpgrade() {
        return true;
    }

    @Override
    public void upgrade(ReleaseResponse response) throws IOException {
        ReleaseFileItem download = getDistribution(response.getFiles());

        if (download != null) {
            File tempFile = File.createTempFile("underscorebackup", ".dmg");
            ServiceManagerImpl.downloadRelease(response, download, tempFile);

            File tempFile2 = File.createTempFile("underscorebackup", ".sh");
            // This is a little helper script that will mount the dmg file and then install the new version.
            try (FileWriter writer = new FileWriter(tempFile2)) {
                writer.write("#!/bin/sh\n" +
                        "\n" +
                        "nohup perl << EOF > \"" + tempFile2 + ".log\" 2>&1\n" +
                        "my \\$file = \"" + tempFile + "\";\n" +
                        "\n" +
                        "my \\$mount = \\`hdiutil attach \"\\$file\" | grep \"/Volumes\"\\`;\n" +
                        "\n" +
                        "if (\\$mount =~ /(\\\\/Volumes\\\\/.*)/) {\n" +
                        "    my \\$mountpoint = \\$1;\n" +
                        "    chomp(\\$mountpoint);\n" +
                        "\n" +
                        "    my \\$existingProcess = \\`ps auxww | grep \"/Applications/Underscore Backup.app/Contents/MacOS/Underscore Backup\" | grep -v grep\\`;\n" +
                        "    if (\\$existingProcess =~ /^\\\\S+\\\\s+(\\\\d+)/) {\n" +
                        "      kill(\"TERM\",\\$1);\n" +
                        "      sleep(10);\n" +
                        "    }\n" +
                        "    system(\"rm -rf \\\\\"/Applications/Underscore Backup.app\\\"\") && die \\$?;\n" +
                        "    system(\"rsync -a \\\\\"\\$mountpoint\\\"/*.app  /Applications/\") && die \\$?;\n" +
                        "\n" +
                        "    system(\"hdiutil detach \\\\\"\\$mountpoint\\\\\"\") && die \\$?;\n" +
                        "\n" +
                        "    if (!fork && !fork) {\n" +
                        "        exec \"\\\\\"/Applications/Underscore Backup.app/Contents/MacOS/Underscore Backup\\\\\"\" || die \\$?;\n" +
                        "    }\n" +
                        "} else {\n" +
                        "    die \"Failed to mount \\$file\";\n" +
                        "}\n" +
                        "EOF\n");
            }

            if (!tempFile2.setExecutable(true)) {
                throw new IOException("Failed to make installer executable");
            }

            executeUpdateProcess(new String[]{tempFile2.toString()});
        }
    }

    @Override
    public FileChangePoller createPoller() throws IOException {
        return new OsxChangePoller();
    }
}
