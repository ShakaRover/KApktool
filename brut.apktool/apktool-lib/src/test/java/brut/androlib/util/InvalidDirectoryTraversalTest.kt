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
package brut.androlib.util

import brut.androlib.BaseTest
import brut.util.BrutIO
import brut.util.OSDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.InvalidPathException

/**
 * BrutIO.sanitizePath 的路径穿越防护测试：
 * 校验合法文件名/子目录名保持原样，而 ../ 相对穿越、绝对根路径、空串等非法输入抛出
 * InvalidPathException。
 */
class InvalidDirectoryTraversalTest : BaseTest() {

    /** 合法文件名应原样返回，并且对应文件确实存在于临时目录中。 */
    @Test
    @Throws(Exception::class)
    fun validFileTest() {
        val validFileName = BrutIO.sanitizePath(sTmpDir!!, "file")
        assertEquals(validFileName, "file")
        assertTrue(File(sTmpDir!!, validFileName).isFile())
    }

    /** 反向相对路径（../file）应被判定为非法。 */
    @Test(expected = InvalidPathException::class)
    @Throws(Exception::class)
    fun invalidBackwardFileTest() {
        BrutIO.sanitizePath(sTmpDir!!, "../file")
    }

    /** 绝对根路径（Windows 为 C:/，其他系统为分隔符）应被判定为非法。 */
    @Test(expected = InvalidPathException::class)
    @Throws(Exception::class)
    fun invalidRootFileTest() {
        val rootLocation = if (OSDetection.isWindows()) "C:/" else File.separator
        BrutIO.sanitizePath(sTmpDir!!, rootLocation + "file")
    }

    /** 空文件名字符串应被判定为非法。 */
    @Test(expected = InvalidPathException::class)
    @Throws(Exception::class)
    fun noFilePassedTest() {
        BrutIO.sanitizePath(sTmpDir!!, "")
    }

    /** 多级反向穿越路径（Windows 反斜杠形式与 POSIX 形式）应被判定为非法。 */
    @Test(expected = InvalidPathException::class)
    @Throws(Exception::class)
    fun invalidBackwardPathOnWindows() {
        val invalidPath = if (OSDetection.isWindows()) "..\\..\\app.exe" else "../../app"
        BrutIO.sanitizePath(sTmpDir!!, invalidPath)
    }

    /** 位于子目录内的合法相对路径应原样返回。 */
    @Test
    @Throws(Exception::class)
    fun validDirectoryFileTest() {
        val fileName = "dir" + File.separator + "file"
        val validFileName = BrutIO.sanitizePath(sTmpDir!!, fileName)
        assertEquals(fileName, validFileName)
    }

    companion object {
        /** 类级初始化：把资源目录 util/traversal 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(InvalidDirectoryTraversalTest::class.java, "util/traversal", sTmpDir!!)
        }
    }
}
