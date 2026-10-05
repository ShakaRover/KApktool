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
package brut.directory

import java.io.File
import java.net.URI

/**
 * 附带 [Directory] 视图的 [File]：目录返回 [FileDirectory]，压缩包返回 [ZipRODirectory]。
 *
 * 用于 APK 既可能是解包目录也可能是 .apk 文件的场景；
 * [delete] 前会先关闭内部目录句柄（ZIP 需释放文件锁）。
 */
class ExtFile : File, AutoCloseable {
    private var mDirectory: Directory? = null

    constructor(file: File) : super(file.path)
    constructor(uri: URI) : super(uri)
    constructor(parent: File, child: String) : super(parent, child)
    constructor(parent: String, child: String) : super(parent, child)
    constructor(pathname: String) : super(pathname)

    /** 惰性获取（并缓存）本文件的目录视图。 */
    @Throws(DirectoryException::class)
    fun getDirectory(): Directory {
        var dir = mDirectory
        if (dir == null) {
            dir = if (isDirectory) {
                FileDirectory(this)
            } else {
                ZipRODirectory(this)
            }
            mDirectory = dir
        }
        return dir
    }

    /** 关闭内部目录视图并释放缓存。 */
    @Throws(DirectoryException::class)
    override fun close() {
        val dir = mDirectory
        if (dir != null) {
            dir.close()
            mDirectory = null
        }
    }

    /** 先关闭目录句柄再删除文件；关闭失败时放弃删除。 */
    override fun delete(): Boolean {
        try {
            close()
        } catch (ignored: DirectoryException) {
            return false
        }

        return super.delete()
    }
}
