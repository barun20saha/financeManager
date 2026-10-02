package com.example.myaccounts

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager // <--- ADD THIS IMPORT
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.myaccounts.storage.StorageManager
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var storageManager: StorageManager

    private var pendingBackupData: String? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    // ------------------------------------------------------------
    // Launcher for selecting where to save the backup JSON file
    // ------------------------------------------------------------

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->

        uri?.let {
            try {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(
                        pendingBackupData?.toByteArray(Charsets.UTF_8)
                            ?: ByteArray(0)
                    )
                }

                Toast.makeText(
                    this,
                    "Backup file saved successfully",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                e.printStackTrace()

                Toast.makeText(
                    this,
                    "Failed to save backup file",
                    Toast.LENGTH_SHORT
                ).show()

            } finally {
                pendingBackupData = null
            }
        }
    }

    // ------------------------------------------------------------
    // Launcher for selecting a backup JSON file to restore
    // ------------------------------------------------------------

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->

        uri?.let {
            restoreDataFromUri(it)
        }
    }

    // ------------------------------------------------------------
    // Launcher for HTML <input type="file">
    // ------------------------------------------------------------

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        if (filePathCallback != null) {

            val uris = WebChromeClient.FileChooserParams.parseResult(
                result.resultCode,
                result.data
            )

            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }
    }

    // ------------------------------------------------------------
    // Activity creation
    // ------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Initialize encrypted BSON storage manager
        storageManager = StorageManager(this)

        // --------------------------------------------------------
        // WebView
        // --------------------------------------------------------

        webView = findViewById(R.id.webView)

        webView.webViewClient = WebViewClient()

        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {

                // Cancel any previous callback
                this@MainActivity.filePathCallback?.onReceiveValue(null)

                this@MainActivity.filePathCallback = filePathCallback

                try {

                    val intent = fileChooserParams?.createIntent()

                    if (intent != null) {
                        fileChooserLauncher.launch(intent)
                    } else {
                        this@MainActivity.filePathCallback = null
                        return false
                    }

                } catch (e: Exception) {

                    e.printStackTrace()

                    this@MainActivity.filePathCallback = null

                    return false
                }

                return true
            }
        }

        // --------------------------------------------------------
        // WebView settings
        // --------------------------------------------------------

        val settings = webView.settings

        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowUniversalAccessFromFileURLs = true

        // --------------------------------------------------------
        // JavaScript ↔ Android bridge  
        // --------------------------------------------------------

        webView.addJavascriptInterface(
            AndroidInterface(this),
            "AndroidBridge"
        )

        // --------------------------------------------------------
        // Load the HTML application
        // --------------------------------------------------------

        webView.loadUrl("file:///android_asset/index.html")

        // --------------------------------------------------------
        // Android back button handling
        // --------------------------------------------------------

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (webView.canGoBack()) {

                        webView.goBack()

                    } else {

                        isEnabled = false

                        onBackPressedDispatcher.onBackPressed()

                        isEnabled = true
                    }
                }
            }
        )

        // --------------------------------------------------------
        // Biometric authentication
        // --------------------------------------------------------

        setupBiometric()
    }

    // ============================================================
    // BIOMETRIC AUTHENTICATION
    // ============================================================

    private fun setupBiometric() {
    executor = ContextCompat.getMainExecutor(this)

    biometricPrompt = BiometricPrompt(
        this,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                webView.post {
                    val message = org.json.JSONObject.quote(errString.toString())
                    webView.evaluateJavascript("window.onBiometricResult(false, $message)", null)
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                webView.post {
                    webView.evaluateJavascript("window.onBiometricResult(true, 'Success')", null)
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                webView.post {
                    webView.evaluateJavascript("window.onBiometricResult(false, 'Failed')", null)
                }
            }
        }
    )

    // Set authenticators to include Device PIN/Pattern alongside Biometrics
    promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock My Accounts")
        .setSubtitle("Use fingerprint, face, or device PIN")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    }
    // ------------------------------------------------------------
    // Start biometric authentication
    // ------------------------------------------------------------

    fun authenticateBiometric() {

        runOnUiThread {

            biometricPrompt.authenticate(promptInfo)
        }
    }

    // ============================================================
    // BACKUP / RESTORE
    // ============================================================

    // ------------------------------------------------------------
    // Save backup state when Activity is recreated
    // ------------------------------------------------------------

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        super.onSaveInstanceState(outState)

        outState.putString(
            "pendingBackupData",
            pendingBackupData
        )
    }

    // ------------------------------------------------------------
    // Restore backup state
    // ------------------------------------------------------------

    override fun onRestoreInstanceState(
        savedInstanceState: Bundle
    ) {
        super.onRestoreInstanceState(savedInstanceState)

        pendingBackupData =
            savedInstanceState.getString(
                "pendingBackupData"
            )
    }

    // ------------------------------------------------------------
    // Restore data from selected JSON file
    // ------------------------------------------------------------

    private fun restoreDataFromUri(uri: Uri) {

        try {

            contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->

                    val jsonStr =
                        inputStream
                            .bufferedReader()
                            .use { it.readText() }

                    // Safely quote JSON before passing it
                    // to JavaScript.
                    val escapedJson =
                        org.json.JSONObject.quote(jsonStr)

                    webView.post {

                        webView.evaluateJavascript(
                            "window.restoreFromAndroid($escapedJson)",
                            null
                        )
                    }
                }

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Failed to read backup file",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ------------------------------------------------------------
    // Save backup file using Android file picker
    // ------------------------------------------------------------

    fun saveFile(
        filename: String,
        data: String
    ) {

        pendingBackupData = data

        createDocumentLauncher.launch(filename)
    }

    // ------------------------------------------------------------
    // Open backup file picker
    // ------------------------------------------------------------

    fun openFilePicker() {

        openDocumentLauncher.launch(
            arrayOf("*/*")
        )
    }

    // ============================================================
    // APP DATA STORAGE
    // ============================================================

    /**
     * Saves application data.
     *
     * JavaScript provides JSON.
     *
     * StorageManager converts:
     *
     * JSON
     *   ↓
     * AppData
     *   ↓
     * BSON
     *   ↓
     * AES-GCM encryption
     *   ↓
     * fireball_data.bson.enc
     */
    fun saveAppData(
        jsonData: String
    ): Boolean {

        return storageManager.saveAppDataJson(
            jsonData
        )
    }

    /**
     * Loads application data.
     *
     * StorageManager reads:
     *
     * fireball_data.bson.enc
     *   ↓
     * AES-GCM decryption
     *   ↓
     * BSON
     *   ↓
     * AppData
     *   ↓
     * JSON
     *
     * The JSON is then returned to JavaScript.
     */
    fun loadAppData(): String? {

        return storageManager.loadAppDataJson()
    }
}

