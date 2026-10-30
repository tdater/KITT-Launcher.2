package com.aiconsole.kittlauncher2.browser

/**
 * Stand-ins served instead of blocked ad and analytics scripts. Game sites often wait for an ad
 * library before starting a game (an `adBreak` callback, an ad player's "complete" event, a
 * tracking callback). An empty response would leave them waiting forever, so these keep the API
 * and report "no ad" straight away, the way uBlock Origin's resources do.
 */
object Surrogates {

    /** The stand-in for a blocked script request, or null to answer with an empty script. */
    fun find(host: String, path: String): String? {
        val h = host.lowercase()
        val p = path.lowercase()
        return when {
            h.endsWith("googlesyndication.com") && p.contains("adsbygoogle") -> ADSBYGOOGLE
            h.endsWith("googletagmanager.com") && (p.contains("/gtag/js") || p.contains("/gtm.js")) -> GTAG
            h.endsWith("google-analytics.com") && (p.endsWith("/analytics.js") || p.endsWith("/ga.js")) -> ANALYTICS
            (h.endsWith("googletagservices.com") || h.endsWith("doubleclick.net")) && p.contains("gpt") -> GPT
            h.endsWith("imasdk.googleapis.com") || h.endsWith("springserve.com") || h.endsWith("spotxchange.com") ||
                h.endsWith("connatix.com") || h.endsWith("aniview.com") || h.endsWith("vidazoo.com") ||
                h.endsWith("primis.tech") || h.endsWith("playwire.com") -> IMA3
            h.endsWith("adinplay.com") -> AIPTAG
            else -> null
        }
    }

    /** Whether a blocked request is a script, judged by its path (WebView doesn't say). */
    fun looksLikeScript(path: String, accept: String?): Boolean {
        val p = path.lowercase()
        return p.endsWith(".js") || p.contains(".js?") || p.contains("/js/") || p.contains("/gtag/") ||
            p.contains("/tag/") || p.contains("adsbygoogle") || p.contains("ima3") || accept?.contains("javascript") == true
    }

    /** Whether a blocked request is a VAST/VMAP XML ad tag that video players parse as XML. */
    fun looksLikeVast(path: String, query: String?): Boolean {
        val p = path.lowercase()
        val q = query?.lowercase().orEmpty()
        return p.endsWith(".xml") || p.contains("vast") || p.contains("vmap") || q.contains("output=vast") || q.contains("output=xml_vast")
    }

    const val EMPTY = "/* blocked by KITT */"
    const val EMPTY_VAST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\"/>"

    /** AdSense, including the H5 games API (adBreak / adConfig) that game pages wait on. */
    private const val ADSBYGOOGLE = """(function () {
  function handle(o) {
    try {
      if (!o || typeof o !== 'object') return;
      if (typeof o.onReady === 'function') setTimeout(o.onReady, 0);
      if (o.type || o.adBreakDone || o.beforeAd || o.afterAd || o.beforeReward) {
        setTimeout(function () {
          if (typeof o.adBreakDone === 'function') o.adBreakDone({ breakType: o.type || 'start', breakName: o.name || '', breakFormat: o.type === 'reward' ? 'reward' : 'interstitial', breakStatus: 'notReady' });
        }, 0);
      }
    } catch (e) {}
  }
  var old = window.adsbygoogle;
  var a = { loaded: true, push: function (o) { handle(o); return 1; } };
  if (old && old.length) for (var i = 0; i < old.length; i++) handle(old[i]);
  window.adsbygoogle = a;
})();"""

    /** gtag.js / GTM: run event callbacks so pages that wait for them carry on. */
    private const val GTAG = """(function () {
  var w = window;
  function call(o) {
    try {
      if (!o) return;
      var cb = o.eventCallback || o.event_callback;
      if (typeof cb === 'function') setTimeout(cb, 1);
      if (o.length >= 3 && o[2] && typeof o[2] === 'object') {
        var c = o[2].event_callback || o[2].eventCallback;
        if (typeof c === 'function') setTimeout(c, 1);
      }
    } catch (e) {}
  }
  var dl = w.dataLayer = w.dataLayer || [];
  try { if (dl.hide && typeof dl.hide.end === 'function') { dl.hide.end(); dl.hide.end = function () {}; } } catch (e) {}
  for (var i = 0; i < dl.length; i++) call(dl[i]);
  var push = dl.push;
  dl.push = function () { for (var j = 0; j < arguments.length; j++) call(arguments[j]); return push.apply(dl, arguments); };
  w.google_tag_manager = w.google_tag_manager || {};
})();"""

