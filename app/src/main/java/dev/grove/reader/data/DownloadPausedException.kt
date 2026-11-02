package dev.grove.reader.data

import kotlinx.coroutines.CancellationException

/** System interruption may be retried; explicit cancellation still discards partial files. */
class DownloadPausedException : CancellationException("文件加载已暂停，点击重试继续。")
