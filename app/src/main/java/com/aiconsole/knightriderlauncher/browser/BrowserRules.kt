package com.aiconsole.knightriderlauncher.browser

/** Ad and tracker hosts blocked when a site has blocking on. Matches the host and its subdomains. */
object AdBlock {

    private val HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com", "pagead2.googlesyndication.com",
        "amazon-adsystem.com", "adnxs.com", "adsrvr.org", "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
        "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com", "smartadserver.com", "moatads.com",
        "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com", "segment.io", "branch.io",
        "facebook.net", "connect.facebook.net", "ads-twitter.com", "ads.linkedin.com", "bat.bing.com",
        "popads.net", "popcash.net", "propellerads.com", "adsterra.com", "exoclick.com", "juicyads.com",
        "mgid.com", "revcontent.com", "zedo.com", "yieldmo.com", "media.net", "33across.com", "sharethrough.com",
        "teads.tv", "imasdk.googleapis.com", "chartbeat.com", "newrelic.com", "nr-data.net", "onesignal.com"
    )

    fun isBlocked(host: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        var candidate = h
        while (true) {
            if (candidate in HOSTS) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || dot == candidate.lastIndexOf('.')) return false
            candidate = candidate.substring(dot + 1)
        }
    }
}

/** Bookmarks in the Netscape HTML format every browser exports and imports. */
object Bookmarks {

