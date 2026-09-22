// Files the page makes itself -- the painter's PNG, a collaborative session's save -- are a
// link to a blob: or data: URL clicked with `download`, which the web view drops without a
// word: its download listener gets a URL that only the page can read. So the page reads it
// and hands the app the bytes (Polyfills.kt). The painter revokes the URL the moment after
// the click, which is why the read has to start inside it.
(function () {
  if (window.__oeeeDownload) return;
  window.__oeeeDownload = true;
  var bridge = window.oeeeDownload;
  if (!bridge) return;
  function local(link) {
    return link.hasAttribute('download') && /^(blob|data):/i.test(link.href);
  }
  function save(link) {
    var name = link.getAttribute('download') || '';
    fetch(link.href).then(function (response) {
      return response.blob();
    }).then(function (blob) {
      var reader = new FileReader();
      reader.onload = function () {
        bridge.postMessage(JSON.stringify({ name: name, type: blob.type || '', data: reader.result }));
      };
      reader.readAsDataURL(blob);
    });
  }
  // A link made and clicked from script, never put in the page, as the painter's is.
  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (local(this)) {
      save(this);
      return;
    }
    return click.apply(this, arguments);
  };
  // A link in the page, clicked by a finger.
  document.addEventListener('click', function (event) {
    var link = event.target && event.target.closest ? event.target.closest('a[download]') : null;
    if (!link || !local(link) || event.defaultPrevented) return;
    event.preventDefault();
    save(link);
  }, true);
})();
