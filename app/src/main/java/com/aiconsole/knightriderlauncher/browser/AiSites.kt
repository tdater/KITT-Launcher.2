package com.aiconsole.knightriderlauncher.browser

/** A chat AI the driver signs in to with their own account (no API credits used). */
data class AiSite(val label: String, val url: String, val host: String, val readSelector: String)

/**
 * The AI page's sites and the page scripts that type a prompt into them and read the latest
 * reply. These sites change their markup; the scripts try known selectors first and then fall
 * back to whatever editable box / last long text block is on the page.
 */
object AiSites {

    val ALL = listOf(
        AiSite("Gemini", "https://gemini.google.com/app", "gemini.google.com", "message-content, model-response"),
        AiSite("Claude", "https://claude.ai/new", "claude.ai", "[data-testid=\"assistant-message\"], .font-claude-message, .font-claude-response"),
        AiSite("Perplexity", "https://www.perplexity.ai/", "perplexity.ai", ".prose"),
        AiSite("ChatGPT", "https://chatgpt.com/", "chatgpt.com", "[data-message-author-role=\"assistant\"]")
    )

    fun forHost(host: String?): AiSite? = host?.let { h -> ALL.firstOrNull { h == it.host || h.endsWith("." + it.host) } }

    /**
     * Types [prompt] into the page's prompt box and sends it. Uses execCommand('insertText') so
     * React/ProseMirror editors register the input, then presses Enter or clicks a send button.
     */
    fun sendPrompt(prompt: String): String = """
(function () {
  var text = ${PageScripts.jsString(prompt)};
  var box = document.querySelector('#prompt-textarea, div[contenteditable="true"].ProseMirror, rich-textarea .ql-editor, textarea') ||
            document.querySelector('[contenteditable="true"], textarea');
  if (!box) return 'no-box';
  box.focus();
  if (box.tagName === 'TEXTAREA') {
    var setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
    setter.call(box, text);
    box.dispatchEvent(new Event('input', { bubbles: true }));
  } else {
    document.execCommand('selectAll', false, null);
    document.execCommand('insertText', false, text);
  }
  setTimeout(function () {
    var send = document.querySelector('button[data-testid="send-button"], button[aria-label*="Send" i], button[aria-label*="Submit" i], button.send-button, button[type="submit"]');
    if (send && !send.disabled) { send.click(); return; }
    box.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
  }, 350);
  return 'sent';
})();
""".trimIndent()

    /** Text of the latest reply on [site], or '' if it can't be found. */
    fun readLatest(site: AiSite?): String = """
(function () {
  var sel = ${PageScripts.jsString(site?.readSelector ?: "")};
  var nodes = sel ? document.querySelectorAll(sel) : [];
  var last = nodes.length ? nodes[nodes.length - 1] : null;
  if (!last) {
    var blocks = Array.prototype.filter.call(document.querySelectorAll('main p, main li, article'), function (n) { return n.innerText.length > 40; });
    last = blocks.length ? blocks[blocks.length - 1].closest('article, div') : null;
  }
  return last ? last.innerText.trim().slice(0, 4000) : '';
})();
""".trimIndent()
}
