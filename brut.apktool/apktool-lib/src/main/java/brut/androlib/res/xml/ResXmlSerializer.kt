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
package brut.androlib.res.xml

import brut.common.Log
import brut.util.TextUtils
import brut.xml.XmlUtils
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * apktool 自研的 [XmlSerializer] 实现：带缩进、命名空间栈与可选自动转义。
 *
 * 设计要点：
 * - 标签在遇到第一个子事件（属性/文本/闭合）时才真正落盘（[check]），以便按命名空间声明补写 xmlns；
 * - 命名空间以扁平数组栈 [mNamespaceStack]（prefix/uri 成对）+ 每层计数管理；
 * - 8KB 字符缓冲批量写出；[mAutoEscape] 仅用于 manifest/二进制 XML 转写场景，
 *   values XML 需要输出"原样"字符串（由 ResXmlPullStreamDecoder 自行转义），须关闭。
 */
class ResXmlSerializer(
    /** 是否自动转义文本与属性值。 */
    private val mAutoEscape: Boolean,
) : XmlSerializer {
    private val mBuffer = CharArray(BUFFER_SIZE)
    private var mIndent = booleanArrayOf(true, false, false, false)
    private var mElementStack = arrayOfNulls<String>(12)
    private var mNamespaceCounts = IntArray(6)
    private var mNamespaceStack = arrayOfNulls<String>(12)

    private var mWriter: Writer? = null
    private var mDepth = 0
    private var mPending = false
    private var mAutoNamespace = 0
    private var mBufferIndex = 0

    // XmlSerializer

    override fun setFeature(name: String?, state: Boolean) {
        throw IllegalStateException(NOT_SUPPORTED)
    }

    override fun getFeature(name: String?): Boolean = false

    override fun setProperty(name: String?, value: Any?) {
        throw IllegalStateException(NOT_SUPPORTED)
    }

    override fun getProperty(name: String?): Any? = null

    @Throws(IOException::class)
    override fun setOutput(os: OutputStream, encoding: String?) {
        if (encoding != null && !encoding.equals(StandardCharsets.UTF_8.name(), ignoreCase = true)) {
            throw UnsupportedOperationException()
        }
        setOutput(OutputStreamWriter(os, StandardCharsets.UTF_8))
    }

    override fun setOutput(writer: Writer?) {
        mWriter = writer
        mDepth = 0
        mIndent[0] = true
        mPending = false
        mAutoNamespace = 0
        mNamespaceCounts[0] = 3
        mNamespaceCounts[1] = 3
        mNamespaceStack[0] = ""
        mNamespaceStack[1] = ""
        mNamespaceStack[2] = XmlUtils.XML_PREFIX
        mNamespaceStack[3] = XmlUtils.XML_URI
        mNamespaceStack[4] = XmlUtils.XMLNS_PREFIX
        mNamespaceStack[5] = XmlUtils.XMLNS_URI
    }

    @Throws(IOException::class)
    override fun startDocument(encoding: String?, standalone: Boolean?) {
        write(XmlUtils.XML_PROLOG)
    }

    @Throws(IOException::class)
    override fun endDocument() {
        while (mDepth > 0) {
            endTag(mElementStack[mDepth * 3 - 3], mElementStack[mDepth * 3 - 1])
        }
        write(System.lineSeparator())
        flush()
    }

    @Throws(IOException::class)
    override fun setPrefix(prefixArg: String?, namespaceArg: String?) {
        check(false)
        var prefix = prefixArg ?: ""
        var namespace = namespaceArg ?: ""

        // 相同的前缀定义直接忽略。
        if (prefix == getPrefix(namespace, true, false)) {
            return
        }

        var i = mNamespaceCounts[mDepth + 1]++ shl 1
        if (mNamespaceStack.size < i + 1) {
            val newStack = arrayOfNulls<String>(mNamespaceStack.size + 16)
            System.arraycopy(mNamespaceStack, 0, newStack, 0, i)
            mNamespaceStack = newStack
        }

        mNamespaceStack[i++] = prefix
        mNamespaceStack[i] = namespace
    }

    override fun getPrefix(namespace: String?, generatePrefix: Boolean): String? =
        try {
            getPrefix(namespace, false, generatePrefix)
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }

    private fun getPrefix(namespaceArg: String?, includeDefault: Boolean, generatePrefix: Boolean): String? {
        val namespace = namespaceArg ?: ""
        var i = mNamespaceCounts[mDepth + 1] * 2 - 2
        while (i >= 0) {
            if (mNamespaceStack[i + 1] == namespace && (includeDefault || !mNamespaceStack[i]!!.isEmpty())) {
                var candidate = mNamespaceStack[i]
                var j = i + 2
                while (j < mNamespaceCounts[mDepth + 1] * 2) {
                    if (mNamespaceStack[j] == candidate) {
                        candidate = null
                        break
                    }
                    j++
                }
                if (candidate != null) {
                    return candidate
                }
            }
            i -= 2
        }

        if (!generatePrefix) {
            return null
        }

        val prefix: String?
        if (namespace.isEmpty()) {
            prefix = ""
        } else {
            var candidate: String?
            do {
                candidate = "n" + mAutoNamespace++
                var i2 = mNamespaceCounts[mDepth + 1] * 2 - 2
                while (i2 >= 0) {
                    if (candidate == mNamespaceStack[i2]) {
                        candidate = null
                        break
                    }
                    i2 -= 2
                }
            } while (candidate == null)
            prefix = candidate
        }

        // setPrefix 会触发标签落盘，这里暂存并恢复 pending 状态。
        val pending = mPending
        mPending = false
        setPrefix(prefix, namespace)
        mPending = pending
        return prefix
    }

    override fun getDepth(): Int = if (mPending) mDepth + 1 else mDepth

    override fun getNamespace(): String? {
        val depth = getDepth()
        return if (depth > 0) mElementStack[depth * 3 - 3] else null
    }

    override fun getName(): String? {
        val depth = getDepth()
        return if (depth > 0) mElementStack[depth * 3 - 1] else null
    }

    @Throws(IOException::class)
    override fun startTag(namespaceArg: String?, name: String?): XmlSerializer {
        check(false)
        writeIndent()

        var i = mDepth * 3
        if (mElementStack.size < i + 3) {
            val newStack = arrayOfNulls<String>(mElementStack.size + 12)
            System.arraycopy(mElementStack, 0, newStack, 0, i)
            mElementStack = newStack
        }

        val namespace = namespaceArg
        var prefix = if (namespace != null) getPrefix(namespace, true, true) ?: "" else ""
        if (namespace != null && namespace.isEmpty()) {
            var j = mNamespaceCounts[mDepth]
            while (j < mNamespaceCounts[mDepth + 1]) {
                if (mNamespaceStack[j * 2]!!.isEmpty() && mNamespaceStack[j * 2 + 1]!!.isNotEmpty()) {
                    throw IllegalStateException("Could not set default namespace for elements in no namespace.")
                }
                j++
            }
        }

        mElementStack[i++] = namespace
        mElementStack[i++] = prefix
        mElementStack[i] = name

        write('<')
        if (prefix.isNotEmpty()) {
            write(prefix)
            write(':')
        }
        write(name!!)

        mIndent[mDepth] = true
        mPending = true
        return this
    }

    @Throws(IOException::class)
    override fun endTag(namespace: String?, name: String?): XmlSerializer {
        if (!mPending) {
            mDepth--
        }
        if ((namespace == null && mElementStack[mDepth * 3] != null) ||
            (namespace != null && namespace != mElementStack[mDepth * 3]) ||
            mElementStack[mDepth * 3 + 2] != name
        ) {
            throw IllegalArgumentException("</{$namespace}$name> does not match start.")
        }

        if (mPending) {
            check(true)
            mDepth--
        } else {
            if (mIndent[mDepth + 1]) {
                writeIndent()
            }
            write("</")
            val prefix = mElementStack[mDepth * 3 + 1]!!
            if (prefix.isNotEmpty()) {
                write(prefix)
                write(':')
            }
            write(name!!)
            write('>')
        }

        mNamespaceCounts[mDepth + 1] = mNamespaceCounts[mDepth]
        return this
    }

    @Throws(IOException::class)
    override fun attribute(namespaceArg: String?, name: String?, value: String?): XmlSerializer {
        if (!mPending) {
            throw IllegalStateException("Illegal position for attribute.")
        }
        val namespace = namespaceArg ?: ""

        write(' ')
        val prefix = if (namespace.isNotEmpty()) getPrefix(namespace, false, true) ?: "" else ""
        if (prefix.isNotEmpty()) {
            write(prefix)
            write(':')
        }
        write(name!!)
        write("=\"")
        if (mAutoEscape) {
            writeEscaped(value!!, true)
        } else {
            write(value!!)
        }
        write('"')
        return this
    }

    @Throws(IOException::class)
    override fun text(text: String?): XmlSerializer {
        check(false)
        mIndent[mDepth] = false
        if (mAutoEscape) {
            writeEscaped(text!!, false)
        } else {
            write(text!!)
        }
        return this
    }

    @Throws(IOException::class)
    override fun text(buf: CharArray, start: Int, len: Int): XmlSerializer =
        text(String(buf, start, len))

    override fun cdsect(text: String?) {
        throw IllegalStateException(NOT_SUPPORTED)
    }

    override fun entityRef(text: String?) {
        throw IllegalStateException(NOT_SUPPORTED)
    }

    override fun processingInstruction(text: String?) {
        throw IllegalStateException(NOT_SUPPORTED)
    }

    override fun comment(text: String?) {
        throw IllegalStateException(NOT_SUPPORTED)
    }

    override fun docdecl(text: String?) {
        throw IllegalStateException(NOT_SUPPORTED)
    }

    override fun ignorableWhitespace(text: String?) {
        throw IllegalStateException(NOT_SUPPORTED)
    }

    @Throws(IOException::class)
    override fun flush() {
        check(false)
        flushBuffer()
    }

    // 工具方法

    /** 把挂起的开始标签落盘（补写 xmlns、按需自闭合）。 */
    @Throws(IOException::class)
    private fun check(close: Boolean) {
        if (!mPending) {
            return
        }
        if (mIndent[mDepth] && mNamespaceCounts[mDepth] < mNamespaceCounts[mDepth + 1]) {
            writeIndent()
            write(' ')
        }

        mPending = false
        mDepth++

        if (mIndent.size <= mDepth) {
            mIndent = mIndent.copyOf(mDepth + 4)
        }
        mIndent[mDepth] = mIndent[mDepth - 1]

        var i = mNamespaceCounts[mDepth - 1]
        while (i < mNamespaceCounts[mDepth]) {
            val prefix = mNamespaceStack[i * 2]!!
            val uri = mNamespaceStack[i * 2 + 1]!!
            write(" xmlns")
            if (prefix.isNotEmpty()) {
                write(':')
                write(prefix)
            } else if (getNamespace()!!.isEmpty() && uri.isNotEmpty()) {
                throw IllegalStateException("Could not set default namespace for elements in no namespace.")
            }
            write("=\"")
            if (mAutoEscape) {
                writeEscaped(uri, true)
            } else {
                write(uri)
            }
            write('"')
            i++
        }

        if (mNamespaceCounts.size <= mDepth + 1) {
            mNamespaceCounts = mNamespaceCounts.copyOf(mDepth + 8)
        }

        mNamespaceCounts[mDepth + 1] = mNamespaceCounts[mDepth]

        if (close) {
            write(" />")
        } else {
            write('>')
        }
    }

    @Throws(IOException::class)
    private fun flushBuffer() {
        if (mBufferIndex > 0) {
            mWriter!!.write(mBuffer, 0, mBufferIndex)
            mWriter!!.flush()
            mBufferIndex = 0
        }
    }

    @Throws(IOException::class)
    private fun writeIndent() {
        write(System.lineSeparator())
        var len = mDepth * 4
        while (len > 0) {
            if (mBufferIndex == BUFFER_SIZE) {
                flushBuffer()
            }
            var batch = BUFFER_SIZE - mBufferIndex
            if (batch > len) {
                batch = len
            }
            Arrays.fill(mBuffer, mBufferIndex, mBufferIndex + batch, ' ')
            len -= batch
            mBufferIndex += batch
        }
    }

    @Throws(IOException::class)
    private fun write(ch: Char) {
        if (mBufferIndex >= BUFFER_SIZE) {
            flushBuffer()
        }
        mBuffer[mBufferIndex++] = ch
    }

    @Throws(IOException::class)
    private fun write(str: String) = write(str, 0, str.length)

    @Throws(IOException::class)
    private fun write(str: String, start: Int, lenArg: Int) {
        var len = lenArg
        var pos = start
        while (len > 0) {
            if (mBufferIndex == BUFFER_SIZE) {
                flushBuffer()
            }
            var batch = BUFFER_SIZE - mBufferIndex
            if (batch > len) {
                batch = len
            }
            for (k in 0 until batch) {
                mBuffer[mBufferIndex + k] = str[pos + k]
            }
            pos += batch
            len -= batch
            mBufferIndex += batch
        }
    }

    /**
     * 仅用于安全转写 manifest 与资源 XML；
     * 序列化 values XML 时必须关闭（值已按 AAPT 规则另行转义）。
     */
    @Throws(IOException::class)
    private fun writeEscaped(str: String, attr: Boolean) {
        var ch = '\u0000'
        var prev = '\u0000'
        var prev2 = '\u0000'
        var i = 0
        val n = str.length
        while (i < n) {
            ch = str[i]
            var written = false
            var plain = true
            when {
                ch == '\n' -> if (attr) {
                    write("&#xA;"); written = true
                }
                ch == '\r' -> if (attr) {
                    write("&#xD;"); written = true
                }
                ch == '\t' -> if (attr) {
                    write("&#x9;"); written = true
                }
                ch == '"' -> if (attr) {
                    write("&quot;"); written = true
                }
                ch == '&' -> {
                    write("&amp;"); written = true
                }
                ch == '<' -> {
                    write("&lt;"); written = true
                }
                ch == '>' -> if (prev == ']' && prev2 == ']') {
                    write("&gt;"); written = true
                }
                TextUtils.isPrintableChar(ch) -> {}
                ch.isHighSurrogate() && i + 1 < n -> {
                    val low = str[i + 1]
                    if (low.isLowSurrogate()) {
                        write(ch)
                        write(low)
                        i++
                    } else {
                        Log.w(TAG, "Bad surrogate pair (U+%04x U+%04x)", ch.code, low.code)
                    }
                    written = true
                }
                else -> {
                    Log.w(TAG, "Illegal character (U+%04x)", ch.code)
                    written = true
                    plain = false
                }
            }
            if (!written && plain) {
                write(ch)
            }
            i++
            prev2 = prev
            prev = ch
        }
    }

    companion object {
        private val TAG = ResXmlSerializer::class.java.name
        private const val NOT_SUPPORTED = "Method is not supported."

        /** 写出缓冲区大小（字符）。 */
        private const val BUFFER_SIZE = 8192
    }
}
