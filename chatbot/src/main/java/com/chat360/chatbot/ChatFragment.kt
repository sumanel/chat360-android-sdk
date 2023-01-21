package com.chat360.chatbot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
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
    private var isMultiFileUpload = false
    private var shouldKeepApplicationInBackground = true
    private var mFilePathCallback: ValueCallback<Array<Uri?>>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        setupViews()
        return initBinding(fragmentBinding)
    }

    private fun setupViews() {
        val fileName = "chat360_cache.mht"

        fragmentBinding.apply {

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
                webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
            val file = File(requireActivity().cacheDir, fileName)
            if (file.exists()) {
                webView.loadUrl("file://" + requireActivity().cacheDir.absolutePath + "/" + fileName)
            }
            else {
                webView.loadUrl("https://app.gaadibaazar.in/page?h=1869e4dc-24e7-424d-b5a2-d871da890b14&store_session=1")
            }
        }

        webChromeClient()
    }

    private fun webChromeClient() {
        fragmentBinding.webView.webChromeClient = object : WebChromeClient() {
            private var mCustomView: View? = null
            private var mCustomViewCallback: CustomViewCallback? = null
            private var mOriginalOrientation = 0
            private var mOriginalSystemUiVisibility = 0
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {

                return true
            }

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
                showBottomSheet()
                return true
            }

            override fun getDefaultVideoPoster(): Bitmap? {
                return if (mCustomView == null) {
                    null
                }
                else BitmapFactory.decodeResource(context?.resources, 2130837573)
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

        }

    }

    private fun showBottomSheet() {
        if (context != null) {
            val bottomSheetDialog = BottomSheetDialog(requireContext())
            bottomSheetDialog.setContentView(R.layout.fragment_upload_bottom_sheet)
            val cameraLayout = bottomSheetDialog.findViewById<ImageView>(R.id.imageViewCamera)
            val fileLayout = bottomSheetDialog.findViewById<ImageView>(R.id.imageViewfile)
            cameraLayout?.setOnClickListener {
                checkAndLaunchCamera()
                bottomSheetDialog.dismiss()
            }
            fileLayout?.setOnClickListener {
                checkAndLaunchFilePicker()
                bottomSheetDialog.dismiss()
            }
            bottomSheetDialog.setOnDismissListener {
                resetFilePathCallback()
            }
            bottomSheetDialog.show()
        }
    }

    private fun checkForStoragePermission(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestedPermission = Manifest.permission.READ_EXTERNAL_STORAGE
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return false
        }
        return true
    }


    private fun checkAndLaunchFilePicker() {
        if (context != null) {
            if (checkForStoragePermission(requireContext())) {
                launchFileIntent()
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "Chat360JPEG_$timeStamp"
        val storageDir = requireContext().externalCacheDir
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    private fun launchCameraIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (activity != null && takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
                takePictureIntent.putExtra("PhotoPaths", mCameraPhotoPath)
            } catch (ex: IOException) {
                //IO exception occurred
            }
            if (photoFile != null) {
                mCameraPhotoPath = "file:" + photoFile.absolutePath
                val photoURI = FileProvider.getUriForFile(
                    requireContext(), getString(R.string.chat360_file_provider), photoFile
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                disableShouldKeepApplicationInBackground()
                startCameraActivity.launch(takePictureIntent)
            }
        }
    }

    private val startCameraActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val data = it.data
                var results: Array<Uri?>? = null
                when {
                    data != null && data.dataString != null -> results =
                        arrayOf(Uri.parse(data.dataString))
                    data != null && data.clipData != null -> {
                        val count = data.clipData!!.itemCount
                        if (count > 0) {
                            results = arrayOfNulls(count)
                            for (i in 0 until count) {
                                results[i] = data.clipData!!.getItemAt(i).uri
                            }
                        }
                    }
                    mCameraPhotoPath != null -> results = arrayOf(Uri.parse(mCameraPhotoPath))
                }

                mFilePathCallback?.onReceiveValue(results)
                mFilePathCallback = null
            }
        }


    private fun checkForCameraPermission(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestedPermission = Manifest.permission.CAMERA
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            return false
        }
        return true
    }

    private fun checkAndLaunchCamera() {
        if (context != null) {
            if (hasCameraPermissionInManifest(requireContext()) && checkForCameraPermission(
                    requireContext()
                )
            ) {
                launchCameraIntent()
            }
        }
    }

    private fun hasCameraPermissionInManifest(context: Context): Boolean {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS
        )
        return packageInfo.requestedPermissions?.contains(Manifest.permission.CAMERA) ?: false
    }

    private fun isMultiFileUpload(): Boolean {
        return isMultiFileUpload
    }

    private fun disableShouldKeepApplicationInBackground() {
        shouldKeepApplicationInBackground = false
    }

    private fun launchFileIntent() {
        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
        contentSelectionIntent.type = "*/*"
        if (isMultiFileUpload()) {
            contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        disableShouldKeepApplicationInBackground()
        startCameraActivity.launch(contentSelectionIntent)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            when (requestedPermission) {
                Manifest.permission.READ_EXTERNAL_STORAGE -> if (isGranted) launchFileIntent() else resetFilePathCallback()
                Manifest.permission.CAMERA -> if (isGranted) launchCameraIntent() else resetFilePathCallback()
            }
        }

    private fun resetFilePathCallback() {
        if (mFilePathCallback != null) {
            mFilePathCallback!!.onReceiveValue(null)
            mFilePathCallback = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        fragmentBinding.webView.saveState(outState)
    }

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