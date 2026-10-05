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

import brut.androlib.meta.ApkInfo
import brut.androlib.res.xml.ResXmlUtils
import brut.xmlpull.XmlPullUtils.EventHandler
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer

/**
 * 二进制 XML 转写事件钩子：顺带收集 android:featureFlag 属性引用的特性名。
 *
 * 总是返回 false（事件照常复制），只做旁路记录。
 */
open class ResXmlPullEventHandler(
    protected val mApkInfo: ApkInfo,
) : EventHandler {
    @Throws(XmlPullParserException::class)
    override fun onEvent(`in`: XmlPullParser, out: XmlSerializer): Boolean {
        val parser = `in`
        val depth = parser.depth
        val type = parser.eventType

        if (depth > 1 && type == XmlPullParser.START_TAG) {
            for (i in 0 until parser.attributeCount) {
                val ns = parser.getAttributeNamespace(i)

                if (ns == ResXmlUtils.ANDROID_RES_NS) {
                    val name = parser.getAttributeName(i)

                    if (name == "featureFlag") {
                        var value = parser.getAttributeValue(i)

                        if (value.isNullOrEmpty()) {
                            continue
                        }
                        if (value.startsWith("!")) {
                            value = value.substring(1)
                            if (value.isEmpty()) {
                                continue
                            }
                        }

                        mApkInfo.featureFlags.add(value)
                    }
                }
            }
        }

        return false
    }
}
