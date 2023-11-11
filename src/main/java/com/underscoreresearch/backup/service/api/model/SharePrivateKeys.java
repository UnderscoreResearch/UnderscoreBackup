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
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * SharePrivateKeys
 */
@JsonPropertyOrder({
  SharePrivateKeys.JSON_PROPERTY_PUBLIC_KEY,
  SharePrivateKeys.JSON_PROPERTY_ENCRYPTED_KEY
})
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class SharePrivateKeys {
  public static final String JSON_PROPERTY_PUBLIC_KEY = "publicKey";
  private String publicKey;

  public static final String JSON_PROPERTY_ENCRYPTED_KEY = "encryptedKey";
  private String encryptedKey;

  public SharePrivateKeys() { 
  }

  public SharePrivateKeys publicKey(String publicKey) {
    this.publicKey = publicKey;
    return this;
  }

   /**
   * Public encryption key with which the encrypted private key is encrypted.
   * @return publicKey
  **/
  @jakarta.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_PUBLIC_KEY)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getPublicKey() {
    return publicKey;
  }


  @JsonProperty(JSON_PROPERTY_PUBLIC_KEY)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }


  public SharePrivateKeys encryptedKey(String encryptedKey) {
    this.encryptedKey = encryptedKey;
    return this;
  }

   /**
   * Encryption of private key used to encrypt share.
   * @return encryptedKey
  **/
  @jakarta.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_ENCRYPTED_KEY)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getEncryptedKey() {
    return encryptedKey;
  }


  @JsonProperty(JSON_PROPERTY_ENCRYPTED_KEY)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setEncryptedKey(String encryptedKey) {
    this.encryptedKey = encryptedKey;
  }


  /**
   * Return true if this SharePrivateKeys object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SharePrivateKeys sharePrivateKeys = (SharePrivateKeys) o;
    return Objects.equals(this.publicKey, sharePrivateKeys.publicKey) &&
        Objects.equals(this.encryptedKey, sharePrivateKeys.encryptedKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKey, encryptedKey);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SharePrivateKeys {\n");
    sb.append("    publicKey: ").append(toIndentedString(publicKey)).append("\n");
    sb.append("    encryptedKey: ").append(toIndentedString(encryptedKey)).append("\n");
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

    // add `publicKey` to the URL query string
    if (getPublicKey() != null) {
      joiner.add(String.format("%spublicKey%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getPublicKey()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `encryptedKey` to the URL query string
    if (getEncryptedKey() != null) {
      joiner.add(String.format("%sencryptedKey%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getEncryptedKey()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    return joiner.toString();
  }
}

