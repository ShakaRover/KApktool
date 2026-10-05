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
package brut.androlib.smali

import brut.androlib.exceptions.AndrolibException
import brut.common.Log
import brut.directory.DirectoryException
import brut.directory.FileDirectory
import brut.util.OS
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.smali.smaliFlexLexer
import com.android.tools.smali.smali.smaliParser
import com.android.tools.smali.smali.smaliTreeWalker
import org.antlr.runtime.CommonTokenStream
import org.antlr.runtime.RecognitionException
import org.antlr.runtime.tree.CommonTree
import org.antlr.runtime.tree.CommonTreeNodeStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * smali -> dex 汇编器：逐个 .smali 文件经 ANTLR 词法/语法/树遍历三步装配进同一个 [DexBuilder]。
 *
 * #3641：opcode API 级别封顶 29（dex 版本 039），更高 API 由 aapt2/打包层处理。
 * 任一文件语法错误即中断整个目录构建。
 */
class SmaliBuilder(apiLevel: Int) {
    // #3641 - opcode API 级别封顶 29（dex 版本最高 039）。
    private val mApiLevel: Int = minOf(apiLevel, 29)

    /** 把 smali 目录汇编为单个 dex 文件。 */
    @Throws(AndrolibException::class)
    fun build(smaliDir: File, dexFile: File) {
        try {
            val dexBuilder = DexBuilder(
                if (mApiLevel > 0) Opcodes.forApi(mApiLevel) else Opcodes.getDefault()
            )

            for (fileName in FileDirectory(smaliDir).getFiles(true)) {
                val smaliFile = File(smaliDir, fileName)

                if (!fileName.endsWith(".smali")) {
                    Log.w(TAG, "Unknown file type, ignoring: $smaliFile")
                    continue
                }

                var success: Boolean
                var cause: Exception?
                try {
                    success = buildFile(smaliFile, dexBuilder)
                    cause = null
                } catch (ex: Exception) {
                    success = false
                    cause = ex
                }
                if (!success) {
                    val ex = AndrolibException("Could not smali file: $smaliFile")
                    cause?.let { ex.initCause(it) }
                    throw ex
                }
            }

            if (dexFile.exists()) {
                OS.rmfile(dexFile)
            } else {
                val parentDir = dexFile.parentFile
                if (parentDir != null) {
                    OS.mkdir(parentDir)
                }
            }

            dexBuilder.writeTo(FileDataStore(dexFile))
        } catch (ex: DirectoryException) {
            throw AndrolibException("Could not smali folder: " + smaliDir.name, ex)
        } catch (ex: IOException) {
            throw AndrolibException("Could not smali folder: " + smaliDir.name, ex)
        } catch (ex: RuntimeException) {
            throw AndrolibException("Could not smali folder: " + smaliDir.name, ex)
        }
    }

    /** 汇编单个 smali 文件；词法/语法/遍历任一阶段报错返回 false。 */
    @Throws(IOException::class, RecognitionException::class)
    private fun buildFile(smaliFile: File, dexBuilder: DexBuilder): Boolean {
        InputStreamReader(Files.newInputStream(smaliFile.toPath()), StandardCharsets.UTF_8).use { reader ->
            val lexer = smaliFlexLexer(reader, mApiLevel)
            lexer.setSourceFile(smaliFile)

            val tokens = CommonTokenStream(lexer)

            if (PRINT_TOKENS) {
                for (token in tokens.tokens) {
                    if (token.channel != smaliParser.HIDDEN) {
                        println(smaliParser.tokenNames[token.type] + ": " + token.text)
                    }
                }
            }

            val parser = smaliParser(tokens)
            parser.setApiLevel(mApiLevel)
            parser.setVerboseErrors(VERBOSE_ERRORS)

            val result = parser.smali_file()

            if (parser.numberOfSyntaxErrors > 0 || lexer.numberOfSyntaxErrors > 0) {
                return false
            }

            val tree = result.tree as CommonTree
            val treeStream = CommonTreeNodeStream(tree)
            treeStream.setTokenStream(tokens)

            val treeWalker = smaliTreeWalker(treeStream)
            treeWalker.setApiLevel(mApiLevel)
            treeWalker.setVerboseErrors(VERBOSE_ERRORS)
            treeWalker.setDexBuilder(dexBuilder)
            treeWalker.smali_file()

            return treeWalker.numberOfSyntaxErrors == 0
        }
    }

    companion object {
        private val TAG = SmaliBuilder::class.java.name

        private const val VERBOSE_ERRORS = false
        private const val PRINT_TOKENS = false
    }
}
