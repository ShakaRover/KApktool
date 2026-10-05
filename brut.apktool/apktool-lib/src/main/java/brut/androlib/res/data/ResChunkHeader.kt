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
package brut.androlib.res.data

import brut.util.BinaryDataInputStream
import java.io.IOException

/**
 * RES chunk 通用头部（type/headerSize/size），并集中定义全部 chunk 类型常量。
 */
class ResChunkHeader(
    @JvmField val type: Int,
    @JvmField val headerSize: Int,
    @JvmField val size: Int,
) {
    companion object {
        /** 头部固定长度。 */
        const val SIZE = 8

        const val RES_NULL_TYPE = 0x0000
        const val RES_STRING_POOL_TYPE = 0x0001
        const val RES_TABLE_TYPE = 0x0002
        const val RES_XML_TYPE = 0x0003

        // RES_XML_TYPE 内部的 chunk 类型
        const val RES_XML_FIRST_CHUNK_TYPE = 0x0100
        const val RES_XML_START_NAMESPACE_TYPE = 0x0100
        const val RES_XML_END_NAMESPACE_TYPE = 0x0101
        const val RES_XML_START_ELEMENT_TYPE = 0x0102
        const val RES_XML_END_ELEMENT_TYPE = 0x0103
        const val RES_XML_CDATA_TYPE = 0x0104
        const val RES_XML_LAST_CHUNK_TYPE = 0x017F
        const val RES_XML_RESOURCE_MAP_TYPE = 0x0180

        // RES_TABLE_TYPE 内部的 chunk 类型
        const val RES_TABLE_PACKAGE_TYPE = 0x0200
        const val RES_TABLE_TYPE_TYPE = 0x0201
        const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
        const val RES_TABLE_LIBRARY_TYPE = 0x0203
        const val RES_TABLE_OVERLAYABLE_TYPE = 0x0204
        const val RES_TABLE_OVERLAYABLE_POLICY_TYPE = 0x0205
        const val RES_TABLE_STAGED_ALIAS_TYPE = 0x0206
        const val RES_TABLE_FLAGGED = 0x0207
        const val RES_TABLE_FLAG_LIST = 0x0208

        /** 从流中读取通用头部。 */
        @JvmStatic
        @Throws(IOException::class)
        fun `read`(`in`: BinaryDataInputStream): ResChunkHeader {
            val type = `in`.readUnsignedShort()
            val headerSize = `in`.readUnsignedShort()
            val size = `in`.readInt()
            return ResChunkHeader(type, headerSize, size)
        }

        /** chunk 类型的可读名称（未知类型输出十六进制）。 */
        @JvmStatic
        fun nameOf(type: Int): String = when (type) {
            RES_NULL_TYPE -> "RES_NULL_TYPE"
            RES_STRING_POOL_TYPE -> "RES_STRING_POOL_TYPE"
            RES_TABLE_TYPE -> "RES_TABLE_TYPE"
            RES_XML_TYPE -> "RES_XML_TYPE"
            RES_XML_START_NAMESPACE_TYPE -> "RES_XML_START_NAMESPACE_TYPE"
            RES_XML_END_NAMESPACE_TYPE -> "RES_XML_END_NAMESPACE_TYPE"
            RES_XML_START_ELEMENT_TYPE -> "RES_XML_START_ELEMENT_TYPE"
            RES_XML_END_ELEMENT_TYPE -> "RES_XML_END_ELEMENT_TYPE"
            RES_XML_CDATA_TYPE -> "RES_XML_CDATA_TYPE"
            RES_XML_RESOURCE_MAP_TYPE -> "RES_XML_RESOURCE_MAP_TYPE"
            RES_TABLE_PACKAGE_TYPE -> "RES_TABLE_PACKAGE_TYPE"
            RES_TABLE_TYPE_TYPE -> "RES_TABLE_TYPE_TYPE"
            RES_TABLE_TYPE_SPEC_TYPE -> "RES_TABLE_TYPE_SPEC_TYPE"
            RES_TABLE_LIBRARY_TYPE -> "RES_TABLE_LIBRARY_TYPE"
            RES_TABLE_OVERLAYABLE_TYPE -> "RES_TABLE_OVERLAYABLE_TYPE"
            RES_TABLE_OVERLAYABLE_POLICY_TYPE -> "RES_TABLE_OVERLAYABLE_POLICY_TYPE"
            RES_TABLE_STAGED_ALIAS_TYPE -> "RES_TABLE_STAGED_ALIAS_TYPE"
            RES_TABLE_FLAGGED -> "RES_TABLE_FLAGGED"
            RES_TABLE_FLAG_LIST -> "RES_TABLE_FLAG_LIST"
            else -> String.format("0x%04x", type)
        }
    }
}
