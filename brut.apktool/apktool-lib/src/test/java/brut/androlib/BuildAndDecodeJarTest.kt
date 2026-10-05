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
 * jar 工程（无 AndroidManifest 资源的普通目录包）构建与解码回归测试。
 *
 * 验证由 testjar 资源目录构建出 testjar.jar 后，再次解码可以正常产出工程目录。
 */
class BuildAndDecodeJarTest : BaseTest() {

    /** 验证解码 testjar.jar 后输出目录确实存在。 */
    @Test
    fun buildAndDecodeTest() {
        assertTrue(sTestNewDir!!.isDirectory)
    }

    companion object {
        /**
         * 类级初始化：解包 testjar 资源目录，构建 testjar.jar，
         * 再把该 jar 解码到新目录。
         */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            sTestOrigDir = File(sTmpDir!!, "testjar-orig")
            sTestNewDir = File(sTmpDir!!, "testjar-new")

            log("Unpacking testjar...")
            copyResourceDir(
                BuildAndDecodeJarTest::class.java, "testjar", sTestOrigDir!!
            )

            log("Building testjar.jar...")
            val testJar = File(sTmpDir!!, "testjar.jar")
            ApkBuilder(sTestOrigDir!!, sConfig!!).build(testJar)

            log("Decoding testjar.jar...")
            ApkDecoder(testJar, sConfig!!).decode(sTestNewDir!!)
        }
    }
}
