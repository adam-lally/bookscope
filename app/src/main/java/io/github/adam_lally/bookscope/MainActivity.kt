package io.github.adam_lally.bookscope

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.adam_lally.bookscope.theme.BookScopeTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class MainActivity : ComponentActivity() {
    val TAG = "BookScope"
    val bookDetector: BookDetector =
        //BasicBookDetector()
        BookDetectorUsingFunctionCalling()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permissions
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) -> {
                // Camera permission already granted
                setContent()
            }

            else -> {
                cameraPermissionRequest.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setContent() {
        setContent {
            BookScopeTheme {
                BookScopeApp()
            }
        }
    }

    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                setContent()
            } else {
                // TODO: Handle Camera permission denied
            }
        }

    @Preview(showBackground = true)
    @Composable
    fun BookScopeApp() {
        CameraPreviewScreen(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        )
    }

    @Composable
    fun CameraPreviewScreen(modifier: Modifier = Modifier) {
        val lensFacing = CameraSelector.LENS_FACING_BACK
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val preview = androidx.camera.core.Preview.Builder().build()
        val previewView = remember {
            PreviewView(context)
        }
        val cameraxSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val imageCapture = remember {
            ImageCapture.Builder().build()
        }
        LaunchedEffect(lensFacing) {
            val cameraProvider = context.getCameraProvider()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraxSelector, preview, imageCapture)
            preview.setSurfaceProvider(previewView.surfaceProvider)
        }

        // Whether to display the spinning progress indicator
        var loading by remember { mutableStateOf(false) }

        // Whether the result dialog is open
        val openResultDialog = remember { mutableStateOf(false) }

        // Text to display in the result dialog (for the "Describe Image" button)
        var displayText by remember { mutableStateOf("") }

        // Book information to display in the result dialog (for the "Detect Books" button)
        var bookInfoList: List<BookInfo> by remember { mutableStateOf(emptyList()) }

        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
            AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
            Row {
                Button(
                    onClick = {
                        loading = true
                        captureImage(imageCapture, context) {
                            //Get image description from the LLM and display it in a dialog
                            getImageDescription(it) {
                                displayText = it
                                bookInfoList = emptyList()
                                openResultDialog.value = true
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(text = "Describe Image")
                }
                Button(
                    onClick = {
                        loading = true
                        captureImage(imageCapture, context) {
                            // Get BookInfo for books in the image and display them in a dialog
                            getBookInfo(it) {
                                displayText = it.message
                                bookInfoList = it.bookInfo
                                openResultDialog.value = true
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(text = "Detect Books")
                }
            }
        }

        // Display the progress indicator while loading
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ){
                CircularProgressIndicator(
                    modifier = Modifier.width(64.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        // Display the result dialog when we have a result to display
        if (openResultDialog.value) {
            ResultDialog(
                text = displayText,
                bookInfoList = bookInfoList,
                onDismissRequest = { openResultDialog.value = false }
            )
        }
    }

    private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
        suspendCoroutine { continuation ->
            ProcessCameraProvider.getInstance(this).also { cameraProvider ->
                cameraProvider.addListener({
                    continuation.resume(cameraProvider.get())
                }, ContextCompat.getMainExecutor(this))
            }
        }

    private fun captureImage(
        imageCapture: ImageCapture,
        context: Context,
        callback: (ByteArray) -> Unit
    ) {
        val name = "CameraxImage.jpeg"

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val byteArray = ByteArray(buffer.capacity())
                    buffer.get(byteArray)
                    callback(byteArray)
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    super.onError(exception)
                }
            })
    }

    private fun getBookInfo(imageBytes: ByteArray, callback: (BookDetectorResult)->Unit) {
        lifecycleScope.launch {
            callback(bookDetector.detectBooksInImage(imageBytes))
        }
    }

    private fun getImageDescription(imageBytes: ByteArray, callback: (String) -> Unit) {
        lifecycleScope.launch {
            callback(describeImage(imageBytes))
        }
    }

    // Card for each BookInfo.  Includes a button which does a web search for the book.
    @Composable
    fun BookInfoCard(bookInfo: BookInfo, modifier: Modifier = Modifier) {
        Card(
            modifier = modifier
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            ),
        ) {
            Row {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = bookInfo.title,
                        modifier = Modifier
                            .wrapContentSize(Alignment.Center),
                        textAlign = TextAlign.Left,
                    )
                    val authorText = bookInfo.author_name?.joinToString(", ") ?: "Unknown"
                    Text(
                        text = "by $authorText",
                        modifier = Modifier
                            .wrapContentSize(Alignment.Center),
                        textAlign = TextAlign.Left,
                    )
                    val ratingText = bookInfo.ratings_average?.let { "%.1f".format(it) } ?: "None"
                    Text(
                        text = "Rating: $ratingText",
                        modifier = Modifier
                            .wrapContentSize(Alignment.Center),
                        textAlign = TextAlign.Left,
                    )
                }
                Button(
                    onClick = {
                        val query = bookInfo.title + " " + (bookInfo.author_name?.joinToString(" ") ?: "Unknown")
                        val intent = Intent(Intent.ACTION_WEB_SEARCH)
                        intent.putExtra(SearchManager.QUERY, query) // query contains search string
                        startActivity(intent)
                    },
                ) {
                    Text(text = ">")
                }
            }
        }
    }

    //Dialog for displaying either text or a list of BookInfo
    //TODO: might be cleaner to have two different dialogs?
    @Composable
    fun ResultDialog(text: String, bookInfoList: List<BookInfo>, onDismissRequest: () -> Unit) {
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (text.isNotEmpty()) {
                        Text(
                            text = text,
                            modifier = Modifier
                                .padding(8.dp),
                        )
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(
                            items = bookInfoList,
                            itemContent = {
                                BookInfoCard(it)
                            })
                    }
                    Button(
                        onClick = { onDismissRequest() },
                        modifier = Modifier
                            .padding(8.dp),
                    ) {
                        Text(text = "Close")
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun ResultDialogPreview() {
        ResultDialog(
            text = "",
            bookInfoList = listOf(
                BookInfo("Economics in America", listOf("Angus Deaton")),
                BookInfo("The Wealth of Nations", listOf("Adam Smith"), ratings_average = 4.5)
            ),
            onDismissRequest = { }
        )
    }
}