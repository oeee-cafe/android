// Signing in with Google, from inside the page (GoogleSignIn.kt).
//
// Google refuses its own sign-in pages in an embedded web view, so the app stops the
// site's "Sign in with Google" link and asks Credential Manager instead. Everything the
// site is asked is asked here, in the page, so it carries the page's cookie and origin;
// the app never holds the session itself, and only ever handles the nonce and the ID
// token. The same three steps the iOS app takes for Apple (AppleSignIn.swift in
// oeee-cafe/apple):
//
//   1. the page asks the site for this sign-in's state and nonce (POST
//      /auth/google/start), which the site keeps in the web view's session;
//   2. the app hands the nonce to Credential Manager and answers with an ID token;
//   3. the page posts the token and the state to /auth/google, which the site checks
//      against the session and signs in (src/google.rs and
//      src/web/handlers/identity.rs in oeee-cafe/web).
(function () {
  var bridge = window.oeeeGoogleSignIn;
  if (!bridge) return;

  // The sign-in under way: its state, and where it is going on to. One at a time.
  var pending = null;

  // Shows the page as it was before the link was tapped. The site leaves it alone for an
  // app that signs in this way (toolbar.jinja); a page from before it knew that slid a
  // skeleton in over itself for the page it thought was coming, and nothing is coming.
  function stay() {
    if (window.oeeeRestoreContent) window.oeeeRestoreContent();
  }

  function form(fields) {
    var body = new URLSearchParams();
    for (var name in fields) {
      if (fields[name]) body.set(name, fields[name]);
    }
    return body.toString();
  }

  // Posts the app's answer to /auth/google, which the site takes it from.
  //
  // Linking from the account page comes back to the account page (next), so there it is
  // posted without leaving and the page is shown again where it is, with the site's word
  // on how it went. Signing in goes on to wherever the site sends it.
  function answer(fields, next) {
    if (next && next === location.pathname) {
      fetch("/auth/google", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: form(fields),
        // The site's redirect back here is not followed: following it would show its
        // message to the fetch rather than to the page.
        redirect: "manual"
      }).then(reload, reload);
      return;
    }
    var element = document.createElement("form");
    element.method = "post";
    element.action = "/auth/google";
    element.style.display = "none";
    for (var name in fields) {
      if (!fields[name]) continue;
      var input = document.createElement("input");
      input.type = "hidden";
      input.name = name;
      input.value = fields[name];
      element.appendChild(input);
    }
    document.body.appendChild(element);
    element.submit();
  }

  function reload() {
    location.reload();
  }

  // What the app says once Credential Manager has answered: an ID token, a sign-in put
  // away without one (cancelled), or one Google would not make (error).
  bridge.onmessage = function (event) {
    var request = pending;
    pending = null;
    if (!request) return;
    var told = {};
    try {
      told = JSON.parse(event.data) || {};
    } catch (error) {
      told = {};
    }
    if (told.cancelled) {
      stay();
      return;
    }
    answer(
      { state: request.state, id_token: told.id_token, error: told.id_token ? null : "failed" },
      request.next
    );
  };

  window.oeeeGoogleAuth = {
    // Starts a sign-in, going on to `next` afterwards. The app calls this when it stops
    // the link to /auth/google.
    begin: function (next) {
      if (pending) return;
      pending = { state: null, next: next || null };
      fetch("/auth/google/start", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: form({ next: next })
      })
        .then(function (response) {
          return response.ok ? response.json() : null;
        })
        .then(function (started) {
          if (!started || !started.state || !started.nonce) {
            pending = null;
            stay();
            return;
          }
          pending.state = started.state;
          bridge.postMessage(JSON.stringify({ nonce: started.nonce }));
        })
        .catch(function () {
          pending = null;
          stay();
        });
    }
  };
})();
