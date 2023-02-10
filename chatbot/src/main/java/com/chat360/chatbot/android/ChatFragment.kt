package com.chat360.chatbot.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.chat360.chatbot.R
import com.chat360.chatbot.common.Chat360SnackBarHelper
import com.chat360.chatbot.common.Constants
import com.chat360.chatbot.common.models.ConfigService
import com.chat360.chatbot.common.utils.viewBinding
import com.chat360.chatbot.databinding.FragmentChatBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class ChatFragment : Fragment() {
    private val fragmentBinding by viewBinding(FragmentChatBinding::inflate)
    private fun <T : ViewBinding> initBinding(binding: T): View {
        return with(binding) {
            root
        }
    }

    private var requestedPermission: String? = null
    private var mCameraPhotoPath: String? = null

    private var mAudioPath: String? = null
    private var isMultiFileUpload = false
    private var shouldKeepApplicationInBackground = true
    private var mFilePathCallback: ValueCallback<Array<Uri?>>? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null

    private var isMediaUploadOptionSelected = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        (activity as AppCompatActivity?)?.supportActionBar?.hide()
        setCloseButtonColor()
        setStatusBarColor()
        if (!Constants.isNetworkAvailable(requireActivity())) {
            Constants.showNoInternetDialog(requireActivity())
        } else {
            setupViews()

        }
        return initBinding(fragmentBinding)
    }

    //Internet Dialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(initBinding(fragmentBinding), savedInstanceState)
        Constants.UNREAD_MESSAGE_COUNT = 0
        showCloseButton()
        setStatusBarColorFromHex()
        setCloseButtonColorFromHex()
    }

    private fun showCloseButton() {
        val showCloseButton = ConfigService.getInstance()?.getConfig()?.showCloseButton
        if (showCloseButton!!) {
            fragmentBinding.imageViewClose.visibility = View.VISIBLE
            setCloseButtonColor()
        } else {
            fragmentBinding.imageViewClose.visibility = View.GONE
        }
    }

    private fun setupViews() {
        val fileName = "chat360_cache.mht"

        fragmentBinding.apply {
            //Initializing WebView Client
            webView.webViewClient = object : WebViewClient() {

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    webView.saveWebArchive(fileName)
                }

                override fun shouldInterceptRequest(
                    view: WebView?, request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (request?.url.toString().contains(fileName)) {
                        val file = File(requireActivity().cacheDir, fileName)
                        if (file.exists()) {
                            return WebResourceResponse("text/html", "UTF-8", file.inputStream())
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            webView.settings.apply {
                domStorageEnabled = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                javaScriptEnabled = true
                databaseEnabled = true
                setSupportZoom(true)
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            }

            webChromeClient()
            val file = File(requireActivity().cacheDir, fileName)
            if (file.exists()) {
                webView.loadUrl("file://" + requireActivity().cacheDir.absolutePath + "/" + fileName)
            } else {
                //Starting the WebView by loading the URL with bot ID and store_session is to store Cache
                val botId = ConfigService.getInstance()?.getConfig()?.botId
                val chat360BaseUrl = requireContext().resources.getString(R.string.chat360_base_url)
                val fcmToken = ConfigService.getInstance()?.getConfig()?.deviceToken
                val appId = requireContext().applicationContext.packageName
                val url = "$chat360BaseUrl$botId&store_session=1&fcm_token=$fcmToken&app_id=$appId"
                webView.loadUrl("$chat360BaseUrl$botId&store_session=1&fcm_token=$fcmToken&app_id=$appId")
                Log.d("chatbot_url", url)
            }
            imageViewClose.setOnClickListener {
                requireActivity().onBackPressed()
            }

        }
    }

    //Adding callbacks and the permission function for web-view requirements
    private fun webChromeClient() {
        fragmentBinding.webView.webChromeClient = object : WebChromeClient() {
            private var mCustomView: View? = null
            private var mCustomViewCallback: CustomViewCallback? = null
            private var mOriginalOrientation = 0
            private var mOriginalSystemUiVisibility = 0
            private var mProgress: ProgressDialog? = null

            override fun onProgressChanged(view: WebView?, progress: Int) {
                if (mProgress == null) {
                    mProgress = ProgressDialog(activity)
                    mProgress?.show()
                }
                mProgress?.setMessage("Loading $progress%")
                if (progress == 100) {
                    mProgress?.dismiss()
                    mProgress = null
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                return true
            }

            private var isMultimediaConsole = true
            private var isMediaUploadOptionSelected = false

            // For Android 5.0
            override fun onShowFileChooser(
                view: WebView,
                filePath: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                // Double check that we don't have any existing callbacks
                if (mFilePathCallback != null) {
                    mFilePathCallback!!.onReceiveValue(null)
                }
                mFilePathCallback = filePath as ValueCallback<Array<Uri?>>?
                isMediaUploadOptionSelected = false
                showBottomSheet()  //Opening the bottomsheet to select if you wan't to use camera or files
                return true
            }

            override fun getDefaultVideoPoster(): Bitmap? {
                return if (mCustomView == null) {
                    null
                } else BitmapFactory.decodeResource(context?.resources, 2130837573)
            }

            override fun onHideCustomView() {
                if (activity != null) {
                    (activity!!.window.decorView as FrameLayout).removeView(mCustomView)
                    if (activity != null) {
                        activity!!.window.decorView.systemUiVisibility = mOriginalSystemUiVisibility
                    }
                    activity!!.requestedOrientation = mOriginalOrientation
                }
                mCustomView = null
                mCustomViewCallback!!.onCustomViewHidden()
                mCustomViewCallback = null
            }

            override fun onShowCustomView(
                paramView: View, paramCustomViewCallback: CustomViewCallback
            ) {
                if (mCustomView != null) {
                    onHideCustomView()
                    return
                }
                mCustomView = paramView
                if (activity != null) {
                    mOriginalSystemUiVisibility = activity!!.window.decorView.systemUiVisibility
                    mOriginalOrientation = activity!!.requestedOrientation
                }
                mCustomViewCallback = paramCustomViewCallback
                if (activity != null) {
                    (activity!!.window.decorView as FrameLayout).addView(
                        mCustomView, FrameLayout.LayoutParams(-1, -1)
                    )
                    activity!!.window.decorView.systemUiVisibility =
                        3846 or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                }
            }

            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
            ): Boolean {
                val newWebView = context?.let { WebView(it) }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()
                if (newWebView != null) {
                    newWebView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            val browserIntent = Intent(Intent.ACTION_VIEW)
                            browserIntent.data = Uri.parse(url)
                            startActivity(browserIntent)
                            return true
                        }
                    }
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {

                for (permission in request.resources) {
                    when (permission) {
                        "android.webkit.resource.AUDIO_CAPTURE" -> {
                            if (checkSelfPermission(
                                    requireContext(), Manifest.permission.RECORD_AUDIO
                                ) != PermissionChecker.PERMISSION_GRANTED
                            ) {
                                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                            } else {
                                request.grant(request.resources)
                                //checkAndLaunchAudioRecord()
                            }
                        }
                    }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String, callback: GeolocationPermissions.Callback
            ) {
                if (context == null) return
                if (!hasLocationPermissionInManifest(requireContext())) {
                    Chat360SnackBarHelper().showMessageInSnackBar(
                        requireView(), "No location permission in manifest"
                    )
                    return
                }
                if (checkForLocationPermission(requireContext())) {
                    callback.invoke(origin, true, false)
                } else {
                    geoOrigin = origin
                    geoCallback = callback
                }
            }
        }
    }

    private fun hasAudioPermissionInManifest(context: Context): Boolean {
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_PERMISSIONS
            )
            val permissions = packageInfo.requestedPermissions
            if (permissions == null || permissions.isEmpty()) {
                return false
            }
            for (perm in permissions) {
                if (perm == Manifest.permission.RECORD_AUDIO) return true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            //Exception occurred
            return false
        }
        return false
    }

    private fun hasLocationPermissionInManifest(context: Context): Boolean {
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_PERMISSIONS
            )
            val permissions = packageInfo.requestedPermissions
            if (permissions == null || permissions.isEmpty()) {
                return false
            }
            for (perm in permissions) {
                if (perm == Manifest.permission.ACCESS_FINE_LOCATION) return true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            //Exception occurred
            return false
        }
        return false
    }

    private fun checkForLocationPermission(context: Context): Boolean {
        return if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            requestedPermission = Manifest.permission.ACCESS_FINE_LOCATION
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            false
        }
    }

    //Setting the statusBar Color from the resources
    private fun setStatusBarColor() {
        try {
            val color = ConfigService.getInstance()?.getConfig()?.statusBarColor
            if (color != -1) {
                val window: Window = requireActivity().window
                // clear FLAG_TRANSLUCENT_STATUS flag:
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                // add FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS flag to the window
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                // finally change the color
                context?.let {
                    window.statusBarColor = ContextCompat.getColor(it, color!!)
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e("StatusBarException", e.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        fragmentBinding.webView.reload()
    }

    //Setting the statusBarColor froom hexadecmal Code
    private fun setStatusBarColorFromHex() {
        try {
            val color = ConfigService.getInstance()?.getConfig()?.statusBarColorFromHex
            if (color != null && color.isNotEmpty() && activity != null) {
                val window: Window = requireActivity().window
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = Color.parseColor(color)
            }
        } catch (e: java.lang.Exception) {
            Log.e("StatusBarException", e.toString())
        }
    }

    //Setting the Close Button Color from the resources
    private fun setCloseButtonColor() {
        try {
            val color = ConfigService.getInstance()?.getConfig()?.closeButtonColor
            if (color != -1 && context != null) {
                DrawableCompat.setTint(
                    DrawableCompat.wrap(fragmentBinding.imageViewClose.drawable),
                    ContextCompat.getColor(requireContext(), color!!)
                )
            }
        } catch (e: java.lang.Exception) {
            Log.e("CloseButtonException", e.toString())
        }
    }

    //Setting the Close Button Color Code From Hex Color Code
    private fun setCloseButtonColorFromHex() {
        try {
            val color = ConfigService.getInstance()?.getConfig()?.closeButtonColorFromHex
            if (color != null && color.isNotEmpty()) {
                DrawableCompat.setTint(
                    DrawableCompat.wrap(
                        fragmentBinding.imageViewClose.drawable
                    ), Color.parseColor(color)
                )
            }
        } catch (e: java.lang.Exception) {
            Log.e("CloseButtonException", e.toString())
        }
    }

    //while uploading the file it'll show the bottom-sheet layout
    private fun showBottomSheet() {
        if (context != null) {
            val bottomSheetDialog = BottomSheetDialog(requireContext())
            bottomSheetDialog.setContentView(R.layout.fragment_upload_bottom_sheet)
            val cameraLayout = bottomSheetDialog.findViewById<ImageView>(R.id.imageViewCamera)
            val fileLayout = bottomSheetDialog.findViewById<ImageView>(R.id.imageViewfile)
            cameraLayout?.setOnClickListener {
                isMediaUploadOptionSelected = true
                checkAndLaunchCamera()
                bottomSheetDialog.dismiss()
            }
            fileLayout?.setOnClickListener {
                isMediaUploadOptionSelected = true
                checkAndLaunchFilePicker()
                bottomSheetDialog.dismiss()
            }
            bottomSheetDialog.setOnDismissListener {
                if (!isMediaUploadOptionSelected) {
                    resetFilePathCallback()
                }
            }
            bottomSheetDialog.show()
        }
    }

    //Checking if User Has Given the Storage Permission
    private fun checkForStoragePermission(context: Context): Boolean {
        return if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            requestedPermission = Manifest.permission.READ_EXTERNAL_STORAGE
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            false
        }
    }

    //Checking the permission is true and launching the file intent to choose the file from storage
    private fun checkAndLaunchFilePicker() {
        if (context != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                launchFileIntent()
            } else {
                if (checkForStoragePermission(requireContext())) {
                    launchFileIntent()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createAudioFile(): File? {

        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "Chat360WAV_" + timeStamp + "_"
        val storageDir = requireContext().externalCacheDir
        return File.createTempFile(
            imageFileName, ".wav", storageDir
        )
    }

    //Will create the image_file
    @Throws(IOException::class)
    private fun createImageFile(): File? {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "Chat360JPEG_" + timeStamp + "_"
        val storageDir = requireContext().externalCacheDir
        return File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",  /* suffix */
            storageDir /* directory */
        )
    }

    private fun launchAudioIntent() {
        val takeAudioIntent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        if (activity != null && takeAudioIntent.resolveActivity(requireActivity().packageManager) != null) {
            // Create the File where the photo should go
            var audioFile: File? = null
            try {
                audioFile = createAudioFile()
                takeAudioIntent.putExtra("AudioPaths", mAudioPath)
            } catch (ex: IOException) {
                //IO exception occurred
            }
            // Continue only if the File was successfully created
            if (audioFile != null) {
                mAudioPath = "file:" + audioFile.absolutePath
                val audioURI: Uri = if (context != null) {
                    FileProvider.getUriForFile(
                        requireContext(), getString(R.string.chat360_file_provider), audioFile
                    )
                } else {
                    Uri.fromFile(audioFile)
                }
                takeAudioIntent.putExtra(MediaStore.EXTRA_OUTPUT, audioURI)
                disableShouldKeepApplicationInBackground()
                startAudioActivity.launch(
                    takeAudioIntent
                )
            }
        }
    }

    private val startAudioActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                // do whatever with the data in the callback
                val data = it.data
                var results: Array<Uri?>? = null
                // Check that the response is a good one
                if (data != null && data.dataString != null) {
                    val dataString = data.dataString
                    results = arrayOf(Uri.parse(dataString))
                } else if (data != null && data.clipData != null) {
                    val count = data.clipData!!.itemCount
                    if (count > 0) {
                        results = arrayOfNulls(count)
                        for (i in 0 until count) {
                            results[i] = data.clipData!!.getItemAt(i).uri
                        }
                    }
                } else {
                    // If there is no data, then we may have taken a photo
                    if (mAudioPath != null) {
                        results = arrayOf(Uri.parse(mAudioPath))
                    }
                }
                mFilePathCallback!!.onReceiveValue(results)
                mFilePathCallback = null
            }
        }

    private fun checkForAudioPermission(context: Context): Boolean {
        return if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            requestedPermission = Manifest.permission.RECORD_AUDIO
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            false
        }
    }

    private fun checkAndLaunchAudioRecord() {
        if (context != null) {
            if (hasAudioPermissionInManifest(requireContext())) {
                if (checkForAudioPermission(requireContext())) {
                    launchAudioIntent()
                }
            } else {
                launchAudioIntent()
            }
        }
    }

    //Will Start Camera
    private fun launchCameraIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (activity != null && takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
            // Create the File where the photo should go
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
                takePictureIntent.putExtra("PhotoPaths", mCameraPhotoPath)
            } catch (ex: IOException) {
                //IO exception occurred
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                mCameraPhotoPath = "file:" + photoFile.absolutePath
                val photoURI: Uri = if (context != null) {
                    FileProvider.getUriForFile(
                        requireContext(), getString(R.string.chat360_file_provider), photoFile
                    )
                } else {
                    Uri.fromFile(photoFile)
                }
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                disableShouldKeepApplicationInBackground()
                startCameraActivity.launch(
                    takePictureIntent
                )
            } else {
                Chat360SnackBarHelper().showMessageInSnackBar(
                    requireView(), "Not able to launch camera please use file option to pick image"
                )
            }
        }
    }

    //result variable for the external intent of chat-bot
    private val startCameraActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                // do whatever with the data in the callback
                val data = it.data
                var results: Array<Uri?>? = null
                // Check that the response is a good one
                if (data != null && data.dataString != null) {
                    val dataString = data.dataString
                    results = arrayOf(Uri.parse(dataString))
                } else if (data != null && data.clipData != null) {
                    val count = data.clipData!!.itemCount
                    if (count > 0) {
                        results = arrayOfNulls(count)
                        for (i in 0 until count) {
                            results[i] = data.clipData!!.getItemAt(i).uri
                        }
                    }
                } else {
                    // If there is no data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = arrayOf(Uri.parse(mCameraPhotoPath))
                    }
                }

                mFilePathCallback!!.onReceiveValue(results)
                mFilePathCallback = null
            }
        }

    //Will verify if using camera is allowed by user
    private fun checkForCameraPermission(context: Context): Boolean {
        return if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            requestedPermission = Manifest.permission.CAMERA
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            false
        }
    }

    //If permission is given camera will be started
    private fun checkAndLaunchCamera() {
        if (context != null) {
            if (hasCameraPermissionInManifest(requireContext())) {
                if (checkForCameraPermission(requireContext())) {
                    launchCameraIntent()
                }
            } else {
                launchCameraIntent()
            }
        }
    }

    //Checking if Permissions are added in AndroidManifest File
    private fun hasCameraPermissionInManifest(context: Context): Boolean {
        val packageInfo: PackageInfo?
        try {
            packageInfo = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_PERMISSIONS
            )
            val permissions = packageInfo.requestedPermissions
            if (permissions == null || permissions.isEmpty()) {
                return false
            }
            for (perm in permissions) {
                if (perm == Manifest.permission.CAMERA) return true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            //Exception occurred
            return false
        }
        return false
    }

    //Upload multiple files
    private fun isMultiFileUpload(): Boolean {
        return isMultiFileUpload
    }

    //Keeping application in background is stopped
    private fun disableShouldKeepApplicationInBackground() {
        shouldKeepApplicationInBackground = false
    }

    //Launching file intent to upload any file
    private fun launchFileIntent() {
        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
        contentSelectionIntent.type = "*/*"
        if (isMultiFileUpload()) {
            contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        if (activity != null) {
            disableShouldKeepApplicationInBackground()
            startCameraActivity.launch(
                contentSelectionIntent
            )
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (requestedPermission == Manifest.permission.READ_EXTERNAL_STORAGE) {
            if (isGranted) {
                launchFileIntent()
            } else {
                resetFilePathCallback()
                if (context != null) {
                    Chat360SnackBarHelper().showSnackBarWithSettingAction(
                        requireContext(),
                        requireView(),
                        "Read storage permission required to complete this operation. Please enable it from app settings."
                    )
                }
            }
        } else if (requestedPermission == Manifest.permission.CAMERA) {
            if (isGranted) {
                launchCameraIntent()
            } else {
                resetFilePathCallback()
                if (context != null) {
                    Chat360SnackBarHelper().showSnackBarWithSettingAction(
                        requireContext(),
                        requireView(),
                        "Camera permission is required by the app to complete this operation. Please enable it from app settings."
                    )
                }
            }

        } else if (requestedPermission == Manifest.permission.ACCESS_FINE_LOCATION) {
            if (isGranted && geoCallback != null && geoOrigin != null) {
                geoCallback!!.invoke(geoOrigin, true, false)
                geoCallback = null
                geoOrigin = null
            } else {
                if (geoCallback != null && geoOrigin != null) {
                    geoCallback!!.invoke(geoOrigin, false, false)
                }
                geoCallback = null
                geoOrigin = null
                if (context != null) {
                    Chat360SnackBarHelper().showSnackBarWithSettingAction(
                        requireContext(),
                        requireView(),
                        "Location permission is required to complete this operation."
                    )
                }
            }
        }
    }

    private fun resetFilePathCallback() {
        if (mFilePathCallback != null) {
            mFilePathCallback!!.onReceiveValue(null)
            mFilePathCallback = null
        }
    }

    //For Caches
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        fragmentBinding.webView.saveState(outState)
    }

    //For Caches
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            fragmentBinding.webView.restoreState(savedInstanceState)
        }
    }

    companion object {
        fun newInstance(): ChatFragment {
            return ChatFragment()
        }
    }
}