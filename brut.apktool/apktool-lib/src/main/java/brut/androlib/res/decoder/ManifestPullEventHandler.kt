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
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer

/**
 * AndroidManifest.xml 事件钩子：
 * 从 manifest 根节点提取包名/版本号，从 uses-sdk 提取 min/target/max 版本，
 * 并在 hideSdkInfo 时把 uses-sdk 标签整体滤掉（返回 true 跳过复制）。
 */
class ManifestPullEventHandler(
    apkInfo: ApkInfo,
    private val mHideSdkInfo: Boolean,
) : ResXmlPullEventHandler(apkInfo) {
    @Throws(XmlPullParserException::class)
    override fun onEvent(`in`: XmlPullParser, out: XmlSerializer): Boolean {
        val parser = `in`
        val depth = parser.depth
        val type = parser.eventType

        if (depth == 1) {
            if (type == XmlPullParser.START_TAG) {
                if (parser.name == "manifest") {
                    parseManifest(parser)
                    return false
                }
            }
        } else if (depth == 2) {
            if (type == XmlPullParser.START_TAG || type == XmlPullParser.END_TAG) {
                if (parser.name == "uses-sdk") {
                    if (type == XmlPullParser.START_TAG) {
                        parseUsesSdk(parser)
                    }
                    return mHideSdkInfo
                }
            }
        }

        return super.onEvent(parser, out)
    }

    /** 读取 manifest 根属性：package / android:versionCode / android:versionName。 */
    private fun parseManifest(`in`: XmlPullParser) {
        val resourcesInfo = mApkInfo.resourcesInfo
        val versionInfo = mApkInfo.versionInfo

        for (i in 0 until `in`.attributeCount) {
            val ns = `in`.getAttributeNamespace(i)

            if (ns.isNullOrEmpty()) {
                val name = `in`.getAttributeName(i)

                if (name == "package") {
                    // 暂存包名，稍后与实际资源包比对。
                    resourcesInfo.packageName = `in`.getAttributeValue(i)
                }
            } else if (ns == ResXmlUtils.ANDROID_RES_NS) {
                val name = `in`.getAttributeName(i)

                if (name == "versionCode") {
                    versionInfo.setVersionCode(`in`.getAttributeValue(i)!!.toInt())
                } else if (name == "versionName") {
                    versionInfo.versionName = `in`.getAttributeValue(i)
                }
            }
        }
    }

    /** 读取 uses-sdk 的 android:min/target/maxSdkVersion（保留原始字符串形式）。 */
    private fun parseUsesSdk(`in`: XmlPullParser) {
        val sdkInfo = mApkInfo.sdkInfo

        for (i in 0 until `in`.attributeCount) {
            val ns = `in`.getAttributeNamespace(i)

            if (ns == ResXmlUtils.ANDROID_RES_NS) {
                val name = `in`.getAttributeName(i)

                if (name == "minSdkVersion") {
                    sdkInfo.minSdkVersion = `in`.getAttributeValue(i)
                } else if (name == "targetSdkVersion") {
                    sdkInfo.targetSdkVersion = `in`.getAttributeValue(i)
                } else if (name == "maxSdkVersion") {
                    sdkInfo.maxSdkVersion = `in`.getAttributeValue(i)
                }
            }
        }
    }
}
