/* ═══════════════════════════════════════════════════════════════════
   B·B PROTECT – Android-Brücke
   Wird von index.html geladen und ist im normalen Browser wirkungslos.
   In der Android-App ersetzt sie zwei Web-Funktionen, die eine WebView
   von Haus aus nicht kann:
     1. Spracherkennung  → native Android-SpeechRecognizer
     2. Datei-Download   → Ablage im Ordner "Download"
   ═══════════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  /* ── 1. Spracherkennung ─────────────────────────────────────────── */
  if (window.AndroidSpeech && !window.SpeechRecognition && !window.webkitSpeechRecognition) {
    var active = null;

    function Recognition() {
      this.lang = 'de-DE';
      this.continuous = false;
      this.interimResults = false;
      this.onresult = null;
      this.onend = null;
      this.onerror = null;
      this.onstart = null;
    }
    Recognition.prototype.start = function () {
      if (active) throw new Error('recognition already started');
      active = this;
      window.AndroidSpeech.start(this.lang || 'de-DE');
      if (this.onstart) try { this.onstart({}); } catch (e) {}
    };
    Recognition.prototype.stop = function () {
      if (active === this) window.AndroidSpeech.stop();
    };
    Recognition.prototype.abort = Recognition.prototype.stop;

    /* Vom nativen Teil aufgerufen */
    window.__androidSpeech = {
      result: function (transcript, isFinal) {
        if (!active || !active.onresult) return;
        var alt = { transcript: transcript, confidence: 1 };
        var res = { 0: alt, length: 1, isFinal: !!isFinal, item: function () { return alt; } };
        var list = { 0: res, length: 1, item: function () { return res; } };
        try { active.onresult({ resultIndex: 0, results: list }); } catch (e) {}
      },
      end: function () {
        var r = active; active = null;
        if (r && r.onend) try { r.onend({}); } catch (e) {}
      },
      error: function (code) {
        var r = active; active = null;
        if (r && r.onerror) try { r.onerror({ error: code }); } catch (e) {}
        if (r && r.onend) try { r.onend({}); } catch (e) {}
      }
    };

    window.SpeechRecognition = window.webkitSpeechRecognition = Recognition;
  }

  /* ── 2. Datei-Export (CSV / JSON) ───────────────────────────────── */
  if (window.AndroidFiles) {
    var nativeClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
      var href = this.getAttribute('href') || '';
      var name = this.getAttribute('download');
      if (name && /^blob:|^data:/.test(href)) {
        var a = this;
        fetch(href)
          .then(function (r) { return r.blob(); })
          .then(function (blob) {
            var fr = new FileReader();
            fr.onload = function () {
              var b64 = String(fr.result).split(',')[1] || '';
              window.AndroidFiles.save(name, blob.type || 'application/octet-stream', b64);
            };
            fr.readAsDataURL(blob);
          })
          .catch(function () { nativeClick.call(a); });
        return;
      }
      return nativeClick.apply(this, arguments);
    };
  }
})();
