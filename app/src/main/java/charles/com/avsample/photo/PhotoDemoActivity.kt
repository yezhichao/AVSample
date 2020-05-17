package charles.com.avsample.photo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
import android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import charles.com.avsample.R
import kotlinx.android.synthetic.main.activity_record_demo.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.security.auth.callback.Callback


class PhotoDemoActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    companion object {
        private const val TAG = "RecordDemoActivity"
        private const val RC_CAMERA_AND_STORAGE = 100
    }

    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var camera: Camera
    private var backCamera: Int = -1
    private var frontCamera: Int = -1
    private var cameraId: Int = -1
    private lateinit var cameraParams: Camera.Parameters

    private val pictureCallback = Camera.PictureCallback { data, _ ->
        val pictureFile: File = getOutputMediaFile(MEDIA_TYPE_IMAGE) ?: run {
            Log.d(TAG, ("Error creating media file, check storage permissions"))
            return@PictureCallback
        }

        try {
            val fos = FileOutputStream(pictureFile)
            fos.write(data)
            fos.close()
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: ${e.message}")
        } catch (e: IOException) {
            Log.d(TAG, "Error accessing file: ${e.message}")
        }

        camera?.startPreview()
    }

    private lateinit var mOrientationListener: OrientationEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_demo)

        initSurfaceView()
        takePhotoButton.setOnClickListener {
            // get an image from the camera
            camera?.takePicture(null, null, pictureCallback)
        }

        switchCameraButton.setOnClickListener {
            //  切换摄像头
            cameraId = if (cameraId == backCamera) frontCamera else backCamera
            stopPreviewAndReleaseCamera()
            openCamera()
        }

        mOrientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                Log.d(TAG, "orientation changed $orientation")
                if (orientation == ORIENTATION_UNKNOWN) return
                if (!this@PhotoDemoActivity::camera.isInitialized) return
                val info = CameraInfo()
                Camera.getCameraInfo(cameraId, info)
                val orientationRotate = (orientation + 45) / 90 * 90;
                var rotation = if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                    (info.orientation - orientationRotate + 360) % 360;
                } else {  // back-facing camera
                    (info.orientation + orientationRotate) % 360;
                }
                cameraParams.setRotation(rotation);

                val cameraParameters = camera.parameters
                cameraParameters.setRotation(rotation)
                Log.d(TAG, "orientation changed cameraParameters $cameraParameters")
                camera.parameters = cameraParameters
            }
        }
    }

    /**
     * Activity的方法，把权限请求结果给EasyPermissions去处理
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 把申请权限结果给EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    /**
     * 有权限被拒绝，如果是点了不再提醒，会提示用户去设置打开权限，AppSettingsDialog可以定制
     */
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.d(TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size)
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show();
        }
    }

    /**
     * 从设置权限页面返回，这里由于返回时自动会回调surfaceCreated去检查权限，这里可以什么都不做
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            // 从设置权限页面返回
            Toast.makeText(this, "从设置权限页面返回", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 有权限被允许，就是个回调，这里可以不做事情
     */
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Log.d(TAG, "onPermissionsGranted:" + requestCode + ":" + perms.size)
    }

    /**
     * 停止预览并释放摄像头
     */
    private fun stopPreviewAndReleaseCamera() {
        mOrientationListener.disable()
        camera.stopPreview()
        camera.release()
    }

    /**
     * 初始化SurfaceView
     */
    private fun initSurfaceView() {
            // 已经有权限
            surfaceHolder = previewSurfaceView.holder
            surfaceHolder.addCallback(object : Callback, SurfaceHolder.Callback {
                override fun surfaceChanged(
                    holder: SurfaceHolder?, format: Int, width: Int, height: Int
                ) {
                    Log.d(TAG, "surfaceChanged holder $holder, format $format, width $width, height $height")
                    try {
                        openCamera()
                    } catch (e: IOException) {
                        Log.e(TAG, "preview error $e")
                    }
                }

                override fun surfaceDestroyed(holder: SurfaceHolder?) {
                    Log.d(TAG, " surfaceDestroyed holder $holder")
                    camera?.apply {
                        // 记得disable OrientationEventListener，否则在Camera release之后再去操作camera会抛异常
                        mOrientationListener.disable()
                        stopPreview()
                        release()
                    }
                }

                override fun surfaceCreated(holder: SurfaceHolder?) {
                    Log.d(TAG, "surfaceCreated holder $holder")
                    frontCamera = findFrontCamera()
                    backCamera = findBackCamera()
                    // 默认使用前置摄像头
                    cameraId = frontCamera
                }
            })
    }

    /**
     * 打开摄像头预览
     */
    @AfterPermissionGranted(RC_CAMERA_AND_STORAGE)
    private fun openCamera() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Log.d(TAG, "openCamera hasPermissions ${EasyPermissions.hasPermissions(this, *perms)}")
        if (EasyPermissions.hasPermissions(this, *perms)) {
            camera = Camera.open(cameraId)
            cameraParams = camera.parameters
            // 设置角度
            setCameraDisplayOrientation(this@PhotoDemoActivity, frontCamera, camera)
            camera.setPreviewDisplay(surfaceHolder) // 通过surfaceView显示取景画面
            camera.startPreview() // 开始预览
            mOrientationListener.enable()
        } else {
            EasyPermissions.requestPermissions(
                this, "应用需要相机/存储权限来预览/存储您的照片",
                RC_CAMERA_AND_STORAGE, *perms
            );
        }
    }

    /**
     * 设置预览画面的角度
     */
    fun setCameraDisplayOrientation(activity: Activity, cameraId: Int, camera: Camera) {
        val info = CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val rotation = activity.windowManager.defaultDisplay
            .rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        var result: Int
        Log.d(
            TAG,
            " setCameraDisplayOrientation info.orientation ${info.orientation} degrees $degrees"
        )
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        Log.d(TAG, " setCameraDisplayOrientation result $result")
        camera.setDisplayOrientation(result)
    }

    /**
     * 查找前置摄像头
     */
    fun findFrontCamera(): Int {
        val numberOfCameras = Camera.getNumberOfCameras()
        val cameraInfo = CameraInfo()
        for (i in 0 until numberOfCameras) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                return i
            }
        }
        return -1
    }

    /**
     * 查找后置摄像头
     */
    fun findBackCamera(): Int {
        val numberOfCameras = Camera.getNumberOfCameras()
        val cameraInfo = CameraInfo()
        for (i in 0 until numberOfCameras) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                return i
            }
        }
        return -1
    }

    /**
     * 获取照片存储位置
     */
    private fun getOutputMediaFileUri(type: Int): Uri {
        return Uri.fromFile(getOutputMediaFile(type))
    }

    /**
     * 获取照片存储位置
     */
    private fun getOutputMediaFile(type: Int): File? {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "AVSample"
        )
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        mediaStorageDir.apply {
            if (!exists()) {
                if (!mkdirs()) {
                    Log.d("AVSample", "failed to create directory")
                    return null
                }
            }
        }

        // Create a media file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        return when (type) {
            MEDIA_TYPE_IMAGE -> {
                File("${mediaStorageDir.path}${File.separator}IMG_$timeStamp.jpg")
            }
            MEDIA_TYPE_VIDEO -> {
                File("${mediaStorageDir.path}${File.separator}VID_$timeStamp.mp4")
            }
            else -> null
        }
    }
}
