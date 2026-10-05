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
package brut.androlib

import brut.androlib.meta.ApkInfo
import brut.directory.ExtFile
import brut.util.OSDetection
import brut.xml.XmlUtils
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import javax.imageio.ImageIO

/**
 * testapp 全量构建-解码集成测试。
 *
 * 类级初始化先把 testapp 资源解包并构建为 APK，再解码到实验目录，
 * 随后各测试逐项验证解码结果：manifest 结构、values 资源（各语言/限定符/
 * BCP-47 变体）、布局 XML、字体、9-patch 像素、二进制目录、assets、
 * so 库、多 dex 与 STORED 存储条目等。
 */
class BuildAndDecodeApkTest : BaseTest() {
    companion object {
        // 类级共享的构建产物 APK（带目录视图），@BeforeClass 生成、@AfterClass 关闭。
        private lateinit var sTestApk: ExtFile

        /**
         * 类级准备：解包 testapp 对照组目录，构建 testapp.apk，
         * 并将其解码到实验目录，供所有测试方法使用。
         */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            sTestOrigDir = File(sTmpDir!!, "testapp-orig")
            sTestNewDir = File(sTmpDir!!, "testapp-new")

            log("Unpacking testapp...")
            copyResourceDir(BuildAndDecodeApkTest::class.java, "testapp", sTestOrigDir!!)

            sConfig!!.isVerbose = true

            log("Building testapp.apk...")
            sTestApk = ExtFile(sTmpDir!!, "testapp.apk")
            ApkBuilder(sTestOrigDir!!, sConfig!!).build(sTestApk)

            log("Decoding testapp.apk...")
            ApkDecoder(sTestApk, sConfig!!).decode(sTestNewDir!!)
        }

        /** 类级清理：关闭构建产物 APK 的目录句柄。 */
        @AfterClass
        @JvmStatic
        @Throws(Exception::class)
        fun afterClass() {
            sTestApk.close()
        }

