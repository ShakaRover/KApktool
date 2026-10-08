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
import brut.androlib.exceptions.NinePatchNotFoundException
import brut.androlib.res.data.LayoutBounds
import brut.androlib.res.data.NinePatchData
import brut.util.BinaryDataInputStream
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteOrder
import javax.imageio.ImageIO

/**
 * 九宫格 PNG 解码器：从 aapt 内联的 npTc/npLb 块重建标准 9-patch 黑线边框。
 *
 * 原图（灰度 + alpha）被平移进 +2 尺寸的 ARGB 画布，再按分块数据画
 * 内容 padding、分割线与可选光学边界（layout bounds，Android 4.3+）。
 */
class ResNinePatchStreamDecoder : ResStreamDecoder {
    @Throws(AndrolibException::class)
    override fun decode(`in`: InputStream?, out: OutputStream?) {
        try {
            val data = `in`!!.readBytes()
            if (data.isEmpty()) {
                return
            }

            val src = ImageIO.read(ByteArrayInputStream(data))
            val w = src.width
            val h = src.height

            val dst = BufferedImage(w + 2, h + 2, BufferedImage.TYPE_INT_ARGB)
            if (src.type == BufferedImage.TYPE_CUSTOM) {
                val srcRaster = src.raster
                val dstRaster = dst.raster
                var gray: IntArray? = null
                var alpha: IntArray? = null
                for (y in 0 until src.height) {
                    gray = srcRaster.getSamples(0, y, w, 1, 0, gray)
                    alpha = srcRaster.getSamples(0, y, w, 1, 1, alpha)

                    dstRaster.setSamples(1, y + 1, w, 1, 0, gray)
                    dstRaster.setSamples(1, y + 1, w, 1, 1, gray)
                    dstRaster.setSamples(1, y + 1, w, 1, 2, gray)
                    dstRaster.setSamples(1, y + 1, w, 1, 3, alpha)
                }
            } else {
                dst.createGraphics().drawImage(src, 1, 1, w, h, null as ImageObserver?)
            }

            val np = findNinePatchData(data)
            drawHLine(dst, h + 1, np.paddingLeft + 1, w - np.paddingRight)
            drawVLine(dst, w + 1, np.paddingTop + 1, h - np.paddingBottom)

            val xDivs = np.xDivs
            if (xDivs.isEmpty()) {
                drawHLine(dst, 0, 1, w)
            } else {
                var i = 0
                while (i < xDivs.size) {
                    drawHLine(dst, 0, xDivs[i] + 1, xDivs[i + 1])
                    i += 2
                }
            }

            val yDivs = np.yDivs
            if (yDivs.isEmpty()) {
                drawVLine(dst, 0, 1, h)
            } else {
                var i = 0
                while (i < yDivs.size) {
                    drawVLine(dst, 0, yDivs[i] + 1, yDivs[i + 1])
                    i += 2
                }
            }

            // 部分图片带光学边界（optical inset / layout bounds）。
            // https://developer.android.com/about/versions/android-4.3.html#OpticalBounds
            try {
                val lb = findLayoutBounds(data)
                for (i in 0 until lb.left) {
                    dst.setRGB(1 + i, h + 1, LayoutBounds.COLOR_TICK)
                }
                for (i in 0 until lb.right) {
                    dst.setRGB(w - i, h + 1, LayoutBounds.COLOR_TICK)
                }
                for (i in 0 until lb.top) {
                    dst.setRGB(w + 1, 1 + i, LayoutBounds.COLOR_TICK)
                }
                for (i in 0 until lb.bottom) {
                    dst.setRGB(w + 1, h - i, LayoutBounds.COLOR_TICK)
                }
            } catch (ignored: NinePatchNotFoundException) {
                // 该块可以不存在。
            }

            ImageIO.write(dst, "png", out)
        } catch (ex: IOException) {
            // 文件不是合法图像。
            throw AndrolibException(ex)
        } catch (ex: NullPointerException) {
            throw AndrolibException(ex)
        }
    }

    @Throws(NinePatchNotFoundException::class, IOException::class)
    private fun findNinePatchData(data: ByteArray): NinePatchData {
        val `in` = BinaryDataInputStream(data, ByteOrder.BIG_ENDIAN)
        findChunk(`in`, NinePatchData.MAGIC)
        return NinePatchData.read(`in`)
    }

    @Throws(NinePatchNotFoundException::class, IOException::class)
    private fun findLayoutBounds(data: ByteArray): LayoutBounds {
        val `in` = BinaryDataInputStream(data, ByteOrder.BIG_ENDIAN)
        findChunk(`in`, LayoutBounds.MAGIC)
        return LayoutBounds.read(`in`)
    }

    /** 在 PNG 扩展块链上寻找指定魔数的块。 */
    @Throws(NinePatchNotFoundException::class, IOException::class)
    private fun findChunk(`in`: BinaryDataInputStream, magic: Int) {
        `in`.skipBytes(8)
        while (true) {
            val size = try {
                `in`.readInt()
            } catch (ignored: EOFException) {
                throw NinePatchNotFoundException()
            }
            if (`in`.readInt() == magic) {
                return
            }
            `in`.skipBytes(size + 4)
        }
    }

    private fun drawHLine(im: BufferedImage, y: Int, x1: Int, x2: Int) {
        for (x in x1..x2) {
            im.setRGB(x, y, NinePatchData.COLOR_TICK)
        }
    }

    private fun drawVLine(im: BufferedImage, x: Int, y1: Int, y2: Int) {
        for (y in y1..y2) {
            im.setRGB(x, y, NinePatchData.COLOR_TICK)
        }
    }
}
