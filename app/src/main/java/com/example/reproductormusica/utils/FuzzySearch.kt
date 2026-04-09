package com.example.reproductormusica.utils

import kotlin.math.abs
import kotlin.math.min

fun fuzzyMatch(query: String, target: String): Boolean {
    if (query.isEmpty()) return true
    val q = query.lowercase()
    val t = target.lowercase()
    var i = 0; var j = 0
    while (i < q.length && j < t.length) {
        if (q[i] == t[j]) i++
        j++
    }
    return i == q.length
}

private val SOUNDEX_TABLE: Map<Char, Char> = mapOf(
    'b' to '1', 'v' to '1',
    'c' to '2', 'k' to '2', 'q' to '2', 's' to '2', 'z' to '2', 'x' to '2',
    'd' to '3', 't' to '3',
    'f' to '4', 'p' to '4',
    'g' to '5', 'j' to '5',
    'l' to '6',
    'm' to '7', 'n' to '7', 'ñ' to '7',
    'r' to '8'
)

fun spanishSoundex(word: String): String {
    if (word.isEmpty()) return ""
    val normalized = word.lowercase()
        .replace("ll", "y")
        .replace("ph", "f")
        .replace("gu", "g")
        .replace("qu", "k")
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')

    val first = normalized.first().let {
        when (it) {
            'v' -> 'b'
            'z', 'x' -> 's'
            else -> it
        }
    }

    val code = StringBuilder().append(first)
    var lastDigit = SOUNDEX_TABLE[first] ?: '0'

    for (ch in normalized.drop(1)) {
        if (ch == 'h') continue
        val digit = SOUNDEX_TABLE[ch] ?: continue
        if (digit != lastDigit) {
            code.append(digit)
            lastDigit = digit
        }
        if (code.length == 4) break
    }

    return code.toString().padEnd(4, '0')
}

fun soundexMatch(query: String, target: String): Boolean {
    val qCode = spanishSoundex(query)
    val tCode = spanishSoundex(target)
    return qCode == tCode
}

private val QWERTY_POS: Map<Char, Pair<Int, Int>> = buildMap {
    "qwertyuiop".forEachIndexed { col, ch -> put(ch, 0 to col) }
    "asdfghjkl".forEachIndexed  { col, ch -> put(ch, 1 to col) }
    "zxcvbnm".forEachIndexed    { col, ch -> put(ch, 2 to col) }
}

fun keyboardDistance(c1: Char, c2: Char): Int {
    val p1 = QWERTY_POS[c1.lowercaseChar()] ?: return 3
    val p2 = QWERTY_POS[c2.lowercaseChar()] ?: return 3
    return abs(p1.first - p2.first) + abs(p1.second - p2.second)
}

fun levenshteinDistance(a: String, b: String): Int {
    val la = a.length; val lb = b.length
    val dp = Array(la + 1) { IntArray(lb + 1) }
    for (i in 0..la) dp[i][0] = i
    for (j in 0..lb) dp[0][j] = j

    for (i in 1..la) {
        for (j in 1..lb) {
            if (a[i - 1] == b[j - 1]) {
                dp[i][j] = dp[i - 1][j - 1]
            } else {
                val kbDist = keyboardDistance(a[i - 1], b[j - 1])
                val substituteCost = if (kbDist <= 1) 1 else 2
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + substituteCost
                )
            }
        }
    }
    return dp[la][lb]
}

fun advancedMatch(query: String, target: String): Boolean {
    if (query.isEmpty()) return true
    val q = query.trim().lowercase()
    val t = target.trim().lowercase()

    if (t.contains(q)) return true

    val qWords = q.split(" ").filter { it.isNotBlank() }
    val tWords = t.split(" ").filter { it.isNotBlank() }

    return qWords.all { qw ->
        tWords.any { tw ->
            fuzzyMatch(qw, tw) ||
                    levenshteinDistance(qw, tw) <= maxEditDistance(qw) ||
                    (qw.length >= 3 && soundexMatch(qw, tw))
        }
    }
}

private fun maxEditDistance(word: String): Int = when {
    word.length <= 3 -> 1
    word.length <= 6 -> 2
    else              -> 3
}