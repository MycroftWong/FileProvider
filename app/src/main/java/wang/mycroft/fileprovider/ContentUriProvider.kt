package wang.mycroft.fileprovider

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.internal.closeQuietly
import java.io.*

/**
 * 描述：TODO
 *
 * @author <a href="mailto:wangqiang@shoplex.com">WangQiang</a>
 * @date 2022-04-20
 */
object ContentUriProvider {

    private const val TAG = "ContentUriProvider"

    private const val HUAWEI_MANUFACTURER = "Huawei"

    fun getUriForFile(context: Context, authority: String, file: File): Uri {
        return if (HUAWEI_MANUFACTURER.equals(Build.MANUFACTURER, ignoreCase = true)) {
            Log.w(TAG, "Using a Huawei device Increased likelihood of failure...")
            try {
                FileProvider.getUriForFile(context, authority, file)
            } catch (e: IllegalArgumentException) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    Log.w(
                        TAG,
                        "Returning Uri.fromFile to avoid Huawei 'external-files-path' bug for pre-N devices",
                        e
                    )
                    Uri.fromFile(file)
                } else {
                    Log.w(
                        TAG,
                        "ANR Risk -- Copying the file the location cache to avoid Huawei 'external-files-path' bug for N+ devices",
                        e
                    )
                    // Note: Periodically clear this cache
                    val cacheFolder = File(context.cacheDir, HUAWEI_MANUFACTURER)
                    val cacheLocation = File(cacheFolder, file.name)
                    var inFile: InputStream? = null
                    var outFile: OutputStream? = null
                    try {
                        inFile = FileInputStream(file)
                        outFile = FileOutputStream(cacheLocation) // appending output stream
                        inFile.copyTo(outFile)
                        Log.i(
                            TAG,
                            "Completed Android N+ Huawei file copy. Attempting to return the cached file"
                        )
                        FileProvider.getUriForFile(context, authority, cacheLocation)
                    } catch (e1: IOException) {
                        Log.e(TAG, "Failed to copy the Huawei file. Re-throwing exception", e1)
                        throw IllegalArgumentException(
                            "Huawei devices are unsupported for Android N", e1
                        )
                    } finally {
                        inFile?.closeQuietly()
                        outFile?.closeQuietly()
                    }
                }
            }
        } else {
            FileProvider.getUriForFile(context, authority, file)
        }
    }
}