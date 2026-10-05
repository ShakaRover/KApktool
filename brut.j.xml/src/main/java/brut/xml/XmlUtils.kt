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
package brut.xml

import brut.common.Log
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext
import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.TransformerException
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

/**
 * DOM/XML 工具集：创建解析器、读写 Document、执行 XPath。
 *
 * 所有 DocumentBuilder 均开启安全特性：禁止 DOCTYPE 声明、不加载外部 DTD，
 * 以阻断 XXE（XML 外部实体注入）攻击面。
 */
object XmlUtils {
    private const val TAG = ""

    /** 固定的 UTF-8 XML 声明。 */
    @JvmField
    val XML_PROLOG: String = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"

    /** 保留前缀 xml。 */
    @JvmField
    val XML_PREFIX: String = "xml"

    /** XML 命名空间 URI。 */
    @JvmField
    val XML_URI: String = "http://www.w3.org/XML/1998/namespace"

    /** 保留前缀 xmlns。 */
    @JvmField
    val XMLNS_PREFIX: String = "xmlns"

    /** xmlns 命名空间 URI。 */
    @JvmField
    val XMLNS_URI: String = "http://www.w3.org/2000/xmlns/"

    private const val FEATURE_DISALLOW_DOCTYPE_DECL =
        "http://apache.org/xml/features/disallow-doctype-decl"
    private const val FEATURE_LOAD_EXTERNAL_DTD =
        "http://apache.org/xml/features/nonvalidating/load-external-dtd"

    private fun newDocumentBuilder(nsAware: Boolean): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = nsAware
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature(FEATURE_DISALLOW_DOCTYPE_DECL, true)
        factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false)

        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        } catch (ignored: IllegalArgumentException) {
            Log.w(TAG, "JAXP 1.5 Support is required to validate XML")
        }

        return factory.newDocumentBuilder()
    }

    /** 创建非命名空间感知的空 Document。 */
    @JvmStatic
    @Throws(SAXException::class, ParserConfigurationException::class)
    fun newDocument(): Document = newDocument(false)

    /** 创建空 Document，可选命名空间感知。 */
    @JvmStatic
    @Throws(SAXException::class, ParserConfigurationException::class)
    fun newDocument(nsAware: Boolean): Document = newDocumentBuilder(nsAware).newDocument()

    /** 从字符串解析 Document（非命名空间感知）。 */
    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun parseDocument(xml: String): Document = parseDocument(xml, false)

    /** 从字符串解析 Document。 */
    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun parseDocument(xml: String, nsAware: Boolean): Document {
        val builder = newDocumentBuilder(nsAware)
        return builder.parse(InputSource(StringReader(xml)))
    }

    /** 从文件加载 Document（非命名空间感知）。 */
    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun loadDocument(file: File): Document = loadDocument(file, false)

    /**
     * 从文件加载 Document。
     *
     * 故意不使用 parse(File)：部分实现下它不会及时关闭文件句柄，
     * 这里用显式流保证解析后立刻释放。
     */
    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun loadDocument(file: File, nsAware: Boolean): Document {
        val builder = newDocumentBuilder(nsAware)
        Files.newInputStream(file.toPath()).use { `in` ->
            return builder.parse(InputSource(`in`))
        }
    }

    /** 写出 Document：固定 UTF-8 声明 + 系统换行，省略序列化器自带的 XML 声明。 */
    @JvmStatic
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class, TransformerException::class)
    fun saveDocument(doc: Document, file: File) {
        val factory = TransformerFactory.newInstance()
        val transformer = factory.newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")

        val xmlDecl = XML_PROLOG.toByteArray(StandardCharsets.US_ASCII)
        val newLine = System.lineSeparator().toByteArray(StandardCharsets.US_ASCII)

        Files.newOutputStream(file.toPath()).use { out ->
            out.write(xmlDecl)
            out.write(newLine)
            transformer.transform(DOMSource(doc), StreamResult(out))
            out.write(newLine)
        }
    }

    /** 执行 XPath 并按 returnType 返回 Node/NodeList/String/Double/Boolean 结果。 */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    @Throws(XPathExpressionException::class)
    fun <T> evaluateXPath(doc: Document, expression: String, returnType: Class<T>): T? {
        val type: QName = when (returnType) {
            Node::class.java -> XPathConstants.NODE
            NodeList::class.java -> XPathConstants.NODESET
            String::class.java -> XPathConstants.STRING
            java.lang.Double::class.java -> XPathConstants.NUMBER
            java.lang.Boolean::class.java -> XPathConstants.BOOLEAN
            else -> throw IllegalArgumentException(
                "Unexpected return type: " + returnType.name
            )
        }

        val xPath = XPathFactory.newInstance().newXPath()
        xPath.namespaceContext = object : NamespaceContext {
            override fun getNamespaceURI(prefix: String): String? =
                doc.lookupNamespaceURI(prefix)

            override fun getPrefix(namespaceURI: String): String? =
                doc.lookupPrefix(namespaceURI)

            override fun getPrefixes(namespaceURI: String): Iterator<String> {
                val prefix = getPrefix(namespaceURI)
                return if (prefix != null) {
                    Collections.singleton(prefix).iterator()
                } else {
                    Collections.emptyIterator()
                }
            }
        }

        return xPath.evaluate(expression, doc, type) as T?
    }
}
