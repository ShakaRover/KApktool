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

import brut.androlib.BaseTest
import brut.androlib.res.data.NinePatchData
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * issue1522 回归测试：缺少分隔线（div）的 9-patch 解码修复。
 *
 * 验证 ResNinePatchStreamDecoder 解码缺少 1 像素分隔列的 9-patch 图时，
 * 会自动补上该列，使首列（除不可见的首尾像素外）全部为黑色标记像素。
 */
class MissingDiv9PatchTest : BaseTest() {

    /** 解码 9-patch 流并逐行校验补齐后的第一列像素颜色。 */
    @Test
    @Throws(Exception::class)
    fun assertMissingDivAdded() {
        val file = File(sTmpDir!!, "pip_dismiss_scrim.9.png")

        val data: ByteArray = Files.newInputStream(file.toPath()).use { input ->
            val decoder = ResNinePatchStreamDecoder()
            val out = ByteArrayOutputStream()
            decoder.decode(input, out)
            out.toByteArray()
        }

        val image: BufferedImage = ImageIO.read(ByteArrayInputStream(data))
        val height = image.height - 1

        // 首尾像素不可见，因此只检查第一列中间部分是否全为黑色。
        for (y in 1 until height) {
            assertEquals("y coordinate failed at: $y", NinePatchData.COLOR_TICK, image.getRGB(0, y))
        }
    }

    companion object {
        /** 类级初始化：把 issue1522 的 9-patch 测试资源复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(
                MissingDiv9PatchTest::class.java, "res/decoder/issue1522", sTmpDir!!
            )
        }
    }
}
