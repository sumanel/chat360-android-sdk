package com.chat360.chatbot.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
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

    private var mAudioPath: String? = null
    private var isMultiFileUpload = false
    private var shouldKeepApplicationInBackground = true
    private var mFilePathCallback: ValueCallback<Array<Uri?>>? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null
    private lateinit var webView: WebView
    private lateinit var imageViewClos: ImageView
    private lateinit var imageViewRefresh: ImageView
    private lateinit var topLayout: FrameLayout

    private var isPreviewShown: Boolean = false

    private var isMediaUploadOptionSelected = false
    var myBooleanState: Boolean? = null
    var url = ""


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        Log.d("chat-bot_oncreateView", "=================")
        (activity as AppCompatActivity?)?.supportActionBar?.hide()
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        return view
    }

    //Internet Dialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("chat-bot_view_created", "=================")
        Constants.UNREAD_MESSAGE_COUNT = 0
        webView = view.findViewById(R.id.webView)
        imageViewClos = view.findViewById(R.id.imageViewClose)
        imageViewRefresh = view.findViewById(R.id.imageViewRefresh)
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
        // Destroy the WebView and clean up resources
        webView.destroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("chat-bot_created", "=================")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("chat-bot_destroyed", "=================")
        webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
        webView.clearHistory()

        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun showCloseButton() {
        val showCloseButton = ConfigService.getInstance()?.getConfig()?.showCloseButton

        Log.d("chat-bot configservice3", "==============")
        if (showCloseButton!!) {
            imageViewClos.visibility = View.VISIBLE
            setCloseButtonColor()
        } else {
            imageViewClos.visibility = View.GONE
        }
    }

    private fun setupViews() {
        val botId = ConfigService.getInstance()?.getConfig()?.botId
        val flutterBool = ConfigService.getInstance()?.getConfig()?.flutter
        val meta = ConfigService.getInstance()?.getConfig()?.meta
        val inputString= meta.toString()
        val jsonObject = JSONObject()

        try {
            // Remove the curly braces at the beginning and end of the string
            val jsonContent = inputString.substring(1, inputString.length - 1)

            // Split the string into key-value pairs
            val keyValuePairs = jsonContent.split(", ")

            // Create a new JSONObject

            // Parse and add key-value pairs to the JSONObject
            for (pair in keyValuePairs) {
                val (key, value) = pair.split("=")
                jsonObject.put(key, value)
            }

            // Now you have your JSONObject
            println(jsonObject.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }


        Log.d("chat-bot configservice1", "=============="+meta)

        Log.d("chat-bot configservice1", "==============")
        val chat360BaseUrl = if(ConfigService.getInstance()?.getConfig()?.isDebug == true) {
            requireContext().resources.getString(R.string.chat360_staging_url)
        } else {
            requireContext().resources.getString(R.string.chat360_base_url)
        }
        val fcmToken = ConfigService.getInstance()?.getConfig()?.deviceToken

        Log.d("chat-bot configservice2", "==============")
        val appId = requireContext().applicationContext.packageName
        val devicemodel = Build.MODEL
        url = if (flutterBool == true) {
            Log.d("flutterTest","working======")
            """$chat360BaseUrl$botId&store_session=1&fcm_token=$fcmToken&app_id=$appId&is_mobile=true&meta=$jsonObject&flutter_sdk_type=android&mobile=1&device_name=$devicemodel"""
        } else {
            """$chat360BaseUrl$botId&store_session=1&fcm_token=$fcmToken&app_id=$appId&is_mobile=true&meta=$jsonObject&mobile=1&device_name=$devicemodel"""
        }
        Log.d("chat-bot configservice1", "=============="+url)

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

        Log.d("chat-bot_url", url)
        webView.clearView()
        webView.measure(100, 100)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

        webView.loadUrl(url)

//        injectJavaScriptToHandleButtonClick()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Handler().postDelayed({
                    injectJavascript(view)
                }, 100) // Adjust the delay as needed
            }
        }

        webView.addJavascriptInterface(JSBridge(requireActivity()), "Bridge")


        imageViewClos.setOnClickListener {

            Log.d("chat-bot_imageViewClose", "Close")
            requireActivity().onBackPressed()
        }

        imageViewRefresh.setOnClickListener {
            setupViews()
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
            Log.d("closeWebView", "closeWebView: ")
            activity.onBackPressed()

        }
    }


