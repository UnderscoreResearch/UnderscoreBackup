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
 * GenerateTokenRequest
 */
@JsonPropertyOrder({
  GenerateTokenRequest.JSON_PROPERTY_CLIENT_ID,
  GenerateTokenRequest.JSON_PROPERTY_CODE,
  GenerateTokenRequest.JSON_PROPERTY_CODE_VERIFIER
})
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class GenerateTokenRequest {
  public static final String JSON_PROPERTY_CLIENT_ID = "clientId";
  private String clientId;

  public static final String JSON_PROPERTY_CODE = "code";
  private String code;

  public static final String JSON_PROPERTY_CODE_VERIFIER = "codeVerifier";
  private String codeVerifier;

  public GenerateTokenRequest() { 
  }

  public GenerateTokenRequest clientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

   /**
   * Client ID of authentication request.
   * @return clientId
  **/
  @jakarta.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_CLIENT_ID)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getClientId() {
    return clientId;
  }


  @JsonProperty(JSON_PROPERTY_CLIENT_ID)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setClientId(String clientId) {
    this.clientId = clientId;
  }


  public GenerateTokenRequest code(String code) {
    this.code = code;
    return this;
  }

   /**
   * Code returned by a previous call to clientAuthCode.
   * @return code
  **/
  @jakarta.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_CODE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getCode() {
    return code;
  }


  @JsonProperty(JSON_PROPERTY_CODE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setCode(String code) {
    this.code = code;
  }


  public GenerateTokenRequest codeVerifier(String codeVerifier) {
    this.codeVerifier = codeVerifier;
    return this;
  }

   /**
   * The original string that was used to generate the codeChallenge in the previous clientAuthCode request.
   * @return codeVerifier
  **/
  @jakarta.annotation.Nonnull
  @JsonProperty(JSON_PROPERTY_CODE_VERIFIER)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getCodeVerifier() {
    return codeVerifier;
  }


  @JsonProperty(JSON_PROPERTY_CODE_VERIFIER)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setCodeVerifier(String codeVerifier) {
    this.codeVerifier = codeVerifier;
  }


  /**
   * Return true if this GenerateTokenRequest object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GenerateTokenRequest generateTokenRequest = (GenerateTokenRequest) o;
    return Objects.equals(this.clientId, generateTokenRequest.clientId) &&
        Objects.equals(this.code, generateTokenRequest.code) &&
        Objects.equals(this.codeVerifier, generateTokenRequest.codeVerifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clientId, code, codeVerifier);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class GenerateTokenRequest {\n");
    sb.append("    clientId: ").append(toIndentedString(clientId)).append("\n");
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
    sb.append("    codeVerifier: ").append(toIndentedString(codeVerifier)).append("\n");
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

    // add `clientId` to the URL query string
    if (getClientId() != null) {
      joiner.add(String.format("%sclientId%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getClientId()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `code` to the URL query string
    if (getCode() != null) {
      joiner.add(String.format("%scode%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getCode()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    // add `codeVerifier` to the URL query string
    if (getCodeVerifier() != null) {
      joiner.add(String.format("%scodeVerifier%s=%s", prefix, suffix, URLEncoder.encode(String.valueOf(getCodeVerifier()), StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
    }

    return joiner.toString();
  }
}

