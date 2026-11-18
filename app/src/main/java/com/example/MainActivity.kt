package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.ThemePreferences
import com.example.core.configureAppCheck
import com.example.data.ExamPilotRepository
import com.example.data.GeminiAiRepository
import com.example.model.AiTaskStatus
import com.example.model.Subject
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.AiProgressBanner
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PracticeTestScreen
import com.example.ui.screens.QuickRevisionScreen
import com.example.ui.screens.StudyPlanScreen
import com.example.ui.screens.SyllabusInputScreen
import com.example.ui.theme.ExamPilotTheme
import com.example.viewmodel.ExamPilotViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase ops
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val themePrefs = remember(context) { ThemePreferences(context.applicationContext) }
            val isDarkMode by themePrefs.isDarkMode.collectAsStateWithLifecycle(initialValue = true)

            ExamPilotTheme(darkTheme = isDarkMode) {
                ExamPilotRoot(
                    isDarkMode = isDarkMode,
                    onToggleTheme = {
                        scope.launch { themePrefs.setDarkMode(!isDarkMode) }
                    },
                )
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private enum class ExamPilotTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("Home", Icons.Rounded.Home, Icons.Outlined.Home),
    PLAN("Plan", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    REVISE("Revise", Icons.Rounded.MenuBook, Icons.Outlined.MenuBook),
    PRACTICE("Practice", Icons.Rounded.Quiz, Icons.Outlined.Quiz),
}

@Composable
private fun ExamPilotRoot(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
) {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(
            isDarkMode = isDarkMode,
            onToggleTheme = onToggleTheme,
            onAuthSuccess = { },
        )
    } else {
        key(user.uid) {
            ExamPilotCockpit(
                currentUserId = user.uid,
                userName = user.displayName,
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamPilotCockpit(
    currentUserId: String,
    userName: String?,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
) {
    val viewModel: ExamPilotViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ExamPilotViewModel(
                    repository = ExamPilotRepository(db),
                    aiRepository = GeminiAiRepository(),
                    currentUserId = currentUserId,
                )
            }
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val tasksState by viewModel.studyTasksState.collectAsStateWithLifecycle()
    val revisionsState by viewModel.revisionsState.collectAsStateWithLifecycle()
    val testsState by viewModel.practiceTestsState.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val extractedPreview by viewModel.extractedSyllabusPreview.collectAsStateWithLifecycle()
    val activeTestId by viewModel.activeTestId.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMsg) {
        if (!snackbarMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            viewModel.clearSnackbar()
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(ExamPilotTab.HOME) }
    var selectedSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSyllabusEditor by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<Subject?>(null) }

    val isAiBusy = aiStatus is AiTaskStatus.Working

    if (showSyllabusEditor) {
        SyllabusInputScreen(
            editingSubject = editingSubject,
            aiStatus = aiStatus,
            extractedSyllabusPreview = extractedPreview,
            onClearExtractedPreview = viewModel::clearExtractedSyllabus,
            onDismissAiStatus = viewModel::clearAiStatus,
            onExtractFromPhoto = viewModel::extractSyllabusFromPhoto,
            onExtractFromDocument = viewModel::extractSyllabusFromDocument,
            onSaveSubject = { existingId, name, examType, level, dateMillis, syllabus, colorIdx, genPlan ->
                viewModel.saveSubjectAndOptionallyPlan(
                    existingId = existingId,
                    name = name,
                    examType = examType,
                    academicLevel = level,
                    examDateEpochMillis = dateMillis,
                    syllabusText = syllabus,
                    colorIndex = colorIdx,
                    generatePlanImmediately = genPlan,
                    onSaved = {
                        showSyllabusEditor = false
                        editingSubject = null
                        if (genPlan) currentTab = ExamPilotTab.PLAN
                    },
                )
            },
            onClose = {
                showSyllabusEditor = false
                editingSubject = null
            },
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "ExamPilot",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    actions = {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("theme_toggle_button"),
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Toggle dark mode",
                            )
                        }
                        IconButton(
                            onClick = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out",
                            )
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        ExamPilotTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name}"),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentTab == ExamPilotTab.HOME || currentTab == ExamPilotTab.PLAN) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingSubject = null
                            showSyllabusEditor = true
                        },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("Add Subject") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_subject"),
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        ExamPilotTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    AiProgressBanner(
                        status = aiStatus,
                        onDismiss = viewModel::clearAiStatus,
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            ExamPilotTab.HOME -> HomeScreen(
                                userName = userName,
                                subjectsState = subjectsState,
                                tasksState = tasksState,
                                isAiBusy = isAiBusy,
                                onAddSubject = {
                                    editingSubject = null
                                    showSyllabusEditor = true
                                },
                                onEditSubject = { subject ->
                                    editingSubject = subject
                                    showSyllabusEditor = true
                                },
                                onDeleteSubject = { subject ->
                                    if (selectedSubjectId == subject.id) selectedSubjectId = null
                                    viewModel.deleteSubject(subject.id)
                                },
                                onGeneratePlan = { subject ->
                                    selectedSubjectId = subject.id
                                    viewModel.generateStudyPlan(subject)
                                    currentTab = ExamPilotTab.PLAN
                                },
                                onToggleTask = viewModel::toggleTaskCompleted,
                                onNavigateToPlan = { currentTab = ExamPilotTab.PLAN },
                                onNavigateToRevise = { subId ->
                                    if (subId != null) selectedSubjectId = subId
                                    currentTab = ExamPilotTab.REVISE
                                },
                                onNavigateToPractice = { subId ->
                                    if (subId != null) selectedSubjectId = subId
                                    currentTab = ExamPilotTab.PRACTICE
                                },
                            )

                            ExamPilotTab.PLAN -> StudyPlanScreen(
                                subjectsState = subjectsState,
                                tasksState = tasksState,
                                selectedSubjectId = selectedSubjectId,
                                isAiBusy = isAiBusy,
                                onSelectSubject = { selectedSubjectId = it },
                                onGeneratePlan = viewModel::generateStudyPlan,
                                onToggleTask = viewModel::toggleTaskCompleted,
                                onAddSubject = {
                                    editingSubject = null
                                    showSyllabusEditor = true
                                },
                            )

                            ExamPilotTab.REVISE -> QuickRevisionScreen(
                                subjectsState = subjectsState,
                                revisionsState = revisionsState,
                                selectedSubjectId = selectedSubjectId,
                                isAiBusy = isAiBusy,
                                onSelectSubject = { selectedSubjectId = it },
                                onGenerateRevision = viewModel::generateQuickRevision,
                                onDeleteRevision = viewModel::deleteRevision,
                                onAddSubject = {
                                    editingSubject = null
                                    showSyllabusEditor = true
                                },
                            )

                            ExamPilotTab.PRACTICE -> PracticeTestScreen(
                                subjectsState = subjectsState,
                                testsState = testsState,
                                selectedSubjectId = selectedSubjectId,
                                activeTestId = activeTestId,
                                isAiBusy = isAiBusy,
                                onSelectSubject = { selectedSubjectId = it },
                                onSelectTest = viewModel::selectPracticeTest,
                                onGenerateTest = viewModel::generatePracticeTest,
                                onSubmitTest = viewModel::submitAndGradeTest,
                                onDeleteTest = viewModel::deletePracticeTest,
                                onAddSubject = {
                                    editingSubject = null
                                    showSyllabusEditor = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
