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

    var alreadySeen = false;
    try { alreadySeen = sessionStorage.getItem("emerald-loaded"); } catch (_) {}
    loaderEl.removeAttribute("hidden");
    var wait = prefersReducedMotion || alreadySeen ? 0 : 1500;

    window.setTimeout(function () {
      loaderEl.setAttribute("hidden", "");
      try { sessionStorage.setItem("emerald-loaded", "1"); } catch (_) {}
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
      var mobileViewport = window.matchMedia("(max-width: 980px)");
      var priorOverflow = "";
      var background = document.querySelectorAll("main, footer");
      function setMenu(open, restoreFocus) {
        open = open && mobileViewport.matches;
        var wasOpen = menuBtn.getAttribute("aria-expanded") === "true";
        if (open && !wasOpen) priorOverflow = document.body.style.overflow;
        menuBtn.setAttribute("aria-expanded", String(open));
        menuBtn.setAttribute("aria-label", open ? "Close menu" : "Open menu");
        mobileMenu.hidden = !open;
        mobileMenu.inert = !open;
        mobileMenu.setAttribute("aria-hidden", String(!open));
        mobileMenu.classList.toggle("is-open", open);
        background.forEach(function (el) { el.inert = open; });
        if (open) document.body.style.overflow = "hidden";
        else if (wasOpen) document.body.style.overflow = priorOverflow;
        if (open) mobileMenu.querySelector("a").focus();
        else if (restoreFocus) menuBtn.focus();
      }
      menuBtn.addEventListener("click", function () {
        setMenu(menuBtn.getAttribute("aria-expanded") !== "true", true);
      });
      mobileMenu.querySelectorAll("a").forEach(function (a) {
        a.addEventListener("click", function () { setMenu(false, true); });
      });
      document.addEventListener("keydown", function (event) {
        if (menuBtn.getAttribute("aria-expanded") !== "true") return;
        if (event.key === "Escape") {
          event.preventDefault();
          setMenu(false, true);
        } else if (event.key === "Tab") {
          var links = Array.from(mobileMenu.querySelectorAll("a"));
          var stops = [menuBtn].concat(links);
          var index = stops.indexOf(document.activeElement);
          event.preventDefault();
          stops[(index + (event.shiftKey ? -1 : 1) + stops.length) % stops.length].focus();
        }
      });
      mobileViewport.addEventListener("change", function () {
        if (!mobileViewport.matches) {
          var hadFocus = mobileMenu.contains(document.activeElement) || document.activeElement === menuBtn;
          setMenu(false, false);
          if (hadFocus) navEl.querySelector("a").focus();
        }
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
          i.querySelector(".faq-a").setAttribute("aria-hidden", "true");
        });
        if (!isOpen) {
          item.classList.add("is-open");
          btn.setAttribute("aria-expanded", "true");
          item.querySelector(".faq-a").setAttribute("aria-hidden", "false");
        }
      });
    });
  })();

  /* ---------------------------------------------------------
     CONTACT FORM
     POST /api/contact
  --------------------------------------------------------- */
  (function contactForm() {
    var form = document.getElementById("contactForm");
    if (!form) return;

    var status = document.getElementById("formStatus");
    var submitBtn = form.querySelector(".btn-primary");

    function setError(fieldId, errId, message) {
      var field = document.getElementById(fieldId);
      var err = document.getElementById(errId);
      var wrap = field.closest(".field");
      field.setAttribute("aria-invalid", message ? "true" : "false");
      if (err) field.setAttribute("aria-describedby", errId);

      if (message) {
        wrap.classList.add("has-error");
        if (err) err.textContent = message;
      } else {
        wrap.classList.remove("has-error");
        if (err) err.textContent = "";
      }

      return !message;
    }

    function clearErrors() {
      setError("f-name", "err-name", "");
      setError("f-email", "err-email", "");
      setError("f-company", "err-company", "");
      setError("f-type", "err-type", "");
      setError("f-message", "err-message", "");
    }

    function showStatus(message, isError) {
      status.textContent = message;
      status.classList.toggle("is-error", !!isError);
      status.classList.add("is-visible");
    }

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      if (submitBtn.disabled) return;

      status.classList.remove("is-visible", "is-error");
      clearErrors();

      var payload = {
        name: document.getElementById("f-name").value.trim(),
        email: document.getElementById("f-email").value.trim(),
        company: document.getElementById("f-company").value.trim(),
        projectType: document.getElementById("f-type").value.trim(),
        message: document.getElementById("f-message").value.trim()
      };

      var emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

      var validName = setError(
        "f-name",
        "err-name",
        payload.name ? "" : "Tell us your name."
      );

      var validEmail = setError(
        "f-email",
        "err-email",
        emailPattern.test(payload.email)
          ? ""
          : "Enter a valid email address."
      );

      var validCompany = setError(
        "f-company",
        "err-company",
        payload.company.length <= 255
          ? ""
          : "Company name must be 255 characters or fewer."
      );

      var validProjectType = setError(
        "f-type",
        "err-type",
        payload.projectType.length <= 255
          ? ""
          : "Project type must be 255 characters or fewer."
      );

      var validMessage = setError(
        "f-message",
        "err-message",
        payload.message.length >= 10 && payload.message.length <= 5000
          ? ""
          : payload.message.length < 10
            ? "A few more details would help (10+ characters)."
            : "Message must be 5000 characters or fewer."
      );

      if (!(validName && validEmail && validCompany && validProjectType && validMessage)) { form.querySelector('[aria-invalid="true"]')?.focus(); return; }

      if (!payload.company) delete payload.company;
      if (!payload.projectType) delete payload.projectType;

      var originalLabel = submitBtn.textContent;
      submitBtn.textContent = "Sending…";
      submitBtn.disabled = true;

      fetch((window.EMERALD_API_BASE || document.querySelector('meta[name="emerald-api-base"]')?.getAttribute("content") || "/api") + "/contact", {
        method: "POST",
        signal: AbortSignal.timeout(20000),
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json"
        },
        body: JSON.stringify(payload)
      })
        .then(function (response) {
          return response.text().then(function (text) {
            var data = null;

            if (text) {
              try {
                data = JSON.parse(text);
              } catch (ignore) {}
            }

            if (!response.ok) {
              var error = new Error("HTTP " + response.status);
              error.status = response.status;
              error.data = data;
              throw error;
            }

            return data;
          });
        })
        .then(function () {
          showStatus(
            "Thanks — your message is in. We'll review your enquiry and get back to you.",
            false
          );
          form.reset();
        })
        .catch(function (error) {
          if (
            error.status === 400 &&
            error.data &&
            error.data.errors
          ) {
            Object.keys(error.data.errors).forEach(function (field) {
              var fieldIds = {
                name: "f-name",
                email: "f-email",
                company: "f-company",
                projectType: "f-type",
                message: "f-message"
              };

              var errorIds = {
                name: "err-name",
                email: "err-email",
                company: "err-company",
                projectType: "err-type",
                message: "err-message"
              };

              if (fieldIds[field] && errorIds[field]) {
                setError(
                  fieldIds[field],
                  errorIds[field],
                  error.data.errors[field]
                );
              }
            });

            showStatus(
              "Please fix the highlighted fields and try again.",
              true
            );
          } else if (error.status === 429) {
 showStatus("Too many attempts. Wait one minute before trying again.", true);
 } else if (error.name === "TimeoutError") {
 showStatus("The server took too long. Your message may have arrived; check with us before sending again.", true);
 } else if (error.status === 415) {
            showStatus(
              "We couldn't send the message in the expected format. Please try again.",
              true
            );
          } else if (error.status >= 500) {
            showStatus(
              "Something went wrong on our side. Please try again.",
              true
            );
          } else {
            showStatus(
              "We couldn't reach the server. Please try again shortly, and keep a copy of your message.",
              true
            );
          }
        })
        .finally(function () {
          submitBtn.textContent = originalLabel;
          submitBtn.disabled = false;
        });
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

document.querySelectorAll('button[data-js]').forEach(b=>b.disabled=false);
