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

import brut.xml.XmlUtils
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.xpath.XPathExpressionException

/**
 * Manifest / network_security_config 等明文 XML 的修补工具（debuggable 注入、
 * 版本属性剥离、provider authorities 的 @string 引用内联展开等）。
 *
 * 所有方法遵循"尽力而为"：任何解析/写入异常静默忽略（与原实现一致）。
 */
object ResXmlUtils {
    /** android 命名空间 URI。 */
    const val ANDROID_RES_NS: String = "http://schemas.android.com/apk/res/android"

    /** res-auto 命名空间 URI。 */
    const val ANDROID_RES_NS_AUTO: String = "http://schemas.android.com/apk/res-auto"

    /** 把 application 的 android:debuggable 置为 true。 */
    @JvmStatic
    fun setApplicationDebugTagTrue(file: File) {
        try {
            val doc = XmlUtils.loadDocument(file)
            val application = doc.getElementsByTagName("application").item(0)
            val attrs = application!!.attributes
            var changed = false

            var debugAttr = attrs.getNamedItem("android:debuggable")
            if (debugAttr == null) {
                debugAttr = doc.createAttribute("android:debuggable")
                debugAttr.nodeValue = "true"
                attrs.setNamedItem(debugAttr)
                changed = true
            } else if (debugAttr.nodeValue != "true") {
                debugAttr.nodeValue = "true"
                changed = true
            }

            if (changed) {
                XmlUtils.saveDocument(doc, file)
            }
        } catch (ignored: IOException) {
        } catch (ignored: SAXException) {
        } catch (ignored: ParserConfigurationException) {
        } catch (ignored: TransformerException) {
        }
    }

    /** 为 application 设置 android:networkSecurityConfig 指向 @xml/network_security_config。 */
    @JvmStatic
    fun setNetworkSecurityConfig(file: File) {
        try {
            val doc = XmlUtils.loadDocument(file)
            val application = doc.getElementsByTagName("application").item(0)
            val attrs = application!!.attributes
            var changed = false

            var netSecConfAttr = attrs.getNamedItem("android:networkSecurityConfig")
            if (netSecConfAttr == null) {
                netSecConfAttr = doc.createAttribute("android:networkSecurityConfig")
                netSecConfAttr.nodeValue = "@xml/network_security_config"
                attrs.setNamedItem(netSecConfAttr)
                changed = true
            } else if (netSecConfAttr.nodeValue != "@xml/network_security_config") {
                netSecConfAttr.nodeValue = "@xml/network_security_config"
                changed = true
            }

            if (changed) {
                XmlUtils.saveDocument(doc, file)
            }
        } catch (ignored: IOException) {
        } catch (ignored: SAXException) {
        } catch (ignored: ParserConfigurationException) {
        } catch (ignored: TransformerException) {
        }
    }

    /** 放宽 network security config：补全 base-config/trust-anchors 的 system 与 user 证书。 */
    @JvmStatic
    fun modNetworkSecurityConfig(file: File) {
        try {
            val doc = if (file.exists()) {
                XmlUtils.loadDocument(file).also { it.documentElement.normalize() }
            } else {
                XmlUtils.newDocument()
            }
            var changed = false

            var root = doc.getElementsByTagName("network-security-config").item(0) as Element?
            if (root == null) {
                root = doc.createElement("network-security-config")
                doc.appendChild(root)
                changed = true
            }

            var baseConfig = root!!.getElementsByTagName("base-config").item(0) as Element?
            if (baseConfig == null) {
                baseConfig = doc.createElement("base-config")
                root.appendChild(baseConfig)
                changed = true
            }

            var trustAnchors = baseConfig!!.getElementsByTagName("trust-anchors").item(0) as Element?
            if (trustAnchors == null) {
                trustAnchors = doc.createElement("trust-anchors")
                baseConfig.appendChild(trustAnchors)
                changed = true
            }

            val certificates = trustAnchors!!.getElementsByTagName("certificates")
            var hasSystemCert = false
            var hasUserCert = false
            for (i in 0 until certificates.length) {
                val cert = certificates.item(i) as Element
                when (cert.getAttribute("src")) {
                    "system" -> hasSystemCert = true
                    "user" -> hasUserCert = true
                }
            }

            if (!hasSystemCert) {
                val certSystem = doc.createElement("certificates")
                certSystem.setAttribute("src", "system")
                trustAnchors.appendChild(certSystem)
                changed = true
            }

            if (!hasUserCert) {
                val certUser = doc.createElement("certificates")
                certUser.setAttribute("src", "user")
                trustAnchors.appendChild(certUser)
                changed = true
            }

            if (changed) {
                XmlUtils.saveDocument(doc, file)
            }
        } catch (ignored: IOException) {
        } catch (ignored: SAXException) {
        } catch (ignored: ParserConfigurationException) {
        } catch (ignored: TransformerException) {
        }
    }

