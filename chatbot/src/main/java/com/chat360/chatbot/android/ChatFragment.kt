package com.chat360.chatbot.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import com.chat360.chatbot.R
import com.chat360.chatbot.common.Chat360SnackBarHelper
import com.chat360.chatbot.common.Constants
import com.chat360.chatbot.common.models.ConfigService
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.IOException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*


class ChatFragment : Fragment() {

    private var requestedPermission: String? = null
    private var mCameraPhotoPath: String? = null
    private var mCameraVideoPath: String? = null
    private var mAudioPath: String? = null
    private var isMultiFileUpload = false
    private var shouldKeepApplicationInBackground = true
    private var mFilePathCallback: ValueCallback<Array<Uri?>>? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null
    private lateinit var webView: WebView
    private lateinit var imageViewClos: ImageView
    private lateinit var topLayout: FrameLayout
    private var isMediaUploadOptionSelected = false
    var url = ""


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        (activity as AppCompatActivity?)?.supportActionBar?.hide()
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Constants.UNREAD_MESSAGE_COUNT = 0
        webView = view.findViewById(R.id.webView)
        imageViewClos = view.findViewById(R.id.imageViewClose)
        topLayout = view.findViewById(R.id.topLayout)

        setCloseButtonColor()
        setAppBarColor()
        setStatusBarColor()
        if (!Constants.isNetworkAvailable(requireActivity())) {
            Constants.showNoInternetDialog(requireActivity())
        } else {
            setupViews()
        }
        showCloseButton()
        setAppBarColorFromHex()
        setStatusBarColorFromHex()
        setCloseButtonColorFromHex()
        webView.clearCache(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView.destroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
        webView.clearHistory()
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun showCloseButton() {
        val showCloseButton = activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.showCloseButton }
        if (showCloseButton!!) {
            imageViewClos.visibility = View.VISIBLE
            setCloseButtonColor()
        } else {
            imageViewClos.visibility = View.GONE
        }
    }

