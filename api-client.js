/* =========================================================
   EMERALD — api-client.js
   Shared by index.html (public site) and admin.html.
   Defines window.Emerald:
     API_BASE  where the Spring Boot API lives (default "/api")
     SEED      offline copy of the site content; mirrors the static HTML
               in index.html and is used by the admin page in demo mode
     request   small fetch wrapper that talks JSON and throws ApiError
   To point the front end at a different origin (e.g. during local
   development) set <meta name="emerald-api-base" content="http://localhost:8080/api">
   or define window.EMERALD_API_BASE before this script loads.
   The backend then needs CORS enabled for that origin.
   ========================================================= */
(function (global) {
  "use strict";

  var meta = document.querySelector('meta[name="emerald-api-base"]');
  var API_BASE = String(global.EMERALD_API_BASE || (meta && meta.getAttribute("content")) || "/api").replace(/\/+$/, "");

  function id(n) { return "00000000-0000-4000-8000-" + ("000000000000" + n).slice(-12); }

  /* Keep in sync with api.yaml v0.2.0 schemas. */
  var SEED = {
    services: [
      { id: id(1), slug: "web-dev", position: 1, title: "Web Development",
        description: "Websites and web applications built on foundations that don't shift under load. We write front-end and back-end code meant to be read by the next engineer, not just shipped by this one.",
        tags: ["Marketing sites", "Web applications", "Front-end engineering", "Back-end systems", "Performance", "Accessibility", "Scalable architecture"] },
      { id: id(2), slug: "ui-ux", position: 2, title: "UI / UX Design",
        description: "Product design grounded in how people actually behave, not how a moodboard wants them to. Research, design systems and prototypes that hold up once real content and real users arrive.",
        tags: ["Product design", "User research", "Design systems", "Prototyping", "Interaction design", "Conversion-focused UI"] },
      { id: id(3), slug: "cyber", position: 3, title: "Cyber",
        description: "Security woven into the build, not inspected in afterwards. We audit applications and infrastructure, close the gaps we find, and set up monitoring so the next gap gets caught early. No system is unbreakable — ours are built to fail safely and get noticed fast.",
        tags: ["Security audits", "Application security", "Infrastructure security", "Monitoring", "Risk reduction", "Security-first development"] },
      { id: id(4), slug: "marketing", position: 4, title: "Marketing",
        description: "Brand strategy and growth work that assumes people are busy and skeptical, because they are. We build campaigns, content and SEO foundations that earn attention instead of renting it, then prove it with numbers you can check.",
        tags: ["Brand strategy", "Digital campaigns", "SEO", "Content", "Growth", "Analytics", "Conversion optimization"] },
      { id: id(5), slug: "devops", position: 5, title: "DevOps",
        description: "Cloud infrastructure and delivery pipelines that let you ship on a Friday without dread. Infrastructure as code, automated deployment and monitoring built so scaling up is a config change, not a re-architecture.",
        tags: ["Cloud infrastructure", "CI/CD", "Deployment automation", "Monitoring", "Reliability", "Infrastructure as code"] }
    ],
    projects: [
      { id: id(11), position: 1, category: "Fintech", title: "Aurum",
        summary: "A neobank platform rebuilt for speed and trust — a full front-end rewrite plus a fraud-monitoring layer clients never see until it matters.",
        tags: ["Web Dev", "Cyber"] },
      { id: id(12), position: 2, category: "Fashion", title: "Solène",
        summary: "A luxury fashion house's e-commerce experience, designed to feel as considered on a phone as it does in their flagship store.",
        tags: ["UI/UX", "Marketing"] },
      { id: id(13), position: 3, category: "Security", title: "Cordon",
        summary: "A real-time infrastructure monitoring platform for a security vendor, built to surface the one alert that matters out of ten thousand.",
        tags: ["Cyber", "DevOps"] },
      { id: id(14), position: 4, category: "Architecture", title: "Atrium Works",
        summary: "A portfolio site for an architecture studio, where every project page loads like a slow, deliberate walk through the building.",
        tags: ["Web Dev", "Marketing"] },
      { id: id(15), position: 5, category: "AI Product", title: "Muse",
        summary: "A creative writing tool's public launch, from onboarding flow to the infrastructure that kept it upright on launch day.",
        tags: ["Web Dev", "DevOps"] },
      { id: id(16), position: 6, category: "E-commerce", title: "Ledger & Co.",
        summary: "An antique dealer's storefront, rebuilt so provenance photography and detail actually get the space they deserve online.",
        tags: ["Web Dev", "UI/UX"] }
    ],
    testimonials: [
      { id: id(21), position: 1, authorName: "Rina Kessler", authorRole: "Head of Product, Northfield Bank",
        quote: "Emerald was the first studio we hired that pushed back on our own brief — and they were right to. The product that shipped was better than the one we asked for." },
      { id: id(22), position: 2, authorName: "Daniel Ács", authorRole: "CTO, Cordon Security",
        quote: "Most vendors hand off a security report and disappear. Emerald's team stayed until every finding was actually closed, then told us what to watch for next." },
      { id: id(23), position: 3, authorName: "Marion Tan", authorRole: "Founder, Muse",
        quote: "I've worked with agencies that outsource the \"boring\" parts. Emerald treated our infrastructure with the same care they gave the homepage. It showed on launch day." },
      { id: id(24), position: 4, authorName: "Jonas Berg", authorRole: "Marketing Director, Solène",
        quote: "They asked harder questions about our customers than our own research team had. The redesign converted almost twice what the old site did." }
    ],
    faqs: [
      { id: id(31), position: 1, question: "What does Emerald actually do?",
        answer: "We design and build digital products end to end — web development, product design, security, marketing and infrastructure — as one team rather than five separate hires." },
      { id: id(32), position: 2, question: "Can you work alongside our existing team?",
        answer: "Regularly. We slot into an existing team for a specific discipline — security audits and design systems are common entry points — and hand off cleanly with documentation your team can actually use." },
      { id: id(33), position: 3, question: "Do you build from scratch, or work with what we have?",
        answer: "Both. Most projects start with an audit of what exists so we're not rebuilding things that already work. What we replace, we replace on purpose." },
      { id: id(34), position: 4, question: "Do you offer support after launch?",
        answer: "Yes. Every project includes a post-launch window by default, and most clients keep us on a retainer for ongoing monitoring, iteration and support afterward." },
      { id: id(35), position: 5, question: "What technologies do you use?",
        answer: "We pick the stack to fit the problem rather than our own preferences — commonly modern JavaScript frameworks, cloud-native infrastructure and established, well-audited security tooling. We'll always explain the choice." },
      { id: id(36), position: 6, question: "How does a project actually start?",
        answer: "A short call to understand the problem, followed by a scoped proposal within a few business days. No lengthy sales process before you know roughly what it costs." }
    ],
    stats: [
      { key: "projects",    label: "Projects shipped",      value: 47 },
      { key: "disciplines", label: "Disciplines, one team", value: 5 },
      { key: "countries",   label: "Countries served",      value: 13 },
      { key: "years",       label: "Years digging",         value: 7 }
    ]
  };

  function ApiError(status, data, network) {
    this.name = "ApiError";
    this.status = status;
    this.data = data || null;
    this.network = !!network;   // fetch failed / timed out
    this.notApi = false;        // got a non-JSON reply (e.g. a static server's HTML 404)
    this.message = (data && data.message) || (network ? "Network error" : "HTTP " + status);
  }
  ApiError.prototype = Object.create(Error.prototype);

  /* True when there is simply no backend behind API_BASE. */
  function backendAbsent(err) { return !!(err && (err.network || err.notApi)); }

  function request(method, path, opts) {
    opts = opts || {};
    var headers = { "Accept": "application/json" };
    var init = { method: method, headers: headers };
    if (opts.body !== undefined) {
      headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(opts.body);
    }
    if (opts.token) headers["Authorization"] = "Bearer " + opts.token;

    var timer = null;
    if ("AbortController" in global) {
      var ctrl = new AbortController();
      init.signal = ctrl.signal;
      timer = global.setTimeout(function () { ctrl.abort(); }, opts.timeout || 8000);
    }

    return fetch(API_BASE + path, init).then(function (res) {
      global.clearTimeout(timer);
      var isJson = (res.headers.get("content-type") || "").indexOf("json") !== -1;
      if (res.status === 204) return null;
      return (isJson ? res.json().catch(function () { return null; }) : Promise.resolve(null)).then(function (data) {
        if (res.ok && isJson) return data;
        var err = new ApiError(res.status, data, false);
        err.notApi = !isJson;
        throw err;
      });
    }, function () {
      global.clearTimeout(timer);
      throw new ApiError(0, null, true);
    });
  }

  global.Emerald = { API_BASE: API_BASE, SEED: SEED, request: request, ApiError: ApiError, backendAbsent: backendAbsent };
})(window);
