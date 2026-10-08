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
package brut.androlib.res

import brut.androlib.Config
import brut.androlib.exceptions.AndrolibException
import brut.androlib.meta.ApkInfo
import brut.androlib.meta.SdkInfo
import brut.androlib.meta.VersionInfo
import brut.androlib.res.decoder.BinaryXmlResourceParser
import brut.androlib.res.decoder.ManifestPullEventHandler
import brut.androlib.res.decoder.ResFileDecoder
import brut.androlib.res.decoder.ResNinePatchStreamDecoder
import brut.androlib.res.decoder.ResRawStreamDecoder
import brut.androlib.res.decoder.ResStreamDecoder
import brut.androlib.res.decoder.ResXmlPullEventHandler
import brut.androlib.res.decoder.ResXmlPullStreamDecoder
import brut.androlib.res.table.ResEntry
import brut.androlib.res.table.ResEntrySpec
import brut.androlib.res.table.ResId
import brut.androlib.res.table.ResOverlayable
import brut.androlib.res.table.ResPackage
import brut.androlib.res.table.ResTable
import brut.androlib.res.table.ResTypeSpec
import brut.androlib.res.table.value.ResBag
import brut.androlib.res.table.value.ResFileReference
import brut.androlib.res.xml.ResXmlSerializer
import brut.androlib.res.xml.ResXmlUtils
import brut.androlib.res.xml.ValuesXmlSerializable
import brut.common.Log
import brut.directory.Directory
import brut.directory.DirectoryException
import brut.directory.FileDirectory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Comparator
import java.util.HashMap
import java.util.LinkedHashMap

/**
 * 资源解码编排器：把 resources.arsc 与 AndroidManifest.xml 解码到解包目录。
 *
 * 阶段：
 * 1. 解码 value 资源（bag 先 resolveKeys）；
 * 2. 用文件解码器映射表解码文件资源；
 * 3. 生成 values XML（按限定词分组）、public.xml、staging 分组、overlayable.xml；
 * 4. manifest 解码后回填 apktool.yml 元数据（包名/SDK/版本重查、framework/library 记录），
 *    并清理 manifest 中的版本属性。
 */
