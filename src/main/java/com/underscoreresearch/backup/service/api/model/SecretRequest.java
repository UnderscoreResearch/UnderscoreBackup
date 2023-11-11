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
 * SecretRequest
 */
@JsonPropertyOrder({
  SecretRequest.JSON_PROPERTY_EMAIL_HASH,
  SecretRequest.JSON_PROPERTY_SECRET
})
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class SecretRequest {
  public static final String JSON_PROPERTY_EMAIL_HASH = "emailHash";
  private String emailHash;

  public static final String JSON_PROPERTY_SECRET = "secret";
  private String secret;

  public SecretRequest() { 
  }

  public SecretRequest emailHash(String emailHash) {
    this.emailHash = emailHash;
    return this;
  }

   /**
   * Base64url encoding of SHA256 hash of email used to encrypt the secret.
   * @return emailHash
  **/
  @jakarta.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_EMAIL_HASH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getEmailHash() {
    return emailHash;
  }


  @JsonProperty(JSON_PROPERTY_EMAIL_HASH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setEmailHash(String emailHash) {
    this.emailHash = emailHash;
  }


  public SecretRequest secret(String secret) {
    this.secret = secret;
    return this;
  }

   /**
   * Encrypted secret.
   * @return secret
  **/
  @jakarta.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_SECRET)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getSecret() {
    return secret;
  }


  @JsonProperty(JSON_PROPERTY_SECRET)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setSecret(String secret) {
    this.secret = secret;
  }


  /**
   * Return true if this SecretRequest object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SecretRequest secretRequest = (SecretRequest) o;
    return Objects.equals(this.emailHash, secretRequest.emailHash) &&
        Objects.equals(this.secret, secretRequest.secret);
  }

  @Override
  public int hashCode() {
    return Objects.hash(emailHash, secret);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SecretRequest {\n");
    sb.append("    emailHash: ").append(toIndentedString(emailHash)).append("\n");
    sb.append("    secret: ").append(toIndentedString(secret)).append("\n");
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

    // add `emailHash` to the URL query string
    if (getEmailHash() != null) {
      joiner.add(String.format("%semailHash%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getEmailHash()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `secret` to the URL query string
    if (getSecret() != null) {
      joiner.add(String.format("%ssecret%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getSecret()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    return joiner.toString();
  }
}

