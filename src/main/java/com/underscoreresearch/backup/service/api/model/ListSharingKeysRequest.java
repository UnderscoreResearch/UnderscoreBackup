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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * ListSharingKeysRequest
 */
@JsonPropertyOrder({
  ListSharingKeysRequest.JSON_PROPERTY_TARGET_ACCOUNT_EMAIL_HASH
})
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class ListSharingKeysRequest {
  public static final String JSON_PROPERTY_TARGET_ACCOUNT_EMAIL_HASH = "targetAccountEmailHash";
  private String targetAccountEmailHash;

  public ListSharingKeysRequest() { 
  }

  public ListSharingKeysRequest targetAccountEmailHash(String targetAccountEmailHash) {
    this.targetAccountEmailHash = targetAccountEmailHash;
    return this;
  }

   /**
   * Target account of email hash (Base64 URL encoded).
   * @return targetAccountEmailHash
  **/
  @jakarta.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_TARGET_ACCOUNT_EMAIL_HASH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getTargetAccountEmailHash() {
    return targetAccountEmailHash;
  }


  @JsonProperty(JSON_PROPERTY_TARGET_ACCOUNT_EMAIL_HASH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setTargetAccountEmailHash(String targetAccountEmailHash) {
    this.targetAccountEmailHash = targetAccountEmailHash;
  }


  /**
   * Return true if this ListSharingKeysRequest object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ListSharingKeysRequest listSharingKeysRequest = (ListSharingKeysRequest) o;
    return Objects.equals(this.targetAccountEmailHash, listSharingKeysRequest.targetAccountEmailHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetAccountEmailHash);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ListSharingKeysRequest {\n");
    sb.append("    targetAccountEmailHash: ").append(toIndentedString(targetAccountEmailHash)).append("\n");
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

    // add `targetAccountEmailHash` to the URL query string
    if (getTargetAccountEmailHash() != null) {
      joiner.add(String.format("%stargetAccountEmailHash%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getTargetAccountEmailHash()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    return joiner.toString();
  }
}

