from pathlib import Path

p = Path('app/src/main/java/com/aiconsole/kittlauncher2/assistant/TextToSpeechHelper.kt')
s = p.read_text()

def replace(old, new):
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'Expected one patch anchor, found {count}: {old[:90]!r}')
    s = s.replace(old, new, 1)

replace('import kotlinx.coroutines.async\n', 'import kotlinx.coroutines.async\nimport kotlinx.coroutines.delay\n')
replace('    private var worker: Job? = null', '    private var worker: Job? = null\n    private var playbackGeneration = 0L')
replace('            queue.addLast(Utterance(text, urgent, deviceOnly, character))\n            if (worker?.isActive != true) worker = main.launch { drain() }', '            queue.addLast(Utterance(text, urgent, deviceOnly, character))\n            _speaking.value = true\n            if (worker?.isActive != true) {\n                val generation = playbackGeneration\n                worker = main.launch { drain(generation) }\n            }')
replace('    private fun haltPlayback() {\n', '    private fun haltPlayback() {\n        playbackGeneration++\n        ducker.release()\n')
replace('''    private suspend fun drain() {
        com.aiconsole.kittlauncher2.data.ReturnHome.noteAudio()
        // Duck music/video for the whole queue so KITT is always heard.
        ducker.duck()
        try {
            while (true) {
                val next = queue.removeFirstOrNull() ?: break
                say(next)
            }
        } finally {
            _speaking.value = false
            _speakingAs.value = null
            ducker.release()
        }
    }''', '''    private suspend fun drain(generation: Long) {
        com.aiconsole.kittlauncher2.data.ReturnHome.noteAudio()
        try {
            while (true) {
                val next = queue.removeFirstOrNull()
                if (next == null) {
                    ducker.release()
                    delay(650L)
                    if (queue.isEmpty()) break
                    continue
                }
                try {
                    say(next)
                } finally {
                    if (playbackGeneration == generation) ducker.release()
                }
            }
        } finally {
            if (playbackGeneration == generation) {
                _speaking.value = false
                _speakingAs.value = null
                ducker.release()
            }
        }
    }''')
replace('                onDevice.play(clip, ducker.attributes, pitch)', '                ducker.duck()\n                onDevice.play(clip, ducker.attributes, pitch)')
replace('                if (tts.speak(', '                ducker.duck()\n                if (tts.speak(')
replace('        player.setOnPreparedListener { it.start() }', '        player.setOnPreparedListener {\n            ducker.duck()\n            it.start()\n        }')
p.write_text(s)

p = Path('app/src/main/java/com/aiconsole/kittlauncher2/assistant/WakeWordListener.kt')
s = p.read_text()
replace('        kittSpeaking = speaking,', '        kittSpeaking = speaking || voice.speaking.value,')
p.write_text(s)
