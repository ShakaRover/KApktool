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
 * baksmali 默认参数测试（issue1481）：以默认配置构建 jar 再解码，
 * 验证 BuildConfig.smali 的反汇编输出与预期完全一致
 * （即 baksmali 调用参数未被意外改动）。
 */
class DefaultBaksmaliVariableTest : BaseTest() {

    /** 逐行比对解码出的 BuildConfig.smali 与预期文本（忽略换行差异）。 */
    @Test
    @Throws(Exception::class)
    fun confirmBaksmaliParamsAreTheSame() {
        val expected =
            ".class public final Lcom/ibotpeaches/issue1481/BuildConfig;\n" +
            ".super Ljava/lang/Object;\n" +
            ".source \"BuildConfig.java\"\n" +
            "\n" +
            "\n" +
            "# static fields\n" +
            ".field public static final APPLICATION_ID:Ljava/lang/String; = \"com.ibotpeaches.issue1481\"\n" +
            "\n" +
            ".field public static final BUILD_TYPE:Ljava/lang/String; = \"debug\"\n" +
            "\n" +
            ".field public static final DEBUG:Z\n" +
            "\n" +
            ".field public static final FLAVOR:Ljava/lang/String; = \"\"\n" +
            "\n" +
            ".field public static final VERSION_CODE:I = 0x1\n" +
            "\n" +
            ".field public static final VERSION_NAME:Ljava/lang/String; = \"1.0\"\n" +
            "\n" +
            "\n" +
            "# direct methods\n" +
            ".method static constructor <clinit>()V\n" +
            "    .locals 1\n" +
            "\n" +
            "    .prologue\n" +
            "    .line 7\n" +
            "    const-string v0, \"true\"\n" +
            "\n" +
            "    invoke-static {v0}, Ljava/lang/Boolean;->parseBoolean(Ljava/lang/String;)Z\n" +
            "\n" +
            "    move-result v0\n" +
            "\n" +
            "    sput-boolean v0, Lcom/ibotpeaches/issue1481/BuildConfig;->DEBUG:Z\n" +
            "\n" +
            "    return-void\n" +
            ".end method\n" +
            "\n" +
            ".method public constructor <init>()V\n" +
            "    .locals 0\n" +
            "\n" +
            "    .prologue\n" +
            "    .line 6\n" +
            "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
            "\n" +
            "    return-void\n" +
            ".end method"

        val obtained = readTextFile(File(sTestNewDir!!, "smali/com/ibotpeaches/issue1481/BuildConfig.smali"))

        assertEquals(replaceNewlines(expected), replaceNewlines(obtained))
    }

    companion object {
        /** 类级初始化：解包 issue1481 工程，构建为 jar 后再解码。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            sTestOrigDir = File(sTmpDir!!, "issue1481-orig")
            sTestNewDir = File(sTmpDir!!, "issue1481-new")

            log("Unpacking issue1481...")
            copyResourceDir(DefaultBaksmaliVariableTest::class.java, "issue1481", sTestOrigDir!!)

            log("Building issue1481.jar...")
            val testJar = File(sTmpDir!!, "issue1481.jar")
            ApkBuilder(sTestOrigDir!!, sConfig!!).build(testJar)

            log("Decoding issue1481.jar...")
            ApkDecoder(testJar, sConfig!!).decode(sTestNewDir!!)
        }
    }
}
