package zed.rainxch.details.data.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeBase64Text(encoded: String): Result<String> =
    try {
        Result.success(Base64.Mime.decode(encoded).decodeToString())
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }
