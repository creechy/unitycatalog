package io.unitycatalog.cli;

import static io.unitycatalog.cli.utils.CliUtils.postProcessAndPrintOutput;
import static java.net.HttpURLConnection.HTTP_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.unitycatalog.cli.utils.CliUtils;
import io.unitycatalog.client.ApiClient;
import io.unitycatalog.client.ApiException;
import io.unitycatalog.client.model.Privilege;
import io.unitycatalog.client.model.ResourceType;
import io.unitycatalog.client.model.UpdateAuthorizationChange;
import io.unitycatalog.client.model.UpdateAuthorizationRequest;
import io.unitycatalog.client.model.UpdateAuthorizationResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.json.JSONObject;

public class PermissionCli {
  private static final ObjectMapper objectMapper = CliUtils.getObjectMapper();

  public static void handle(CommandLine cmd, ApiClient apiClient)
      throws JsonProcessingException, ApiException {
    String[] subArgs = cmd.getArgs();
    String subCommand = subArgs[1];
    JSONObject json = CliUtils.createJsonFromOptions(cmd);
    String output = CliUtils.EMPTY;
    switch (subCommand) {
      case CliUtils.LIST:
        output = listPermissions(apiClient, json);
        break;
      case CliUtils.CREATE:
        output = addPermission(apiClient, json);
        break;
      case CliUtils.DELETE:
        output = removePermission(apiClient, json);
        break;
      default:
        CliUtils.printEntityHelp(CliUtils.CATALOG);
    }
    postProcessAndPrintOutput(cmd, output, subCommand);
  }

  private static String listPermissions(ApiClient apiClient, JSONObject json) throws ApiException {

    String resource = json.optString("resource", null);
    String fullName = json.optString("full_name", null);

    // Check that it's a valid resource type
    ResourceType.fromValue(resource.toUpperCase());

    URI authorizationEndpoint =
        URI.create(apiClient.getBaseUri() + "/permissions/" + resource + "/" + fullName);

    HttpRequest request = HttpRequest.newBuilder().uri(authorizationEndpoint).GET().build();

    try {
      HttpResponse<String> response =
          apiClient.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != HTTP_OK) {
        throw new ApiException("Error retrieving permissions: " + response.body());
      }

      UpdateAuthorizationResponse authResponse =
          objectMapper.readValue(response.body(), UpdateAuthorizationResponse.class);
      return objectMapper.writeValueAsString(authResponse.getPrivilegeAssignments());
    } catch (InterruptedException | IOException e) {
      throw new ApiException(e);
    }
  }

  private static String addPermission(ApiClient apiClient, JSONObject json)
      throws JsonProcessingException, ApiException {

    String email = json.optString("email", null);
    String privilege = json.optString("privilege", null);
    String resource = json.optString("resource", null);
    String fullName = json.optString("full_name", null);

    UpdateAuthorizationChange change = new UpdateAuthorizationChange();
    change.principal(email);
    change.add(List.of(Privilege.valueOf(privilege)));
    UpdateAuthorizationRequest updateRequest = new UpdateAuthorizationRequest();
    updateRequest.addChangesItem(change);

    return changePermission(apiClient, resource, fullName, updateRequest);
  }

  private static String removePermission(ApiClient apiClient, JSONObject json)
      throws ApiException, JsonProcessingException {

    String email = json.optString("email", null);
    String privilege = json.optString("privilege", null);
    String resource = json.optString("resource", null);
    String fullName = json.optString("full_name", null);

    UpdateAuthorizationChange change = new UpdateAuthorizationChange();
    change.principal(email);
    change.remove(List.of(Privilege.valueOf(privilege)));
    UpdateAuthorizationRequest updateRequest = new UpdateAuthorizationRequest();
    updateRequest.addChangesItem(change);

    return changePermission(apiClient, resource, fullName, updateRequest);
  }

  private static String changePermission(
      ApiClient apiClient,
      String resource,
      String fullName,
      UpdateAuthorizationRequest updateRequest)
      throws JsonProcessingException, ApiException {

    // Check that it's a valid resource type
    ResourceType.fromValue(resource.toUpperCase());

    URI authorizationEndpoint =
        URI.create(apiClient.getBaseUri() + "/permissions/" + resource + "/" + fullName);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(authorizationEndpoint)
            .setHeader("content-type", "application/json")
            .method(
                "PATCH",
                HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(updateRequest)))
            .build();

    try {
      HttpResponse<String> response =
          apiClient.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != HTTP_OK) {
        throw new ApiException("Error changing authorization: " + response.body());
      }

      UpdateAuthorizationResponse authResponse =
          objectMapper.readValue(response.body(), UpdateAuthorizationResponse.class);
      return objectMapper.writeValueAsString(authResponse.getPrivilegeAssignments());
    } catch (InterruptedException | IOException e) {
      throw new ApiException(e);
    }
  }
}