    private val LINK = Regex("""<a\s[^>]*href\s*=\s*"([^"]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG = Regex("<[^>]+>")

    /** (title, url) pairs for every http(s) link, de-duplicated by URL. */
    fun parse(html: String): List<Pair<String, String>> = LINK.findAll(html)
        .mapNotNull { m ->
            val url = unescape(m.groupValues[1].trim())
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return@mapNotNull null
            val title = unescape(m.groupValues[2].replace(TAG, "").trim()).ifEmpty { url }
            title to url
        }
        .distinctBy { it.second }
        .toList()

    fun export(items: List<Pair<String, String>>): String = buildString {
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        appendLine("<TITLE>Bookmarks</TITLE>")
        appendLine("<H1>KITT Launcher</H1>")
        appendLine("<DL><p>")
        items.forEach { (title, url) -> appendLine("    <DT><A HREF=\"${escape(url)}\">${escape(title)}</A>") }
        appendLine("</DL><p>")
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'")
    private fun escape(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}

/** Which WebView builds are too old for heavy sites like YouTube Music. */
object WebViewVersion {
    /** Below this Chromium major version, modern web apps are noticeably slow or broken. */
    const val RECOMMENDED_MAJOR = 120

    fun major(versionName: String?): Int? = versionName?.substringBefore('.')?.toIntOrNull()

    fun isOutdated(versionName: String?): Boolean = major(versionName)?.let { it < RECOMMENDED_MAJOR } ?: false
}

/** JavaScript helpers the browser toolbar runs on the current page. */
object PageScripts {

    /**
     * Removes overlays that trap the page (cookie walls, sign-up modals, "open in app" banners):
     * fixed/sticky elements covering a large part of the screen or sitting on top, then restores
     * scrolling that the overlay disabled.
     */
    const val CLOSE_POPUPS = """
(function () {
  var removed = 0, vw = innerWidth, vh = innerHeight;
  document.querySelectorAll('body *').forEach(function (el) {
    var cs = getComputedStyle(el);
    if (cs.position !== 'fixed' && cs.position !== 'sticky') return;
    var r = el.getBoundingClientRect();
    var big = r.width * r.height > vw * vh * 0.25;
    var onTop = parseInt(cs.zIndex || '0', 10) >= 100;
    var dialog = el.getAttribute('role') === 'dialog' || el.getAttribute('aria-modal') === 'true';
    if ((big && onTop) || dialog) { el.remove(); removed++; }
  });
  [document.documentElement, document.body].forEach(function (n) {
    if (!n) return;
    n.style.setProperty('overflow', 'auto', 'important');
    n.style.setProperty('position', 'static', 'important');
    n.classList.remove('modal-open', 'no-scroll', 'noscroll', 'overflow-hidden');
  });
  return removed;
})();
"""

    /**
     * Scrolls the page by [fraction] of a screen. Chat sites (Gemini, Claude, ChatGPT) scroll an
     * inner panel rather than the window, so it scrolls the scrollable box under the middle of the
     * view, else the window if it scrolls, else the largest scrollable box.
     */
    fun scrollBy(fraction: Double): String = """
(function () {
  var f = $fraction;
  function scrolls(el) {
    var y = getComputedStyle(el).overflowY;
    return (y === 'auto' || y === 'scroll' || y === 'overlay') && el.scrollHeight > el.clientHeight + 4;
  }
  var target = null;
  var el = document.elementFromPoint(innerWidth / 2, innerHeight / 2);
  while (el && el !== document.body && el !== document.documentElement) {
    if (scrolls(el)) { target = el; break; }
    el = el.parentElement;
  }
  var root = document.scrollingElement || document.documentElement;
  if (!target && root.scrollHeight <= root.clientHeight + 4) {
    var area = 0;
    document.querySelectorAll('body *').forEach(function (e) {
      if (!scrolls(e)) return;
      var r = e.getBoundingClientRect();
      if (r.width * r.height > area) { area = r.width * r.height; target = e; }
    });
  }
  if (target) target.scrollBy({top: target.clientHeight * f, behavior: 'smooth'});
  else window.scrollBy({top: innerHeight * f, behavior: 'smooth'});
})();
"""

    /** Finds an install link on a userscript page (Greasy Fork, OpenUserJS, GitHub raw…). */
    const val FIND_USERSCRIPT = """
(function () {
  var a = document.querySelector('a.install-link[href]') ||
          Array.prototype.find.call(document.querySelectorAll('a[href]'), function (x) { return /\.user\.js(\?|#|$)/i.test(x.href); });
  return a ? a.href : '';
})();
"""

    /**
     * Injected at document start in every frame: tells the app (through the `KittMedia` bridge)
     * whenever this frame starts or stops making sound. Each frame reports under its own id so a
     * paused page doesn't hide a playing embed.
     */
    const val MEDIA_WATCH = """
(function () {
  if (window.__kittMedia || typeof KittMedia === 'undefined') return;
  window.__kittMedia = true;
  var id = Math.random().toString(36).slice(2), last = null;
  function report() {
    try {
      var playing = Array.prototype.some.call(document.querySelectorAll('video,audio'), function (m) {
        return !m.paused && !m.ended && !m.muted && m.volume > 0;
      });
      if (playing !== last) { last = playing; KittMedia.state(id, playing); }
    } catch (e) {}
  }
  ['play', 'playing', 'pause', 'ended', 'volumechange', 'emptied', 'abort'].forEach(function (t) {
    document.addEventListener(t, report, true);
  });
  window.addEventListener('pagehide', function () { if (last) KittMedia.state(id, false); });
})();
"""

    /** Pauses everything playing in the page (and same-origin frames), marking it for [RESUME_MEDIA]. */
    const val PAUSE_MEDIA = """
(function () {
  function pause(d) {
    try {
      d.querySelectorAll('video,audio').forEach(function (m) { if (!m.paused) { m.dataset.kittPaused = '1'; m.pause(); } });
      d.querySelectorAll('iframe').forEach(function (f) { try { pause(f.contentDocument); } catch (e) {} });
    } catch (e) {}
  }
  pause(document);
})();
"""

    /** Plays again whatever [PAUSE_MEDIA] paused. */
    const val RESUME_MEDIA = """
(function () {
  function resume(d) {
    try {
      d.querySelectorAll('video,audio').forEach(function (m) {
        if (m.dataset.kittPaused) { delete m.dataset.kittPaused; var p = m.play(); if (p && p.catch) p.catch(function () {}); }
      });
      d.querySelectorAll('iframe').forEach(function (f) { try { resume(f.contentDocument); } catch (e) {} });
    } catch (e) {}
  }
  resume(document);
})();
"""

    /** Selected text, or the page's readable text as a fallback (for reading aloud). */
    const val SELECTION_OR_TEXT = "(function(){var s=String(getSelection()||'').trim();return s||document.body.innerText.slice(0,4000);})();"

    fun jsString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                ' ' -> append("\\u2028")
                ' ' -> append("\\u2029")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
