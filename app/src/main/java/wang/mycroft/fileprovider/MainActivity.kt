package wang.mycroft.fileprovider

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.*


/**
 * 使用FileProvider
 *
 * @author Mycroft Wong
 * @date 2019年9月6日
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * 拼多多apk下载地址
         */
        private const val URL_APK =
            "http://mcdn.yangkeduo.com/android_dev/2019-09-03/38d49ed27bdfc31247a1d92e7c67d7c2.apk"

        private val httpClient: OkHttpClient = OkHttpClient.Builder()
            .build()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadButton.setOnClickListener { downloadApk() }

        takeButton.setOnClickListener { takePhoto() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionList = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val needPermissionList = mutableListOf<String>()
            permissionList.filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }.forEach { needPermissionList.add(it) }

            if (needPermissionList.isNotEmpty()) {
                requestPermissions(needPermissionList.toTypedArray(), 2)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 2) {
            return
        }
        if (grantResults.any { it == PackageManager.PERMISSION_DENIED }) {
            Toast.makeText(this, "请先通过权限", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private var dialog: Dialog? = null

    private fun downloadApk() {
        if (dialog != null) {
            return
        }

        val request = Request.Builder()
            .url(URL_APK)
            .get()
            .build()

        val call = httpClient.newCall(request)

        dialog = AlertDialog.Builder(this)
            .setCancelable(false)
            .setMessage("正在下载中...")
            .show()
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "文件下载失败", Toast.LENGTH_SHORT).show()
                    dialog?.dismiss()
                    dialog = null
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "文件下载失败", Toast.LENGTH_SHORT).show()
                        dialog?.dismiss()
                        dialog = null
                    }
                    return
                }

                // 构造文件
                val file = File(File(getExternalFilesDir(null), "apk_file"), "pdd.apk")
                file.parentFile.mkdirs()

                // 构造流
                val sink = file.sink().buffer()
                val source = response.body!!.source()

                val bufferSize = 8 * 1024L

                while (!source.exhausted()) {
                    source.read(sink.buffer, bufferSize)
                    sink.emit()
                }

                // 关闭流
                sink.flush()
                source.close()
                sink.close()

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "文件下载成功，准备安装", Toast.LENGTH_SHORT).show()
                    dialog?.dismiss()
                    dialog = null
                    window?.decorView?.postDelayed({ installApk(file) }, 1500)
                }
            }
        })
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(this, "wang.mycroft.fileprovider.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        installIntent.data = uri
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val packageList = packageManager.queryIntentActivities(installIntent, 0)
        if (packageList.size > 0) {
            startActivity(installIntent)
        }
    }

    private var tempPhotoUri: Uri? = null

    private var tempFile: File? = null

    private fun takePhoto() {
        tempFile = File(File(filesDir, "image_file"), "${UUID.randomUUID()}.jpg")
        if (!tempFile?.parentFile?.exists()!! && !tempFile?.parentFile?.mkdirs()!!) {
            return
        }
        tempPhotoUri =
            FileProvider.getUriForFile(this, "wang.mycroft.fileprovider.fileprovider", tempFile!!)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, tempPhotoUri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val packageList = packageManager.queryIntentActivities(intent, 0)
        if (packageList.size > 0) {
            startActivityForResult(intent, 1)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1) {
            revokeUriPermission(tempPhotoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            if (resultCode == Activity.RESULT_OK) {
//                val option = BitmapFactory.Options()
//                option.inSampleSize = 4
//                image.setImageBitmap(BitmapFactory.decodeFile(tempFile?.absolutePath, option))
//
                compressImageUri(tempPhotoUri!!)
            }
        }
    }

    private fun compressImageUri(imageUri: Uri) {
        contentResolver.openInputStream(imageUri)?.let {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true

            BitmapFactory.decodeStream(it, null, options)
            Log.e("mycroft", "<${options.outWidth}, ${options.outHeight}>")

            options.inJustDecodeBounds = false

            val screenWidth = getScreenWidth()
            if (screenWidth != -1) {
                options.inSampleSize = options.outWidth / screenWidth
            }

            val bitmap =
                BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri), null, options)
            bitmap.let {
                image.setImageBitmap(bitmap)
            }
        }
    }

    private fun getScreenWidth(): Int {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        return point.x
    }
}
