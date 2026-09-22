// navigator.share, which Android's web view does not have, through the system's share sheet
// (Polyfills.kt). The site checks for it before falling back to copying the link (sharePost
// in post_view.jinja, oeee-cafe/web). Text and links only; no files.
(function () {
  var bridge = window.oeeeShare;
  if (!bridge || navigator.share) return;
  function shareable(data) {
    return !!data && !(data.files && data.files.length) && !!(data.url || data.text || data.title);
  }
  navigator.share = function (data) {
    if (!shareable(data)) {
      return Promise.reject(new TypeError('Nothing this app can share'));
    }
    var text = [data.text, data.url].filter(Boolean).join('\n');
    bridge.postMessage(JSON.stringify({ title: data.title || '', text: text || data.title }));
    return Promise.resolve();
  };
  navigator.canShare = shareable;
})();
