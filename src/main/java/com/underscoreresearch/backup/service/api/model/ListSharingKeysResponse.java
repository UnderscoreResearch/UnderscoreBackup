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
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * ListSharingKeysResponse
 */
@JsonPropertyOrder({
  ListSharingKeysResponse.JSON_PROPERTY_PUBLIC_KEYS
})
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2023-04-28T23:26:00.575807500-07:00[America/Los_Angeles]")
public class ListSharingKeysResponse {
  public static final String JSON_PROPERTY_PUBLIC_KEYS = "publicKeys";
  private List<String> publicKeys = new ArrayList<>();

  public ListSharingKeysResponse() { 
  }

  public ListSharingKeysResponse publicKeys(List<String> publicKeys) {
    this.publicKeys = publicKeys;
    return this;
  }

  public ListSharingKeysResponse addPublicKeysItem(String publicKeysItem) {
    if (this.publicKeys == null) {
      this.publicKeys = new ArrayList<>();
    }
    this.publicKeys.add(publicKeysItem);
    return this;
  }

   /**
   * List of public keys to encrypt with for given target account.
   * @return publicKeys
  **/
  @javax.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_PUBLIC_KEYS)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public List<String> getPublicKeys() {
    return publicKeys;
  }


  @JsonProperty(JSON_PROPERTY_PUBLIC_KEYS)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setPublicKeys(List<String> publicKeys) {
    this.publicKeys = publicKeys;
  }


  /**
   * Return true if this ListSharingKeysResponse object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ListSharingKeysResponse listSharingKeysResponse = (ListSharingKeysResponse) o;
    return Objects.equals(this.publicKeys, listSharingKeysResponse.publicKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKeys);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ListSharingKeysResponse {\n");
    sb.append("    publicKeys: ").append(toIndentedString(publicKeys)).append("\n");
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

    // add `publicKeys` to the URL query string
    if (getPublicKeys() != null) {
      for (int i = 0; i < getPublicKeys().size(); i++) {
        joiner.add(String.format("%spublicKeys%s%s=%s", prefix, suffix,
            "".equals(suffix) ? "" : String.format("%s%d%s", containerPrefix, i, containerSuffix),
            URLEncoder.encode(String.valueOf(getPublicKeys().get(i)), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
      }
    }

    return joiner.toString();
  }
}

