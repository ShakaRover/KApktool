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

import brut.androlib.smali.SmaliDecoder
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * DEX 容器（Android 16 / issue3910）回归测试。
 *
 * 构造一个 zip 条目 classes.dex，其负载是两个拼接的 dex，并写入容器头字段
 * （container_size / header_offset），验证 SmaliDecoder 能把容器内的多个 dex
 * 分别反汇编到 smali/、smali_classes2/ 目录。
 */
class DexContainerTest {

    /** 解码 DEX 容器后，两个子 dex 应分别落到 smali/ 与 smali_classes2/。 */
    @Test
    @Throws(Exception::class)
    fun decodeDexContainer() {
        val dexA = writeDex("Lorg/test/A;", 0)
        val dexB = writeDex("Lorg/test/B;", dexA.size)

        // 拼接两个 dex，并按容器格式写头部字段。
        val container = ByteArray(dexA.size + dexB.size)
        System.arraycopy(dexA, 0, container, 0, dexA.size)
        System.arraycopy(dexB, 0, container, dexA.size, dexB.size)
        for (headerOffset in intArrayOf(0, dexA.size)) {
            writeInt(container, headerOffset + HeaderItem.CONTAINER_SIZE_OFFSET, container.size)
            writeInt(container, headerOffset + HeaderItem.HEADER_OFFSET_OFFSET, headerOffset)
        }

        val jar = File.createTempFile("dex_container", ".jar")
        jar.deleteOnExit()
        ZipOutputStream(FileOutputStream(jar)).use { zos ->
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(container)
            zos.closeEntry()
        }

        val outDir = File.createTempFile("dex_container_out", ".dir")
        outDir.delete()
        outDir.mkdirs()
        outDir.deleteOnExit()

        SmaliDecoder(jar, false, 1).decode("classes.dex", outDir)

        assertTrue(File(outDir, "smali/org/test/A.smali").isFile)
        assertTrue(File(outDir, "smali_classes2/org/test/B.smali").isFile)
    }

    companion object {
        /** 旧版（无容器字段）dex 头部大小为 112；v041 头部为 120。 */
        private const val LEGACY_HEADER_SIZE = 112

        /**
         * 用 dexlib2 生成一个只含单个类的合法 v041 dex，并定位到容器偏移 [baseOffset]。
         *
         * dexlib2 的 DexWriter 只会写 112 字节头部，因此这里在头部末尾插入 8 字节，
         * 并把头部/string_ids/map_list 中所有指向该区间的偏移统一后移 8 字节。
         * 容器内的 dex 偏移是相对容器起点的（见 DexBackedDexFile 直接使用这些偏移），
         * 因此还要再加上 [baseOffset]。
         * 仅适用于本测试所用的「无字段、无方法、无注解」的最小类。
         */
        private fun writeDex(className: String, baseOffset: Int): ByteArray {
            val classDef = ImmutableClassDef(
                className, 0, "Ljava/lang/Object;", null, null, setOf(), null, null
            )
            val dataStore = MemoryDataStore()
            DexPool.writeTo(dataStore, ImmutableDexFile(Opcodes.default, setOf(classDef)))
            return toDexV041(dataStore.data, baseOffset)
        }

        /** 在 112 字节头部后插入 8 字节，生成 v041 头部并重定位到容器偏移。 */
        private fun toDexV041(dex: ByteArray, baseOffset: Int): ByteArray {
            val out = ByteArray(dex.size + 8)
            System.arraycopy(dex, 0, out, 0, LEGACY_HEADER_SIZE)
            System.arraycopy(
                dex, LEGACY_HEADER_SIZE, out, LEGACY_HEADER_SIZE + 8,
                dex.size - LEGACY_HEADER_SIZE
            )

            // 版本号改为 041。
            out[4] = '0'.code.toByte()
            out[5] = '4'.code.toByte()
            out[6] = '1'.code.toByte()

            // file_size 只统计本 dex 自身，不含容器定位偏移。
            writeInt(out, HeaderItem.FILE_SIZE_OFFSET, readInt(out, HeaderItem.FILE_SIZE_OFFSET) + 8)

            // 先取插入 8 字节后的本地位置（用于在 out 内定位各段）。
            val stringCount = readInt(out, HeaderItem.STRING_COUNT_OFFSET)
            val stringStart = localOffset(readInt(out, HeaderItem.STRING_START_OFFSET))
            val mapOffset = localOffset(readInt(out, HeaderItem.MAP_OFFSET))

            // 头部中的各段起始偏移：先补偿插入的 8 字节，再加上容器偏移。
            // 0 表示该段不存在，保持不变。
            for (off in intArrayOf(
                HeaderItem.MAP_OFFSET, HeaderItem.STRING_START_OFFSET,
                HeaderItem.TYPE_START_OFFSET, HeaderItem.PROTO_START_OFFSET,
                HeaderItem.FIELD_START_OFFSET, HeaderItem.METHOD_START_OFFSET,
                HeaderItem.CLASS_START_OFFSET, HeaderItem.DATA_START_OFFSET
            )) {
                val value = readInt(out, off)
                if (value >= LEGACY_HEADER_SIZE) {
                    writeInt(out, off, value + 8 + baseOffset)
                }
            }

            // string_ids 中指向 string_data 的偏移。
            for (i in 0 until stringCount) {
                val p = stringStart + i * 4
                writeInt(out, p, readInt(out, p) + 8 + baseOffset)
            }

            // map_list 中每个段偏移。每项布局：ushort type、ushort unused、uint size、uint offset。
            val mapSize = readInt(out, mapOffset)
            for (i in 0 until mapSize) {
                val p = mapOffset + 4 + i * 12 + 8
                val value = readInt(out, p)
                if (value >= LEGACY_HEADER_SIZE) {
                    writeInt(out, p, value + 8 + baseOffset)
                }
            }

            return out
        }

        /** 把旧 dex 中的偏移换算为插入 8 字节后的本地位置（0 保持不变）。 */
        private fun localOffset(value: Int): Int {
            return if (value >= LEGACY_HEADER_SIZE) value + 8 else value
        }

        /** 小端读取 32 位整数（与 dex 头部字节序一致）。 */
        private fun readInt(buf: ByteArray, offset: Int): Int {
            return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8) or
                ((buf[offset + 2].toInt() and 0xFF) shl 16) or
                ((buf[offset + 3].toInt() and 0xFF) shl 24)
        }

        /** 小端写入 32 位整数（与 dex 头部字节序一致）。 */
        private fun writeInt(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = value.toByte()
            buf[offset + 1] = (value shr 8).toByte()
            buf[offset + 2] = (value shr 16).toByte()
            buf[offset + 3] = (value shr 24).toByte()
        }
    }
}
