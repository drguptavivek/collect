package edu.aiims.medresodk.auth.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import edu.aiims.medresodk.auth.R
import edu.aiims.medresodk.auth.databinding.ActivityMedresQrScannerBinding
import edu.aiims.medresodk.auth.injection.MedresAuthDependencyComponentProvider
import edu.aiims.medresodk.auth.managers.MedresAuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.odk.collect.projects.ProjectsRepository
import org.odk.collect.settings.keys.MetaKeys
import org.odk.collect.shared.strings.UUIDGenerator
import timber.log.Timber
import javax.inject.Inject


/**
 * MEDRES QR Scanner Activity
 *
 * Standalone QR scanner for MEDRES using CameraX and Google ML Kit.
 * Does not depend on ODK project being configured.
 *
 * Features:
 * - Live camera QR code scanning
 * - Import from gallery photos
 */
class MedresQrScannerActivity : AppCompatActivity() {

    @Inject
    lateinit var authManager: MedresAuthManager

    private lateinit var binding: ActivityMedresQrScannerBinding
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var barcodeScanner: BarcodeScanner
    private var isProcessing = false

    // Dependencies (initialized manually)
    private lateinit var projectsRepository: ProjectsRepository

    // Gallery photo picker
    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handlePhotoImport(it) }
    }

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as MedresAuthDependencyComponentProvider).medresAuthDependencyComponent.inject(this)

        try {
            // Initialize dependencies manually without Dagger
            initializeDependencies()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize dependencies")
            Toast.makeText(this, "Failed to initialize scanner", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityMedresQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupMLKit()
        checkAndRequestCameraPermission()
    }

    private fun initializeDependencies() {
        val uuidGenerator = UUIDGenerator()
        val gson = Gson()

        // Create meta settings wrapper
        val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
        val metaSettings = SharedPreferencesSettings(metaPrefs)

        // Initialize ProjectsRepository
        projectsRepository = org.odk.collect.projects.SharedPreferencesProjectsRepository(
            uuidGenerator,
            gson,
            metaSettings,
            MetaKeys.KEY_PROJECTS
        )
    }

    private fun setupUI() {
        binding.closeButton.setOnClickListener { finish() }
        binding.selectPhotoButton.setOnClickListener { openPhotoPicker() }

        // Get camera provider
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupMLKit() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        barcodeScanner = BarcodeScanning.getClient(options)
    }

    private fun checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        // Wait for camera provider to be ready
        lifecycleScope.launch {
            while (!::cameraProvider.isInitialized) {
                delay(100)
            }
            bindCameraUseCases()
        }
    }

    private fun bindCameraUseCases() {
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    ContextCompat.getMainExecutor(this),
                    QRCodeImageAnalyzer { qrCode ->
                        handleQrCodeResult(qrCode)
                    }
                )
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Timber.e(e, "Camera binding failed")
            Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPhotoPicker() {
        pickPhotoLauncher.launch("image/*")
    }

    private fun handlePhotoImport(uri: android.net.Uri) {
        try {
            // Use ML Kit to decode QR code from image
            val image = InputImage.fromFilePath(this, uri)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { qrCode ->
                            handleQrCodeData(qrCode)
                            return@addOnSuccessListener
                        }
                    }
                    Toast.makeText(this, R.string.medres_invalid_qr_code, Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "Failed to decode QR code from image")
                    Toast.makeText(this, R.string.medres_invalid_qr_code, Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode QR code from image")
            Toast.makeText(this, R.string.medres_invalid_qr_code, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleQrCodeResult(qrCode: String) {
        if (isProcessing) return
        isProcessing = true

        handleQrCodeData(qrCode)

        // Reset processing flag after delay
        lifecycleScope.launch {
            delay(2000)
            isProcessing = false
        }
    }

    private fun handleQrCodeData(qrData: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            // --- Classify via typed parser ---
            val parseResult = edu.aiims.medresodk.auth.qr.MedresQrParser().parse(qrData)

            when (parseResult) {
                is edu.aiims.medresodk.auth.qr.InvalidQr -> {
                    binding.progressBar.visibility = View.GONE
                    Timber.w("QR rejected — invalid: ${parseResult.reason}")
                    Toast.makeText(
                        this@MedresQrScannerActivity,
                        getString(R.string.medres_invalid_qr_code),
                        Toast.LENGTH_LONG
                    ).show()
                    isProcessing = false
                }

                is edu.aiims.medresodk.auth.qr.StandardOdkManagedQr -> {
                    binding.progressBar.visibility = View.GONE
                    Timber.i("Standard ODK managed QR rejected")
                    Toast.makeText(
                        this@MedresQrScannerActivity,
                        "Standard ODK QR rejected. Please use a MEDRES Project QR.",
                        Toast.LENGTH_LONG
                    ).show()
                    isProcessing = false
                }

                is edu.aiims.medresodk.auth.qr.MedresProjectQr -> {
                    handleMedresProjectQr(parseResult)
                }

                is edu.aiims.medresodk.auth.qr.DraftFormQr -> {
                    handleDraftFormQr(parseResult)
                }
            }
        }
    }

    /** Stage a MEDRES project QR and return to login (credentials required). */
    private fun handleMedresProjectQr(qr: edu.aiims.medresodk.auth.qr.MedresProjectQr) {
        Timber.i("MEDRES project QR detected — project ${qr.centralProjectId}")

        val authPrefs = getSharedPreferences(
            edu.aiims.medresodk.auth.utils.MedresConstants.MEDRES_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val stagingStore = edu.aiims.medresodk.auth.qr.MedresQrStagingStore(authPrefs)

        stagingStore.stageMedresProject(
            edu.aiims.medresodk.auth.qr.StagedMedresProjectContext(
                authBaseUrl = qr.authBaseUrl,
                centralProjectId = qr.centralProjectId,
                projectName = qr.projectName,
                usernameHint = qr.usernameHint,
                generalSettingsJson = qr.generalSettingsJson,
                adminSettingsJson = qr.adminSettingsJson
            )
        )

        binding.progressBar.visibility = View.GONE
        Toast.makeText(
            this@MedresQrScannerActivity,
            R.string.medres_settings_imported_successfully,
            Toast.LENGTH_SHORT
        ).show()
        routeAfterSuccessfulScan()
    }

    /** Stage a draft QR and return to login (no credentials required — demo path). */
    private fun handleDraftFormQr(qr: edu.aiims.medresodk.auth.qr.DraftFormQr) {
        Timber.i("Draft QR detected — project ${qr.centralProjectId} form ${qr.formId}")

        val authPrefs = getSharedPreferences(
            edu.aiims.medresodk.auth.utils.MedresConstants.MEDRES_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val stagingStore = edu.aiims.medresodk.auth.qr.MedresQrStagingStore(authPrefs)

        stagingStore.stageDraftForm(
            edu.aiims.medresodk.auth.qr.StagedDraftFormContext(
                originalDraftUrl = qr.originalDraftUrl,
                centralProjectId = qr.centralProjectId,
                formId = qr.formId,
                displayName = qr.displayName,
                displayIcon = qr.displayIcon,
                generalSettingsJson = qr.generalSettingsJson
            )
        )

        binding.progressBar.visibility = View.GONE
        Toast.makeText(
            this@MedresQrScannerActivity,
            "Draft form detected. Tap 'Start Testing' to continue.",
            Toast.LENGTH_LONG
        ).show()
        routeAfterSuccessfulScan()
    }

    /** After staging, return to the login screen (just finish — login is always our caller). */
    private fun routeAfterSuccessfulScan() {
        finish()
    }

    private fun setCurrentProject(projectId: String) {
        // Set the current project ID in meta preferences
        val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
        metaPrefs.edit()
            .putString("current_project_id", projectId)
            .commit()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.medres_camera_permission_required)
            .setMessage(R.string.medres_camera_permission_message)
            .setPositiveButton(R.string.medres_ok) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * ML Kit Image Analyzer for QR code detection
     */
    private inner class QRCodeImageAnalyzer(
        private val onQrCodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { qrCode ->
                                onQrCodeDetected(qrCode)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Timber.e(e, "QR code detection failed")
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    /**
     * SharedPreferences implementation of ODK Settings interface
     */
    private class SharedPreferencesSettings(private val prefs: android.content.SharedPreferences) : org.odk.collect.shared.settings.Settings {
        override fun save(key: String, value: Any?) {
            val editor = prefs.edit()
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
                null -> editor.remove(key)
                else -> throw IllegalArgumentException("Unsupported type")
            }
            editor.apply()
        }

        override fun getString(key: String) = prefs.getString(key, null)
        override fun getBoolean(key: String) = prefs.getBoolean(key, false)
        override fun getLong(key: String) = prefs.getLong(key, 0L)
        override fun getInt(key: String) = prefs.getInt(key, 0)
        override fun getFloat(key: String) = prefs.getFloat(key, 0f)
        override fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, null)
        override fun getAll(): Map<String, *> = prefs.all
        override fun contains(key: String) = prefs.contains(key)
        override fun remove(key: String) { prefs.edit().remove(key).apply() }
        override fun clear() { prefs.edit().clear().apply() }

        // Unused stubs
        override fun setDefaultForAllSettingsWithoutValues() {}
        override fun saveAll(prefs: Map<String, Any?>) {
            prefs.forEach { save(it.key, it.value) }
        }
        override fun reset(key: String) { remove(key) }
        override fun registerOnSettingChangeListener(listener: org.odk.collect.shared.settings.Settings.OnSettingChangeListener) {}
        override fun unregisterOnSettingChangeListener(listener: org.odk.collect.shared.settings.Settings.OnSettingChangeListener) {}
    }
}
