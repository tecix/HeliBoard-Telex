package com.viettelex.keyboard

import android.content.Context
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mở file dữ liệu nhị phân đóng gói kèm bàn phím (vnlexicon.bin).
 * Trong app Android gọi [install] từ Context (mở qua AssetManager);
 * trong JVM test mặc định tìm `src/main/assets/<name>`.
 */
object KeyboardData {
    @Volatile
    var open: (String) -> InputStream? = ::openFromAssetsDir

    @JvmStatic
    fun install(context: Context) {
        val appAssets = context.applicationContext.assets
        open = { name -> runCatching { appAssets.open(name) }.getOrNull() }
    }

    fun buffer(name: String): ByteBuffer {
        val bytes = open(name)?.use { it.readBytes() }
            ?: error("Missing keyboard asset: $name")
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun openFromAssetsDir(name: String): InputStream? {
        val candidates = arrayOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
            File("../app/src/main/assets/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.inputStream()
    }
}

internal object Cp {
    fun count(s: String): Int = s.codePointCount(0, s.length)
    fun dropLast(s: String, n: Int): String {
        if (n <= 0) return s
        var end = s.length
        var k = n
        while (k > 0 && end > 0) { end = s.offsetByCodePoints(end, -1); k-- }
        return s.substring(0, end)
    }
    fun lastCodePoint(s: String): Int? = if (s.isEmpty()) null else s.codePointBefore(s.length)
    fun capitalizeFirst(w: String): String {
        if (w.isEmpty()) return w
        val first = w.codePointAt(0)
        val n = Character.charCount(first)
        return String(Character.toChars(first)).uppercase() + w.substring(n)
    }
}