class ResDecoder(
    private val mApkInfo: ApkInfo,
    private val mConfig: Config,
) {
    /** 资源表。 */
    val table: ResTable = ResTable(mApkInfo, mConfig)

    /** 包内路径 -> 解包路径 映射。 */
    val resFileMap: MutableMap<String, String> = HashMap()

    /** 解码 resources.arsc 全部内容。 */
    @Throws(AndrolibException::class)
    fun decodeResources(apkDir: File?) {
        if (!mApkInfo.hasResources()) {
            return
        }

        table.load()

        val inDir: Directory
        val outDir: Directory
        try {
            inDir = mApkInfo.apkFile!!.getDirectory()
            outDir = FileDirectory(apkDir!!)
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }

        val pkg = table.mainPackage!!

        val decoders = HashMap<ResFileDecoder.Type, ResStreamDecoder>()
        decoders[ResFileDecoder.Type.UNKNOWN] = ResRawStreamDecoder()
        decoders[ResFileDecoder.Type.PNG_9PATCH] = ResNinePatchStreamDecoder()

        val parser = BinaryXmlResourceParser(
            table, mConfig.isIgnoreRawValues, mConfig.isDecodeResolveLazy,
        )
        var serial = ResXmlSerializer(true)
        val handler = ResXmlPullEventHandler(mApkInfo)
        decoders[ResFileDecoder.Type.BINARY_XML] = ResXmlPullStreamDecoder(parser, serial, handler)

        val fileDecoder = ResFileDecoder(decoders)

        Log.i(TAG, "Decoding value resources...")
        for (entry in listEntries(pkg).toMutableList()) {
            val value = entry.value
            if (value is ResBag) {
                value.resolveKeys()
            }
        }

        Log.i(TAG, "Decoding file resources...")
        for (entry in listEntries(pkg).toMutableList()) {
            if (entry.value is ResFileReference) {
                fileDecoder.decode(entry, inDir, outDir, resFileMap)
            }
        }

        parser.getFirstError()?.let { throw it }

        // 生成的 values XML 关闭自动转义。
        serial = ResXmlSerializer(false)

        Log.i(TAG, "Generating values XMLs...")
        generateValuesXmls(pkg, outDir, serial)
        generatePublicXml(pkg, outDir, serial)
        generateStagingXmls(pkg, outDir, serial)
        generateOverlayableXml(pkg, outDir, serial)
    }

    /** 列出包内条目（framework 包走组级条目）。 */
    private fun listEntries(pkg: ResPackage): Iterable<ResEntry> =
        if (pkg.getId() == ResTable.SYS_PACKAGE_ID) {
            pkg.getGroup().listEntries()
        } else {
            pkg.listEntries()
        }

    /** 按 "类型名+限定词" 分组生成 values*.xml。 */
    private fun generateValuesXmls(pkg: ResPackage, outDir: Directory, serial: ResXmlSerializer) {
        // 按类型名+限定词聚合条目，忽略子包别名重复；
        // 同时决定每组是否需要写 android 命名空间。
        val entriesMap = HashMap<Pair<String, String>, MutableList<ResEntry>>()
        val needsNamespace = HashSet<Pair<String, String>>()
        for (entry in listEntries(pkg)) {
            val value = entry.value
            if (value is ValuesXmlSerializable && !pkg.isAlias(entry.getResId())) {
                val type = entry.getType()
                val key = Pair(type.getName(), type.getConfig().toQualifiers())
                entriesMap.getOrPut(key) { ArrayList() }.add(entry)
                if (type.flag != null) {
                    needsNamespace.add(key)
                }
            }
        }

        // 每组输出一个 values XML。
        for ((key, entries) in entriesMap) {
            val typeName = key.first
            val qualifiers = key.second

            entries.sortBy { it.getResId() }

            val outFileName = "res/values$qualifiers/" +
                (if (typeName.endsWith("s")) {
                    typeName
                } else if (typeName == "^attr-private") {
                    "attrs-private"
                } else {
                    typeName + "s"
                }) + ".xml"
            try {
                outDir.getFileOutput(outFileName).use { out ->
                    serial.setOutput(out, null)
                    serial.startDocument(null, null)
                    if (needsNamespace.contains(key)) {
                        serial.setPrefix("android", ResXmlUtils.ANDROID_RES_NS)
                    }
                    serial.startTag(null, "resources")

                    for (entry in entries) {
                        // 已校验过值类型，直接转换。
                        (entry.value as ValuesXmlSerializable).serializeToValuesXml(serial, entry)
                    }

                    serial.endTag(null, "resources")
                    serial.endDocument()
                }
            } catch (ex: DirectoryException) {
                throw AndrolibException("Could not generate: $outFileName", ex)
            } catch (ex: IOException) {
                throw AndrolibException("Could not generate: $outFileName", ex)
            }
        }
    }

    /** 生成 res/values/public.xml。 */
    private fun generatePublicXml(pkg: ResPackage, outDir: Directory, serial: ResXmlSerializer) {
        val specs = ArrayList(pkg.listEntrySpecs())
        specs.sortBy { it.getResId() }

        val outFileName = "res/values/public.xml"
        try {
            outDir.getFileOutput(outFileName).use { out ->
                serial.setOutput(out, null)
                serial.startDocument(null, null)
                serial.startTag(null, "resources")

                for (spec in specs) {
                    serial.startTag(null, "public")
                    serial.attribute(null, "type", spec.getTypeSpec().name)
                    serial.attribute(null, "name", spec.name)
                    serial.attribute(null, "id", spec.getResId().toString())
                    serial.endTag(null, "public")
                }

                serial.endTag(null, "resources")
                serial.endDocument()
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException("Could not generate: $outFileName", ex)
        } catch (ex: IOException) {
            throw AndrolibException("Could not generate: $outFileName", ex)
        }
    }

    /** framework 包：把子包条目拆成 staging / final 两组 public XML。 */
    private fun generateStagingXmls(pkg: ResPackage, outDir: Directory, serial: ResXmlSerializer) {
        if (pkg.getId() != ResTable.SYS_PACKAGE_ID) {
            return
        }

        // 收集并排序子包全部条目规格。
        val subSpecs = ArrayList<ResEntrySpec>()
        for (subPkg in pkg.getGroup().listSubPackages()) {
            subSpecs.addAll(subPkg.listEntrySpecs())
        }
        if (subSpecs.isEmpty()) {
            return
        }
        subSpecs.sortBy { it.getResId() }

        // 按类型分组。
        val stagingGroups = LinkedHashMap<ResTypeSpec, MutableList<ResEntrySpec>>()
        val finalGroups = LinkedHashMap<ResTypeSpec, MutableList<ResEntrySpec>>()
        var prevMap: MutableMap<ResTypeSpec, MutableList<ResEntrySpec>>? = null
        var currList: MutableList<ResEntrySpec>? = null
        var prevId: ResId? = null
        for (spec in subSpecs) {
            val currId = spec.getResId()
            val currMap = if (pkg.isAlias(currId)) finalGroups else stagingGroups

            if (prevId == null || currId.typeId() != prevId.typeId() || currMap !== prevMap) {
                currList = ArrayList()
                currMap[spec.getTypeSpec()] = currList
                prevMap = currMap
            }

            currList!!.add(spec)
            prevId = currId
        }

        // 输出 staging 分组。
        if (stagingGroups.isNotEmpty()) {
            writeStagingGroup(stagingGroups, "res/values/public-staging.xml",
                "staging-public-group", pkg, outDir, serial)
        }

        // 输出 final 分组。
        if (finalGroups.isNotEmpty()) {
            writeStagingGroup(finalGroups, "res/values/public-final.xml",
                "staging-public-group-final", pkg, outDir, serial)
        }
    }

    private fun writeStagingGroup(
        groups: MutableMap<ResTypeSpec, MutableList<ResEntrySpec>>,
        outFileName: String,
        tagName: String,
        pkg: ResPackage,
        outDir: Directory,
        serial: ResXmlSerializer,
    ) {
        try {
            outDir.getFileOutput(outFileName).use { out ->
                serial.setOutput(out, null)
                serial.startDocument(null, null)
                serial.startTag(null, "resources")

                for ((typeSpec, specs) in groups) {
                    val firstId = ResId.of(typeSpec.`package`.getId(), typeSpec.getId(), 0)

                    serial.startTag(null, tagName)
                    serial.attribute(null, "type", typeSpec.name)
                    serial.attribute(null, "first-id", firstId.toString())

                    var lastEntryId = 0
                    for (spec in specs) {
                        val entryId = spec.getId()
                        while (lastEntryId++ < entryId) {
                            serial.startTag(null, "public")
                            serial.attribute(null, "name", "removed_")
                            serial.endTag(null, "public")
                        }

                        serial.startTag(null, "public")
                        serial.attribute(null, "name", spec.name)
                        serial.endTag(null, "public")
                    }

                    serial.endTag(null, tagName)
                }

                serial.endTag(null, "resources")
                serial.endDocument()
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException("Could not generate: $outFileName", ex)
        } catch (ex: IOException) {
            throw AndrolibException("Could not generate: $outFileName", ex)
        }
    }

    /** 生成 res/values/overlayable.xml。 */
    private fun generateOverlayableXml(pkg: ResPackage, outDir: Directory, serial: ResXmlSerializer) {
        if (pkg.getOverlayableCount() == 0) {
            return
        }

        val overlayables = ArrayList(pkg.listOverlayables())
        overlayables.sortBy { it.getName() }

        val outFileName = "res/values/overlayable.xml"
        try {
            outDir.getFileOutput(outFileName).use { out ->
                serial.setOutput(out, null)
                serial.startDocument(null, null)
                serial.startTag(null, "resources")

                for (overlayable in overlayables) {
                    overlayable.serializeToXml(serial)
                }

                serial.endTag(null, "resources")
                serial.endDocument()
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException("Could not generate: $outFileName", ex)
        } catch (ex: IOException) {
            throw AndrolibException("Could not generate: $outFileName", ex)
        }
    }

    /** 解码 AndroidManifest.xml 并回填元数据。 */
    @Throws(AndrolibException::class)
    fun decodeManifest(apkDir: File) {
        if (!mApkInfo.hasManifest()) {
            return
        }

        val inDir: Directory
        val outDir: Directory
        try {
            inDir = mApkInfo.apkFile!!.getDirectory()
            outDir = FileDirectory(apkDir)
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }

        val pkg = table.mainPackage

        val parser = BinaryXmlResourceParser(
            table, mConfig.isIgnoreRawValues, mConfig.isDecodeResolveLazy,
        )
        val serial = ResXmlSerializer(true)
        val handler = ManifestPullEventHandler(mApkInfo, !mConfig.isAnalysisMode)
        val decoder = ResXmlPullStreamDecoder(parser, serial, handler)

        Log.i(TAG, "Decoding AndroidManifest.xml with " +
            (if (pkg != null) "resources" else "only framework resources") + "...")
        try {
            inDir.getFileInput("AndroidManifest.xml").use { `in` ->
                outDir.getFileOutput("AndroidManifest.xml").use { out ->
                    decoder.decode(`in`, out)
                }
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        }

        val resourcesInfo = mApkInfo.resourcesInfo

        // 应用保留了原始属性值时打标。
        if (parser.hasRawValues() && !mConfig.isIgnoreRawValues) {
            resourcesInfo.setKeepRawValues(true)
        }

        if (pkg != null) {
            resourcesInfo.setPackageId(pkg.getId())

            // packageName 此前被临时填成 manifest 包名；
            // 与实际资源包不同才保留（改名），相同则清空。
            val manifestPackage = resourcesInfo.packageName
            val resourcesPackage = pkg.getName()
            if (!resourcesPackage.isNullOrEmpty() && resourcesPackage != manifestPackage) {
                resourcesInfo.packageName = resourcesPackage
            } else {
                // 无需改名：资源包为空或与 manifest 相同。
                resourcesInfo.packageName = null
            }

            // 从资源解析 sdkInfo 引用。
            val sdkInfo = mApkInfo.sdkInfo
            if (!sdkInfo.isEmpty) {
                sdkInfo.minSdkVersion?.let { ref ->
                    ResXmlUtils.pullValueFromIntegers(apkDir, ref)?.let { sdkInfo.minSdkVersion = it }
                }
                sdkInfo.targetSdkVersion?.let { ref ->
                    ResXmlUtils.pullValueFromIntegers(apkDir, ref)?.let { sdkInfo.targetSdkVersion = it }
                }
                sdkInfo.maxSdkVersion?.let { ref ->
                    ResXmlUtils.pullValueFromIntegers(apkDir, ref)?.let { sdkInfo.maxSdkVersion = it }
                }
            }

            // 从资源解析 versionName 引用。
            val versionInfo = mApkInfo.versionInfo
            if (!versionInfo.isEmpty) {
                versionInfo.versionName?.let { ref ->
                    ResXmlUtils.pullValueFromStrings(apkDir, ref)?.let { versionInfo.versionName = it }
                }
            }

            // 记录资源表用到的 framework 包 ID。
            val framePackageIds = ArrayList(table.getFramePackageIds())
            if (framePackageIds.isNotEmpty()) {
                val usesFramework = mApkInfo.usesFramework
                val frameworkIds = usesFramework.ids
                framePackageIds.sort()
                for (id in framePackageIds) {
                    frameworkIds.add(id)
                }
                usesFramework.tag = mConfig.frameworkTag
            }

            // 记录资源表用到的共享库包名。
            val libPackageIds = ArrayList(table.getLibPackageIds())
            if (libPackageIds.isNotEmpty()) {
                val usesLibrary = mApkInfo.usesLibrary
                libPackageIds.sort()
                for (id in libPackageIds) {
                    usesLibrary.add(if (id == pkg.getId()) pkg.getName() else table.getDynamicRefPackageName(id))
                }
            }
        } else {
            // 无法改名：manifest 在无资源模式下解码。
            resourcesInfo.packageName = null
        }

        val manifest = File(apkDir, "AndroidManifest.xml")

        if (!mConfig.isAnalysisMode) {
            // 移除 versionCode/versionName：改由 apktool.yml 传参给 aapt2。
            ResXmlUtils.removeManifestVersions(manifest)
        }
    }

    companion object {
        private val TAG = ResDecoder::class.java.name
    }
}
