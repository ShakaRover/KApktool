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
import brut.androlib.res.table.ResTable
import brut.common.BrutException
import brut.common.Log
import brut.util.OS
import java.io.File

/**
 * aapt2 调用器：compile（资源目录 -> resources.zip）+ link（链接出最终 APK/manifest）。
 *
 * 所有工程侧元数据（SDK/版本/包 ID/稀疏编码/特性标志等）都翻译成 aapt2 命令行参数；
 * 内置二进制缺失时回退 $PATH 中的 aapt2。
 */
class AaptInvoker(
    private val mApkInfo: ApkInfo,
    private val mConfig: Config,
) {
    /** 执行 aapt2 compile + link。 */
    @Throws(AndrolibException::class)
    fun invoke(outApk: File, manifest: File?, resDir: File?) {
        val sdkInfo = mApkInfo.sdkInfo
        val versionInfo = mApkInfo.versionInfo
        val resourcesInfo = mApkInfo.resourcesInfo

        var aaptPathCfg = mConfig.aaptBinary
        if (aaptPathCfg.isNullOrEmpty()) {
            aaptPathCfg = try {
                AaptManager.getBinaryFile().path
            } catch (ex: AndrolibException) {
                val name = AaptManager.getBinaryName()
                Log.w(TAG, name + ": " + ex.message + " (defaulting to \$PATH binary)")
                name
            }
        }
        val aaptPath: String = aaptPathCfg

        val cmd = ArrayList<String>()
        var resZip: File? = null

        if (resDir != null) {
            resZip = File(resDir.parent, "build/resources.zip")
            OS.rmfile(resZip)

            // 先把资源目录编译为扁平 arsc 包。
            cmd.add(aaptPath)
            cmd.add("compile")

            if (mConfig.isVerbose) {
                cmd.add("-v")
            }

            cmd.add("-o")
            cmd.add(resZip.path)

            cmd.add("--dir")
            cmd.add(resDir.path)

            // 把 aapt1 时代合法的错误在 aapt2 里降级为 warning。
            cmd.add("--legacy")

            if (mConfig.isNoCrunch) {
                cmd.add("--no-crunch")
            }
            if (mApkInfo.featureFlags.isNotEmpty()) {
                val featureFlags = ArrayList<String>()
                for (flag in mApkInfo.featureFlags) {
                    featureFlags.add("$flag=true")
                }
                cmd.add("--feature-flags")
                cmd.add(featureFlags.joinToString(","))
            }

            try {
                OS.exec(cmd.toTypedArray())
                Log.d(TAG, "aapt2 compile command ran: $cmd")
            } catch (ex: BrutException) {
                throw AndrolibException(ex)
            }

            cmd.clear()
        }

        if (manifest == null) {
            return
        }

        // 链接资源生成最终 APK。
        cmd.add(aaptPath)
        cmd.add("link")

        if (mConfig.isVerbose) {
            cmd.add("-v")
        }

        cmd.add("-o")
        cmd.add(outApk.path)

        cmd.add("--manifest")
        cmd.add(manifest.path)

        sdkInfo.minSdkVersion?.let {
            cmd.add("--min-sdk-version")
            cmd.add(it)
        }
        sdkInfo.targetSdkVersion?.let {
            cmd.add("--target-sdk-version")
            cmd.add(it)
        }
        if (versionInfo.getVersionCode() >= 0) {
            cmd.add("--version-code")
            cmd.add(versionInfo.getVersionCode().toString())
        }
        versionInfo.versionName?.let {
            cmd.add("--version-name")
            cmd.add(it)
        }
        if (resourcesInfo.getPackageId() >= 0) {
            val pkgId = resourcesInfo.getPackageId()
            if (pkgId == 0) {
                cmd.add("--shared-lib")
            } else if (pkgId > ResTable.SYS_PACKAGE_ID) {
                cmd.add("--package-id")
                cmd.add(pkgId.toString())
                if (pkgId < ResTable.APP_PACKAGE_ID) {
                    cmd.add("--allow-reserved-package-id")
                }
            }
        }
        // 空包名会传给 aapt2 并导致其 abort（all packages being linked must have a name），
        // 同时兼容旧 apktool.yml 中持久化的 packageName: ''。
        resourcesInfo.packageName?.takeIf { it.isNotEmpty() }?.let {
            cmd.add("--rename-resources-package")
            cmd.add(it)
        }
        if (resourcesInfo.isSparseEntries()) {
            cmd.add("--enable-sparse-encoding")
        }
        if (resourcesInfo.isCompactEntries()) {
            cmd.add("--enable-compact-entries")
        }
        if (resourcesInfo.isKeepRawValues()) {
            cmd.add("--keep-raw-values")
        }
        if (mApkInfo.featureFlags.isNotEmpty()) {
            val featureFlags = ArrayList<String>()
            for (flag in mApkInfo.featureFlags) {
                featureFlags.add("$flag=true")
            }
            cmd.add("--feature-flags")
            cmd.add(featureFlags.joinToString(","))
        }

        // 关闭 aapt2 的自动改写行为。
        cmd.add("--no-auto-version")
        cmd.add("--no-version-vectors")
        cmd.add("--no-version-transitions")
        cmd.add("--no-resource-deduping")
        cmd.add("--no-compile-sdk-metadata")

        // #3427 - 忽略 aapt2 更严格的 manifest 解析。
        cmd.add("--warn-manifest-validation")

        for (includeFile in getIncludeFiles()) {
            cmd.add("-I")
            cmd.add(includeFile.path)
        }
        resZip?.let { cmd.add(it.path) }

        try {
            OS.exec(cmd.toTypedArray())
            Log.d(TAG, "aapt2 link command ran: $cmd")
        } catch (ex: BrutException) {
            throw AndrolibException(ex)
        }
    }

    /** 收集 -I 参数：framework 包与已提供的共享库。 */
    @Throws(AndrolibException::class)
    private fun getIncludeFiles(): List<File> {
        val files = ArrayList<File>()

        val usesFramework = mApkInfo.usesFramework
        val frameworkIds = usesFramework.ids
        if (frameworkIds.isNotEmpty()) {
            val framework = Framework(mConfig)
            val tag = usesFramework.tag
            for (id in frameworkIds) {
                files.add(framework.getApkFile(id, tag))
            }
        }

        val usesLibrary = mApkInfo.usesLibrary
        if (usesLibrary.isNotEmpty()) {
            val libraryFiles = mConfig.libraryFiles
            for (name in usesLibrary) {
                val fileNames = if (name != null) libraryFiles[name] else null
                if (fileNames != null) {
                    for (fileName in fileNames) {
                        files.add(File(fileName))
                    }
                } else {
                    Log.w(TAG, "Shared library was not provided: $name")
                }
            }
        }

        return files
    }

    companion object {
        private val TAG = AaptInvoker::class.java.name
    }
}
