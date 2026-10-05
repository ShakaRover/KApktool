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

import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * dex 静态字段初值测试（issue2543）：关闭 baksmali 调试模式后构建 jar 再解码，
 * 验证布尔型静态字段 b/c 的默认参数值（false/true）在 smali 输出中正确保留。
 */
class DexStaticFieldValueTest : BaseTest() {

    /** 逐行比对解码出的 HelloWorld.smali 与预期文本（忽略换行差异）。 */
    @Test
    @Throws(Exception::class)
    fun disassembleDexFileToKeepDefaultParameters() {
        val expected =
            ".class public LHelloWorld;\n" +
            ".super Ljava/lang/Object;\n" +
            "\n" +
            "\n" +
            "# static fields\n" +
            ".field private static b:Z = false\n" +
            "\n" +
            ".field private static c:Z = true\n" +
            "\n" +
            "\n" +
            "# direct methods\n" +
            ".method public static main([Ljava/lang/String;)V\n" +
            "    .locals 1\n" +
            "\n" +
            "    return-void\n" +
            ".end method"

        val obtained = readTextFile(File(sTestNewDir!!, "smali/HelloWorld.smali"))

        assertEquals(replaceNewlines(expected), replaceNewlines(obtained))
    }

    companion object {
        /** 类级初始化：解包 issue2543 工程，关闭 baksmali 调试模式后构建为 jar 再解码。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            sTestOrigDir = File(sTmpDir!!, "issue2543-orig")
            sTestNewDir = File(sTmpDir!!, "issue2543-new")

            log("Unpacking issue2543...")
            copyResourceDir(DexStaticFieldValueTest::class.java, "issue2543", sTestOrigDir!!)

            sConfig!!.isBaksmaliDebugMode = false

            log("Building issue2543.jar...")
            val testJar = File(sTmpDir!!, "issue2543.jar")
            ApkBuilder(sTestOrigDir!!, sConfig!!).build(testJar)

            log("Decoding issue2543.jar...")
            ApkDecoder(testJar, sConfig!!).decode(sTestNewDir!!)
        }
    }
}
