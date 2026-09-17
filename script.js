/* =========================================================
   EMERALD — script.js
   Vanilla JS. No dependencies.
   ========================================================= */
(function () {
  "use strict";

  var prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var isCoarsePointer = window.matchMedia("(pointer: coarse)").matches;

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
        mobileMenu.classList.toggle("is-open", !open);
        document.body.style.overflow = !open ? "hidden" : "";
      });
      mobileMenu.querySelectorAll("a").forEach(function (a) {
        a.addEventListener("click", function () {
          menuBtn.setAttribute("aria-expanded", "false");
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
      "web-dev":     { label: "Web Dev",     angle: "left",  navId: "services" },
      "ui-ux":       { label: "UI / UX",     angle: "right", navId: "services" },
      "cyber":       { label: "Cyber",       angle: "left",  navId: "services" },
      "marketing":   { label: "Marketing",   angle: "right", navId: "services" },
      "devops":      { label: "DevOps",      angle: "left",  navId: "services" },
      "how-we-work": { label: "Process",     angle: "right", navId: null },
      "work":        { label: "Work",        angle: "left",  navId: "work" },
      "testimonials":{ label: "Praise",      angle: "right", navId: null },
      "faq":         { label: "FAQ",         angle: "left",  navId: "faq" },
      "contact":     { label: "Contact",     angle: "none",  navId: "contact" }
    };

    var sectionEntries = Object.keys(SECTION_MAP)
      .map(function (id) { return { id: id, el: document.getElementById(id), config: SECTION_MAP[id] }; })
      .filter(function (entry) { return entry.el; });

    var navLinks = document.querySelectorAll('.nav-links a[href^="#"], .mobile-menu a[href^="#"]');
    var currentAngle = null;

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

    if (!("IntersectionObserver" in window) || sectionEntries.length === 0) return;

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

    sectionEntries.forEach(function (entry) { io.observe(entry.el); });
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
  --------------------------------------------------------- */
  (function stats() {
    var nums = document.querySelectorAll(".stat b[data-count]");
    if (!nums.length) return;

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

    if (!("IntersectionObserver" in window)) {
      nums.forEach(animateCount);
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          animateCount(entry.target);
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.6 });
    nums.forEach(function (el) { io.observe(el); });
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
  --------------------------------------------------------- */
  (function faq() {
    var items = document.querySelectorAll(".faq-item");
    items.forEach(function (item) {
      var btn = item.querySelector(".faq-q");
      btn.addEventListener("click", function () {
        var isOpen = btn.getAttribute("aria-expanded") === "true";
        items.forEach(function (i) {
          i.classList.remove("is-open");
          i.querySelector(".faq-q").setAttribute("aria-expanded", "false");
        });
        if (!isOpen) {
          item.classList.add("is-open");
          btn.setAttribute("aria-expanded", "true");
        }
      });
    });
  })();

  /* ---------------------------------------------------------
     CONTACT FORM
     Client-side validation + a clearly-labelled demo submit.
     Replace the block marked below with a real fetch() call
     to your backend or form endpoint (e.g. Formspree, a
     serverless function, or your own API) to go live.
  --------------------------------------------------------- */
  (function contactForm() {
    var form = document.getElementById("contactForm");
    if (!form) return;
    var status = document.getElementById("formStatus");

    function setError(fieldId, errId, message) {
      var field = document.getElementById(fieldId);
      var err = document.getElementById(errId);
      var wrap = field.closest(".field");
      if (message) {
        wrap.classList.add("has-error");
        if (err) err.textContent = message;
      } else {
        wrap.classList.remove("has-error");
        if (err) err.textContent = "";
      }
      return !message;
    }

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      status.classList.remove("is-visible");

      var name = document.getElementById("f-name").value.trim();
      var email = document.getElementById("f-email").value.trim();
      var message = document.getElementById("f-message").value.trim();
      var emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

      var validName = setError("f-name", "err-name", name ? "" : "Tell us your name.");
      var validEmail = setError("f-email", "err-email", emailPattern.test(email) ? "" : "Enter a valid email address.");
      var validMessage = setError("f-message", "err-message", message.length >= 10 ? "" : "A few more details would help (10+ characters).");

      if (!(validName && validEmail && validMessage)) return;

      /* ---------------------------------------------------
         DEMO SUBMIT — no backend is connected in this build.
         To go live, replace this block with something like:

         fetch("https://your-api.example.com/contact", {
           method: "POST",
           headers: { "Content-Type": "application/json" },
           body: JSON.stringify(Object.fromEntries(new FormData(form)))
         })
           .then(function (res) { ... })
           .catch(function (err) { ... });
      --------------------------------------------------- */
      var submitBtn = form.querySelector(".btn-primary");
      var originalLabel = submitBtn.textContent;
      submitBtn.textContent = "Sending…";
      submitBtn.disabled = true;

      window.setTimeout(function () {
        submitBtn.textContent = originalLabel;
        submitBtn.disabled = false;
        status.textContent = "Message captured. This demo form isn't connected to a backend yet — wire it up to your email service or API to start receiving these for real.";
        status.classList.add("is-visible");
        form.reset();
      }, 600);
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
