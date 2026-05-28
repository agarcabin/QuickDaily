package com.quickdaily.util

/** 文件读取结果 */
sealed class ReadResult {
    data class Success(val content: String) : ReadResult()
    data object NotFound : ReadResult()
    data class Error(val exception: Exception) : ReadResult()
}

/** 文件写入结果 */
sealed class WriteResult {
    data object Success : WriteResult()
    data class Error(val exception: Exception) : WriteResult()
}