    /** analytics.js / ga.js: hit callbacks fire at once. */
    private const val ANALYTICS = """(function () {
  var w = window, q = (w.ga && w.ga.q) || [];
  var tracker = { get: function () {}, set: function () {}, send: function () {} };
  function ga() {
    var a = arguments, last = a[a.length - 1];
    if (typeof a[0] === 'function') { try { a[0](tracker); } catch (e) {} }
    if (last && typeof last === 'object' && typeof last.hitCallback === 'function') setTimeout(last.hitCallback, 1);
  }
  ga.create = function () { return tracker; };
  ga.getAll = function () { return [tracker]; };
  ga.getByName = function () { return tracker; };
  ga.loaded = true;
  w.ga = ga;
  w[w.GoogleAnalyticsObject || 'ga'] = ga;
  w._gaq = w._gaq || [];
  for (var i = 0; i < q.length; i++) ga.apply(null, q[i]);
})();"""

    /** Google Publisher Tag: queued commands run, every call is a no-op that chains. */
    private const val GPT = """(function () {
  var self = function () { return this; };
  var empty = function () { return []; };
  var nul = function () { return null; };
  var slot = { addService: self, defineSizeMapping: self, setTargeting: self, clearTargeting: self, setCollapseEmptyDiv: self,
    setConfig: self, setClickUrl: self, set: self, updateTargetingFromMap: self, setForceSafeFrame: self, setSafeFrameConfig: self,
    get: nul, getTargeting: empty, getTargetingKeys: empty, getResponseInformation: nul,
    getSlotElementId: function () { return ''; }, getAdUnitPath: function () { return ''; } };
  var pubads = { addEventListener: self, removeEventListener: self, enableSingleRequest: self, enableLazyLoad: self,
    collapseEmptyDivs: self, disableInitialLoad: self, refresh: self, clear: self, setTargeting: self, clearTargeting: self,
    setPrivacySettings: self, setRequestNonPersonalizedAds: self, setCentering: self, enableVideoAds: self, display: self,
    setForceSafeFrame: self, setSafeFrameConfig: self, setPublisherProvidedId: self, set: self, updateCorrelator: self,
    setLocation: self, setVideoContent: self, enableAsyncRendering: self, setCookieOptions: self,
    setTagForChildDirectedTreatment: self, getTargeting: empty, getTargetingKeys: empty, getSlots: empty, get: nul,
    isInitialLoadDisabled: function () { return false; } };
  var g = window.googletag = window.googletag || {};
  var queue = g.cmd || [];
  g.apiReady = true;
  g.pubadsReady = true;
  g.pubads = function () { return pubads; };
  g.companionAds = g.content = g.pubads;
  g.defineSlot = g.defineOutOfPageSlot = function () { return slot; };
  g.destroySlots = function () { return true; };
  g.display = g.enableServices = g.disablePublisherConsole = g.openConsole = g.setAdIframeTitle = g.setConfig = function () {};
  g.sizeMapping = function () { var b = { addSize: function () { return b; }, build: function () { return []; } }; return b; };
  g.getVersion = function () { return ''; };
  g.enums = { OutOfPageFormat: {} };
  g.cmd = { push: function (f) { try { f.call(window); } catch (e) {} return 1; } };
  for (var i = 0; i < queue.length; i++) { try { queue[i].call(window); } catch (e) {} }
})();"""

