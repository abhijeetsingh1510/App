package com.example.a1app
import android.content.pm.PackageManager
import android.Manifest
import java.nio.ByteOrder
import android.graphics.Color


import android.graphics.BitmapFactory


import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a1app.ui.theme._1appTheme
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import android.content.res.AssetFileDescriptor
import java.nio.MappedByteBuffer
import java.io.File

class MainActivity : ComponentActivity() {

    // TensorFlow Lite interpreter
    private lateinit var interpreter: Interpreter
    private var isModelLoaded = false

    // Image capture launchers
    private lateinit var inputImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var verificationImageLauncher: ActivityResultLauncher<Intent>

    // URIs for input and verification images
    private var inputImageUri: Uri? = null
    private var verificationImageUri: Uri? = null
    private lateinit var photoFile: File

    companion object {
        private const val CAMERA_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request camera permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        }

        // Set the content for the Compose UI
        setContent {
            _1appTheme {
                App(
                    onTakeInputImage = { captureInputImage() },
                    onTakeVerificationImage = { captureVerificationImage() },
                    onVerify = { verifyPerson() }
                )
            }
        }

        // Initialize image capture launchers
        inputImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val extras = result.data?.extras
                val bitmap = extras?.get("data") as Bitmap?
                if (bitmap != null) {
                    inputImageUri = saveBitmapToUri(bitmap)
                    Log.d("Image Capture", "Input Image URI: $inputImageUri")
                } else {
                    Log.e("Image Capture", "Captured bitmap is null.")
                }
            } else {
                Log.e("Image Capture", "Input image capture failed.")
            }
        }

        verificationImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val extras = result.data?.extras
                val bitmap = extras?.get("data") as Bitmap?
                if (bitmap != null) {
                    verificationImageUri = saveBitmapToUri(bitmap)
                    Log.d("Image Capture", "Verification Image URI: $verificationImageUri")
                } else {
                    Log.e("Image Capture", "Captured bitmap is null.")
                }
            } else {
                Log.e("Image Capture", "Verification image capture failed.")
            }
        }


        // Load the TensorFlow Lite model
        try {
            loadModel()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Method to capture input image from the camera
    private fun captureInputImage() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            Log.d("Image Capture", "Launching camera for input image")
            inputImageLauncher.launch(takePictureIntent)
        } else {
            Log.e("Camera", "No camera app available")
        }
    }

    // Method to capture verification image from the camera
    private fun captureVerificationImage() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            Log.d("Image Capture", "Launching camera for verification image")
            verificationImageLauncher.launch(takePictureIntent)
        } else {
            Log.e("Camera", "No camera app available")
        }
    }

    private fun saveBitmapToUri(bitmap: Bitmap?): Uri? {
        if (bitmap == null) return null
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "temp_image", null)
        if (path == null) {
            Log.e("Image Save", "Failed to save bitmap to URI")
            return null
        }
        return Uri.parse(path)
    }

    // Load TensorFlow Lite model
    @Throws(IOException::class)
    private fun loadModel() {
        try {
            val assetFileDescriptor: AssetFileDescriptor = assets.openFd("siamesemodel.tflite")
            val fileInputStream = assetFileDescriptor.createInputStream()
            val fileChannel: FileChannel = fileInputStream.channel
            val startOffset: Long = assetFileDescriptor.startOffset
            val declaredLength: Long = assetFileDescriptor.declaredLength
            val tfliteModel: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(tfliteModel)
            isModelLoaded = true // Set model loaded flag to true after successful loading
            Log.d("Model Loading", "Model loaded successfully.")
        } catch (e: IOException) {
            Log.e("Model Loading", "Failed to load model: ${e.message}")
            throw e  // Re-throwing for further handling
        }
    }




    // Run the verification process
    private fun verifyPerson() {
        Log.d("Verification", "Verify button clicked")
        if (inputImageUri != null && verificationImageUri != null) {
            Log.d("Verification", "Input URI: $inputImageUri, Verification URI: $verificationImageUri")
            try {
                val inputImageBitmap = loadBitmapFromUri(inputImageUri!!)
                val verificationImageBitmap = loadBitmapFromUri(verificationImageUri!!)
                if (inputImageBitmap == null || verificationImageBitmap == null) {
                    Log.e("Verification", "Failed to load one of the images")
                    return
                }

                Log.d("Verification", "Both images captured successfully.")
                Log.d("Verification", "Input Image Size: ${inputImageBitmap.width}x${inputImageBitmap.height}")
                Log.d("Verification", "Verification Image Size: ${verificationImageBitmap.width}x${verificationImageBitmap.height}")
                if (isModelLoaded) {  // Check if the model is loaded before running inference
                    runVerification(inputImageBitmap, verificationImageBitmap)
                } else {
                    Log.e("Verification", "Model not loaded, cannot run inference.")
                }


            } catch (e: Exception) {
                Log.e("Verification", "Error loading images: ${e.message}")
            }
        } else {
            Log.d("Verification", "Please capture both input and verification images.")
        }
    }

    // Function to load bitmap from URI using InputStream
