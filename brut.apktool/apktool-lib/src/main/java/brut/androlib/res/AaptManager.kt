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

import brut.androlib.exceptions.AndrolibException
import brut.common.BrutException
import brut.util.Jar
import brut.util.OS
import brut.util.OSDetection
import java.io.File
import java.io.IOException

/**
 * 内置 aapt2 二进制管理：按平台从 jar 中解压、赋可执行权限并识别版本。
 */
object AaptManager {
    /** 二进制名。 */
    @JvmStatic
    fun getBinaryName(): String = "aapt2"

    /** 释放当前平台的内置二进制为可执行临时文件。 */
    @JvmStatic
    @Throws(AndrolibException::class)
    fun getBinaryFile(): File {
        val binName = getBinaryName()

        if (!OSDetection.is64Bit()) {
            throw AndrolibException("$binName binaries are not available for 32-bit platforms.")
        }

        val binPath = StringBuilder("/prebuilt/")
        if (OSDetection.isUnix()) {
            binPath.append("linux") // ELF 64-bit LSB executable, x86-64
        } else if (OSDetection.isMacOSX()) {
            binPath.append("macosx") // x86_64 + arm64 胖二进制
        } else if (OSDetection.isWindows()) {
            binPath.append("windows") // x86_64
        } else {
            throw AndrolibException("Could not identify platform: " + OSDetection.returnOS())
        }
        binPath.append('/')
        binPath.append(binName)
        if (OSDetection.isWindows()) {
            binPath.append(".exe")
        }

        val binFile = try {
            Jar.getResourceAsFile(AaptManager::class.java, binPath.toString(), binName + "_")
        } catch (ex: BrutException) {
            throw AndrolibException(ex)
        }
        setBinaryExecutable(binFile)
        return binFile
    }

    @Throws(AndrolibException::class)
    private fun setBinaryExecutable(binFile: File) {
        if (!binFile.isFile || !binFile.canRead()) {
            throw AndrolibException("Could not read aapt binary: " + binFile.path)
        }
        if (!binFile.setExecutable(true)) {
            throw AndrolibException("Could not set aapt binary as executable: " + binFile.path)
        }
    }

    /** 运行二进制获取版本号（1/2）。 */
    @JvmStatic
    @Throws(AndrolibException::class)
    fun getBinaryVersion(binFile: File): Int {
        setBinaryExecutable(binFile)

        val versionStr = OS.execAndReturn(arrayOf(binFile.path, "version"))
            ?: throw AndrolibException("Could not execute aapt binary at location: " + binFile.path)

        return getVersionFromString(versionStr)
    }

    /** 从 version 输出文本识别 aapt 大版本。 */
    @JvmStatic
    @Throws(AndrolibException::class)
    fun getVersionFromString(versionStr: String): Int = when {
        versionStr.startsWith("Android Asset Packaging Tool (aapt) 2:") -> 2
        versionStr.startsWith("Android Asset Packaging Tool (aapt) 2.") -> 2 // Android SDK 26.0.2 之前
        versionStr.startsWith("Android Asset Packaging Tool, v0.") -> 1
        else -> throw AndrolibException("Could not identify aapt binary version: $versionStr")
    }
}
