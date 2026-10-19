package com.aiconsole.kittlauncher2.assistant

/** A downloadable voice for the on-device neural engine (sherpa-onnx). */
data class LocalVoice(
    val id: String,
    val label: String,
    /** Archive name in sherpa-onnx's `tts-models` GitHub release (without .tar.bz2). */
    val archive: String,
    val sizeMb: Int,
    /** Exact archive size and SHA-256 as published on the release: a cut or damaged download is refused. */
    val bytes: Long = 0,
    val sha256: String = "",
    /** Named speakers for multi-speaker voices; empty for single-voice packs. */
    val speakers: List<String> = emptyList(),
    val defaultSpeaker: Int = 0,
    /** Studio-quality but heavier: slower to start a sentence on modest head units. */
    val heavy: Boolean = false
) {
    val url: String get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archive.tar.bz2"
}

/**
 * The on-device voices KITT can download: free, open neural voices (Piper and Kokoro, via
 * sherpa-onnx) that run entirely on the head unit, so KITT sounds natural with no API key and
 * no internet once a voice is downloaded. Plain Kotlin (unit-tested).
 */
object LocalVoices {

    val ALL: List<LocalVoice> = listOf(
        LocalVoice("ryan", "Ryan · US male, clear (recommended)", "vits-piper-en_US-ryan-medium-int8", 20, bytes = 21083446, sha256 = "376eb489d42e98cb49f0e13a633e8d88580bd247ed8aacc298a733210404f771"),
        LocalVoice("joe", "Joe · US male, relaxed", "vits-piper-en_US-joe-medium-int8", 20, bytes = 21230019, sha256 = "644527f29eca0ada5595d7b6e4daf6388f5ac0c4870b4d42996a6687fcefeb37"),
        LocalVoice("norman", "Norman · US male, mature", "vits-piper-en_US-norman-medium-int8", 20, bytes = 20987233, sha256 = "cb481a514bc213ccf3899391c0f27fdcc4e4b814ec30496f28089a027b5aa01b"),
        LocalVoice("bryce", "Bryce · US male, young", "vits-piper-en_US-bryce-medium-int8", 19, bytes = 20910568, sha256 = "89cd4f464c91579440565927bfca9d26555f577c4e37431167568c934fdb82f6"),
        LocalVoice("john", "John · US male", "vits-piper-en_US-john-medium-int8", 19, bytes = 20800096, sha256 = "2ea2f6cf88bba033be71c941d00cd4d2af35d23655b5d68b7fe8239d519f17b4"),
        LocalVoice("lessac", "Lessac · US female", "vits-piper-en_US-lessac-medium-int8", 19, bytes = 20969179, sha256 = "f1c6d0295cf16087b05f80fdca5b44daca5cd78e2c425d419a42ba34929805f9"),
        LocalVoice("amy", "Amy · US female", "vits-piper-en_US-amy-medium-int8", 20, bytes = 21028122, sha256 = "bd23c0aa629eb3719448582f45ede49e8fa6a679061fed5eab16a6a6fd8e7e82"),
        LocalVoice("kristin", "Kristin · US female", "vits-piper-en_US-kristin-medium-int8", 19, bytes = 20882061, sha256 = "16289d7ee8e6b2311a0a0af6531a55f498f82499644a1bb6fddb991fe6fa950c"),
        LocalVoice("alan", "Alan · British male", "vits-piper-en_GB-alan-medium-int8", 20, bytes = 21103831, sha256 = "f0b2526c9600657141ccbbe412f6159dceedab5bab4ba4e8857d9443acb50de4"),
        LocalVoice("northern", "Northern English male", "vits-piper-en_GB-northern_english_male-medium-int8", 20, bytes = 21100291, sha256 = "9b2afdc8f35426f6b2739f255e67841679a52b56d19ece874a1eae208cb88ad1"),
        LocalVoice("cori", "Cori · British female", "vits-piper-en_GB-cori-medium-int8", 19, bytes = 20768736, sha256 = "169ca8aff3adb271f009a4924c99928a811dbf2b52eaca2dbb460e8c34478c93"),
        LocalVoice(
            "kokoro", "Kokoro · 11 studio voices (best quality, slower)", "kokoro-int8-en-v0_19", 98, bytes = 103248205, sha256 = "c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd",
            speakers = listOf(
                "Adult female (US)", "Bella (US)", "Nicole (US)", "Sarah (US)", "Sky (US)", "Adam (US male)",
                "Michael (US male)", "Emma (UK)", "Isabella (UK)", "George (UK male)", "Lewis (UK male)"
            ),
            defaultSpeaker = 6,
            heavy = true
        )
    )

    val DEFAULT: LocalVoice = ALL.first()

    fun byId(id: String?): LocalVoice = ALL.firstOrNull { it.id == id } ?: DEFAULT

    /** The speaker to use: the driver's pick if the voice has that many speakers, else its default. */
    fun speaker(voice: LocalVoice, picked: Int): Int =
        if (voice.speakers.isNotEmpty() && picked in voice.speakers.indices) picked else voice.defaultSpeaker

    /**
     * A safe relative path inside an archive: "a/b.onnx" stays, anything absolute or climbing
     * out ("../x", "/etc/x") is refused, so a bad archive can't write outside the voice folder.
     */
    fun safeEntry(name: String): String? {
        val clean = name.replace('\\', '/').removePrefix("./")
        if (clean.isEmpty() || clean.startsWith("/") || clean.split('/').any { it == ".." }) return null
        return clean
    }

    /** "vits-piper-…/en_US-ryan-medium.onnx" → "en_US-ryan-medium.onnx": drops the archive's top folder. */
    fun stripTop(path: String): String? = path.substringAfter('/', "").takeIf { it.isNotEmpty() }
}
