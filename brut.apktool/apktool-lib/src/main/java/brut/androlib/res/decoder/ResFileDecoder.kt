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
package brut.androlib.res.decoder

import brut.androlib.exceptions.AndrolibException
import brut.androlib.exceptions.NinePatchNotFoundException
import brut.androlib.exceptions.RawXmlEncounteredException
import brut.androlib.res.table.ResEntry
import brut.androlib.res.table.value.ResFileReference
import brut.androlib.res.table.value.ResPrimitive
import brut.androlib.res.table.value.ResString
import brut.common.Log
import brut.directory.Directory
import brut.directory.DirectoryException
import brut.util.BrutIO
import java.io.IOException

/**
 * 文件型资源条目解码调度器。
 *
 * 按 aapt2 规则由扩展名选择解码器（xml/xsd→二进制 XML，9.png→九宫格，其余直通）；
 * 解码失败分级降级：非二进制 XML / 非九宫格 PNG 按原样拷贝，彻底失败则条目置为 NULL。
 * 同时登记输入路径到输出路径的映射（resFileMap）。
 */
class ResFileDecoder(
    private val mDecoders: Map<Type, ResStreamDecoder>,
) {
    /** 解码器路由类型。 */
    enum class Type {
        /** 未知（直通拷贝）。 */
        UNKNOWN,

        /** 二进制 XML。 */
        BINARY_XML,

        /** 九宫格 PNG。 */
        PNG_9PATCH,
    }

    /** 解码单个文件资源条目到输出目录。 */
    fun decode(entry: ResEntry, inDir: Directory, outDir: Directory, resFileMap: MutableMap<String, String>) {
        var inFileName = (entry.value as ResFileReference).path

        // 某些应用把字符串当文件引用存；找不到文件时回退为字符串值。
        if (!inDir.containsFile(inFileName)) {
            entry.value = ResString(inFileName)
            return
        }

        // 取输入文件扩展名。
        var ext = if (inFileName.endsWith(".9.png")) {
            "9.png"
        } else {
            BrutIO.getExtension(inFileName).lowercase()
        }

        // 用 aapt2 式规则决定解码器。
        // TODO: 用魔数判定并补全被剥离的扩展名。
        var type = Type.UNKNOWN
        if (ext.isNotEmpty() && entry.getType().getName() != "raw") {
            type = when (ext) {
                "xml", "xsd" -> Type.BINARY_XML
                "9.png" -> Type.PNG_9PATCH
                else -> Type.UNKNOWN
            }
        }

        // 由条目生成输出文件名。
        val outFileName = "res/" + entry.getType().getName() + entry.getType().getConfig().toQualifiers() +
            "/" + entry.getName() + if (ext.isEmpty()) "" else ".$ext"

        // 登记输入->输出名映射。
        resFileMap[inFileName] = outFileName

        Log.d(TAG, "Decoding file $inFileName to $outFileName")

        try {
            if (type != Type.UNKNOWN) {
                try {
                    decode(type, inDir, inFileName, outDir, outFileName)
                    return
                } catch (ignored: RawXmlEncounteredException) {
                    // 视作原始 XML 文件。
                    Log.d(TAG, "Could not decode binary XML file: $inFileName")
                } catch (ignored: NinePatchNotFoundException) {
                    // 视作原始 PNG：部分应用含未处理的 3x3 假九宫格，
                    // 原样导出，留给 aapt2 正确处理。
                    Log.d(TAG, "Could not find 9-patch chunk in file: $inFileName")
                }
            }

            decode(Type.UNKNOWN, inDir, inFileName, outDir, outFileName)
        } catch (ignored: AndrolibException) {
            Log.w(TAG, "Could not decode file, replacing by NULL value: $inFileName")
            entry.value = ResPrimitive.NULL
        }
    }

    @Throws(AndrolibException::class)
    private fun decode(
        type: Type,
        inDir: Directory,
        inFileName: String,
        outDir: Directory,
        outFileName: String,
    ) {
        val decoder = mDecoders[type]
            ?: throw IllegalStateException("Undefined decoder for type: $type")

        var success = false
        try {
            inDir.getFileInput(inFileName).use { input ->
                outDir.getFileOutput(outFileName).use { output ->
                    decoder.decode(input, output)
                    success = true
                }
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        } finally {
            // 失败时清掉半成品输出。
            if (!success) {
                outDir.removeFile(outFileName)
            }
        }
    }

    companion object {
        private val TAG = ResFileDecoder::class.java.name
    }
}
