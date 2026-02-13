package dropnext.lib.jdbi.rowdecoder

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo


/**
 * Marks that a property expects a JSON value from the database.
 *
 * Of course, `kotlinx.serialization` is also used for deserializing the JSON value.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Retention(AnnotationRetention.BINARY)
// TODO: This needs to have AnnotationTarget.PROPERTY and not AnnotationTarget.FIELD or AnnotationTarget.VALUE_PARAMETER
//     or else it will not be retrievable with getElementAnnotations.
//     See https://github.com/Kotlin/kotlinx.serialization/issues/1001 for more details.
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class SqlJsonValue
