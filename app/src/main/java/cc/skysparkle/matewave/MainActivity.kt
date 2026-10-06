package cc.skysparkle.matewave

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import cc.skysparkle.matewave.audio.SoundManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.network.TransportType
import cc.skysparkle.matewave.ui.screens.AccountScreen
import cc.skysparkle.matewave.ui.screens.ConnectionScreen
import cc.skysparkle.matewave.ui.screens.FriendsScreen
import cc.skysparkle.matewave.ui.screens.GameScreen
import cc.skysparkle.matewave.ui.screens.LicensesScreen
import cc.skysparkle.matewave.ui.screens.MainMenuScreen
import cc.skysparkle.matewave.ui.screens.AppearanceScreen
import cc.skysparkle.matewave.ui.screens.PermissionsScreen
import cc.skysparkle.matewave.ui.screens.PuzzleSolveScreen
import cc.skysparkle.matewave.ui.screens.SettingsScreen
import cc.skysparkle.matewave.ui.screens.ShareApkScreen
import cc.skysparkle.matewave.ui.screens.VsAiSetupScreen
import cc.skysparkle.matewave.ui.theme.MatewaveTheme
import cc.skysparkle.matewave.viewmodel.GameViewModel
import cc.skysparkle.matewave.presence.PresenceHub

