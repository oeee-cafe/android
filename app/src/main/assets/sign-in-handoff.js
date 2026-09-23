// Signing in through a browser and taking the answer back (SignInHandoff.kt).
//
// Some sign-ins cannot happen in this web view. Apple has no native sheet on Android, and
// its page opened here would be opened in a browser of the app's own, where the answer
// arrives without this session. So the site hands one out: the page starts a handoff, the
// app opens the browser at the URL it gets, and the page asks the site until the browser
// has finished -- at which point the site signs *this* session in (src/handoff.rs in
// oeee-cafe/web).
//
// Everything the site is asked is asked here, in the page, so it carries the page's cookie
// and origin. The app only opens a browser; it never holds the session, and never sees the
// secret that claims the handoff.
(function () {
  var bridge = window.oeeeHandoff;
  if (!bridge) return;

  // The handoff under way: its id, its secret, and where it is going afterwards.
  var pending = null;
  var asking = null;
  // Whether an ask is in flight. Two at once can spend the handoff between
  // them: one gets the sign-in and the other gets "there is no such handoff",
  // and whichever lands second decides what the page does -- which once left
  // a successful sign-in on the floor, because the failure arrived first and
  // cleared `pending` out from under it.
  var inFlight = false;

  // How often to ask whether the browser has finished, and how long to keep asking. The
  // site forgets a handoff after fifteen minutes, so there is nothing to find after that.
  var ASK_EVERY = 2000;
  var GIVE_UP_AFTER = 15 * 60 * 1000;

  function stop() {
    if (asking) {
      clearInterval(asking);
      asking = null;
    }
    pending = null;
  }

  // Shows the page as it was before the button was tapped, as the Google and Apple
  // sign-ins do when they end without signing anyone in.
  function stay() {
    stop();
    if (window.oeeeRestoreContent) window.oeeeRestoreContent();
  }

  function form(fields) {
    var body = new URLSearchParams();
    for (var name in fields) {
      if (fields[name]) body.set(name, fields[name]);
    }
    return body.toString();
  }

  function post(path, fields) {
    return fetch(path, {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: form(fields)
    }).then(function (response) {
      return response.ok ? response.json() : null;
    });
  }

  // Asks the site whether the browser has finished, and acts on what it says.
  function ask(confirmed) {
    if (!pending || inFlight) return;
    inFlight = true;
    var fields = { id: pending.id, secret: pending.secret };
    if (confirmed) fields.confirm = "1";
    post("/auth/handoff/claim", fields)
      .then(function (answer) {
        inFlight = false;
        if (!answer) {
          // The site could not answer. Nothing has been decided, so keep
          // asking rather than giving up on a sign-in that may have taken.
          return;
        }
        if (!pending) return;
        if (answer.status === "waiting") return;
        if (answer.status === "failed") {
          // Something went wrong there, and the handoff is still good: the
          // next ask tries again.
          return;
        }
        if (answer.status === "confirm") {
          // Linking the browser's account to the one signed in here. The site words the
          // question, because it is the one that knows the reader's language; the app
          // draws it as its own dialog (SiteDialogs.kt).
          if (window.confirm(answer.message)) {
            ask(true);
          } else {
            stay();
          }
          return;
        }
        if (answer.status === "ready") {
          var next = answer.next || "/";
          stop();
          location.href = next;
          return;
        }
        // "unknown", or anything a later site says that this does not know: the sign-in
        // is not going to arrive.
        stay();
      })
      .catch(function () {
        // A request that did not land says nothing either way; the next one will.
        inFlight = false;
      });
  }

  window.oeeeHandoffAuth = {
    // Starts a sign-in with `provider` in a browser, going on to `next` afterwards.
    // The app calls this when it stops the link to /auth/<provider>.
    begin: function (provider, next) {
      if (pending) return;
      post("/auth/handoff/start", { provider: provider, next: next })
        .then(function (started) {
          if (!started || !started.id || !started.secret || !started.url) {
            stay();
            return;
          }
          pending = started;
          bridge.postMessage(JSON.stringify({ url: started.url }));
          asking = setInterval(function () {
            ask(false);
          }, ASK_EVERY);
          setTimeout(function () {
            if (pending) stay();
          }, GIVE_UP_AFTER);
        })
        .catch(stay);
    },

    // The app is in front again, so somebody has probably just come back from the
    // browser: ask now rather than waiting for the next turn of the clock.
    resume: function () {
      if (pending) ask(false);
    },

    // The app went to open the browser and could not.
    failed: function () {
      stay();
    }
  };
})();
