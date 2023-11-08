package xyz.teamgravity.cameraxdemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import xyz.teamgravity.cameraxdemo.ui.theme.CameraXDemoTheme
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        private val PERMISSIONS: Array<String> = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    private var recording: Recording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasPermissions()) requestPermissions()
        setContent {
            CameraXDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val scope = rememberCoroutineScope()
                    val scaffold = rememberBottomSheetScaffoldState()
                    val controller = remember {
                        LifecycleCameraController(applicationContext).apply {
                            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
                        }
                    }
                    val images = remember { mutableStateListOf<Bitmap>() }

                    BottomSheetScaffold(
                        scaffoldState = scaffold,
                        sheetPeekHeight = 0.dp,
                        sheetContent = {
                            PhotoSheet(
                                photos = images
                            )
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                            CameraPreview(
                                controller = controller
                            )
                            IconButton(
                                onClick = {
                                    controller.cameraSelector =
                                        if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA
                                        else CameraSelector.DEFAULT_BACK_CAMERA
                                },
                                modifier = Modifier.offset(
                                    x = 16.dp,
                                    y = 16.dp
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = stringResource(id = R.string.switch_camera)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceAround,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            scaffold.bottomSheetState.expand()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Photo,
                                        contentDescription = stringResource(id = R.string.open_gallery)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        takePhoto(
                                            controller = controller,
                                            onPhotoTaken = images::add
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = stringResource(id = R.string.take_photo)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        recordVideo(controller)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = stringResource(id = R.string.record_video)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        requestPermissions(PERMISSIONS, 0)
    }

    private fun hasPermissions(): Boolean {
        return PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun takePhoto(
        controller: LifecycleCameraController,
        onPhotoTaken: (Bitmap) -> Unit
    ) {
        if (!hasPermissions()) return
        controller.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val matrix = Matrix()
                    matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                    val bitmap = Bitmap.createBitmap(
                        image.toBitmap(),
                        0,
                        0,
                        image.width,
                        image.height,
                        matrix,
                        true
                    )
                    onPhotoTaken(bitmap)
                }
            }
        )
    }

    private fun recordVideo(controller: LifecycleCameraController) {
        if (!hasPermissions()) return

        if (recording != null) {
            recording?.close()
            recording = null
            return
        }

        val file = File(filesDir, "recording.mp4")
        recording = controller.startRecording(
            FileOutputOptions.Builder(file).build(),
            AudioConfig.create(true),
            ContextCompat.getMainExecutor(applicationContext)
        ) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    recording?.close()
                    recording = null
                    Toast.makeText(
                        this,
                        getString(if (event.hasError()) R.string.video_capture_failed else R.string.video_capture_successful),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

@Composable
fun PhotoSheet(
    photos: List<Bitmap>
) {
    if (photos.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp)
        ) {
            Text(text = stringResource(id = R.string.no_photos))
        }
    } else {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 16.dp,
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(photos) { photo ->
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
fun CameraPreview(
    controller: LifecycleCameraController
) {
    val owner = LocalLifecycleOwner.current
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                this.controller = controller
                controller.bindToLifecycle(owner)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}