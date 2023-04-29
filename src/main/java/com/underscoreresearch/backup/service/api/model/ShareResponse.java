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
import java.util.StringJoiner;
import java.util.Objects;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import com.underscoreresearch.backup.service.api.model.SharePrivateKeys;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * ShareResponse
 */
@JsonPropertyOrder({
  ShareResponse.JSON_PROPERTY_NAME,
  ShareResponse.JSON_PROPERTY_SHARE_ID,
  ShareResponse.JSON_PROPERTY_SOURCE_ID,
  ShareResponse.JSON_PROPERTY_DESTINATION,
  ShareResponse.JSON_PROPERTY_PRIVATE_KEYS
})
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2023-04-28T23:26:00.575807500-07:00[America/Los_Angeles]")
public class ShareResponse {
  public static final String JSON_PROPERTY_NAME = "name";
  private String name;

  public static final String JSON_PROPERTY_SHARE_ID = "shareId";
  private String shareId;

  public static final String JSON_PROPERTY_SOURCE_ID = "sourceId";
  private String sourceId;

  public static final String JSON_PROPERTY_DESTINATION = "destination";
  private String destination;

  public static final String JSON_PROPERTY_PRIVATE_KEYS = "privateKeys";
  private List<SharePrivateKeys> privateKeys = new ArrayList<>();

  public ShareResponse() { 
  }

  public ShareResponse name(String name) {
    this.name = name;
    return this;
  }

   /**
   * Name of share.
   * @return name
  **/
  @javax.annotation.Nonnull
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


  public ShareResponse shareId(String shareId) {
    this.shareId = shareId;
    return this;
  }

   /**
   * Unique identifier of share.
   * @return shareId
  **/
  @javax.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_SHARE_ID)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getShareId() {
    return shareId;
  }


  @JsonProperty(JSON_PROPERTY_SHARE_ID)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setShareId(String shareId) {
    this.shareId = shareId;
  }


  public ShareResponse sourceId(String sourceId) {
    this.sourceId = sourceId;
    return this;
  }

   /**
   * Unique identifier of source being shared.
   * @return sourceId
  **/
  @javax.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_SOURCE_ID)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getSourceId() {
    return sourceId;
  }


  @JsonProperty(JSON_PROPERTY_SOURCE_ID)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }


  public ShareResponse destination(String destination) {
    this.destination = destination;
    return this;
  }

   /**
   * Base64 URL encoding of encrypted destination of share.
   * @return destination
  **/
  @javax.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_DESTINATION)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getDestination() {
    return destination;
  }


  @JsonProperty(JSON_PROPERTY_DESTINATION)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setDestination(String destination) {
    this.destination = destination;
  }


  public ShareResponse privateKeys(List<SharePrivateKeys> privateKeys) {
    this.privateKeys = privateKeys;
    return this;
  }

  public ShareResponse addPrivateKeysItem(SharePrivateKeys privateKeysItem) {
    if (this.privateKeys == null) {
      this.privateKeys = new ArrayList<>();
    }
    this.privateKeys.add(privateKeysItem);
    return this;
  }

   /**
   * List of private keys to the destination encrypted by different public keys.
   * @return privateKeys
  **/
  @javax.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_PRIVATE_KEYS)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public List<SharePrivateKeys> getPrivateKeys() {
    return privateKeys;
  }


  @JsonProperty(JSON_PROPERTY_PRIVATE_KEYS)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setPrivateKeys(List<SharePrivateKeys> privateKeys) {
    this.privateKeys = privateKeys;
  }


  /**
   * Return true if this ShareResponse object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ShareResponse shareResponse = (ShareResponse) o;
    return Objects.equals(this.name, shareResponse.name) &&
        Objects.equals(this.shareId, shareResponse.shareId) &&
        Objects.equals(this.sourceId, shareResponse.sourceId) &&
        Objects.equals(this.destination, shareResponse.destination) &&
        Objects.equals(this.privateKeys, shareResponse.privateKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, shareId, sourceId, destination, privateKeys);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ShareResponse {\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    shareId: ").append(toIndentedString(shareId)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    destination: ").append(toIndentedString(destination)).append("\n");
    sb.append("    privateKeys: ").append(toIndentedString(privateKeys)).append("\n");
    sb.append("}");
    return sb.toString();
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
      joiner.add(String.format("%sname%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getName()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `shareId` to the URL query string
    if (getShareId() != null) {
      joiner.add(String.format("%sshareId%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getShareId()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `sourceId` to the URL query string
    if (getSourceId() != null) {
      joiner.add(String.format("%ssourceId%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getSourceId()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `destination` to the URL query string
    if (getDestination() != null) {
      joiner.add(String.format("%sdestination%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getDestination()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `privateKeys` to the URL query string
    if (getPrivateKeys() != null) {
      for (int i = 0; i < getPrivateKeys().size(); i++) {
        if (getPrivateKeys().get(i) != null) {
          joiner.add(getPrivateKeys().get(i).toUrlQueryString(String.format("%sprivateKeys%s%s", prefix, suffix,
          "".equals(suffix) ? "" : String.format("%s%d%s", containerPrefix, i, containerSuffix))));
        }
      }
    }

    return joiner.toString();
  }
}

