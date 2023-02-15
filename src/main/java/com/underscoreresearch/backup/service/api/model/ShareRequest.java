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
import com.underscoreresearch.backup.service.api.model.SharePrivateKeys;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * ShareRequest
 */
@JsonPropertyOrder({
  ShareRequest.JSON_PROPERTY_TARGET_ACCOUNT_EMAIL_HASH,
  ShareRequest.JSON_PROPERTY_NAME,
  ShareRequest.JSON_PROPERTY_DESTINATION,
  ShareRequest.JSON_PROPERTY_PRIVATE_KEYS
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2023-02-13T08:26:04.416037100-08:00[America/Los_Angeles]")
public class ShareRequest {
  public static final String JSON_PROPERTY_TARGET_ACCOUNT_EMAIL_HASH = "targetAccountEmailHash";
  private String targetAccountEmailHash;

  public static final String JSON_PROPERTY_NAME = "name";
  private String name;

  public static final String JSON_PROPERTY_DESTINATION = "destination";
  private String destination;

  public static final String JSON_PROPERTY_PRIVATE_KEYS = "privateKeys";
  private List<SharePrivateKeys> privateKeys = new ArrayList<>();

  public ShareRequest() { 
  }

  public ShareRequest targetAccountEmailHash(String targetAccountEmailHash) {
    this.targetAccountEmailHash = targetAccountEmailHash;
    return this;
  }

   /**
   * Target account of email hash (Base64 URL encoded).
   * @return targetAccountEmailHash
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "Target account of email hash (Base64 URL encoded).")
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


  public ShareRequest name(String name) {
    this.name = name;
    return this;
  }

   /**
   * Name of share.
   * @return name
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "Name of share.")
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


  public ShareRequest destination(String destination) {
    this.destination = destination;
    return this;
  }

   /**
   * Base64 URL encoding of encrypted destination of share.
   * @return destination
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "Base64 URL encoding of encrypted destination of share.")
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


  public ShareRequest privateKeys(List<SharePrivateKeys> privateKeys) {
    this.privateKeys = privateKeys;
    return this;
  }

  public ShareRequest addPrivateKeysItem(SharePrivateKeys privateKeysItem) {
    this.privateKeys.add(privateKeysItem);
    return this;
  }

   /**
   * List of private keys to the destination encrypted by different public keys.
   * @return privateKeys
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "List of private keys to the destination encrypted by different public keys.")
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
   * Return true if this ShareRequest object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ShareRequest shareRequest = (ShareRequest) o;
    return Objects.equals(this.targetAccountEmailHash, shareRequest.targetAccountEmailHash) &&
        Objects.equals(this.name, shareRequest.name) &&
        Objects.equals(this.destination, shareRequest.destination) &&
        Objects.equals(this.privateKeys, shareRequest.privateKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetAccountEmailHash, name, destination, privateKeys);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ShareRequest {\n");
    sb.append("    targetAccountEmailHash: ").append(toIndentedString(targetAccountEmailHash)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
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

}

