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
package brut.util

/**
 * 运行平台探测工具：基于 `os.name` 与 `sun.arch.data.model` 判断操作系统与位宽。
 *
 * Windows 上的 64 位判断优先读取 `PROCESSOR_ARCHITECTURE` / `PROCESSOR_ARCHITEW6432`
 * 环境变量，以兼容 32 位进程运行在 64 位系统（WoW64）的情况。
 */
object OSDetection {
    private val OS: String = System.getProperty("os.name").lowercase()
    private val BIT: String = System.getProperty("sun.arch.data.model").lowercase()

    /** 当前系统是否为 Windows。 */
    @JvmStatic
    fun isWindows(): Boolean = OS.contains("win")

    /** 当前系统是否为 macOS。 */
    @JvmStatic
    fun isMacOSX(): Boolean = OS.contains("mac")

    /** 当前系统是否为类 Unix（Linux、*nix、BSD、AIX、SunOS）。 */
    @JvmStatic
    fun isUnix(): Boolean =
        OS.contains("nix") || OS.contains("nux") || OS.contains("aix") || OS.contains("sunos")

    /** 当前进程是否运行在 64 位环境。 */
    @JvmStatic
    fun is64Bit(): Boolean {
        if (isWindows()) {
            val arch = System.getenv("PROCESSOR_ARCHITECTURE")
            val wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432")
            return (arch != null && arch.endsWith("64")) ||
                (wow64Arch != null && wow64Arch.endsWith("64"))
        }
        return BIT == "64"
    }

    /** 返回已转小写的系统名称字符串。 */
    @JvmStatic
    fun returnOS(): String = OS
}