        /** 判断 ARGB 像素是否完全透明（alpha 通道为 0）。 */
        private fun isTransparent(pixel: Int): Boolean {
            return (pixel shr 24) == 0
        }
    }

    /** 验证解码流程执行完毕后实验目录确实存在。 */
    @Test
    fun buildAndDecodeTest() {
        assertTrue(sTestNewDir!!.isDirectory)
    }

    /** 验证 apktool.yml 中记录了 manifest 里声明的特性标志。 */
    @Test
    @Throws(Exception::class)
    fun confirmFeatureFlagsRecorded() {
        val testInfo = ApkInfo.load(File(sTestNewDir!!, "apktool.yml"))
        assertTrue(testInfo.featureFlags.contains("brut.feature.permission"))
        assertTrue(testInfo.featureFlags.contains("brut.feature.activity"))
    }

    /** 验证零字节 jpg 文件不会导致整个 jpg 扩展名进入 doNotCompress。 */
    @Test
    @Throws(Exception::class)
    fun confirmZeroByteFileExtensionIsNotStored() {
        val testInfo = ApkInfo.load(File(sTestNewDir!!, "apktool.yml"))
        assertFalse(testInfo.doNotCompress.contains("jpg"))
    }

    /** 验证零字节文件本身以完整路径形式被记录为 STORED。 */
    @Test
    @Throws(Exception::class)
    fun confirmZeroByteFileIsStored() {
        val testInfo = ApkInfo.load(File(sTestNewDir!!, "apktool.yml"))
        assertTrue(testInfo.doNotCompress.contains("assets/0byte_file.jpg"))
    }

    /** 对比解码出的 AndroidManifest.xml 与对照组是否一致。 */
    @Test
    @Throws(Exception::class)
    fun confirmManifestStructureTest() {
        compareXmlFiles("AndroidManifest.xml")
    }

    /** 验证 manifest 中 platformBuildVersion 属性保留、compileSdkVersion 相关属性被剥离。 */
    @Test
    @Throws(Exception::class)
    fun confirmPlatformManifestValuesTest() {
        val doc = XmlUtils.loadDocument(File(sTestNewDir!!, "AndroidManifest.xml"))

        val platformBuildVersionNameExpr = "/manifest/@platformBuildVersionName"
        val platformBuildVersionNameValue = XmlUtils.evaluateXPath(doc, platformBuildVersionNameExpr, String::class.java)
        assertEquals("6.0-2438415", platformBuildVersionNameValue)

        val platformBuildVersionCodeExpr = "/manifest/@platformBuildVersionCode"
        val platformBuildVersionCodeValue = XmlUtils.evaluateXPath(doc, platformBuildVersionCodeExpr, String::class.java)
        assertEquals("23", platformBuildVersionCodeValue)

        val compileSdkVersionExpr = "/manifest/@compileSdkVersion"
        val compileSdkVersionNode = XmlUtils.evaluateXPath(doc, compileSdkVersionExpr, Node::class.java)
        assertNull("compileSdkVersion should have been stripped", compileSdkVersionNode)

        val compileSdkVersionCodenameExpr = "/manifest/@compileSdkVersionCodename"
        val compileSdkVersionCodenameNode = XmlUtils.evaluateXPath(doc, compileSdkVersionCodenameExpr, Node::class.java)
        assertNull("compileSdkVersionCodename should have been stripped", compileSdkVersionCodenameNode)
    }

    /** 对比 anims 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesAnimsTest() {
        compareValuesFiles("values-mcc001/anims.xml")
    }

    /** 对比 arrays 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesArraysTest() {
        compareValuesFiles("values-mcc001/arrays.xml")
    }

    /** 对比存在类型转换的 arrays 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesArraysCastingTest() {
        compareValuesFiles("values-mcc002/arrays.xml")
        compareValuesFiles("values-mcc003/arrays.xml")
    }

    /** 对比 attrs 声明文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesAttrsTest() {
        compareValuesFiles("values/attrs.xml")
    }

    /** 对比 bools 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesBoolsTest() {
        compareValuesFiles("values-mcc001/bools.xml")
    }

    /** 对比 colors 资源文件（默认与 mcc001 目录）。 */
    @Test
    @Throws(Exception::class)
    fun valuesColorsTest() {
        compareValuesFiles("values/colors.xml")
        compareValuesFiles("values-mcc001/colors.xml")
    }

    /** 对比 dimens 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesDimensTest() {
        compareValuesFiles("values-mcc001/dimens.xml")
    }

    /** 对比 drawables 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesDrawablesTest() {
        compareValuesFiles("values-mcc001/drawables.xml")
    }

    /** 对比 ids 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesIdsTest() {
        compareValuesFiles("values-mcc001/ids.xml")
    }

    /** 对比 integers 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesIntegersTest() {
        compareValuesFiles("values/integers.xml")
        compareValuesFiles("values-mcc001/integers.xml")
    }

    /** 对比 layouts 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesLayoutsTest() {
        compareValuesFiles("values-mcc001/layouts.xml")
    }

    /** 对比 plurals 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesPluralsTest() {
        compareValuesFiles("values-mcc001/plurals.xml")
    }

    /** 对比 overlayable 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesOverlayableTest() {
        compareValuesFiles("values/overlayable.xml")
    }

    /** 对比 strings 资源文件（默认与 mcc001 目录）。 */
    @Test
    @Throws(Exception::class)
    fun valuesStringsTest() {
        compareValuesFiles("values/strings.xml")
        compareValuesFiles("values-mcc001/strings.xml")
    }

    /** 对比 styles 资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesStylesTest() {
        compareValuesFiles("values-mcc001/styles.xml")
    }

    /** 对比超长字符串所在的 en 语言资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesExtraLongTest() {
        compareValuesFiles("values-en/strings.xml")
    }

    /** 验证恰为 0x7FFF 长度的字符串保持原样，未被替换为 STRING_TOO_LARGE。 */
    @Test
    @Throws(Exception::class)
    fun valuesMaxLengthTest() {
        val doc = XmlUtils.loadDocument(File(sTestNewDir!!, "res/values-en/strings.xml"))

        // long_string_32767 应为恰好 0x7FFF 个 "a"，
        // 这是 UTF-8 字符串允许的最大长度；
        // 超过该长度的字符串会被替换为 STRING_TOO_LARGE。
        // valuesExtraLongTest 已覆盖该场景，但这里针对该边界单独验证。
        val expression = "/resources/string[@name='long_string_32767']/text()"
        val value = XmlUtils.evaluateXPath(doc, expression, String::class.java)
        assertEquals(0x7FFF, value!!.length)
    }

    /** 对比带语法性别限定符（neuter/feminine）的语言资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesGrammaticalGenderTest() {
        compareValuesFiles("values-neuter/strings.xml")
        compareValuesFiles("values-feminine/strings.xml")
    }

    /** 回归 #702：验证 mcc+mnc 组合限定符目录下的字符串正确解码。 */
    @Test
    @Throws(Exception::class)
    fun bug702Test() {
        compareValuesFiles("values-mcc001-mnc00/strings.xml")
    }

    /** 对比含交叉类型引用的字符串资源文件。 */
    @Test
    @Throws(Exception::class)
    fun valuesReferencesTest() {
        compareValuesFiles("values-mcc002/strings.xml")
    }

    /** 验证同目录内跨类型（string/integer/bool）引用正确解码。 */
    @Test
    @Throws(Exception::class)
    fun crossTypeTest() {
        compareValuesFiles("values-mcc003/strings.xml")
        compareValuesFiles("values-mcc003/integers.xml")
        compareValuesFiles("values-mcc003/bools.xml")
    }

    /** 验证超长多限定符目录名能被正确还原并解码。 */
    @Test
    @Throws(Exception::class)
    fun qualifiersTest() {
        compareValuesFiles(
            "values-mcc004-mnc04-en-rUS-ldrtl-sw100dp-w200dp-h300dp" +
                "-long-round-highdr-land-desk-night-xhdpi-finger-keyssoft-12key" +
                "-navhidden-dpad-v26/strings.xml",
        )
    }

    /** 验证 mnc 高位被省略（mnc00）时的目录名解码。 */
    @Test
    @Throws(Exception::class)
    fun shortendedMncTest() {
        compareValuesFiles("values-mcc001-mnc01/strings.xml")
    }

    /** 验证无 mcc 前缀的 mnc 目录（HTC 样式）解码。 */
    @Test
    @Throws(Exception::class)
    fun shortMncHtcTest() {
        compareValuesFiles("values-mnc01/strings.xml")
    }

    /** 验证短 mnc 在 API 26 编码规则下的目录名解码。 */
    @Test
    @Throws(Exception::class)
    fun shortMncv2Test() {
        compareValuesFiles("values-mcc238-mnc06/strings.xml")
    }

    /** 验证三位数长 mnc 的目录名解码。 */
    @Test
    @Throws(Exception::class)
    fun longMncTest() {
        compareValuesFiles("values-mcc238-mnc870/strings.xml")
    }

    /** 验证 watch（anydpi 相关）限定符目录解码。 */
    @Test
    @Throws(Exception::class)
    fun anyDpiTest() {
        compareValuesFiles("values-watch/strings.xml")
    }

    /** 验证三字符语言码（packed 编码）目录 ast-rES 解码。 */
    @Test
    @Throws(Exception::class)
    fun packed3CharsTest() {
        compareValuesFiles("values-ast-rES/strings.xml")
    }

    /** 验证 ldrtl（从右向左）限定符目录解码。 */
    @Test
    @Throws(Exception::class)
    fun rightToLeftTest() {
        compareValuesFiles("values-ldrtl/strings.xml")
    }

    /** 验证三字母语言码按 BCP-47 规则编码的目录解码。 */
    @Test
    @Throws(Exception::class)
    fun threeLetterLangBcp47Test() {
        compareValuesFiles("values-ast/strings.xml")
    }

    /** 验证 Android O 引入的语言限定符目录解码。 */
    @Test
    @Throws(Exception::class)
    fun androidOStringTest() {
        compareValuesFiles("values-ast/strings.xml")
    }

    /** 验证两字母语言码（fr）不被误当作 BCP-47 编码，目录仍为 values-fr。 */
    @Test
    fun twoLetterNotHandledAsBcpTest() {
        assertTrue(File(sTestNewDir!!, "res/values-fr").isDirectory)
    }

    /** 验证两字母语言+地区（en-rUS）目录解码。 */
    @Test
    @Throws(Exception::class)
    fun twoLetterLangBcp47Test() {
        compareValuesFiles("values-en-rUS/strings.xml")
    }

    /** 验证含文字（script）的 BCP-47 语言标签目录解码。 */
    @Test
    @Throws(Exception::class)
    fun scriptBcp47Test() {
        compareValuesFiles("values-b+en+Latn+US/strings.xml")
    }

    /** 验证地区型 BCP-47 语言标签（419 拉丁美洲）目录解码。 */
    @Test
    @Throws(Exception::class)
    fun regionLocaleBcp47Test() {
        compareValuesFiles("values-b+en+Latn+419/strings.xml")
    }

    /** 验证数字地区码 BCP-47 语言标签目录解码。 */
    @Test
    @Throws(Exception::class)
    fun numericalRegionBcp47Test() {
        compareValuesFiles("values-b+eng+419/strings.xml")
    }

    /** 验证带变体（variant）的 BCP-47 语言标签目录解码。 */
    @Test
    @Throws(Exception::class)
    fun variantBcp47Test() {
        compareValuesFiles("values-b+en+US+posix/strings.xml")
    }

    /** 验证语言+变体形式的 BCP-47 目录解码（iw+660）。 */
    @Test
    @Throws(Exception::class)
    fun valuesBcp47LanguageVariantTest() {
        compareValuesFiles("values-b+iw+660/strings.xml")
    }

    /** 验证语言+文字+地区+变体完整组合的 BCP-47 目录解码。 */
    @Test
    @Throws(Exception::class)
    fun valuesBcp47LanguageScriptRegionVariantTest() {
        compareValuesFiles("values-b+ast+Latn+IT+arevela/strings.xml")
        compareValuesFiles("values-b+ast+Hant+IT+arabext/strings.xml")
    }

    /** 验证 API 23 新增的 round/notround 屏幕形态限定符目录解码。 */
    @Test
    @Throws(Exception::class)
    fun api23ConfigurationsTest() {
        compareValuesFiles("values-round/strings.xml")
        compareValuesFiles("values-notround/strings.xml")
    }

    /** 验证 API 26 新增的 widecg/lowdr/nowidecg/vrheadset 限定符目录解码。 */
    @Test
    @Throws(Exception::class)
    fun api26ConfigurationsTest() {
        compareValuesFiles("values-widecg-v26/strings.xml")
        compareValuesFiles("values-lowdr-v26/strings.xml")
        compareValuesFiles("values-nowidecg-v26/strings.xml")
        compareValuesFiles("values-vrheadset-v26/strings.xml")
    }

    /** 验证以美元符开头的资源文件名能原样导出为 XML。 */
    @Test
    @Throws(Exception::class)
    fun leadingDollarSignResourceNameTest() {
        compareXmlFiles("res/drawable/\$avd_hide_password__0.xml")
        compareXmlFiles("res/drawable/\$avd_show_password__0.xml")
        compareXmlFiles("res/drawable/\$avd_show_password__1.xml")
        compareXmlFiles("res/drawable/\$avd_show_password__2.xml")
        compareXmlFiles("res/drawable/avd_show_password.xml")
    }

    /** 验证 #1662：font 目录下的 otf 文件不被编码为 values，仅 xml 被解码。 */
    @Test
    @Throws(Exception::class)
    fun fontTest() {
        val fontXml = File(sTestNewDir!!, "res/font/lobster.xml")
        val fontFile = File(sTestNewDir!!, "res/font/lobster_regular.otf")

        // 依据 #1662，确保字体文件未被编码。
        assertTrue(fontXml.isFile)
        compareXmlFiles("res/font/lobster.xml")

        // 若正确跳过字体（otf）文件的解码，则该 values 文件不应存在。
        assertFalse(File(sTestNewDir!!, "res/values/fonts.xml").isFile)
        assertTrue(fontFile.isFile)
    }

    /** 验证布局中以 XML 引用形式书写的属性正确解码。 */
    @Test
    @Throws(Exception::class)
    fun xmlReferenceAttributeTest() {
        compareXmlFiles("res/layout/issue1040.xml")
    }

    /** 验证布局中自定义（非 android 命名空间）属性正确解码。 */
    @Test
    @Throws(Exception::class)
    fun xmlCustomAttributeTest() {
        compareXmlFiles("res/layout/issue1063.xml")
    }

    /** 验证 #1157：非 android 前缀的自定义属性保持原名解码。 */
    @Test
    @Throws(Exception::class)
    fun xmlCustomAttrsNotAndroidTest() {
        compareXmlFiles("res/layout/issue1157.xml")
    }

    /** 验证 #1274：match_parent 字面值不被写成 fill_parent。 */
    @Test
    @Throws(Exception::class)
    fun xmlExpectMatchParentTest() {
        compareXmlFiles("res/layout/issue1274.xml")
    }

    /** 验证 #1674：布局中文本属性写法统一解码。 */
    @Test
    @Throws(Exception::class)
    fun xmlUniformAutoTextTest() {
        compareXmlFiles("res/layout/issue1674.xml")
    }

    /** 验证 navigation 资源图 XML 正确解码。 */
    @Test
    @Throws(Exception::class)
    fun navigationResourceTest() {
        compareXmlFiles("res/navigation/nav_graph.xml")
    }

    /** 验证 xml 目录下的 xsd 文件原样解码。 */
    @Test
    @Throws(Exception::class)
    fun xmlXsdFileTest() {
        compareXmlFiles("res/xml/ww_box_styles_schema.xsd")
    }

    /** 验证 xml 中字面值属性解码。 */
    @Test
    @Throws(Exception::class)
    fun xmlLiteralsTest() {
        compareXmlFiles("res/xml/literals.xml")
    }

    /** 验证 xml 中资源引用属性解码。 */
    @Test
    @Throws(Exception::class)
    fun xmlReferencesTest() {
        compareXmlFiles("res/xml/references.xml")
    }

    /** 验证无障碍服务配置文件 XML 正确解码。 */
    @Test
    @Throws(Exception::class)
    fun xmlAccessibilityTest() {
        compareXmlFiles("res/xml/accessibility_service_config.xml")
    }

    /** 对比 nodpi 密度目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableNoDpiTest() {
        compareBinaryFolder("res/drawable-nodpi")
    }

    /** 对比 anydpi 密度目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableAnyDpiTest() {
        compareBinaryFolder("res/drawable-anydpi")
    }

    /** 对比自定义数值密度（534dpi）目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableNumberedDpiTest() {
        compareBinaryFolder("res/drawable-534dpi")
    }

    /** 对比 ldpi 密度目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableLdpiTest() {
        compareBinaryFolder("res/drawable-ldpi")
    }

    /** 对比 mdpi 密度目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableMdpiTest() {
        compareBinaryFolder("res/drawable-mdpi")
    }

    /** 对比 tvdpi 密度目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableTvdpiTest() {
        compareBinaryFolder("res/drawable-tvdpi")
    }

    /** 对比 xhdpi 密度目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableXhdpiTest() {
        compareBinaryFolder("res/drawable-xhdpi")
    }

    /** 对比解码前后 9-patch 图片的关键像素点（透明、黑线、蓝色块）。 */
    @Test
    @Throws(Exception::class)
    fun ninePatchImageColorTest() {
        val fileName = "res/drawable-xhdpi/ninepatch.9.png"

        val control = File(sTestOrigDir!!, fileName)
        val test = File(sTestNewDir!!, fileName)

        val controlImage = ImageIO.read(control)
        val testImage = ImageIO.read(test)

        // 从 0,0 开始——应为透明空白区域。
        assertEquals(controlImage.getRGB(0, 0), testImage.getRGB(0, 0))

        // 再看 30,0——黑色像素。
        assertEquals(controlImage.getRGB(30, 0), testImage.getRGB(30, 0))

        // 最后 30,30——蓝色像素。
        assertEquals(controlImage.getRGB(30, 30), testImage.getRGB(30, 30))
    }

    /** 回归 #1508：验证按钮 9-patch 解码后各区域像素不变。 */
    @Test
    @Throws(Exception::class)
    fun issue1508Test() {
        val fileName = "res/drawable-xhdpi/btn_zoom_up_normal.9.png"

        val control = File(sTestOrigDir!!, fileName)
        val test = File(sTestNewDir!!, fileName)

        val controlImage = ImageIO.read(control)
        val testImage = ImageIO.read(test)

        // 0, 0 = 透明
        assertEquals(controlImage.getRGB(0, 0), testImage.getRGB(0, 0))

        // 30, 0 = 黑色线条
        assertEquals(controlImage.getRGB(0, 30), testImage.getRGB(0, 30))

        // 30, 30 = 灰色按钮区域
        assertEquals(controlImage.getRGB(30, 30), testImage.getRGB(30, 30))
    }

    /** 回归 #1511：整幅图像逐像素对比，防止丢失光学边界（Optical Bounds）。 */
    @Test
    @Throws(Exception::class)
    fun issue1511Test() {
        val fileName = "res/drawable-xxhdpi/textfield_activated_holo_dark.9.png"

        val control = File(sTestOrigDir!!, fileName)
        val test = File(sTestNewDir!!, fileName)

        val controlImage = ImageIO.read(control)
        val testImage = ImageIO.read(test)

        // 检查整幅图像，此处绝不允许出错。
        val w = controlImage.width
        val h = controlImage.height

        val controlImageGrid = controlImage.getRGB(0, 0, w, h, null, 0, w)
        val testImageGrid = testImage.getRGB(0, 0, w, h, null, 0, w)

        for (i in 0 until controlImageGrid.size) {
            assertEquals("Image lost Optical Bounds at i = " + i, controlImageGrid[i], testImageGrid[i])
        }
    }

    /** 对多张 xxhdpi 9-patch 逐行逐列验证 npTc 分块信息未丢失。 */
    @Test
    @Throws(Exception::class)
    fun robust9patchTest() {
        val ninePatches = arrayOf(
            "ic_notification_overlay.9.png",
            "status_background.9.png",
            "search_bg_transparent.9.png",
            "screenshot_panel.9.png",
            "recents_lower_gradient.9.png",
        )

        for (ninePatch in ninePatches) {
            val fileName = "res/drawable-xxhdpi/" + ninePatch

            val control = File(sTestOrigDir!!, fileName)
            val test = File(sTestNewDir!!, fileName)

            val controlImage = ImageIO.read(control)
            val testImage = ImageIO.read(test)

            val w = controlImage.width
            val h = controlImage.height

            // 检查整条水平线。
            for (i in 1 until w) {
                if (isTransparent(controlImage.getRGB(i, 0))) {
                    assertTrue(isTransparent(testImage.getRGB(i, 0)))
                } else {
                    assertEquals(
                        "Image lost npTc chunk on image " + ninePatch + " at (x, y) (" + i + "," + 0 + ")",
                        controlImage.getRGB(i, 0), testImage.getRGB(i, 0),
                    )
                }
            }

            // 检查整条垂直线。
            for (i in 1 until h) {
                if (isTransparent(controlImage.getRGB(0, i))) {
                    assertTrue(isTransparent(testImage.getRGB(0, i)))
                } else {
                    assertEquals(
                        "Image lost npTc chunk on image " + ninePatch + " at (x, y) (" + 0 + "," + i + ")",
                        controlImage.getRGB(0, i), testImage.getRGB(0, i),
                    )
                }
            }
        }
    }

    /** 对比 xxhdpi 密度目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableXxhdpiTest() {
        compareBinaryFolder("res/drawable-xxhdpi")
    }

    /** 对比带 API 级别限定符的 xxhdpi-v4 目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableQualifierXxhdpiTest() {
        compareBinaryFolder("res/drawable-xxhdpi-v4")
    }

    /** 对比 xxxhdpi 密度目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun drawableXxxhdpiTest() {
        compareBinaryFolder("res/drawable-xxxhdpi")
    }

    /** 对比 raw 目录下的二进制文件。 */
    @Test
    @Throws(Exception::class)
    fun resRawTest() {
        compareBinaryFolder("res/raw")
    }

    /** 验证构建产物中 mp3 文件以 STORED（压缩级别 0）方式存放。 */
    @Test
    @Throws(Exception::class)
    fun storedMp3FilesAreNotCompressedTest() {
        assertEquals(0, sTestApk.getDirectory().getCompressionLevel("res/raw/rain.mp3"))
    }

    /** 对比 lib 目录下的 so 库文件。 */
    @Test
    @Throws(Exception::class)
    fun libsTest() {
        compareBinaryFolder("lib")
    }

    /** 对比 assets 中的普通文本文件。 */
    @Test
    @Throws(Exception::class)
    fun fileAssetTest() {
        compareBinaryFolder("assets/txt")
    }

    /** 对比 assets 中的 Unicode 文件名字符（Windows 下跳过）。 */
    @Test
    @Throws(Exception::class)
    fun unicodeAssetTest() {
        assumeTrue(!OSDetection.isWindows())
        compareBinaryFolder("assets/unicode-txt")
    }

    /** 对比 APK 中未知顶层目录的文件。 */
    @Test
    @Throws(Exception::class)
    fun unknownFolderTest() {
        compareBinaryFolder("unknown")
    }

    /** 验证多 dex 工程：smali_classes2/3 目录存在且构建产物含 classes2/3.dex。 */
    @Test
    @Throws(Exception::class)
    fun multipleDexTest() {
        compareBinaryFolder("smali_classes2")
        compareBinaryFolder("smali_classes3")
        assertTrue(File(sTestOrigDir!!, "build/apk/classes2.dex").isFile)
        assertTrue(File(sTestOrigDir!!, "build/apk/classes3.dex").isFile)
    }

    /** 验证单 dex 工程：smali 目录存在且构建产物含 classes.dex。 */
    @Test
    @Throws(Exception::class)
    fun singleDexTest() {
        compareBinaryFolder("smali")
        assertTrue(File(sTestOrigDir!!, "build/apk/classes.dex").isFile)
    }
}
