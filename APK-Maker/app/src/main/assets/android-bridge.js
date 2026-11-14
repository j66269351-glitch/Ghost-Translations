/* Makes the web app's webkitSpeechRecognition + clipboard work inside the Android app. */
(function () {
  if (!window.AndroidSR) return;
  var cur = null;
  function SR() { this.lang = 'en-US'; this.interimResults = false; this.continuous = false; }
  SR.prototype.start = function () { cur = this; AndroidSR.start(this.lang, !!this.interimResults); };
  SR.prototype.stop = function () { AndroidSR.stop(); };
  SR.prototype.abort = function () { AndroidSR.abort(); };
  window.SpeechRecognition = window.webkitSpeechRecognition = SR;
  window.__srEvt = function (type, data) {
    var r = cur; if (!r) return;
    try {
      if (type === 'start' && r.onstart) r.onstart({});
      else if (type === 'result' && r.onresult) {
        var d = JSON.parse(data), alt = [{ transcript: d.t, confidence: 1 }];
        alt.isFinal = d.final;
        r.onresult({ resultIndex: 0, results: [alt] });
      } else if (type === 'error' && r.onerror) r.onerror({ error: data });
      else if (type === 'end') { cur = null; if (r.onend) r.onend({}); }
    } catch (e) {}
  };
  try {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: {
      readText: function () { return Promise.resolve(AndroidSR.readClip()); },
      writeText: function (t) { AndroidSR.writeClip(String(t)); return Promise.resolve(); }
    }});
  } catch (e) {}
})();
