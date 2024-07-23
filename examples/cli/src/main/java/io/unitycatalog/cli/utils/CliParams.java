package io.unitycatalog.cli.utils;

public enum CliParams {
  IDENTITY_TOKEN("identity_token", "Identity token to authorize", "identityToken"),
  NAME("name", "The name of the entity.", "name"),
  SCHEMA_NAME("schema", "The name of the schema.", "schema_name"),
  CATALOG_NAME("catalog", "The name of the catalog.", "catalog_name"),
  PROPERTIES(
      "properties",
      "The properties of the entity. Need to be in json format. For example: \"{\"key1\": \"value1\", \"key2\": \"value2\"}\".",
      "properties"),
  FULL_NAME(
      "full_name",
      "The full name of the table. The full name is the concatenation of the catalog name, schema name, and table/volume name separated by a dot. For example, catalog_name.schema_name.table_name.",
      "full_name"),
  STORAGE_LOCATION(
      "storage_location",
      "The storage location associated with the table. Need to be specified for external tables.",
      "storage_location"),
  MAX_RESULTS("max_results", "The maximum number of results to return.", "max_results"),
  TABLE_TYPE(
      "table_type",
      "The type of the table. Supported values are MANAGED and EXTERNAL. For create table only EXTERNAL tables are supported in this CLI example.",
      "table_type"),
  DATA_SOURCE_FORMAT(
      "format",
      "The format of the data source. Supported values are DELTA, PARQUET, ORC, JSON, CSV, AVRO and TEXT.",
      "data_source_format"),
  COLUMNS(
      "columns",
      "The columns of the table. Each column spec should be in the sql-like format  \"column_name column_data_type\".Supported data types are BOOLEAN, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, DATE, TIMESTAMP, TIMESTAMP_NTZ, STRING, BINARY, DECIMAL. Multiple columns should be separated by a comma. For example: \"id INT, name STRING\".",
      "columns"),
  VOLUME_TYPE(
      "volume_type",
      "The type of the volume. Supported values are MANAGED and EXTERNAL.",
      "volume_type"),
  NEW_NAME("new_name", "The new name of the entity.", "new_name"),
  INPUT_PARAMS("input_params", "The input parameters of the function", "input_params"),
  DATA_TYPE("data_type", "The data type of the function", "data_type"),
  PATH("path", "Path inside a volume", "path"),
  ROUTINE_DEFINITION("def", "The routine definition of the function", "routine_definition"),
  LANGUAGE("language", "The language of the function", "external_language"),
  COMMENT("comment", "Comment/Description of the entity.", "comment"),
  SERVER("server", "UC Server to connect to. Default is reference server.", "server"),
  AUTH_TOKEN("auth_token", "PAT token to authorize uc requests", "auth_token"),
  OUTPUT("output", "To indicate CLI output format preference", "output"),
  FORCE("force", "To force delete the entity", "force");
  private final String value;
  private final String helpMessage;
  private final String serverParam;

  CliParams(String value) {
    this.value = value;
    this.serverParam = "";
    this.helpMessage = "";
  }

  CliParams(String value, String helpMessage) {
    this.value = value;
    this.helpMessage = helpMessage;
    this.serverParam = "";
  }

  CliParams(String value, String helpMessage, String serverParam) {
    this.value = value;
    this.helpMessage = helpMessage;
    this.serverParam = serverParam;
  }

  public String val() {
    return value;
  }

  public String getHelpMessage() {
    return helpMessage;
  }

  public String getServerParam() {
    return serverParam;
  }

  public static CliParams fromString(String text) {
    for (CliParams cliParam : CliParams.values()) {
      if (cliParam.value.equalsIgnoreCase(text)) {
        return cliParam;
      }
    }
    throw new IllegalArgumentException("No enum constant for value: " + text);
  }

  public static boolean contains(String text) {
    for (CliParams cliParam : CliParams.values()) {
      if (cliParam.value.equalsIgnoreCase(text)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return value;
  }
}
