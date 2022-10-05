package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.HUMAN_READABLE;
import static com.underscoreresearch.backup.utils.LogUtil.printFile;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupFile;

@CommandPlugin(value = "history", args = "[FILES]...", description = "List file history",
        needPrivateKey = false, supportSource = true)
@Slf4j
public class HistoryCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        List<String> paths = commandLine.getArgList().subList(1, commandLine.getArgList().size());

        if (paths.size() == 0) {
            throw new ParseException("No files specified");
        }

        for (String path : paths.stream().map(file -> PathNormalizer.normalizePath(file)).collect(Collectors.toList())) {
            listHistory(commandLine, repository, path);
        }
    }

    private void listHistory(CommandLine commandLine,
                             MetadataRepository repository,
                             String path) throws IOException {
        List<BackupFile> versions = repository.file(path);
        if (versions != null) {
            System.out.println(path + ":");
            long totalSize = versions.stream().map(t -> t.getLength() != null ? t.getLength() : 0).reduce((a, b) -> a + b).get();
            if (commandLine.hasOption(HUMAN_READABLE))
                System.out.println("total " + readableSize(totalSize));
            else
                System.out.println("total " + totalSize);

            for (BackupFile file : versions) {
                System.out.println(printFile(commandLine, false, file));
            }
        }
    }
}
