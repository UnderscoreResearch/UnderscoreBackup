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

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.StringJoiner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * ReleaseFileItem
 */
@JsonPropertyOrder({
        ReleaseFileItem.JSON_PROPERTY_NAME,
        ReleaseFileItem.JSON_PROPERTY_SIZE,
        ReleaseFileItem.JSON_PROPERTY_URL,
        ReleaseFileItem.JSON_PROPERTY_SECURE_URL
})
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class ReleaseFileItem {
    public static final String JSON_PROPERTY_NAME = "name";
    public static final String JSON_PROPERTY_SIZE = "size";
    public static final String JSON_PROPERTY_URL = "url";
    public static final String JSON_PROPERTY_SECURE_URL = "secureUrl";
    private String name;
    private BigDecimal size;
    private String url;
    private String secureUrl;

    public ReleaseFileItem() {
    }

    public ReleaseFileItem name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Name of download.
     *
     * @return name
     **/
    @jakarta.annotation.Nonnull
    @JsonProperty(JSON_PROPERTY_NAME)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)

    public String getName() {
        return name;
    }


    @JsonProperty(JSON_PROPERTY_NAME)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public void setName(String name) {
        this.name = name;
    }


    public ReleaseFileItem size(BigDecimal size) {
        this.size = size;
        return this;
    }

    /**
     * Size of download.
     *
     * @return size
     **/
    @jakarta.annotation.Nonnull
    @JsonProperty(JSON_PROPERTY_SIZE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)

    public BigDecimal getSize() {
        return size;
    }


    @JsonProperty(JSON_PROPERTY_SIZE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public void setSize(BigDecimal size) {
        this.size = size;
    }


    public ReleaseFileItem url(String url) {
        this.url = url;
        return this;
    }

    /**
     * URL for download.
     *
     * @return url
     **/
    @jakarta.annotation.Nonnull
    @JsonProperty(JSON_PROPERTY_URL)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)

    public String getUrl() {
        return url;
    }


    @JsonProperty(JSON_PROPERTY_URL)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public void setUrl(String url) {
        this.url = url;
    }


    public ReleaseFileItem secureUrl(String secureUrl) {
        this.secureUrl = secureUrl;
        return this;
    }

    /**
     * Non-public URL to download release using token if needed.
     *
     * @return secureUrl
     **/
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_SECURE_URL)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

    public String getSecureUrl() {
        return secureUrl;
    }


    @JsonProperty(JSON_PROPERTY_SECURE_URL)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setSecureUrl(String secureUrl) {
        this.secureUrl = secureUrl;
    }


    /**
     * Return true if this ReleaseFileItem object is equal to o.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ReleaseFileItem releaseFileItem = (ReleaseFileItem) o;
        return Objects.equals(this.name, releaseFileItem.name) &&
                Objects.equals(this.size, releaseFileItem.size) &&
                Objects.equals(this.url, releaseFileItem.url) &&
                Objects.equals(this.secureUrl, releaseFileItem.secureUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, size, url, secureUrl);
    }

    @Override
    public String toString() {
        String sb = "class ReleaseFileItem {\n" +
                "    name: " + toIndentedString(name) + "\n" +
                "    size: " + toIndentedString(size) + "\n" +
                "    url: " + toIndentedString(url) + "\n" +
                "    secureUrl: " + toIndentedString(secureUrl) + "\n" +
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

        // add `name` to the URL query string
        if (getName() != null) {
            joiner.add(String.format("%sname%s=%s", prefix, suffix, URLEncoder.encode(getName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `size` to the URL query string
        if (getSize() != null) {
            joiner.add(String.format("%ssize%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getSize()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `url` to the URL query string
        if (getUrl() != null) {
            joiner.add(String.format("%surl%s=%s", prefix, suffix, URLEncoder.encode(getUrl(), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        // add `secureUrl` to the URL query string
        if (getSecureUrl() != null) {
            joiner.add(String.format("%ssecureUrl%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getSecureUrl()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        }

        return joiner.toString();
    }
}

