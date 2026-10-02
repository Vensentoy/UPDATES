package com.revyu.app.core.navigation

object RevyuDestinations {
    const val Opening = "opening"
    const val Login = "login"
    const val ApiKeySetup = "api_key_setup"
    const val StudyLoadUpload = "study_load_upload"
    const val Home = "home"

    // Five-tab shell
    const val SmartCalendar = "smart_calendar"
    const val Calendar = "calendar_tab"
    const val Reviewer = "reviewer_tab"
    const val CreateLanding = "create_landing" // legacy: kept for the import flow's popBackStack target
    const val Widgets = "widgets"
    const val History = "history"
    const val Settings = "settings"

    // New standalone Calendar tab (prototype's warm cream Calendar) — the old hybrid
    // "SmartCalendar = Home" tab is now split: Home shows the mascot dashboard, and this
    // Calendar tab is the study calendar shell the prototype calls "Calendar".
    const val CalendarTab = "calendar_tab"

    // New study-load import flow (lives under the Create tab). Nested graph so one
    // StudyLoadImportViewModel (scoped to this graph's entry) survives picker -> review.
    const val StudyLoadImportGraphPattern = "study_load_import_graph"
    const val StudyLoadPicker = "study_load_picker"
    const val StudyLoadReview = "study_load_review"

    // School-calendar import flow — same nested-graph pattern as the study-load import.
    const val SchoolCalendarImportGraphPattern = "school_calendar_import_graph"
    const val SchoolCalendarPicker = "school_calendar_picker"
    const val SchoolCalendarReview = "school_calendar_review"

    const val SubjectDetailPattern = "subject/{subjectId}"
    fun subjectDetail(subjectId: String) = "subject/$subjectId"

    // Create Study Set wizard — nested graph scoped to one subject so the wizard
    // ViewModel survives across its steps without re-passing state through arguments.
    const val CreateGraphPattern = "create/{subjectId}"
    fun createGraph(subjectId: String) = "create/$subjectId"

    const val UploadMaterial = "upload"
    const val CustomizeReviewer = "reviewer"
    const val CustomizePractice = "practice"
    const val Generating = "generating"

    const val StudySetOverviewPattern = "study_set/{studySetId}"
    fun studySetOverview(studySetId: String) = "study_set/$studySetId"

    const val ExamModePattern = "study_set/{studySetId}/exam"
    fun examMode(studySetId: String) = "study_set/$studySetId/exam"

    const val ResultsPattern = "study_set/{studySetId}/results/{attemptId}"
    fun results(studySetId: String, attemptId: String) = "study_set/$studySetId/results/$attemptId"

    const val SemesterVaultSubjects = "vault_subjects"

    // Semester Vault flow — same nested-graph-scoped-ViewModel pattern as the Create wizard.
    const val VaultGraphPattern = "vault/{subjectId}"
    fun vaultGraph(subjectId: String) = "vault/$subjectId"

    const val VaultConfigure = "vault_configure"
    const val VaultGenerating = "vault_generating"

    const val ArgSubjectId = "subjectId"
    const val ArgStudySetId = "studySetId"
    const val ArgAttemptId = "attemptId"

    /** SavedStateHandle result key set when the user saves an API key on the ApiKeySetup screen. */
    const val ApiKeySavedResultKey = "api_key_saved"

    /** The top-level tabs; the bottom bar shows only on these roots. */
    val TabRoots = listOf(SmartCalendar, Home, CalendarTab, Reviewer, Widgets, History, Settings)
}