    /** AdinPlay (aiptag): pre-rolls and rewarded ads complete at once. */
    private const val AIPTAG = """(function () {
  var w = window, t = w.aiptag = w.aiptag || {};
  t.cmd = t.cmd || [];
  t.cmd.display = t.cmd.display || [];
  t.cmd.player = t.cmd.player || [];
  function later(f, a) { if (typeof f === 'function') setTimeout(function () { try { f(a); } catch (e) {} }, 0); }
  w.aipPlayer = function (o) {
    o = o || {};
    this.startPreRoll = function () { later(o.AIP_COMPLETE, 'blocked'); later(o.AIP_REMOVE); };
    this.startMidRoll = this.startPreRoll;
    this.startRewardedAd = function () { later(o.AIP_REWARDEDCOMPLETE, 'timeout'); later(o.AIP_REMOVE); };
    this.showVideo = this.startPreRoll;
  };
  w.aipDisplayTag = { display: function () {}, destroy: function () {}, clear: function () {}, refresh: function () {} };
  function run(q) {
    var list = Array.prototype.slice.call(q);
    q.length = 0;
    q.push = function (f) { try { f(); } catch (e) {} return 1; };
    list.forEach(function (f) { try { f(); } catch (e) {} });
  }
  run(t.cmd.player);
  run(t.cmd.display);
  run(t.cmd);
})();"""

