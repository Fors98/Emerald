/* =========================================================
   EMERALD — script.js
   Vanilla JS. No dependencies.
   ========================================================= */
(function () {
  "use strict";

  var prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var isCoarsePointer = window.matchMedia("(pointer: coarse)").matches;

  // Set by the sections below so that content re-rendered from the API
  // (see DYNAMIC CONTENT) can re-attach scrollspy observers and count-ups.
  var hooks = { refreshSpy: function () {}, bindCounts: function () {} };
  var E = window.Emerald;

  /* ---------------------------------------------------------
     LOADER
     A short, fast loading sequence. Skips itself instantly
     if the user has already seen it this session, or prefers
     reduced motion.
  --------------------------------------------------------- */
  (function loader() {
    var loaderEl = document.getElementById("loader");
    if (!loaderEl) return;

    var alreadySeen = sessionStorage.getItem("emerald-loaded");
    var wait = prefersReducedMotion || alreadySeen ? 0 : 1500;

    window.setTimeout(function () {
      loaderEl.setAttribute("hidden", "");
      sessionStorage.setItem("emerald-loaded", "1");
    }, wait);
  })();

  /* ---------------------------------------------------------
     CUSTOM CURSOR (fine pointers only)
  --------------------------------------------------------- */
  (function cursor() {
    if (isCoarsePointer) return;
    var dot = document.querySelector(".cursor-dot");
    if (!dot) return;

    var targetX = 0, targetY = 0, x = 0, y = 0;
    window.addEventListener("mousemove", function (e) {
      targetX = e.clientX;
      targetY = e.clientY;
      dot.classList.remove("is-hidden");
    });
    document.addEventListener("mouseleave", function () {
      dot.classList.add("is-hidden");
    });

    function tick() {
      x += (targetX - x) * 0.25;
      y += (targetY - y) * 0.25;
      dot.style.transform = "translate(" + x + "px," + y + "px) translate(-50%,-50%)";
      requestAnimationFrame(tick);
    }
    tick();

    var interactive = "a, button, input, textarea, select, .crystal, .work-card";
    document.addEventListener("mouseover", function (e) {
      if (e.target.closest(interactive)) dot.classList.add("is-active");
    });
    document.addEventListener("mouseout", function (e) {
      if (e.target.closest(interactive)) dot.classList.remove("is-active");
    });
  })();

  /* ---------------------------------------------------------
     NAV: solid background on scroll + mobile menu
  --------------------------------------------------------- */
  (function nav() {
    var navEl = document.getElementById("siteNav");
    var menuBtn = document.getElementById("menuBtn");
    var mobileMenu = document.getElementById("mobileMenu");

    function onScroll() {
      if (window.scrollY > 24) navEl.classList.add("is-scrolled");
      else navEl.classList.remove("is-scrolled");
    }
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });

    if (menuBtn && mobileMenu) {
      menuBtn.addEventListener("click", function () {
        var open = menuBtn.getAttribute("aria-expanded") === "true";
        menuBtn.setAttribute("aria-expanded", String(!open));
        menuBtn.setAttribute("aria-label", !open ? "Close menu" : "Open menu");
        mobileMenu.classList.toggle("is-open", !open);
        document.body.style.overflow = !open ? "hidden" : "";
      });
      mobileMenu.querySelectorAll("a").forEach(function (a) {
        a.addEventListener("click", function () {
          menuBtn.setAttribute("aria-expanded", "false");
          menuBtn.setAttribute("aria-label", "Open menu");
          mobileMenu.classList.remove("is-open");
          document.body.style.overflow = "";
        });
      });
    }
  })();

  /* ---------------------------------------------------------
     CRYSTAL NAVIGATION
     Click: pulse the crystal, draw a constellation line to
     the statue, then smooth-scroll to the target section.
  --------------------------------------------------------- */
  (function crystals() {
    var crystalsEls = document.querySelectorAll(".crystal");
    var stage = document.getElementById("orbitStage");
    var svg = document.getElementById("constellation");

    function buildLines() {
      if (!svg || !stage) return;
      svg.innerHTML = "";
      var stageRect = stage.getBoundingClientRect();
      var center = { x: stageRect.width / 2, y: stageRect.height / 2 };
      crystalsEls.forEach(function (c) {
        var r = c.getBoundingClientRect();
        var cx = r.left - stageRect.left + r.width / 2;
        var cy = r.top - stageRect.top + r.height / 2;
        var line = document.createElementNS("http://www.w3.org/2000/svg", "line");
        line.setAttribute("x1", center.x);
        line.setAttribute("y1", center.y);
        line.setAttribute("x2", cx);
        line.setAttribute("y2", cy);
        line.dataset.service = c.dataset.service;
        svg.appendChild(line);
      });
    }

    if (svg) {
      svg.setAttribute("viewBox", "0 0 " + (stage ? stage.clientWidth : 0) + " " + (stage ? stage.clientHeight : 0));
      window.addEventListener("resize", buildLines);
      window.setTimeout(buildLines, prefersReducedMotion ? 0 : 1600);
    }

    crystalsEls.forEach(function (c) {
      c.addEventListener("mouseenter", function () {
        svg && svg.classList.add("is-active");
        var line = svg && svg.querySelector('line[data-service="' + c.dataset.service + '"]');
        if (line) line.style.opacity = ".8";
      });
      c.addEventListener("mouseleave", function () {
        svg && svg.classList.remove("is-active");
        var line = svg && svg.querySelector('line[data-service="' + c.dataset.service + '"]');
        if (line) line.style.opacity = "";
      });

      c.addEventListener("click", function () {
        c.classList.add("pulse");
        window.setTimeout(function () { c.classList.remove("pulse"); }, 850);

        var targetSel = c.getAttribute("data-target");
        var target = targetSel && document.querySelector(targetSel);
        if (!target) return;

        window.setTimeout(function () {
          var top = target.getBoundingClientRect().top + window.pageYOffset - 84;
          window.scrollTo({ top: top, behavior: prefersReducedMotion ? "auto" : "smooth" });
        }, prefersReducedMotion ? 0 : 180);
      });
    });
  })();

  /* ---------------------------------------------------------
     STATUE COMPANION + SCROLLSPY
     A small fixed placard tracks scroll position and shows a
     different photographed angle of the statue depending on
     which section is centered in the viewport — left/right
     alternate through the middle of the page, and the back
     view is reserved for the finale in the Contact section
     (where it appears as a large embedded image instead).
     The same section-tracking also drives the nav's active
     link highlight (scrollspy).
     Service blocks are discovered from the DOM (.service-block)
     rather than hard-coded, so services added through the admin
     dashboard are tracked too; they alternate left/right.
  --------------------------------------------------------- */
  (function statueCompanionAndScrollspy() {
    var companion = document.getElementById("statueCompanion");
    var companionImg = document.getElementById("companionImg");
    var companionLabel = document.getElementById("companionLabel");
    var leftSrc = document.getElementById("assetAngleLeft");
    var rightSrc = document.getElementById("assetAngleRight");
    if (!companion || !companionImg || !leftSrc || !rightSrc) return;

    // id -> { label, angle, navId }. angle "none" hides the companion
    // (used for the hero, where the big static front view already
    // does the job, and Contact, where the back view takes over as
    // a full finale image instead of the small placard).
    var SECTION_MAP = {
      "home":        { label: "Home",        angle: "none",  navId: "home" },
      "about":       { label: "About",       angle: "right", navId: "about" },
      "how-we-work": { label: "Process",     angle: "right", navId: null },
      "work":        { label: "Work",        angle: "left",  navId: "work" },
      "testimonials":{ label: "Praise",      angle: "right", navId: null },
      "faq":         { label: "FAQ",         angle: "left",  navId: "faq" },
      "contact":     { label: "Contact",     angle: "none",  navId: "contact" }
    };

    var navLinks = document.querySelectorAll('.nav-links a[href^="#"], .mobile-menu a[href^="#"]');
    var currentAngle = null;
    var sectionEntries = [];

    function collectEntries() {
      var entries = Object.keys(SECTION_MAP)
        .map(function (id) { return { id: id, el: document.getElementById(id), config: SECTION_MAP[id] }; })
        .filter(function (entry) { return entry.el; });
      document.querySelectorAll(".service-block").forEach(function (el, i) {
        var h = el.querySelector("h3");
        entries.push({
          id: el.id, el: el,
          config: { label: h ? h.textContent : "Service", angle: i % 2 === 0 ? "left" : "right", navId: "services" }
        });
      });
      return entries;
    }

    function setActiveNav(navId) {
      if (!navId) return;
      navLinks.forEach(function (a) {
        var isMatch = a.getAttribute("href") === "#" + navId;
        a.classList.toggle("is-active", isMatch);
      });
    }

    function showCompanion(config) {
      if (config.angle === "none") {
        companion.classList.remove("is-visible", "is-left");
        return;
      }

      // The angle images are photographed from the opposite visual side:
      // left-facing image -> right side, right-facing image -> left side.
      // This keeps the statue visually looking toward the section content.
      companion.classList.toggle("is-left", config.angle === "right");
      companion.classList.add("is-visible");
      if (config.angle !== currentAngle) {
        currentAngle = config.angle;
        var newSrc = config.angle === "left" ? leftSrc.src : rightSrc.src;
        companionImg.classList.add("is-swapping");
        window.setTimeout(function () {
          companionImg.setAttribute("src", newSrc);
          companionImg.classList.remove("is-swapping");
        }, prefersReducedMotion ? 0 : 220);
      }
    }

    function updateLabel(text) {
      if (companionLabel) companionLabel.textContent = text;
    }

    if (!("IntersectionObserver" in window)) return;

    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var match = sectionEntries.filter(function (s) { return s.el === entry.target; })[0];
        if (!match) return;
        showCompanion(match.config);
        updateLabel(match.config.label);
        setActiveNav(match.config.navId);
      });
    }, { rootMargin: "-45% 0px -45% 0px", threshold: 0 });

    hooks.refreshSpy = function () {
      io.disconnect();
      sectionEntries = collectEntries();
      sectionEntries.forEach(function (entry) { io.observe(entry.el); });
    };
    hooks.refreshSpy();
  })();

  /* ---------------------------------------------------------
     SMOOTH SCROLL for all in-page anchor links (nav, footer)
  --------------------------------------------------------- */
  (function anchors() {
    document.querySelectorAll('a[href^="#"]').forEach(function (a) {
      a.addEventListener("click", function (e) {
        var id = a.getAttribute("href");
        if (!id || id === "#") return;
        var target = document.querySelector(id);
        if (!target) return;
        e.preventDefault();
        var top = target.getBoundingClientRect().top + window.pageYOffset - 84;
        window.scrollTo({ top: top, behavior: prefersReducedMotion ? "auto" : "smooth" });
      });
    });
  })();

  /* ---------------------------------------------------------
     SCROLL REVEALS
     One quiet fade+rise per section header / media block.
  --------------------------------------------------------- */
  (function reveals() {
    var els = document.querySelectorAll(".reveal");
    if (!("IntersectionObserver" in window) || prefersReducedMotion) {
      els.forEach(function (el) { el.classList.add("is-visible"); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.2, rootMargin: "0px 0px -60px 0px" });
    els.forEach(function (el) { io.observe(el); });
  })();

  /* ---------------------------------------------------------
     STATS COUNT-UP
     bindCounts() can be called again after the stats are
     re-rendered from the API; elements already counted are skipped.
  --------------------------------------------------------- */
  (function stats() {
    var io = null;

    function animateCount(el) {
      var target = parseInt(el.getAttribute("data-count"), 10) || 0;
      if (prefersReducedMotion) { el.textContent = target; return; }
      var start = 0;
      var duration = 1200;
      var startTime = null;
      function step(ts) {
        if (!startTime) startTime = ts;
        var progress = Math.min((ts - startTime) / duration, 1);
        var eased = 1 - Math.pow(1 - progress, 3);
        el.textContent = Math.round(start + (target - start) * eased);
        if (progress < 1) requestAnimationFrame(step);
      }
      requestAnimationFrame(step);
    }

    function counted(el) { el.setAttribute("data-counted", "1"); }

    hooks.bindCounts = function () {
      var nums = Array.prototype.filter.call(
        document.querySelectorAll(".stat b[data-count]"),
        function (el) { return !el.hasAttribute("data-counted"); }
      );
      if (!nums.length) return;
      if (!("IntersectionObserver" in window)) {
        nums.forEach(function (el) { counted(el); animateCount(el); });
        return;
      }
      if (!io) {
        io = new IntersectionObserver(function (entries) {
          entries.forEach(function (entry) {
            if (entry.isIntersecting) {
              animateCount(entry.target);
              io.unobserve(entry.target);
            }
          });
        }, { threshold: 0.6 });
      }
      nums.forEach(function (el) { counted(el); io.observe(el); });
    };
    hooks.bindCounts();
  })();

  /* ---------------------------------------------------------
     HOW WE WORK — strata highlight as each stage scrolls
     through the center of the viewport
  --------------------------------------------------------- */
  (function strata() {
    var items = document.querySelectorAll("[data-stratum]");
    if (!items.length || !("IntersectionObserver" in window)) return;
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        entry.target.classList.toggle("is-active", entry.isIntersecting);
      });
    }, { threshold: 0.55 });
    items.forEach(function (el) { io.observe(el); });
  })();

  /* ---------------------------------------------------------
     FAQ ACCORDION
     Delegated on #faqList so it keeps working after the list is
     re-rendered from the API.
  --------------------------------------------------------- */
  (function faq() {
    var list = document.getElementById("faqList");
    if (!list) return;
    list.addEventListener("click", function (e) {
      var btn = e.target.closest(".faq-q");
      if (!btn || !list.contains(btn)) return;
      var item = btn.closest(".faq-item");
      var isOpen = btn.getAttribute("aria-expanded") === "true";
      list.querySelectorAll(".faq-item").forEach(function (i) {
        i.classList.remove("is-open");
        i.querySelector(".faq-q").setAttribute("aria-expanded", "false");
        i.querySelector(".faq-a").setAttribute("aria-hidden", "true");
      });
      if (!isOpen) {
        item.classList.add("is-open");
        btn.setAttribute("aria-expanded", "true");
        item.querySelector(".faq-a").setAttribute("aria-hidden", "false");
      }
    });
  })();

  /* ---------------------------------------------------------
     FORM HELPERS (contact + newsletter)
  --------------------------------------------------------- */
  function setFieldError(inputId, errId, message) {
    var input = document.getElementById(inputId);
    var err = document.getElementById(errId);
    var wrap = input && input.closest(".field");
    if (wrap) wrap.classList.toggle("has-error", !!message);
    if (err) err.textContent = message || "";
    return !message;
  }

  function showStatus(el, text, isError) {
    el.textContent = text;
    el.classList.toggle("is-error", !!isError);
    el.classList.add("is-visible");
  }

  var EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  /* ---------------------------------------------------------
     CONTACT FORM  ->  POST /api/contact   (api.yaml: submitContactForm)
     Client-side validation mirrors the server rules (name required,
     valid email, message >= 10 chars). Server-side 400 responses have
     the shape { errors: { field: message } } and are mapped onto the
     matching #err-<field> spans.
  --------------------------------------------------------- */
  (function contactForm() {
    var form = document.getElementById("contactForm");
    if (!form) return;
    var status = document.getElementById("formStatus");
    var FIELDS = { name: "f-name", email: "f-email", company: "f-company", projectType: "f-type", message: "f-message" };

    function clearErrors() {
      Object.keys(FIELDS).forEach(function (k) { setFieldError(FIELDS[k], "err-" + k, ""); });
    }

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      status.classList.remove("is-visible", "is-error");
      clearErrors();

      var payload = {
        name: document.getElementById("f-name").value.trim(),
        email: document.getElementById("f-email").value.trim(),
        company: document.getElementById("f-company").value.trim(),
        projectType: document.getElementById("f-type").value.trim(),
        message: document.getElementById("f-message").value.trim()
      };

      var validName = setFieldError("f-name", "err-name", payload.name ? "" : "Tell us your name.");
      var validEmail = setFieldError("f-email", "err-email", EMAIL_PATTERN.test(payload.email) ? "" : "Enter a valid email address.");
      var validMessage = setFieldError("f-message", "err-message", payload.message.length >= 10 ? "" : "A few more details would help (10+ characters).");
      if (!(validName && validEmail && validMessage)) return;

      // Optional fields are omitted when empty (they are nullable in the contract).
      if (!payload.company) delete payload.company;
      if (!payload.projectType) delete payload.projectType;

      var submitBtn = form.querySelector(".btn-primary");
      var originalLabel = submitBtn.textContent;
      submitBtn.textContent = "Sending…";
      submitBtn.disabled = true;

      E.request("POST", "/contact", { body: payload }).then(function () {
        showStatus(status, "Thanks — your message is in. We'll reply within two business days.");
        form.reset();
      }).catch(function (err) {
        if (err.status === 400 && err.data && err.data.errors) {
          Object.keys(err.data.errors).forEach(function (k) {
            if (FIELDS[k]) setFieldError(FIELDS[k], "err-" + k, err.data.errors[k]);
          });
          showStatus(status, "Please fix the highlighted fields and try again.", true);
        } else if (err.status === 429) {
          showStatus(status, "Too many messages in a short time. Please wait a moment and try again.", true);
        } else if (E.backendAbsent(err)) {
          showStatus(status, "We couldn't reach the server. Please try again shortly, or email us directly.", true);
        } else {
          showStatus(status, "Something went wrong on our side. Please try again.", true);
        }
      }).then(function () {
        submitBtn.textContent = originalLabel;
        submitBtn.disabled = false;
      });
    });
  })();

  /* ---------------------------------------------------------
     NEWSLETTER  ->  POST /api/newsletter/subscribe
  --------------------------------------------------------- */
  (function newsletter() {
    var form = document.getElementById("newsletterForm");
    if (!form) return;
    var status = document.getElementById("nlStatus");

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      status.classList.remove("is-visible", "is-error");
      var email = document.getElementById("nl-email").value.trim();
      if (!setFieldError("nl-email", "err-nl-email", EMAIL_PATTERN.test(email) ? "" : "Enter a valid email address.")) return;

      var btn = form.querySelector("button[type=submit]");
      btn.disabled = true;
      E.request("POST", "/newsletter/subscribe", { body: { email: email } }).then(function (res) {
        showStatus(status, res && res.status === "already_subscribed" ? "You're already on the list." : "You're subscribed. Thank you.");
        form.reset();
      }).catch(function (err) {
        if (err.status === 400 && err.data && err.data.errors && err.data.errors.email) {
          setFieldError("nl-email", "err-nl-email", err.data.errors.email);
        } else if (E.backendAbsent(err)) {
          showStatus(status, "We couldn't reach the server. Please try again shortly.", true);
        } else {
          showStatus(status, "Something went wrong. Please try again.", true);
        }
      }).then(function () { btn.disabled = false; });
    });
  })();

  /* ---------------------------------------------------------
     DYNAMIC CONTENT
     The static HTML in index.html is the baseline (works without JS
     or a backend). On load we ask the API for the live content and,
     for every request that succeeds, replace that section's markup.
     If the API is unreachable the static content simply stays.
     All text is inserted with textContent — never innerHTML — so
     content entered in the admin dashboard cannot inject markup.
  --------------------------------------------------------- */
  (function dynamicContent() {
    function el(tag, className, text) {
      var n = document.createElement(tag);
      if (className) n.className = className;
      if (text !== undefined && text !== null) n.textContent = text;
      return n;
    }
    function tagList(className, tags) {
      var ul = el("ul", className);
      (tags || []).forEach(function (t) { ul.appendChild(el("li", null, t)); });
      return ul;
    }
    function pad2(n) { return (n < 10 ? "0" : "") + n; }
    function byPosition(a, b) { return (a.position || 0) - (b.position || 0); }
    function initials(name) {
      return String(name || "").split(/\s+/).filter(Boolean).slice(0, 2)
        .map(function (w) { return w.charAt(0).toUpperCase(); }).join("");
    }
    function fill(container, nodes, sectionEl) {
      container.textContent = "";
      nodes.forEach(function (n) { container.appendChild(n); });
      if (sectionEl) sectionEl.hidden = nodes.length === 0;
    }

    var renderers = {
      services: function (items) {
        var container = document.getElementById("servicesList");
        if (!container) return;
        var nodes = items.slice().sort(byPosition).map(function (s, i) {
          var art = el("article", "service-block");
          art.id = s.slug;
          var left = el("div");
          left.appendChild(el("span", "service-index", pad2(i + 1)));
          left.appendChild(el("h3", null, s.title));
          left.appendChild(el("p", null, s.description));
          left.appendChild(tagList("service-tags", s.tags));
          var visual = el("div", "service-visual");
          var facets = el("div", "facet-lines"); facets.setAttribute("aria-hidden", "true");
          var gem = el("span", "crystal-gem"); gem.setAttribute("aria-hidden", "true");
          visual.appendChild(facets); visual.appendChild(gem);
          art.appendChild(left); art.appendChild(visual);
          return art;
        });
        fill(container, nodes, document.getElementById("services"));
        hooks.refreshSpy();
      },

      projects: function (items) {
        var container = document.getElementById("workGrid");
        if (!container) return;
        var nodes = items.slice().sort(byPosition).map(function (p, i) {
          var card = el("article", "work-card p" + ((i % 6) + 1));
          card.tabIndex = 0;
          var bg = el("div", "wc-bg"); bg.setAttribute("aria-hidden", "true");
          card.appendChild(bg);
          card.appendChild(el("span", "wc-cat", p.category));
          card.appendChild(el("h3", null, p.title));
          card.appendChild(el("p", null, p.summary));
          card.appendChild(tagList("wc-tags", p.tags));
          return card;
        });
        fill(container, nodes, document.getElementById("work"));
      },

      testimonials: function (items) {
        var container = document.getElementById("testimonialsGrid");
        if (!container) return;
        var nodes = items.slice().sort(byPosition).map(function (t) {
          var card = el("div", "t-card");
          card.appendChild(el("p", "t-quote", t.quote));
          var person = el("div", "t-person");
          person.appendChild(el("div", "t-avatar", initials(t.authorName)));
          var who = el("div");
          who.appendChild(el("b", null, t.authorName));
          who.appendChild(el("span", null, t.authorRole));
          person.appendChild(who);
          card.appendChild(person);
          return card;
        });
        fill(container, nodes, document.getElementById("testimonials"));
      },

      faqs: function (items) {
        var container = document.getElementById("faqList");
        if (!container) return;
        var nodes = items.slice().sort(byPosition).map(function (f, i) {
          var n = i + 1;
          var item = el("div", "faq-item");
          var h = el("h3");
          var btn = el("button", "faq-q", f.question);
          btn.id = "faq-q-" + n;
          btn.setAttribute("aria-expanded", "false");
          btn.setAttribute("aria-controls", "faq-a-" + n);
          var plus = el("span", "plus"); plus.setAttribute("aria-hidden", "true");
          btn.appendChild(plus);
          h.appendChild(btn);
          var ans = el("div", "faq-a");
          ans.id = "faq-a-" + n;
          ans.setAttribute("aria-hidden", "true");
          ans.setAttribute("role", "region");
          ans.setAttribute("aria-labelledby", btn.id);
          ans.appendChild(el("p", null, f.answer));
          item.appendChild(h); item.appendChild(ans);
          return item;
        });
        fill(container, nodes, document.getElementById("faq"));
      },

      stats: function (items) {
        var container = document.getElementById("statsRow");
        if (!container) return;
        var nodes = items.map(function (s) {
          var wrap = el("div", "stat");
          var b = el("b", null, "0");
          b.setAttribute("data-count", String(s.value));
          wrap.appendChild(b);
          wrap.appendChild(el("span", null, s.label));
          return wrap;
        });
        fill(container, nodes);
        hooks.bindCounts();
      }
    };

    // GET /services, /projects, /testimonials, /faqs, /stats — each returns { items: [...] }
    Object.keys(renderers).forEach(function (name) {
      E.request("GET", "/" + name).then(function (res) {
        if (res && Array.isArray(res.items)) renderers[name](res.items);
      }).catch(function () { /* no backend or request failed: keep the static content */ });
    });
  })();

  /* ---------------------------------------------------------
     STUDIO CLOCK — a small, honest bit of texture in the footer
  --------------------------------------------------------- */
  (function studioClock() {
    var el = document.getElementById("studioClock");
    if (!el) return;
    function update() {
      var now = new Date();
      var time;
      try {
        time = now.toLocaleTimeString("en-GB", {
          timeZone: "Europe/Berlin",
          hour: "2-digit",
          minute: "2-digit"
        });
      } catch (e) {
        time = now.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
      }
      el.textContent = "Studio time — " + time + " CET";
    }
    update();
    window.setInterval(update, 30000);
  })();

  /* ---------------------------------------------------------
     FOOTER YEAR
  --------------------------------------------------------- */
  (function year() {
    var el = document.getElementById("year");
    if (el) el.textContent = new Date().getFullYear();
  })();

})();
