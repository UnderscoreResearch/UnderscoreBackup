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
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * SourceUsage
 */
@JsonPropertyOrder({
  SourceUsage.JSON_PROPERTY_TIME,
  SourceUsage.JSON_PROPERTY_USAGE
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2023-02-21T23:05:24.673599500-08:00[America/Los_Angeles]")
public class SourceUsage {
  public static final String JSON_PROPERTY_TIME = "time";
  private BigDecimal time;

  public static final String JSON_PROPERTY_USAGE = "usage";
  private BigDecimal usage;

  public SourceUsage() { 
  }

  public SourceUsage time(BigDecimal time) {
    this.time = time;
    return this;
  }

   /**
   * Epoch timestamp for start of period.
   * @return time
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "Epoch timestamp for start of period.")
  @JsonProperty(JSON_PROPERTY_TIME)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public BigDecimal getTime() {
    return time;
  }


  @JsonProperty(JSON_PROPERTY_TIME)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setTime(BigDecimal time) {
    this.time = time;
  }


  public SourceUsage usage(BigDecimal usage) {
    this.usage = usage;
    return this;
  }

   /**
   * Max usage in GB for the period.
   * @return usage
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "Max usage in GB for the period.")
  @JsonProperty(JSON_PROPERTY_USAGE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public BigDecimal getUsage() {
    return usage;
  }


  @JsonProperty(JSON_PROPERTY_USAGE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setUsage(BigDecimal usage) {
    this.usage = usage;
  }


  /**
   * Return true if this SourceUsage object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SourceUsage sourceUsage = (SourceUsage) o;
    return Objects.equals(this.time, sourceUsage.time) &&
        Objects.equals(this.usage, sourceUsage.usage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(time, usage);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SourceUsage {\n");
    sb.append("    time: ").append(toIndentedString(time)).append("\n");
    sb.append("    usage: ").append(toIndentedString(usage)).append("\n");
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

