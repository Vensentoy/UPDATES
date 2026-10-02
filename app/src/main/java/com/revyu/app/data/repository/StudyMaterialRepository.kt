package com.revyu.app.data.repository

import android.net.Uri
import com.revyu.app.core.util.ExtractionStatus
import com.revyu.app.core.util.MaterialExtractor
import com.revyu.app.data.local.dao.StudyMaterialDao
import com.revyu.app.data.local.entities.StudyMaterialEntity
import kotlinx.coroutines.flow.Flow

data class MaterialUploadResult(
    val material: StudyMaterialEntity,
    val warning: String? = null
)

class StudyMaterialRepository(
    private val dao: StudyMaterialDao,
    private val extractor: MaterialExtractor
) {
    fun observeForSubject(subjectId: String): Flow<List<StudyMaterialEntity>> =
        dao.observeForSubject(subjectId)

    suspend fun getById(id: String): StudyMaterialEntity? = dao.getById(id)

    suspend fun getByFileName(subjectId: String, fileName: String): StudyMaterialEntity? =
        dao.getByFileName(subjectId, fileName)

    suspend fun uploadAndExtract(
        subjectId: String,
        uri: Uri,
        fileName: String,
        onStatus: (ExtractionStatus) -> Unit = {}
    ): MaterialUploadResult {
        val extraction = extractor.extract(uri, fileName, onStatus)
        val entity = StudyMaterialEntity(
            subjectId = subjectId,
            fileName = extraction.fileName,
            sourceType = extraction.sourceType,
            extractedText = extraction.text,
            characterCount = extraction.text.length
        )
        dao.insert(entity)
        return MaterialUploadResult(material = entity, warning = extraction.warning)
    }
}