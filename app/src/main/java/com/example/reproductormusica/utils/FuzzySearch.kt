package com.example.reproductormusica.utils

fun fuzzyMatch(query: String, target: String): Boolean {
    if (query.isEmpty()) return true
    val q = query.lowercase()
    val t = target.lowercase()
    var i = 0
    var j = 0
    while (i < q.length && j < t.length) {
        if (q[i] == t[j]) {
            i++
        }
        j++
    }
    return i == q.length
}