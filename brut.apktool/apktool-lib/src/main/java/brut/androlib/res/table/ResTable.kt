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
package brut.androlib.res.table

import brut.androlib.Config
import brut.androlib.exceptions.AndrolibException
import brut.androlib.exceptions.UndefinedResObjectException
import brut.androlib.meta.ApkInfo
import brut.androlib.res.Framework
import brut.androlib.res.decoder.BinaryResourceParser
import brut.common.Log
import brut.directory.DirectoryException
import brut.directory.ExtFile
import brut.directory.ZipRODirectory
import java.io.File
import java.io.IOException

/**
 * 资源表（resources.arsc）根对象。
 *
 * [load] 解析主 APK 后，共享库与 framework 包按需懒加载；
 * package ID 为 0 的共享库经动态引用表（[mDynamicRefTable]）换回真实 ID 或顺延分配。
 */
class ResTable(
    private val mApkInfo: ApkInfo,
    private val mConfig: Config,
) {
    private val mPackageGroups = LinkedHashMap<Int, ResPackageGroup>()
    private val mLibPackageIds = ArrayList<Int>()
    private val mFramePackageIds = ArrayList<Int>()
    private val mDynamicRefTable = LinkedHashMap<Int, String>()
    private var mNextPackageId = SYS_PACKAGE_ID + 1

    /** 主包（加载后确定）。 */
    var mainPackage: ResPackage? = null
        private set

    /** 关联的 APK 元信息。 */
    val apkInfo: ApkInfo
        get() = mApkInfo

    /** 全局配置。 */
    val config: Config
        get() = mConfig

    /** 已加载的库包 ID 集合。 */
    fun getLibPackageIds(): Collection<Int> = mLibPackageIds

    /** 已加载的 framework 包 ID 集合。 */
    fun getFramePackageIds(): Collection<Int> = mFramePackageIds

    /** 解析主包资源表。 */
    @Throws(AndrolibException::class)
    fun load() {
        if (mainPackage != null) {
            throw AndrolibException("The resource table has already been loaded.")
        }

        Log.i(TAG, "Loading resource table...")
        val apkFile = mApkInfo.apkFile!!

        val zipDir: ZipRODirectory = try {
            apkFile.getDirectory() as ZipRODirectory
        } catch (ex: DirectoryException) {
            throw AndrolibException("Could not open apk file: $apkFile", ex)
        }

        loadPackagesFromApk(apkFile, zipDir, true)

        val pkgGroup: ResPackageGroup = if (mPackageGroups.isEmpty()) {
            // resources.arsc 为空：创建一个 ID 为 0 的占位包组。
            val group = ResPackageGroup(this, 0, "")
            mPackageGroups[0] = group
            group
        } else if (mPackageGroups.containsKey(APP_PACKAGE_ID)) {
            // 优先使用标准应用包组（0x7F）。
            mPackageGroups[APP_PACKAGE_ID]!!
        } else {
            // 否则取表中第一个包组。
            mPackageGroups.values.iterator().next()
        }

        mainPackage = pkgGroup.getBasePackage()
    }

    @Throws(AndrolibException::class)
    private fun loadPackagesFromApk(apkFile: File, zipDir: ZipRODirectory, isMainPackage: Boolean) {
        try {
            if (!zipDir.containsFile("resources.arsc")) {
                throw AndrolibException("Could not find resources.arsc in file: $apkFile")
            }

            zipDir.getFileInput("resources.arsc").use { `in` ->
                val parser = if (isMainPackage) {
                    BinaryResourceParser(this, mConfig.isKeepBrokenResources, mConfig.isDecodeResolveGreedy)
                } else {
                    BinaryResourceParser(this, true, true)
                }
                parser.parse(`in`)

                // apk 元信息只在主包解析时更新。
                if (isMainPackage) {
                    if (parser.hasSparseEntries()) {
                        mApkInfo.resourcesInfo.setSparseEntries(true)
                    }
                    if (parser.hasCompactEntries()) {
                        mApkInfo.resourcesInfo.setCompactEntries(true)
                    }
                    parser.getFlags()?.let { mApkInfo.featureFlags.addAll(it) }
                }
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException("Could not load resources.arsc from file: $apkFile", ex)
        } catch (ex: IOException) {
            throw AndrolibException("Could not load resources.arsc from file: $apkFile", ex)
        }
    }

    /** 包组是否存在。 */
    fun hasPackageGroup(id: Int): Boolean = mPackageGroups.containsKey(id)

    /** 取包组。 */
    @Throws(UndefinedResObjectException::class)
    fun getPackageGroup(id: Int): ResPackageGroup =
        mPackageGroups[id] ?: throw UndefinedResObjectException(
            String.format("package group: id=0x%02x", id)
        )

    /** 登记包组；ID 为 0 的共享库包会在此处换取真实 ID。 */
    @Throws(AndrolibException::class)
    fun addPackageGroup(idArg: Int, name: String): ResPackageGroup {
        var id = idArg
        val existing = mPackageGroups[id]
        if (existing != null) {
            throw AndrolibException(
                String.format("Repeated package group: id=0x%02x, name=%s", id, name)
            )
        }

        // ID 为 0x00 且主包已加载：说明正在加载共享库，改用动态引用表中的 ID，
        // 或顺延分配下一个未被占用的 ID。
        if (id == 0 && mainPackage != null) {
            id = getDynamicRefPackageId(name)
            if (id == 0) {
                do {
                    id = mNextPackageId++
                } while (mDynamicRefTable.containsKey(id))
            }
        }

        val pkgGroup = ResPackageGroup(this, id, name)
        mPackageGroups[id] = pkgGroup
        return pkgGroup
    }

    /** 包组数量。 */
    fun getPackageGroupCount(): Int = mPackageGroups.size

    /** 全部包组。 */
    fun listPackageGroups(): Collection<ResPackageGroup> = mPackageGroups.values

    /** 解析包组：先库、再表内、最后 framework 懒加载。 */
    @Throws(AndrolibException::class)
    fun resolvePackageGroup(id: Int): ResPackageGroup {
        if (id != SYS_PACKAGE_ID && mainPackage != null) {
            val pkgGroup = loadLibraryById(id)
            if (pkgGroup != null) {
                return pkgGroup
            }
        }

        mPackageGroups[id]?.let { return it }

        return loadFrameworkById(id)
    }

    @Throws(AndrolibException::class)
    private fun loadLibraryById(id: Int): ResPackageGroup? {
        if (mLibPackageIds.contains(id)) {
            return mPackageGroups[id]
        }

        val main = mainPackage ?: return null
        val name: String = if (id == main.getId()) {
            main.getName()
        } else {
            mDynamicRefTable[id] ?: return null
        }

        val fileNames = mConfig.libraryFiles[name] ?: return null

        for (fileName in fileNames) {
            loadPackagesFromApk(File(fileName))
        }

        val pkgGroup = mPackageGroups[id]
            ?: throw AndrolibException(String.format("Library package not found: id=0x%02x", id))

        mLibPackageIds.add(id)
        return pkgGroup
    }

    @Throws(AndrolibException::class)
    private fun loadFrameworkById(id: Int): ResPackageGroup {
        if (mFramePackageIds.contains(id)) {
            return mPackageGroups[id]!!
        }

        loadPackagesFromApk(Framework(mConfig).getApkFile(id))

        val pkgGroup = mPackageGroups[id]
            ?: throw AndrolibException(String.format("Framework package not found: id=0x%02x", id))

        mFramePackageIds.add(id)
        return pkgGroup
    }

    @Throws(AndrolibException::class)
    private fun loadPackagesFromApk(apkFile: File) {
        Log.i(TAG, "Loading resource table from file: $apkFile")
        try {
            ZipRODirectory(apkFile).use { zipDir ->
                loadPackagesFromApk(apkFile, zipDir, false)
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException("Could not open apk file: $apkFile", ex)
        }
    }

    /** 由资源 ID 解析条目规格（跨包组）。 */
    @Throws(AndrolibException::class)
    fun resolve(resId: ResId): ResEntrySpec =
        resolvePackageGroup(resId.pkgId()).getEntrySpec(resId.typeId(), resId.entryId())

    /** 由资源 ID 解析条目（跨包组）。 */
    @Throws(AndrolibException::class)
    fun resolveEntry(resId: ResId): ResEntry =
        resolvePackageGroup(resId.pkgId()).getEntry(resId.typeId(), resId.entryId())

    /** 动态引用表中该 ID 对应的共享库包名（未定义返回 null 并告警）。 */
    fun getDynamicRefPackageName(id: Int): String? {
        val name = mDynamicRefTable[id]
        if (name == null) {
            Log.w(TAG, "Dynamic ref package name not defined for package ID: 0x%02x", id)
        }
        return name
    }

    /** 反查共享库包名对应的动态引用 ID；未定义返回 0。 */
    fun getDynamicRefPackageId(name: String): Int {
        for ((id, value) in mDynamicRefTable) {
            if (name == value) {
                return id
            }
        }
        Log.w(TAG, "Dynamic ref package ID not defined for package: $name")
        return 0
    }

    /** 登记共享库动态引用（ID/名字冲突时告警并保持首次映射）。 */
    fun addDynamicRefPackage(id: Int, name: String) {
        // 该 ID 不能指向另一个名字。
        val existing = mDynamicRefTable[id]
        if (existing != null) {
            if (existing != name) {
                Log.w(TAG, "Repeated dynamic ref package ID: %s (assigned to name: %s)", id, existing)
            }
            // 相同映射属正常，直接复用。
            return
        }

        // 该名字不能指向另一个 ID。
        for ((otherId, value) in mDynamicRefTable) {
            if (name == value) {
                Log.w(TAG, "Repeated dynamic ref package name: %s (assigned to ID: %s)", name, otherId)
                return
            }
        }

        mDynamicRefTable[id] = name
    }

    companion object {
        private val TAG = ResTable::class.java.name

        /** framework（android）包 ID。 */
        const val SYS_PACKAGE_ID: Int = 0x01

        /** 标准应用包 ID。 */
        const val APP_PACKAGE_ID: Int = 0x7F
    }
}
