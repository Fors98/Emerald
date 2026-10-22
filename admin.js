/* =========================================================
   EMERALD — admin.js
   Admin dashboard for admin.html. Talks to the API described in
   api.yaml (auth, /admin/* and the public content endpoints).

   Demo mode: if no backend answers at Emerald.API_BASE (network error
   or a non-JSON reply from a static server), the dashboard runs
   against an in-memory copy of Emerald.SEED and accepts any sign-in,
   with a visible banner. Nothing is persisted in demo mode.

   Security notes for the backend team:
   - The bearer token is kept in sessionStorage (cleared when the tab
     closes). If you prefer an httpOnly cookie session instead, change
     the auth section of api.yaml before building the controllers.
   - All text is rendered with textContent, never innerHTML.
   ========================================================= */
(function () {
  "use strict";

  var E = window.Emerald;
  var TOKEN_KEY = "emerald-admin-token";
  var state = { token: null, demo: false, user: null, tab: "overview" };

  /* ---------- tiny DOM helper ---------- */
  function h(tag, attrs, kids) {
    var n = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      var v = attrs[k];
      if (v === null || v === undefined || v === false) return;
      if (k === "class") n.className = v;
      else if (k === "value") n.value = v;
      else if (k.indexOf("on") === 0) n.addEventListener(k.slice(2), v);
      else n.setAttribute(k, v === true ? "" : v);
    });
    [].concat(kids === undefined ? [] : kids).forEach(function (c) {
      if (c === null || c === undefined || c === false) return;
      n.appendChild(typeof c === "string" || typeof c === "number" ? document.createTextNode(String(c)) : c);
    });
    return n;
  }
  function $(id) { return document.getElementById(id); }
  function clone(x) { return JSON.parse(JSON.stringify(x)); }
  function fmtDate(iso) {
    var d = new Date(iso);
    return isNaN(d) ? String(iso || "") : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
  }
  function store(key, val) {
    try { if (val === null) sessionStorage.removeItem(key); else sessionStorage.setItem(key, val); } catch (e) { /* storage blocked */ }
  }
  function load(key) { try { return sessionStorage.getItem(key); } catch (e) { return null; } }

  /* ---------- demo store ---------- */
  var demoSeq = 100;
  var DEMO = null;
  function demoData() {
    if (!DEMO) {
      DEMO = {
        services: clone(E.SEED.services), projects: clone(E.SEED.projects),
        testimonials: clone(E.SEED.testimonials), faqs: clone(E.SEED.faqs), stats: clone(E.SEED.stats),
        contacts: [
          { id: "00000000-0000-4000-8000-0000000000a1", name: "Alex Rivera", email: "alex@example.com", company: "Northwind Ltd", projectType: "Web Development",
            message: "We need a rebuild of our marketing site before Q1.", status: "received", receivedAt: new Date(Date.now() - 3600e3).toISOString() },
          { id: "00000000-0000-4000-8000-0000000000a2", name: "Sam Okoye", email: "sam@example.com", company: null, projectType: "Cyber",
            message: "Looking for a security audit of our customer portal.", status: "read", receivedAt: new Date(Date.now() - 86400e3).toISOString() }
        ],
        subscribers: [
          { id: "00000000-0000-4000-8000-0000000000b1", email: "reader@example.com", subscribedAt: new Date(Date.now() - 5 * 86400e3).toISOString() }
        ]
      };
    }
    return DEMO;
  }
  function demoId() { demoSeq += 1; return "00000000-0000-4000-8000-" + ("000000000000" + demoSeq).slice(-12); }

  /* ---------- data layer ---------- */
  function expired() {
    state.token = null; store(TOKEN_KEY, null);
    showLogin("Your session has expired. Please sign in again.");
  }
  // One refresh at a time. The server rotates the refresh cookie on every call and treats reuse of an
  // old cookie as theft, so two parallel refreshes would end the session. Everyone shares one promise.
  var refreshing = null;
  function refreshSession() {
    if (!refreshing) {
      refreshing = E.request("POST", "/auth/refresh").then(function (auth) {
        state.token = auth.accessToken;
        state.user = auth.user;
        store(TOKEN_KEY, state.token);
        refreshing = null;
        return auth;
      }, function (err) {
        refreshing = null;
        throw err;
      });
    }
    return refreshing;
  }
  // Runs a live request with the bearer token (or the demo function in demo mode).
  // On 401 it silently refreshes the access token once and retries; only if that fails is the user signed out.
  function call(method, path, body, demoFn) {
    if (state.demo) {
      try { return Promise.resolve(demoFn()); } catch (e) { return Promise.reject(e); }
    }
    function send() { return E.request(method, path, { body: body, token: state.token }); }
    return send().catch(function (err) {
      if (err.status !== 401) throw err;
      return refreshSession().then(function () { return send(); }).catch(function (e) {
        if (e.status === 401 || e.status === 403) expired();
        throw e;
      });
    });
  }
  function demoError(status, message) { var e = new E.ApiError(status, { message: message }, false); return e; }

  var api = {
    login: function (email, password) {
      return E.request("POST", "/auth/login", { body: { email: email, password: password } }).catch(function (err) {
        if (E.backendAbsent(err)) {
          state.demo = true;
          return { accessToken: "demo", tokenType: "Bearer", expiresIn: 3600, user: { email: email, role: "ADMIN" } };
        }
        throw err;
      });
    },
    logout: function () {
      if (state.demo) return Promise.resolve();
      return E.request("POST", "/auth/logout", { token: state.token }).catch(function () { /* token is dropped locally either way */ });
    },
    me: function () { return call("GET", "/auth/me", undefined, function () { return state.user; }); },

    dashboard: function () {
      return call("GET", "/admin/dashboard", undefined, function () {
        var d = demoData();
        return {
          contactsTotal: d.contacts.length,
          contactsNew: d.contacts.filter(function (c) { return c.status === "received"; }).length,
          subscribersTotal: d.subscribers.length,
          services: d.services.length, projects: d.projects.length,
          testimonials: d.testimonials.length, faqs: d.faqs.length
        };
      });
    },

    contacts: function (status) {
      var q = "?page=0&size=100" + (status ? "&status=" + encodeURIComponent(status) : "");
      return call("GET", "/admin/contacts" + q, undefined, function () {
        var items = demoData().contacts.filter(function (c) { return !status || c.status === status; });
        return { items: clone(items), page: 0, size: 100, totalItems: items.length, totalPages: 1 };
      });
    },
    updateContact: function (id, status) {
      return call("PATCH", "/admin/contacts/" + id, { status: status }, function () {
        var c = demoData().contacts.filter(function (x) { return x.id === id; })[0];
        if (!c) throw demoError(404, "Not found");
        c.status = status; return clone(c);
      });
    },
    deleteContact: function (id) {
      return call("DELETE", "/admin/contacts/" + id, undefined, function () {
        DEMO.contacts = DEMO.contacts.filter(function (x) { return x.id !== id; });
      });
    },

    subscribers: function () {
      return call("GET", "/admin/subscribers?page=0&size=100", undefined, function () {
        var items = demoData().subscribers;
        return { items: clone(items), page: 0, size: 100, totalItems: items.length, totalPages: 1 };
      });
    },
    deleteSubscriber: function (id) {
      return call("DELETE", "/admin/subscribers/" + id, undefined, function () {
        DEMO.subscribers = DEMO.subscribers.filter(function (x) { return x.id !== id; });
      });
    },

    // Public read endpoints (also used by the website).
    list: function (name) {
      if (state.demo) return Promise.resolve({ items: clone(demoData()[name]) });
      return E.request("GET", "/" + name);
    },
    create: function (name, body) {
      return call("POST", "/admin/" + name, body, function () {
        var item = clone(body); item.id = demoId(); demoData()[name].push(item); return item;
      });
    },
    update: function (name, id, body) {
      return call("PUT", "/admin/" + name + "/" + id, body, function () {
        var arr = demoData()[name];
        for (var i = 0; i < arr.length; i++) {
          if (arr[i].id === id) { var keep = arr[i].slug; arr[i] = Object.assign(clone(body), { id: id }); if (keep) arr[i].slug = keep; return arr[i]; }
        }
        throw demoError(404, "Not found");
      });
    },
    remove: function (name, id) {
      return call("DELETE", "/admin/" + name + "/" + id, undefined, function () {
        DEMO[name] = DEMO[name].filter(function (x) { return x.id !== id; });
      });
    },
    saveStats: function (items) {
      return call("PUT", "/admin/stats", { items: items }, function () { demoData().stats = clone(items); return { items: clone(items) }; });
    }
  };

  /* ---------- views ---------- */
  var panel = $("panel");

  function errText(err) {
    if (err && err.data && err.data.errors) {
      return Object.keys(err.data.errors).map(function (k) { return k + ": " + err.data.errors[k]; }).join(" · ");
    }
    if (E.backendAbsent(err)) return "Could not reach the server.";
    return (err && err.message) || "Something went wrong.";
  }
  function flash(message, isError) {
    var old = panel.querySelector(".flash"); if (old) old.remove();
    var box = h("div", { class: "form-status flash is-visible" + (isError ? " is-error" : ""), role: isError ? "alert" : "status" }, message);
    panel.insertBefore(box, panel.children[1] || null);
  }
  function emptyRow(text) { return h("div", { class: "empty" }, text); }

  function showLogin(message) {
    $("loginView").hidden = false; $("appView").hidden = true;
    $("logoutBtn").hidden = true; $("adminUser").textContent = "";
    $("demoBanner").hidden = true;
    var st = $("loginStatus");
    st.textContent = message || ""; st.classList.toggle("is-visible", !!message);
  }
  function showApp() {
    $("loginView").hidden = true; $("appView").hidden = false;
    $("logoutBtn").hidden = false;
    $("adminUser").textContent = state.user ? state.user.email : "";
    $("demoBanner").hidden = !state.demo;
    $("demoBase").textContent = E.API_BASE;
    buildTabs(); go(state.tab);
  }

  var TABS = [
    ["overview", "Overview"], ["messages", "Messages"], ["subscribers", "Subscribers"],
    ["services", "Services"], ["projects", "Projects"], ["testimonials", "Testimonials"],
    ["faqs", "FAQ"], ["stats", "Stats"]
  ];
  function buildTabs() {
    var nav = $("tabs"); nav.textContent = "";
    TABS.forEach(function (t) {
      nav.appendChild(h("button", {
        type: "button", role: "tab", "data-tab": t[0],
        "aria-selected": String(t[0] === state.tab),
        onclick: function () { go(t[0]); }
      }, t[1]));
    });
  }
  function go(tab) {
    state.tab = tab;
    Array.prototype.forEach.call($("tabs").children, function (b) {
      b.setAttribute("aria-selected", String(b.getAttribute("data-tab") === tab));
    });
    panel.textContent = "";
    var view = tab === "overview" ? viewOverview : tab === "messages" ? viewMessages : tab === "subscribers" ? viewSubscribers
      : tab === "stats" ? viewStats : function () { return viewCollection(tab); };
    Promise.resolve(view()).catch(function (err) { if (err && err.status !== 401) flash(errText(err), true); });
  }

  /* Overview */
  function viewOverview() {
    panel.appendChild(h("h2", {}, "Overview"));
    return api.dashboard().then(function (d) {
      var cells = [
        ["New messages", d.contactsNew], ["All messages", d.contactsTotal], ["Subscribers", d.subscribersTotal],
        ["Services", d.services], ["Projects", d.projects], ["Testimonials", d.testimonials], ["FAQ entries", d.faqs]
      ];
      panel.appendChild(h("div", { class: "cards" }, cells.map(function (c) {
        return h("div", { class: "card" }, [h("b", {}, c[1]), h("span", {}, c[0])]);
      })));
    });
  }

  /* Messages (contact submissions) */
  var STATUSES = ["received", "read", "replied", "archived"];
  function viewMessages(filter) {
    panel.textContent = "";
    panel.appendChild(h("h2", {}, "Messages"));
    var sel = h("select", { class: "inline", "aria-label": "Filter by status", onchange: function () { viewMessages(sel.value); } },
      [h("option", { value: "" }, "All statuses")].concat(STATUSES.map(function (s) { return h("option", { value: s, selected: s === filter }, s); })));
    panel.appendChild(h("div", { class: "toolbar" }, [sel]));
    return api.contacts(filter || "").then(function (res) {
      if (!res.items.length) { panel.appendChild(emptyRow("No messages.")); return; }
      var rows = res.items.map(function (c) {
        var status = h("select", { class: "inline", "aria-label": "Status for " + c.name, onchange: function () {
          api.updateContact(c.id, status.value).then(function () { flash("Status updated."); }).catch(function (e) { flash(errText(e), true); });
        } }, STATUSES.map(function (s) { return h("option", { value: s, selected: s === c.status }, s); }));
        return h("tr", {}, [
          h("td", {}, fmtDate(c.receivedAt)),
          h("td", {}, [h("div", {}, c.name), h("div", { class: "muted" }, c.email), c.company ? h("div", { class: "muted" }, c.company) : null]),
          h("td", {}, c.projectType || "—"),
          h("td", {}, h("div", { class: "msg" }, c.message)),
          h("td", {}, status),
          h("td", {}, h("button", { type: "button", class: "btn btn-danger btn-sm", onclick: function () {
            if (!window.confirm("Delete this message permanently?")) return;
            api.deleteContact(c.id).then(function () { viewMessages(filter); }).catch(function (e) { flash(errText(e), true); });
          } }, "Delete"))
        ]);
      });
      panel.appendChild(h("div", { class: "table-wrap" }, h("table", {}, [
        h("thead", {}, h("tr", {}, ["Received", "From", "Type", "Message", "Status", ""].map(function (t) { return h("th", { scope: "col" }, t); }))),
        h("tbody", {}, rows)
      ])));
    });
  }

  /* Subscribers */
  function viewSubscribers() {
    panel.textContent = "";
    panel.appendChild(h("h2", {}, "Subscribers"));
    return api.subscribers().then(function (res) {
      if (!res.items.length) { panel.appendChild(emptyRow("No subscribers yet.")); return; }
      var rows = res.items.map(function (s) {
        return h("tr", {}, [
          h("td", {}, s.email), h("td", {}, fmtDate(s.subscribedAt)),
          h("td", {}, h("button", { type: "button", class: "btn btn-danger btn-sm", onclick: function () {
            if (!window.confirm("Remove " + s.email + "?")) return;
            api.deleteSubscriber(s.id).then(viewSubscribers).catch(function (e) { flash(errText(e), true); });
          } }, "Remove"))
        ]);
      });
      panel.appendChild(h("div", { class: "table-wrap" }, h("table", {}, [
        h("thead", {}, h("tr", {}, ["Email", "Subscribed", ""].map(function (t) { return h("th", { scope: "col" }, t); }))),
        h("tbody", {}, rows)
      ])));
    });
  }

  /* Content collections: services, projects, testimonials, faqs */
  var COLLECTIONS = {
    services: { label: "Services", titleKey: "title", fields: [
      { k: "slug", label: "Slug (URL anchor, e.g. web-dev)", type: "text", createOnly: true, req: true, pattern: "[a-z0-9]+(-[a-z0-9]+)*" },
      { k: "title", label: "Title", type: "text", req: true },
      { k: "description", label: "Description", type: "textarea", req: true },
      { k: "tags", label: "Tags (comma separated)", type: "tags" },
      { k: "position", label: "Position", type: "number", req: true }
    ] },
    projects: { label: "Projects", titleKey: "title", fields: [
      { k: "title", label: "Title", type: "text", req: true },
      { k: "category", label: "Category", type: "text", req: true },
      { k: "summary", label: "Summary", type: "textarea", req: true },
      { k: "tags", label: "Tags (comma separated)", type: "tags" },
      { k: "position", label: "Position", type: "number", req: true }
    ] },
    testimonials: { label: "Testimonials", titleKey: "authorName", fields: [
      { k: "authorName", label: "Author name", type: "text", req: true },
      { k: "authorRole", label: "Author role / company", type: "text", req: true },
      { k: "quote", label: "Quote", type: "textarea", req: true },
      { k: "position", label: "Position", type: "number", req: true }
    ] },
    faqs: { label: "FAQ", titleKey: "question", fields: [
      { k: "question", label: "Question", type: "text", req: true },
      { k: "answer", label: "Answer", type: "textarea", req: true },
      { k: "position", label: "Position", type: "number", req: true }
    ] }
  };

  function viewCollection(name, editing) {
    var cfg = COLLECTIONS[name];
    panel.textContent = "";
    panel.appendChild(h("h2", {}, cfg.label));
    return api.list(name).then(function (res) {
      var items = res.items.slice().sort(function (a, b) { return (a.position || 0) - (b.position || 0); });
      if (!items.length) panel.appendChild(emptyRow("Nothing here yet."));
      else {
        var rows = items.map(function (it) {
          return h("tr", {}, [
            h("td", {}, it.position), h("td", {}, it[cfg.titleKey]),
            h("td", { class: "actions" }, [
              h("button", { type: "button", class: "btn btn-ghost btn-sm", onclick: function () { viewCollection(name, it); } }, "Edit"),
              h("button", { type: "button", class: "btn btn-danger btn-sm", onclick: function () {
                if (!window.confirm("Delete “" + it[cfg.titleKey] + "”?")) return;
                api.remove(name, it.id).then(function () { viewCollection(name); }).catch(function (e) { flash(errText(e), true); });
              } }, "Delete")
            ])
          ]);
        });
        panel.appendChild(h("div", { class: "table-wrap" }, h("table", {}, [
          h("thead", {}, h("tr", {}, ["#", "Title", ""].map(function (t) { return h("th", { scope: "col" }, t); }))),
          h("tbody", {}, rows)
        ])));
      }
      panel.appendChild(h("h3", {}, editing ? "Edit" : "Add new"));
      panel.appendChild(collectionForm(name, cfg, editing, items.length + 1));
    });
  }

  function collectionForm(name, cfg, editing, nextPos) {
    var inputs = {};
    var fields = cfg.fields.filter(function (f) { return !(f.createOnly && editing); }).map(function (f) {
      var id = "cf-" + f.k;
      var val = editing ? editing[f.k] : (f.k === "position" ? nextPos : "");
      if (f.type === "tags") val = (val || []).join(", ");
      var input = f.type === "textarea"
        ? h("textarea", { id: id, required: f.req, rows: 4, value: val })
        : h("input", { id: id, type: f.type === "number" ? "number" : "text", min: f.type === "number" ? "1" : false, pattern: f.pattern, required: f.req, value: val });
      inputs[f.k] = { f: f, el: input };
      return h("div", { class: "field" }, [h("label", { for: id }, f.label), input]);
    });
    var form = h("form", { class: "edit-form", novalidate: true }, fields.concat([
      h("div", { class: "row" }, [
        h("button", { type: "submit", class: "btn btn-primary" }, editing ? "Save changes" : "Add"),
        editing ? h("button", { type: "button", class: "btn btn-ghost", onclick: function () { viewCollection(name); } }, "Cancel") : null
      ])
    ]));
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var body = {}, missing = null;
      Object.keys(inputs).forEach(function (k) {
        var f = inputs[k].f, raw = inputs[k].el.value.trim();
        if (f.req && !raw && !missing) missing = f.label;
        if (f.type === "number") body[k] = parseInt(raw, 10) || 1;
        else if (f.type === "tags") body[k] = raw ? raw.split(",").map(function (t) { return t.trim(); }).filter(Boolean) : [];
        else body[k] = raw;
      });
      if (missing) { flash("Please fill in: " + missing, true); return; }
      if (inputs.slug && !new RegExp("^" + inputs.slug.f.pattern + "$").test(body.slug)) { flash("Slug may only contain lowercase letters, digits and hyphens.", true); return; }
      var op = editing ? api.update(name, editing.id, body) : api.create(name, body);
      op.then(function () { return viewCollection(name).then(function () { flash(editing ? "Saved." : "Added."); }); })
        .catch(function (err) { flash(errText(err), true); });
    });
    return form;
  }

  /* Stats */
  function viewStats() {
    panel.textContent = "";
    panel.appendChild(h("h2", {}, "Stats"));
    return api.list("stats").then(function (res) {
      var rows = res.items.map(function (s, i) {
        return h("div", { class: "srow" }, [
          h("input", { type: "text", "aria-label": "Label " + (i + 1), value: s.label, "data-k": "label" }),
          h("input", { type: "number", min: "0", "aria-label": "Value " + (i + 1), value: s.value, "data-k": "value" })
        ]);
      });
      var form = h("form", { class: "stats-form", novalidate: true }, rows.concat([
        h("div", {}, h("button", { type: "submit", class: "btn btn-primary" }, "Save stats"))
      ]));
      form.addEventListener("submit", function (e) {
        e.preventDefault();
        var out = res.items.map(function (s, i) {
          var pair = rows[i].querySelectorAll("input");
          return { key: s.key, label: pair[0].value.trim() || s.label, value: Math.max(0, parseInt(pair[1].value, 10) || 0) };
        });
        api.saveStats(out).then(function () { flash("Stats saved."); }).catch(function (err) { flash(errText(err), true); });
      });
      panel.appendChild(form);
    });
  }

  /* ---------- auth wiring ---------- */
  function enter(auth) {
    state.token = auth.accessToken;
    state.user = auth.user;
    if (!state.demo) store(TOKEN_KEY, state.token);
    $("l-password").value = "";
    showApp();
  }

  $("loginForm").addEventListener("submit", function (e) {
    e.preventDefault();
    var email = $("l-email").value.trim(), password = $("l-password").value;
    if (!email || !password) { showLogin("Enter your email and password."); return; }
    var btn = e.target.querySelector("button[type=submit]");
    btn.disabled = true;
    api.login(email, password).then(enter).catch(function (err) {
      showLogin(err.status === 401 ? "Incorrect email or password." : errText(err));
    }).then(function () { btn.disabled = false; });
  });

  $("logoutBtn").addEventListener("click", function () {
    api.logout().then(function () {
      state.token = null; state.user = null; state.demo = false; DEMO = null;
      store(TOKEN_KEY, null);
      showLogin("");
    });
  });

  // Restore a session from this tab, if any.
  var saved = load(TOKEN_KEY);
  if (saved) {
    state.token = saved;
    api.me().then(function (user) { state.user = user; showApp(); }).catch(function () { state.token = null; store(TOKEN_KEY, null); showLogin(""); });
  } else {
    showLogin("");
  }
})();
