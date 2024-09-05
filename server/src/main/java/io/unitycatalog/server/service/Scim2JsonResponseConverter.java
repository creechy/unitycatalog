package io.unitycatalog.server.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.annotation.Nullable;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.ResponseConverterFunction;

public class Scim2JsonResponseConverter implements ResponseConverterFunction {

  /*
   Okta doesn't appear to like fields with nulls in the JSON coming back from
   the POST method (aka create-user). Get errors like

   Expression Evaluation Error occurred for schema property: primaryPhone. Please verify
   your SCIM property externalName in the Profile Editor. Error:EL1015E: Cannot
   perform selection on input data of type 'null'

   So this is to serialize the response and suppress null fields. It should be ok for us,
   cause there isn't currently a case where we'd want to set a value to null anyways.
  */

  private ObjectMapper mapper;

  public Scim2JsonResponseConverter() {
    mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
  }

  @Override
  public HttpResponse convertResponse(
      ServiceRequestContext ctx,
      ResponseHeaders headers,
      @Nullable Object result,
      HttpHeaders trailers)
      throws Exception {
    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    return HttpResponse.of(headers, HttpData.wrap(mapper.writeValueAsBytes(result)), trailers);
  }
}
