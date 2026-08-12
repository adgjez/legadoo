package io.legado.app.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.video.GenerationRecommendation
import io.legado.app.video.api.GenerationMode
import io.legado.app.video.data.entities.VideoCharacter
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.data.entities.VideoScene
import io.legado.app.video.ui.character.CharacterListScreen
import io.legado.app.video.ui.components.GenerationModeRecommendCard
import io.legado.app.video.ui.pipeline.CostTrackerScreen
import io.legado.app.video.ui.pipeline.MultiEpisodeScreen
import io.legado.app.video.ui.pipeline.NewProjectWizard
import io.legado.app.video.ui.pipeline.PipelineStageScreen
import io.legado.app.video.ui.pipeline.QualityReportScreen
import io.legado.app.video.ui.pipeline.TemplateEngineScreen
import io.legado.app.video.ui.preview.ProjectExportScreen
import io.legado.app.video.ui.preview.VideoPreviewScreen
import io.legado.app.video.ui.project.VideoProjectListScreen
import io.legado.app.video.ui.scene.PromptEvolutionScreen
import io.legado.app.video.ui.scene.SceneEditorScreen
import io.legado.app.video.ui.scene.SceneTransitionScreen
import io.legado.app.video.ui.settings.VideoSettingsScreen
import io.legado.app.video.ui.storyboard.StoryboardWorkbenchScreen
import io.legado.app.video.ui.theme.VideoColors

class VideoWorkbenchActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, VideoWorkbenchActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VideoWorkbenchContent(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoWorkbenchContent(
    onBack: () -> Unit
) {
    val viewModel: VideoProjectViewModel = viewModel()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val currentProject by viewModel.currentProject.collectAsStateWithLifecycle()
    val currentScenes by viewModel.currentScenes.collectAsStateWithLifecycle()
    val currentCharacters by viewModel.currentCharacters.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pipelineStages by viewModel.pipelineStages.collectAsStateWithLifecycle()
    val pipelineProgress by viewModel.pipelineProgress.collectAsStateWithLifecycle()
    val isPipelineRunning by viewModel.isPipelineRunning.collectAsStateWithLifecycle()
    val generationProgress by viewModel.generationProgress.collectAsStateWithLifecycle()
    val qualityReport by viewModel.qualityReport.collectAsStateWithLifecycle()
    val generationRecommendation by viewModel.generationRecommendation.collectAsStateWithLifecycle()

    var navigationStack by remember { mutableStateOf<List<Screen>>(listOf(Screen.ProjectList)) }
    var showWizard by remember { mutableStateOf(false) }
    var editingScene by remember { mutableStateOf<VideoScene?>(null) }
    var showCharacterList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showQualityReport by remember { mutableStateOf(false) }

    val currentScreen = navigationStack.last()

    fun navigateTo(screen: Screen) {
        navigationStack = navigationStack + screen
    }

    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
        } else {
            onBack()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            viewModel.clearError()
        }
    }

    when (currentScreen) {
        Screen.ProjectList -> {
            VideoProjectListScreen(
                projects = projects,
                onProjectClick = { project ->
                    viewModel.loadProject(project.id)
                    navigateTo(Screen.Storyboard)
                },
                onNewProject = { showWizard = true },
                onSettingsClick = { showSettings = true },
                onDeleteProject = { project -> viewModel.deleteProject(project) }
            )
        }

        Screen.Storyboard -> {
            currentProject?.let { project ->
                StoryboardWorkbenchScreen(
                    project = project,
                    scenes = currentScenes,
                    characters = currentCharacters,
                    onBack = { navigateBack() },
                    onSceneClick = { scene -> editingScene = scene },
                    onAddScene = { viewModel.addScene(project.id) },
                    onDeleteScene = { scene -> viewModel.deleteScene(scene) },
                    onReorderScenes = { },
                    onStartGeneration = {
                        viewModel.executeFullPipeline(project.id)
                        navigateTo(Screen.Pipeline)
                    },
                    onCharactersClick = { showCharacterList = true },
                    onExportClick = { navigateTo(Screen.Preview) },
                    header = {
                        GenerationModeRecommendCard(
                            recommendation = generationRecommendation,
                            onOverrideMode = { m -> viewModel.overrideGenerationMode(m) },
                            onResetAuto = { viewModel.resetGenerationModeToAuto() },
                            onConfirm = { viewModel.confirmGenerationMode() },
                            onConfirmAndStart = {
                                viewModel.confirmRecommendationAndStartPipeline(project.id)
                                navigateTo(Screen.Pipeline)
                            }
                        )
                    }
                )
            }
        }

        Screen.Pipeline -> {
            PipelineStageScreen(
                projectId = currentProject?.id.orEmpty(),
                stages = pipelineStages,
                overallProgress = pipelineProgress,
                isPaused = false,
                isCancelled = false,
                errors = listOfNotNull(error),
                onPause = { viewModel.pausePipeline() },
                onResume = { },
                onCancel = { viewModel.cancelPipeline() },
                onRetryFailed = { }
            )
        }

        Screen.Preview -> {
            currentProject?.let { project ->
                VideoPreviewScreen(
                    project = project,
                    scenes = currentScenes,
                    onBack = { navigateBack() },
                    onSceneClick = { scene -> editingScene = scene },
                    onExport = { }
                )
            }
        }

        Screen.Characters -> {
            CharacterListScreen(
                characters = currentCharacters,
                onBack = { showCharacterList = false },
                onCharacterClick = { },
                onAddCharacter = { },
                onDeleteCharacter = { character -> viewModel.deleteCharacter(character) },
                onGenerateAll = { }
            )
        }

        Screen.Settings -> {
            VideoSettingsScreen(
                onBack = { showSettings = false }
            )
        }

        Screen.Templates -> {
            TemplateEngineScreen(
                onDismiss = { navigateBack() },
                onTemplateSelected = { result ->
                    viewModel.createProjectFromTemplate(result)
                    navigateBack()
                }
            )
        }

        Screen.MultiEpisode -> {
            currentProject?.let { project ->
                MultiEpisodeScreen(
                    projectName = project.name,
                    episodes = emptyList(),
                    onBack = { navigateBack() },
                    onEpisodeClick = { },
                    onPlanEpisodes = { },
                    onGenerateAll = { }
                )
            }
        }

        Screen.CostTracker -> {
            currentProject?.let { project ->
                CostTrackerScreen(
                    projectId = project.id,
                    onBack = { navigateBack() }
                )
            }
        }

        Screen.Export -> {
            currentProject?.let { project ->
                ProjectExportScreen(
                    project = project,
                    onBack = { navigateBack() },
                    onExport = { }
                )
            }
        }

        Screen.PromptEvolution -> {
            editingScene?.let { scene ->
                PromptEvolutionScreen(
                    originalPrompt = scene.visualPrompt,
                    sceneTitle = scene.title,
                    onBack = { navigateBack() },
                    onApply = { evolvedPrompt ->
                        viewModel.updateScenePrompt(scene, evolvedPrompt)
                        navigateBack()
                    }
                )
            }
        }

        Screen.SceneTransition -> {
            currentProject?.let { project ->
                SceneTransitionScreen(
                    fromSceneTitle = "当前场景",
                    toSceneTitle = "下一场景",
                    onBack = { navigateBack() },
                    onConfirm = { }
                )
            }
        }
    }

    editingScene?.let { scene ->
        SceneEditorScreen(
            scene = scene,
            characters = currentCharacters,
            onBack = { editingScene = null },
            onSave = { updatedScene ->
                viewModel.saveScene(updatedScene)
                editingScene = null
            },
            onGenerateImage = { viewModel.generateSceneImage(scene) },
            onGenerateVideo = { viewModel.generateSceneVideo(scene) }
        )
    }

    if (showWizard) {
        NewProjectWizard(
            onDismiss = { showWizard = false },
            onCreate = { result ->
                viewModel.createProjectFromWizard(result)
                showWizard = false
                viewModel.currentProject.value?.let {
                    viewModel.loadProject(it.id)
                }
                navigationStack = navigationStack.dropLast(navigationStack.size - 1) + Screen.Storyboard
            }
        )
    }

    if (showQualityReport) {
        QualityReportScreen(
            report = qualityReport,
            onDismiss = { showQualityReport = false },
            onRegenerate = { }
        )
    }

    if (showSettings) {
        VideoSettingsScreen(
            onBack = { showSettings = false }
        )
    }

    if (showCharacterList) {
        CharacterListScreen(
            characters = currentCharacters,
            onBack = { showCharacterList = false },
            onCharacterClick = { },
            onAddCharacter = { },
            onDeleteCharacter = { character -> viewModel.deleteCharacter(character) },
            onGenerateAll = { }
        )
    }
}

sealed class Screen {
    object ProjectList : Screen()
    object Storyboard : Screen()
    object Pipeline : Screen()
    object Preview : Screen()
    object Characters : Screen()
    object Settings : Screen()
    object Templates : Screen()
    object MultiEpisode : Screen()
    object CostTracker : Screen()
    object Export : Screen()
    object PromptEvolution : Screen()
    object SceneTransition : Screen()
    data class SceneEditor(val sceneId: String) : Screen()
}
