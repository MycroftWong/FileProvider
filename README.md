# FileProvider

## 前言

`Android`开发始终脱离不了图片处理，特别是`Android 7.0`开始，无法通过`file:///`的`URI`来进行在应用之间共享文件，取而代之的是`content uri`。这样必然增加了开发难度，如必须生成`content uri`，赋予访问权限，同时暂时没有找到能够通过`content uri`获取文件大小的方法。同时一些特殊需要，如安装`apk`、调用相机拍照都需要改成`content uri`，而无法再直接通过`Uri.fromFile(File)`获取的`URI`共享文件。所以认真研究`FileProvider`是必要的。

下面着重通过官方文档介绍`FileProvider`的使用：[FileProvider](https://developer.android.google.cn/reference/android/support/v4/content/FileProvider)

注：本文所用代码在[FileProvider](https://github.com/MycroftWong/FileProvider)中，可进行查阅。

## 翻译

这里就不在引用原文了，下面是翻译的内容

`FileProvider`是`ContentProvider`的一个特殊子类，为了加强在应用之间安全的分享文件。通过创建`content://`的`uri`替换`file:///`的`uri`。

`content://`的`uri`允许授予临时的读写权限。当我们创建一个`Intent`，其中包含一个`content uri`，为了能够让对方的应用能够访问到这个`content uri`，可以使用`Intent.setFlags()`添加权限。如果收到`content uri`的是`Activity`，那么这些权限一直会存在，只要收到`content uri`的`Activity`栈处于活动状态。如果收到`content uri`的是`Service`，那么`Service`一直运行，权限就会一直在。

相对于`file:///`的`uri`，你想控制文件的控制权限，那么你必须修改系统的底层文件权限。你提供的这些权限将对所有的应用有效，一直保留到你更改他们。这种权限控制根本上是不安全的。

提供`content uri`增加了文件访问权限等级，使得`FileProvider`成为`Android`安全基础框架非常重要的部分。

关于`FileProvider`，通过下面5点介绍：

1. 定义`FileProvider`
2. 指定可用的文件
3. 为一个文件生成`content uri`
4. 为一个`uri`赋予临时权限
5. 提供`content uri`给其他应用

### 1. 定义`FileProvider`
因为`FileProvider`的默认功能就是为文件提供`content uri`，你不需要定义`FileProvider`的子类。而是，你在`manifest`中包含一个`FileProvider`。为了指定`FileProvider`组件，在`manifest`添加一个`provider`元素。这是`android:name`属性为`android.support.v4.content.FileProvider`（`androidx`为`androidx.core.content.FileProvider`）。设置`android:authorities`为`content uri`的域名。例如你的域名是`wang.mycroft`，你应该设置`authority`为`wang.mycroft.fileprovider`。设置`android:exported`属性为`false`，`FileProvider`不需要设置为公开的。设置`android:grantUriPermissions`属性为`true`，为了允许赋予文件的临时访问权限。如下：

```xml
<manifest>
    ...
    <application>
        ...
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="wang.mycroft.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            ...
        </provider>
        ...
    </application>
</manifest>
```

如果你想重写`FileProvider`方法的默认行为，那么继承`FileProvider`类，在`provider`中指定`android:name`为其的全路径类名。

### 2. 指定可用的文件

一个`FileProvider`只能为预先指定的文件夹下的文件提供`content uri`。为了指定一个文件夹，在`xml`中指定文件的存储路径，在`paths`下添加子属性。例如，下列的`path`元素告诉`FileProvider`你想要把私有文件区域下的`image/`子目录提供`content uri`。

```xml
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="my_images" path="images/"/>
    ...
</paths>
```

`<paths>`元素必须包含一个或多个子元素：

```xml
<files-path name="name" path="path" />
```

代表`app`内部存储区域的`files/`子目录下的文件。此子目录的值和`Context.getFilesDir()`的返回值相同。

```xml
<cache-path name="name" path="path" />
```

代表`app`内部存储区域的缓存子目录。此子目录的值和`Context.getCacheDir()`的返回值相同。

```xml
<external-path name="name" path="path" />
```

代表外部存储区域的根目录。此子目录的值和`Environment.getExternalStorageDirectory()`的返回值相同。

```xml
<external-files-path name="name" path="path" />
```

代表`app`外部存储区域的文件。此子目录的值和`Context.getExternalFilesDir(null)`的返回值相同。

```xml
<external-cache-path name="name" path="path" />
```

代表`app`外部存储区域的缓存子目录文件。此子目录的值和`Context.getExternalCacheDir()`的返回值相同。

```xml
<external-media-path name="name" path="path" />
```

代表`app`外部存储区域的多媒体子目录文件。此子目录的值和`Context.getExternalMediaDirs()`的返回值相同。（`Context.getExternalMediaDirs()`需要`API > 21`）

---

这些子元素使用相同的属性：

1. `name`：`uri`相对路径。为了强制保证安全，这个值用于隐藏实际分享的子目录。子目录名包含在`path`属性上。
2. `path`：被分享的目录。`name`被认为是`uri`相对路径，`path`则是实际分享的子目录。注意，`path`的值是一个子目录，不是具体的文件。不能单独指定一个分享的文件名，也不能使用通配符指定一系列的文件。

一定要将被分享文件的所在目录添加到`paths`中，作为一个子元素，如下`xml`中制定了两个子目录：

```xml
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="my_images" path="images/"/>
    <files-path name="my_docs" path="docs/"/>
</paths>
```

将`paths`元素和其子元素添加到项目中的`xml`文件中。例如将其放在`res/xml/file_paths.xml`中。为了在`FileProvider`中引用这个文件，添加一个`<meta-data>`元素作为我们定义的`<provider>`的子元素。设置`<meta-data>`元素的子元素`android:name`值为`android.support.FILE_PROVIDER_PATHS`，设置子元素`android:resource`的属性值为`@xml/file_paths`（注意不需要添加后缀`.xml`）。如下：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="wang.mycroft.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### 3. 为一个文件生成`content uri`

为了使用`content uri`分享一个文件到另外的应用程序，你的`app`必须生成`content uri`。为了生成`content uri`，为这个文件构造一个`File`对象，传入`FileProvider.getUriForFile()`方法中，得到一个`URI`对象。你可以将得到的`URI`添加到一个`Intent`中，然后发送到另外的应用程序。收到`URI`的应用程序，可以通过调用`ContentResolver.openFileDescriptor()`得到一个`ParcelFileDescriptor`对象，用于打开文件和获取其中的内容。

例如，假定你的`app`使用一个`FileProvider`分享文件到其他`app`中，`authority`值为`wang.mycroft.fileprovier`。为了获取在内部存储区域的`images/`子目录中的文件`default_image.jpg`的`content uri`，使用如下代码：

```java
File imagePath = new File(Context.getFilesDir(), "images");
File newFile = new File(imagePath, "default_image.jpg");
Uri contentUri = getUriForFile(getContext(), "wang.mycroft.fileprovider", newFile);
```

最后得到的`content uri`的值是：`content://wang.mycroft.fileprovider/images/default_image.jpg`。

### 4. 为一个`uri`赋予临时权限

为了为`FileProvider.getUriForFile()`得到的`content uri`赋予访问权限，需要如下步骤：

1. 为一个`content uri`调用`Context.grantUriPermission(package, Uri, mode_flags)`，使用期望的标记（`flags`）。这样就为指定的包赋予了`content uri`临时的访问权限。标记（`flags`）可以设置的值为：`Intent.FLAG_GRANT_READ_URI_PERMISSION`和（或）` Intent.FLAG_GRANT_WRITE_URI_PERMISSION`。权限保留到你调用`revokeUriPermission()`或者直到设备重启。
2. 调用`Intent.setData()`将`content uri`添加到`Intent`中。
3. 调用`Intent.setFlags()`设置`Intent.FLAG_GRANT_READ_URI_PERMISSION`和（或）` Intent.FLAG_GRANT_WRITE_URI_PERMISSION`。最后将`Intent`发送到另外的`app`中。大多数时候，你会通过`Activity.setResult()`使用。

`content uri`的`Activity`所在的栈保持活跃状态，那么权限就会一直会被保留。当任务栈结束，权限将自动移除。权限会被赋予给`Activity`所在`app`的所有组件。

### 5. 提供`content uri`给其他应用

会有多种方法将一个`content uri`提供给其他`app`。一个通用的方法是通过调用`startActivityForResult()`，其他应用通过发送一个`Intent`启动我们`app`的`Activity`。作为相应，我们的`app`将直接返回一个`content uri`给对方的`app`，或者提供一个界面，让用户选择文件。在后一种情况下，一旦用户选择我们`app`的文件，我们将提供文件的`content uri`。在两种情况下，我们的`app`都会通过`setResult()`返回带有`content uri`的`Intent`。

你也可以将`content uri`放在`ClipData`中。然后将`ClipData`添加到`Intent`发送到指定`app`。通过调用`Intent.setClipData()`即可。可以在`Intent`添加多个`ClipData`。当你调用`Intent.setFlags()`设置临时权限时，同样的权限将被设置到所有的`content uri`中。

## 源码

`FileProvider`的源码比较简单，反而我觉得应该更多的了解`ContentResolver`，`FileDescriptor`，`ParcelFileDescriptor`的使用，这是`FileProvider`的基础知识。所以这里不分析源码，后面有机会再深入了解。

## 使用

举几个我们在开发过程中，实际会遇到的问题。

下面的代码中都是用了`Intent.addFlags(int)`添加`Intent.FLAG_GRANT_READ_URI_PERMISSION`或`Intent.FLAG_GRANT_WRITE_URI_PERMISSION`权限，这样就为`Intent`中所有的`Uri`和`ClipData`赋予了临时权限。另外还有一种方法是使用`Context.grantUriPermission(String, Uri)`来单独为某一个`package`（包/`app`）赋予`Uri`的访问权限。两者必有其一，不用重复添加。

### 前提

在`manifest中添加`FileProvider`：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="wang.mycroft.fileprovider.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

下面是`res/xml/file_paths.xml`的内容：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path
        name="image"
        path="image_file" />
    <external-files-path
        name="apk"
        path="apk_file" />
</paths>
```

### 1. 下载`apk`，调用系统安装

因为无法再使用`Uri.fromFile(File)`，所以就必须使用`FileProvider`。如下所示，得到`apk`文件的`content uri`，然后启动`Android`安装器。另外需要注意，添加安装文件的权限。

```kotlin
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
```

```xml
<!-- 请求安装APK的权限，API29舍弃，应该使用PackageInstaller -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

### 2. 调用相机拍照

下面将`app`私有文件提供给相机，拍照后进行保存。

```kotlin
private var tempPhotoUri: Uri? = null

private var tempFile: File? = null

private fun takePhoto() {
    tempFile = File(File(filesDir, "image_file"), "${UUID.randomUUID()}.jpg")
    if (!tempFile?.parentFile?.exists()!! && tempFile?.parentFile?.mkdirs()!!) {
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
        // 回收权限
        revokeUriPermission(tempPhotoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        // 有可能返回结果为Activity.RESULT_CANCEL
        if (resultCode == Activity.RESULT_OK) {
            val option = BitmapFactory.Options()
            option.inSampleSize = 4
            image.setImageBitmap(BitmapFactory.decodeFile(tempFile?.absolutePath, option))
        }
    }
}
```

### 3. 图片读取并压缩

在如下代码中，读取一个`content uri`的图片文件，进行压缩，并显示在屏幕上。

```kotlin
private fun compressImageUri(imageUri: Uri) {
    // 打开流
    contentResolver.openInputStream(imageUri)?.let {
        // 仅仅读取尺寸
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(it, null, options)

        // 读取到了尺寸，设置inSampleSize
        options.inJustDecodeBounds = false
        val screenWidth = getScreenWidth()
        if (screenWidth != -1) {
            options.inSampleSize = options.outWidth / screenWidth
        }

        // 真正的读取内容
        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri), null, options)
        bitmap.let {
            // 显示在ImageView上
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
```

## 总结

我们在`app`之间共享内容最多的应该就是图片了，而`Android 7.0`开始，不允许直接使用`file:///`的`URI`进行共享，这样会触发`FileUriExposedException`。取而代之的是使用`content uri`。避免了文件安全问题，但是也增加了开发成本，当然也是我们必须学习的一环。

`FileProvider`的使用其实非常简单，难以理解的是`Uri`的操作。但是作为安卓开发工作者要接受一个比较重要的概念：避免直接使用文件，一切使用`Uri`来共享内容，并赋予访问权限。