//    private fun loadBitmapFromUri(uri: Uri): Bitmap {
//        val inputStream = contentResolver.openInputStream(uri)
//        return BitmapFactory.decodeStream(inputStream).also {
//            if (it == null) {
//                Log.e("Image Load", "Failed to decode bitmap from URI: $uri")
//            } else {
//                Log.d("Image Load", "Successfully loaded bitmap from URI: $uri")
//            }
//        }
//    }
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
    }

    // Preprocess image and run inference

    private fun runVerification(inputImg: Bitmap, verificationImg: Bitmap) {
        // Resize images to 100x100 if they're not already
        val resizedInputImg = Bitmap.createScaledBitmap(inputImg, 100, 100, true)
        val resizedVerificationImg = Bitmap.createScaledBitmap(verificationImg, 100, 100, true)

        // Preprocess images to get FloatArrays
        val inputTensor = preprocess(resizedInputImg) // Should return FloatArray of shape (100, 100, 3)
        val verificationTensor = preprocess(resizedVerificationImg) // Should return FloatArray of shape (100, 100, 3)

        // Create a 4D array for the input (1, 100, 100, 3)
        val inputArray = Array(1) { Array(100) { Array(100) { FloatArray(3) } } }

        // Populate the input array with the two tensors
        for (h in 0 until 100) {
            for (w in 0 until 100) {
                inputArray[0][h][w][0] = inputTensor[(h * 100 + w) * 3]     // R
                inputArray[0][h][w][1] = inputTensor[(h * 100 + w) * 3 + 1] // G
                inputArray[0][h][w][2] = inputTensor[(h * 100 + w) * 3 + 2] // B
            }
        }

        // Create the verification input array in the same way
        val verificationArray = Array(1) { Array(100) { Array(100) { FloatArray(3) } } }
        for (h in 0 until 100) {
            for (w in 0 until 100) {
                verificationArray[0][h][w][0] = verificationTensor[(h * 100 + w) * 3]     // R
                verificationArray[0][h][w][1] = verificationTensor[(h * 100 + w) * 3 + 1] // G
                verificationArray[0][h][w][2] = verificationTensor[(h * 100 + w) * 3 + 2] // B
            }
        }

        // Allocate tensors if not done already
        interpreter.allocateTensors()
        val result = Array(1) { FloatArray(1) }
        Log.d("Verification", "Running model inference")

        // Prepare the input for the model
        val combinedInputArray = Array(1) { Array(2) { Array(100) { Array(100) { FloatArray(3) } } } }
        combinedInputArray[0][0] = inputArray[0] // First image
        combinedInputArray[0][1] = verificationArray[0] // Second image

        try {
            interpreter.run(combinedInputArray, result)
            Log.d("Verification", "Result from model: ${result[0][0]}")
        } catch (e: Exception) {
            Log.e("Verification", "Error running inference: ${e.message}")
            return
        }

        // Decide if verification passed based on result
        val isVerified = result[0][0] > 0.5  // Adjust the threshold based on your model's output
        if (isVerified) {
            Log.d("Verification", "Verified!")
        } else {
            Log.d("Verification", "Not Verified!")
        }
    }





    // Adjust the preprocess function to return a FloatArray

    private fun preprocess(bitmap: Bitmap): FloatArray {
        // Resize the bitmap to 100x100 if it's not already
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true)

        // Create a FloatArray to hold the normalized pixel values
        val floatArray = FloatArray(100 * 100 * 3)

        // Loop through each pixel and convert to normalized values
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                // Get the pixel value
                val pixel = resizedBitmap.getPixel(x, y)

                // Extract RGB values and normalize to [0, 1]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                // Populate the floatArray
                floatArray[(y * 100 + x) * 3] = r // R
                floatArray[(y * 100 + x) * 3 + 1] = g // G
                floatArray[(y * 100 + x) * 3 + 2] = b // B
            }
        }

        return floatArray
    }






}

@Composable
fun MainScreen(
    onTakeInputImage: () -> Unit,
    onTakeVerificationImage: () -> Unit,
    onVerify: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { onTakeInputImage() }) {
            Text("Take Input Image")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onTakeVerificationImage() }) {
            Text("Take Verification Image")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onVerify() }) {
            Text("Verify")
        }
    }
}

@Composable
fun App(
    onTakeInputImage: () -> Unit,
    onTakeVerificationImage: () -> Unit,
    onVerify: () -> Unit
) {
    MainScreen(
        onTakeInputImage = onTakeInputImage,
        onTakeVerificationImage = onTakeVerificationImage,
        onVerify = onVerify
    )
}
