@file:Suppress("NOTHING_TO_INLINE")
package klite

import java.lang.System.Logger
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*

fun Any.logger(name: String = javaClass.name): Logger = System.getLogger(name)
inline fun Logger.info(msg: String) = log(Logger.Level.INFO, msg)
inline fun Logger.warn(msg: String) = log(Logger.Level.WARNING, msg)
inline fun Logger.error(msg: String, e: Throwable? = null) = log(Logger.Level.ERROR, msg, e)
inline fun Logger.error(e: Throwable) = log(Logger.Level.ERROR, "", e)

fun String.urlDecode() = URLDecoder.decode(this, Charsets.UTF_8)!!
fun String.urlEncode() = URLEncoder.encode(this, Charsets.UTF_8)!!

fun ByteArray.base64Encode() = Base64.getEncoder().encodeToString(this)
fun String.base64Encode() = toByteArray().base64Encode()
fun String.base64Decode() = Base64.getDecoder().decode(this)

typealias Params = Map<String, String?>

fun urlEncodeParams(params: Params) = params.mapNotNull { e -> e.value?.let { e.key + "=" + it.urlEncode() } }.joinToString("&")
fun urlDecodeParams(params: String?): Params = params?.split('&')?.associate(::keyValue) ?: emptyMap()
internal fun keyValue(s: String) = s.split('=', limit = 2).let { it[0] to it.getOrNull(1)?.urlDecode() }

val URI.queryParams: Params get() = urlDecodeParams(rawQuery)
operator fun URI.plus(suffix: String) = URI(toString() + suffix)

fun String?.trimToNull() = this?.trim()?.takeIf { it.isNotEmpty() }

val HttpExchange.browser: String? get() = detectBrowser(header("User-Agent"))

fun detectBrowser(userAgent: String?) = userAgent?.run {
  fun String.detect(browser: String): String? {
    val p = indexOf(browser)
    return if (p >= 0) substring(p, indexOf(' ', startIndex = p + 1).takeIf { it > 0 } ?: length) else null
  }

  (if (contains("Mobile")) "Mobile/" else "") + (
    detect("Edg") ?: detect("Chrome") ?: detect("Firefox") ?: detect("Trident") ?: detect("MSIE") ?:
    if (contains("Safari")) detect("Version")?.replace("Version", "Safari") else split(' ', ';').find { it.contains("bot") } ?: this)
}
