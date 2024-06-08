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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * FileListResponse
 */
@JsonPropertyOrder({
        FileListResponse.JSON_PROPERTY_FILES,
        FileListResponse.JSON_PROPERTY_COMPLETED
})
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class FileListResponse {
    public static final String JSON_PROPERTY_FILES = "files";
    public static final String JSON_PROPERTY_COMPLETED = "completed";
    private List<String> files = new ArrayList<>();
    private Boolean completed;

    public FileListResponse() {
    }

    public FileListResponse files(List<String> files) {
        this.files = files;
        return this;
    }

    public FileListResponse addFilesItem(String filesItem) {
        if (this.files == null) {
            this.files = new ArrayList<>();
        }
        this.files.add(filesItem);
        return this;
    }

    /**
     * List of files.
     *
     * @return files
     **/
    @jakarta.annotation.Nonnull
    @JsonProperty(JSON_PROPERTY_FILES)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)

    public List<String> getFiles() {
        return files;
    }


    @JsonProperty(JSON_PROPERTY_FILES)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public void setFiles(List<String> files) {
        this.files = files;
    }


    public FileListResponse completed(Boolean completed) {
        this.completed = completed;
        return this;
    }

    /**
     * If false there are more files to be fetched. Use the last file of the previous response as the start parameter to fetch the next page.
     *
     * @return completed
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_COMPLETED)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public Boolean getCompleted() {
        return completed;
    }


    @JsonProperty(JSON_PROPERTY_COMPLETED)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }


    /**
     * Return true if this FileListResponse object is equal to o.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileListResponse fileListResponse = (FileListResponse) o;
        return Objects.equals(this.files, fileListResponse.files) &&
                Objects.equals(this.completed, fileListResponse.completed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(files, completed);
    }

    @Override
    public String toString() {
        String sb = "class FileListResponse {\n" +
                "    files: " + toIndentedString(files) + "\n" +
                "    completed: " + toIndentedString(completed) + "\n" +
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
            for (int i = 0; i < getFiles().size(); i++) {
                joiner.add(String.format("%sfiles%s%s=%s", prefix, suffix,
                        "".equals(suffix) ? "" : String.format("%s%d%s", containerPrefix, i, containerSuffix),
                        URLEncoder.encode(String.valueOf(getFiles().get(i)), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
            }
        }

        // add `completed` to the URL query string
        if (getCompleted() != null) {
            joiner.add(String.format("%scompleted%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getCompleted()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        return joiner.toString();
    }
}