    private fun setupViews() {
        val botId =activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.botId }
        val flutterBool = activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.flutter }
        val meta = activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.meta }
        val inputString= meta.toString()
        val jsonObject = JSONObject()

        try {
            val jsonContent = inputString.substring(1, inputString.length - 1)
            val keyValuePairs = jsonContent.split(", ")
            for (pair in keyValuePairs) {
                val (key, value) = pair.split("=")
                jsonObject.put(key, value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        var chat360BaseUrl = if(activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.isDebug } == true) {
            requireContext().resources.getString(R.string.chat360_staging_url)
        } else {
            requireContext().resources.getString(R.string.chat360_base_url)
        }

        if(activity?.let { ConfigService.getInstance(it.applicationContext)?.getBaseURL() } != null) {
            chat360BaseUrl =
                activity?.applicationContext?.let { ConfigService.getInstance(it)?.getBaseURL().toString() }
                    .toString()
        }

        val fcmToken = activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.deviceToken }
        val appId = "" //requireContext().applicationContext.packageName
        val devicemodel = Build.MODEL
        url = if (flutterBool == true) {
            "$chat360BaseUrl$botId&store_session=1&fcm_token=$fcmToken&app_id=$appId&is_mobile=true&meta=$jsonObject&flutter_sdk_type=android&mobile=1&device_name=$devicemodel"
        } else {
            "$chat360BaseUrl$botId&store_session=1&fcm_token=$fcmToken&app_id=$appId&is_mobile=true&meta=$jsonObject&mobile=1&device_name=$devicemodel"
        }

        webView.settings.apply {
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            setSupportZoom(true)
            //cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        }

        webChromeClient()
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.clearView()
        webView.measure(100, 100)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.loadUrl(url)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Handler(Looper.getMainLooper()).postDelayed({
                    injectJavascript(view)
                    injectJavascriptForCommunication(view)
                }, 100) // Adjust the delay as needed
            }
        }

        webView.addJavascriptInterface(JSBridge(requireActivity()), "Bridge")
        webView.addJavascriptInterface(WebCommunicationBridge(this), "WebCommunicationBridge")

        imageViewClos.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    class WebCommunicationBridge(private val activity: ChatFragment) {
        @JavascriptInterface
        fun postMessage(message: String) {
            Log.d("WebCommunicationBridge", "Message received from web: $message")

            val metadata =
                activity.activity?.let { ConfigService.getInstance(it.applicationContext)?.getMetadata() }

            activity.sendResponseToWeb(
                "CHAT360_WINDOW_EVENT",
                metadata
            )
        }
    }

    private fun injectJavascriptForCommunication(view: WebView?){
        val jsBridgeCode = """
            (function() {
                window.sendToApp = function(event) {
                    if (!event || !event.type) {
                        console.log('sendToApp requires event.type');
                        return;
                    }
                    if (window.WebCommunicationBridge && window.WebCommunicationBridge.postMessage) {
                        window.WebCommunicationBridge.postMessage(JSON.stringify(event));
                    } else {
                        console.log("WebCommunicationBridge not available:", event);
                    }
                };
        
                window.receiveFromApp = function(event) {
                    console.log("Received from app:", event);
                    if (window.onAppEvent) {
                        window.onAppEvent(event);
                    }
                };
            })();
        """.trimIndent()

        view?.evaluateJavascript(jsBridgeCode, null)
    }

    fun sendResponseToWeb(type: String, data: Map<String, String>?) {
        val payloadObject = JSONObject(data)
        val jsonPayload = JSONObject().apply {
            put("type", type)
            put("data", payloadObject)
        }.toString()

        val jsCode = "window.receiveFromApp(JSON.parse('$jsonPayload'));"

        webView.evaluateJavascript(jsCode) { result ->
            if (result == null || result == "null") {
                Log.d("NativeToWeb", "Sent event $type successfully.")
            } else {
                Log.e("NativeToWeb", "Error sending message: $result")
            }
        }
    }


    private fun injectJavascript(view: WebView?) {
        val webView = requireNotNull(view) { "WebView cannot be null" }

        webView.loadUrl(
            """
        javascript: (function(){
                let closeButton = document.querySelector(".cursor-pointer");
                if (closeButton) {
                    closeButton.addEventListener("click", function () {
                    console.log('Button clicked from JavaScript');
                        Bridge.calledFromJS();
                    });
                }
        })()
    """
        )
    }


    class JSBridge(private val activity: Activity) {
        @JavascriptInterface
        fun calledFromJS() {
            activity.onBackPressed()

        }
    }


    //Adding callbacks and the permission function for web-view requirements
    private fun webChromeClient() {
        webView.webChromeClient = object : WebChromeClient() {
            private var mCustomView: View? = null
            private var mCustomViewCallback: CustomViewCallback? = null
            private var mOriginalOrientation = 0
            private var mOriginalSystemUiVisibility = 0

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    "JavaScriptConsole",
                    "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                )
                return true
            }

            // For Android 5.0
            override fun onShowFileChooser(
                view: WebView,
                filePath: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                if (mFilePathCallback != null) {
                    mFilePathCallback!!.onReceiveValue(null)
                }
                mFilePathCallback = filePath as ValueCallback<Array<Uri?>>?
                isMediaUploadOptionSelected = false
                showBottomSheet()
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
                                request.grant(request.resources)
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
                        requireView(), "No location permission"
                    )
                    return
                }
                if (checkForLocationPermission(requireContext())) {
                    if (!isLocationEnabled(requireContext())) {
                        showGPSEnableDialog(requireContext())
                    }

                    callback.invoke(origin, true, false)

                } else {
                    geoOrigin = origin
                    geoCallback = callback
                }
            }
        }
    }


    private fun showGPSEnableDialog(context: Context) {
        AlertDialog.Builder(context)
            .setMessage("Please allow your location.")
            .setPositiveButton(
                "Allow"
            ) { _, _ ->
                context.startActivity(
                    Intent(
                        Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    )
                )
            }
            .setNegativeButton("Cancel") { _, _ ->
                Chat360SnackBarHelper().showMessageInSnackBar(
                    requireView(), "No location permission"
                )
            }
            .show()
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

    fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkForLocationPermission(context: Context): Boolean {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return if (hasPermissions(context, *PERMISSIONS)
        ) {
            true
        } else {
            requestedPermission = Manifest.permission.ACCESS_FINE_LOCATION
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)

            requestedPermission = Manifest.permission.ACCESS_COARSE_LOCATION
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            false
        }
    }

    fun isLocationEnabled(context: Context): Boolean {
        var locationMode = 0
        val locationProviders: String
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            locationMode = try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE)
            } catch (e: SettingNotFoundException) {
                e.printStackTrace()
                return false
            }
            locationMode != Settings.Secure.LOCATION_MODE_OFF
        } else {
            locationProviders = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED
            )
            !TextUtils.isEmpty(locationProviders)
        }
    }

    private fun setAppBarColor() {
        try {
            val color = activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.statusBarColor }
            if (color != -1) {
                val window: Window = requireActivity().window
                // clear FLAG_TRANSLUCENT_STATUS flag:
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                // add FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS flag to the window
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                // finally change the color
                context?.let {


                    val frameLayout: FrameLayout? = view?.findViewById(R.id.topLayout)

                    // Set the background color programmatically
                    frameLayout?.setBackgroundColor(ContextCompat.getColor(it, color!!))
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e("StatusBarException", e.toString())
        }
    }

    //Setting the statusBar Color from the resources
    private fun setStatusBarColor() {
        try {
            val color = context?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.statusBarColor }
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

    private fun setStatusBarColorFromHex() {
        try {
            val color = activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.statusBarColorFromHex }
            if (!color.isNullOrEmpty() && activity != null) {
                val window: Window = requireActivity().window
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = Color.parseColor(color)

            }
        } catch (e: java.lang.Exception) {
            Log.e("StatusBarException", e.toString())
        }
    }

    private fun setAppBarColorFromHex() {
        try {
            val color = activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.statusBarColorFromHex }
            if (!color.isNullOrEmpty() && activity != null) {
                val window: Window = requireActivity().window
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                activity?.findViewById<FrameLayout?>(R.id.topLayout)
                    ?.setBackgroundColor(Color.parseColor(color))

            }
        } catch (e: java.lang.Exception) {
            Log.e("StatusBarException", e.toString())
        }
    }

    //Setting the Close Button Color from the resources
    private fun setCloseButtonColor() {
        try {
            val color = activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.closeButtonColor }
            if (color != -1 && context != null) {
                DrawableCompat.setTint(
                    DrawableCompat.wrap(imageViewClos.drawable),
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
            val color = activity?.let { ConfigService.getInstance(it.applicationContext)?.getConfig()?.closeButtonColorFromHex }
            if (!color.isNullOrEmpty()) {
                DrawableCompat.setTint(
                    DrawableCompat.wrap(
                        imageViewClos.drawable
                    ), Color.parseColor(color)
                )
            }
        } catch (e: java.lang.Exception) {
            Log.e("CloseButtonException", e.toString())
        }
    }

    private fun showCameraOptionsDialog() {
        val options = arrayOf("Take Photo", "Record Video")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Choose an option")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {isMediaUploadOptionSelected = true
                    checkAndLaunchCamera()
                    dialog.dismiss()
                }
                1 -> {isMediaUploadOptionSelected = true
                    checkAndLaunchVideoCamera()
                dialog.dismiss()}
            }
        }
        builder.setOnDismissListener {
            if (!isMediaUploadOptionSelected) {
                resetFilePathCallback()
            }
        }

        builder.show()
    }

    //while uploading the file it'll show the bottom-sheet layout
    private fun showBottomSheet() {
        if (context != null) {
            val bottomSheetDialog = BottomSheetDialog(requireContext())
            bottomSheetDialog.setContentView(R.layout.fragment_upload_bottom_sheet)
            val cameraLayout = bottomSheetDialog.findViewById<ImageView>(R.id.imageViewCamera)
            val videoLayout = bottomSheetDialog.findViewById<ImageView>(R.id.imageViewVideo)
            val fileLayout = bottomSheetDialog.findViewById<ImageView>(R.id.imageViewfile)
            cameraLayout?.setOnClickListener {

                isMediaUploadOptionSelected = true
                checkAndLaunchCamera()
                bottomSheetDialog.dismiss()

            }
            videoLayout?.setOnClickListener{

                isMediaUploadOptionSelected = true
                checkAndLaunchVideoCamera()
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

    @Throws(IOException::class)
    private fun createVideoFile(): File? {
        // Create a video file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoFileName = "Chat360Video_" + timeStamp + "_"
        val storageDir = requireContext().externalCacheDir
        return File.createTempFile(
            videoFileName,  /* prefix */
            ".mp4",  /* suffix */
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
                        mAudioPath= null;
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
                isMediaUploadOptionSelected = false
            }
        }
    }

    private fun launchCameraVideoIntent() {
        val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)

        if (activity != null && takeVideoIntent.resolveActivity(requireActivity().packageManager) != null) {
            // Create the File where the video should go
            var videoFile: File? = null
            try {

                videoFile = createVideoFile()
            } catch (ex: IOException) {
                // Handle IOException if necessary
            }

            // Continue only if the File was successfully created
            if (videoFile != null) {
                mCameraVideoPath = "file:" + videoFile.absolutePath
                val videoURI: Uri = if (context != null) {
                    FileProvider.getUriForFile(
                        requireContext(), getString(R.string.chat360_file_provider), videoFile
                    )
                } else {
                    Uri.fromFile(videoFile)
                }
                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoURI)
                takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0.7) // 0 = lowest quality
                takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 10 * 1024 * 1024)
                disableShouldKeepApplicationInBackground()
                startVideoActivity.launch(takeVideoIntent)
            } else {
                Chat360SnackBarHelper().showMessageInSnackBar(
                    requireView(), "Not able to capture video. Please try again."
                )
            }
        }
    }


    private val startVideoActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Handle the video capture result here
                val data = result.data
                var results: Array<Uri?>? = null
                // Check that the response is a good one
                if (data != null && data.dataString != null) {
                    val dataString = data.dataString
                    results = arrayOf(Uri.parse(dataString))
                } else {
                    // If there is no data, then we may have captured a video
                    if (mCameraVideoPath != null) {
                        results = arrayOf(Uri.parse(mCameraVideoPath))
                        mCameraVideoPath = null;
                    }
                }
                mFilePathCallback!!.onReceiveValue(results)
                mFilePathCallback = null
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                isMediaUploadOptionSelected = false
                mFilePathCallback!!.onReceiveValue(null)
                mFilePathCallback = null
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
                        mCameraPhotoPath = null;
                    }
                }

                mFilePathCallback!!.onReceiveValue(results)
                mFilePathCallback = null
            } else if (it.resultCode == Activity.RESULT_CANCELED) {
                isMediaUploadOptionSelected = false
                mFilePathCallback!!.onReceiveValue(null)
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

    private fun checkAndLaunchVideoCamera() {

        if (context != null) {
            if (hasCameraPermissionInManifest(requireContext())) {
                if (checkForCameraPermission(requireContext())) {
                    launchCameraVideoIntent()
                }
            } else {
                launchCameraVideoIntent()
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

        } else if (requestedPermission == Manifest.permission.ACCESS_FINE_LOCATION || requestedPermission == Manifest.permission.ACCESS_COARSE_LOCATION) {
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
}