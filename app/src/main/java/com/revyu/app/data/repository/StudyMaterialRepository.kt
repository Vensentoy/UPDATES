package com.revyu.app.data.repository

import android.net.Uri
import com.revyu.app.core.util.ExtractionStatus
import com.revyu.app.core.util.MaterialExtractor
import com.revyu.app.data.local.dao.StudyMaterialDao
import com.revyu.app.data.local.entities.StudyMaterialEntity
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

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

    suspend fun ensureContentHash(material: StudyMaterialEntity): StudyMaterialEntity {
        val hash = material.contentHash ?: computeContentHash(material.extractedText)
        if (material.contentHash != hash) {
            dao.updateContentHash(material.id, hash)
            return material.copy(contentHash = hash)
        }
        return material
    }

    suspend fun uploadAndExtract(
        subjectId: String,
        uri: Uri,
        fileName: String,
        onStatus: (ExtractionStatus) -> Unit = {}
    ): MaterialUploadResult {
        val extraction = extractor.extract(uri, fileName, onStatus)
        val contentHash = computeContentHash(extraction.text)
        val entity = StudyMaterialEntity(
            subjectId = subjectId,
            fileName = extraction.fileName,
            sourceType = extraction.sourceType,
            extractedText = extraction.text,
            characterCount = extraction.text.length,
            contentHash = contentHash
        )
        dao.insert(entity)
        return MaterialUploadResult(material = entity, warning = extraction.warning)
    }

    private fun computeContentHash(text: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}