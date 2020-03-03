package com.underscoreresearch.backup.model;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@EqualsAndHashCode()
@ToString
public class BackupActivePath {
    @JsonIgnore
    private Map<String, BackupActiveFile> files = new HashMap<>();

    @JsonProperty
    public Set<BackupActiveFile> getFiles() {
        return new HashSet<>(files.values());
    }

    @JsonProperty
    public void setFiles(Set<BackupActiveFile> files) {
        this.files = files.stream().collect(Collectors.toMap(t -> t.getPath(),
                t -> t));
    }

    public void setParentPath(String parent) {
        String realParent;
        if (!parent.endsWith(PATH_SEPARATOR))
            realParent = parent + PATH_SEPARATOR;
        else
            realParent = parent;

        files = files.entrySet().stream().collect(Collectors.toMap(t -> realParent + t.getValue().getPath(),
                Map.Entry::getValue));
    }

    public BackupActivePath(String parent, Set<BackupActiveFile> files) {
        String realParent;
        if (!parent.endsWith(PATH_SEPARATOR))
            realParent = parent + PATH_SEPARATOR;
        else
            realParent = parent;

        this.files = new HashMap<>();
        for (BackupActiveFile item : files) {
            this.files.put(realParent + item.getPath(), item);
        }
    }

    @JsonIgnore
    public void addFile(BackupFile file) {
        files.put(file.getPath(), BackupActiveFile.builder().path(stripPath(file.getPath())).build());
    }

    @JsonIgnore
    public boolean unprocessedFile(String file) {
        BackupActiveFile activeFile = getFile(file);
        if (activeFile == null)
            return false;
        if (activeFile.getStatus() == null || activeFile.getStatus() == BackupActiveStatus.INCOMPLETE)
            return true;
        return false;
    }

    @JsonIgnore
    public BackupActiveFile getFile(BackupFile file) {
        return getFile(file.getPath());
    }

    @JsonIgnore
    public BackupActiveFile getFile(String file) {
        return files.get(file);
    }

    @JsonIgnore
    public boolean completed() {
        return !files.values().stream()
                .anyMatch(t -> t.getStatus() == null || t.getStatus() == BackupActiveStatus.INCOMPLETE);
    }

    @JsonIgnore
    public Set<String> includedPaths() {
        return files.entrySet().stream()
                .filter(t -> t.getValue().getStatus() == BackupActiveStatus.INCLUDED)
                .map(t -> t.getValue().getPath())
                .collect(Collectors.toSet());
    }

    public static String stripPath(String path) {
        int ind;
        if (path.endsWith(PATH_SEPARATOR))
            ind = path.lastIndexOf(PATH_SEPARATOR, path.length() - PATH_SEPARATOR.length() - 1);
        else
            ind = path.lastIndexOf(PATH_SEPARATOR);
        if (ind >= 0)
            return path.substring(ind + PATH_SEPARATOR.length());
        return path;
    }

    public static String findParent(String path) {
        int ind;
        if (path.endsWith(PATH_SEPARATOR))
            ind = path.lastIndexOf(PATH_SEPARATOR, path.length() - PATH_SEPARATOR.length() - 1);
        else
            ind = path.lastIndexOf(PATH_SEPARATOR);
        if (ind >= 0)
            return path.substring(0, ind + PATH_SEPARATOR.length());
        return null;
    }

    public void mergeChanges(BackupActivePath otherPaths) {
        for (Map.Entry<String, BackupActiveFile> entry : otherPaths.files.entrySet()) {
            BackupActiveFile existingFile = files.get(entry.getKey());
            if (existingFile == null || existingFile.getStatus() == BackupActiveStatus.EXCLUDED) {
                files.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
