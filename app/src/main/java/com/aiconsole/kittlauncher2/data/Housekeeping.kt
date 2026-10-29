package com.aiconsole.kittlauncher2.data

/**
 * One feature release's highlights, shown once after an update. [level] counts feature
 * releases across both editions (their version numbers differ), so the notes are shared.
 */
data class ReleaseNote(val level: Int, val items: List<String>)

/** Pure "what's new" bookkeeping (unit-tested). Newest release first. */
object ReleaseNotes {

    val ALL = listOf(
        ReleaseNote(
            10,
            listOf(
                "Every edition gets the solid colour dock keys with icons and bold labels, and a row of live readings on the Drive " +
                    "page. Tap one for its low, high and average since start.",
                "A new hologram navigation map in every edition, in your theme's colours: glowing roads, a neon route, a scan grid " +
                    "and sonar rings on the car.",
                "KITT Holo: pick a pickup truck instead of the car for the hologram turntable (KITT Config → Hologram vehicle). " +
                    "It turns with the same live readings.",
            )
        ),
        ReleaseNote(
            9,
            listOf(
                "KITT Holo: the new cockpit edition, with a rotating hologram car, film-themed Drive screens and the new gauge faces.",
                "Bigger, easier dock buttons: solid colour keys with an icon and bold label, so Diag, Trips, Config, System and the " +
                    "media keys can be read at a glance while driving.",
                "More live readings on the hologram car: speed, load, throttle, fuel, intake air and fuel burn. Tap one for a " +
                    "bigger card with its low, high and average since start and what the number means.",
            )
        ),
        ReleaseNote(
            8,
            listOf(
                "Seven new gauge faces: Needle, Nixie tubes, Skyline bars, HUD tape, Scope, Halo, and a Film HUD that " +
                    "follows the theme. Diagnostics → Gauges → Edit layout → Style shows them all live.",
                "Restyle one gauge or all of them from the style gallery; Needle and Halo mark the highest value this drive.",
                "Gauges page: the Cluster button draws the first gauge large, like an instrument cluster.",
                "With stored fault codes, the Codes tab can route you to the nearest repair shop.",
            )
        ),
        ReleaseNote(
            7,
            listOf(
                "Music library: the Media page has a new Library tab. Pick Spotify, YouTube Music or a USB player, search it, " +
                    "or browse its playlists, artists and albums and tap to play, without opening the app.",
                "Recently played: your last songs, playlists and searches are one tap away at the top of the Library.",
                "Say \"Hey KITT, play Nightcall on Spotify\" to search and play by voice.",
                "If the head unit throws its radio screen over KITT when audio or the mic starts, KITT now brings itself back " +
                    "(KITT Config → Microphone fix).",
            )
        ),
        ReleaseNote(
            6,
            listOf(
                "No signal? KITT still answers the common questions from the car itself: engine temperature, battery, " +
                    "fuel, speed, fault codes and \"is everything OK?\", instantly, with any warning first.",
                "KITT offline speech: the mic understands full questions and commands with no signal and no Google " +
                    "recognizer. Download it under KITT Config → Speech input (30 MB).",
            )
        ),
        ReleaseNote(
            5,
            listOf(
                "Offline \"Hey KITT\": a small listener on the head unit hears you with no internet, no chimes, even over " +
                    "music, and runs quick commands by itself (\"KITT, pause music\", \"KITT, find gas\"). Turn it on under " +
                    "KITT Config → \"Hey KITT\" wake word.",
                "Safe mode: if KITT ever crashes at start-up a few times in a row, it starts without web pages and extras " +
                    "so you're never stuck, with a button to send a report or go back to normal.",
                "One line on your last drive when you set off: fault codes still on, a weak battery, your grade.",
                "Bigger buttons while driving, with volume controls and your top three apps.",
                "Search in KITT Config, beta updates (Updates → Beta updates), and offline map files for dead zones.",
                "Faster start-up; OBD adapters reconnect faster; the offline voice frees its memory when idle and " +
                    "checks every download.",
            )
        ),
        ReleaseNote(
            4,
            listOf(
                "Film themes now come in editions: T-800, Liquid Metal and Future War; Visor, ED-209 and OCP Delta City; " +
                    "Digital Rain, Real World and Zion; DeLorean 1985, Hill Valley 2015 and 1885. Each has its own colours, " +
                    "scanner, screen effects, readouts and boot sequence. Pick one under KITT Config → Theme.",
                "Eight new film characters to talk to, three per film; each edition brings its own, or pick your favourite.",
                "Film start-up sounds (with sound effects on), and a little surprise at 88 km/h in Back to the Future.",
                "KITT neural (offline): free, natural voices that run on the head unit, no API key. Download one under KITT Config → Voice.",
                "The Games page is now a full retro-game browser: game site tiles, favourites, continue playing, game mode, " +
                    "an on-screen touch pad, and controllers that work with every kind of web emulator.",
                "The stream window has a new home page of free movie, TV, music and radio services (Fandango at Home added), " +
                    "favourites, continue watching and theater mode. Stronger ad and pop-up blocking in both.",
                "Bluetooth OBD adapters connect more reliably on head units, and the browser and YouTube Music run smoother.",
            )
        ),
        ReleaseNote(
            3,
            listOf(
                "System check in KITT Config: every permission and setting KITT needs, with a fix button for each.",
                "Send a problem report to your phone with a QR code, or share it.",
                "KITT warns when it's no longer the home screen, and finds you the nearest gas station when fuel runs low.",
                "Parking spot saved when you switch off; scan it to your phone from the Trips screen.",
                "Speed limit on the map, with an optional warning when you're over it.",
                "GM tire pressures (community-sourced; not every GM vehicle answers).",
                "KITT comments on hard stops, cold revs and long idling, and grades every drive.",
                "KARR, the evil-twin personality. Optional KITT sound effects.",
                "A night theme that switches at sunset, the full-screen Pursuit dashboard, and Maps with KITT's side panel.",
                "Big-text reading for AI answers, a mini player for the stream window, and per-site data saver.",
                "Automatic weekly backups that restore into either edition.",
            )
        ),
        ReleaseNote(2, listOf("\"Hey KITT\" no longer listens over your music.")),
        ReleaseNote(
            1,
            listOf(
                "A stuck web page is shut down with a Reload button instead of freezing the launcher.",
                "Updates install properly, with progress and clear messages.",
                "Lite mode, Screen size, and Savage KITT.",
            )
        ),
    )

    val latest: Int get() = ALL.maxOf { it.level }

    /**
     * Notes newer than [lastSeen]. Nothing on a fresh install ([updated] false): the driver
     * hasn't missed anything.
     */
    fun since(lastSeen: Int, updated: Boolean): List<ReleaseNote> = when {
        !updated -> emptyList()
        // Updated from a build older than this feature: just the latest release.
        lastSeen <= 0 -> ALL.filter { it.level == latest }
        else -> ALL.filter { it.level > lastSeen }
    }
}

/** Pure weekly-backup schedule (unit-tested). */
object AutoBackupRules {
    const val EVERY_MS = 7 * 86_400_000L
    const val KEEP = 4

    fun due(now: Long, lastBackup: Long): Boolean = lastBackup <= 0 || now - lastBackup >= EVERY_MS

    /** Backup file names (sortable by date) to delete, keeping the newest [KEEP]. */
    fun toPrune(names: List<String>): List<String> = names.sortedDescending().drop(KEEP)

    fun fileName(edition: String, date: String): String = "kitt-backup-$edition-$date.json"
}
