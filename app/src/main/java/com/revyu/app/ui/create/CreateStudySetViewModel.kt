package com.revyu.app.ui.create

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.core.util.ExtractionStatus
import com.revyu.app.core.util.RevyuResult
import com.revyu.app.core.util.UriUtils
import com.revyu.app.data.local.entities.DifficultyMix
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.local.entities.ReviewerDetail
import com.revyu.app.data.local.entities.ReviewerFontStyle
import com.revyu.app.data.local.entities.ReviewerMargins
import com.revyu.app.data.local.entities.StudyMaterialEntity
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.repository.GenerationRepository
import com.revyu.app.data.repository.StudyMaterialRepository
import com.revyu.app.data.repository.StudySetRepository
import com.revyu.app.data.repository.SubjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class GenerationUiState {
    data object Idle : GenerationUiState()
    data object InProgress : GenerationUiState()
    data class Success(val studySetId: String) : GenerationUiState()
    data class Failed(val message: String, val isRateLimit: Boolean, val isMissingApiKey: Boolean = false) : GenerationUiState()
}

sealed class UploadUiState {
    data object Idle : UploadUiState()
    data object Uploading : UploadUiState()
    data class ExistingStudySetsFound(val count: Int, val latestStudySetId: String) : UploadUiState()
    data class Failed(val message: String) : UploadUiState()
}

