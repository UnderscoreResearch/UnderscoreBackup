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
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * ListSourcesResponse
 */
@JsonPropertyOrder({
  ListSourcesResponse.JSON_PROPERTY_SOURCES
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2023-03-08T21:58:23.489056400-08:00[America/Los_Angeles]")
public class ListSourcesResponse {
  public static final String JSON_PROPERTY_SOURCES = "sources";
  private List<SourceResponse> sources = new ArrayList<>();

  public ListSourcesResponse() { 
  }

  public ListSourcesResponse sources(List<SourceResponse> sources) {
    this.sources = sources;
    return this;
  }

  public ListSourcesResponse addSourcesItem(SourceResponse sourcesItem) {
    this.sources.add(sourcesItem);
    return this;
  }

   /**
   * List of sources.
   * @return sources
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "List of sources.")
  @JsonProperty(JSON_PROPERTY_SOURCES)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public List<SourceResponse> getSources() {
    return sources;
  }


  @JsonProperty(JSON_PROPERTY_SOURCES)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setSources(List<SourceResponse> sources) {
    this.sources = sources;
  }


  /**
   * Return true if this ListSourcesResponse object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ListSourcesResponse listSourcesResponse = (ListSourcesResponse) o;
    return Objects.equals(this.sources, listSourcesResponse.sources);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sources);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ListSourcesResponse {\n");
    sb.append("    sources: ").append(toIndentedString(sources)).append("\n");
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
