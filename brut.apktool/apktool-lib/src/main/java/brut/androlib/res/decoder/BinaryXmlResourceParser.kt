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
import brut.androlib.exceptions.UndefinedResObjectException
import brut.androlib.res.data.FeatureFlag
import brut.androlib.res.data.ResChunkHeader
import brut.androlib.res.data.ResStringPool
import brut.androlib.res.table.ResEntrySpec
import brut.androlib.res.table.ResId
import brut.androlib.res.table.ResPackage
import brut.androlib.res.table.ResTable
import brut.androlib.res.table.value.ResAttribute
import brut.androlib.res.table.value.ResItem
import brut.androlib.res.table.value.ResString
import brut.androlib.res.table.value.ResValue
import brut.androlib.res.xml.ResXmlUtils
import brut.common.Log
import brut.util.BinaryDataInputStream
import com.google.common.io.BaseEncoding
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.Reader

/**
 * 二进制 XML 资源（layout/menu/AndroidManifest.xml 等）的 XmlPullParser 实现。
 *
 * 把 aapt 的 RES_XML_* chunk 流翻译成 pull 事件；属性值经资源表引用反查还原成
 * 语义值（@ref/@color/枚举名等）。容错策略：
 *  - 资源表优先于字符串池取名（混淆样本，#2972 命名空间回退）；
 *  - 未定义 attr 引用注入 dummy 规格（#2836，skipUnresolved 时仅告警）；
 *  - 首个无法恢复的错误记录于 [getFirstError] 供上层决定回退 raw XML；
 *  - 原始值（rawValue）存在时可透传（mIgnoreRawValues=false）。
 */
