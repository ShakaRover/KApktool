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
package brut.util

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 固定线程池后台任务批处理器。
 *
 * 提交的任务会被记录为 Future，[waitForFinish] 统一等待并复现异常；
 * 等待期间禁止再次提交（mSubmitAllowed 状态位），防止任务清单被中途改写。
 */
class BackgroundWorker(threads: Int) {
    /** 底层固定大小线程池。 */
    val executor: ExecutorService = Executors.newFixedThreadPool(threads)

    private val mWorkerFutures: MutableList<Future<*>> = ArrayList()
    @Volatile
    private var mSubmitAllowed = true

    /** 等待全部已提交任务结束；任一任务异常将包装为 RuntimeException 抛出。 */
    fun waitForFinish() {
        checkState()
        mSubmitAllowed = false
        for (future in mWorkerFutures) {
            try {
                future.get()
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RuntimeException(ex)
            } catch (ex: ExecutionException) {
                throw RuntimeException(ex)
            }
        }
        mWorkerFutures.clear()
        mSubmitAllowed = true
    }

    /** 丢弃 Future 记录（不取消已提交任务），用于异常路径的批量回收。 */
    fun clearFutures() {
        mWorkerFutures.clear()
    }

    private fun checkState() {
        if (!mSubmitAllowed) {
            throw IllegalStateException("BackgroundWorker is not ready")
        }
    }

    /** 立即关停线程池，尝试中断正在执行的任务。 */
    fun shutdownNow() {
        mSubmitAllowed = false
        executor.shutdownNow()
    }

    /** 提交一个无返回值任务。 */
    fun submit(task: Runnable) {
        checkState()
        mWorkerFutures.add(executor.submit(task))
    }

    /** 提交一个有返回值任务，并返回其 Future。 */
    fun <T> submit(task: Callable<T>): Future<T> {
        checkState()
        val future = executor.submit(task)
        mWorkerFutures.add(future)
        return future
    }
}
