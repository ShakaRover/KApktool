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

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 共享库（shared library）资源引用测试。
 *
 * 验证 library.apk 先解码重建后登记为共享库，client.apk 在引用该库资源时
 * 仍能完成解码与重建，并在 dist 目录产出对应 APK。
 */
class SharedLibraryTest : BaseTest() {

    /** 依次对 library.apk 与 client.apk 做解码/重建，校验产物存在。 */
    @Test
    @Throws(Exception::class)
    fun isSharedResourceDecodingAndRebuildingWorking() {
        // 解码 library.apk
        val libraryApk = File(sTmpDir!!, "library.apk")
        val libraryDir = File("$libraryApk.out")
        ApkDecoder(libraryApk, sConfig!!).decode(libraryDir)

        // 构建 library.apk
        ApkBuilder(libraryDir, sConfig!!).build(null)

        assertTrue(File(libraryDir, "dist/" + libraryApk.name).exists())

        // 把 library.apk 作为共享库登记进配置
        sConfig!!.libraryFiles.put(
            "com.google.android.test.shared_library", arrayOf(libraryApk.absolutePath)
        )

        // 解码 client.apk
        val clientApk = File(sTmpDir!!, "client.apk")
        val clientDir = File("$clientApk.out")
        ApkDecoder(clientApk, sConfig!!).decode(clientDir)

        // 构建 client.apk
        ApkBuilder(clientDir, sConfig!!).build(null)

        assertTrue(File(clientDir, "dist/" + clientApk.name).exists())
    }

    companion object {
        /** 类级初始化：把 shared_library 测试资源复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(SharedLibraryTest::class.java, "shared_library", sTmpDir!!)
        }
    }
}
