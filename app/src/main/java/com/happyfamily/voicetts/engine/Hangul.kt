package com.happyfamily.voicetts.engine

/** 한글 음절 분해·조합과 표준 발음 규칙(연음, 비음화, 경음화 등). */
object Hangul {
    const val ONSETS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    const val VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    val CODAS = listOf(
        "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ",
        "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )
    /** 받침으로 실제 소리 나는 7개 대표음. */
    const val CODA_SOUNDS = "ㄱㄴㄷㄹㅁㅂㅇ"

    data class Syl(var onset: Char, var vowel: Char, var coda: String)

    fun isSyllable(c: Char) = c in '가'..'힣'

    fun decompose(c: Char): Syl {
        val i = c - '가'
        return Syl(ONSETS[i / 588], VOWELS[(i % 588) / 28], CODAS[i % 28])
    }

    fun compose(onset: Char, vowel: Char, coda: String = ""): String {
        val o = ONSETS.indexOf(onset)
        val v = VOWELS.indexOf(vowel)
        val c = CODAS.indexOf(coda)
        require(o >= 0 && v >= 0 && c >= 0) { "bad jamo $onset $vowel $coda" }
        return ('가' + (o * 588 + v * 28 + c)).toString()
    }

    private fun neutral(coda: String): String = when (coda) {
        "" -> ""
        "ㄱ", "ㄲ", "ㅋ", "ㄳ", "ㄺ" -> "ㄱ"
        "ㄴ", "ㄵ", "ㄶ" -> "ㄴ"
        "ㄷ", "ㅅ", "ㅆ", "ㅈ", "ㅊ", "ㅌ", "ㅎ" -> "ㄷ"
        "ㄹ", "ㄼ", "ㄽ", "ㄾ", "ㅀ" -> "ㄹ"
        "ㅁ", "ㄻ" -> "ㅁ"
        "ㅂ", "ㅍ", "ㅄ", "ㄿ" -> "ㅂ"
        else -> "ㅇ"
    }

    /** 겹받침을 (남는 받침, 넘어가는 소리)로 나눈다. */
    private fun split(coda: String): Pair<String, Char?> = when (coda) {
        "ㄳ" -> "ㄱ" to 'ㅆ'
        "ㄵ" -> "ㄴ" to 'ㅈ'
        "ㄶ" -> "ㄴ" to null
        "ㄺ" -> "ㄹ" to 'ㄱ'
        "ㄻ" -> "ㄹ" to 'ㅁ'
        "ㄼ" -> "ㄹ" to 'ㅂ'
        "ㄽ" -> "ㄹ" to 'ㅆ'
        "ㄾ" -> "ㄹ" to 'ㅌ'
        "ㄿ" -> "ㄹ" to 'ㅍ'
        "ㅀ" -> "ㄹ" to null
        "ㅄ" -> "ㅂ" to 'ㅆ'
        else -> "" to coda[0]
    }

    private fun tense(c: Char) = when (c) {
        'ㄱ' -> 'ㄲ'; 'ㄷ' -> 'ㄸ'; 'ㅂ' -> 'ㅃ'; 'ㅅ' -> 'ㅆ'; 'ㅈ' -> 'ㅉ'; else -> c
    }

    /** 소리 낼 때 구분이 거의 없는 모음을 합쳐 필요한 녹음 수를 줄인다. */
    fun reduceVowel(onset: Char, vowel: Char): Char {
        var v = vowel
        if (onset in "ㅈㅉㅊ") v = when (v) {
            'ㅑ' -> 'ㅏ'; 'ㅕ' -> 'ㅓ'; 'ㅛ' -> 'ㅗ'; 'ㅠ' -> 'ㅜ'; 'ㅒ' -> 'ㅐ'; 'ㅖ' -> 'ㅔ'; else -> v
        }
        if (v == 'ㅢ' && onset != 'ㅇ') v = 'ㅣ'
        if (v == 'ㅖ' && onset != 'ㅇ' && onset != 'ㄹ') v = 'ㅔ'
        if (v == 'ㅒ' && onset != 'ㅇ') v = 'ㅐ'
        return v
    }

    /** 모음이 끝날 때의 입 모양(받침 소리 단위는 이것만 구분하면 된다). */
    fun vowelEnding(v: Char): Char = when (v) {
        'ㅏ', 'ㅑ', 'ㅘ' -> 'ㅏ'
        'ㅐ', 'ㅒ', 'ㅙ' -> 'ㅐ'
        'ㅓ', 'ㅕ', 'ㅝ' -> 'ㅓ'
        'ㅔ', 'ㅖ', 'ㅞ', 'ㅚ' -> 'ㅔ'
        'ㅗ', 'ㅛ' -> 'ㅗ'
        'ㅜ', 'ㅠ' -> 'ㅜ'
        'ㅡ' -> 'ㅡ'
        else -> 'ㅣ'
    }

    /** 한 어절(한글 음절만)을 실제 발음 음절열로 바꾼다. 받침은 대표음 하나 또는 빈 문자열. */
    fun pronounce(word: String): List<Syl> {
        val s = word.filter { isSyllable(it) }.map { decompose(it) }
        for (i in s.indices) {
            val a = s[i]
            if (i == s.lastIndex) { a.coda = neutral(a.coda); break }
            val b = s[i + 1]
            if (a.coda.isEmpty()) continue
            // 구개음화: 굳이 → 구지, 같이 → 가치
            if (b.onset == 'ㅇ' && (b.vowel == 'ㅣ' || b.vowel == 'ㅕ') && a.coda in listOf("ㄷ", "ㅌ", "ㄾ")) {
                b.onset = if (a.coda == "ㄷ") 'ㅈ' else 'ㅊ'
                a.coda = if (a.coda == "ㄾ") "ㄹ" else ""
                continue
            }
            // 연음
            if (b.onset == 'ㅇ') {
                when {
                    a.coda == "ㅇ" -> {}
                    a.coda == "ㅎ" -> a.coda = ""
                    else -> {
                        val (rest, moved) = split(a.coda)
                        a.coda = rest
                        if (moved != null) b.onset = moved
                    }
                }
                continue
            }
            // 거센소리되기 (받침 + ㅎ)
            if (b.onset == 'ㅎ') {
                val (rest, asp) = when (a.coda) {
                    "ㄱ", "ㄲ", "ㅋ" -> "" to 'ㅋ'
                    "ㄺ" -> "ㄹ" to 'ㅋ'
                    "ㄷ", "ㅅ", "ㅆ", "ㅌ" -> "" to 'ㅌ'
                    "ㅈ", "ㅊ" -> "" to 'ㅊ'
                    "ㄵ" -> "ㄴ" to 'ㅊ'
                    "ㅂ", "ㅍ" -> "" to 'ㅍ'
                    "ㄼ" -> "ㄹ" to 'ㅍ'
                    else -> a.coda to 'ㅎ'
                }
                if (asp != 'ㅎ') { a.coda = rest; b.onset = asp; continue }
            }
            // ㅎ 받침
            if (a.coda == "ㅎ" || a.coda == "ㄶ" || a.coda == "ㅀ") {
                val base = when (a.coda) { "ㄶ" -> "ㄴ"; "ㅀ" -> "ㄹ"; else -> "" }
                when (b.onset) {
                    'ㄱ' -> { b.onset = 'ㅋ'; a.coda = base }
                    'ㄷ' -> { b.onset = 'ㅌ'; a.coda = base }
                    'ㅈ' -> { b.onset = 'ㅊ'; a.coda = base }
                    'ㅅ' -> { b.onset = 'ㅆ'; a.coda = base }
                    'ㄴ' -> a.coda = if (a.coda == "ㅎ") "ㄴ" else base
                    else -> a.coda = if (a.coda == "ㅎ") "ㄷ" else base
                }
                if (a.coda.isEmpty()) continue
            }
            var r = neutral(a.coda)
            if (r in listOf("ㄱ", "ㄷ", "ㅂ")) {
                when (b.onset) {
                    'ㄱ', 'ㄷ', 'ㅂ', 'ㅅ', 'ㅈ' -> b.onset = tense(b.onset)
                    'ㄴ', 'ㅁ', 'ㄹ' -> {
                        r = when (r) { "ㄱ" -> "ㅇ"; "ㄷ" -> "ㄴ"; else -> "ㅁ" }
                        if (b.onset == 'ㄹ') b.onset = 'ㄴ'
                    }
                }
            } else if ((r == "ㅁ" || r == "ㅇ") && b.onset == 'ㄹ') {
                b.onset = 'ㄴ'
            } else if (r == "ㄴ" && b.onset == 'ㄹ') {
                r = "ㄹ"
            } else if (r == "ㄹ" && b.onset == 'ㄴ') {
                b.onset = 'ㄹ'
            }
            a.coda = r
        }
        for (x in s) x.vowel = reduceVowel(x.onset, x.vowel)
        return s
    }

    fun toText(syls: List<Syl>) = syls.joinToString("") { compose(it.onset, it.vowel, it.coda) }
}
