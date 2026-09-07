package com.example.myaccounts // Keep YOUR original package name here!

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
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.myaccounts.storage.JsonStorageManager
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var storageManager: JsonStorageManager
    private var pendingBackupData: String? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    // Launcher for selecting where to save the backup JSON file
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(pendingBackupData?.toByteArray(Charsets.UTF_8) ?: ByteArray(0))
                }
                Toast.makeText(this, "Backup file saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to save backup file", Toast.LENGTH_SHORT).show()
            } finally {
                pendingBackupData = null
            }
        }
    }

    // Launcher for selecting a backup JSON file to restore (via bridge)
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            restoreDataFromUri(it)
        }
    }

    // Launcher for standard HTML file input (onShowFileChooser)
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback != null) {
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storageManager = JsonStorageManager(this)

        webView = findViewById(R.id.webView)
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                
                try {
                    val intent = fileChooserParams?.createIntent()
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // Register the JS bridge
        webView.addJavascriptInterface(AndroidInterface(this), "AndroidBridge")

        webView.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(enabled = true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )

        setupBiometric()
    }

    private fun setupBiometric() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    webView.post {
                        webView.evaluateJavascript("window.onBiometricResult(false, '$errString')", null)
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
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for My Accounts")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    fun authenticateBiometric() {
        runOnUiThread {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pendingBackupData", pendingBackupData)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        pendingBackupData = savedInstanceState.getString("pendingBackupData")
    }

    private fun restoreDataFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonStr = inputStream.bufferedReader().use { it.readText() }
                // Use a safer way to call JS: evaluateJavascript with quoted string
                val escapedJson = org.json.JSONObject.quote(jsonStr)
                webView.post {
                    webView.evaluateJavascript("window.restoreFromAndroid($escapedJson)", null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to read backup file", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveFile(filename: String, data: String) {
        pendingBackupData = data
        createDocumentLauncher.launch(filename)
    }

    fun openFilePicker() {
        openDocumentLauncher.launch(arrayOf("*/*"))
    }

    fun saveAppData(jsonData: String): Boolean {
        return storageManager.saveAppData(jsonData)
    }

    fun loadAppData(): String? {
        return storageManager.loadAppData()
    }
}

class AndroidInterface(private val activity: MainActivity) {
    @JavascriptInterface
    fun saveFile(filename: String, data: String) {
        activity.runOnUiThread {
            activity.saveFile(filename, data)
        }
    }

    @JavascriptInterface
    fun openFilePicker() {
        activity.runOnUiThread {
            activity.openFilePicker()
        }
    }

    @JavascriptInterface
    fun authenticateBiometric() {
        activity.authenticateBiometric()
    }

    @JavascriptInterface
    fun saveAppData(jsonData: String): Boolean {
        return activity.saveAppData(jsonData)
    }

    @JavascriptInterface
    fun loadAppData(): String? {
        return activity.loadAppData()
    }
}