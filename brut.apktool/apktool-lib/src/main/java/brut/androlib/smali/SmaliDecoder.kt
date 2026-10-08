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
import brut.util.OS
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.disassembleDexFile
import com.android.tools.smali.dexlib2.analysis.InlineMethodResolver
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.DexBackedOdexFile
import com.android.tools.smali.dexlib2.dexbacked.ZipDexContainer
import java.io.File
import java.io.IOException
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * dex -> smali 反汇编器（baksmali 封装）。
 *
 * 一个 APK 可含多个 dex；解码某个 dex 时自动把同容器的其余 dex 一并载入以获得完整上下文，
 * 各 dex 反汇编到独立 smali[_name[N]] 目录；
 * [mInferredApiLevel] 记录所有已解码 dex 中最小的 opcode API 级别。
 * odex（含优化 opcode）拒绝直接反汇编。
 */
class SmaliDecoder(
    apkFile: File,
    private val mDebugMode: Boolean,
    jobs: Int,
) {
    private val mDexContainer: ZipDexContainer = ZipDexContainer(apkFile, null)
    private val mDexFiles: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mInferredApiLevel = AtomicInteger()

    // 单个 dex 内部的类级并发度：与 apktool 的 -j 一致（至少 1）。
    private val mJobs: Int = maxOf(jobs, 1)

    init {
        // ZipDexContainer 懒初始化且非线程安全：在构造线程上先行触发初始化。
        //
        // 注意：ZipFile 打开失败时抛的是 ZipDexContainer.NotAZipFileException（RuntimeException，
        // 而非 IOException）。加固/畸形 APK（例如某条目压缩方法非法）会让 java.util.zip.ZipFile
        // 拒绝整个包，若不捕获就会向上抛出无信息的裸异常，用户无法判断是包本身有问题。
        try {
            mDexContainer.getEntry("")
        } catch (ex: IOException) {
            throw AndrolibException("Could not open apk file: $apkFile", ex)
        } catch (ex: ZipDexContainer.NotAZipFileException) {
            throw AndrolibException("Could not open apk file: $apkFile", ex)
        }
    }

    /** 已解码的 dex 条目名集合。 */
    val dexFiles: MutableSet<String>
        get() = mDexFiles

    /** 推断出的最小 API 级别（0 表示未解码）。 */
    val inferredApiLevel: Int
        get() = mInferredApiLevel.get()

    /** 反汇编指定 dex（连同多 dex 上下文）到输出目录。 */
    @Throws(AndrolibException::class)
    fun decode(dexName: String, outDir: File) {
        try {
            // 取出请求的 dex。
            val dexEntry = mDexContainer.getEntry(dexName)

            // 请求的 dex 编号为 1。
            val dexFiles = TreeMap<Int, DexBackedDexFile>()

            val prefix = "$dexName/"
            if (dexEntry != null) {
                dexFiles[1] = dexEntry.dexFile

                // 多 dex 容器时补充其余 dex。
                for (dexEntryName in mDexContainer.dexEntryNames) {
                    if (dexEntryName == dexName) {
                        continue
                    }

                    if (!dexEntryName.startsWith(prefix)) {
                        continue
                    }

                    val dexNum = try {
                        dexEntryName.substring(prefix.length).toInt()
                    } catch (ignored: NumberFormatException) {
                        continue
                    }
                    if (dexNum > 1) {
                        dexFiles[dexNum] = mDexContainer.getEntry(dexEntryName)!!.dexFile
                    }
                }
            } else {
                // Android 16 引入的 DEX 容器：单个 zip 条目（如 classes.dex）内拼接了多个 dex，
                // 因此不存在名为 dexName 的条目。ksmali 的 ZipDexContainer 将其命名为
                // "<条目名>/0"、"<条目名>/1" ...（0 基）。按 N 升序收集并映射为
                // dexFiles[1]、dexFiles[2] ...，与 classes.dex 编号为 1 的约定保持一致。
                for (dexEntryName in mDexContainer.dexEntryNames) {
                    if (!dexEntryName.startsWith(prefix)) {
                        continue
                    }

                    val index = dexEntryName.substring(prefix.length).toIntOrNull() ?: continue
                    val containerEntry = mDexContainer.getEntry(dexEntryName) ?: continue
                    dexFiles[index + 1] = containerEntry.dexFile
                }

                if (dexFiles.isEmpty()) {
                    throw AndrolibException("Could not find file: $dexName")
                }
            }

            // 每个 dex 反汇编到独立目录。
            for ((dexNum, dexFile) in dexFiles) {
                if (dexFile.supportsOptimizedOpcodes()) {
                    throw AndrolibException("Cannot disassemble an odex file without deodexing it: $dexName")
                }

                var dirName = "smali"
                if (dexNum > 1 || dexName != "classes.dex") {
                    dirName += "_" + dexName.substring(0, dexName.lastIndexOf('.')).replace('/', '@')
                    if (dexNum > 1) {
                        dirName += dexNum
                    }
                }

                decodeFile(dexFile, File(outDir, dirName))
            }

            mDexFiles.add(dexName)
        } catch (ex: IOException) {
            throw AndrolibException("Could not baksmali file: $dexName", ex)
        }
    }

    /** 执行 baksmali 反汇编（并发度取自 -j）。 */
    private fun decodeFile(dexFile: DexBackedDexFile, smaliDir: File) {
        val jobs = mJobs

        val options = BaksmaliOptions().apply {
            parameterRegisters = true
            localsDirective = true
            sequentialLabels = true
            debugInfo = mDebugMode
            codeOffsets = false
            accessorComments = false
            allowOdex = false
            deodex = false
            implicitReferences = false
            normalizeVirtualMethods = false
            registerInfo = 0

            if (dexFile is DexBackedOdexFile) {
                inlineResolver = InlineMethodResolver.createInlineMethodResolver(dexFile.odexVersion)
            }
        }

        OS.mkdir(smaliDir)
        disassembleDexFile(dexFile, smaliDir, jobs, options)

        val apiLevel = dexFile.opcodes.api
        mInferredApiLevel.updateAndGet { cur -> if (cur == 0 || cur > apiLevel) apiLevel else cur }
    }
}