// =================================================================
// JAVASCRIPT INTERFACE
// =================================================================

class AndroidInterface(
    private val activity: MainActivity
) {

    // ------------------------------------------------------------
    // Save backup file
    // ------------------------------------------------------------

    @JavascriptInterface
    fun saveFile(
        filename: String,
        data: String
    ) {

        activity.runOnUiThread {

            activity.saveFile(
                filename,
                data
            )
        }
    }

    // ------------------------------------------------------------
    // Open backup file picker
    // ------------------------------------------------------------

    @JavascriptInterface
    fun openFilePicker() {

        activity.runOnUiThread {

            activity.openFilePicker()
        }
    }

    // ------------------------------------------------------------
    // Biometric authentication
    // ------------------------------------------------------------

    @JavascriptInterface
    fun authenticateBiometric() {

        activity.authenticateBiometric()
    }

    // ------------------------------------------------------------
    // Save application data
    // ------------------------------------------------------------

    @JavascriptInterface
    fun saveAppData(
        jsonData: String
    ): Boolean {

        return activity.saveAppData(
            jsonData
        )
    }

    // ------------------------------------------------------------
    // Load application data
    // ------------------------------------------------------------

    @JavascriptInterface
    fun loadAppData(): String? {

        return activity.loadAppData()
    }

    
}