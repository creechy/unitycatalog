package io.unitycatalog.server.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.annotation.Nullable;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.ResponseConverterFunction;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.Calendar;

public class Scim2JsonResponseConverter implements ResponseConverterFunction {

  private ObjectMapper mapper;

  public Scim2JsonResponseConverter() {
    mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    // mapper.registerModule(new SimpleModule().addSerializer(Calendar.class, new
    // CustomSerializer()));
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
    // return HttpResponse.of(HttpStatus.OK, MediaType.JSON, mapper.writeValueAsBytes(result));
  }

  public class CustomSerializer extends JsonSerializer<Calendar> {
    @Override
    public void serialize(Calendar value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      try {
        gen.writeString(String.valueOf(11111L));
      } catch (DateTimeParseException e) {
        System.err.println(e);
        gen.writeString("");
      }
    }
  }
}
