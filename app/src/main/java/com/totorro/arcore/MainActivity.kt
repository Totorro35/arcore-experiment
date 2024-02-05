package com.totorro.arcore

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewAttachmentManager
import com.totorro.arcore.ui.theme.MobileandroidarcoreTheme
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.collision.Vector3
import io.github.sceneview.geometries.Cube
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Size
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.ViewNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
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

fun createAnchorNodeCube(
    engine: Engine,
    materialLoader: MaterialLoader,
    anchor: Anchor
): AnchorNode {
    val anchorNode = AnchorNode(engine = engine, anchor = anchor)
    val boundingBoxNode = CubeNode(
        engine,
        size = Size(0.1f),
        center = Cube.DEFAULT_CENTER,
        materialInstance = materialLoader.createColorInstance(Color(android.graphics.Color.RED), 0f, 0f, 0f)
    ).apply {
        isEditable = true
        isVisible = true
    }
    anchorNode.addChildNode(boundingBoxNode)
    anchorNode.isVisible = true

    return anchorNode
}

@Composable
fun ARModelViewer(context: Context) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    var frame by remember { mutableStateOf<Frame?>(null) }
    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    var childNodes = rememberNodes()
    val materialLoader = rememberMaterialLoader(engine)
    ARScene(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        sessionFeatures = EnumSet.of(Session.Feature.SHARED_CAMERA),
        sessionConfiguration = { session, config ->
            config.focusMode = Config.FocusMode.FIXED
        },
        onSessionCreated = { session: Session ->
            cameraController = CameraController(context, session)
            cameraController?.openCamera()
            cameraController?.toggleAutoFocus(true)
        },
        onSessionResumed = {
        },
        onSessionUpdated = { session, updatedFrame ->
            frame = updatedFrame
        },
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { motionEvent, node ->
                if (node == null) {
                    val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)
                    hitResults?.firstOrNull {
                        it.isValid(
                            depthPoint = false,
                            point = false
                        )
                    }?.createAnchorOrNull()
                        ?.let { anchor ->
                            Toast.makeText(context, "Add Cube", Toast.LENGTH_SHORT).show()
                            childNodes += createAnchorNodeCube(
                                engine = engine,
                                materialLoader = materialLoader,
                                anchor = anchor
                            )
                        }
                }
            },
        ),
        onViewUpdated = {
        },
        onViewCreated = {
        },
        childNodes=childNodes
    )
}
