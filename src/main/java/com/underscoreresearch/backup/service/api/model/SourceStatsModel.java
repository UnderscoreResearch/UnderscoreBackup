/*
 * Underscore Backup Service Public API
 * Public and externally accessible API for the Underscore Backup Service
 *
 * The version of the OpenAPI document: 1.0.0
 * Contact: support@underscorebackup.com
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.underscoreresearch.backup.service.api.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.StringJoiner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * SourceStatsModel
 */
@JsonPropertyOrder({
        SourceStatsModel.JSON_PROPERTY_FILES,
        SourceStatsModel.JSON_PROPERTY_FILE_VERSIONS,
        SourceStatsModel.JSON_PROPERTY_TOTAL_SIZE,
        SourceStatsModel.JSON_PROPERTY_TOTAL_SIZE_LAST_VERSION,
        SourceStatsModel.JSON_PROPERTY_BLOCK_PARTS,
        SourceStatsModel.JSON_PROPERTY_BLOCKS,
        SourceStatsModel.JSON_PROPERTY_DIRECTORIES,
        SourceStatsModel.JSON_PROPERTY_DIRECTORY_VERSIONS,
        SourceStatsModel.JSON_PROPERTY_RECENT_ERROR
})
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class SourceStatsModel {
    public static final String JSON_PROPERTY_FILES = "files";
    public static final String JSON_PROPERTY_FILE_VERSIONS = "fileVersions";
    public static final String JSON_PROPERTY_TOTAL_SIZE = "totalSize";
    public static final String JSON_PROPERTY_TOTAL_SIZE_LAST_VERSION = "totalSizeLastVersion";
    public static final String JSON_PROPERTY_BLOCK_PARTS = "blockParts";
    public static final String JSON_PROPERTY_BLOCKS = "blocks";
    public static final String JSON_PROPERTY_DIRECTORIES = "directories";
    public static final String JSON_PROPERTY_DIRECTORY_VERSIONS = "directoryVersions";
    public static final String JSON_PROPERTY_RECENT_ERROR = "recentError";
    private Long files;
    private Long fileVersions;
    private Long totalSize;
    private Long totalSizeLastVersion;
    private Long blockParts;
    private Long blocks;
    private Long directories;
    private Long directoryVersions;
    private String recentError;

    public SourceStatsModel() {
    }

    public SourceStatsModel files(Long files) {
        this.files = files;
        return this;
    }

    /**
     * Number of file versions.
     *
     * @return files
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_FILES)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public Long getFiles() {
        return files;
    }


    @JsonProperty(JSON_PROPERTY_FILES)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setFiles(Long files) {
        this.files = files;
    }


    public SourceStatsModel fileVersions(Long fileVersions) {
        this.fileVersions = fileVersions;
        return this;
    }

    /**
     * Number of total file versions.
     *
     * @return fileVersions
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_FILE_VERSIONS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public Long getFileVersions() {
        return fileVersions;
    }


    @JsonProperty(JSON_PROPERTY_FILE_VERSIONS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setFileVersions(Long fileVersions) {
        this.fileVersions = fileVersions;
    }


    public SourceStatsModel totalSize(Long totalSize) {
        this.totalSize = totalSize;
        return this;
    }

    /**
     * Total size of all versions of files.
     *
     * @return totalSize
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_TOTAL_SIZE)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public Long getTotalSize() {
        return totalSize;
    }


    @JsonProperty(JSON_PROPERTY_TOTAL_SIZE)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }


    public SourceStatsModel totalSizeLastVersion(Long totalSizeLastVersion) {
        this.totalSizeLastVersion = totalSizeLastVersion;
        return this;
    }

    /**
     * Total size of all latest versions of files.
     *
     * @return totalSizeLastVersion
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_TOTAL_SIZE_LAST_VERSION)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public Long getTotalSizeLastVersion() {
        return totalSizeLastVersion;
    }


    @JsonProperty(JSON_PROPERTY_TOTAL_SIZE_LAST_VERSION)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setTotalSizeLastVersion(Long totalSizeLastVersion) {
        this.totalSizeLastVersion = totalSizeLastVersion;
    }


    public SourceStatsModel blockParts(Long blockParts) {
        this.blockParts = blockParts;
        return this;
    }

    /**
     * Total number of block parts.
     *
     * @return blockParts
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_BLOCK_PARTS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public Long getBlockParts() {
        return blockParts;
    }


    @JsonProperty(JSON_PROPERTY_BLOCK_PARTS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setBlockParts(Long blockParts) {
        this.blockParts = blockParts;
    }


    public SourceStatsModel blocks(Long blocks) {
        this.blocks = blocks;
        return this;
    }

    /**
     * Total number of used blocks
     *
     * @return blocks
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_BLOCKS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public Long getBlocks() {
        return blocks;
    }


    @JsonProperty(JSON_PROPERTY_BLOCKS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setBlocks(Long blocks) {
        this.blocks = blocks;
    }


    public SourceStatsModel directories(Long directories) {
        this.directories = directories;
        return this;
    }

    /**
     * Total number of unique directory paths.
     *
     * @return directories
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_DIRECTORIES)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public Long getDirectories() {
        return directories;
    }


    @JsonProperty(JSON_PROPERTY_DIRECTORIES)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setDirectories(Long directories) {
        this.directories = directories;
    }


    public SourceStatsModel directoryVersions(Long directoryVersions) {
        this.directoryVersions = directoryVersions;
        return this;
    }

    /**
     * Total number of versions of directory contents.
     *
     * @return directoryVersions
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_DIRECTORY_VERSIONS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public Long getDirectoryVersions() {
        return directoryVersions;
    }


    @JsonProperty(JSON_PROPERTY_DIRECTORY_VERSIONS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setDirectoryVersions(Long directoryVersions) {
        this.directoryVersions = directoryVersions;
    }


    public SourceStatsModel recentError(String recentError) {
        this.recentError = recentError;
        return this;
    }

    /**
     * Recent error recorded
     *
     * @return recentError
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_RECENT_ERROR)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public String getRecentError() {
        return recentError;
    }


    @JsonProperty(JSON_PROPERTY_RECENT_ERROR)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setRecentError(String recentError) {
        this.recentError = recentError;
    }


    /**
     * Return true if this SourceStatsModel object is equal to o.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SourceStatsModel sourceStatsModel = (SourceStatsModel) o;
        return Objects.equals(this.files, sourceStatsModel.files) &&
                Objects.equals(this.fileVersions, sourceStatsModel.fileVersions) &&
                Objects.equals(this.totalSize, sourceStatsModel.totalSize) &&
                Objects.equals(this.totalSizeLastVersion, sourceStatsModel.totalSizeLastVersion) &&
                Objects.equals(this.blockParts, sourceStatsModel.blockParts) &&
                Objects.equals(this.blocks, sourceStatsModel.blocks) &&
                Objects.equals(this.directories, sourceStatsModel.directories) &&
                Objects.equals(this.directoryVersions, sourceStatsModel.directoryVersions) &&
                Objects.equals(this.recentError, sourceStatsModel.recentError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(files, fileVersions, totalSize, totalSizeLastVersion, blockParts, blocks, directories, directoryVersions, recentError);
    }

    @Override
    public String toString() {
        String sb = "class SourceStatsModel {\n" +
                "    files: " + toIndentedString(files) + "\n" +
                "    fileVersions: " + toIndentedString(fileVersions) + "\n" +
                "    totalSize: " + toIndentedString(totalSize) + "\n" +
                "    totalSizeLastVersion: " + toIndentedString(totalSizeLastVersion) + "\n" +
                "    blockParts: " + toIndentedString(blockParts) + "\n" +
                "    blocks: " + toIndentedString(blocks) + "\n" +
                "    directories: " + toIndentedString(directories) + "\n" +
                "    directoryVersions: " + toIndentedString(directoryVersions) + "\n" +
                "    recentError: " + toIndentedString(recentError) + "\n" +
                "}";
        return sb;
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }

    /**
     * Convert the instance into URL query string.
     *
     * @return URL query string
     */
    public String toUrlQueryString() {
        return toUrlQueryString(null);
    }

    /**
     * Convert the instance into URL query string.
     *
     * @param prefix prefix of the query string
     * @return URL query string
     */
    public String toUrlQueryString(String prefix) {
        String suffix = "";
        String containerSuffix = "";
        String containerPrefix = "";
        if (prefix == null) {
            // style=form, explode=true, e.g. /pet?name=cat&type=manx
            prefix = "";
        } else {
            // deepObject style e.g. /pet?id[name]=cat&id[type]=manx
            prefix = prefix + "[";
            suffix = "]";
            containerSuffix = "]";
            containerPrefix = "[";
        }

        StringJoiner joiner = new StringJoiner("&");

        // add `files` to the URL query string
        if (getFiles() != null) {
            joiner.add(String.format("%sfiles%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getFiles()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `fileVersions` to the URL query string
        if (getFileVersions() != null) {
            joiner.add(String.format("%sfileVersions%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getFileVersions()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `totalSize` to the URL query string
        if (getTotalSize() != null) {
            joiner.add(String.format("%stotalSize%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getTotalSize()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `totalSizeLastVersion` to the URL query string
        if (getTotalSizeLastVersion() != null) {
            joiner.add(String.format("%stotalSizeLastVersion%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getTotalSizeLastVersion()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `blockParts` to the URL query string
        if (getBlockParts() != null) {
            joiner.add(String.format("%sblockParts%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getBlockParts()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `blocks` to the URL query string
        if (getBlocks() != null) {
            joiner.add(String.format("%sblocks%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getBlocks()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `directories` to the URL query string
        if (getDirectories() != null) {
            joiner.add(String.format("%sdirectories%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getDirectories()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `directoryVersions` to the URL query string
        if (getDirectoryVersions() != null) {
            joiner.add(String.format("%sdirectoryVersions%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getDirectoryVersions()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `recentError` to the URL query string
        if (getRecentError() != null) {
            joiner.add(String.format("%srecentError%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getRecentError()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        return joiner.toString();
    }
}

