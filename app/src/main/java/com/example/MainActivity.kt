package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.StudioRepository
import com.example.interop.ExternalEditorBridge
import com.example.interop.SharedBackMedia
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutWithCleanup
import com.example.ui.components.AdaptiveStudioNavRail
import com.example.ui.components.FloatingStudioNavBar
import com.example.ui.components.StudioTab
import com.example.ui.screens.CreatorStudioScreen
import com.example.ui.screens.MediaLibraryScreen
import com.example.ui.screens.MonetizationWalletScreen
import com.example.ui.theme.KromaStudioTheme
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {

    private var inboundSharedMedia by mutableStateOf<SharedBackMedia?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inboundSharedMedia = ExternalEditorBridge.extractSharedMedia(this, intent)

        setContent {
            KromaStudioTheme {
                AppContent(
                    inboundSharedMedia = inboundSharedMedia,
                    onConsumeInboundMedia = { inboundSharedMedia = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ExternalEditorBridge.extractSharedMedia(this, intent)?.let { shared ->
            inboundSharedMedia = shared
        }
    }
}

@Composable
private fun AppContent(
    inboundSharedMedia: SharedBackMedia?,
    onConsumeInboundMedia: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
            )
        }
    }

    val user = currentUser
    if (user == null) {
        LoginScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        )
    } else {
        val uid = user.uid
        key(uid) {
            val studioViewModel: StudioViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        StudioViewModel(
                            repository = StudioRepository(app),
                            currentUserId = uid,
                            currentUser = user,
                        )
                    }
                },
            )

            AuthenticatedStudioScaffold(
                viewModel = studioViewModel,
                inboundSharedMedia = inboundSharedMedia,
                onConsumeInboundMedia = onConsumeInboundMedia,
                onSignOut = {
                    signOutWithCleanup(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null },
                    )
                },
            )
        }
    }
}

@Composable
private fun AuthenticatedStudioScaffold(
    viewModel: StudioViewModel,
    inboundSharedMedia: SharedBackMedia?,
    onConsumeInboundMedia: () -> Unit,
    onSignOut: () -> Unit,
) {
    var currentTab by remember { mutableStateOf(StudioTab.STUDIO) }
    var openPublishSignal by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    // Handle back button cleanly when on secondary tabs
    BackHandler(enabled = currentTab != StudioTab.STUDIO) {
        currentTab = StudioTab.STUDIO
    }

    LaunchedEffect(inboundSharedMedia) {
        if (inboundSharedMedia != null) {
            viewModel.setDraftMedia(
                uri = inboundSharedMedia.localUri,
                mediaType = inboundSharedMedia.mediaType,
                editedExternally = true,
                externalEditorName = inboundSharedMedia.sourceHint,
            )
            currentTab = StudioTab.STUDIO
            openPublishSignal += 1
            onConsumeInboundMedia()
        }
    }

    LaunchedEffect(feedback.message) {
        val msg = feedback.message
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = if (isExpandedScreen) 16.dp else 90.dp),
                )
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                if (isExpandedScreen) {
                    AdaptiveStudioNavRail(
                        currentTab = currentTab,
                        onSelectTab = { currentTab = it },
                        onQuickCreateClick = { currentTab = StudioTab.STUDIO },
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    when (currentTab) {
                        StudioTab.STUDIO -> CreatorStudioScreen(
                            viewModel = viewModel,
                            openPublishSheetSignal = openPublishSignal,
                        )
                        StudioTab.LIBRARY -> MediaLibraryScreen(
                            viewModel = viewModel,
                            onGoToStudio = { currentTab = StudioTab.STUDIO },
                        )
                        StudioTab.WALLET -> MonetizationWalletScreen(
                            viewModel = viewModel,
                            onSignOut = onSignOut,
                        )
                    }

                    if (!isExpandedScreen) {
                        FloatingStudioNavBar(
                            currentTab = currentTab,
                            onSelectTab = { currentTab = it },
                            onQuickCreateClick = { currentTab = StudioTab.STUDIO },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}
