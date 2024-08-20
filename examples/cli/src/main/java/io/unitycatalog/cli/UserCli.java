package io.unitycatalog.cli;

import static io.unitycatalog.cli.utils.CliUtils.postProcessAndPrintOutput;
import static io.unitycatalog.client.model.User.StateEnum.DISABLED;
import static io.unitycatalog.client.model.User.StateEnum.ENABLED;
import static java.net.HttpURLConnection.HTTP_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.UserResource;
import io.unitycatalog.cli.utils.CliUtils;
import io.unitycatalog.client.ApiClient;
import io.unitycatalog.client.ApiException;
import io.unitycatalog.client.model.User;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.json.JSONObject;

public class UserCli {
  private static final ObjectMapper objectMapper = CliUtils.getObjectMapper();

  public static String CONTROL_PATH = "/api/1.0/unity-control";
  public static String USER_PATH = "/scim2/Users";

  public static void handle(CommandLine cmd, ApiClient apiClient)
      throws JsonProcessingException, ApiException {
    apiClient.setBasePath(CONTROL_PATH);
    String[] subArgs = cmd.getArgs();
    String subCommand = subArgs[1];
    JSONObject json = CliUtils.createJsonFromOptions(cmd);
    String output = CliUtils.EMPTY;
    switch (subCommand) {
      case CliUtils.CREATE:
        output = createUser(apiClient, json);
        break;
      case CliUtils.LIST:
        output = listUsers(apiClient, json);
        break;
      case CliUtils.GET:
        output = getUser(apiClient, json);
        break;
      case CliUtils.UPDATE:
        output = updateUser(apiClient, json);
        break;
      case CliUtils.DELETE:
        output = deleteUser(apiClient, json);
        break;
      default:
        CliUtils.printEntityHelp(CliUtils.CATALOG);
    }
    postProcessAndPrintOutput(cmd, output, subCommand);
  }

  private static String createUser(ApiClient apiClient, JSONObject json)
      throws JsonProcessingException, ApiException {

    User user = objectMapper.readValue(json.toString(), User.class);

    URI usersEndpoint = URI.create(apiClient.getBaseUri() + USER_PATH);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(usersEndpoint)
            .setHeader("content-type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(toUserResource(user))))
            .build();

    try {
      HttpResponse<String> response =
          apiClient.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != HTTP_OK) {
        throw new ApiException("Error creating user: " + response.body());
      }
      return objectMapper.writeValueAsString(
          toUser(objectMapper.readValue(response.body(), UserResource.class)));
    } catch (InterruptedException | IOException e) {
      throw new ApiException(e);
    }
  }

  private static String listUsers(ApiClient apiClient, JSONObject json)
      throws JsonProcessingException, ApiException {

    URI usersEndpoint = URI.create(apiClient.getBaseUri() + USER_PATH);

    return objectMapper.writeValueAsString(getUsers(apiClient, usersEndpoint));
  }

  private static String getUser(ApiClient apiClient, JSONObject json)
      throws JsonProcessingException, ApiException {

    User user = objectMapper.readValue(json.toString(), User.class);

    return objectMapper.writeValueAsString(getUser(apiClient, user.getEmail()));
  }

  private static String updateUser(ApiClient apiClient, JSONObject json)
      throws JsonProcessingException, ApiException {

    User user = objectMapper.readValue(json.toString(), User.class);

    User userRecord = getUser(apiClient, user.getEmail());

    if (user.getName() != null) {
      userRecord.setName(user.getName());
    }
    if (user.getExternalId() != null) {
      userRecord.setExternalId(user.getExternalId());
    }

    URI usersEndpoint = URI.create(apiClient.getBaseUri() + USER_PATH + "/" + userRecord.getId());

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(usersEndpoint)
            .setHeader("content-type", "application/json")
            .PUT(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(toUserResource(userRecord))))
            .build();

    try {
      HttpResponse<String> response =
          apiClient.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != HTTP_OK) {
        throw new ApiException("Error creating user: " + response.body());
      }
      return objectMapper.writeValueAsString(
          toUser(objectMapper.readValue(response.body(), UserResource.class)));
    } catch (InterruptedException | IOException e) {
      throw new ApiException(e);
    }
  }

  private static String deleteUser(ApiClient apiClient, JSONObject json)
      throws ApiException, JsonProcessingException {
    User user = objectMapper.readValue(json.toString(), User.class);

    User userRecord = getUser(apiClient, user.getEmail());

    URI usersEndpoint = URI.create(apiClient.getBaseUri() + USER_PATH + "/" + userRecord.getId());

    HttpRequest request = HttpRequest.newBuilder().uri(usersEndpoint).DELETE().build();

    try {
      HttpResponse<String> response =
          apiClient.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != HTTP_OK) {
        throw new ApiException("Error creating user: " + response.body());
      }
      return "";
    } catch (InterruptedException | IOException e) {
      throw new ApiException(e);
    }
  }

  private static List<User> getUsers(ApiClient apiClient, URI usersEndpoint) throws ApiException {
    HttpRequest request = HttpRequest.newBuilder().uri(usersEndpoint).GET().build();
    try {
      HttpResponse<String> response =
          apiClient.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != HTTP_OK) {
        throw new ApiException("Error creating user: " + response.body());
      }
      List<UserResource> userResources =
          objectMapper.readValue(response.body(), new TypeReference<List<UserResource>>() {});
      return userResources.stream().map(UserCli::toUser).toList();
    } catch (InterruptedException | IOException e) {
      throw new ApiException(e);
    }
  }

  private static User getUser(ApiClient apiClient, String email) throws ApiException {
    String filter = "userName eq \"" + email + "\"";

    URI usersEndpoint =
        URI.create(
            apiClient.getBaseUri()
                + USER_PATH
                + "?filter="
                + URLEncoder.encode(filter, StandardCharsets.UTF_8));

    List<User> users = getUsers(apiClient, usersEndpoint);

    if (users.size() > 1) {
      throw new ApiException("Found more than one user!");
    } else if (users.isEmpty()) {
      throw new ApiException("User not found.");
    } else {
      return users.get(0);
    }
  }

  private static UserResource toUserResource(User user) {
    UserResource userResource = new UserResource();
    userResource.setId(user.getId());
    userResource.setDisplayName(user.getName());
    userResource.setEmails(new Email().setPrimary(true).setValue(user.getEmail()));
    userResource.setExternalId(user.getExternalId());
    userResource.setActive(user.getState() == null || user.getState().equals(ENABLED));
    return userResource;
  }

  private static User toUser(UserResource userResource) {
    Calendar lastModified = userResource.getMeta().getLastModified();

    return new User()
        .id(userResource.getId())
        .name(userResource.getDisplayName())
        .email(userResource.getEmails().get(0).getValue())
        .externalId(userResource.getExternalId())
        .state((userResource.getActive() ? ENABLED : DISABLED))
        .createdAt(userResource.getMeta().getCreated().getTimeInMillis())
        .updatedAt((lastModified != null) ? lastModified.getTimeInMillis() : null);
  }
}
