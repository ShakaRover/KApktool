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
package brut.androlib

import brut.androlib.res.Framework
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * framework 资源包安装测试。
 *
 * 验证把 framework.apk 安装进临时 framework 目录时，
 * 生成的文件名符合“包ID[-tag].apk”规则。
 */
class FrameworkTest : BaseTest() {

    /** 验证设置 frameworkTag 后安装产出的文件带 tag 后缀。 */
    @Test
    @Throws(Exception::class)
    fun isFrameworkTaggingWorking() {
        sConfig!!.frameworkDirectory = sTmpDir!!.absolutePath
        sConfig!!.frameworkTag = "building"

        val frameApk = File(sTmpDir!!, FRAMEWORK_APK)
        Framework(sConfig!!).install(frameApk)

        assertTrue(File(sTmpDir!!, "2-building.apk").exists())
    }

    /** 验证未设置 tag 时安装产出的文件为无 tag 的默认命名。 */
    @Test
    @Throws(Exception::class)
    fun isFrameworkInstallingWorking() {
        sConfig!!.frameworkDirectory = sTmpDir!!.absolutePath

        val frameApk = File(sTmpDir!!, FRAMEWORK_APK)
        Framework(sConfig!!).install(frameApk)

        assertTrue(File(sTmpDir!!, "2.apk").exists())
    }

    companion object {
        // 测试用 framework APK 文件名。
        private const val FRAMEWORK_APK = "framework.apk"

        /** 类级初始化：把 framework 测试资源复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(FrameworkTest::class.java, "framework", sTmpDir!!)
        }
    }
}