    /** 从 manifest 根节点移除 android:versionCode 与 android:versionName。 */
    @JvmStatic
    fun removeManifestVersions(file: File) {
        try {
            val doc = XmlUtils.loadDocument(file)
            val manifest = doc.firstChild
            val attrs = manifest!!.attributes
            var changed = false

            if (attrs.getNamedItem("android:versionCode") != null) {
                attrs.removeNamedItem("android:versionCode")
                changed = true
            }

            if (attrs.getNamedItem("android:versionName") != null) {
                attrs.removeNamedItem("android:versionName")
                changed = true
            }

            if (changed) {
                XmlUtils.saveDocument(doc, file)
            }
        } catch (ignored: IOException) {
        } catch (ignored: SAXException) {
        } catch (ignored: ParserConfigurationException) {
        } catch (ignored: TransformerException) {
        }
    }

    /**
     * AOSP 缺陷：provider authorities / intent-filter scheme 中的 @string 引用会导致
     * public 资源无法参与安装属性；这里把引用替换为 strings.xml 中的字面值。
     */
    @JvmStatic
    fun fixingPublicAttrsInProviderAttributes(file: File) {
        try {
            val doc = XmlUtils.loadDocument(file, true)
            var changed = false

            var expression = "/manifest/application/provider/@android:authorities"
            var nodes = XmlUtils.evaluateXPath(doc, expression, NodeList::class.java)

            for (i in 0 until nodes!!.length) {
                if (replaceStringReference(file, nodes.item(i))) {
                    changed = true
                }
            }

            expression = "/manifest/application/activity/intent-filter/data/@android:scheme"
            nodes = XmlUtils.evaluateXPath(doc, expression, NodeList::class.java)

            for (i in 0 until nodes!!.length) {
                if (replaceStringReference(file, nodes.item(i))) {
                    changed = true
                }
            }

            if (changed) {
                XmlUtils.saveDocument(doc, file)
            }
        } catch (ignored: IOException) {
        } catch (ignored: SAXException) {
        } catch (ignored: ParserConfigurationException) {
        } catch (ignored: XPathExpressionException) {
        } catch (ignored: TransformerException) {
        }
    }

    /** 节点值若为字符串引用则替换为字面值；替换成功返回 true。 */
    private fun replaceStringReference(file: File, node: Node): Boolean {
        val replacement = pullValueFromStrings(file.parentFile, node.nodeValue) ?: return false

        node.nodeValue = replacement
        return true
    }

    /** 从 res/values/strings.xml 查找引用（@string/foo）对应的字面值。 */
    @JvmStatic
    fun pullValueFromStrings(apkDir: File?, key: String?): String? =
        pullValueFromXml(File(apkDir, "res/values/strings.xml"), "string", key)

    /** 从 res/values/integers.xml 查找引用（@integer/foo）对应的字面值。 */
    @JvmStatic
    fun pullValueFromIntegers(apkDir: File?, key: String?): String? =
        pullValueFromXml(File(apkDir, "res/values/integers.xml"), "integer", key)

    private fun pullValueFromXml(file: File, type: String, keyArg: String?): String? {
        var key = keyArg
        if (!file.isFile || key == null || !key.contains('@')) {
            return null
        }

        key = key.replace("@$type/", "")
        return try {
            val doc = XmlUtils.loadDocument(file)
            val expression = String.format("/resources/%s[@name='%s']/text()", type, key)
            XmlUtils.evaluateXPath(doc, expression, String::class.java)
        } catch (ignored: IOException) {
            null
        } catch (ignored: SAXException) {
            null
        } catch (ignored: ParserConfigurationException) {
            null
        } catch (ignored: XPathExpressionException) {
            null
        }
    }
}
