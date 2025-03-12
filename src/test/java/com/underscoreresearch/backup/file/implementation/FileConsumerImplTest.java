package com.underscoreresearch.backup.file.implementation;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.FileBlockAssignment;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupBlockCompletion;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupSet;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;

class FileConsumerImplTest {
    private MetadataRepository repository;
    private FileConsumerImpl fileConsumer;
    private FileBlockAssignment firstAssignment;
    private FileBlockAssignment secondAssignment;
    private BackupSet set;
    private BackupFile file;
    private BackupCompletion promise;
    private AtomicBoolean completed = new AtomicBoolean();
    private AtomicBoolean success = new AtomicBoolean();

    @BeforeEach
    public void setup() {
        repository = Mockito.mock(MetadataRepository.class);
        set = new BackupSet();
        file = BackupFile.builder().path("/").build();

        firstAssignment = Mockito.mock(FileBlockAssignment.class);
        secondAssignment = Mockito.mock(FileBlockAssignment.class);

        promise = (success) -> {
            this.completed.set(true);
            this.success.set(success);
        };

        fileConsumer = new FileConsumerImpl(repository, Lists.newArrayList(firstAssignment, secondAssignment));
    }

    @Test
    public void emptyFile() throws IOException {
        file.setLength(0L);
        fileConsumer.backupFile(set, file, promise);

        assertThat(completed.get(), Is.is(true));
        assertThat(success.get(), Is.is(true));

        Mockito.verify(repository).addFile(file);
        Mockito.verify(firstAssignment, Mockito.never()).assignBlocks(any(), any(), any());
        Mockito.verify(secondAssignment, Mockito.never()).assignBlocks(any(), any(), any());
    }

    @Test
    public void firstAssignmentCompletedSuccessfully() throws IOException {
        file.setLength(1L);
        Mockito.when(firstAssignment.assignBlocks(any(), any(), any())).then((t) -> {
            BackupBlockCompletion completion = t.getArgument(2);
            completion.completed(new ArrayList<>());
            return true;
        });

        fileConsumer.backupFile(set, file, promise);

        assertThat(completed.get(), Is.is(true));
        assertThat(success.get(), Is.is(true));

        Mockito.verify(repository).addFile(file);
        Mockito.verify(firstAssignment).assignBlocks(any(), any(), any());
        Mockito.verify(secondAssignment, Mockito.never()).assignBlocks(any(), any(), any());
    }

    @Test
    public void firstAssignmentFailed() throws IOException {
        file.setLength(1L);
        Mockito.when(firstAssignment.assignBlocks(any(), any(), any())).then((t) -> {
            BackupBlockCompletion completion = t.getArgument(2);
            completion.completed(null);
            return true;
        });

        fileConsumer.backupFile(set, file, promise);

        assertThat(completed.get(), Is.is(true));
        assertThat(success.get(), Is.is(false));

        Mockito.verify(repository, Mockito.never()).addFile(any());
        Mockito.verify(firstAssignment).assignBlocks(any(), any(), any());
        Mockito.verify(secondAssignment, Mockito.never()).assignBlocks(any(), any(), any());
    }

    @Test
    public void noAssignedments() throws IOException {
        file.setLength(1L);

        fileConsumer.backupFile(set, file, promise);

        assertThat(completed.get(), Is.is(true));
        assertThat(success.get(), Is.is(false));

        Mockito.verify(repository, Mockito.never()).addFile(any());
        Mockito.verify(firstAssignment).assignBlocks(any(), any(), any());
        Mockito.verify(secondAssignment).assignBlocks(any(), any(), any());
    }

    @Test
    public void secondAssignmentCompletedSuccessfully() throws IOException {
        file.setLength(1L);
        Mockito.when(secondAssignment.assignBlocks(any(), any(), any())).then((t) -> {
            BackupBlockCompletion completion = t.getArgument(2);
            completion.completed(new ArrayList<>());
            return true;
        });

        fileConsumer.backupFile(set, file, promise);

        assertThat(completed.get(), Is.is(true));
        assertThat(success.get(), Is.is(true));

        Mockito.verify(repository).addFile(file);
        Mockito.verify(firstAssignment).assignBlocks(any(), any(), any());
        Mockito.verify(secondAssignment).assignBlocks(any(), any(), any());
    }
}