//    private fun injectJavaScriptToHandleButtonClick(webView: WebView?) {
//        webView?.evaluateJavascript(
//            """
//            function handleButtonClick() {
//                var button = document.getElementById('chatbot-header-close-button');
//                if (button) {
//                    button.addEventListener('click', function() {
//                        // Do something when the button is clicked
//                        // For example:
//                        alert('Button clicked!');
//                    });
//                }
//            }
//            handleButtonClick();
//            """.trimIndent()
//        )
//        { result ->
//            // The resultCallback will not be used here, so you can ignore it
//        }
//    }


    //Adding callbacks and the permission function for web-view requirements
    private fun webChromeClient() {
        Log.d("chat-bot checking webView chromeclient", "==============")
        webView.webChromeClient = object : WebChromeClient() {

            private var mCustomView: View? = null
            private var mCustomViewCallback: CustomViewCallback? = null
            private var mOriginalOrientation = 0
            private var mOriginalSystemUiVisibility = 0
            private var mProgress: ProgressDialog? = null

//            override fun onProgressChanged(view: WebView?, progress: Int) {
//                if (mProgress == null) {
//                    mProgress = ProgressDialog(activity)
//                    mProgress?.show()
//                }
//                mProgress?.setMessage("Loading $progress%")
//                if (progress == 100) {
//                    mProgress?.dismiss()
//                    mProgress = null
//
//                    Log.d("chat-bot_progress", "========================================")
//                }
//            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    "JavaScriptConsole",
                    "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                )
                return true
            }

//           private var isMediaUploadOptionSelected = false

            // For Android 5.0
            override fun onShowFileChooser(
                view: WebView,
                filePath: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                Log.d("chat-bot opening media", isMediaUploadOptionSelected.toString())
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

                Log.d("chat-bot checking webView is causing customview hide", "==============")
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

                Log.d("chat-bot checking webView is customviewshow", "==============")
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
                Log.d("chat-bot checking webView is causing it or not1", "==============")
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
                            Log.d(
                                "chat-bot checking webView is causing it or not",
                                "=============="
                            )
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

//        webView.addJavascriptInterface(object : Any() {
//            @JavascriptInterface
//            fun handleButtonClick(buttonId: String) {
//                // This method will be called from JavaScript when a button is clicked
//                // You can handle the button click event here
//                if (buttonId == "closeWebViewButton") {
//                    closeWebView()
//                }
//            }
//        }, "AndroidInterface")
    }
//    private fun injectJavaScriptToHandleButtonClick() {
//        val javascript = """
//        // Inject JavaScript code here to handle button clicks
//        document.getElementById('chatbot-header-close-button').addEventListener('click', function() {
//            console.log('Button clicked from JavaScript');
//            AndroidInterface.handleButtonClick('closeWebViewButton');
//        });
//    """
//        webView.evaluateJavascript(javascript, null)
//    }


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
        val PERMISSION_ALL = 1
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
            val color = ConfigService.getInstance()?.getConfig()?.statusBarColor

            Log.d("chat-bot configservice3", "==============")
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
            val color = ConfigService.getInstance()?.getConfig()?.statusBarColor

            Log.d("chat-bot configservice3", "==============")
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

    //Setting the statusBarColor froom hexadecmal Code
    private fun setStatusBarColorFromHex() {
        try {
            val color = ConfigService.getInstance()?.getConfig()?.statusBarColorFromHex

            Log.d("chat-bot configservice4", "==============")
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

    private fun setAppBarColorFromHex() {
        try {
            val color = ConfigService.getInstance()?.getConfig()?.statusBarColorFromHex

            Log.d("chat-bot configservice4", "==============")
            if (color != null && color.isNotEmpty() && activity != null) {
                val window: Window = requireActivity().window
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)


                val frameLayout: FrameLayout? = activity?.findViewById(R.id.topLayout)

                if (frameLayout != null) {
                    frameLayout.setBackgroundColor(Color.parseColor(color))
                }

            }
        } catch (e: java.lang.Exception) {
            Log.e("StatusBarException", e.toString())
        }
    }

    //Setting the Close Button Color from the resources
    private fun setCloseButtonColor() {
        try {
            val color = ConfigService.getInstance()?.getConfig()?.closeButtonColor

            Log.d("chat-bot configservice5", "==============")
            if (color != -1 && context != null) {
                DrawableCompat.setTint(
                    DrawableCompat.wrap(imageViewClos.drawable),
                    ContextCompat.getColor(requireContext(), color!!)
                )
                DrawableCompat.setTint(
                    DrawableCompat.wrap(imageViewRefresh.drawable),
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
            if (!color.isNullOrEmpty()) {
                DrawableCompat.setTint(
                    DrawableCompat.wrap(
                        imageViewClos.drawable
                    ), Color.parseColor(color)
                )
                DrawableCompat.setTint(
                    DrawableCompat.wrap(
                        imageViewRefresh.drawable
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
            Log.d("chat-bot opening media-", isMediaUploadOptionSelected.toString())
            resetFilePathCallback()


        } }

        builder.show()
    }

//    private fun showCameraOptionsDialog() {
//        if (context != null) {
//            val bottomSheetDialog = BottomSheetDialog(requireContext())
//            bottomSheetDialog.setContentView(R.layout.fragment_upload_bottom_sheet)
//            val cameraLayout = bottomSheetDialog.findViewById<ImageView>(R.id.imageViewCamera)
//            val fileLayout = bottomSheetDialog.findViewById<ImageView>(R.id.imageViewfile)
//            cameraLayout?.setOnClickListener {
//                isMediaUploadOptionSelected = true
//                checkAndLaunchCamera()
//                bottomSheetDialog.dismiss()
//            }
//            fileLayout?.setOnClickListener {
//                isMediaUploadOptionSelected = true
//                checkAndLaunchVideoCamera()
//                bottomSheetDialog.dismiss()
//            }
//            bottomSheetDialog.setOnDismissListener {
//                Log.d("chat-bot opening media--", isMediaUploadOptionSelected.toString())
//
//                if (!isMediaUploadOptionSelected) {
//                    Log.d("chat-bot opening media-", isMediaUploadOptionSelected.toString())
//                    resetFilePathCallback()
//
//
//                }
//            }
//            bottomSheetDialog.show()
//        }
//    }

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
                Log.d("chat-bot opening media--", isMediaUploadOptionSelected.toString())

                if (!isMediaUploadOptionSelected) {
                    Log.d("chat-bot opening media-", isMediaUploadOptionSelected.toString())
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
        Log.d("chat-bot opening launch", isMediaUploadOptionSelected.toString())

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
                    if (mCameraPhotoPath != null) {
                        results = arrayOf(Uri.parse(mCameraPhotoPath))
                    }
                }

                mFilePathCallback!!.onReceiveValue(results)
                mFilePathCallback = null
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                var results: Array<Uri?>? = null

                isMediaUploadOptionSelected = false
                mFilePathCallback!!.onReceiveValue(results)
                mFilePathCallback = null
            }
        }


    //result variable for the external intent of chat-bot
    private val startCameraActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d("chat-bot opening start", isMediaUploadOptionSelected.toString())
            if (it.resultCode == Activity.RESULT_OK) {
                Log.d("chat-bot opening startif", isMediaUploadOptionSelected.toString())

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
            } else if (it.resultCode == Activity.RESULT_CANCELED) {
                var results: Array<Uri?>? = null

                isMediaUploadOptionSelected = false
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

    private fun checkAndLaunchVideoCamera() {

        if (context != null) {
            if (hasCameraPermissionInManifest(requireContext())) {
                if (checkForCameraPermission(requireContext())) {
                    launchCameraVideoIntent()
//                    launchCameraIntent()
                }
            } else {
                launchCameraVideoIntent()
//                launchCameraIntent()
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