class CreateStudySetViewModel(
    val subjectId: String,
    private val context: android.content.Context,
    private val subjectRepository: SubjectRepository,
    private val studyMaterialRepository: StudyMaterialRepository,
    private val studySetRepository: StudySetRepository,
    private val generationRepository: GenerationRepository,
    private val networkObserver: com.revyu.app.core.util.NetworkObserver? = null
) : ViewModel() {

    val isOnline: kotlinx.coroutines.flow.StateFlow<Boolean> =
        networkObserver?.isOnline ?: kotlinx.coroutines.flow.MutableStateFlow(true)

    var subjectName by mutableStateOf("")
        private set

    var existingMaterials by mutableStateOf<List<StudyMaterialEntity>>(emptyList())
        private set

    var selectedMaterial by mutableStateOf<StudyMaterialEntity?>(null)
        private set

    var uploadState by mutableStateOf<UploadUiState>(UploadUiState.Idle)
        private set

    var uploadWarning by mutableStateOf<String?>(null)
        private set
    
    var uploadStatus by mutableStateOf<ExtractionStatus>(ExtractionStatus.Reading)
        private set
    // Reviewer customization
    var reviewerColumns by mutableStateOf(2)
        private set
    var reviewerFontStyle by mutableStateOf(ReviewerFontStyle.SERIF)
        private set
    var reviewerFontSizeSp by mutableStateOf(12)
        private set
    var reviewerMargins by mutableStateOf(ReviewerMargins.NORMAL)
        private set
    var reviewerDetail by mutableStateOf(ReviewerDetail.STANDARD)
        private set
    var flashcardCount by mutableStateOf(20)
        private set
    var difficultyMix by mutableStateOf(DifficultyMix.BALANCED)
        private set
    var rewriteReviewer by mutableStateOf(false)
        private set
    var variationIndex by mutableStateOf(0)
        private set

    // Practice widget customization
    var maxQuestions by mutableStateOf(30)
        private set
    var selectedQuestionTypes by mutableStateOf(setOf(QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE))
        private set

    var generationState by mutableStateOf<GenerationUiState>(GenerationUiState.Idle)
        private set

    var generationStep by mutableStateOf("Reading your file")
        private set

    private var activeGenerationStudySet: StudySetEntity? = null

    init {
        viewModelScope.launch {
            subjectRepository.getSubject(subjectId)?.let { subjectName = it.name }
        }
        viewModelScope.launch {
            studyMaterialRepository.observeForSubject(subjectId).collect { existingMaterials = it }
        }
    }

    fun uploadMaterial(uri: Uri) {
        val fileName = UriUtils.displayName(context, uri)
        uploadState = UploadUiState.Uploading
        uploadWarning = null
        uploadStatus = ExtractionStatus.Reading
        viewModelScope.launch {
            try {
                val result = studyMaterialRepository.uploadAndExtract(subjectId, uri, fileName) { uploadStatus = it }
                val newMaterial = result.material
                val contentHash = newMaterial.contentHash ?: computeContentHashForText(newMaterial.extractedText)

                val existingOtherMaterial = studyMaterialRepository.getByContentHash(subjectId, contentHash)
                    .firstOrNull { it.id != newMaterial.id }

                val siblings = if (existingOtherMaterial != null) {
                    studySetRepository.getSiblingsForMaterial(subjectId, existingOtherMaterial.id)
                } else emptyList()

                if (existingOtherMaterial != null && siblings.isNotEmpty()) {
                    studyMaterialRepository.deleteMaterial(newMaterial.id)
                    val latest = siblings.maxByOrNull { it.createdAt }!!
                    selectedMaterial = existingOtherMaterial
                    variationIndex = siblings.size
                    uploadState = UploadUiState.ExistingStudySetsFound(
                        count = siblings.size,
                        latestStudySetId = latest.id
                    )
                } else {
                    selectedMaterial = newMaterial
                    variationIndex = 0
                    uploadWarning = result.warning
                    uploadState = UploadUiState.Idle
                }
            } catch (e: Exception) {
                uploadState = UploadUiState.Failed(e.message ?: "Couldn't read that file.")
            }
        }
    }

    private fun computeContentHashForText(text: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun selectExistingMaterial(material: StudyMaterialEntity) {
        selectedMaterial = material
        uploadWarning = null
        viewModelScope.launch {
            val siblings = studySetRepository.getSiblingsForMaterial(subjectId, material.id)
            if (siblings.isNotEmpty()) {
                val latest = siblings.maxByOrNull { it.createdAt }!!
                variationIndex = siblings.size
                uploadState = UploadUiState.ExistingStudySetsFound(
                    count = siblings.size,
                    latestStudySetId = latest.id
                )
            } else {
                variationIndex = 0
                uploadState = UploadUiState.Idle
            }
        }
    }

    fun dismissUploadState() {
        uploadState = UploadUiState.Idle
    }

    fun applyReviewerColumns(columns: Int) { reviewerColumns = columns }
    fun applyReviewerFontStyle(style: ReviewerFontStyle) { reviewerFontStyle = style }
    fun setReviewerFontSize(sizeSp: Int) { reviewerFontSizeSp = sizeSp }
    fun applyReviewerMargins(margins: ReviewerMargins) { reviewerMargins = margins }
    fun applyReviewerDetail(detail: ReviewerDetail) { reviewerDetail = detail }
    fun applyFlashcardCount(count: Int) { flashcardCount = count }
    fun applyDifficultyMix(mix: DifficultyMix) { difficultyMix = mix }
    fun applyRewriteReviewer(rewrite: Boolean) { rewriteReviewer = rewrite }

    fun applyMaxQuestions(count: Int) { maxQuestions = count }
    fun toggleQuestionType(type: QuestionType) {
        selectedQuestionTypes = if (type in selectedQuestionTypes) {
            selectedQuestionTypes - type
        } else {
            selectedQuestionTypes + type
        }
    }

    fun startGeneration() {
        if (networkObserver != null && !networkObserver.isOnline.value) {
            generationState = GenerationUiState.Failed("You are offline. AI generation requires an active internet connection.", false)
            return
        }

        val material = selectedMaterial ?: run {
            generationState = GenerationUiState.Failed("Upload a study material first.", false)
            return
        }
        if (selectedQuestionTypes.isEmpty()) {
            generationState = GenerationUiState.Failed("Pick at least one question type.", false)
            return
        }

        generationState = GenerationUiState.InProgress
        generationStep = "Reading your file"
        viewModelScope.launch {
            val title = if (variationIndex > 0) {
                "${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())} — ${material.fileName} · v${variationIndex + 1}"
            } else {
                "${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())} — ${material.fileName}"
            }
            val studySet = activeGenerationStudySet
                ?.takeIf { it.sourceMaterialId == material.id }
                ?: StudySetEntity(
                    subjectId = subjectId,
                    sourceMaterialId = material.id,
                    title = title,
                    reviewerColumns = reviewerColumns,
                    reviewerFontStyle = reviewerFontStyle,
                    reviewerFontSizeSp = reviewerFontSizeSp,
                    reviewerMargins = reviewerMargins,
                    reviewerDetail = reviewerDetail,
                    flashcardCount = flashcardCount,
                    difficultyMix = difficultyMix,
                    variationIndex = variationIndex,
                    questionLanguage = "English",
                    maxQuestions = maxQuestions,
                    questionTypes = selectedQuestionTypes.toList()
                ).also {
                    studySetRepository.insertStudySet(it)
                    activeGenerationStudySet = it
                }

            when (val result = generationRepository.generateStudySet(
                studySet = studySet,
                subjectName = subjectName,
                sourceText = material.extractedText,
                rewriteReviewer = rewriteReviewer,
                onProgress = { generationStep = it }
            )) {
                is RevyuResult.Success -> {
                    activeGenerationStudySet = result.data
                    generationState = GenerationUiState.Success(studySet.id)
                }
                is RevyuResult.Error -> {
                    activeGenerationStudySet = studySet.copy(
                        generationStatus = com.revyu.app.data.local.entities.GenerationStatus.FAILED,
                        generationError = result.message
                    )
                    generationState = GenerationUiState.Failed(result.message, result.isRateLimit, result.isMissingApiKey)
                }
            }
        }
    }

    fun retryGeneration() = startGeneration()
}
