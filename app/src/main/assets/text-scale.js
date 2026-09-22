// The reader's font size as the site's --oeee-text-scale, which its type scale follows (ds.css
// in oeee-cafe/web). This file is a function and not a statement: the app calls it with the
// scale, which changes while the app runs (PageScripts.textScale).
(function (scale) {
  document.documentElement.style.setProperty('--oeee-text-scale', String(scale));
})
