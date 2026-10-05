/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.androlib.meta

import brut.androlib.exceptions.AndrolibException
import brut.directory.DirectoryException
import brut.directory.ExtFile
import brut.yaml.YamlPullParser
import brut.yaml.YamlSerializable
import brut.yaml.YamlSerializer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.TreeSet
import java.util.regex.Pattern

/**
 * apktool.yml 的根模型：APK 元数据总集（版本、框架依赖、SDK/资源信息、特性标志等）。
 *
 * [onEntry] 解析时做 apkFileName 恶意路径校验（禁止分隔符与 . / ..）；
 * [hasSources] / [hasManifest] / [hasResources] 基于 [apkFile] 的目录视图探测内容。
 */
class ApkInfo : YamlSerializable {
    private var mVersion: String? = null
    private var mApkFileName: String? = null
    private val mUsesFramework = UsesFramework()
    private val mUsesLibrary = ArrayList<String?>()
    private val mSdkInfo = SdkInfo()
    private val mVersionInfo = VersionInfo()
    private val mResourcesInfo = ResourcesInfo()
    private val mFeatureFlags = TreeSet<String?>()
    private val mDoNotCompress = ArrayList<String?>()

    // 仅在从文件（而非流）加载时设置。
    private var mApkFile: ExtFile? = null

    /** 写入 apktool.yml。 */
    @Throws(IOException::class)
    fun save(file: File) {
        YamlSerializer(Files.newOutputStream(file.toPath())).use { serial ->
            serialize(serial)
        }
    }

    @Throws(IOException::class)
    override fun onEntry(parser: YamlPullParser) {
        when (parser.getKey()) {
            "version" -> mVersion = parser.getString()
            "apkFileName" -> {
                mApkFileName = parser.getString()
                // 对可能的恶意输入做校验。
                val name = mApkFileName
                if (name != null &&
                    (name == "." || name == ".." ||
                        name.contains('/') || name.contains('\\'))
                ) {
                    throw SecurityException("Malicious value for apkFileName: $name")
                }
            }
            "usesFramework" -> {
                mUsesFramework.clear()
                parser.readObject(mUsesFramework)
            }
            "usesLibrary" -> {
                mUsesLibrary.clear()
                parser.readStringSeq(mUsesLibrary)
            }
            "sdkInfo" -> {
                mSdkInfo.clear()
                parser.readObject(mSdkInfo)
            }
            "versionInfo" -> {
                mVersionInfo.clear()
                parser.readObject(mVersionInfo)
            }
            "resourcesInfo" -> {
                mResourcesInfo.clear()
                parser.readObject(mResourcesInfo)
            }
            "featureFlags" -> {
                mFeatureFlags.clear()
                parser.readStringSeq(mFeatureFlags)
            }
            "doNotCompress" -> {
                mDoNotCompress.clear()
                parser.readStringSeq(mDoNotCompress)
            }
        }
    }

    @Throws(IOException::class)
    override fun serialize(serial: YamlSerializer) {
        serial.writeString("version", mVersion)
        serial.writeString("apkFileName", mApkFileName)
        if (!mUsesFramework.isEmpty) {
            serial.writeObject("usesFramework", mUsesFramework)
        }
        if (mUsesLibrary.isNotEmpty()) {
            serial.writeStringSeq("usesLibrary", mUsesLibrary)
        }
        if (!mSdkInfo.isEmpty) {
            serial.writeObject("sdkInfo", mSdkInfo)
        }
        if (!mVersionInfo.isEmpty) {
            serial.writeObject("versionInfo", mVersionInfo)
        }
        if (!mResourcesInfo.isEmpty) {
            serial.writeObject("resourcesInfo", mResourcesInfo)
        }
        if (mFeatureFlags.isNotEmpty()) {
            serial.writeStringSeq("featureFlags", mFeatureFlags)
        }
        if (mDoNotCompress.isNotEmpty()) {
            serial.writeStringSeq("doNotCompress", mDoNotCompress)
        }
    }

    /** apktool 版本号。 */
    var version: String?
        get() = mVersion
        set(value) {
            mVersion = value
        }

    /** 目标 APK 文件名（不含路径）。 */
    var apkFileName: String?
        get() = mApkFileName
        set(value) {
            mApkFileName = value
        }

    /** framework 依赖声明。 */
    val usesFramework: UsesFramework
        get() = mUsesFramework

    /** uses-library 列表。 */
    val usesLibrary: MutableList<String?>
        get() = mUsesLibrary

    /** SDK 版本信息。 */
    val sdkInfo: SdkInfo
        get() = mSdkInfo

    /** versionCode/versionName 信息。 */
    val versionInfo: VersionInfo
        get() = mVersionInfo

    /** 资源表信息。 */
    val resourcesInfo: ResourcesInfo
        get() = mResourcesInfo

    /** 收集到的特性标志名集合（去重有序）。 */
    val featureFlags: MutableSet<String?>
        get() = mFeatureFlags

    /** 构建时需保持 STORED 的扩展名/条目集合。 */
    val doNotCompress: MutableList<String?>
        get() = mDoNotCompress

    /** 关联的 APK 文件；首次设置时补齐 apkFileName。 */
    var apkFile: ExtFile?
        get() = mApkFile
        set(value) {
            mApkFile = value
            if (mApkFileName == null && value != null) {
                mApkFileName = value.name
            }
        }

    /** 是否包含 dex 源码。 */
    @Throws(AndrolibException::class)
    fun hasSources(): Boolean {
        val file = mApkFile ?: return false
        return try {
            file.getDirectory().containsFile("classes.dex")
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    /** 是否包含 AndroidManifest.xml。 */
    @Throws(AndrolibException::class)
    fun hasManifest(): Boolean {
        val file = mApkFile ?: return false
        return try {
            file.getDirectory().containsFile("AndroidManifest.xml")
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    /** 是否包含 resources.arsc。 */
    @Throws(AndrolibException::class)
    fun hasResources(): Boolean {
        val file = mApkFile ?: return false
        return try {
            file.getDirectory().containsFile("resources.arsc")
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    companion object {
        /** 原样导出的资源目录。 */
        @JvmField
        val RAW_DIRS: Array<String> = arrayOf("assets", "lib")

        /** classes2.dex 及以上的多 dex 文件名模式。 */
        @JvmField
        val CLASSES_FILES_PATTERN: Pattern = Pattern.compile("classes([2-9]|[1-9][0-9]+)?\\.dex")

        /** 签名与时间戳类文件模式。 */
        @JvmField
        val ORIGINAL_FILES_PATTERN: Pattern = Pattern.compile(
            "AndroidManifest\\.xml|META-INF/[^/]+\\.(RSA|SF|MF)|stamp-cert-sha256"
        )

        /** aapt2 应直接拷贝（不编译）的标准文件模式。 */
        @JvmField
        val STANDARD_FILES_PATTERN: Pattern = Pattern.compile(
            "resources\\.arsc|(" + RAW_DIRS.joinToString("|") + ")/.*|" +
                CLASSES_FILES_PATTERN.pattern() + "|" + ORIGINAL_FILES_PATTERN.pattern()
        )

        /** 从流加载 apktool.yml。 */
        @JvmStatic
        @Throws(IOException::class)
        fun load(`in`: InputStream?): ApkInfo {
            YamlPullParser(`in`!!).use { parser ->
                val apkInfo = ApkInfo()
                parser.readObject(apkInfo)
                return apkInfo
            }
        }

        /** 从文件加载 apktool.yml。 */
        @JvmStatic
        @Throws(IOException::class)
        fun load(file: File): ApkInfo {
            YamlPullParser(Files.newInputStream(file.toPath())).use { parser ->
                val apkInfo = ApkInfo()
                parser.readObject(apkInfo)
                return apkInfo
            }
        }
    }
}