    /**
     * Google Interactive Media Ads (IMA3 SDK) stand-in: Tubi, Pluto TV, Plex, Roku Channel,
     * Crackle, Sling, Xumo, Fawesome, PBS and Dailymotion load `imasdk.googleapis.com/js/sdkloader/ima3.js`
     * and wait for `google.ima.AdsLoader` to fire `ADS_MANAGER_LOADED` -> `CONTENT_RESUME_REQUESTED`
     * -> `ALL_ADS_COMPLETED` before starting the main video stream.
     */
    private const val IMA3 = """(function () {
  var w = window;
  w.google = w.google || {};
  var ima = w.google.ima = w.google.ima || {};
  function EvTarget() { this._l = {}; }
  EvTarget.prototype.addEventListener = function (t, fn) {
    if (!this._l[t]) this._l[t] = [];
    if (typeof fn === 'function') this._l[t].push(fn);
  };
  EvTarget.prototype.removeEventListener = function (t, fn) {
    if (!this._l[t]) return;
    this._l[t] = this._l[t].filter(function (f) { return f !== fn; });
  };
  EvTarget.prototype._emit = function (t, data) {
    var list = (this._l && this._l[t]) ? this._l[t].slice() : [];
    for (var i = 0; i < list.length; i++) {
      try { list[i](data); } catch (e) {}
    }
  };
  var AdEventType = {
    AD_BREAK_READY: 'adBreakReady',
    AD_BUFFERING: 'adBuffering',
    AD_CAN_PLAY: 'adCanPlay',
    AD_METADATA: 'adMetadata',
    AD_PROGRESS: 'adProgress',
    ALL_ADS_COMPLETED: 'allAdsCompleted',
    CLICK: 'click',
    COMPLETE: 'complete',
    CONTENT_PAUSE_REQUESTED: 'contentPauseRequested',
    CONTENT_RESUME_REQUESTED: 'contentResumeRequested',
    DURATION_CHANGE: 'durationChange',
    FIRST_QUARTILE: 'firstQuartile',
    IMPRESSION: 'impression',
    INTERACTION: 'interaction',
    LINEAR_CHANGED: 'linearChanged',
    LOADED: 'loaded',
    LOG: 'log',
    MIDPOINT: 'midpoint',
    PAUSED: 'paused',
    RESUMED: 'resumed',
    SKIPPABLE_STATE_CHANGED: 'skippableStateChanged',
    SKIPPED: 'skipped',
    STARTED: 'started',
    THIRD_QUARTILE: 'thirdQuartile',
    USER_CLOSE: 'userClose',
    VOLUME_CHANGED: 'volumeChanged',
    VOLUME_MUTED: 'volumeMuted'
  };
  var AdsManagerLoadedEventType = { ADS_MANAGER_LOADED: 'adsManagerLoaded' };
  var AdErrorEventType = { AD_ERROR: 'adError' };
  ima.AdEvent = { Type: AdEventType };
  ima.AdsManagerLoadedEvent = { Type: AdsManagerLoadedEventType };
  ima.AdErrorEvent = { Type: AdErrorEventType };
  ima.AdError = { ErrorCode: {}, Type: {} };
  ima.ViewMode = { NORMAL: 'normal', FULLSCREEN: 'fullscreen' };
  ima.ImaSdkSettings = function () {};
  ima.ImaSdkSettings.VpaidMode = { DISABLED: 0, ENABLED: 1, INSECURE: 2 };
  ima.ImaSdkSettings.prototype = {
    setVpaidMode: function () {}, setLocale: function () {}, setNumRedirects: function () {},
    setAutoPlayAdBreaks: function () {}, setCookiesEnabled: function () {}, setPlayerType: function () {},
    setPlayerVersion: function () {}, setPpid: function () {}, setSessionId: function () {},
    setFeatureFlags: function () {}, setDisableCustomPlaybackForIOS10Plus: function () {}
  };
  ima.settings = new ima.ImaSdkSettings();
  ima.AdDisplayContainer = function () {
    this.initialize = function () {};
    this.destroy = function () {};
  };
  ima.AdsRequest = function () {
    this.setAdWillAutoPlay = function () {};
    this.setAdWillPlayMuted = function () {};
    this.setContinuousPlayback = function () {};
  };
  ima.AdsRenderingSettings = function () {};
  ima.CompanionAdSelectionSettings = function () {};
  ima.CompanionAdSelectionSettings.CreativeType = { ALL: 'All', FLASH: 'Flash', IMAGE: 'Image' };
  ima.CompanionAdSelectionSettings.ResourceType = { ALL: 'All', HTML: 'Html', IFRAME: 'IFrame', STATIC: 'Static' };
  ima.CompanionAdSelectionSettings.SizeCriteria = { IGNORE: 'IgnoreSize', SELECT_EXACT_MATCH: 'SelectExactMatch', SELECT_NEAR_MATCH: 'SelectNearMatch' };
  function makeManager() {
    var m = new EvTarget();
    m.init = function () {};
    m.start = function () {
      setTimeout(function () {
        m._emit(AdEventType.CONTENT_RESUME_REQUESTED, { type: AdEventType.CONTENT_RESUME_REQUESTED, getAd: function () { return null; }, getAdData: function () { return {}; } });
        m._emit(AdEventType.ALL_ADS_COMPLETED, { type: AdEventType.ALL_ADS_COMPLETED, getAd: function () { return null; }, getAdData: function () { return {}; } });
      }, 0);
    };
    m.resize = m.pause = m.resume = m.stop = m.skip = m.destroy = m.discardAdBreak = m.updateAdsRenderingSettings = m.focus = function () {};
    m.setVolume = function () {};
    m.getVolume = function () { return 1; };
    m.getRemainingTime = function () { return 0; };
    m.getCuePoints = function () { return []; };
    m.getCurrentAd = function () { return null; };
    m.isCustomClickTrackingUsed = function () { return false; };
    m.isCustomPlaybackUsed = function () { return false; };
    return m;
  }
  ima.AdsLoader = function () {
    EvTarget.call(this);
    this._settings = ima.settings;
  };
  ima.AdsLoader.prototype = Object.create(EvTarget.prototype);
  ima.AdsLoader.prototype.getSettings = function () { return this._settings; };
  ima.AdsLoader.prototype.contentComplete = function () {};
  ima.AdsLoader.prototype.destroy = function () {};
  ima.AdsLoader.prototype.requestAds = function () {
    var self = this;
    var mgr = makeManager();
    setTimeout(function () {
      self._emit(AdsManagerLoadedEventType.ADS_MANAGER_LOADED, {
        type: AdsManagerLoadedEventType.ADS_MANAGER_LOADED,
        getAdsManager: function () { return mgr; },
        getUserRequestContext: function () { return {}; }
      });
    }, 0);
  };
  ima.dai = ima.dai || {
    api: {
      StreamManager: function () {
        EvTarget.call(this);
        this.requestStream = function () {};
        this.replaceAdTagParameters = function () {};
        this.reset = function () {};
        this.setClickElement = function () {};
        this.processMetadata = function () {};
        this.onTimedMetadata = function () {};
      },
      StreamEvent: { Type: { LOADED: 'loaded', AD_BREAK_STARTED: 'adBreakStarted', AD_BREAK_ENDED: 'adBreakEnded', ERROR: 'error' } },
      VODStreamRequest: function () {},
      LiveStreamRequest: function () {}
    }
  };
})();"""
}
