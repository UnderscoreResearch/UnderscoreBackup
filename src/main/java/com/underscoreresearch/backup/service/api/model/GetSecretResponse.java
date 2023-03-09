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

import java.util.Objects;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * GetSecretResponse
 */
@JsonPropertyOrder({
  GetSecretResponse.JSON_PROPERTY_AVAILABLE,
  GetSecretResponse.JSON_PROPERTY_SECRET
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2023-03-08T21:58:23.489056400-08:00[America/Los_Angeles]")
public class GetSecretResponse {
  public static final String JSON_PROPERTY_AVAILABLE = "available";
  private Boolean available;

  public static final String JSON_PROPERTY_SECRET = "secret";
  private String secret;

  public GetSecretResponse() { 
  }

  public GetSecretResponse available(Boolean available) {
    this.available = available;
    return this;
  }

   /**
   * True if the secret is available to be fetched using the emailHash provided.
   * @return available
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "True if the secret is available to be fetched using the emailHash provided.")
  @JsonProperty(JSON_PROPERTY_AVAILABLE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Boolean getAvailable() {
    return available;
  }


  @JsonProperty(JSON_PROPERTY_AVAILABLE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setAvailable(Boolean available) {
    this.available = available;
  }


  public GetSecretResponse secret(String secret) {
    this.secret = secret;
    return this;
  }

   /**
   * Actual secret if collected using called with valid code and codeVerifier.
   * @return secret
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "Actual secret if collected using called with valid code and codeVerifier.")
  @JsonProperty(JSON_PROPERTY_SECRET)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public String getSecret() {
    return secret;
  }


  @JsonProperty(JSON_PROPERTY_SECRET)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setSecret(String secret) {
    this.secret = secret;
  }


  /**
   * Return true if this GetSecretResponse object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GetSecretResponse getSecretResponse = (GetSecretResponse) o;
    return Objects.equals(this.available, getSecretResponse.available) &&
        Objects.equals(this.secret, getSecretResponse.secret);
  }

  @Override
  public int hashCode() {
    return Objects.hash(available, secret);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class GetSecretResponse {\n");
    sb.append("    available: ").append(toIndentedString(available)).append("\n");
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

}