class BinaryXmlResourceParser(
    private val mTable: ResTable,
    private val mIgnoreRawValues: Boolean,
    private val mSkipUnresolved: Boolean,
) : XmlPullParser {
    private val mStringPool = ResStringPool()
    private val mNamespaces = NamespaceStack()

    private var mIn: BinaryDataInputStream? = null
    private var mParser: ResChunkPullParser? = null
    private var mResourceMap: Array<ResId>? = null
    private var mHasRawValues = false
    private var mFirstError: AndrolibException? = null

    private var mEventType = XmlPullParser.START_DOCUMENT
    private var mLineNumber = -1
    private var mNamespaceIndex = -1
    private var mNameIndex = -1
    private var mIdIndex = -1
    private var mClassIndex = -1
    private var mStyleIndex = -1
    private var mFlagExt: FlagExt? = null
    private var mAttributes: Array<Attribute?>? = null

    /** 是否观察到保留的原始属性值。 */
    fun hasRawValues(): Boolean = mHasRawValues

    /** 首个不可恢复错误（无则 null）。 */
    fun getFirstError(): AndrolibException? = mFirstError

    // XmlPullParser

    @Throws(XmlPullParserException::class)
    override fun setFeature(name: String?, state: Boolean) {
        throw XmlPullParserException(NOT_SUPPORTED)
    }

    override fun getFeature(name: String?): Boolean = false

    @Throws(XmlPullParserException::class)
    override fun setProperty(name: String?, value: Any?) {
        throw XmlPullParserException(NOT_SUPPORTED)
    }

    override fun getProperty(name: String?): Any? = null

    @Throws(XmlPullParserException::class)
    override fun setInput(`in`: Reader?) {
        throw XmlPullParserException(NOT_SUPPORTED)
    }

    @Throws(XmlPullParserException::class)
    override fun setInput(inputStream: InputStream?, inputEncoding: String?) {
        if (inputEncoding != null) {
            throw XmlPullParserException(NOT_SUPPORTED)
        }

        reset()
        var input = BinaryDataInputStream(BufferedInputStream(inputStream))
        var parser = ResChunkPullParser(input)
        mIn = input
        mParser = parser
        try {
            if (!nextChunk()) {
                throw IOException("Input file is empty.")
            }
            if (parser.chunkType() != ResChunkHeader.RES_XML_TYPE) {
                throw IOException("Unexpected chunk: " + parser.chunkName() + " (expected: RES_XML_TYPE)")
            }
        } catch (ex: IOException) {
            mIn = null
            mParser = null
            throw XmlPullParserException("Could not initialize parser.", this, ex)
        }

        mParser = ResChunkPullParser(input, parser.dataSize())
    }

    override fun getInputEncoding(): String? = null

    @Throws(XmlPullParserException::class)
    override fun defineEntityReplacementText(entityName: String?, replacementText: String?) {
        throw XmlPullParserException(NOT_SUPPORTED)
    }

    override fun getNamespaceCount(depth: Int): Int = mNamespaces.getCount(depth)

    override fun getNamespacePrefix(pos: Int): String? =
        mStringPool.getString(mNamespaces.getPrefix(pos))

    override fun getNamespaceUri(pos: Int): String? =
        mStringPool.getString(mNamespaces.getUri(pos))

    override fun getNamespace(prefix: String?): String = throw RuntimeException(NOT_SUPPORTED)

    override fun getDepth(): Int = mNamespaces.getDepth()

    override fun getPositionDescription(): String = "XML line #$mLineNumber"

    override fun getLineNumber(): Int = mLineNumber

    override fun getColumnNumber(): Int = -1

    @Throws(XmlPullParserException::class)
    override fun isWhitespace(): Boolean {
        if (mEventType != XmlPullParser.TEXT) {
            throw XmlPullParserException("Parser must be on TEXT to get text.", this, null)
        }
        val text = getText() ?: return true
        return text.all { it.isWhitespace() }
    }

    override fun getText(): String? {
        if (mEventType != XmlPullParser.TEXT) {
            return null
        }
        return mStringPool.getString(mNameIndex)
    }

    override fun getTextCharacters(holderForStartAndLength: IntArray?): CharArray? {
        val text = getText() ?: return null
        val len = text.length
        holderForStartAndLength!![0] = 0
        holderForStartAndLength[1] = len
        return text.toCharArray()
    }

    override fun getNamespace(): String? {
        if (mEventType != XmlPullParser.START_TAG && mEventType != XmlPullParser.END_TAG) {
            return null
        }
        return mStringPool.getString(mNamespaceIndex)
    }

    override fun getName(): String? {
        if (mEventType != XmlPullParser.START_TAG && mEventType != XmlPullParser.END_TAG) {
            return null
        }
        return mStringPool.getString(mNameIndex)
    }

    override fun getPrefix(): String? {
        if (mEventType != XmlPullParser.START_TAG && mEventType != XmlPullParser.END_TAG) {
            return null
        }
        return mStringPool.getString(mNamespaces.findPrefix(mNamespaceIndex))
    }

    override fun isEmptyElementTag(): Boolean = false

    override fun getAttributeCount(): Int {
        if (mEventType != XmlPullParser.START_TAG) {
            return -1
        }
        val count = mAttributes?.size ?: 0
        // 还原的 featureFlag 属性以附加属性的形式暴露。
        return if (mFlagExt != null) count + 1 else count
    }

    override fun getAttributeNamespace(index: Int): String? {
        if (isFlagExtAttribute(index)) {
            return ResXmlUtils.ANDROID_RES_NS
        }

        val attr = getAttribute(index) ?: return XmlPullParser.NO_NAMESPACE

        val nameId = getAttributeNameResourceId(index)

        // #2972 - 命名空间索引为 -1 表示属性缺 ns；但资源来自系统包时可
        // 归入默认命名空间。虽然覆盖整个系统命名空间略显激进，总比不解析好。
        if (attr.ns < 0) {
            if (nameId.pkgId() == ResTable.APP_PACKAGE_ID) {
                return ResXmlUtils.ANDROID_RES_NS_AUTO
            }
            if (nameId.pkgId() == ResTable.SYS_PACKAGE_ID) {
                return ResXmlUtils.ANDROID_RES_NS
            }
            return XmlPullParser.NO_NAMESPACE
        }

        // 压缩器可能删掉 ns 字符串：非空则直接用，
        // 否则按资源归属回退 auto/android 命名空间。
        val uri = mStringPool.getString(attr.ns)
        if (!uri.isNullOrEmpty()) {
            return uri
        }
        return if (nameId.pkgId() == ResTable.APP_PACKAGE_ID) {
            ResXmlUtils.ANDROID_RES_NS_AUTO
        } else {
            ResXmlUtils.ANDROID_RES_NS
        }
    }

    override fun getAttributeName(index: Int): String {
        if (isFlagExtAttribute(index)) {
            return "featureFlag"
        }

        val attr = getAttribute(index)
        if (attr == null || attr.name < 0) {
            return ""
        }

        val nameId = getAttributeNameResourceId(index)

        // 资源表名字优先于字符串池：混淆样本中字符串池值常错位
        //（如 app:state_collapsed 被写成 app:d2），查表可得正确名字。
        if (nameId != ResId.NULL) {
            try {
                return mTable.resolve(nameId).name
            } catch (ignored: AndrolibException) {
            }
        }

        // 资源表失败回退字符串池。
        var name = mStringPool.getString(attr.name) ?: ""

        // 部分优化应用删掉了 attr 规格但引用还在：注入通用规格保证可重建。
        if (nameId != ResId.NULL) {
            try {
                var pkg: ResPackage? = mTable.mainPackage
                if (pkg == null) {
                    // 无主包时改用 android 包。
                    pkg = mTable.resolvePackageGroup(ResTable.SYS_PACKAGE_ID).getBasePackage()
                }

                // #2836 - 无法解析的条目跳过。
                if (mSkipUnresolved || nameId.pkgId() != pkg.getId()) {
                    Log.w(TAG, "Unresolved attr reference: ns=%s, name=%s, id=%s",
                        getAttributePrefix(index), name, nameId)
                    return name
                }

                Log.d(TAG, "Injecting dummy for unresolved attr reference: ns=%s, name=%s, id=%s",
                    getAttributePrefix(index), name, nameId)
                if (!pkg.hasTypeSpec(nameId.typeId())) {
                    pkg.addTypeSpec(nameId.typeId(), "attr")
                    pkg.addType(nameId.typeId())
                }
                if (name.isEmpty()) {
                    name = ResEntrySpec.DUMMY_PREFIX + nameId
                }
                name = pkg.addEntrySpec(nameId.typeId(), nameId.entryId(), name).name
                pkg.addEntry(nameId.typeId(), nameId.entryId(), ResAttribute.DEFAULT)
            } catch (ex: AndrolibException) {
                if (mFirstError == null) {
                    mFirstError = ex
                }
                Log.w(TAG, "Could not add missing attr: ns=%s, name=%s, id=%s",
                    getAttributePrefix(index), name, nameId)
            }
        }

        return name
    }

    override fun getAttributePrefix(index: Int): String {
        if (isFlagExtAttribute(index)) {
            return "android"
        }
        val attr = getAttribute(index) ?: return ""
        if (attr.ns < 0) {
            return ""
        }
        return mStringPool.getString(mNamespaces.findPrefix(attr.ns)) ?: ""
    }

    override fun getAttributeType(index: Int): String = "CDATA"

    override fun isAttributeDefault(index: Int): Boolean = false

    override fun getAttributeValue(index: Int): String {
        if (isFlagExtAttribute(index)) {
            val flagName = mStringPool.getString(mFlagExt!!.flagNameIndex)
            return if (flagName != null) FeatureFlag.toString(flagName, mFlagExt!!.flagNegated) else ""
        }

        val attr = getAttribute(index) ?: return ""

        // 保留原始值时直接使用（现代应用罕见）。
        if (mHasRawValues && !mIgnoreRawValues) {
            val rawValue = mStringPool.getString(attr.rawValue)
            if (rawValue != null) {
                return rawValue
            }
        }

        // 尝试经资源表解码带类型的值。
        var value: ResItem? = null
        var name: String? = null
        var decoded: String? = null
        try {
            var pkg: ResPackage? = mTable.mainPackage
            if (pkg == null) {
                // 无主包时改用 android 包。
                pkg = mTable.resolvePackageGroup(ResTable.SYS_PACKAGE_ID).getBasePackage()
            }

            value = if (attr.valueType == ResValue.TYPE_STRING) {
                val strValue: CharSequence? = mStringPool.getText(attr.valueData)
                if (strValue != null) ResString(strValue) else null
            } else {
                ResItem.parse(pkg, attr.valueType, attr.valueData)
            }

            if (value != null) {
                val nameId = getAttributeNameResourceId(index)
                if (nameId != ResId.NULL) {
                    // 需要 attr 条目自身的值来格式化本值。
                    try {
                        val nameEntry = mTable.resolveEntry(nameId)
                        name = nameEntry.getName()
                        val nameValue = nameEntry.value as? ResAttribute
                        if (nameValue != null) {
                            // 引用/属性类型无需并入 attr 允许的类型集合。
                            val isExplicitType = when (attr.valueType) {
                                ResValue.TYPE_NULL, ResValue.TYPE_REFERENCE,
                                ResValue.TYPE_DYNAMIC_REFERENCE, ResValue.TYPE_ATTRIBUTE,
                                ResValue.TYPE_DYNAMIC_ATTRIBUTE,
                                -> false
                                else -> true
                            }
                            if (isExplicitType && !nameValue.hasSymbolsForValue(value)) {
                                nameValue.addValueType(attr.valueType)
                            }

                            decoded = nameValue.formatAsAttributeValue(value)
                        } else {
                            Log.w(TAG, "Unexpected attribute name: $nameEntry")
                        }
                    } catch (ignored: UndefinedResObjectException) {
                    }
                } else {
                    // 用默认 attr 格式化。
                    decoded = ResAttribute.DEFAULT.formatAsAttributeValue(value)
                }
            }
        } catch (ex: AndrolibException) {
            if (mFirstError == null) {
                mFirstError = ex
            }
        }

        if (decoded == null) {
            if (name == null) {
                name = mStringPool.getString(attr.name)
            }

            Log.w(TAG, "Could not decode attribute value: ns=%s, name=%s, type=0x%02x, value=0x%08x",
                getAttributePrefix(index), name, attr.valueType, attr.valueData)

            if (value != null) {
                // 用默认 attr 兜底格式化。
                try {
                    decoded = ResAttribute.DEFAULT.formatAsAttributeValue(value)
                } catch (ignored: AndrolibException) {
                }
            }
            if (decoded == null) {
                decoded = ""
            }
        }

        return decoded
    }

    override fun getAttributeValue(namespace: String?, name: String?): String {
        if (mEventType != XmlPullParser.START_TAG) {
            throw IndexOutOfBoundsException("Parser must be on START_TAG to get attributes.")
        }
        val flagExt = mFlagExt
        if (flagExt != null && ResXmlUtils.ANDROID_RES_NS == namespace && name == "featureFlag") {
            val flagName = mStringPool.getString(flagExt.flagNameIndex)
            return if (flagName != null) FeatureFlag.toString(flagName, flagExt.flagNegated) else ""
        }
        val attrs = mAttributes ?: return ""
        if (name == null) {
            return ""
        }
        val uriIdx = mStringPool.findString(namespace)
        val nameIdx = mStringPool.findString(name)
        for (i in attrs.indices) {
            val attr = attrs[i]
            if (attr != null && uriIdx == attr.ns && nameIdx == attr.name) {
                return getAttributeValue(i)
            }
        }
        return ""
    }

    override fun getEventType(): Int = mEventType

    @Throws(XmlPullParserException::class, IOException::class)
    override fun next(): Int {
        if (mIn == null) {
            throw XmlPullParserException("Parser is not opened.", this, null)
        }
        return try {
            doNext()
        } catch (ex: IOException) {
            reset()
            throw ex
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextToken(): Int = next()

    @Throws(XmlPullParserException::class)
    override fun require(type: Int, namespace: String?, name: String?) {
        if (type != mEventType ||
            (namespace != null && namespace != getNamespace()) ||
            (name != null && name != getName())
        ) {
            throw XmlPullParserException(XmlPullParser.TYPES[type] + " is expected.", this, null)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextText(): String {
        if (mEventType != XmlPullParser.START_TAG) {
            throw XmlPullParserException("Parser must be on START_TAG to read next text.", this, null)
        }
        val eventType = next()
        if (eventType == XmlPullParser.END_TAG) {
            return ""
        }
        if (eventType != XmlPullParser.TEXT) {
            throw XmlPullParserException("Parser must be on TEXT or END_TAG to read text.", this, null)
        }
        val result = getText()
        if (next() != XmlPullParser.END_TAG) {
            throw XmlPullParserException("Event TEXT must be immediately followed by END_TAG.", this, null)
        }
        return result ?: ""
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextTag(): Int {
        var eventType = next()
        if (eventType == XmlPullParser.TEXT && isWhitespace()) {
            eventType = next()
        }
        if (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_TAG) {
            throw XmlPullParserException("Expected start or end tag.", this, null)
        }
        return eventType
    }

    // 工具方法

    private fun isFlagExtAttribute(index: Int): Boolean {
        if (mEventType != XmlPullParser.START_TAG) {
            throw IndexOutOfBoundsException("Parser must be on START_TAG to get attributes.")
        }
        return mFlagExt != null && index == (mAttributes?.size ?: 0)
    }

    private fun getAttribute(index: Int): Attribute? {
        if (mEventType != XmlPullParser.START_TAG) {
            throw IndexOutOfBoundsException("Parser must be on START_TAG to get attributes.")
        }
        val attrs = mAttributes
        if (attrs == null || index < 0 || index >= attrs.size) {
            throw IndexOutOfBoundsException(
                String.format("Attribute index out of range: index=%s, length=%s", index, attrs?.size ?: 0)
            )
        }
        return attrs[index]
    }

    private fun getAttributeNameResourceId(index: Int): ResId {
        val map = mResourceMap ?: return ResId.NULL
        val attr = getAttribute(index) ?: return ResId.NULL
        if (attr.name < 0 || attr.name >= map.size) {
            return ResId.NULL
        }
        return map[attr.name]
    }

    private fun reset() {
        mIn = null
        mParser = null
        mResourceMap = null
        mStringPool.reset()
        mNamespaces.reset()
        resetEventInfo()
    }

    private fun resetEventInfo() {
        mEventType = XmlPullParser.START_DOCUMENT
        mLineNumber = -1
        mNamespaceIndex = -1
        mNameIndex = -1
        mIdIndex = -1
        mClassIndex = -1
        mStyleIndex = -1
        mFlagExt = null
        mAttributes = null
    }

    @Throws(IOException::class)
    private fun nextChunk(): Boolean {
        val parser = mParser!!
        // 跳过当前 chunk 尾部填充或未知数据。
        if (parser.isChunk()) {
            val skipped = parser.skipChunk()
            if (skipped > 0) {
                Log.d(TAG, "Skipped unknown %s bytes at end of %s chunk.", skipped, parser.chunkName())
            }
        }

        // 复位上一事件数据。
        val prevEventType = mEventType
        if (prevEventType != -1) {
            resetEventInfo()
        }

        // 根级命名空间全部弹出后停止。
        if (prevEventType == XmlPullParser.END_TAG && mNamespaces.getDepth() == 0 &&
            mNamespaces.getCurrentCount() == 0
        ) {
            return false
        }

        while (parser.next()) {
            // 跳过未知或不支持的 chunk。
            if (parser.chunkType() == ResChunkHeader.RES_NULL_TYPE) {
                Log.d(TAG, "Skipping unknown chunk (%s) of %s bytes at 0x%08x.",
                    parser.chunkName(), parser.chunkSize(), parser.chunkStart())
                parser.skipChunk()
                continue
            }

            Log.d(TAG, "Chunk at 0x%08x: %s (%s bytes)",
                parser.chunkStart(), parser.chunkName(), parser.chunkSize())
            return true
        }

        return false
    }

    /** 事件推进核心：解析 chunk 并映射为 pull 事件。 */
    @Throws(IOException::class)
    private fun doNext(): Int {
        val input = mIn!!
        var parser = mParser!!
        if (mEventType == XmlPullParser.END_DOCUMENT) {
            return XmlPullParser.END_DOCUMENT
        }
        if (mEventType == XmlPullParser.END_TAG) {
            mNamespaces.decrementDepth()
        }

        while (nextChunk()) {
            parser = mParser!!
            when (parser.chunkType()) {
                ResChunkHeader.RES_STRING_POOL_TYPE -> {
                    mStringPool.parse(parser)
                    continue
                }
                ResChunkHeader.RES_XML_RESOURCE_MAP_TYPE -> {
                    skipUnreadHeader()

                    val map = arrayOfNulls<ResId>(parser.dataSize() / 4)
                    for (i in map.indices) {
                        map[i] = ResId.of(input.readInt())
                    }
                    @Suppress("UNCHECKED_CAST")
                    mResourceMap = map as Array<ResId>
                    continue
                }
            }

            if (parser.chunkType() < ResChunkHeader.RES_XML_FIRST_CHUNK_TYPE ||
                parser.chunkType() > ResChunkHeader.RES_XML_LAST_CHUNK_TYPE
            ) {
                skipUnexpectedChunk()
                continue
            }

            // ResXMLTree_node
            mLineNumber = input.readInt()
            input.skipInt() // comment

            skipUnreadHeader()

            // 扩展节点数据跟在 ResXMLTree_node 之后。
            when (parser.chunkType()) {
                ResChunkHeader.RES_XML_START_NAMESPACE_TYPE -> {
                    // ResXMLTree_namespaceExt
                    val prefix = input.readInt()
                    val uri = input.readInt()

                    mNamespaces.push(prefix, uri)
                }
                ResChunkHeader.RES_XML_END_NAMESPACE_TYPE -> {
                    // ResXMLTree_namespaceExt
                    input.skipInt() // prefix
                    input.skipInt() // uri

                    mNamespaces.pop()
                }
                ResChunkHeader.RES_XML_START_ELEMENT_TYPE -> {
                    val startPosition = input.position()
                    // ResXMLTree_attrExt
                    mNamespaceIndex = input.readInt()
                    mNameIndex = input.readInt()
                    val attributeStart = input.readUnsignedShort()
                    val attributeSize = input.readUnsignedShort()
                    val attributeCount = input.readUnsignedShort()
                    mIdIndex = input.readUnsignedShort()
                    mClassIndex = input.readUnsignedShort()
                    mStyleIndex = input.readUnsignedShort()

                    // 存在 flag 时以 attrExt 后的 ResXMLTreeFlagExt 表示：
                    // 用属性区起点偏移能否容纳 attrExt + FlagExt 来判断。
                    if (attributeStart.toLong() == input.position() - startPosition + FlagExt.SIZE) {
                        val flagExt = FlagExt.read(input)

                        if (flagExt.descriptor == FlagExt.FLAG_INFO) {
                            mFlagExt = flagExt
                        }
                    }

                    // 与属性区起点对齐。
                    input.jumpTo(startPosition + attributeStart)

                    val attrs = arrayOfNulls<Attribute>(attributeCount)
                    for (i in 0 until attributeCount) {
                        val attr = Attribute.read(input)

                        if (attributeSize > Attribute.SIZE) {
                            val skipped = input.skipBytes(attributeSize - Attribute.SIZE)
                            Log.d(TAG, "Skipped unknown %s bytes in attribute.", skipped)
                        }

                        // 检测应用是否保留了原始属性值。
                        if (attr!!.let {
                                if (it.valueType == ResValue.TYPE_STRING) {
                                    it.valueData != it.rawValue
                                } else {
                                    it.rawValue >= 0
                                }
                            }
                        ) {
                            mHasRawValues = true
                        }

                        attrs[i] = attr
                    }
                    mAttributes = attrs

                    mNamespaces.incrementDepth()
                    mEventType = XmlPullParser.START_TAG
                    return mEventType
                }
                ResChunkHeader.RES_XML_END_ELEMENT_TYPE -> {
                    // ResXMLTree_endElementExt
                    mNamespaceIndex = input.readInt()
                    mNameIndex = input.readInt()

                    mEventType = XmlPullParser.END_TAG
                    return mEventType
                }
                ResChunkHeader.RES_XML_CDATA_TYPE -> {
                    // ResXMLTree_cdataExt
                    mNameIndex = input.readInt()
                    input.skipInt() // size, res0, type
                    input.skipInt() // data

                    mEventType = XmlPullParser.TEXT
                    return mEventType
                }
                else -> skipUnexpectedChunk()
            }
        }

        Log.d(TAG, "End of chunks at 0x%08x", input.position())

        // 主流长度未知，这里用 available() 判断尾随数据。
        if (input.available() > 0) {
            Log.d(TAG, "Ignoring trailing data at 0x%08x.", input.position())
        }

        mEventType = XmlPullParser.END_DOCUMENT
        return mEventType
    }

    @Throws(IOException::class)
    private fun skipUnexpectedChunk() {
        Log.w(TAG, "Skipping unexpected %s chunk of %s bytes at 0x%08x.",
            mParser!!.chunkName(), mParser!!.chunkSize(), mParser!!.chunkStart())
        mParser!!.skipChunk()
    }

    /** 有些应用谎报头部尺寸：按实际读取量对比后跳过剩余字节。 */
    @Throws(IOException::class)
    private fun skipUnreadHeader() {
        val bytesRead = (mIn!!.position() - mParser!!.chunkStart()).toInt()
        readExceedingBytes("Chunk header", mParser!!.headerSize(), bytesRead)
    }

    @Throws(IOException::class)
    private fun readExceedingBytes(name: String, size: Int, bytesRead: Int): ByteArray? {
        val bytesExceeding = size - bytesRead
        if (bytesExceeding > 0) {
            val buf = mIn!!.readBytes(bytesExceeding)
            for (element in buf) {
                if (element.toInt() != 0) {
                    Log.w(TAG, "%s size: %s bytes, read: %s bytes. Exceeding bytes: %s",
                        name, size, bytesRead, BaseEncoding.base16().encode(buf))
                    return buf
                }
            }
        }
        return null
    }

    /**
     * 命名空间栈：扁平 int 数组存 [count, prefix, uri..., count] 分段结构，
     * 每层深度一段；pop 只移计数，深度进出调整段边界。
     */
    private class NamespaceStack {
        private var mData = IntArray(INITIAL_CAPACITY)
        private var mDataLength = 0
        private var mDepth = 0

        init {
            reset()
        }

        fun reset() {
            mDataLength = 0
            mDepth = -1
            incrementDepth()
        }

        fun getDepth(): Int = mDepth

        fun incrementDepth() {
            ensureCapacity()
            val offset = mDataLength
            mData[offset] = 0
            mData[offset + 1] = 0
            mDataLength += 2
            mDepth++
        }

        private fun ensureCapacity() {
            if (mData.size - mDataLength >= 2) {
                return
            }
            val newData = IntArray(mData.size + INITIAL_CAPACITY)
            System.arraycopy(mData, 0, newData, 0, mDataLength)
            mData = newData
        }

        fun decrementDepth() {
            if (mDataLength == 0) {
                return
            }
            val offset = mDataLength - 1
            val count = mData[offset]
            mDataLength -= 2 + count * 2
            mDepth--
        }

        /** 前 depth 层累计的命名空间声明数（全局下标基准）。 */
        fun getCount(depth: Int): Int {
            if (mDataLength == 0 || depth <= 0) {
                return 0
            }
            var d = depth
            if (d > mDepth) {
                d = mDepth
            }
            var total = 0
            var offset = 0
            while (d > 0) {
                val count = mData[offset]
                total += count
                offset += 2 + count * 2
                d--
            }
            return total
        }

        fun getCurrentCount(): Int {
            if (mDataLength == 0) {
                return 0
            }
            return mData[mDataLength - 1]
        }

        fun push(prefix: Int, uri: Int) {
            ensureCapacity()
            val offset = mDataLength - 1
            val count = mData[offset]
            // 更新段头的计数。
            mData[offset - 1 - count * 2] = count + 1
            // 新声明写入段尾：prefix、uri、新尾计数。
            mData[offset] = prefix
            mData[offset + 1] = uri
            mData[offset + 2] = count + 1
            mDataLength += 2
        }

        fun pop(): Boolean {
            if (mDataLength == 0) {
                return false
            }
            var offset = mDataLength - 1
            var count = mData[offset]
            if (count == 0) {
                return false
            }
            count--
            offset -= 2
            mData[offset] = count
            offset -= 1 + count * 2
            mData[offset] = count
            mDataLength -= 2
            return true
        }

        fun getPrefix(index: Int): Int = get(index, true)

        fun getUri(index: Int): Int = get(index, false)

        private fun get(indexArg: Int, isPrefix: Boolean): Int {
            if (mDataLength == 0 || indexArg < 0) {
                return -1
            }
            var index = indexArg
            var offset = 0
            for (i in mDepth downTo 0) {
                val count = mData[offset]
                if (index >= count) {
                    index -= count
                    offset += 2 + count * 2
                    continue
                }
                offset += 1 + index * 2
                if (!isPrefix) {
                    offset++
                }
                return mData[offset]
            }
            return -1
        }

        /** 按 uri 字符串池下标找 prefix 下标。 */
        fun findPrefix(uri: Int): Int = find(uri, true)

        /** 按 prefix 字符串池下标找 uri 下标。 */
        fun findUri(prefix: Int): Int = find(prefix, false)

        private fun find(prefixOrUri: Int, isPrefix: Boolean): Int {
            if (mDataLength == 0) {
                return -1
            }
            var offset = mDataLength - 1
            for (i in mDepth downTo 0) {
                var count = mData[offset]
                offset -= 2
                while (count > 0) {
                    if (isPrefix) {
                        if (mData[offset + 1] == prefixOrUri) {
                            return mData[offset]
                        }
                    } else {
                        if (mData[offset] == prefixOrUri) {
                            return mData[offset + 1]
                        }
                    }
                    offset -= 2
                    count--
                }
            }
            return -1
        }

        companion object {
            private const val INITIAL_CAPACITY = 32
        }
    }

    /** ResXMLTreeFlagExt：feature flag 描述符（8 字节）。 */
    private class FlagExt private constructor(
        val descriptor: Int,
        val flagNegated: Boolean,
        val flagNameIndex: Int,
    ) {
        companion object {
            const val SIZE = 8

            const val PADDING = 0x00
            const val FLAG_INFO = 0x01

            /** 从流中读取 flag 扩展块。 */
            @JvmStatic
            @Throws(IOException::class)
            fun `read`(`in`: BinaryDataInputStream): FlagExt {
                // ResXMLTreeFlagExt
                val descriptor = `in`.readUnsignedByte()
                val flagNegated = `in`.readBoolean()
                `in`.skipShort() // reserved
                val flagNameIndex = `in`.readInt()

                return FlagExt(descriptor, flagNegated, flagNameIndex)
            }
        }
    }

    /** ResXMLTree_attribute：20 字节属性记录（值畸形时返回 null）。 */
    private class Attribute private constructor(
        val ns: Int,
        val name: Int,
        val rawValue: Int,
        val valueType: Int,
        val valueData: Int,
    ) {
        companion object {
            const val SIZE = 20

            /** 从流中读取属性；Res_value 尺寸非法时返回 null。 */
            @JvmStatic
            @Throws(IOException::class)
            fun `read`(`in`: BinaryDataInputStream): Attribute? {
                // ResXMLTree_attribute
                val ns = `in`.readInt()
                val name = `in`.readInt()
                val rawValue = `in`.readInt()
                // Res_value
                val valueSize = `in`.readUnsignedShort()
                if (valueSize < 8) {
                    return null
                }
                `in`.skipByte() // res0
                val valueType = `in`.readUnsignedByte()
                val valueData = `in`.readInt()

                return Attribute(ns, name, rawValue, valueType, valueData)
            }
        }
    }

    companion object {
        private val TAG = BinaryXmlResourceParser::class.java.name
        private const val NOT_SUPPORTED = "Method is not supported."
    }
}