class MainActivity : ComponentActivity() {
    /** Asks for the highest refresh rate at the current resolution, for smoother animations. */
    private fun enableHighRefreshRate() {
        runCatching {
            val screen: android.view.Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val current = screen.mode
            val best = screen.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app has one dark look: light system bar icons over the background photo.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        enableHighRefreshRate()
        SoundManager.init(this)

        val appContext = applicationContext
        kotlin.concurrent.thread(name = "warmup", isDaemon = true) {
            runCatching { cc.skysparkle.matewave.data.FriendsStore.preload() }
            runCatching { cc.skysparkle.matewave.puzzles.PuzzleRepository.load(appContext) }
        }

        setContent {
            MatewaveTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                ) {
                    cc.skysparkle.matewave.ui.components.AppBackgroundImage()
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(cc.skysparkle.matewave.ui.theme.PhotoScrim)
                    )
                    AppNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        SoundManager.onAppPaused()
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val viewModel: GameViewModel = viewModel()
    val studyViewModel: cc.skysparkle.matewave.viewmodel.OpeningStudyViewModel = viewModel()

    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.init(context) }

    // Presence is shared with the background service, so the lists stay the same in both.
    val presence = remember { PresenceHub.get(context) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        if (cc.skysparkle.matewave.presence.PresenceSettings(context).backgroundEnabled) {
            cc.skysparkle.matewave.presence.PresenceService.start(context)
        }
        PresenceHub.acquire(context)
        onDispose { PresenceHub.release(context) }
    }

    NavHost(
        navController = navController,
        startDestination = "menu",
        enterTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeThroughIn()
            else sharedAxisIn(forward = true)
        },
        exitTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeThroughOut()
            else sharedAxisOut(forward = true)
        },
        popEnterTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeThroughIn()
            else sharedAxisIn(forward = false)
        },
        popExitTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeThroughOut()
            else sharedAxisOut(forward = false)
        }
    ) {
        composable("menu") {
            MainMenuScreen(
                viewModel = viewModel,
                studyViewModel = studyViewModel,
                onContinueGame = {
                    if (viewModel.resumeVsAi()) navController.navigate("game")
                },
                onQuickGame = {
                    // Quick game repeats the last setup against the AI.
                    val last = cc.skysparkle.matewave.settings.LastGameSetup(context)
                    val color = last.aiColor
                    viewModel.startVsAi(
                        last.aiDifficulty,
                        color ?: listOf(PieceColor.WHITE, PieceColor.BLACK).random(),
                        last.aiTime,
                        randomColor = color == null
                    )
                    navController.navigate("game")
                },
                onVsAiClick = { navController.navigate("vsAiSetup") },
                onPuzzlesClick = {
                    val puzzle = cc.skysparkle.matewave.puzzles.PuzzleRepository.pickForRating(
                        context,
                        cc.skysparkle.matewave.data.ProfileStore.get().elo,
                        cc.skysparkle.matewave.stats.Stats.solvedPuzzles()
                    )
                    if (puzzle != null) {
                        viewModel.startPuzzle(puzzle)
                        navController.navigate("puzzleSolve")
                    }
                },
                onStudyClick = { navController.navigate("openingStudy") },
                onVsPlayerClick = { navController.navigate("connectionNearby") },
                onSettingsClick = { navController.navigate("settings") },
                onAccountClick = { navController.navigate("account") },
                onFriendsClick = { navController.navigate("friends") }
            )
        }

        composable("settings") {
            SettingsScreen(
                onHomeClick = { navController.popBackStack("menu", inclusive = false) },
                onFriendsClick = {
                    navController.popBackStack("menu", inclusive = false)
                    navController.navigate("friends")
                },
                onAccountClick = {
                    navController.popBackStack("menu", inclusive = false)
                    navController.navigate("account")
                },
                onOpenPermissions = { navController.navigate("permissions") },
                onOpenAppearance = { navController.navigate("appearance") },
                onOpenLicenses = { navController.navigate("licenses") },
                onOpenShare = { navController.navigate("shareApk") }
            )
        }

        composable("shareApk") {
            ShareApkScreen(onBack = { navController.popBackStack() })
        }

        composable("licenses") {
            LicensesScreen(onBack = { navController.popBackStack() })
        }

        composable("permissions") {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }

        composable("appearance") {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }

        composable("account") {
            AccountScreen(
                studyViewModel = studyViewModel,
                onStudyClick = { navController.navigate("openingStudy") },
                onHomeClick = { navController.popBackStack("menu", inclusive = false) },
                onFriendsClick = {
                    navController.popBackStack("menu", inclusive = false)
                    navController.navigate("friends")
                },
                onSettingsClick = {
                    navController.popBackStack("menu", inclusive = false)
                    navController.navigate("settings")
                }
            )
        }

        composable("friends") {
            FriendsScreen(
                presence = presence,
                onPlay = { navController.navigate("connectionNearby") },
                onHomeClick = { navController.popBackStack("menu", inclusive = false) },
                onSettingsClick = {
                    navController.popBackStack("menu", inclusive = false)
                    navController.navigate("settings")
                },
                onAccountClick = {
                    navController.popBackStack("menu", inclusive = false)
                    navController.navigate("account")
                }
            )
        }

        composable("vsAiSetup") {
            VsAiSetupScreen(
                onBack = { navController.popBackStack() },
                onStart = { difficulty, color, timeControl ->

                    viewModel.startVsAi(
                        difficulty,
                        color ?: listOf(PieceColor.WHITE, PieceColor.BLACK).random(),
                        timeControl,
                        randomColor = color == null
                    )
                    navController.navigate("game")
                }
            )
        }

        composable("game") {
            GameScreen(
                viewModel = viewModel,
                onExit = { navController.popBackStack("menu", inclusive = false) }
            )
        }

        composable("openingStudy") {
            cc.skysparkle.matewave.ui.screens.OpeningStudyScreen(
                viewModel = studyViewModel,
                onBack = { navController.popBackStack() },
                onOpenTrainer = { navController.navigate("openingTrainer") }
            )
        }

        composable("openingTrainer") {
            cc.skysparkle.matewave.ui.screens.OpeningTrainerScreen(
                viewModel = studyViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("puzzleSolve") {
            PuzzleSolveScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack("menu", inclusive = false) },
                onNextPuzzle = { next -> viewModel.startPuzzle(next) }
            )
        }

        composable("connectionNearby") {
            val bleTransport = remember { cc.skysparkle.matewave.network.Transports.ble(context) }
            val lanTransport = remember { cc.skysparkle.matewave.network.Transports.lan(context) }

            ConnectionScreen(

                presence = presence,
                onStartHost = { timeControl ->
                    // Wait for the opponent on Wi-Fi and, if allowed, Bluetooth at once.
                    val transports = buildList<cc.skysparkle.matewave.network.ConnectionTransport> {
                        add(lanTransport)
                        if (cc.skysparkle.matewave.network.AppPermissions.allGranted(context, cc.skysparkle.matewave.network.AppPermissions.nearby)) {
                            add(bleTransport)
                        }
                    }
                    val transport = if (transports.size == 1) lanTransport
                        else cc.skysparkle.matewave.network.DualHostTransport(transports)
                    viewModel.startVsPlayerNetwork(transport, PieceColor.WHITE, timeControl, isHost = true)
                    navController.navigate("game")
                },
                onJoin = { type, timeControl, targetId ->
                    val transport = if (type == TransportType.BLUETOOTH) bleTransport else lanTransport
                    viewModel.startVsPlayerNetwork(transport, PieceColor.BLACK, timeControl, isHost = false, targetId = targetId)
                    navController.navigate("game")
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private val TAB_ROUTES = setOf("menu", "friends", "settings", "account")

private fun isTabSwitch(from: String?, to: String?): Boolean =
    from in TAB_ROUTES && to in TAB_ROUTES && from != to

private val MotionEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun fadeThroughIn(): androidx.compose.animation.EnterTransition =
    androidx.compose.animation.fadeIn(
        androidx.compose.animation.core.tween(durationMillis = 260, delayMillis = 90, easing = MotionEasing)
    ) + androidx.compose.animation.scaleIn(
        initialScale = 0.97f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, delayMillis = 90, easing = MotionEasing)
    )

private fun fadeThroughOut(): androidx.compose.animation.ExitTransition =
    androidx.compose.animation.fadeOut(
        androidx.compose.animation.core.tween(durationMillis = 110, easing = androidx.compose.animation.core.LinearEasing)
    )

private fun sharedAxisIn(forward: Boolean): androidx.compose.animation.EnterTransition =
    androidx.compose.animation.slideInHorizontally(
        androidx.compose.animation.core.tween(durationMillis = 340, easing = MotionEasing)
    ) { width -> if (forward) width / 14 else -width / 14 } +
        androidx.compose.animation.fadeIn(
            androidx.compose.animation.core.tween(durationMillis = 240, delayMillis = 80, easing = MotionEasing)
        )

private fun sharedAxisOut(forward: Boolean): androidx.compose.animation.ExitTransition =
    androidx.compose.animation.slideOutHorizontally(
        androidx.compose.animation.core.tween(durationMillis = 340, easing = MotionEasing)
    ) { width -> if (forward) -width / 14 else width / 14 } +
        androidx.compose.animation.fadeOut(
            androidx.compose.animation.core.tween(durationMillis = 120, easing = androidx.compose.animation.core.LinearEasing)
        )
