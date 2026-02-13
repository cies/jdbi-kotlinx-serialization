package dropnext.lib.jdbi.fake

import java.sql.ResultSet


/** Helper function to create FakeResultSet without having to manually specify the types. */
fun createFakeResultSet(vararg values: Any?): ResultSet {
  val columns = values.map { value ->
    FakeColumn.fromValue(value)
  }
  return FakeResultSet(columns)
}

/** Helper function to create FakeResultSetWithMetadata. */
fun createFakeResultSetWithMetadata(vararg columnsWithData: Pair<Pair<String, String>, Any?>): ResultSet {
  val columns = columnsWithData.map { (metadata, value) ->
    val (label, name) = metadata
    val baseColumn = FakeColumn.fromValue(value)
    FakeColumnWithMetadata(baseColumn.value, baseColumn.sqlType, label, name)
  }
  return FakeResultSetWithMetadata(columns)
}

/** Helper function to create BrokenMetadataResultSet. */
fun createBrokenMetadataResultSet(vararg values: Any?): ResultSet {
  val columns = values.map { value ->
    FakeColumn.fromValue(value)
  }
  return BrokenMetadataResultSet(columns)
}