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

import brut.androlib.exceptions.AndrolibException
import brut.androlib.smali.SmaliDecoder
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 畸形 ZIP 的错误信息回归测试。
 *
 * 某些加固/恶意 APK 会把某个条目的中央目录压缩方法改成非法值，java.util.zip.ZipFile
 * 会在打开时直接拒绝整个包。此时 SmaliDecoder 必须抛出带说明的 AndrolibException，
 * 而不是把 ZipDexContainer.NotAZipFileException 这个无信息的 RuntimeException 直接透出。
 */
class MalformedZipTest {

    /** 中央目录压缩方法非法时，应得到可读的 AndrolibException。 */
    @Test
    @Throws(Exception::class)
    fun malformedCentralDirectoryReportsClearError() {
        val zip = File.createTempFile("malformed", ".apk")
        zip.deleteOnExit()
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(ByteArray(16))
            zos.closeEntry()
        }

        // 把中央目录里第一个条目的压缩方法改成非法值（同 issue #3953 的 0x6bf7）。
        val bytes = zip.readBytes()
        val cen = indexOf(bytes, byteArrayOf(0x50, 0x4b, 0x01, 0x02))
        assertTrue("central directory header not found", cen >= 0)
        bytes[cen + 10] = 0xf7.toByte()
        bytes[cen + 11] = 0x6b
        zip.writeBytes(bytes)

        try {
            SmaliDecoder(zip, false, 1)
            fail("expected AndrolibException for a zip with an invalid compression method")
        } catch (ex: AndrolibException) {
            assertTrue(
                "unexpected message: ${ex.message}",
                ex.message!!.startsWith("Could not open apk file:")
            )
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    continue@outer
                }
            }
            return i
        }
        return -1
    }
}
