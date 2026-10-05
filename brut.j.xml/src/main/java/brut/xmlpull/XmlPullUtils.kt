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
package brut.xmlpull

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.IOException

/**
 * XmlPull 事件流复制工具：把 [XmlPullParser] 的事件逐一转发到 [XmlSerializer]。
 *
 * 可注入 [EventHandler] 在复制过程中拦截/改写事件（返回 true 表示该事件已被处理）。
 */
object XmlPullUtils {
    private const val PROPERTY_XMLDECL_STANDALONE =
        "http://xmlpull.org/v1/doc/properties.html#xmldecl-standalone"

    /** 事件拦截回调：在事件被复制前获得处理机会。 */
    interface EventHandler {
        /** 处理当前事件；返回 true 表示已消费、跳过默认复制。 */
        @Throws(XmlPullParserException::class)
        fun onEvent(`in`: XmlPullParser, out: XmlSerializer): Boolean
    }

    /** 完整复制事件流。 */
    @JvmStatic
    @Throws(XmlPullParserException::class, IOException::class)
    fun copy(`in`: XmlPullParser, out: XmlSerializer) {
        copy(`in`, out, null)
    }

    /** 带事件拦截器地复制事件流。 */
    @JvmStatic
    @Throws(XmlPullParserException::class, IOException::class)
    fun copy(`in`: XmlPullParser, out: XmlSerializer, handler: EventHandler?) {
        val standalone = `in`.getProperty(PROPERTY_XMLDECL_STANDALONE) as? Boolean

        // 部分解析器已消费掉 START_DOCUMENT 事件，这里手动补发以保持一致性。
        if (`in`.eventType == XmlPullParser.START_DOCUMENT) {
            out.startDocument(`in`.inputEncoding, standalone)
        }

        while (true) {
            val event = `in`.nextToken()
            if (event == -1) {
                break
            }
            if (event == XmlPullParser.END_DOCUMENT) {
                out.endDocument()
                break
            }
            if (event == XmlPullParser.START_DOCUMENT) {
                out.startDocument(`in`.inputEncoding, standalone)
                continue
            }
            if (handler != null && handler.onEvent(`in`, out)) {
                continue
            }
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (!`in`.getFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES)) {
                        val nsStart = `in`.getNamespaceCount(`in`.depth - 1)
                        val nsEnd = `in`.getNamespaceCount(`in`.depth)
                        for (i in nsStart until nsEnd) {
                            out.setPrefix(`in`.getNamespacePrefix(i), `in`.getNamespaceUri(i))
                        }
                    }
                    out.startTag(normalizeNamespace(`in`.namespace), `in`.name)
                    for (i in 0 until `in`.attributeCount) {
                        out.attribute(
                            normalizeNamespace(`in`.getAttributeNamespace(i)),
                            `in`.getAttributeName(i),
                            `in`.getAttributeValue(i),
                        )
                    }
                }
                XmlPullParser.END_TAG ->
                    out.endTag(normalizeNamespace(`in`.namespace), `in`.name)
                XmlPullParser.TEXT ->
                    out.text(`in`.text)
                XmlPullParser.CDSECT ->
                    out.cdsect(`in`.text)
                XmlPullParser.ENTITY_REF ->
                    out.entityRef(`in`.name)
                XmlPullParser.IGNORABLE_WHITESPACE ->
                    out.ignorableWhitespace(`in`.text)
                XmlPullParser.PROCESSING_INSTRUCTION ->
                    out.processingInstruction(`in`.text)
                XmlPullParser.COMMENT ->
                    out.comment(`in`.text)
                XmlPullParser.DOCDECL ->
                    out.docdecl(`in`.text)
                else -> throw IllegalStateException("Unknown event: $event")
            }
        }
    }

    /**
     * 部分解析器在不支持命名空间时返回空串，会让序列化器困惑；统一归一为 null。
     */
    private fun normalizeNamespace(namespace: String?): String? =
        if (!namespace.isNullOrEmpty()) namespace else null
}
