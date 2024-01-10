package com.totorro.arcore

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.os.Handler
import android.os.HandlerThread
import com.google.ar.core.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class CameraController(private val context: Context, private val session: Session) {

    private val cameraManager: CameraManager by lazy {
        val context = context.applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val sharedCamera = session.sharedCamera
    private val cameraId = session.cameraConfig.cameraId
    private val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureBuilder: CaptureRequest.Builder

    private val cameraThread = HandlerThread("cameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var isConfigured = false
    private val deviceCallback = sharedCamera.createARDeviceStateCallback(
        object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                session.sharedCamera.arCoreSurfaces.forEach {
                    captureBuilder.addTarget(it)
                }

                val outputConfigurations =
                    session.sharedCamera.arCoreSurfaces.map { OutputConfiguration(it) }
                val sessionStateCallback = sharedCamera.createARSessionStateCallback(
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(_cameraCaptureSession: CameraCaptureSession) {
                            cameraCaptureSession = _cameraCaptureSession
                            isConfigured = true
                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        }
                    },
                    cameraHandler,
                )
                device.createCaptureSessionByOutputConfigurations(
                    outputConfigurations,
                    sessionStateCallback,
                    cameraHandler,
                )
            }

            override fun onDisconnected(camera: CameraDevice) {}

            override fun onError(camera: CameraDevice, error: Int) {}
        },
        cameraHandler,
    )

    @SuppressLint("MissingPermission")
    fun openCamera() = runBlocking {
        cameraManager.openCamera(cameraId, deviceCallback, cameraHandler)
        while (!isConfigured) {
            delay(100)
        }
    }

    fun closeCamera() {
        cameraCaptureSession.close()
        cameraDevice.close()
        cameraThread.quitSafely()
    }

    private fun areaFromPoint(x: Int, y: Int, areaSize: Int): MeteringRectangle {
        val sensorArraySize: IntArray =
            cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let {
                intArrayOf(it.width(), it.height())
            } ?: run {
                intArrayOf(
                    session.cameraConfig.imageSize.width,
                    session.cameraConfig.imageSize.height,
                )
            }

        val focusX = (x / session.cameraConfig.imageSize.width) * sensorArraySize[0]
        val focusY = (y / session.cameraConfig.imageSize.height) * sensorArraySize[1]

        // Create a MeteringRectangle for focusing
        return MeteringRectangle(
            (focusX - areaSize / 2).coerceAtLeast(0),
            (focusY - areaSize / 2).coerceAtLeast(0),
            areaSize,
            areaSize,
            MeteringRectangle.METERING_WEIGHT_MAX - 1,
        )
    }

    private fun isMeteringAreaAWBSupported() = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0 >= 1
    private var isAutoWhiteBalanceEnabled: Boolean = false
    fun toggleAutoWhiteBalance(isAutoWhiteBalanceEnabled: Boolean = !this.isAutoWhiteBalanceEnabled) {
        captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        captureBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, !isAutoWhiteBalanceEnabled)
        cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), null, cameraHandler)
    }

    fun autoWhiteBalanceOnArea(
        x: Int = session.cameraConfig.imageSize.width / 2,
        y: Int = session.cameraConfig.imageSize.height / 2,
        areaSize: Int = AREA_SIZE,
    ) {
        val captureCallbackHandler: CameraCaptureSession.CaptureCallback =
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    super.onCaptureCompleted(session, request, result)
                    when (result.get(CaptureResult.CONTROL_AWB_STATE)) {
                        CaptureResult.CONTROL_AWB_STATE_CONVERGED,
                        null,
                        -> {
                            toggleAutoWhiteBalance(isAutoWhiteBalanceEnabled)
                        }
                    }
                }
            }

        if (isMeteringAreaAWBSupported()) {
            captureBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS, arrayOf(areaFromPoint(x, y, areaSize)))
        }
        captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        captureBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, false)
        cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), captureCallbackHandler, cameraHandler)
    }

    private fun isMeteringAreaAESupported() =
        cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0 >= 1

    private var isAutoExposureEnabled: Boolean = false
    fun toggleAutoExposure(isAutoExposureEnabled: Boolean = !this.isAutoExposureEnabled) {
        if (isMeteringAreaAESupported()) {
            captureBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, null)
        }
        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, !isAutoExposureEnabled)
        cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), null, cameraHandler)
        this.isAutoExposureEnabled = isAutoExposureEnabled
    }

    fun autoExposureOnArea(
        x: Int = session.cameraConfig.imageSize.width / 2,
        y: Int = session.cameraConfig.imageSize.height / 2,
        areaSize: Int = AREA_SIZE,
    ) {
        val area = areaFromPoint(x, y, areaSize)
        val captureCallbackHandler: CameraCaptureSession.CaptureCallback =
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    super.onCaptureCompleted(session, request, result)
                    when (result.get(CaptureResult.CONTROL_AE_STATE)) {
                        null,
                        CaptureResult.CONTROL_AE_STATE_CONVERGED,
                        -> {
                            toggleAutoExposure(isAutoExposureEnabled)
                        }
                    }
                }
            }

        if (isMeteringAreaAESupported()) {
            captureBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(area))
        }
        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false)
        cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), captureCallbackHandler, cameraHandler)
    }

    private fun isMeteringAreaAFSupported() =
        cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0 >= 1

    private var isAutoFocusEnabled: Boolean = false
    fun toggleAutoFocus(isAutoFocusEnabled: Boolean = !this.isAutoFocusEnabled) {
        if (isMeteringAreaAFSupported()) {
            captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, null)
        }
        if (isAutoFocusEnabled) {
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        } else {
            captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO)
        }
        cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), null, cameraHandler)
        this.isAutoFocusEnabled = isAutoFocusEnabled
    }

    fun autoFocusOnArea(
        x: Int = session.cameraConfig.imageSize.width / 2,
        y: Int = session.cameraConfig.imageSize.height / 2,
        areaSize: Int = AREA_SIZE,
    ) {
        val captureCallbackHandler: CameraCaptureSession.CaptureCallback =
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    super.onCaptureCompleted(session, request, result)
                    when (result.get(CaptureResult.CONTROL_AF_STATE)) {
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
                        null,
                        -> {
                            toggleAutoFocus(isAutoFocusEnabled)
                        }
                    }
                }
            }

        if (isMeteringAreaAFSupported()) {
            captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(areaFromPoint(x, y, areaSize)))
        }
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO)
        captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), captureCallbackHandler, cameraHandler)
    }

    private var isFlashEnabled: Boolean = false
    fun toggleFlash(isFlashEnabled: Boolean = !this.isFlashEnabled) {
        captureBuilder.set(CaptureRequest.FLASH_MODE, if (isFlashEnabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
        this.isFlashEnabled = isFlashEnabled
        cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), null, cameraHandler)
    }

    companion object {
        private val TAG = CameraController::class.java.simpleName
        private const val AREA_SIZE = 200
    }
}
