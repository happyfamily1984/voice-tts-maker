package com.happyfamily.voicetts.engine

/** 글자를 읽을 수 있는 한글 어절과 쉼으로 바꾼다 (숫자·영문·기호 포함). */
object TextNormalizer {
    sealed class Token
    data class Word(val text: String, val numeric: Boolean = false) : Token()
    data class Pause(val ms: Int) : Token()

    private val SINO = listOf("영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private val NATIVE_ONES = listOf("", "하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉")
    private val NATIVE_ONES_ATTR = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉")
    private val NATIVE_TENS = listOf("", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔")
    private val NATIVE_COUNTERS = listOf(
        "시간", "시", "개", "명", "살", "마리", "잔", "병", "권", "장", "가지", "대", "곳", "번째"
    )
    private val PHONE = Regex("\\d+(?:-\\d+)+")
    private val LETTERS = mapOf(
        'a' to "에이", 'b' to "비", 'c' to "씨", 'd' to "디", 'e' to "이", 'f' to "에프", 'g' to "지",
        'h' to "에이치", 'i' to "아이", 'j' to "제이", 'k' to "케이", 'l' to "엘", 'm' to "엠", 'n' to "엔",
        'o' to "오", 'p' to "피", 'q' to "큐", 'r' to "알", 's' to "에스", 't' to "티", 'u' to "유",
        'v' to "브이", 'w' to "더블유", 'x' to "엑스", 'y' to "와이", 'z' to "지"
    )
    private val SYMBOLS = mapOf(
        '%' to "퍼센트", '+' to "더하기", '=' to "는", '&' to "그리고", '@' to "골뱅이",
        '℃' to "도", '°' to "도", '₩' to "원", '$' to "달러"
    )

    /** 한자어 수 읽기: 2026 → 이천이십육 */
    fun sino(n: Long): String {
        if (n == 0L) return "영"
        val big = listOf("", "만", "억", "조")
        val sb = StringBuilder()
        var groups = mutableListOf<Int>()
        var x = n
        while (x > 0) { groups.add((x % 10000).toInt()); x /= 10000 }
        for (gi in groups.indices.reversed()) {
            val g = groups[gi]
            if (g == 0) continue
            val part = StringBuilder()
            val units = listOf("천", "백", "십", "")
            val digits = listOf(g / 1000, g / 100 % 10, g / 10 % 10, g % 10)
            for (k in 0..3) {
                val d = digits[k]
                if (d == 0) continue
                if (d != 1 || k == 3) part.append(SINO[d])
                part.append(units[k])
            }
            // 10000 → "만" (일만 아님)
            if (gi == 1 && g == 1 && groups.size == 2) sb.append("만")
            else sb.append(part).append(big.getOrElse(gi) { "" })
        }
        return sb.toString()
    }

    /** 고유어 수 읽기(1~99). attr=true면 단위 앞 형태(한, 두, 스무). */
    fun native(n: Int, attr: Boolean): String {
        val t = n / 10
        val o = n % 10
        if (attr && n == 20) return "스무"
        return NATIVE_TENS[t] + (if (attr) NATIVE_ONES_ATTR[o] else NATIVE_ONES[o])
    }

    fun tokenize(text: String): List<Token> {
        val out = ArrayList<Token>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { out.add(Word(cur.toString())); cur.clear() } }
        fun pause(ms: Int) {
            flush()
            val last = out.lastOrNull()
            if (last is Pause) { if (last.ms < ms) out[out.lastIndex] = Pause(ms) }
            else if (out.isNotEmpty()) out.add(Pause(ms))
        }
        var i = 0
        val s = text
        while (i < s.length) {
            val c = s[i]
            when {
                Hangul.isSyllable(c) -> { cur.append(c); i++ }
                c.isDigit() -> {
                    flush()
                    // 전화번호(010-1234-5678)는 한 자리씩 읽는다
                    val phone = PHONE.find(s, i)
                    if (phone != null && phone.range.first == i && (c == '0' || phone.value.count { it == '-' } >= 2)) {
                        for (ch in phone.value) {
                            if (ch == '-') pause(150)
                            else out.add(Word(if (ch == '0') "공" else SINO[ch - '0'], true))
                        }
                        i = phone.range.last + 1
                        continue
                    }
                    var j = i
                    val digits = StringBuilder()
                    while (j < s.length && (s[j].isDigit() || (s[j] == ',' && j + 1 < s.length && s[j + 1].isDigit() && digits.isNotEmpty()))) {
                        if (s[j] != ',') digits.append(s[j]); j++
                    }
                    val d = digits.toString()
                    val rest = s.substring(j)
                    if ((d.length > 1 && d[0] == '0') || d.length > 15) {
                        // 전화번호 등은 한 자리씩 읽는다
                        for (ch in d) out.add(Word(if (ch == '0') "공" else SINO[ch - '0'], true))
                    } else {
                        val n = d.toLong()
                        val useNative = n in 1..99 && NATIVE_COUNTERS.any { rest.startsWith(it) } &&
                            !rest.startsWith("시스") && !rest.startsWith("대한")
                        out.add(Word(if (useNative) native(n.toInt(), true) else sino(n), true))
                    }
                    // 소수점
                    if (j + 1 < s.length && s[j] == '.' && s[j + 1].isDigit()) {
                        out.add(Word("점", true))
                        j++
                        while (j < s.length && s[j].isDigit()) {
                            out.add(Word(SINO[s[j] - '0'], true)); j++
                        }
                    }
                    i = j
                }
                c.lowercaseChar() in LETTERS -> {
                    flush()
                    while (i < s.length && s[i].lowercaseChar() in LETTERS) {
                        out.add(Word(LETTERS.getValue(s[i].lowercaseChar()))); i++
                    }
                }
                c in SYMBOLS -> { flush(); out.add(Word(SYMBOLS.getValue(c))); i++ }
                c == ',' || c == '、' || c == ';' || c == ':' || c == '·' -> { pause(220); i++ }
                c == '.' || c == '!' || c == '?' || c == '\n' || c == '。' || c == '…' -> { pause(420); i++ }
                else -> { flush(); i++ }
            }
        }
        flush()
        while (out.lastOrNull() is Pause) out.removeAt(out.lastIndex)
        return out
    }

    /** 녹음 단위의 키: 한글 음절만 남긴 문자열. 숫자·영문은 읽는 소리로 바꾼다. */
    fun key(text: String): String =
        tokenize(text).filterIsInstance<Word>().joinToString("") { it.text }
}
