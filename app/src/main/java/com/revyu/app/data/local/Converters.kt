package com.revyu.app.data.local

import androidx.room.TypeConverter
import com.revyu.app.data.local.entities.ExamStatus
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.local.entities.ReviewerFontStyle
import com.revyu.app.data.local.entities.ReviewerMargins
import com.revyu.app.data.local.entities.SourceFileType
import com.revyu.app.data.local.entities.StudySetKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        json.encodeToString(value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else json.decodeFromString(value)

    @TypeConverter
    fun fromQuestionTypeList(value: List<QuestionType>?): String =
        json.encodeToString((value ?: emptyList()).map { it.name })

    @TypeConverter
    fun toQuestionTypeList(value: String?): List<QuestionType> =
        if (value.isNullOrBlank()) emptyList()
        else json.decodeFromString<List<String>>(value).map { QuestionType.valueOf(it) }

    @TypeConverter
    fun fromQuestionType(value: QuestionType): String = value.name

    @TypeConverter
    fun toQuestionType(value: String): QuestionType = QuestionType.valueOf(value)

    @TypeConverter
    fun fromExamStatus(value: ExamStatus): String = value.name

    @TypeConverter
    fun toExamStatus(value: String): ExamStatus = ExamStatus.valueOf(value)

    @TypeConverter
    fun fromGenerationStatus(value: GenerationStatus): String = value.name

    @TypeConverter
    fun toGenerationStatus(value: String): GenerationStatus = GenerationStatus.valueOf(value)

    @TypeConverter
    fun fromReviewerFontStyle(value: ReviewerFontStyle): String = value.name

    @TypeConverter
    fun toReviewerFontStyle(value: String): ReviewerFontStyle = ReviewerFontStyle.valueOf(value)

    @TypeConverter
    fun fromReviewerMargins(value: ReviewerMargins): String = value.name

    @TypeConverter
    fun toReviewerMargins(value: String): ReviewerMargins = ReviewerMargins.valueOf(value)

    @TypeConverter
    fun fromSourceFileType(value: SourceFileType): String = value.name

    @TypeConverter
    fun toSourceFileType(value: String): SourceFileType = SourceFileType.valueOf(value)

    @TypeConverter
    fun fromStudySetKind(value: StudySetKind): String = value.name

    @TypeConverter
    fun toStudySetKind(value: String): StudySetKind = StudySetKind.valueOf(value)
}
