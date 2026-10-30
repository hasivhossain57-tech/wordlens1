package com.wordlens.app

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class Entry(val bangla: String, val synonyms: String)

/**
 * Looks up a single English word:
 *  - Bangla meaning + extra meanings by part of speech (Google Translate public endpoint)
 *  - English synonyms (same endpoint, with a fallback to dictionaryapi.dev)
 * Blocking calls: always run on Dispatchers.IO.
 */
object Dictionary {

    private val cache = ConcurrentHashMap<String, Entry>()

    fun lookup(word: String): Entry {
        cache[word]?.let { return it }

        var bangla = ""
        var synonyms = emptyList<String>()
        var ok = false

        try {
            val root = JSONArray(httpGet(gtxUrl(word)))
            bangla = parseBangla(root)
            synonyms = parseGtxSynonyms(root, word)
            ok = bangla.isNotBlank()
        } catch (e: Exception) {
            ok = false
        }

        if (synonyms.isEmpty()) {
            synonyms = try {
                fetchDictionaryApiSynonyms(word)
            } catch (e: Exception) {
                emptyList()
            }
        }

        val entry = Entry(
            bangla = if (ok) bangla else "(অর্থ পাওয়া যায়নি — ইন্টারনেট চেক করুন)",
            synonyms = if (synonyms.isEmpty()) "—" else synonyms.take(8).joinToString(", ")
        )
        if (ok) cache[word] = entry
        return entry
    }

    private fun gtxUrl(word: String): String {
        val q = URLEncoder.encode(word, "UTF-8")
        return "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=en&tl=bn&dt=t&dt=bd&dt=ss&q=$q"
    }

    private fun parseBangla(root: JSONArray): String {
        val sb = StringBuilder()

        // Main translation
        val first = root.optJSONArray(0)?.optJSONArray(0)?.optString(0).orEmpty()
        sb.append(first)

        // Other meanings grouped by part of speech
        val dict = root.optJSONArray(1)
        if (dict != null) {
            for (i in 0 until minOf(dict.length(), 4)) {
                val e = dict.optJSONArray(i) ?: continue
                val pos = e.optString(0)
                val terms = e.optJSONArray(1) ?: continue
                val list = mutableListOf<String>()
                for (j in 0 until minOf(terms.length(), 5)) list.add(terms.optString(j))
                if (list.isNotEmpty()) {
                    sb.append("\n").append(pos).append(": ").append(list.joinToString(", "))
                }
            }
        }
        return sb.toString().trim()
    }

    private fun parseGtxSynonyms(root: JSONArray, word: String): List<String> {
        val out = LinkedHashSet<String>()
        if (root.length() <= 11 || root.isNull(11)) return emptyList()
        val groups = root.optJSONArray(11) ?: return emptyList()
        for (i in 0 until groups.length()) {
            val posEntry = groups.optJSONArray(i) ?: continue
            val senses = posEntry.optJSONArray(1) ?: continue
            for (j in 0 until senses.length()) {
                val sense = senses.optJSONArray(j) ?: continue
                val words = sense.optJSONArray(0) ?: continue
                for (k in 0 until words.length()) {
                    val w = words.optString(k)
                    if (w.isNotBlank() && !w.equals(word, true)) out.add(w)
                }
            }
        }
        return out.toList()
    }

    private fun fetchDictionaryApiSynonyms(word: String): List<String> {
        val q = URLEncoder.encode(word, "UTF-8")
        val arr = JSONArray(httpGet("https://api.dictionaryapi.dev/v2/entries/en/$q"))
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val meanings = arr.optJSONObject(i)?.optJSONArray("meanings") ?: continue
            for (m in 0 until meanings.length()) {
                val mo = meanings.optJSONObject(m) ?: continue
                addAll(out, mo.optJSONArray("synonyms"))
                val defs = mo.optJSONArray("definitions") ?: continue
                for (d in 0 until defs.length()) {
                    addAll(out, defs.optJSONObject(d)?.optJSONArray("synonyms"))
                }
            }
        }
        return out.filter { !it.equals(word, true) }
    }

    private fun addAll(target: MutableSet<String>, arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) target.add(s)
        }
    }

    private fun httpGet(url: String): String {
        var lastError: Exception? = null
        repeat(2) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                try {
                    if (conn.responseCode != 200) {
                        // 404 etc. is a valid "no result": do not retry
                        throw java.io.IOException("HTTP ${conn.responseCode}")
                    }
                    return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: java.io.IOException("request failed")
    }
}
