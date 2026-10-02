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
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.local.entities.ReviewerFontStyle
import com.revyu.app.data.local.entities.ReviewerMargins
import com.revyu.app.data.local.entities.StudyMaterialEntity
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.repository.GenerationRepository
import com.revyu.app.data.repository.StudyMaterialRepository
import com.revyu.app.data.repository.StudySetRepository
import com.revyu.app.data.repository.SubjectRepository
import kotlinx.coroutines.launch
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
    data class ExistingStudySetFound(val studySetId: String, val message: String) : UploadUiState()
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

    // Practice widget customization
    var maxQuestions by mutableStateOf(15)
        private set
    var selectedQuestionTypes by mutableStateOf(setOf(QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE))
        private set

    var generationState by mutableStateOf<GenerationUiState>(GenerationUiState.Idle)
        private set

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
                val existingMaterial = studyMaterialRepository.getByFileName(subjectId, fileName)
                if (existingMaterial != null) {
                    val existingStudySet = studySetRepository.getReadyByMaterialId(existingMaterial.id)
                    if (existingStudySet != null) {
                        selectedMaterial = existingMaterial
                        uploadState = UploadUiState.ExistingStudySetFound(
                            studySetId = existingStudySet.id,
                            message = "A Study Set for this file already exists! Opening your existing Study Set..."
                        )
                        return@launch
                    } else {
                        selectedMaterial = existingMaterial
                        uploadWarning = "Re-using previously extracted content for $fileName."
                        uploadState = UploadUiState.Idle
                        return@launch
                    }
                }

                val result = studyMaterialRepository.uploadAndExtract(subjectId, uri, fileName) { uploadStatus = it }
                selectedMaterial = result.material
                uploadWarning = result.warning
                uploadState = UploadUiState.Idle
            } catch (e: Exception) {
                uploadState = UploadUiState.Failed(e.message ?: "Couldn't read that file.")
            }
        }
    }

    fun selectExistingMaterial(material: StudyMaterialEntity) {
        selectedMaterial = material
        uploadWarning = null
        viewModelScope.launch {
            val existingStudySet = studySetRepository.getReadyByMaterialId(material.id)
            if (existingStudySet != null) {
                uploadState = UploadUiState.ExistingStudySetFound(
                    studySetId = existingStudySet.id,
                    message = "A Study Set for this file already exists! Opening your existing Study Set..."
                )
            }
        }
    }

    fun applyReviewerColumns(columns: Int) { reviewerColumns = columns }
    fun applyReviewerFontStyle(style: ReviewerFontStyle) { reviewerFontStyle = style }
    fun setReviewerFontSize(sizeSp: Int) { reviewerFontSizeSp = sizeSp }
    fun applyReviewerMargins(margins: ReviewerMargins) { reviewerMargins = margins }

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
        viewModelScope.launch {
            val title = "${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())} — ${material.fileName}"
            val studySet = StudySetEntity(
                subjectId = subjectId,
                sourceMaterialId = material.id,
                title = title,
                reviewerColumns = reviewerColumns,
                reviewerFontStyle = reviewerFontStyle,
                reviewerFontSizeSp = reviewerFontSizeSp,
                reviewerMargins = reviewerMargins,
                questionLanguage = "English",
                maxQuestions = maxQuestions,
                questionTypes = selectedQuestionTypes.toList()
            )
            studySetRepository.insertStudySet(studySet)

            when (val result = generationRepository.generateStudySet(studySet, subjectName, material.extractedText)) {
                is RevyuResult.Success -> generationState = GenerationUiState.Success(studySet.id)
                is RevyuResult.Error -> generationState = GenerationUiState.Failed(result.message, result.isRateLimit, result.isMissingApiKey)
            }
        }
    }

    fun retryGeneration() = startGeneration()
}
