package com.totorro.arcore

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.totorro.arcore.ui.theme.MobileandroidarcoreTheme
import io.github.sceneview.ar.ARScene
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import java.util.EnumSet

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MobileandroidarcoreTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ARModelViewer(this)
                }
            }
        }
    }
}

@Composable
fun ARModelViewer(context: Context) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    var frame by remember { mutableStateOf<Frame?>(null) }
    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    ARScene(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        sessionFeatures = EnumSet.of(Session.Feature.SHARED_CAMERA),
        sessionConfiguration = { _, config ->
            config.apply {
                focusMode = Config.FocusMode.FIXED
            }
        },
        onSessionCreated = { session: Session ->
            cameraController = CameraController(context, session)
            cameraController?.openCamera()
        },
        onSessionResumed = {
        },
        onSessionUpdated = { session, updatedFrame ->
            frame = updatedFrame
        },
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { motionEvent, node ->
                cameraController?.autoFocusOnArea()
            },
        ),
        onViewUpdated = {
        },
        onViewCreated = {
        },
    )
}
