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
package brut.androlib.smali

import brut.androlib.exceptions.AndrolibException
import brut.common.Log
import brut.directory.DirectoryException
import brut.directory.FileDirectory
import brut.util.OS
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.smali.SmaliOptions
import com.android.tools.smali.smali.assemble
import java.io.File
import java.io.IOException

/**
 * smali -> dex 汇编器：委托 smali 模块的 [assemble] 单遍前端，把整个目录装配成单个 dex 文件。
 *
 * #3641：opcode API 级别封顶 29（dex 版本 039），更高 API 由 aapt2/打包层处理。
 * 任一文件语法错误即中断整个目录构建。
 */
class SmaliBuilder(apiLevel: Int, jobs: Int) {
    // #3641 - opcode API 级别封顶 29（dex 版本最高 039）。
    private val mApiLevel: Int = minOf(apiLevel, 29)

    // 汇编并发度：与 apktool 的 -j 一致（至少 1）。
    private val mJobs: Int = maxOf(jobs, 1)

    /** 把 smali 目录汇编为单个 dex 文件。 */
    @Throws(AndrolibException::class)
    fun build(smaliDir: File, dexFile: File) {
        try {
            warnUnknownFiles(smaliDir)

            if (dexFile.exists()) {
                OS.rmfile(dexFile)
            } else {
                val parentDir = dexFile.parentFile
                if (parentDir != null) {
                    OS.mkdir(parentDir)
                }
            }

            val options = SmaliOptions().apply {
                // apiLevel <= 0 时退回默认 opcode 集合，保持旧行为。
                this.apiLevel = if (mApiLevel > 0) mApiLevel else Opcodes.default.api
                outputDexFile = dexFile.absolutePath
                this.jobs = mJobs
                verboseErrors = VERBOSE_ERRORS
                printTokens = PRINT_TOKENS
            }

            if (!assemble(options, smaliDir.absolutePath)) {
                throw AndrolibException("Could not smali folder: " + smaliDir.name)
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException("Could not smali folder: " + smaliDir.name, ex)
        } catch (ex: IOException) {
            throw AndrolibException("Could not smali folder: " + smaliDir.name, ex)
        } catch (ex: RuntimeException) {
            throw AndrolibException("Could not smali folder: " + smaliDir.name, ex)
        }
    }

    /** 对目录中非 .smali 文件保持与旧实现一致的告警。 */
    @Throws(DirectoryException::class)
    private fun warnUnknownFiles(smaliDir: File) {
        for (fileName in FileDirectory(smaliDir).getFiles(true)) {
            if (!fileName.endsWith(".smali")) {
                Log.w(TAG, "Unknown file type, ignoring: " + File(smaliDir, fileName))
            }
        }
    }

    companion object {
        private val TAG = SmaliBuilder::class.java.name

        private const val VERBOSE_ERRORS = false
        private const val PRINT_TOKENS = false
    }
}
