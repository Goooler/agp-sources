/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
const AGGREGATED_SUITE_NAME = 'Aggregated';
const TEST_STATUS = Object.freeze({
  PASSED: 'passed',
  FAILED: 'failed',
  SKIPPED: 'skipped',
  PASS: 'pass',
  FAIL: 'fail',
});
const ALL_TEST_STATUSES = Object.freeze([
  TEST_STATUS.PASSED,
  TEST_STATUS.FAILED,
  TEST_STATUS.SKIPPED,
]);

/**
 * UI Utilities
 * Collection of helper functions for DOM manipulation and common UI patterns.
 */
const UIUtils = {
  /**
   * Escapes special characters for use in HTML content and attributes.
   */
  escapeHTML(str) {
    if (str === null || str === undefined) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  },

  /**
   * Validates and normalizes URLs for safe use in anchor href attributes.
   * Relative artifact links reject schemes, leading slashes, and parent traversal.
   */
  sanitizeUrl(url, allowExternal = false) {
    if (!url || typeof url !== "string") return "";
    const trimmed = url.trim();
    if (!trimmed || /[\u0000-\u001F\u007F]/.test(trimmed)) return "";
    const lower = trimmed.toLowerCase();
    if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("data:")) {
      return "";
    }
    if (allowExternal) {
      if (lower.startsWith("https://") || lower.startsWith("http://")) {
        return trimmed;
      }
      return "";
    }
    if (trimmed.includes(":") || trimmed.includes("\\") || trimmed.startsWith("/")) {
      return "";
    }
    const rawParts = trimmed.split("/");
    const encodedParts = [];
    for (let i = 0; i < rawParts.length; i++) {
      const rawPart = rawParts[i];
      if (!rawPart || rawPart === "." || rawPart === "..") {
        return "";
      }
      let decoded;
      try {
        decoded = decodeURIComponent(rawPart);
      } catch (_) {
        return "";
      }
      if (
        !decoded ||
        decoded === "." ||
        decoded === ".." ||
        decoded.includes("/") ||
        decoded.includes("\\") ||
        decoded.includes(":") ||
        /[\u0000-\u001F\u007F]/.test(decoded)
      ) {
        return "";
      }
      encodedParts.push(encodeURIComponent(decoded));
    }
    return encodedParts.join("/");
  },

  /**
   * Normalizes test status strings to 'pass', 'fail', or 'skipped'.
   */
  normalizeStatus(status) {
    const s = String(status || "").trim().toLowerCase();
    if (s === "pass" || s === "passed") return TEST_STATUS.PASS;
    if (s === "fail" || s === "failed" || s === "error") return TEST_STATUS.FAIL;
    if (s === "skip" || s === "skipped") return TEST_STATUS.SKIPPED;
    return s;
  },

  /**
   * Formats duration in milliseconds for compact human-readable display.
   */
  formatDuration(ms) {
    if (ms === null || ms === undefined || ms === "" || Number.isNaN(Number(ms))) {
      return "—";
    }
    const n = Math.max(0, Number(ms));
    if (n < 1000) {
      return `${Math.round(n)} ms`;
    }
    if (n < 60000) {
      return `${(n / 1000).toFixed(1)} s`;
    }
    const totalSec = Math.round(n / 1000);
    const mins = Math.floor(totalSec / 60);
    const secs = totalSec % 60;
    if (mins >= 60) {
      const hours = Math.floor(mins / 60);
      const remMins = mins % 60;
      return `${hours}h ${remMins}m`;
    }
    return `${mins}m ${secs}s`;
  },

  /**
   * Renders a status pill HTML snippet matching the clean outlined UX status tag.
   */
  statusTag(status) {
    const norm = this.normalizeStatus(status);
    if (norm === TEST_STATUS.PASS) {
      return '<span class="rich-status-tag rich-status-passed">PASSED</span>';
    }
    if (norm === TEST_STATUS.FAIL) {
      return '<span class="rich-status-tag rich-status-failed">FAILED</span>';
    }
    if (norm === TEST_STATUS.SKIPPED) {
      return '<span class="rich-status-tag rich-status-neutral">SKIPPED</span>';
    }
    return `<span class="rich-status-tag rich-status-neutral">${this.escapeHTML(String(status || "UNKNOWN").toUpperCase())}</span>`;
  },

  /**
   * Formats device model(s) with smaller muted `API <level>` text beside each device name.
   */
  formatDevicesRichHtml(res, fallbackText = "") {
    const actors = res && Array.isArray(res.actors) ? res.actors.filter(Boolean) : [];
    const formatPartFromString = (part) => {
      const trimmed = String(part || "").trim();
      if (!trimmed) return "";
      const m = trimmed.match(/^(.*?)\s*(?:\(\s*API\s+([^)]+)\s*\)|·\s*API\s+(\S+)|\bAPI\s+(\S+))$/i);
      if (m) {
        const model = m[1].trim();
        const api = (m[2] || m[3] || m[4] || "").trim();
        if (model && api) {
          return `${this.escapeHTML(model)} <span class="cell-sub">API ${this.escapeHTML(api)}</span>`;
        }
      }
      return this.escapeHTML(trimmed);
    };

    if (actors.length > 0) {
      const rendered = actors.map(a => {
        const model = String(a.name || a.id || "Device").trim();
        const hasApi = a.api !== null && a.api !== undefined && String(a.api).trim() !== "";
        return hasApi
          ? `${this.escapeHTML(model)} <span class="cell-sub">API ${this.escapeHTML(String(a.api).trim())}</span>`
          : this.escapeHTML(model);
      });
      return rendered.join(' <span class="cell-arrow" aria-hidden="true">→</span> ');
    }

    if (!fallbackText) return "—";
    const parts = String(fallbackText).split(/\s*(?:->|→)\s*/).filter(Boolean);
    return parts.map(formatPartFromString).join(' <span class="cell-arrow" aria-hidden="true">→</span> ');
  },

  /**
   * Formats the subline beneath the Device / Source -> Target fact on the detail summary card,
   * surfacing the system image and emulator/device serial directly below the device name.
   */
  formatDevicesFactSub(res, plugin = null) {
    const actors = res && Array.isArray(res.actors) ? res.actors.filter(Boolean) : [];
    if (actors.length === 0) return "";
    if (actors.length === 1) {
      const a = actors[0];
      const pieces = [];
      if (a.image && String(a.image).trim()) {
        const img = String(a.image).trim();
        pieces.push(/\bimage$/i.test(img) ? img : `${img} image`);
      }
      if (a.serial && String(a.serial).trim()) {
        pieces.push(String(a.serial).trim());
      }
      return pieces.join(" · ");
    }
    const serials = actors
      .map(a => (a && a.serial ? String(a.serial).trim() : ""))
      .filter(Boolean);
    const serialsText = serials.join(" → ");
    return (plugin || SuiteRegistry.resolve('default')).getCrossDeviceFactSub(serialsText);
  },

  formatSuiteName(name) {
    if (!name) return "";
    const str = String(name);
    return str.charAt(0).toLowerCase() + str.slice(1);
  },

  /**
   * Resolves the failed step label target from a VariantTestResult, preferring the step's actual
   * name (e.g. "Setup", "Verify") and falling back to "step N" only when no step name is present.
   */
  getFailedStepTarget(res) {
    if (!res) return "";
    const steps = Array.isArray(res.steps) ? res.steps : [];
    const failedNum = res.callout && res.callout.failedStep !== null && res.callout.failedStep !== undefined
      ? res.callout.failedStep
      : null;
    const matchedStep = (failedNum !== null ? steps.find(s => s && s.n === failedNum) : null)
      || steps.find(s => s && this.normalizeStatus(s.status) === TEST_STATUS.FAIL)
      || null;
    const stepName = matchedStep && matchedStep.name ? String(matchedStep.name).trim() : "";
    if (stepName) return stepName;
    if (failedNum !== null && String(failedNum).trim() !== "") return `step ${String(failedNum).trim()}`;
    return "";
  },

  /**
   * Builds a multi-select dropdown with "Select All" / "Clear" actions
   * and a scrollable list of options.
   */
  buildActionDropdown(container, options, selectedValueOrArray, onSelectionChange, searchable = true, multiSelect = true) {
    if (!container) return;

    container.innerHTML = "";
    container.style.padding = "0";
    container.style.overflow = "hidden";
    container.setAttribute("role", multiSelect ? "group" : "listbox");

    let currentState = multiSelect
      ? (Array.isArray(selectedValueOrArray) ? [...selectedValueOrArray] : [])
      : selectedValueOrArray;

    let searchInput = null;
    if (searchable) {
      const searchContainer = document.createElement("div");
      searchContainer.className = "dropdown-search-zone";

      searchInput = document.createElement("input");
      searchInput.type = "text";
      searchInput.className = "popover-search";
      searchInput.placeholder = "Search...";
      searchInput.setAttribute("autocomplete", "off");

      searchContainer.appendChild(searchInput);
      container.appendChild(searchContainer);
    }

    const listZone = document.createElement("div");
    listZone.className = "dropdown-scroll-zone";

    if (multiSelect) {
      const actionZone = document.createElement("div");
      actionZone.className = "dropdown-action-zone";

      const selectAllBtn = document.createElement("button");
      selectAllBtn.className = "dropdown-action-btn";
      selectAllBtn.textContent = "Select all";

      const clearBtn = document.createElement("button");
      clearBtn.className = "dropdown-action-btn";
      clearBtn.textContent = "Clear";

      actionZone.appendChild(selectAllBtn);
      actionZone.appendChild(clearBtn);
      container.appendChild(actionZone);

      selectAllBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        e.preventDefault();
        currentState = options.filter(o => o.value !== "all").map(o => o.value);
        onSelectionChange([...currentState]);
        renderList(searchInput ? searchInput.value : "");
      });

      clearBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        e.preventDefault();
        currentState = [];
        onSelectionChange([...currentState]);
        renderList(searchInput ? searchInput.value : "");
      });
    }

    container.appendChild(listZone);

    const renderList = (filter = "") => {
      listZone.innerHTML = "";

      const filteredOptions = options.filter(opt =>
        opt.name.toLowerCase().includes(filter.toLowerCase())
      );

      if (filteredOptions.length === 0) {
        listZone.innerHTML = `<div class="p-4 text-xs text-gray-500 text-center">No options found</div>`;
        return;
      }

      filteredOptions.forEach(opt => {
        const isChecked = multiSelect ? currentState.includes(opt.value) : currentState === opt.value;

        const item = document.createElement(multiSelect ? "label" : "button");
        item.className = "popover-item";

        if (multiSelect) {
          const checkbox = document.createElement("input");
          checkbox.type = "checkbox";
          checkbox.className = "popover-checkbox";
          checkbox.checked = isChecked;
          item.appendChild(checkbox);

          const handleChange = () => {
            if (checkbox.checked) {
              if (!currentState.includes(opt.value)) currentState.push(opt.value);
            } else {
              currentState = currentState.filter(v => v !== opt.value);
            }
            onSelectionChange([...currentState]);
          };

          item.addEventListener("change", (e) => {
            e.stopPropagation();
            handleChange();
          });

          item.addEventListener("keydown", (e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              e.stopPropagation();
              checkbox.checked = !checkbox.checked;
              handleChange();
            }
          });
        } else {
          item.setAttribute("role", "option");
          item.setAttribute("tabindex", "0");
          if (isChecked) {
            item.classList.add('active-popover-item');
            item.setAttribute("aria-selected", "true");
          } else {
            item.setAttribute("aria-selected", "false");
          }

          const triggerSelect = (e) => {
            e.preventDefault();
            e.stopPropagation();
            currentState = opt.value;
            onSelectionChange(currentState);
            renderList(searchInput ? searchInput.value : "");

            // Close dropdown
            const dropdownMenu = container.closest('.dropdown-menu');
            if (dropdownMenu) {
              dropdownMenu.classList.add('hidden');
              const btn = TestReportApp.getDropdownConfigs().find(c => c.dropdown === dropdownMenu)?.btn;
              if (btn) {
                btn.setAttribute('aria-expanded', 'false');
                btn.focus();
              }
            }
          };

          item.addEventListener("click", triggerSelect);
          item.addEventListener("keydown", (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              triggerSelect(e);
            }
          });
        }

        const text = document.createElement("span");
        text.textContent = opt.name;
        item.appendChild(text);

        listZone.appendChild(item);
      });
    };

    if (searchInput) {
      searchInput.addEventListener("input", (e) => {
        renderList(e.target.value);
      });
    }

    renderList();
  }
};

/**
 * Constants for navigation actions
 */
const BREADCRUMB_ACTIONS = {
  GO_TO_MODULES: 'go-to-modules',
  GO_TO_PACKAGES: 'go-to-packages',
  GO_TO_CLASSES: 'go-to-classes',
  GO_TO_TEST_CASES: 'go-to-test-cases'
};

/**
 * Header Info Tooltips — Content
 * ------------------------------------------------------------------
 * All explanatory copy for header/label tooltips lives here so it can be
 * edited in one place. Each entry has a short `title` and a `body`.
 * Elements opt in with `data-info-key="<key>"`. Entries may be functions
 * when the copy depends on runtime context, in which case the value of
 * `data-info-arg` is passed in.
 */
const HEADER_INFO = {
  // --- Top header stats ---
  'stat.modules': {
    title: 'Modules',
    body: 'Number of Gradle modules (e.g. :app, :core:data) whose test results are included in this report.',
  },
  'stat.packages': {
    title: 'Packages',
    body: 'Total packages containing test classes across all modules.',
  },
  'stat.classes': {
    title: 'Classes',
    body: 'Total test classes across all modules that reported at least one test result.',
  },
  'stat.duration': {
    title: 'Duration',
    body: 'Total execution time of tests matching the selected suite and variants.',
  },
  'stat.passRate': {
    title: 'Pass Rate',
    body: 'passed / (passed + failed). Skipped tests are excluded. Green is 100%, amber is 80% to 99.9%, and red is below 80%.',
  },
  'stat.tests': {
    title: 'Tests',
    body: 'Total number of test cases executed in the selected test suite and variant.',
  },
  'stat.failed': {
    title: 'Failed',
    body: 'Number of test cases that failed an assertion or threw an unexpected exception.',
  },

  // --- Header controls ---
  'header.testSuite': {
    title: 'Test Suite',
    body: 'Choose whose results you are looking at. “all” combines every test suite; pick a single suite to see only the tests that suite ran.',
  },
  'header.groupBy': {
    title: 'Group by',
    body: 'Changes the granularity of the rows below — list results by Modules, by Packages, by Classes, or as a flat list of individual Test Cases.',
  },

  // --- Main table: name column (contextual) ---
  'col.project': {
    title: 'Project hierarchy',
    body: 'Expandable tree of Modules → Packages → Classes → Test Cases, plus Targets when tests ran on more than one target. Counts on a parent row are the totals of everything underneath it.',
  },
  'col.modules': {
    title: 'Module',
    body: 'Gradle module name. Counts are the totals of all test classes in the module. Click a module to drill into its packages.',
  },
  'col.packages': {
    title: 'Package',
    body: 'Kotlin/Java package. Counts are the totals of all test classes in the package. Click a package to drill into its classes.',
  },
  'col.classes': {
    title: 'Class',
    body: 'Test class name. Counts are the totals of its test cases. Click a class to drill into its test cases.',
  },
  'col.testCases': {
    title: 'Test Case',
    body: 'An individual test method. Failed test cases are links: click one to open its stack trace.',
  },
  'col.richTestCases': {
    title: 'Test',
    body: 'An individual test method. Click a test row to inspect its details, execution steps, logs, or stack trace.',
  },
  'col.path': {
    title: 'Path',
    body: 'Where this item lives: its Gradle module, package and, for test cases, class.',
  },
  'col.module': {
    title: 'Module',
    body: 'Gradle module that owns this package.',
  },
  'col.target': {
    title: 'Target',
    body: 'The test suite target the test case ran on — a named run context configured for the test suite, such as locally connected devices or a Gradle Managed Device.',
  },

  // --- Main table: per-variant & single-suite metric columns ---
  'col.pass': {
    title: 'Pass',
    body: 'Number of tests that completed successfully for this variant under the selected test suite. Click to sort.',
  },
  'col.fail': {
    title: 'Fail',
    body: 'Number of tests where an assertion failed or an unexpected exception was thrown. Click to sort.',
  },
  'col.skip': {
    title: 'Skip',
    body: 'Number of tests reported as skipped, e.g. ignored or disabled with annotations like @Ignore. Skipped tests are excluded from the pass rate. Click to sort.',
  },
  'col.passRate': {
    title: 'Pass Rate',
    body: 'passed / (passed + failed), with the raw counts shown underneath. Skipped tests are excluded. Green is 95 % or above, yellow 80 % to 94.9 %, red below 80 %. Click to sort.',
  },
  'col.duration': {
    title: 'Duration',
    body: 'Execution time recorded for the test or aggregated group. Click to sort.',
  },
  'col.status': {
    title: 'Status',
    body: 'Outcome of the test run (Failed, Skipped, or Passed). Click to sort.',
  },
  'col.summary': (key) => {
    if (!key) return null;
    const col = TestReportApp.getActiveSummaryColumns().find(c => c && c.key === key);
    if (!col || !col.tooltip) return null;
    const body = col.sortable === false ? col.tooltip : `${col.tooltip.replace(/\.$/, '')}. Click to sort.`;
    return { title: col.label || col.key, body };
  },
};

const TOOLTIP_SELECTOR = '[data-info-key], [data-tooltip], [title], [data-stored-title]';

const Tooltip = {
  element: null,
  activeTarget: null,
  hideTimeout: null,

  init() {
    this.element = document.getElementById('a11y-tooltip');
    if (!this.element) return;

    document.body.addEventListener('mouseover', (e) => {
      const target = e.target.closest(TOOLTIP_SELECTOR);
      if (target && !target.contains(e.relatedTarget)) this.show(target);
    });

    document.body.addEventListener('mouseout', (e) => {
      const target = e.target.closest(TOOLTIP_SELECTOR);
      if (target && !target.contains(e.relatedTarget)) {
        this.startHide();
      }
    });

    document.body.addEventListener('focusin', (e) => {
      const target = this.resolveFocusTarget(e.target);
      if (target && !target.contains(e.relatedTarget)) this.show(target);
    });

    document.body.addEventListener('focusout', (e) => {
      const target = this.resolveFocusTarget(e.target);
      if (target && !target.contains(e.relatedTarget)) {
        this.hide();
      }
    });

    // Dismiss tooltip on scroll (prevent detaching from element)
    window.addEventListener('scroll', () => this.hide(), true);

    // Clicking a sortable header re-renders the table head, detaching the
    // info tooltip's target; dismiss it up front.
    document.addEventListener('mousedown', () => {
      if (this.element.classList.contains('info')) this.hide();
    }, true);

    // Keep tooltip open when hovering over it
    this.element.addEventListener('mouseenter', () => this.clearHide());
    this.element.addEventListener('mouseleave', () => this.startHide());
  },

  /**
   * Focusable containers (e.g. sortable <th>, the Test Suite button) wrap a
   * non-focusable label carrying `data-info-key`. When such a container gains
   * focus, surface the info tooltip of its label.
   */
  resolveFocusTarget(el) {
    const direct = el.closest(TOOLTIP_SELECTOR);
    if (direct) return direct;
    if (!el.matches || !el.matches('button, th')) return null;
    // Skip labels inside hidden states (e.g. the Test Suite button swaps
    // between an "all" and a "selected" layout).
    return [...el.querySelectorAll('[data-info-key]')].find(n => n.getClientRects().length > 0) || null;
  },

  /** Returns {title?, body} for an info-key target, or null. */
  resolveInfo(target) {
    const key = target.dataset.infoKey;
    if (!key) return null;
    let entry = HEADER_INFO[key];
    if (typeof entry === 'function') entry = entry(target.dataset.infoArg || '');
    return entry || null;
  },

  show(target) {
    this.clearHide();

    if (this.activeTarget && this.activeTarget !== target) {
      this.hide();
    }

    const info = this.resolveInfo(target);
    if (info) {
      this.showInfo(target, info);
      return;
    }

    let text = target.getAttribute('data-tooltip') || target.getAttribute('title') || target.dataset.storedTitle;
    if (!text) {
      this.hide();
      return;
    }

    // Prevent native tooltip by transiently removing title
    if (target.hasAttribute('title')) {
      target.dataset.storedTitle = target.getAttribute('title');
      target.removeAttribute('title');
    }

    // Establish programmatic connection for ALL targets
    if (!target.hasAttribute('aria-describedby')) {
      target.setAttribute('aria-describedby', 'a11y-tooltip');
      target.dataset.addedAriaDescribedby = 'true';
    }

    // Ensure accessible name is preserved if no other source exists
    if (!target.hasAttribute('aria-label') && !target.hasAttribute('aria-labelledby')) {
      target.setAttribute('aria-label', text);
      target.dataset.addedAriaLabel = 'true';
    }

    this.activeTarget = target;
    this.element.classList.remove('info');
    this.element.textContent = text;
    this.position(target);
  },

  /**
   * Renders a rich (title + body) header info tooltip. Unlike plain tooltips,
   * the target keeps its visible label as its accessible name; the
   * explanation is only exposed as a description via aria-describedby.
   */
  showInfo(target, info) {
    if (!target.hasAttribute('aria-describedby')) {
      target.setAttribute('aria-describedby', 'a11y-tooltip');
      target.dataset.addedAriaDescribedby = 'true';
    }

    this.activeTarget = target;
    this.element.classList.add('info');
    this.element.textContent = '';
    if (info.title) {
      const titleEl = document.createElement('span');
      titleEl.className = 'info-tooltip-title';
      titleEl.textContent = info.title;
      this.element.appendChild(titleEl);
    }
    const bodyEl = document.createElement('span');
    bodyEl.className = 'info-tooltip-body';
    bodyEl.textContent = info.body;
    this.element.appendChild(bodyEl);

    this.position(target);
  },

  position(target) {
    this.element.classList.remove('top', 'bottom');
    this.element.classList.add('visible');

    const rect = target.getBoundingClientRect();
    const tooltipRect = this.element.getBoundingClientRect();

    let top = rect.top - tooltipRect.height - 10;
    let left = rect.left + (rect.width / 2) - (tooltipRect.width / 2);

    // Flip if no space on top
    if (top < 10) {
      top = rect.bottom + 10;
      this.element.classList.add('bottom');
    } else {
      this.element.classList.add('top');
    }

    // Keep within viewport horizontal bounds
    left = Math.max(10, Math.min(left, window.innerWidth - tooltipRect.width - 10));

    // Position arrow to point at target center
    const arrowX = (rect.left + rect.width / 2) - left;
    this.element.style.setProperty('--arrow-x', `${arrowX}px`);

    this.element.style.top = `${top}px`;
    this.element.style.left = `${left}px`;
    this.element.setAttribute('aria-hidden', 'false');
  },

  startHide() {
    this.hideTimeout = setTimeout(() => this.hide(), 100);
  },

  clearHide() {
    if (this.hideTimeout) clearTimeout(this.hideTimeout);
  },

  hide() {
    if (!this.element || !this.activeTarget) return;

    if (this.activeTarget) {
      if (this.activeTarget.dataset.storedTitle !== undefined) {
        this.activeTarget.setAttribute('title', this.activeTarget.dataset.storedTitle);
        delete this.activeTarget.dataset.storedTitle;
      }
      if (this.activeTarget.dataset.addedAriaDescribedby) {
        this.activeTarget.removeAttribute('aria-describedby');
        delete this.activeTarget.dataset.addedAriaDescribedby;
      }
      if (this.activeTarget.dataset.addedAriaLabel) {
        // Keep aria-label intact to ensure permanent accessible name
        delete this.activeTarget.dataset.addedAriaLabel;
      }
    }

    this.element.classList.remove('visible', 'top', 'bottom');
    this.element.setAttribute('aria-hidden', 'true');
    this.activeTarget = null;
  }
};

/**
 * Main application object.
 * DEPENDENCY: Requires 'TEST_DATA_SOURCE' to be defined in data.js
 */
const TestReportApp = {
  activeTrigger: null,
  _detailLoadPromises: new Map(),
  _dynamicSuiteDropdowns: [],

  state: {
    viewMode: 'flat',
    density: 'comfy',
    currentFlatView: 'testCases',
    selectedModule: null,
    selectedPackage: null,
    selectedClass: null,
    currentView: 'report',
    detailViewMode: null,
    currentTestCase: null,
    currentStackTraceContext: {},
    activeDetailRunIndex: 0,
    listHistoryDepth: null,
    filters: {
      variants: [],
      search: '',
      status: [...ALL_TEST_STATUSES],
      testSuite: 'all',
      modules: [],
      packages: [],
      classes: [],
      testCases: [],
      targets: [],
      suiteAttributes: {},
    },
    sort: { by: 'fail', order: 'desc' },
    isResizing: false,
    columnWidths: {},
    variants: [],
    targets: [],
    suiteDescriptors: {},
    suiteEnvironments: [],
    processedData: null
  },
  elements: {},

  init: function () {
    this.baseTitle = document.title;
    this.cacheDOMElements();
    Tooltip.init();

    // Directly access the global variable from data.js
    if (typeof TEST_DATA_SOURCE !== 'undefined') {
      this.setupTestResults(TEST_DATA_SOURCE);
      this.populateFilters();
      this.bindEvents();
      this.initResizableColumns();
      this.closeDropdownsOnClickOutside();
      Navigation.init();
      this.render();
    } else {
      console.error("TEST_DATA_SOURCE is not defined. Make sure data.js is loaded before script.js");
      this.elements.resultsData.innerHTML = `<tr><td colspan="100%" class="text-center text-red-600 font-bold" style="padding: 2rem;" tabindex="-1" id="data-load-error">Error: Data file not loaded.</td></tr>`;
      document.getElementById('data-load-error').focus();
    }
  },

  initResizableColumns() {
    const headerRow = this.elements.tableHeaders;

    const setColumnWidth = (id, width) => {
      document.documentElement.style.setProperty(`--col-width-${id.replace(/\./g, '-')}`, `${width}px`);
      const resizer = this.elements.tableHeaders.querySelector(`.resizer[data-resizer-id="${id}"]`);
      if (resizer) resizer.setAttribute('aria-valuenow', Math.round(width));
    };

    headerRow.addEventListener('pointerdown', (e) => {
      if (e.target.classList.contains('resizer')) {
        if (this.state.isResizing) return;

        const resizer = e.target;
        const resizerId = resizer.dataset.resizerId;
        const columnTh = resizer.closest('th');
        const startX = e.pageX;
        const startWidth = columnTh.getBoundingClientRect().width;
        let animationFrameId = null;

        resizer.setPointerCapture(e.pointerId);
        resizer.classList.add('resizing');
        this.state.isResizing = true;

        const onPointerMove = (moveEvt) => {
          const diffX = moveEvt.pageX - startX;
          const newWidth = Math.max(50, startWidth + diffX);
          this.state.columnWidths[resizerId] = newWidth;

          if (animationFrameId) cancelAnimationFrame(animationFrameId);
          animationFrameId = requestAnimationFrame(() => {
            setColumnWidth(resizerId, newWidth);
          });
        };

        const onPointerUp = (upEvt) => {
          resizer.releasePointerCapture(upEvt.pointerId);
          resizer.classList.remove('resizing');
          resizer.removeEventListener('pointermove', onPointerMove);
          resizer.removeEventListener('pointerup', onPointerUp);
          resizer.removeEventListener('pointercancel', onPointerUp);
          setTimeout(() => { this.state.isResizing = false; }, 0);
        };

        resizer.addEventListener('pointermove', onPointerMove);
        resizer.addEventListener('pointerup', onPointerUp);
        resizer.addEventListener('pointercancel', onPointerUp);

        e.preventDefault();
        e.stopPropagation();
      }
    });

    headerRow.addEventListener('dblclick', (e) => {
      if (e.target.classList.contains('resizer')) {
        const id = e.target.dataset.resizerId;
        delete this.state.columnWidths[id];
        document.documentElement.style.removeProperty(`--col-width-${id.replace(/\./g, '-')}`);
        // Update to default width instead of removing
        const newWidth = e.target.closest('th').getBoundingClientRect().width;
        e.target.setAttribute('aria-valuenow', Math.round(newWidth));
        e.preventDefault();
        e.stopPropagation();
      }
    });

    headerRow.addEventListener('keydown', (e) => {
      if (e.target.classList.contains('resizer')) {
        const id = e.target.dataset.resizerId;
        const columnTh = e.target.closest('th');
        const currentWidth = columnTh.getBoundingClientRect().width;
        let newWidth = this.state.columnWidths[id] || currentWidth;

        if (e.key === 'ArrowLeft') {
          newWidth = Math.max(50, newWidth - 10);
          this.state.columnWidths[id] = newWidth;
          setColumnWidth(id, newWidth);
          e.preventDefault();
        } else if (e.key === 'ArrowRight') {
          newWidth = Math.min(1000, newWidth + 10);
          this.state.columnWidths[id] = newWidth;
          setColumnWidth(id, newWidth);
          e.preventDefault();
        } else if (e.key === 'Enter' || e.key === ' ') {
          e.stopImmediatePropagation();
          delete this.state.columnWidths[id];
          document.documentElement.style.removeProperty(`--col-width-${id.replace(/\./g, '-')}`);
          // Update to default width instead of removing
          const resetWidth = e.target.closest('th').getBoundingClientRect().width;
          e.target.setAttribute('aria-valuenow', Math.round(resetWidth));
          e.preventDefault();
        }
      }
    });

    // Apply initial widths if any
    for (const [id, width] of Object.entries(this.state.columnWidths)) {
      setColumnWidth(id, width);
    }
  },

  cacheDOMElements() {
    this.elements = {
      appTitle: document.getElementById('app-title'),
      reportDate: document.getElementById('report-date'),
      headerStatsMulti: document.getElementById('header-stats-multi'),
      headerStatsSingle: document.getElementById('header-stats-single'),
      totalModules: document.getElementById('total-modules'),
      totalPackages: document.getElementById('total-packages'),
      totalClasses: document.getElementById('total-classes'),
      suiteEnvBar: document.getElementById('suite-env-bar'),
      statusSegments: document.getElementById('status-segments'),
      suiteAttrFilters: document.getElementById('suite-attr-filters'),
      searchInput: document.getElementById('search-input'),

      variantFilterBtn: document.getElementById('variant-filter-btn'),
      variantFilterDropdown: document.getElementById('variant-filter-dropdown'),
      variantFilterList: document.getElementById('variant-filter-list'),

      testSuiteFilterBtn: document.getElementById('testsuite-filter-btn'),
      testSuiteFilterText: document.getElementById('testsuite-filter-text'),
      testSuiteFilterDropdown: document.getElementById('testsuite-filter-dropdown'),
      testSuiteFilterList: document.getElementById('testsuite-filter-list'),
      tsAllState: document.getElementById('ts-all-state'),
      tsSelectedState: document.getElementById('ts-selected-state'),

      statusFilterBtn: document.getElementById('status-filter-btn'),
      statusFilterText: document.getElementById('status-filter-text'),
      statusFilterDropdown: document.getElementById('status-dropdown'),
      statusFilterList: document.getElementById('status-filter-list'),

      tableHeaders: document.getElementById('table-headers'),
      resultsData: document.getElementById('results-data'),
      breadcrumbs: document.getElementById('breadcrumbs'),
      groupByBtn: document.getElementById('group-by-btn'),
      groupByText: document.getElementById('group-by-text'),
      groupByDropdown: document.getElementById('group-by-dropdown'),


      // New UI Elements
      viewSegments: document.getElementById('view-segments'),
      densitySegments: document.getElementById('density-segments'),
      mainTable: document.querySelector('table'),
      statusChipContainer: document.getElementById('status-chip-container'),
      modChipContainer: document.getElementById('mod-chip-container'),
      pkgChipContainer: document.getElementById('pkg-chip-container'),
      clsChipContainer: document.getElementById('cls-chip-container'),
      tcChipContainer: document.getElementById('tc-chip-container'),
      tgtChipContainer: document.getElementById('tgt-chip-container'),

      moduleFilterBtn: document.getElementById('mod-filter-btn'),
      moduleFilterText: document.getElementById('mod-filter-text'),
      moduleFilterDropdown: document.getElementById('mod-dropdown'),
      moduleFilterList: document.getElementById('mod-filter-list'),

      packageFilterBtn: document.getElementById('pkg-filter-btn'),
      packageFilterText: document.getElementById('pkg-filter-text'),
      packageFilterDropdown: document.getElementById('pkg-dropdown'),
      packageFilterList: document.getElementById('pkg-filter-list'),

      classFilterBtn: document.getElementById('cls-filter-btn'),
      classFilterText: document.getElementById('cls-filter-text'),
      classFilterDropdown: document.getElementById('cls-dropdown'),
      classFilterList: document.getElementById('cls-filter-list'),

      tcFilterBtn: document.getElementById('tc-filter-btn'),
      tcFilterText: document.getElementById('tc-filter-text'),
      tcFilterDropdown: document.getElementById('tc-dropdown'),
      tcFilterList: document.getElementById('tc-filter-list'),

      targetFilterBtn: document.getElementById('tgt-filter-btn'),
      targetFilterText: document.getElementById('tgt-filter-text'),
      targetFilterDropdown: document.getElementById('tgt-dropdown'),
      targetFilterList: document.getElementById('tgt-filter-list'),

      addFilterBtn: document.getElementById('add-filter-btn'),
      addFilterDropdown: document.getElementById('add-filter-dropdown'),
      addFilterList: document.getElementById('add-filter-list'),
      searchContainer: document.querySelector('.search-container'),
      searchRevealBtn: document.getElementById('search-reveal-btn'),
      searchWrapper: document.getElementById('search-wrapper'),
      searchClearBtn: document.getElementById('search-clear-btn'),

      reportViewControls: document.getElementById('report-view-controls'),
      filterControlsGroup: document.querySelector('.flex-start-gap-4'),
      reportView: document.getElementById('report-view'),
      stackTraceView: document.getElementById('stack-trace-view'),
      detailTopbarActions: document.getElementById('detail-topbar-actions'),
      stackTraceBreadcrumbs: document.getElementById('stack-trace-breadcrumbs'),
      stackTraceGrid: document.getElementById('stack-trace-grid'),
    };
  },

  setupTestResults(rootReport) {
    this.state.variants = rootReport.variants || [];
    this.state.testSuites = rootReport.testSuites || [];
    this.state.targets = rootReport.targets || [];
    this.state.suiteDescriptors = rootReport.suiteDescriptors || {};
    this.state.suiteEnvironments = rootReport.suiteEnvironments || [];
    this.state.relativeRootDir = rootReport.relativeRootDir;
    this.state.filters.variants = [...(rootReport.variants || [])];
    this.state.filters.suiteAttributes = {};

    const actualSuites = (rootReport.testSuites || []).filter(ts => ts !== AGGREGATED_SUITE_NAME);
    if (actualSuites.length === 1) {
      this.state.filters.testSuite = actualSuites[0];
    }

    if ((this.state.sort.by === TEST_STATUS.FAIL || this.state.sort.by === TEST_STATUS.FAILED) && rootReport.variants && rootReport.variants.length > 0) {
      this.state.sort.by = `${rootReport.variants[0]}.fail`;
    }

    // Populate header
    if (this.elements.appTitle) this.elements.appTitle.textContent = rootReport.projectName || 'Test Report';
    if (this.elements.reportDate) this.elements.reportDate.textContent = rootReport.timestamp || '';
    if (this.elements.totalModules) this.elements.totalModules.textContent = rootReport.numberOfModules || 0;
    if (this.elements.totalPackages) this.elements.totalPackages.textContent = rootReport.numberOfPackages || 0;
    if (this.elements.totalClasses) this.elements.totalClasses.textContent = rootReport.numberOfClasses || 0;

    this.processedData = rootReport;
    this.suiteHasRichEntries = new Map();
    this.suiteAdapterIds = new Map();
    for (const [sName, desc] of Object.entries(this.state.suiteDescriptors || {})) {
      if (desc && desc.adapterId) {
        this.suiteAdapterIds.set(sName, desc.adapterId);
      }
      if (desc && Array.isArray(desc.summaryColumns) && desc.summaryColumns.length > 0) {
        this.suiteHasRichEntries.set(sName, true);
      }
    }

    const suiteTargetSets = new Map();
    for (const m of (rootReport.modules || [])) {
      for (const p of (m.packages || [])) {
        for (const c of (p.classes || [])) {
          for (const tc of (c.testCases || [])) {
            for (const tgt of (tc.targets || [])) {
              const tName = tgt.name || '';
              for (const sr of (tgt.testSuiteResults || [])) {
                if (sr && sr.testSuiteName) {
                  let set = suiteTargetSets.get(sr.testSuiteName);
                  if (!set) {
                    set = new Set();
                    suiteTargetSets.set(sr.testSuiteName, set);
                  }
                  set.add(tName);
                  for (const res of Object.values(sr.variantResults || {})) {
                    if (!res) continue;
                    if (res.adapterId && res.adapterId !== 'default' && !this.suiteAdapterIds.has(sr.testSuiteName)) {
                      this.suiteAdapterIds.set(sr.testSuiteName, res.adapterId);
                    }
                    if (this.hasRichReportEntries(res)) {
                      this.suiteHasRichEntries.set(sr.testSuiteName, true);
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    this.suiteTargetsMap = new Map();
    suiteTargetSets.forEach((set, sName) => {
      this.suiteTargetsMap.set(sName, Array.from(set));
    });

    this.syncSortModeWithLayout();

    // Build O(1) test case index asynchronously in time-sliced background chunks to prevent UI thread freezing
    this.buildTestCaseIndexAsync(rootReport);
  },

  /**
   * Resolves the registered SuiteRegistry plugin for a given suite name.
   */
  resolveSuitePlugin(suiteName) {
    const desc = (this.state.suiteDescriptors || {})[suiteName];
    const adapterId = (desc && desc.adapterId) || (this.suiteAdapterIds && this.suiteAdapterIds.get(suiteName)) || 'default';
    return SuiteRegistry.resolve(adapterId);
  },

  getSummaryColumnClass(col) {
    return this.resolveSuitePlugin(this.state.filters.testSuite).getSummaryColumnClass(col);
  },

  /**
   * Returns true when a single test suite and a single build variant are active.
   */
  isSingleSuiteSingleVariant() {
    const suite = this.state.filters.testSuite;
    const variants = this.state.filters.variants || [];
    return Boolean(suite && suite !== 'all' && suite !== AGGREGATED_SUITE_NAME && variants.length === 1);
  },

  /**
   * Returns true only when a single suite & single variant are active AND that suite opted into
   * rich report rendering (via its suite plugin, declared summaryColumns, or `androidx.report.*` entries).
   * Classic suites (`unitTest`, `androidTest`, `screenshotTest`, and plain custom suites) return false.
   */
  usesRichSingleSuiteLayout() {
    if (!this.isSingleSuiteSingleVariant()) return false;
    const suiteName = this.state.filters.testSuite;
    const plugin = this.resolveSuitePlugin(suiteName);
    if (plugin && plugin.id !== 'default') {
      return Boolean(plugin.useRichSingleSuiteList);
    }
    const normalizedSuite = String(suiteName || '').toLowerCase();
    if (normalizedSuite === 'unittest' || normalizedSuite === 'androidtest') {
      return false;
    }
    const desc = (this.state.suiteDescriptors || {})[suiteName];
    if (desc && Array.isArray(desc.summaryColumns) && desc.summaryColumns.length > 0) {
      return true;
    }
    return Boolean(this.suiteHasRichEntries && this.suiteHasRichEntries.get(suiteName));
  },

  /**
   * Returns true when displaying the flat test cases table in rich single-suite, single-variant mode.
   */
  isSingleSuiteTestList() {
    return this.usesRichSingleSuiteLayout() &&
      this.state.viewMode === 'flat' &&
      this.state.currentFlatView === 'testCases';
  },

  /**
   * Keeps active sort and single-suite status tab filters compatible across layout switches.
   */
  syncSortModeWithLayout() {
    if (
      !this.usesRichSingleSuiteLayout() &&
      Array.isArray(this.state.filters.status) &&
      this.state.filters.status.length < ALL_TEST_STATUSES.length &&
      (!this.elements.statusChipContainer || this.elements.statusChipContainer.classList.contains('hidden'))
    ) {
      this.state.filters.status = [...ALL_TEST_STATUSES];
      if (this.elements.statusFilterList) {
        this.buildStatusDropdown();
      }
    }

    const by = this.state.sort.by || '';
    if (this.isSingleSuiteTestList()) {
      if (by === 'target' && this.hasOnlyDefaultTarget()) {
        this.state.sort = { by: 'status', order: 'asc' };
        return;
      }
      const { variant, metric } = this.parseSortKey(by);
      if (variant) {
        // Keep the classic sort so leaving the rich list restores it.
        this.savedClassicSort = { ...this.state.sort };
        const norm = this.normalizeMetric(metric);
        if (norm === TEST_STATUS.FAILED) {
          this.state.sort = { by: 'status', order: 'asc' };
        } else if (norm === TEST_STATUS.PASSED || norm === 'rate') {
          this.state.sort = { by: 'status', order: 'desc' };
        } else if (norm === 'duration') {
          this.state.sort = { by: 'duration', order: this.state.sort.order };
        } else if (norm === 'target') {
          this.state.sort = { by: 'target', order: this.state.sort.order };
        } else {
          this.state.sort = { by: 'status', order: 'asc' };
        }
      }
    } else {
      const firstVariant = (this.state.filters.variants && this.state.filters.variants[0]) || (this.state.variants && this.state.variants[0]);
      const isRichSortKey = by === 'status' || by === 'duration' || by === 'target' || by.startsWith('attr:');
      const savedSort = this.savedClassicSort;
      this.savedClassicSort = null;
      if (isRichSortKey && savedSort) {
        const { variant: savedVar, metric: savedMetric } = this.parseSortKey(savedSort.by || '');
        const activeVars = this.state.filters.variants || [];
        if (savedVar && !activeVars.includes(savedVar)) {
          this.state.sort = firstVariant
            ? { by: `${firstVariant}.${savedMetric}`, order: savedSort.order }
            : { by: 'name', order: 'asc' };
        } else {
          this.state.sort = savedSort;
        }
      } else if (by === 'status') {
        if (firstVariant) {
          this.state.sort = {
            by: `${firstVariant}.fail`,
            order: this.state.sort.order === 'asc' ? 'desc' : 'asc',
          };
        } else {
          this.state.sort = { by: 'name', order: 'asc' };
        }
      } else if (by === 'duration' || by === 'target' || by.startsWith('attr:')) {
        if (firstVariant) {
          this.state.sort = { by: `${firstVariant}.fail`, order: 'desc' };
        } else {
          this.state.sort = { by: 'name', order: 'asc' };
        }
      }
    }
  },

  async buildTestCaseIndexAsync(rootReport) {
    this.testCaseIndex = new Map();

    const staticHierarchy = { 'module': 'package', 'package': 'class', 'class': 'testCase', 'testCase': 'target' };
    const annotateType = (node, type) => {
      node.type = type;
      const childType = staticHierarchy[type];
      if (!childType) return;

      const childKey = this.pluralize(childType);
      const children = node[childKey] || (type === 'class' ? node.testCases : []) || [];
      children.forEach(child => annotateType(child, childType));
    };

    const modules = rootReport.modules || [];
    let itemsProcessed = 0;
    const CHUNK_SIZE = 200; // Yield to event loop every 200 classes to keep UI responsive

    for (const m of modules) {
      annotateType(m, 'module');
      for (const p of (m.packages || [])) {
        for (const c of (p.classes || [])) {
          itemsProcessed++;
          for (const tc of (c.testCases || [])) {
            const entry = { testCase: tc, moduleName: m.name, packageName: p.name, className: c.name, classNode: c };
            this.testCaseIndex.set(`${m.name}:${p.name}:${c.name}:${tc.name}`, entry);
            const nameKey = (tc.name || '').toLowerCase();
            if (!this.testCaseIndex.has(nameKey)) {
              this.testCaseIndex.set(nameKey, entry);
            }
          }
          if (itemsProcessed % CHUNK_SIZE === 0) {
            await new Promise(resolve => setTimeout(resolve, 0));
          }
        }
      }
    }
  },

  populateFilters() {
    // Test Suite Dropdown
    const actualSuites = (this.state.testSuites || []).filter(ts => ts !== AGGREGATED_SUITE_NAME);
    const isSingleSuite = actualSuites.length === 1;

    if (isSingleSuite) {
      this.state.filters.testSuite = actualSuites[0];
      if (this.elements.testSuiteFilterList) {
        this.elements.testSuiteFilterList.innerHTML = '';
      }
    } else {
      const testSuiteOptions = [
        { name: 'all', value: 'all' },
        ...[...actualSuites]
          .sort((a, b) => UIUtils.formatSuiteName(a).localeCompare(UIUtils.formatSuiteName(b)))
          .map(ts => ({ name: UIUtils.formatSuiteName(ts), value: ts }))
      ];
      UIUtils.buildActionDropdown(this.elements.testSuiteFilterList, testSuiteOptions, this.state.filters.testSuite, (newVal) => {
        if (this.state.filters.testSuite !== newVal) {
          this.state.filters.suiteAttributes = {};
        }
        this.state.filters.testSuite = newVal;
        this.syncSortModeWithLayout();
        this.updateTestSuiteUI();
        this.render();
        Navigation.push();
      }, false, false);
    }
    this.updateTestSuiteUI();

    // Variants Dropdown
    const variantOptions = this.state.variants.map(v => ({ name: v, value: v }));
    UIUtils.buildActionDropdown(this.elements.variantFilterList, variantOptions, this.state.filters.variants, (newArr) => {
      this.state.filters.variants = newArr;
      const { variant, metric } = this.parseSortKey(this.state.sort.by);
      if (variant && !newArr.includes(variant)) {
        if (newArr.length > 0) {
          this.state.sort.by = `${newArr[0]}.${metric}`;
        } else {
          this.state.sort.by = 'name';
        }
      }
      this.syncSortModeWithLayout();
      this.updateVariantButtonText();
      this.render();
      Navigation.push();
    }, true, true);

    // Status Dropdown
    this.buildStatusDropdown();
    this.updateVariantButtonText();
  },

  buildStatusDropdown() {
    const statusOptions = [
      { name: 'Passed', value: TEST_STATUS.PASSED },
      { name: 'Failed', value: TEST_STATUS.FAILED },
      { name: 'Skipped', value: TEST_STATUS.SKIPPED }
    ];
    if (!Array.isArray(this.state.filters.status)) {
      this.state.filters.status = [...ALL_TEST_STATUSES];
    }

    const updateStatusButtonText = () => {
      let label = 'Status: All';
      const statusArr = this.state.filters.status;
      if (statusArr.length === 0 || statusArr.length === statusOptions.length) {
        label = 'Status: All';
      } else if (statusArr.length === 1) {
        label = 'Status: ' + statusOptions.find(o => o.value === statusArr[0])?.name;
      } else {
        label = `Status: ${statusArr.length} Selected`;
      }
      if (this.elements.statusFilterText) {
        this.elements.statusFilterText.textContent = label;
      }
    };

    updateStatusButtonText();

    UIUtils.buildActionDropdown(this.elements.statusFilterList, statusOptions, this.state.filters.status, (newArr) => {
      this.state.filters.status = newArr;
      updateStatusButtonText();
      this.render();
      Navigation.push();
    }, false, true);
  },

  updateVariantButtonText() {
    const selectedCount = this.state.filters.variants.length;
    const totalCount = this.state.variants.length;

    if (this.elements.variantFilterBtn) {
      let label = 'Filter by Variant';
      if (selectedCount === totalCount && totalCount > 0) {
        label = 'Filter by Variant: All';
      } else if (selectedCount === 1) {
        label = `Filter by Variant: ${this.state.filters.variants[0]}`;
      } else {
        label = `Filter by Variant: ${selectedCount} Selected`;
      }
      this.elements.variantFilterBtn.setAttribute('data-tooltip', label);
      this.elements.variantFilterBtn.setAttribute('aria-label', label);
    }
  },

  // --- EVENT BINDING & HANDLING ---
  bindEvents() {
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        if (Tooltip.activeTarget) {
          Tooltip.hide();
          return;
        }
        const openDropdownConf = this.getDropdownConfigs().find(c => c.dropdown && !c.dropdown.classList.contains('hidden'));
        if (openDropdownConf) {
          this.toggleDropdown(openDropdownConf.dropdown, openDropdownConf.btn);
        } else if (this.state.currentView === 'stack-trace') {
          if (this.state.detailViewMode === 'rich') {
            this.returnFromDetailView();
          } else {
            history.back();
          }
        } else if (this.elements.searchWrapper && this.elements.searchWrapper.classList.contains('expanded') && !this.elements.searchWrapper.classList.contains('always-expanded')) {
          this.elements.searchWrapper.classList.remove('expanded');
          setTimeout(() => {
            this.elements.searchRevealBtn.classList.remove('hidden');
            this.elements.searchRevealBtn.focus();
          }, 300);
        }
      }
    });

    this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
      if (btn && dropdown) {
        this.bindDropdownKeyboardEvents(btn, dropdown);
      }
    });

    if (this.elements.statusSegments) {
      this.elements.statusSegments.addEventListener('click', (e) => {
        const btn = e.target.closest('button[data-status-tab]');
        if (!btn) return;
        const tab = btn.dataset.statusTab;
        if (tab === 'all') {
          this.state.filters.status = [...ALL_TEST_STATUSES];
        } else if (tab === 'failed') {
          this.state.filters.status = [TEST_STATUS.FAILED];
        } else if (tab === 'passed') {
          this.state.filters.status = [TEST_STATUS.PASSED];
        } else if (tab === 'skipped') {
          this.state.filters.status = [TEST_STATUS.SKIPPED];
        }
        this.buildStatusDropdown();
        this.render();
        Navigation.push();
      });
    }

    this.elements.searchInput.addEventListener('input', () => {
      this.state.filters.search = this.elements.searchInput.value.trim();
      if (this.state.filters.search.length > 0) {
        this.elements.searchClearBtn.classList.remove('hidden');
      } else {
        this.elements.searchClearBtn.classList.add('hidden');
      }
      this.render();
    });

    if (this.elements.searchClearBtn) {
      this.elements.searchClearBtn.addEventListener('click', () => {
        this.elements.searchInput.value = '';
        this.state.filters.search = '';
        this.elements.searchClearBtn.classList.add('hidden');
        this.render();
        this.elements.searchInput.focus();
      });
    }

    if (this.elements.searchRevealBtn) {
      this.elements.searchRevealBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.elements.searchRevealBtn.classList.add('hidden');
        this.elements.searchWrapper.classList.add('expanded');
        this.elements.searchInput.focus();
      });
    }

    this.elements.testSuiteFilterBtn.addEventListener('click', () => {
      const actualSuites = (this.state.testSuites || []).filter(ts => ts !== AGGREGATED_SUITE_NAME);
      if (actualSuites.length <= 1) return;
      this.toggleDropdown(this.elements.testSuiteFilterDropdown, this.elements.testSuiteFilterBtn);
    });
    this.elements.variantFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.variantFilterDropdown, this.elements.variantFilterBtn));
    this.elements.statusFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.statusFilterDropdown, this.elements.statusFilterBtn));

    if (this.elements.moduleFilterBtn) this.elements.moduleFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.moduleFilterDropdown, this.elements.moduleFilterBtn));
    if (this.elements.packageFilterBtn) this.elements.packageFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.packageFilterDropdown, this.elements.packageFilterBtn));
    if (this.elements.classFilterBtn) this.elements.classFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.classFilterDropdown, this.elements.classFilterBtn));
    if (this.elements.tcFilterBtn) this.elements.tcFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.tcFilterDropdown, this.elements.tcFilterBtn));
    if (this.elements.targetFilterBtn) this.elements.targetFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.targetFilterDropdown, this.elements.targetFilterBtn));

    // Add Filter Logic
    if (this.elements.addFilterBtn) {
      this.elements.addFilterBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.toggleDropdown(this.elements.addFilterDropdown, this.elements.addFilterBtn);
      });
    }

    const filterTypeConfig = {
      'status': { container: this.elements.statusChipContainer, dropdown: this.elements.statusFilterDropdown, btn: this.elements.statusFilterBtn },
      'module': { container: this.elements.modChipContainer, dropdown: this.elements.moduleFilterDropdown, btn: this.elements.moduleFilterBtn, stateKey: 'modules' },
      'package': { container: this.elements.pkgChipContainer, dropdown: this.elements.packageFilterDropdown, btn: this.elements.packageFilterBtn, stateKey: 'packages' },
      'class': { container: this.elements.clsChipContainer, dropdown: this.elements.classFilterDropdown, btn: this.elements.classFilterBtn, stateKey: 'classes' },
      'testCase': { container: this.elements.tcChipContainer, dropdown: this.elements.tcFilterDropdown, btn: this.elements.tcFilterBtn, stateKey: 'testCases' },
      'target': { container: this.elements.tgtChipContainer, dropdown: this.elements.targetFilterDropdown, btn: this.elements.targetFilterBtn, stateKey: 'targets' }
    };

    if (this.elements.addFilterList) {
      this.elements.addFilterList.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        const target = e.target.closest('.dropdown-item');
        if (!target) return;

        const filterType = target.dataset.filterType;
        const config = filterTypeConfig[filterType];

        if (config) {
          config.container.classList.remove('hidden');
          this.elements.addFilterDropdown.classList.add('hidden');
          this.handleHeaderFilterChange(filterType);
          this.syncSortModeWithLayout();
          this.updateFilterButtons();
          this.render();
          Navigation.push();

          setTimeout(() => {
            this.toggleDropdown(config.dropdown, config.btn);
          }, 0);
        }
      });
    }

    // Close Filter Logic
    document.querySelectorAll('.chip-close').forEach(closeBtn => {
      closeBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const filterType = closeBtn.dataset.filterClose;
        const config = filterTypeConfig[filterType];

        if (config) {
          if (filterType === 'status') {
            this.state.filters.status = [...ALL_TEST_STATUSES];
            this.buildStatusDropdown();
          } else {
            this.state.filters[config.stateKey] = [];
          }
          config.container.classList.add('hidden');
          this.handleHeaderFilterChange();
          this.syncSortModeWithLayout();
          this.updateFilterButtons();
          this.render();
          Navigation.push();
        }
      });
    });

    if (this.elements.viewSegments) {
      this.elements.viewSegments.addEventListener('click', (e) => {
        const btn = e.target.closest('.segment-btn');
        if (!btn) return;
        this.state.viewMode = btn.dataset.value;
        this.syncSortModeWithLayout();
        this.updateViewModeUI();
        this.resetSelection();
        this.render();
        Navigation.push();
      });
    }

    if (this.elements.densitySegments) {
      this.elements.densitySegments.addEventListener('click', (e) => {
        const btn = e.target.closest('.segment-btn');
        if (!btn) return;
        this.elements.densitySegments.querySelectorAll('.segment-btn').forEach(b => {
          b.classList.remove('active');
          b.setAttribute('aria-pressed', 'false');
        });
        btn.classList.add('active');
        btn.setAttribute('aria-pressed', 'true');
        this.state.density = btn.dataset.value;
        if (this.elements.mainTable) {
          if (this.state.density === 'compact') {
            this.elements.mainTable.classList.add('table-compact');
          } else {
            this.elements.mainTable.classList.remove('table-compact');
          }
        }
      });
    }

    if (this.elements.groupByBtn) {
      this.elements.groupByBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.toggleDropdown(this.elements.groupByDropdown, this.elements.groupByBtn);
      });
    }

    if (this.elements.groupByDropdown) {
      this.elements.groupByDropdown.addEventListener('click', (e) => {
        const target = e.target.closest('.dropdown-item');
        if (target) {
          this.state.currentFlatView = target.dataset.value;
          this.elements.groupByDropdown.classList.add('hidden');
          this.elements.groupByBtn.setAttribute('aria-expanded', 'false');
          this.elements.groupByBtn.focus();

          // When changing the grouping via the dropdown, we reset all selections
          // to show the global list for that grouping, consistent with coverage report.
          this.resetSelection();
          this.syncSortModeWithLayout();

          this.render();
          Navigation.push();
        }
      });
    }
    this.elements.resultsData.addEventListener('click', (e) => {
      const stackTrigger = e.target.closest('.stack-trace-trigger');
      if (stackTrigger && !e.target.closest('a, button, .resizer')) {
        e.preventDefault();
        e.stopPropagation();
        this.openStackTrace(stackTrigger);
        return;
      }
      if (this.state.viewMode === 'flat') {
        const clickable = e.target.closest('[data-interactive="flat"]');
        if (clickable) {
          e.preventDefault();
          this.handleFlatRowClick(clickable);
        }
      } else {
        const treeToggle = e.target.closest('[data-interactive="tree"]');
        if (treeToggle) {
          e.preventDefault();
          this.handleTreeRowClick(treeToggle);
        }
      }
    });
    this.elements.resultsData.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        const clickable = e.target.closest('[data-interactive="flat"], [data-interactive="tree"], .clickable-status, .stack-trace-trigger');
        if (clickable) {
          e.preventDefault();
          clickable.click();
        }
      }
    });
    const handleBreadcrumbAction = (e) => {
      const backBtn = e.target.closest('[data-action="back-to-list"]');
      if (backBtn) {
        e.preventDefault();
        this.returnFromDetailView();
        return;
      }
      const link = e.target.closest('a[data-action]');
      if (!link) return;
      e.preventDefault();
      const { action, moduleName, packageName } = link.dataset;

      if (action === BREADCRUMB_ACTIONS.GO_TO_MODULES) {
        this.state.selectedModule = null;
        this.state.selectedPackage = null;
        this.state.selectedClass = null;
        this.state.currentFlatView = 'modules';
      } else if (action === BREADCRUMB_ACTIONS.GO_TO_PACKAGES) {
        this.state.selectedModule = moduleName || this.state.selectedModule;
        this.state.selectedPackage = null;
        this.state.selectedClass = null;
        this.state.currentFlatView = 'packages';
      } else if (action === BREADCRUMB_ACTIONS.GO_TO_CLASSES) {
        this.state.selectedModule = moduleName || this.state.selectedModule;
        this.state.selectedPackage = packageName || this.state.selectedPackage;
        this.state.selectedClass = null;
        this.state.currentFlatView = 'classes';
      } else if (action === BREADCRUMB_ACTIONS.GO_TO_TEST_CASES) {
        this.state.selectedModule = moduleName || this.state.selectedModule;
        this.state.selectedPackage = packageName || this.state.selectedPackage;
        this.state.selectedClass = link.dataset.className || this.state.selectedClass;
        this.state.currentFlatView = 'testCases';
      }

      this.state.viewMode = 'flat';
      this.syncSortModeWithLayout();
      this.updateViewModeUI();
      this.showReportView();
      this.render();
      Navigation.push();
    };

    this.elements.breadcrumbs.addEventListener('click', handleBreadcrumbAction);
    this.elements.stackTraceBreadcrumbs.addEventListener('click', handleBreadcrumbAction);

    this.elements.breadcrumbs.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        const link = e.target.closest('.breadcrumb-link');
        if (link) {
          e.preventDefault();
          link.click();
        }
      }
    });

    this.elements.stackTraceBreadcrumbs.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        const link = e.target.closest('.breadcrumb-link, [data-action="back-to-list"]');
        if (link) {
          e.preventDefault();
          link.click();
        }
      }
    });

    this.elements.tableHeaders.addEventListener('click', (e) => {
      if (this.state.isResizing || e.target.classList.contains('resizer')) return;
      const th = e.target.closest('[data-sort-by]');
      if (!th) return;

      const newSortBy = th.dataset.sortBy;
      const isAlreadyActive = this.isSortActive(newSortBy);
      if (isAlreadyActive) {
        this.state.sort.by = newSortBy;
        this.state.sort.order = this.state.sort.order === 'asc' ? 'desc' : 'asc';
      } else {
        this.state.sort.by = newSortBy;
        const isAscendingDefault =
          newSortBy === 'name' ||
          newSortBy === 'target' ||
          newSortBy === 'status' ||
          newSortBy.startsWith('attr:');
        this.state.sort.order = isAscendingDefault ? 'asc' : 'desc';
      }

      const headerName = th.textContent.replace(/[▲▼]/g, '').trim();
      const orderText = this.state.sort.order === 'asc' ? 'ascending' : 'descending';
      this.announce(`Sorted by ${headerName}, ${orderText}`);

      this.render();
      Navigation.push();
    });
    this.elements.tableHeaders.addEventListener('keydown', (e) => {
      if (e.target.classList.contains('resizer')) return;
      if (e.key === 'Enter' || e.key === ' ') {
        const th = e.target.closest('[data-sort-by]');
        if (th) {
          e.preventDefault();
          th.click();
        }
      }
    });

  },

  bindDropdownKeyboardEvents(btn, dropdown) {
    btn.addEventListener('keydown', (e) => {
      if (btn.disabled || btn.classList.contains('unclickable') || btn.getAttribute('aria-disabled') === 'true') {
        return;
      }
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        e.stopPropagation();
        this.toggleDropdown(dropdown, btn);
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        if (dropdown.classList.contains('hidden')) {
          this.toggleDropdown(dropdown, btn);
        } else {
          const firstItem = dropdown.querySelector('button, [tabindex="0"], input');
          if (firstItem) firstItem.focus();
        }
      }
    });
    dropdown.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        const items = Array.from(dropdown.querySelectorAll('input:not([disabled]), button:not([disabled]), [role="option"], [role="menuitem"]'))
          .filter(el => el.style.display !== 'none' && el.offsetWidth > 0 && el.offsetHeight > 0);
        if (items.length === 0) return;
        const currentIndex = items.indexOf(document.activeElement);
        let nextIndex = 0;
        if (e.key === 'ArrowDown') {
          nextIndex = currentIndex < items.length - 1 ? currentIndex + 1 : 0;
        } else {
          nextIndex = currentIndex > 0 ? currentIndex - 1 : items.length - 1;
        }
        items[nextIndex].focus();
      }
    });
  },

  updateTestSuiteUI() {
    const actualSuites = (this.state.testSuites || []).filter(ts => ts !== AGGREGATED_SUITE_NAME);
    const isSingleSuite = actualSuites.length === 1;

    if (isSingleSuite) {
      this.state.filters.testSuite = actualSuites[0];
      if (this.elements.tsAllState) this.elements.tsAllState.classList.add('hidden');
      if (this.elements.tsSelectedState) this.elements.tsSelectedState.classList.remove('hidden');
      if (this.elements.testSuiteFilterText) {
        this.elements.testSuiteFilterText.textContent = UIUtils.formatSuiteName(actualSuites[0]);
      }
      if (this.elements.testSuiteFilterBtn) {
        this.elements.testSuiteFilterBtn.disabled = true;
        this.elements.testSuiteFilterBtn.classList.add('unclickable');
        this.elements.testSuiteFilterBtn.setAttribute('aria-disabled', 'true');
        this.elements.testSuiteFilterBtn.setAttribute('aria-expanded', 'false');
        this.elements.testSuiteFilterBtn.removeAttribute('aria-haspopup');
        this.elements.testSuiteFilterBtn.tabIndex = -1;
      }
      if (this.elements.testSuiteFilterDropdown) {
        this.elements.testSuiteFilterDropdown.classList.add('hidden');
      }
    } else {
      if (this.elements.testSuiteFilterBtn) {
        this.elements.testSuiteFilterBtn.disabled = false;
        this.elements.testSuiteFilterBtn.classList.remove('unclickable');
        this.elements.testSuiteFilterBtn.removeAttribute('aria-disabled');
        this.elements.testSuiteFilterBtn.setAttribute('aria-haspopup', 'listbox');
        this.elements.testSuiteFilterBtn.tabIndex = 0;
      }
      if (this.elements.tsAllState && this.elements.tsSelectedState) {
        if (this.state.filters.testSuite === 'all') {
          this.elements.tsAllState.classList.remove('hidden');
          this.elements.tsSelectedState.classList.add('hidden');
        } else {
          this.elements.tsAllState.classList.add('hidden');
          this.elements.tsSelectedState.classList.remove('hidden');
          if (this.elements.testSuiteFilterText) {
            this.elements.testSuiteFilterText.textContent = UIUtils.formatSuiteName(this.state.filters.testSuite);
          }
        }
      }
    }
  },

  updateFilterButtons() {
    let activeChipsCount = 0;
    const { filters } = this.state;

    const updateChip = (type, stateArray, totalCount, textElement, chipContainer) => {
      if (!textElement) return;

      let label = `${type.charAt(0).toUpperCase() + type.slice(1)}: All`;
      if (stateArray.length > 0 && stateArray.length < totalCount) {
        if (stateArray.length === 1) {
          label = `${type.charAt(0).toUpperCase() + type.slice(1)}: ${stateArray[0]}`;
        } else {
          label = `${type.charAt(0).toUpperCase() + type.slice(1)}: ${stateArray.length} Selected`;
        }
      }
      textElement.textContent = label;

      if (stateArray.length > 0 || !chipContainer.classList.contains('hidden')) {
        chipContainer.classList.remove('hidden');
        activeChipsCount++;
        this.toggleAddFilterOption(type, false);
      } else {
        this.toggleAddFilterOption(type, true);
      }
    };

    const totalModules = this.processedData.modules ? this.processedData.modules.length : 0;
    const totalPackages = this.processedData.modules ? [...new Set(this.processedData.modules.flatMap(m => (m.packages || []).map(p => p.name)))].length : 0;
    const totalClasses = this.processedData.modules ? [...new Set(this.processedData.modules.flatMap(m => (m.packages || []).flatMap(p => (p.classes || []).map(c => c.name))))].length : 0;
    const totalTestCases = this.processedData.modules ? [...new Set(this.processedData.modules.flatMap(m => (m.packages || []).flatMap(p => (p.classes || []).flatMap(c => (c.testCases || []).map(tc => tc.name)))))].length : 0;
    const totalTargets = this.processedData.targets ? this.processedData.targets.length : 0;

    updateChip('module', filters.modules, totalModules, this.elements.moduleFilterText, this.elements.modChipContainer);
    updateChip('package', filters.packages, totalPackages, this.elements.packageFilterText, this.elements.pkgChipContainer);
    updateChip('class', filters.classes, totalClasses, this.elements.classFilterText, this.elements.clsChipContainer);
    updateChip('testCase', filters.testCases, totalTestCases, this.elements.tcFilterText, this.elements.tcChipContainer);
    updateChip('target', filters.targets, totalTargets, this.elements.targetFilterText, this.elements.tgtChipContainer);

    const isStatusVisible = !this.elements.statusChipContainer.classList.contains('hidden');
    if (isStatusVisible) {
      activeChipsCount++;
      this.toggleAddFilterOption('status', false);
    } else {
      this.toggleAddFilterOption('status', true);
    }

    if (this.elements.addFilterBtn) {
      if (activeChipsCount === 6) { // module, package, class, testCase, target, status
        this.elements.addFilterBtn.closest('#add-filter-container').classList.add('hidden');
      } else {
        this.elements.addFilterBtn.closest('#add-filter-container').classList.remove('hidden');
      }
    }
  },

  updateViewModeUI() {
    if (this.elements.viewSegments) {
      this.elements.viewSegments.querySelectorAll('.segment-btn').forEach(btn => {
        const isActive = btn.dataset.value === this.state.viewMode;
        btn.classList.toggle('active', isActive);
        btn.setAttribute('aria-pressed', isActive ? 'true' : 'false');
      });
    }
  },

  toggleAddFilterOption(type, show) {
    if (!this.elements.addFilterList) return;
    const option = this.elements.addFilterList.querySelector(`[data-filter-type="${type}"]`);
    if (option) {
      if (show) {
        option.classList.remove('hidden');
        option.style.display = '';
      } else {
        option.classList.add('hidden');
        option.style.display = 'none';
      }
    }
  },

  getDropdownConfigs() {
    return [
      { btn: this.elements.testSuiteFilterBtn, dropdown: this.elements.testSuiteFilterDropdown },
      { btn: this.elements.variantFilterBtn, dropdown: this.elements.variantFilterDropdown },
      { btn: this.elements.statusFilterBtn, dropdown: this.elements.statusFilterDropdown },
      { btn: this.elements.moduleFilterBtn, dropdown: this.elements.moduleFilterDropdown },
      { btn: this.elements.packageFilterBtn, dropdown: this.elements.packageFilterDropdown },
      { btn: this.elements.classFilterBtn, dropdown: this.elements.classFilterDropdown },
      { btn: this.elements.tcFilterBtn, dropdown: this.elements.tcFilterDropdown },
      { btn: this.elements.targetFilterBtn, dropdown: this.elements.targetFilterDropdown },
      { btn: this.elements.addFilterBtn, dropdown: this.elements.addFilterDropdown },
      { btn: this.elements.groupByBtn, dropdown: this.elements.groupByDropdown },
      ...(this._dynamicSuiteDropdowns || []),
    ];
  },

  toggleDropdown(dropdownToToggle, button) {
    if (button && (button.disabled || button.classList.contains('unclickable') || button.getAttribute('aria-disabled') === 'true')) {
      return;
    }
    this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
      if (dropdown && dropdown !== dropdownToToggle) {
        dropdown.classList.add('hidden');
        if (btn) btn.setAttribute('aria-expanded', 'false');
      }
    });

    if (dropdownToToggle) {
      const isHidden = dropdownToToggle.classList.contains('hidden');
      if (isHidden) {
        dropdownToToggle.classList.remove('hidden');
        if (button) button.setAttribute('aria-expanded', 'true');

        // Focus management
        setTimeout(() => {
          const searchInput = dropdownToToggle.querySelector('input');
          if (searchInput) {
            searchInput.focus();
          } else {
            const firstItem = dropdownToToggle.querySelector('button, [tabindex="0"], input');
            if (firstItem) firstItem.focus();
          }
        }, 0);
      } else {
        dropdownToToggle.classList.add('hidden');
        if (button) {
          button.setAttribute('aria-expanded', 'false');
          button.focus();
        }
      }
    }
  },

  closeAllDropdowns() {
    this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
      if (dropdown && !dropdown.classList.contains('hidden')) {
        dropdown.classList.add('hidden');
        if (btn) {
          btn.setAttribute('aria-expanded', 'false');
          btn.focus();
        }
      }
    });
  },

  announce(message) {
    const announcer = document.getElementById('a11y-announcer');
    if (announcer) {
      announcer.textContent = '';
      if (this.announceTimeout) clearTimeout(this.announceTimeout);
      // Small delay to ensure the DOM change is registered
      this.announceTimeout = setTimeout(() => {
        announcer.textContent = message;
      }, 50);
    }
  },
  closeDropdownsOnClickOutside() {
    document.addEventListener('click', (e) => {
      this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
        if (btn && dropdown && !btn.contains(e.target) && !dropdown.contains(e.target)) {
          dropdown.classList.add('hidden');
          if (btn) btn.setAttribute('aria-expanded', 'false');
        }
      });

      // Search Input Collapse
      if (this.elements.searchWrapper && this.elements.searchRevealBtn && !this.elements.searchWrapper.classList.contains('always-expanded')) {
        if (!this.elements.searchWrapper.contains(e.target) && !this.elements.searchRevealBtn.contains(e.target)) {
          if (this.elements.searchInput && this.elements.searchInput.value === '') {
            this.elements.searchWrapper.classList.remove('expanded');
            setTimeout(() => {
              this.elements.searchRevealBtn.classList.remove('hidden');
            }, 300);
          }
        }
      }
    });
  },

  handleHeaderFilterChange(explicitType = null) {
    if (this.state.viewMode !== 'flat') return;

    if (explicitType) {
      const viewMap = { 'module': 'modules', 'package': 'packages', 'class': 'classes', 'testCase': 'testCases', 'target': 'testCases' };
      if (viewMap[explicitType]) {
        this.state.currentFlatView = viewMap[explicitType];
      }
    } else {
      const { classes, packages, modules, testCases, targets } = this.state.filters;
      const activeChips = [];
      if (!this.elements.tgtChipContainer.classList.contains('hidden')) activeChips.push('target');
      if (!this.elements.tcChipContainer.classList.contains('hidden')) activeChips.push('testCase');
      if (!this.elements.clsChipContainer.classList.contains('hidden')) activeChips.push('class');
      if (!this.elements.pkgChipContainer.classList.contains('hidden')) activeChips.push('package');
      if (!this.elements.modChipContainer.classList.contains('hidden')) activeChips.push('module');

      if (targets.length > 0 || activeChips.includes('target') || testCases.length > 0 || activeChips.includes('testCase')) {
        this.state.currentFlatView = 'testCases';
      } else if (classes.length > 0 || activeChips.includes('class')) {
        this.state.currentFlatView = 'classes';
      } else if (packages.length > 0 || activeChips.includes('package')) {
        this.state.currentFlatView = 'packages';
      } else if (modules.length > 0 || activeChips.includes('module')) {
        this.state.currentFlatView = 'modules';
      } else {
        // Fallback to testCases if no hierarchy filters are active
        this.state.currentFlatView = 'testCases';
      }
    }

    // Reset drill-down context to avoid confusing states when grouping abruptly changes
    this.resetSelection();
  },

  resetSelection() {
    this.state.selectedModule = null;
    this.state.selectedPackage = null;
    this.state.selectedClass = null;
  },

  handleFlatRowClick(target) {
    const { name, type, moduleName, packageName } = target.dataset;
    if (type === 'module') {
      this.state.selectedModule = name;
      this.state.currentFlatView = 'packages';
    } else if (type === 'package') {
      this.state.selectedModule = moduleName;
      this.state.selectedPackage = name;
      this.state.currentFlatView = 'classes';
    } else if (type === 'class') {
      this.state.selectedModule = moduleName;
      this.state.selectedPackage = packageName;
      this.state.selectedClass = name;
      this.state.currentFlatView = 'testCases';
    }
    this.syncSortModeWithLayout();
    this.render();
    Navigation.push();
  },

  handleTreeRowClick(target) {
    const row = target.closest('tr');
    if (!row) return;
    const arrow = row.querySelector('.collapsible-arrow');
    const toggle = row.querySelector('[data-interactive="tree"]');
    if (!arrow.classList.contains('invisible')) {
      arrow.classList.toggle('open');
      const isOpen = arrow.classList.contains('open');
      if (toggle) toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
      document.querySelectorAll(`[data-parent-id="${row.dataset.id}"]`).forEach(child => {
        child.classList.toggle('hidden', !isOpen);
        if (!isOpen) {
          const childArrow = child.querySelector('.collapsible-arrow.open');
          const childToggle = child.querySelector('[data-interactive="tree"]');
          if (childArrow) {
            childArrow.classList.remove('open');
            if (childToggle) childToggle.setAttribute('aria-expanded', 'false');
            this.collapseDescendants(child);
          }
        }
      });
    }
  },

  collapseDescendants(parentRow) {
    document.querySelectorAll(`[data-parent-id="${parentRow.dataset.id}"]`).forEach(child => {
      child.classList.add('hidden');
      const childArrow = child.querySelector('.collapsible-arrow.open');
      const childToggle = child.querySelector('[data-interactive="tree"]');
      if (childArrow) {
        childArrow.classList.remove('open');
        if (childToggle) childToggle.setAttribute('aria-expanded', 'false');
        this.collapseDescendants(child);
      }
    });
  },

  /**
   * Normalizes summaryAttributes from either a keyed object or an array into a uniform entry.
   */
  getSummaryAttr(res, key) {
    if (!res || !res.summaryAttributes || !key) return null;
    const attrs = res.summaryAttributes;
    if (Array.isArray(attrs)) {
      const found = attrs.find(a => a && a.key === key);
      if (!found) return null;
      const val = found.value !== undefined && found.value !== null ? String(found.value) : '';
      const disp = found.displayValue !== undefined && found.displayValue !== null
        ? String(found.displayValue)
        : val;
      return { key, label: found.label || key, value: val || disp, displayValue: disp || val };
    }
    if (typeof attrs === 'object' && Object.prototype.hasOwnProperty.call(attrs, key)) {
      const entry = attrs[key];
      if (entry === null || entry === undefined) return null;
      if (typeof entry === 'object') {
        const val = entry.value !== undefined && entry.value !== null ? String(entry.value) : '';
        const disp = entry.displayValue !== undefined && entry.displayValue !== null
          ? String(entry.displayValue)
          : val;
        return { key, label: entry.label || key, value: val || disp, displayValue: disp || val };
      }
      const val = String(entry);
      return { key, label: key, value: val, displayValue: val };
    }
    return null;
  },

  /**
   * Lists all summary attribute entries present on a VariantTestResult.
   */
  getAllSummaryAttrs(res) {
    if (!res || !res.summaryAttributes) return [];
    const attrs = res.summaryAttributes;
    if (Array.isArray(attrs)) {
      return attrs
        .filter(a => a && a.key)
        .map(a => this.getSummaryAttr(res, a.key))
        .filter(Boolean);
    }
    if (typeof attrs === 'object') {
      return Object.keys(attrs)
        .map(k => this.getSummaryAttr(res, k))
        .filter(Boolean);
    }
    return [];
  },

  /**
   * Returns summary columns declared by the active suite descriptor.
   */
  getActiveSummaryColumns() {
    if (!this.usesRichSingleSuiteLayout()) return [];
    const descriptor = (this.state.suiteDescriptors || {})[this.state.filters.testSuite];
    return descriptor && Array.isArray(descriptor.summaryColumns) ? descriptor.summaryColumns : [];
  },

  /**
   * Resolves the SuiteEnvironmentInfo for a suite, variant, and optional module or envId. Returns null when no
   * environment matches, or when the matches belong to different modules or variants and so describe different apps.
   */
  getMatchingSuiteEnvironment(suiteName, variantName, moduleName = null, envId = null) {
    const envs = this.state.suiteEnvironments || [];
    if (envId) {
      const byId = envs.find(e => e && e.envId === envId);
      if (byId) return byId;
    }
    const candidates = envs.filter(e => {
      if (!e) return false;
      if (suiteName && suiteName !== 'all' && e.suiteName && e.suiteName !== suiteName) return false;
      if (variantName && e.variantName && e.variantName !== variantName) return false;
      if (moduleName && e.modulePath && e.modulePath !== moduleName) return false;
      return true;
    });
    const apps = new Set(candidates.map(e => `${e.modulePath || ''}|${e.variantName || ''}`));
    return apps.size === 1 ? candidates[0] : null;
  },

  /**
   * Checks whether a leaf test case (or target) matches the search query across method, class,
   * summary attributes, devices, failure callout, stack trace, steps, and properties.
   */
  matchesRichLeafFilters(leafNode, res, { activeAttrFilters, searchTerm, parentMatchesSearch }) {
    if (!res) return false;
    for (const [attrKey, expectedVal] of activeAttrFilters) {
      const attr = this.getSummaryAttr(res, attrKey);
      if (!attr || (attr.value !== expectedVal && attr.displayValue !== expectedVal)) {
        return false;
      }
    }
    if (searchTerm && !parentMatchesSearch && !this.doesLeafMatchSearch(leafNode, searchTerm)) {
      return false;
    }
    return true;
  },

  doesLeafMatchSearch(node, searchTerm) {
    if (!searchTerm) return true;
    if ((node.name || '').toLowerCase().includes(searchTerm)) return true;

    const activeSuite = this.state.filters.testSuite;
    const activeVariants = this.state.filters.variants || [];
    const targetNodes = this.getScopedTargetNodes(node);

    for (const tNode of targetNodes) {
      if (tNode !== node && (tNode.name || '').toLowerCase().includes(searchTerm)) return true;
      const suiteResults = tNode.testSuiteResults || [];
      for (const sr of suiteResults) {
        if (activeSuite !== 'all' && sr.testSuiteName !== activeSuite) continue;
        const varMap = sr.variantResults || {};
        for (const [vName, res] of Object.entries(varMap)) {
          if (activeVariants.length > 0 && !activeVariants.includes(vName)) continue;
          if (!res) continue;

          if (res.sourceLocation) {
            const loc = res.sourceLocation;
            const locStr = `${loc.file || ''}:${loc.line || ''} ${loc.className || ''} ${loc.methodName || ''}`.toLowerCase();
            if (locStr.includes(searchTerm)) return true;
          }

          for (const attr of this.getAllSummaryAttrs(res)) {
            if (attr.value.toLowerCase().includes(searchTerm) || attr.displayValue.toLowerCase().includes(searchTerm)) {
              return true;
            }
          }

          if (Array.isArray(res.actors)) {
            for (const actor of res.actors) {
              const actorText = `${actor.role || ''} ${actor.name || ''} api ${actor.api || ''} ${actor.serial || ''} ${actor.image || ''} ${actor.playServices || ''}`.toLowerCase();
              if (actorText.includes(searchTerm)) return true;
            }
          }

          if (res.callout) {
            const calloutText = `${res.callout.title || ''} ${res.callout.message || ''} ${res.callout.likelyCause || ''} ${res.callout.kind || ''}`.toLowerCase();
            if (calloutText.includes(searchTerm)) return true;
          }

          if (res.properties && typeof res.properties === 'object') {
            for (const [pk, pv] of Object.entries(res.properties)) {
              if (String(pk).toLowerCase().includes(searchTerm) || String(pv).toLowerCase().includes(searchTerm)) {
                return true;
              }
            }
          }

          if (Array.isArray(res.steps)) {
            for (const st of res.steps) {
              const stepText = `${st.name || ''} ${st.call || ''} ${st.on || ''}`.toLowerCase();
              if (stepText.includes(searchTerm)) return true;
            }
          }
        }
      }

      const stackTraces = tNode.commonStackTraces || node.commonStackTraces || [];
      for (const group of stackTraces) {
        if (!group || !group.stackTrace) continue;
        let matchesActiveFilter = false;
        for (const [suite, variants] of Object.entries(group.occurrences || {})) {
          if (activeSuite !== 'all' && suite !== activeSuite) continue;
          if (variants.some(v => activeVariants.includes(v))) {
            matchesActiveFilter = true;
            break;
          }
        }
        if (matchesActiveFilter && group.stackTrace.toLowerCase().includes(searchTerm)) {
          return true;
        }
      }
    }

    return false;
  },

  // --- DATA PROCESSING & FILTERING ---
  getFilteredAndSortedData() {
    if (!this.processedData) return { modules: [] };

    const richLayout = this.usesRichSingleSuiteLayout();
    const activeAttrFilters = richLayout
      ? Object.entries(this.state.filters.suiteAttributes || {}).filter(([, val]) => val && val !== 'all')
      : [];
    const hasSuiteAttrFilters = activeAttrFilters.length > 0;
    const hasSearch = !!this.state.filters.search;
    const searchTerm = hasSearch ? this.state.filters.search.toLowerCase() : '';
    const hasDropdownFilters = this.state.filters.modules.length > 0 ||
      this.state.filters.packages.length > 0 ||
      this.state.filters.classes.length > 0 ||
      this.state.filters.testCases.length > 0 ||
      (this.state.filters.targets && this.state.filters.targets.length > 0);
    const hasStatusFilters = this.state.filters.status.length < ALL_TEST_STATUSES.length;
    const hasTestSuiteFilter = this.state.filters.testSuite !== 'all';
    const isFiltering = hasSearch || hasDropdownFilters || hasStatusFilters || hasTestSuiteFilter || hasSuiteAttrFilters;

    const filterAndSort = (nodes, type, parentMatchesSearch = false) => {
      if (!Array.isArray(nodes) || nodes.length === 0) return [];
      const childType = this.getChildType(type);
      const isLeaf = !childType;
      const childKey = childType ? (type === 'class' ? 'testCases' : this.pluralize(childType)) : null;
      const result = [];

      for (const node of nodes) {
        // Fast paths: discard outright if missing explicit filters.
        if (type === 'module' && this.state.filters.modules.length > 0 && !this.state.filters.modules.includes(node.name)) continue;
        if (type === 'package' && this.state.filters.packages.length > 0 && !this.state.filters.packages.includes(node.name)) continue;
        if (type === 'class' && this.state.filters.classes.length > 0 && !this.state.filters.classes.includes(node.name)) continue;
        if (type === 'testCase' && this.state.filters.testCases.length > 0 && !this.state.filters.testCases.includes(node.name)) continue;
        if (type === 'target' && this.state.filters.targets && this.state.filters.targets.length > 0 && !this.state.filters.targets.includes(node.name)) continue;

        // Drill-down selection filters (Flat view only)
        if (this.state.viewMode === 'flat') {
          if (type === 'module' && this.state.selectedModule && this.state.selectedModule !== node.name) continue;
          if (type === 'package' && this.state.selectedPackage && this.state.selectedPackage !== node.name) continue;
          if (type === 'class' && this.state.selectedClass && this.state.selectedClass !== node.name) continue;
        }

        // Search filter
        let selfMatchesSearch = false;
        if (hasSearch && !(isLeaf && richLayout)) {
          selfMatchesSearch = (node.name || '').toLowerCase().includes(searchTerm);
        }
        const effectiveMatchesSearch = hasSearch ? (selfMatchesSearch || parentMatchesSearch) : false;

        let rawChildren = null;
        let filteredChildren = null;
        let hasVisibleChildren = false;
        if (!isLeaf) {
          rawChildren = node[childKey] || (type === 'class' ? node.testCases : []) || [];
          filteredChildren = filterAndSort(rawChildren, childType, effectiveMatchesSearch);
          hasVisibleChildren = filteredChildren.length > 0;
        }

        // Test Suite Filter
        if (hasTestSuiteFilter && type !== 'testCase' && type !== 'target') {
          if (node.testSuiteSummaries) {
            const suiteMatch = node.testSuiteSummaries.find(ts => ts.name === this.state.filters.testSuite);
            if (!suiteMatch) continue;
          } else {
            continue;
          }
        }

        if (richLayout && isLeaf) {
          const activeVariant = this.state.filters.variants[0];
          const res = this.getVariantResultForTestCase(node, this.state.filters.testSuite, activeVariant);
          if (!this.matchesRichLeafFilters(node, res, { activeAttrFilters, searchTerm, parentMatchesSearch })) {
            continue;
          }
        }

        if (hasStatusFilters && isLeaf) {
          let hasFail = false;
          let hasPass = false;
          let hasSkipped = false;

          this.state.filters.variants.forEach(v => {
            const res = this.getVariantResultForTestCase(node, this.state.filters.testSuite, v);
            if (res) {
              const norm = UIUtils.normalizeStatus(res.status);
              if (norm === TEST_STATUS.FAIL) hasFail = true;
              if (norm === TEST_STATUS.PASS) hasPass = true;
              if (norm === TEST_STATUS.SKIPPED) hasSkipped = true;
            }
          });

          let matchesStatus = false;
          if (hasPass && this.state.filters.status.includes(TEST_STATUS.PASSED)) matchesStatus = true;
          if (hasFail && this.state.filters.status.includes(TEST_STATUS.FAILED)) matchesStatus = true;
          if (hasSkipped && this.state.filters.status.includes(TEST_STATUS.SKIPPED)) matchesStatus = true;

          if (!matchesStatus) continue;
        }

        if (isFiltering) {
          if (isLeaf) {
            if (!richLayout && hasSearch && !effectiveMatchesSearch) continue;
          } else if (!hasVisibleChildren && ((richLayout && type === 'testCase' && rawChildren.length > 0) || !effectiveMatchesSearch)) {
            // Rich multi-target views render one row per visible target, so drop test cases whose targets were all filtered out.
            continue;
          }
        }

        if (isLeaf) {
          result.push({ ...node, type });
        } else {
          result.push({ ...node, type, [childKey]: filteredChildren });
        }
      }

      result.sort((a, b) => this.compareNodes(a, b));
      return result;
    };

    return {
      ...this.processedData,
      modules: filterAndSort(this.processedData.modules || [], 'module'),
    };
  },

  /**
   * Iterates leaf test case runs matching the current hierarchy selection, suite, and variants.
   */
  forEachScopedLeafRun(callback) {
    if (!this.processedData || !this.processedData.modules) return;
    const activeSuite = this.state.filters.testSuite;
    const activeVariants = this.state.filters.variants || [];
    const { modules: modFilter, packages: pkgFilter, classes: clsFilter, testCases: tcFilter, targets: tgtFilter } = this.state.filters;

    for (const m of this.processedData.modules) {
      if (modFilter.length > 0 && !modFilter.includes(m.name)) continue;
      if (this.state.viewMode === 'flat' && this.state.selectedModule && this.state.selectedModule !== m.name) continue;

      for (const p of (m.packages || [])) {
        if (pkgFilter.length > 0 && !pkgFilter.includes(p.name)) continue;
        if (this.state.viewMode === 'flat' && this.state.selectedPackage && this.state.selectedPackage !== p.name) continue;

        for (const c of (p.classes || [])) {
          if (clsFilter.length > 0 && !clsFilter.includes(c.name)) continue;
          if (this.state.viewMode === 'flat' && this.state.selectedClass && this.state.selectedClass !== c.name) continue;

          for (const tc of (c.testCases || [])) {
            if (tcFilter.length > 0 && !tcFilter.includes(tc.name)) continue;

            if (tc.targets && tc.targets.length > 0 && !this.hasOnlyDefaultTarget()) {
              for (const tgt of tc.targets) {
                if (tgtFilter && tgtFilter.length > 0 && !tgtFilter.includes(tgt.name)) continue;
                const scopedNode = { ...tc, target: tgt };
                callback(scopedNode, m, p, c, activeSuite, activeVariants);
              }
            } else {
              callback(tc, m, p, c, activeSuite, activeVariants);
            }
          }
        }
      }
    }
  },

  /**
   * Computes pass, fail, skipped, and duration totals across runs matching the active suite,
   * variants, hierarchy selection, attribute filters, and search query, independent of `filters.status`.
   */
  computeActiveSummaryStats() {
    let passed = 0;
    let failed = 0;
    let skipped = 0;
    let durationMs = 0;
    let hasAnyDuration = false;

    const activeSuite = this.state.filters.testSuite;
    const activeVariants = this.state.filters.variants || [];
    const richLayout = this.usesRichSingleSuiteLayout();
    const searchTerm = (this.state.filters.search || '').toLowerCase();
    const activeAttrFilters = richLayout
      ? Object.entries(this.state.filters.suiteAttributes || {}).filter(([, val]) => val && val !== 'all')
      : [];

    this.forEachScopedLeafRun((leafNode, m, p, c) => {
      if (
        activeSuite !== 'all' &&
        !richLayout &&
        (!c.testSuiteSummaries || !c.testSuiteSummaries.some(ts => ts.name === activeSuite))
      ) {
        return;
      }
      const parentMatchesSearch = Boolean(
        searchTerm && (
          (m.name || '').toLowerCase().includes(searchTerm) ||
          (p.name || '').toLowerCase().includes(searchTerm) ||
          (c.name || '').toLowerCase().includes(searchTerm)
        )
      );
      if (!richLayout && searchTerm && !parentMatchesSearch && !(leafNode.name || '').toLowerCase().includes(searchTerm)) {
        return;
      }

      for (const v of activeVariants) {
        const res = this.getVariantResultForTestCase(leafNode, activeSuite, v);
        if (!res) continue;
        if (richLayout && !this.matchesRichLeafFilters(leafNode, res, { activeAttrFilters, searchTerm, parentMatchesSearch })) {
          continue;
        }
        const norm = UIUtils.normalizeStatus(res.status);
        if (norm === TEST_STATUS.PASS) passed++;
        else if (norm === TEST_STATUS.FAIL) failed++;
        else if (norm === TEST_STATUS.SKIPPED) skipped++;

        const leafDur = this.getNodeVariantDurationMs(leafNode, v);
        if (leafDur !== null && leafDur !== undefined && !Number.isNaN(Number(leafDur))) {
          durationMs += Number(leafDur);
          hasAnyDuration = true;
        }
      }
    });

    const total = passed + failed + skipped;
    return {
      passed,
      failed,
      skipped,
      total,
      durationMs: hasAnyDuration ? durationMs : null,
    };
  },

  syncOptionalClass(el, cls, active) {
    if (!el) return;
    if (active) {
      el.classList.add(cls);
    } else if (el.classList.contains(cls)) {
      el.classList.remove(cls);
    }
  },

  countActiveSuiteModules() {
    const allModules = (this.processedData && this.processedData.modules) || [];
    const activeSuite = this.state.filters && this.state.filters.testSuite;
    if (!activeSuite || activeSuite === 'all' || activeSuite === AGGREGATED_SUITE_NAME) {
      return allModules.length;
    }
    return allModules.filter(m =>
      Array.isArray(m && m.testSuiteSummaries) &&
      m.testSuiteSummaries.some(ts => ts && ts.name === activeSuite)
    ).length;
  },

  /**
   * Renders header stats, suite environment bar, status tabs, and dynamic suite attribute filters.
   */
  renderHeaderAndToolbar() {
    const singleMode = this.usesRichSingleSuiteLayout();
    const isDetailView = this.state.currentView === 'stack-trace';
    const isRichDetail = isDetailView && this.state.detailViewMode === 'rich';
    this.syncOptionalClass(document.body, 'rich-suite-active', singleMode || isRichDetail);
    const headerToolbar = this.elements.reportViewControls ? this.elements.reportViewControls.parentElement : null;
    this.syncOptionalClass(headerToolbar, 'hidden', isRichDetail);

    if (this.elements.reportViewControls) {
      this.syncOptionalClass(this.elements.reportViewControls, 'rich-suite-toolbar', singleMode);
      const isSingleVar = (this.state.variants || []).length <= 1;
      const isSingleMod = this.countActiveSuiteModules() <= 1;
      this.syncOptionalClass(this.elements.reportViewControls, 'rich-single-variant', singleMode && isSingleVar);
      this.syncOptionalClass(this.elements.reportViewControls, 'rich-single-module', singleMode && isSingleMod);
    }

    if (singleMode) {
      const stats = this.computeActiveSummaryStats();
      if (this.elements.headerStatsMulti) this.elements.headerStatsMulti.classList.add('hidden');
      if (this.elements.headerStatsSingle) {
        this.elements.headerStatsSingle.classList.remove('hidden');
        const relevant = stats.passed + stats.failed;
        const rate = relevant > 0 ? (stats.passed / relevant) * 100 : null;
        let rateText = '—';
        let rateTone = '';
        if (rate !== null) {
          rateText = Math.abs(rate - Math.round(rate)) < 0.05 ? `${Math.round(rate)}%` : `${rate.toFixed(1)}%`;
          if (stats.failed === 0 && stats.passed > 0) {
            rateTone = 'stat-tone-green';
          } else if (rate >= 80) {
            rateTone = 'stat-tone-amber';
          } else {
            rateTone = 'stat-tone-red';
          }
        }
        const failTone = stats.failed > 0 ? 'stat-tone-red' : '';
        this.elements.headerStatsSingle.innerHTML = `
          <div class="stat-item ${rateTone}">
            <span class="stat-label" data-info-key="stat.passRate" tabindex="0">PASS RATE</span>
            <span class="stat-value num" id="single-pass-rate">${UIUtils.escapeHTML(rateText)}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label" data-info-key="stat.tests" tabindex="0">TESTS</span>
            <span class="stat-value num" id="single-total-tests">${UIUtils.escapeHTML(stats.total)}</span>
          </div>
          <div class="stat-item ${failTone}">
            <span class="stat-label" data-info-key="stat.failed" tabindex="0">FAILED</span>
            <span class="stat-value num" id="single-failed-tests">${UIUtils.escapeHTML(stats.failed)}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label" data-info-key="stat.duration" tabindex="0">DURATION</span>
            <span class="stat-value num" id="single-total-duration">${UIUtils.escapeHTML(UIUtils.formatDuration(stats.durationMs))}</span>
          </div>
        `;
      }

      if (isDetailView) {
        if (this.elements.suiteEnvBar) this.elements.suiteEnvBar.classList.add('hidden');
        if (this.elements.statusSegments) this.elements.statusSegments.classList.add('hidden');
        if (this.elements.suiteAttrFilters) this.elements.suiteAttrFilters.classList.add('hidden');
      } else {
        this.renderSuiteEnvBar();
        this.renderStatusTabs(stats);
        this.renderSuiteAttributeFilters();
      }

      if (this.elements.searchWrapper) {
        this.elements.searchWrapper.classList.add('always-expanded', 'expanded');
      }
      if (this.elements.searchRevealBtn) {
        this.elements.searchRevealBtn.classList.add('hidden');
      }
      if (this.elements.searchInput) {
        this.elements.searchInput.placeholder = 'Search tests…';
      }
    } else {
      if (this.elements.headerStatsSingle) {
        this.elements.headerStatsSingle.classList.add('hidden');
        this.elements.headerStatsSingle.innerHTML = '';
      }
      if (this.elements.headerStatsMulti) this.elements.headerStatsMulti.classList.remove('hidden');
      if (this.elements.suiteEnvBar) {
        this.elements.suiteEnvBar.classList.add('hidden');
        this.elements.suiteEnvBar.innerHTML = '';
      }
      if (this.elements.statusSegments) {
        this.elements.statusSegments.classList.add('hidden');
        this.elements.statusSegments.innerHTML = '';
      }
      if (this.elements.suiteAttrFilters) {
        this.elements.suiteAttrFilters.classList.add('hidden');
        this.elements.suiteAttrFilters.innerHTML = '';
      }
      this._dynamicSuiteDropdowns = [];

      // Leaving the rich layout restores the collapsible search box.
      if (this.elements.searchWrapper && this.elements.searchWrapper.classList.contains('always-expanded')) {
        this.elements.searchWrapper.classList.remove('always-expanded');
        if (!this.state.filters.search && document.activeElement !== this.elements.searchInput) {
          this.elements.searchWrapper.classList.remove('expanded');
          if (this.elements.searchRevealBtn) {
            this.elements.searchRevealBtn.classList.remove('hidden');
          }
        }
        if (this.elements.searchInput) {
          this.elements.searchInput.placeholder = 'Search report...';
        }
      }
    }
  },

  /**
   * Renders the single-suite environment metadata sub-header bar.
   */
  renderSuiteEnvBar() {
    const bar = this.elements.suiteEnvBar;
    if (!bar) return;

    const suiteName = this.state.filters.testSuite;
    const variantName = this.state.filters.variants[0];
    const env = this.getMatchingSuiteEnvironment(suiteName, variantName, this.state.selectedModule);
    const desc = (this.state.suiteDescriptors || {})[suiteName];
    const safeDocsUrl = desc && desc.docsUrl ? UIUtils.sanitizeUrl(desc.docsUrl, true) : '';

    const items = [];
    if (env) {
      if (env.applicationName || env.applicationId) {
        const appDisplay = env.applicationName && env.applicationId
          ? `${UIUtils.escapeHTML(env.applicationName)} <span class="font-mono text-gray-600">(${UIUtils.escapeHTML(env.applicationId)})</span>`
          : `<span class="${env.applicationName ? '' : 'font-mono'}">${UIUtils.escapeHTML(env.applicationName || env.applicationId)}</span>`;
        items.push(`<span class="suite-env-item"><span class="suite-env-label">App</span><span class="suite-env-value">${appDisplay}</span></span>`);
      }
      if (env.versionName || (env.versionCode !== null && env.versionCode !== undefined)) {
        const ver = env.versionName && env.versionCode !== null && env.versionCode !== undefined
          ? `${env.versionName} (${env.versionCode})`
          : (env.versionName || String(env.versionCode));
        items.push(`<span class="suite-env-item"><span class="suite-env-label">Version</span><span class="suite-env-value num">${UIUtils.escapeHTML(ver)}</span></span>`);
      }
      const vName = env.variantName || variantName;
      if (vName) {
        items.push(`<span class="suite-env-item"><span class="suite-env-label">Variant</span><span class="suite-env-value">${UIUtils.escapeHTML(vName)}</span></span>`);
      }
      if (env.agpVersion) {
        items.push(`<span class="suite-env-item"><span class="suite-env-label">AGP</span><span class="suite-env-value num">${UIUtils.escapeHTML(env.agpVersion)}</span></span>`);
      }
      if (env.gradleVersion) {
        items.push(`<span class="suite-env-item"><span class="suite-env-label">Gradle</span><span class="suite-env-value num">${UIUtils.escapeHTML(env.gradleVersion)}</span></span>`);
      }
      if (env.testLibraryVersion) {
        items.push(`<span class="suite-env-item"><span class="suite-env-label">Test library</span><span class="suite-env-value num">${UIUtils.escapeHTML(env.testLibraryVersion)}</span></span>`);
      }
      if (env.runner) {
        items.push(`<span class="suite-env-item"><span class="suite-env-label">Runner</span><span class="suite-env-value font-mono">${UIUtils.escapeHTML(env.runner)}</span></span>`);
      }
    }

    const plugin = this.resolveSuitePlugin(suiteName);
    const hasPluginBarExtras = Boolean(plugin.hasSuiteEnvBarExtras(env, this));

    if (items.length === 0 && !safeDocsUrl && !hasPluginBarExtras) {
      bar.classList.add('hidden');
      bar.innerHTML = '';
      return;
    }

    const joinedItems = items.join('<span class="suite-env-sep" aria-hidden="true">·</span>');
    const docsHtml = safeDocsUrl
      ? `<a class="suite-env-docs-link" href="${UIUtils.escapeHTML(safeDocsUrl)}" target="_blank" rel="noopener noreferrer">Docs <span aria-hidden="true">↗</span></a>`
      : '';

    bar.innerHTML = `
      <div class="suite-env-bar-inner">
        <div class="suite-env-items">${joinedItems}</div>
        <div class="suite-env-actions">${docsHtml}</div>
      </div>
    `;
    if (hasPluginBarExtras) {
      plugin.mountSuiteEnvBarExtras(bar, env, desc, this, UIUtils);
    }
    bar.classList.remove('hidden');
  },

  /**
   * Renders the status segmented tabs (All, Failed, Passed, and Skipped when > 0) with live counts.
   */
  renderStatusTabs(stats = null) {
    const container = this.elements.statusSegments;
    if (!container) return;

    const { passed, failed, skipped, total: allCount } = stats || this.computeActiveSummaryStats();
    const statusFilter = this.state.filters.status || [];
    const isAllActive = statusFilter.length === ALL_TEST_STATUSES.length;
    const isFailedActive = statusFilter.length === 1 && statusFilter[0] === TEST_STATUS.FAILED;
    const isPassedActive = statusFilter.length === 1 && statusFilter[0] === TEST_STATUS.PASSED;
    const isSkippedActive = statusFilter.length === 1 && statusFilter[0] === TEST_STATUS.SKIPPED;

    const tabs = [
      { id: 'all', label: 'All', count: allCount, active: isAllActive },
      { id: 'failed', label: 'Failed', count: failed, active: isFailedActive },
      { id: 'passed', label: 'Passed', count: passed, active: isPassedActive },
    ];
    if (skipped > 0 || isSkippedActive) {
      tabs.push({ id: 'skipped', label: 'Skipped', count: skipped, active: isSkippedActive });
    }

    container.innerHTML = tabs.map(t => `
      <button type="button" class="segment-btn ${t.active ? 'active' : ''}" data-status-tab="${t.id}" aria-pressed="${t.active ? 'true' : 'false'}">
        <span>${UIUtils.escapeHTML(t.label)}</span>
        <span class="segment-count">${UIUtils.escapeHTML(t.count)}</span>
      </button>
    `).join('');
    container.classList.remove('hidden');
  },

  /**
   * Renders dynamic dropdown filter menus with live counts for filterable suite summary columns.
   */
  renderSuiteAttributeFilters() {
    const container = this.elements.suiteAttrFilters;
    if (!container) return;

    const columns = this.getActiveSummaryColumns().filter(col => Boolean(col && col.filterable));
    this._dynamicSuiteDropdowns = [];
    if (columns.length === 0) {
      container.classList.add('hidden');
      container.innerHTML = '';
      return;
    }

    const suiteName = this.state.filters.testSuite;
    const variantName = this.state.filters.variants[0];

    container.innerHTML = '';
    columns.forEach(col => {
      const colLabel = col.label || col.key;
      const valueCounts = new Map();
      const valueLabels = new Map();
      let totalWithAttr = 0;

      this.forEachScopedLeafRun((leafNode) => {
        const res = this.getVariantResultForTestCase(leafNode, suiteName, variantName);
        if (!res) return;
        const attr = this.getSummaryAttr(res, col.key);
        if (!attr || (!attr.value && !attr.displayValue)) return;
        const valKey = attr.value || attr.displayValue;
        totalWithAttr++;
        valueCounts.set(valKey, (valueCounts.get(valKey) || 0) + 1);
        if (!valueLabels.has(valKey)) {
          valueLabels.set(valKey, attr.displayValue || valKey);
        }
      });

      const currentSelected = (this.state.filters.suiteAttributes || {})[col.key] || 'all';
      if (valueCounts.size === 0 && currentSelected === 'all') {
        return;
      }

      const wrapper = document.createElement('div');
      wrapper.className = 'relative';

      const selectedLabel = currentSelected === 'all'
        ? 'All'
        : (valueLabels.get(currentSelected) || currentSelected);

      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `dropdown-btn ${currentSelected !== 'all' ? 'has-selection' : ''}`;
      btn.dataset.attrFilterBtn = col.key;
      btn.setAttribute('aria-haspopup', 'listbox');
      btn.setAttribute('aria-expanded', 'false');
      btn.innerHTML = `
        <span class="dropdown-btn-label">${UIUtils.escapeHTML(colLabel)}:</span>
        <span class="dropdown-btn-value">${UIUtils.escapeHTML(selectedLabel)}</span>
        <svg aria-hidden="true" focusable="false" xmlns="http://www.w3.org/2000/svg" width="14" height="14" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" class="dropdown-caret">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      `;

      const dropdown = document.createElement('div');
      dropdown.className = 'dropdown-menu p-1 hidden';
      dropdown.setAttribute('role', 'listbox');
      dropdown.setAttribute('aria-label', `${colLabel} filter options`);

      const options = [
        { value: 'all', label: `All ${colLabel.toLowerCase()}`, count: totalWithAttr },
        ...Array.from(valueCounts.entries()).map(([val, cnt]) => ({
          value: val,
          label: valueLabels.get(val) || val,
          count: cnt,
        })),
      ];

      options.forEach(opt => {
        const optBtn = document.createElement('button');
        optBtn.type = 'button';
        const isSelected = currentSelected === opt.value;
        optBtn.className = `popover-item justify-between rounded-md ${isSelected ? 'active-popover-item' : ''}`;
        optBtn.setAttribute('role', 'option');
        optBtn.setAttribute('aria-selected', isSelected ? 'true' : 'false');
        optBtn.dataset.attrValue = opt.value;
        optBtn.innerHTML = `
          <span>${UIUtils.escapeHTML(opt.label)}</span>
          <span class="dropdown-count">${UIUtils.escapeHTML(opt.count)}</span>
        `;
        optBtn.addEventListener('click', (e) => {
          e.preventDefault();
          e.stopPropagation();
          if (!this.state.filters.suiteAttributes) {
            this.state.filters.suiteAttributes = {};
          }
          if (opt.value === 'all') {
            delete this.state.filters.suiteAttributes[col.key];
          } else {
            this.state.filters.suiteAttributes[col.key] = opt.value;
          }
          dropdown.classList.add('hidden');
          btn.setAttribute('aria-expanded', 'false');
          this.render();
          Navigation.push();
          const newBtn = Array.from(
            this.elements.suiteAttrFilters?.querySelectorAll('button[data-attr-filter-btn]') || []
          ).find(b => b.dataset.attrFilterBtn === col.key);
          if (newBtn) newBtn.focus();
        });
        dropdown.appendChild(optBtn);
      });

      btn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.toggleDropdown(dropdown, btn);
      });
      this.bindDropdownKeyboardEvents(btn, dropdown);

      wrapper.appendChild(btn);
      wrapper.appendChild(dropdown);
      container.appendChild(wrapper);
      this._dynamicSuiteDropdowns.push({ btn, dropdown });
    });

    if (container.children.length > 0) {
      container.classList.remove('hidden');
    } else {
      container.classList.add('hidden');
    }
  },

  // --- RENDERING ---
  render() {
    this.updateDynamicFilters();

    if (this.state.currentView === 'stack-trace') {
      const wasRichDetail = this.state.detailViewMode === 'rich';
      const detailTarget = this.resolveDetailViewTarget(this.state.currentTestCase, this.state.currentStackTraceContext);
      this.state.detailViewMode = detailTarget.mode;
      this.renderHeaderAndToolbar();
      this.renderStackTraceGrid(this.state.currentTestCase, this.state.currentStackTraceContext, detailTarget);
      if (wasRichDetail || detailTarget.mode === 'rich') {
        // Entering or leaving the rich view changes the breadcrumbs and title.
        this.renderStackTraceBreadcrumbs(this.state.currentStackTraceContext || {});
        this.syncDetailViewTitleAndAnnounce(this.state.currentTestCase);
        return;
      }
    } else {
      const data = this.getFilteredAndSortedData();
      this.renderHeaderAndToolbar();
      this.renderTable(data);
      this.updateGroupByText();
      this.updateTooltipsForOverflow();
    }

    const visibleItemsCount = this.elements.resultsData.querySelectorAll('tr.table-row:not(.hidden)').length;
    this.announce(`Showing ${visibleItemsCount} results.`);
  },

  updateDynamicFilters() {
    const filters = this.state.filters;
    let contextModules = this.processedData.modules || [];

    if (filters.modules.length > 0) {
      contextModules = contextModules.filter(m => filters.modules.includes(m.name));
    }

    let basePackages = contextModules.flatMap(m => m.packages || []);
    let filteredPackages = basePackages;
    if (filters.packages.length > 0) {
      filteredPackages = basePackages.filter(p => filters.packages.includes(p.name));
    }

    let baseClasses = filteredPackages.flatMap(p => p.classes || []);
    let filteredClasses = baseClasses;
    if (filters.classes.length > 0) {
      filteredClasses = baseClasses.filter(c => filters.classes.includes(c.name));
    }

    const moduleOptions = (this.processedData.modules || []).map(m => ({ name: m.name, value: m.name }));
    const packageOptions = [...new Set(basePackages.map(p => p.name))].sort().map(name => ({ name, value: name }));
    const classOptions = [...new Set(filteredPackages.flatMap(p => p.classes || []).map(c => c.name))].sort().map(name => ({ name, value: name }));
    const testCaseOptions = [...new Set(filteredClasses.flatMap(c => c.testCases || []).map(tc => tc.name))].sort().map(name => ({ name, value: name }));
    const targetOptions = (this.processedData.targets || []).map(t => ({ name: t, value: t }));

    UIUtils.buildActionDropdown(this.elements.moduleFilterDropdown, moduleOptions, filters.modules, (newArr) => {
      filters.packages = [];
      filters.classes = [];
      filters.testCases = [];
      filters.targets = [];
      filters.modules = newArr;
      this.handleHeaderFilterChange('module');
      this.syncSortModeWithLayout();
      this.updateFilterButtons();
      this.render();
      Navigation.push();
    });

    UIUtils.buildActionDropdown(this.elements.packageFilterDropdown, packageOptions, filters.packages, (newArr) => {
      filters.classes = [];
      filters.testCases = [];
      filters.targets = [];
      filters.packages = newArr;
      this.handleHeaderFilterChange('package');
      this.syncSortModeWithLayout();
      this.updateFilterButtons();
      this.render();
      Navigation.push();
    });

    UIUtils.buildActionDropdown(this.elements.classFilterDropdown, classOptions, filters.classes, (newArr) => {
      filters.testCases = [];
      filters.targets = [];
      filters.classes = newArr;
      this.handleHeaderFilterChange('class');
      this.syncSortModeWithLayout();
      this.updateFilterButtons();
      this.render();
      Navigation.push();
    });

    UIUtils.buildActionDropdown(this.elements.tcFilterDropdown, testCaseOptions, filters.testCases, (newArr) => {
      filters.targets = [];
      filters.testCases = newArr;
      this.handleHeaderFilterChange('testCase');
      this.syncSortModeWithLayout();
      this.updateFilterButtons();
      this.render();
      Navigation.push();
    });

    UIUtils.buildActionDropdown(this.elements.targetFilterDropdown, targetOptions, filters.targets, (newArr) => {
      filters.targets = newArr;
      this.handleHeaderFilterChange('target');
      this.syncSortModeWithLayout();
      this.updateFilterButtons();
      this.render();
      Navigation.push();
    });
  },

  updateGroupByText() {
    if (!this.elements.groupByText) return;
    const viewMap = {
      'modules': 'Modules',
      'packages': 'Packages',
      'classes': 'Classes',
      'testCases': 'Test Cases'
    };
    this.elements.groupByText.textContent = viewMap[this.state.currentFlatView] || 'Test Cases';

    if (this.elements.groupByDropdown) {
      this.elements.groupByDropdown.querySelectorAll('.dropdown-item').forEach(item => {
        if (item.dataset.value === this.state.currentFlatView) {
          item.classList.add('active-popover-item');
          item.setAttribute('aria-selected', 'true');
        } else {
          item.classList.remove('active-popover-item');
          item.setAttribute('aria-selected', 'false');
        }
      });
    }

    if (this.elements.groupByBtn) {
      this.elements.groupByBtn.parentElement.style.display = this.state.viewMode === 'flat' ? 'block' : 'none';
    }
  },

  renderTable(data) {
    const isRichTable = this.isSingleSuiteTestList();
    this.syncOptionalClass(this.elements.mainTable, 'single-suite-table', isRichTable);
    const tableContainer = this.elements.mainTable ? this.elements.mainTable.parentElement : null;
    this.syncOptionalClass(tableContainer, 'rich-table-container', isRichTable);
    const breadcrumbRow = this.elements.breadcrumbs ? this.elements.breadcrumbs.closest('.breadcrumb-row-style') : null;
    const isSingleModule = this.countActiveSuiteModules() <= 1;
    this.syncOptionalClass(breadcrumbRow, 'rich-hide-breadcrumb-row', isRichTable && isSingleModule);

    this.renderHeaders();
    this.renderBreadcrumbs();
    if (this.state.viewMode === 'tree') {
      this.renderTreeRows(data);
    } else {
      this.renderFlatRows(data);
    }
  },

  renderHeaders() {
    const sortIndicator = (key) => this.isSortActive(key) ? (this.state.sort.order === 'asc' ? '▲' : '▼') : '';
    const richSortArrow = (key) => this.isSortActive(key)
      ? `<i class="rich-sort-arrow active" aria-hidden="true">${this.state.sort.order === 'asc' ? '▴' : '▾'}</i>`
      : `<i class="rich-sort-arrow" aria-hidden="true">▾</i>`;
    const getAriaSort = (key) => this.isSortActive(key) ? (this.state.sort.order === 'asc' ? 'ascending' : 'descending') : 'none';
    const nameWidth = Math.round(this.state.columnWidths['name'] || 400);

    if (this.isSingleSuiteTestList()) {
      const summaryColumns = this.getActiveSummaryColumns();
      let targetTh = '';
      if (!this.hasOnlyDefaultTarget()) {
        targetTh = `<th scope="col" class="text-left col-target cursor-pointer" data-sort-by="target" tabindex="0" aria-sort="${getAriaSort('target')}"><span data-info-key="col.target">Target</span> ${richSortArrow('target')}</th>`;
      }

      const dynamicColsHtml = summaryColumns.map(col => {
        const colLabel = col.label || col.key;
        const sortKey = `attr:${col.key}`;
        const colCls = this.getSummaryColumnClass(col);
        const labelSpan = col.tooltip
          ? `<span data-info-key="col.summary" data-info-arg="${UIUtils.escapeHTML(col.key)}">${UIUtils.escapeHTML(colLabel)}</span>`
          : `<span>${UIUtils.escapeHTML(colLabel)}</span>`;
        if (col.sortable === false) {
          return `<th scope="col" class="text-left ${colCls}">${labelSpan}</th>`;
        }
        return `<th scope="col" class="text-left ${colCls} cursor-pointer" data-sort-by="${UIUtils.escapeHTML(sortKey)}" tabindex="0" aria-sort="${getAriaSort(sortKey)}">${labelSpan} ${richSortArrow(sortKey)}</th>`;
      }).join('');

      this.elements.tableHeaders.innerHTML = `
        <tr class="single-suite-header-row">
          <th scope="col" class="text-left col-test cursor-pointer" data-sort-by="name" tabindex="0" aria-sort="${getAriaSort('name')}"><span data-info-key="col.richTestCases">Test</span> ${richSortArrow('name')}</th>
          ${targetTh}
          ${dynamicColsHtml}
          <th scope="col" class="text-left col-status cursor-pointer" data-sort-by="status" tabindex="0" aria-sort="${getAriaSort('status')}"><span data-info-key="col.status">Status</span> ${richSortArrow('status')}</th>
          <th scope="col" class="text-left col-duration cursor-pointer" data-sort-by="duration" tabindex="0" aria-sort="${getAriaSort('duration')}"><span data-info-key="col.duration">Duration</span> ${richSortArrow('duration')}</th>
        </tr>`;
      return;
    }

    const variantsToShow = this.state.filters.variants;
    let nameHeader = this.state.viewMode === 'tree' ? 'Name' : (this.state.currentFlatView === 'testCases' ? 'Test Case' : this.state.currentFlatView.charAt(0).toUpperCase() + this.state.currentFlatView.slice(1));
    // Contextual info tooltip for the name column
    const nameInfoKey = this.state.viewMode === 'tree'
      ? 'col.project'
      : ({ modules: 'col.modules', packages: 'col.packages', classes: 'col.classes', testCases: 'col.testCases' }[this.state.currentFlatView] || 'col.project');

    let pathHeader = '';
    let pathSubHeader = '';
    if (this.state.viewMode === 'flat' && !this.state.selectedModule) {
      if (this.state.currentFlatView === 'classes' || this.state.currentFlatView === 'testCases') {
        const pathWidth = Math.round(this.state.columnWidths['path'] || 300);
        pathHeader = `<th scope="col" class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30 col-path"><span data-info-key="col.path">Path</span><div class="resizer" data-resizer-id="path" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${pathWidth}"></div></th>`;
        pathSubHeader = `<th scope="col" class="py-2 px-6 bg-gray-50 z-30 col-path"></th>`;
      } else if (this.state.currentFlatView === 'packages') {
        const moduleWidth = Math.round(this.state.columnWidths['module'] || 200);
        pathHeader = `<th scope="col" class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30 col-module"><span data-info-key="col.module">Module</span><div class="resizer" data-resizer-id="module" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${moduleWidth}"></div></th>`;
        pathSubHeader = `<th scope="col" class="py-2 px-6 bg-gray-50 z-30 col-module"></th>`;
      }
    }

    let targetHeader = '';
    let targetSubHeader = '';
    if (this.state.viewMode === 'flat' && this.state.currentFlatView === 'testCases' && !this.hasOnlyDefaultTarget()) {
      const targetWidth = Math.round(this.state.columnWidths['target'] || 150);
      targetHeader = `<th scope="col" class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30 col-target"><span data-info-key="col.target">Target</span><div class="resizer" data-resizer-id="target" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${targetWidth}"></div></th>`;
      targetSubHeader = `<th scope="col" class="py-2 px-6 bg-gray-50 z-30 col-target"></th>`;
    }

    this.elements.tableHeaders.innerHTML = `
            <tr class="border-b border-gray-200">
                <th scope="col" class="py-4 px-6 text-left font-semibold text-gray-700 sticky-name bg-gray-50 z-30 cursor-pointer" data-sort-by="name" tabindex="0" aria-sort="${getAriaSort('name')}"><span data-info-key="${nameInfoKey}">${nameHeader}</span> ${sortIndicator('name')}<div class="resizer" data-resizer-id="name" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${nameWidth}"></div></th>
                ${targetHeader}
                ${pathHeader}
                ${variantsToShow.map(v => `<th scope="col" class="py-4 px-4 text-center font-semibold text-gray-700 border-l border-gray-200" colspan="4">${UIUtils.escapeHTML(v)}</th>`).join('')}
            </tr>
            <tr class="border-b border-gray-200">
                <th scope="col" class="py-2 px-6 sticky-name bg-gray-50 z-30"></th>
                ${targetSubHeader}
                ${pathSubHeader}
                ${variantsToShow.map(v => {
                  const passKey = `${v}.pass`;
                  const failKey = `${v}.fail`;
                  const skipKey = `${v}.skip`;
                  const rateKey = `${v}.rate`;
                  const escV = UIUtils.escapeHTML(v);
                  const escPassKey = UIUtils.escapeHTML(passKey);
                  const escFailKey = UIUtils.escapeHTML(failKey);
                  const escSkipKey = UIUtils.escapeHTML(skipKey);
                  const escRateKey = UIUtils.escapeHTML(rateKey);
                  return `<th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600 border-l border-gray-200 cursor-pointer" tabindex="0" data-sort-by="${escPassKey}" aria-sort="${getAriaSort(passKey)}" aria-label="Sort by Pass for ${escV}"><span data-info-key="col.pass">Pass</span> ${sortIndicator(passKey)}</th>` +
                         `<th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600 cursor-pointer" tabindex="0" data-sort-by="${escFailKey}" aria-sort="${getAriaSort(failKey)}" aria-label="Sort by Fail for ${escV}"><span data-info-key="col.fail">Fail</span> ${sortIndicator(failKey)}</th>` +
                         `<th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600 cursor-pointer" tabindex="0" data-sort-by="${escSkipKey}" aria-sort="${getAriaSort(skipKey)}" aria-label="Sort by Skip for ${escV}"><span data-info-key="col.skip">Skip</span> ${sortIndicator(skipKey)}</th>` +
                         `<th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600 cursor-pointer" tabindex="0" data-sort-by="${escRateKey}" aria-sort="${getAriaSort(rateKey)}" aria-label="Sort by Pass Rate for ${escV}"><span data-info-key="col.passRate">Pass Rate</span> ${sortIndicator(rateKey)}</th>`;
                }).join('')}
            </tr>`;
  },

  renderBreadcrumbs() {
    if (this.state.viewMode !== 'flat') {
      this.elements.breadcrumbs.innerHTML = '';
      return;
    }
    const { selectedModule, selectedPackage, selectedClass } = this.state;
    let html = '';

    // "Project" is the root link
    if (selectedModule) {
      html += `<a href="#" class="breadcrumb-link" data-action="${BREADCRUMB_ACTIONS.GO_TO_MODULES}" aria-label="Go back to Project Overview">Project</a>`;
    } else {
      html += `<span class="breadcrumb-current">Project</span>`;
    }

    // Module level
    if (selectedModule) {
      html += `<span class="breadcrumb-separator" aria-hidden="true">/</span>`;
      if (selectedPackage) {
        html += `<a href="#" class="breadcrumb-link" data-action="${BREADCRUMB_ACTIONS.GO_TO_PACKAGES}" aria-label="Go back to module: ${UIUtils.escapeHTML(selectedModule)}">${UIUtils.escapeHTML(selectedModule)}</a>`;
      } else {
        html += `<span class="breadcrumb-current">${UIUtils.escapeHTML(selectedModule)}</span>`;
      }
    }

    // Package level
    if (selectedPackage) {
      html += `<span class="breadcrumb-separator" aria-hidden="true">/</span>`;
      if (selectedClass) {
        html += `<a href="#" class="breadcrumb-link" data-action="${BREADCRUMB_ACTIONS.GO_TO_CLASSES}" aria-label="Go back to package: ${UIUtils.escapeHTML(selectedPackage)}">${UIUtils.escapeHTML(selectedPackage)}</a>`;
      } else {
        html += `<span class="breadcrumb-current">${UIUtils.escapeHTML(selectedPackage)}</span>`;
      }
    }

    // Class level
    if (selectedClass) {
      html += `<span class="breadcrumb-separator" aria-hidden="true">/</span>`;
      html += `<span class="breadcrumb-current">${UIUtils.escapeHTML(selectedClass)}</span>`;
    }

    this.elements.breadcrumbs.innerHTML = html;
  },

  renderTreeRows(data) {
    let html = '';
    const renderNode = (node, parentId, level, context = {}) => {
      const type = node.type;
      const currentContext = { ...context };
      if (type === 'module') currentContext.moduleName = node.name;
      if (type === 'package') currentContext.packageName = node.name;
      if (type === 'class') currentContext.className = node.name;
      if (type === 'testCase') currentContext.testCaseName = node.name;

      const childType = this.getChildType(type);
      const childKey = this.pluralize(childType);
      const children = node[childKey] || (type === 'class' ? node.testCases : []) || [];
      const hasChildren = children.length > 0;
      const uniqueId = `${parentId}-${node.name}`.replace(/[^a-zA-Z0-9-_]/g, '');

      let nameContent = `<span class="font-medium">${UIUtils.escapeHTML(node.name)}</span>`;
      if (type === 'class') {
        nameContent += this.getClassSuiteBadges(node);
      } else if (type === 'testCase') {
        if (this.hasOnlyDefaultTarget() && this.isTestCaseClickable(node)) {
          nameContent = `<span class="font-medium text-blue-700 hover-underline cursor-pointer stack-trace-trigger" data-module="${UIUtils.escapeHTML(currentContext.moduleName || '')}" data-package="${UIUtils.escapeHTML(currentContext.packageName || '')}" data-class="${UIUtils.escapeHTML(currentContext.className || '')}" data-test-case="${UIUtils.escapeHTML(node.name || '')}" tabindex="0" role="button" aria-label="View details for ${UIUtils.escapeHTML(node.name)}">${UIUtils.escapeHTML(node.name)}</span>`;
        } else {
          nameContent = `<span class="font-medium text-gray-800">${UIUtils.escapeHTML(node.name)}</span>`;
        }
      } else if (type === 'target' && this.isTestCaseClickable(node)) {
        nameContent = `<span class="font-medium text-blue-700 hover-underline cursor-pointer stack-trace-trigger" data-module="${UIUtils.escapeHTML(currentContext.moduleName || '')}" data-package="${UIUtils.escapeHTML(currentContext.packageName || '')}" data-class="${UIUtils.escapeHTML(currentContext.className || '')}" data-test-case="${UIUtils.escapeHTML(currentContext.testCaseName || '')}" data-target="${UIUtils.escapeHTML(node.name || '')}" tabindex="0" role="button" aria-label="View details for ${UIUtils.escapeHTML(node.name)}">${UIUtils.escapeHTML(node.name)}</span>`;
      }

      const chevron = `<svg aria-hidden="true" focusable="false" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="collapsible-arrow ${!hasChildren ? 'invisible' : ''}"><path d="m9 18 6-6-6-6"></path></svg>`;

      const ariaExpanded = hasChildren ? 'aria-expanded="false"' : '';
      const roleAttr = hasChildren ? 'role="button"' : '';
      const tabindexAttr = hasChildren ? 'tabindex="0"' : '';
      const interactiveAttr = hasChildren ? 'data-interactive="tree" cursor-pointer' : '';

      html += `
                <tr class="table-row ${level > 0 ? 'hidden' : ''}" data-id="${uniqueId}" data-parent-id="${parentId}">
                    <td class="py-3 px-6 sticky-name ${hasChildren ? 'cursor-pointer' : ''}" title="${UIUtils.escapeHTML(node.name)}" ${tabindexAttr} ${ariaExpanded} ${roleAttr} ${hasChildren ? 'data-interactive="tree"' : ''}>
                        <div style="padding-left: ${level * 1.0}rem; display: flex; align-items: center; width: 100%;">
                            ${chevron} ${nameContent}
                        </div>
                    </td>
                    ${this._renderStatusCell(node, currentContext)}
                </tr>`;

      if (hasChildren) {
        children.forEach(child => renderNode(child, uniqueId, level + 1, currentContext));
      }
    };

    if (data.modules) data.modules.forEach(module => renderNode(module, 'root', 0));
    this.elements.resultsData.innerHTML = html || '<tr><td colspan="100%" class="text-center text-gray-500" style="padding: 2rem;">No items match the current filters.</td></tr>';
  },

  /**
   * Formats a summary attribute cell value for display in the single-suite table.
   */
  formatSummaryAttrCellHTML(res, col) {
    const attr = this.getSummaryAttr(res, col.key);
    if (!attr || (!attr.displayValue && !attr.value)) {
      if (col.key === 'devices' && res && Array.isArray(res.actors) && res.actors.length > 0) {
        return `<span class="cell-value">${UIUtils.formatDevicesRichHtml(res, '')}</span>`;
      }
      return '<span class="cell-sub">—</span>';
    }
    const rawDisplay = attr.displayValue || attr.value;

    if (col.key === 'devices') {
      return `<span class="cell-value">${UIUtils.formatDevicesRichHtml(res, rawDisplay)}</span>`;
    }

    if (attr.value && attr.value !== rawDisplay) {
      return `<span class="cell-value">${UIUtils.escapeHTML(rawDisplay)}</span><span class="cell-meta font-mono">${UIUtils.escapeHTML(attr.value)}</span>`;
    }

    if (rawDisplay.includes(' -> ') || rawDisplay.includes(' → ')) {
      const parts = rawDisplay.split(/\s*(?:->|→)\s*/);
      return `<span class="cell-value">${parts.map(p => UIUtils.escapeHTML(p)).join('<span class="cell-arrow" aria-hidden="true">→</span>')}</span>`;
    }
    return `<span class="cell-value">${UIUtils.escapeHTML(rawDisplay)}</span>`;
  },

  renderFlatRows(data) {
    let items = [];
    const view = this.state.currentFlatView;
    if (data.modules) {
      if (view === 'modules') items = data.modules.map(i => ({ ...i, type: 'module' }));
      else if (view === 'packages') items = data.modules.flatMap(m => m.packages.map(i => ({ ...i, parent: m.name, moduleName: m.name, type: 'package' })));
      else if (view === 'classes') items = data.modules.flatMap(m => m.packages.flatMap(p => p.classes.map(i => ({ ...i, parent: p.name, moduleName: m.name, packageName: p.name, type: 'class' }))));
      else if (view === 'testCases') {
        items = data.modules.flatMap(m =>
          m.packages.flatMap(p =>
            p.classes.flatMap(c =>
              c.testCases.flatMap(tc => {
                if (tc.targets && tc.targets.length > 0 && !this.hasOnlyDefaultTarget()) {
                  return tc.targets.map(tgt => ({
                    ...tc,
                    parent: c.name,
                    moduleName: m.name,
                    packageName: p.name,
                    className: c.name,
                    type: 'testCase',
                    target: tgt
                  }));
                } else {
                  return [{
                    ...tc,
                    parent: c.name,
                    moduleName: m.name,
                    packageName: p.name,
                    className: c.name,
                    type: 'testCase'
                  }];
                }
              })
            )
          )
        );
      }
    }

    items.sort((a, b) => this.compareNodes(a, b));

    if (this.isSingleSuiteTestList()) {
      const suiteName = this.state.filters.testSuite;
      const variantName = this.state.filters.variants[0];
      const summaryColumns = this.getActiveSummaryColumns();
      const showModuleInMeta = this.countActiveSuiteModules() > 1 && !this.state.selectedModule;
      const showTargetCol = !this.hasOnlyDefaultTarget();

      this.elements.resultsData.innerHTML = items.map(item => {
        const res = this.getVariantResultForTestCase(item, suiteName, variantName);
        const clickable = this.isTestCaseClickable(item);
        const targetAttr = item.target ? `data-target="${UIUtils.escapeHTML(item.target.name)}"` : '';
        const failedStepTarget = UIUtils.getFailedStepTarget(res);
        const failedInlineHtml = failedStepTarget
          ? ` · <span class="failed-step-inline">failed at ${UIUtils.escapeHTML(failedStepTarget)}</span>`
          : '';
        const metaPrefix = showModuleInMeta
          ? `${UIUtils.escapeHTML(item.moduleName)} · ${UIUtils.escapeHTML(item.className)}`
          : UIUtils.escapeHTML(item.className);

        const targetCell = showTargetCol
          ? `<td class="col-target text-gray-500 truncate" title="${UIUtils.escapeHTML(item.target ? item.target.name : '-')}">${UIUtils.escapeHTML(item.target ? item.target.name : '-')}</td>`
          : '';

        const dynamicCells = summaryColumns.map(col => {
          const colCls = this.getSummaryColumnClass(col);
          return `<td class="${colCls}">${this.formatSummaryAttrCellHTML(res, col)}</td>`;
        }).join('');

        const durationText = UIUtils.formatDuration(res ? res.durationMs : null);
        const statusHtml = UIUtils.statusTag(res ? res.status : '');
        const titleFontCls = /\s/.test(String(item.name || '')) ? '' : ' font-mono';
        const rowAttrs = clickable
          ? `class="table-row single-suite-row stack-trace-trigger" tabindex="0" data-module="${UIUtils.escapeHTML(item.moduleName || '')}" data-package="${UIUtils.escapeHTML(item.packageName || '')}" data-class="${UIUtils.escapeHTML(item.className || '')}" data-test-case="${UIUtils.escapeHTML(item.name || '')}" ${targetAttr} aria-label="View details for ${UIUtils.escapeHTML(item.name)}"`
          : 'class="table-row"';

        return `<tr ${rowAttrs}>
          <td class="col-test" title="${UIUtils.escapeHTML(item.name)}">
            <span class="cell-title${titleFontCls}">${UIUtils.escapeHTML(item.name)}</span>
            <span class="cell-meta">${metaPrefix}${failedInlineHtml}</span>
          </td>
          ${targetCell}
          ${dynamicCells}
          <td class="col-status">${statusHtml}</td>
          <td class="col-duration"><span class="cell-value num">${UIUtils.escapeHTML(durationText)}</span></td>
        </tr>`;
      }).join('') || '<tr><td colspan="100%" class="text-center text-gray-500" style="padding: 2rem;">No tests match the current filters.</td></tr>';
      return;
    }

    this.elements.resultsData.innerHTML = items.map(item => {
      let nameTd = `<td class="py-3 px-6 sticky-name font-medium" title="${UIUtils.escapeHTML(item.name)}">${UIUtils.escapeHTML(item.name)}</td>`;
      if (item.type !== 'testCase') {
        const suiteBadges = (item.type === 'class') ? this.getClassSuiteBadges(item) : '';
        nameTd = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover-underline cursor-pointer" tabindex="0" role="link" title="${UIUtils.escapeHTML(item.name)}" data-name="${UIUtils.escapeHTML(item.name)}" data-type="${item.type}" data-module-name="${UIUtils.escapeHTML(item.moduleName || '')}" data-package-name="${UIUtils.escapeHTML(item.packageName || '')}" data-interactive="flat">${UIUtils.escapeHTML(item.name)}${suiteBadges}</td>`;
      } else if (this.isTestCaseClickable(item)) {
        const targetAttr = item.target ? `data-target="${UIUtils.escapeHTML(item.target.name)}"` : '';
        nameTd = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover-underline cursor-pointer stack-trace-trigger" data-module="${UIUtils.escapeHTML(item.moduleName || '')}" data-package="${UIUtils.escapeHTML(item.packageName || '')}" data-class="${UIUtils.escapeHTML(item.className || '')}" data-test-case="${UIUtils.escapeHTML(item.name || '')}" ${targetAttr} tabindex="0" role="button" aria-label="View details for ${UIUtils.escapeHTML(item.name)}">${UIUtils.escapeHTML(item.name)}</td>`;
      } else {
        nameTd = `<td class="py-3 px-6 sticky-name font-medium text-gray-800" title="${UIUtils.escapeHTML(item.name)}">${UIUtils.escapeHTML(item.name)}</td>`;
      }

      let targetCell = '';
      if (this.state.viewMode === 'flat' && view === 'testCases' && !this.hasOnlyDefaultTarget()) {
        const targetName = item.target ? item.target.name : '-';
        targetCell = `<td class="py-3 px-6 text-gray-500 text-sm truncate col-target" title="${UIUtils.escapeHTML(targetName)}">${UIUtils.escapeHTML(targetName)}</td>`;
      }

      let pathCell = '';
      if (this.state.viewMode === 'flat' && !this.state.selectedModule) {
        if (view === 'packages') {
          pathCell = `<td class="py-3 px-6 text-gray-500 text-sm truncate col-module" title="${UIUtils.escapeHTML(item.moduleName)}">${UIUtils.escapeHTML(item.moduleName)}</td>`;
        } else if (view === 'classes') {
          pathCell = `<td class="px-2 col-path" title="${UIUtils.escapeHTML(item.moduleName)} > ${UIUtils.escapeHTML(item.packageName)}">
                    <div class="flex flex-col" style="overflow: hidden; width: 100%;">
                        <span class="text-xs text-gray-500 truncate-block">${UIUtils.escapeHTML(item.moduleName)}</span>
                        <span class="text-sm text-gray-500 truncate-block">${UIUtils.escapeHTML(item.packageName)}</span>
                    </div>
                </td>`;
        } else if (view === 'testCases') {
          pathCell = `<td class="px-2 col-path" title="${UIUtils.escapeHTML(item.moduleName)} > ${UIUtils.escapeHTML(item.packageName)} > ${UIUtils.escapeHTML(item.className)}">
                    <div class="flex flex-col" style="overflow: hidden; width: 100%;">
                        <span class="text-xs text-gray-500 truncate-block">${UIUtils.escapeHTML(item.moduleName)}</span>
                        <span class="text-sm text-gray-500 truncate-block">${UIUtils.escapeHTML(item.packageName)} > ${UIUtils.escapeHTML(item.className)}</span>
                    </div>
                </td>`;
        }
      }

      const context = {
        moduleName: item.moduleName,
        packageName: item.packageName,
        className: item.className,
        targetName: item.target ? item.target.name : undefined
      };

      return `<tr class="table-row">
            ${nameTd}
            ${targetCell}
            ${pathCell}
            ${this._renderStatusCell(item, context)}
        </tr>`;
    }).join('') || '<tr><td colspan="100%" class="text-center text-gray-500" style="padding: 2rem;">No results found.</td></tr>';
  },

  updateTooltipsForOverflow() {
    if (!this.elements.resultsData) return;
    const nameCells = this.elements.resultsData.querySelectorAll('.sticky-name');
    nameCells.forEach(cell => {
      const isOverflowing = cell.scrollWidth > cell.clientWidth;
      if (!isOverflowing) {
        cell.removeAttribute('title');
      }
    });

    const pathCells = this.elements.resultsData.querySelectorAll('.truncate, .max-w-300');
    pathCells.forEach(cell => {
      const blocks = cell.querySelectorAll('.truncate-block');
      let isOverflowing = false;

      if (blocks.length > 0) {
        blocks.forEach(block => {
          if (block.scrollWidth > block.clientWidth) {
            isOverflowing = true;
          }
        });
      } else if (cell.scrollWidth > cell.clientWidth) {
        isOverflowing = true;
      }

      if (!isOverflowing) {
        cell.removeAttribute('title');
      }
    });
  },

  // --- HELPERS ---

  /**
   * Returns the target-level nodes of `node` that match the target filter.
   * Classic layouts read a test case row that groups several targets through its first target.
   * Rich single-suite layouts read every matching target.
   */
  getScopedTargetNodes(node) {
    if (!node) return [];
    if (node.type === 'target') return [node];
    if (node.target) return [node.target];
    if (Array.isArray(node.targets) && node.targets.length > 0) {
      if (!this.usesRichSingleSuiteLayout()) return [node.targets[0]];
      const tgtFilter = this.state.filters?.targets;
      if (Array.isArray(tgtFilter) && tgtFilter.length > 0) {
        return node.targets.filter(t => t && tgtFilter.includes(t.name));
      }
      return node.targets;
    }
    return [node];
  },

  getScopedSummaryLists(node) {
    if (!node) return [];
    if (node.type === 'target') return node.testSuiteSummaries ? [node.testSuiteSummaries] : [];
    if (node.target && node.target.testSuiteSummaries) return [node.target.testSuiteSummaries];
    if (Array.isArray(node.targets) && node.targets.length > 0) {
      return this.getScopedTargetNodes(node).map(t => t && t.testSuiteSummaries).filter(Boolean);
    }
    return node.testSuiteSummaries ? [node.testSuiteSummaries] : [];
  },

  getVariantResultForTestCase(testCase, suiteName, variantName) {
    const targetNodes = this.getScopedTargetNodes(testCase);
    // Priority: Fail > Pass > Skipped
    let finalRes = null;
    for (const targetNode of targetNodes) {
      const testSuiteResults = targetNode && targetNode.testSuiteResults;
      if (!testSuiteResults) continue;
      let suitesToSearch = [];
      if (suiteName === 'all') {
        suitesToSearch = testSuiteResults.filter(ts => ts.testSuiteName !== AGGREGATED_SUITE_NAME);
        if (suitesToSearch.length === 0) suitesToSearch = testSuiteResults;
      } else {
        const specific = testSuiteResults.find(ts => ts.testSuiteName === suiteName);
        if (specific) suitesToSearch = [specific];
      }

      for (const suite of suitesToSearch) {
        const res = suite.variantResults ? suite.variantResults[variantName] : null;
        if (res) {
          const norm = UIUtils.normalizeStatus(res.status);
          if (norm === TEST_STATUS.FAIL) return res;
          if (norm === TEST_STATUS.PASS) finalRes = res;
          if (norm === TEST_STATUS.SKIPPED && !finalRes) finalRes = res;
        }
      }
    }
    return finalRes;
  },

  /**
   * Returns true when a result opens the rich detail view: its suite plugin always uses the rich list,
   * or the result carries `androidx.report.*` entries.
   */
  hasRichReportEntries(res) {
    if (!res) return false;
    const plugin = globalThis.SuiteRegistry ? globalThis.SuiteRegistry.resolve(res.adapterId) : null;
    if (plugin && plugin.useRichSingleSuiteList) return true;
    if (Array.isArray(res.steps) && res.steps.length > 0) return true;
    if (Array.isArray(res.actors) && res.actors.length > 0) return true;
    if (Array.isArray(res.artifacts) && res.artifacts.length > 0) return true;
    if (Array.isArray(res.logMeta) && res.logMeta.length > 0) return true;
    if (
      res.callout &&
      (res.callout.title ||
        res.callout.message ||
        res.callout.likelyCause ||
        (res.callout.failedStep !== null && res.callout.failedStep !== undefined))
    ) {
      return true;
    }
    if (res.sourceLocation && (res.sourceLocation.file || res.sourceLocation.className)) return true;
    if (this.getAllSummaryAttrs(res).length > 0) return true;
    return false;
  },

  hasVisibleFailures(node) {
    if (!node) return false;
    const activeSuite = this.state.filters.testSuite;
    const activeVariants = this.state.filters.variants;
    const targetNodes = this.getScopedTargetNodes(node);

    for (const targetNode of targetNodes) {
      const commonStackTraces = (targetNode && targetNode.commonStackTraces) || node.commonStackTraces || [];
      const hasStackMatch = commonStackTraces.some(group => {
        for (const [suite, variants] of Object.entries((group && group.occurrences) || {})) {
          if (activeSuite !== 'all' && suite !== activeSuite) continue;
          if (variants.some(v => activeVariants.includes(v))) return true;
        }
        return false;
      });
      if (hasStackMatch) return true;
    }
    return false;
  },

  isTestCaseClickable(node) {
    if (!node) return false;
    const customMatch = globalThis.SuiteRegistry
      ? globalThis.SuiteRegistry.findCustomDetailMatch(node, this)
      : null;
    if (customMatch) return true;
    if (this.hasVisibleFailures(node)) return true;

    const activeSuite = this.state.filters.testSuite;
    const activeVariants = this.state.filters.variants || [];
    const richLayout = this.usesRichSingleSuiteLayout();
    const targetNodes = this.getScopedTargetNodes(node);

    for (const targetNode of targetNodes) {
      const testSuiteResults = (targetNode && targetNode.testSuiteResults) || [];
      for (const sr of testSuiteResults) {
        if (activeSuite !== 'all' && sr.testSuiteName !== activeSuite) continue;
        for (const v of activeVariants) {
          const res = sr.variantResults ? sr.variantResults[v] : null;
          if (res && (richLayout || this.hasRichReportEntries(res))) {
            return true;
          }
        }
      }
    }
    return false;
  },

  getClassSuiteBadges(node) {
    if (!node) return '';
    if (node._suiteBadges !== undefined) return node._suiteBadges;

    const summariesList = this.getScopedSummaryLists(node);
    if (summariesList.length === 0) {
      node._suiteBadges = '';
      return '';
    }

    const suiteNames = Array.from(
      new Set(
        summariesList
          .flatMap(summaries => summaries.map(ts => ts.name))
          .filter(name => name && name !== AGGREGATED_SUITE_NAME)
      )
    );

    if (suiteNames.length === 0) {
      node._suiteBadges = '';
      return '';
    }

    node._suiteBadges = suiteNames
      .map(name => `<span style="background-color: #f3e8ff; color: #6b21a8; font-size: 0.65rem; font-weight: 700; border-radius: 4px; padding: 0.15rem 0.35rem; margin-left: 0.5rem;">${UIUtils.escapeHTML(UIUtils.formatSuiteName(name))}</span>`)
      .join('');
    return node._suiteBadges;
  },

  /**
   * Computes duration in milliseconds for a node and variant under the active suite filter.
   */
  getNodeVariantDurationMs(node, variantName, variantSummary = null) {
    if (!node) return null;
    if (variantSummary && variantSummary.durationMs !== null && variantSummary.durationMs !== undefined) {
      return Number(variantSummary.durationMs);
    }

    const suiteFilter = this.state.filters.testSuite;
    const summariesList = this.getScopedSummaryLists(node);

    if (summariesList.length > 0) {
      let summarySum = 0;
      let summaryFound = false;
      summariesList.forEach(tss => {
        const sSum = suiteFilter === 'all'
          ? tss.find(ts => ts.name === AGGREGATED_SUITE_NAME)
          : tss.find(ts => ts.name === suiteFilter);
        if (sSum && Array.isArray(sSum.variantSummaries)) {
          const vs = sSum.variantSummaries.find(item => item.name === variantName);
          if (vs && vs.durationMs !== null && vs.durationMs !== undefined) {
            summarySum += Number(vs.durationMs) || 0;
            summaryFound = true;
          }
        }
      });
      if (summaryFound) return summarySum;
    }

    const isLeaf = node.type === 'testCase' || node.type === 'target' || (!node.packages && !node.classes && !node.testCases && (node.testSuiteResults || node.target || node.targets));
    if (isLeaf) {
      const targetNodes = this.getScopedTargetNodes(node);
      let leafSum = 0;
      let leafFound = false;
      targetNodes.forEach(tNode => {
        const suiteResults = tNode.testSuiteResults || [];
        suiteResults.forEach(sr => {
          if (suiteFilter === 'all') {
            if (sr.testSuiteName === AGGREGATED_SUITE_NAME) return;
          } else if (sr.testSuiteName !== suiteFilter) {
            return;
          }
          const r = sr.variantResults && sr.variantResults[variantName];
          if (r && r.durationMs !== null && r.durationMs !== undefined) {
            leafSum += Number(r.durationMs) || 0;
            leafFound = true;
          }
        });
      });
      if (leafFound) return leafSum;
    }

    return null;
  },

  _renderStatusCell(node, context = {}) {
    const variantsToShow = this.state.filters.variants;

    if (!node) {
      return variantsToShow.map(() => `<td colspan="4" class="border-l border-gray-200"></td>`).join('');
    }

    const suiteFilter = this.state.filters.testSuite;
    const summariesList = this.getScopedSummaryLists(node);

    return `${variantsToShow.map(v => {
      const matchedSummaries = [];
      for (const testSuiteSummaries of summariesList) {
        const suiteSummary = suiteFilter === 'all'
          ? testSuiteSummaries.find(ts => ts.name === AGGREGATED_SUITE_NAME)
          : testSuiteSummaries.find(ts => ts.name === suiteFilter);
        if (suiteSummary && Array.isArray(suiteSummary.variantSummaries)) {
          const vs = suiteSummary.variantSummaries.find(item => item.name === v);
          if (vs) matchedSummaries.push(vs);
        }
      }

      let variantSummary = null;
      if (matchedSummaries.length === 1) {
        variantSummary = matchedSummaries[0];
      } else if (matchedSummaries.length > 1) {
        const passed = matchedSummaries.reduce((acc, s) => acc + (s.passed || 0), 0);
        const failed = matchedSummaries.reduce((acc, s) => acc + (s.failed || 0), 0);
        const skipped = matchedSummaries.reduce((acc, s) => acc + (s.skipped || 0), 0);
        const total = matchedSummaries.reduce((acc, s) => acc + (s.total || 0), 0);
        const relevant = passed + failed;
        const rate = relevant > 0 ? (passed / relevant) * 100 : 0;
        variantSummary = { name: v, passed, failed, skipped, total, rate };
      }

      if (!variantSummary || variantSummary.total === 0) return '<td colspan="4" class="text-center text-gray-500 border-l border-gray-200">-</td>';

      const { passed, failed, skipped, rate } = variantSummary;
      const relevantTotal = passed + failed;
      const passRateColor = rate >= 95 ? 'text-green-600' : rate >= 80 ? 'text-yellow-600' : 'text-red-600';

      const filter = this.state.filters.status;
      const showPassed = filter.includes(TEST_STATUS.PASSED);
      const showFailed = filter.includes(TEST_STATUS.FAILED);
      const showSkipped = filter.includes(TEST_STATUS.SKIPPED);

      return `
            <td class="py-3 px-4 text-center ${showPassed ? 'text-green-600' : 'text-gray-500'} font-medium border-l border-gray-200" aria-label="${showPassed ? passed : '-'} passed tests for ${UIUtils.escapeHTML(v)}">${showPassed ? passed : '-'}</td>
            <td class="py-3 px-4 text-center ${showFailed && failed > 0 ? 'text-red-600 font-bold' : 'text-gray-500'} border-l border-gray-200" aria-label="${showFailed ? failed : '-'} failed tests for ${UIUtils.escapeHTML(v)}">${showFailed ? failed : '-'}</td>
            <td class="py-3 px-4 text-center ${showSkipped ? 'text-yellow-600' : 'text-gray-500'}" aria-label="${showSkipped ? skipped : '-'} skipped tests for ${UIUtils.escapeHTML(v)}">${showSkipped ? skipped : '-'}</td>
            <td class="py-3 px-4 text-center" aria-label="${rate.toFixed(1)}% pass rate (${passed}/${relevantTotal}) for ${UIUtils.escapeHTML(v)}">
                <div class="flex flex-col">
                    <span class="font-bold ${passRateColor}">${rate.toFixed(1)}%</span>
                    <span class="text-xs text-gray-500">${passed}/${relevantTotal}</span>
                </div>
            </td>`;
    }).join('')}`;
  },

  openStackTrace(element) {
    const { module, package: pkg, class: clz, testCase: tcName, target: tgtName } = element.dataset;
    if (!tcName) return;

    let res = null;

    if (this.testCaseIndex) {
      // 1. Try exact fully-qualified key lookup O(1)
      const exactKey = `${module}:${pkg}:${clz}:${tcName}`;
      res = this.testCaseIndex.get(exactKey);

      // 2. Fallback to case-insensitive testCase name lookup O(1)
      if (!res) {
        res = this.testCaseIndex.get((tcName || '').toLowerCase());
      }
    }

    // 3. Fallback to direct search if index is still populating in background
    if (!res && this.processedData?.modules) {
      for (const m of this.processedData.modules) {
        if (!module || m.name === module) {
          for (const p of (m.packages || [])) {
            if (!pkg || p.name === pkg) {
              for (const c of (p.classes || [])) {
                if (!clz || c.name === clz) {
                  const t = (c.testCases || []).find(tc => tc.name === tcName || tc.name.toLowerCase() === (tcName || '').toLowerCase());
                  if (t) {
                    res = { testCase: t, moduleName: m.name, packageName: p.name, className: c.name };
                    break;
                  }
                }
              }
            }
          }
        }
      }
    }

    if (res) {
      this.activeTrigger = element;
      this.activeTriggerMeta = element && element.dataset ? {
        module: element.dataset.module || '',
        pkg: element.dataset.package || '',
        cls: element.dataset.class || '',
        tc: element.dataset.testCase || '',
        target: element.dataset.target || '',
      } : null;
      this.savedScrollY = window.scrollY || 0;
      const mainEl = this.elements.reportView ? this.elements.reportView.querySelector('main') : null;
      this.savedMainScrollTop = mainEl ? mainEl.scrollTop : 0;
      this.state.activeDetailRunIndex = null;

      const testCaseClone = { ...res.testCase };
      if (tgtName && testCaseClone.targets) {
        testCaseClone.target = testCaseClone.targets.find(t => t.name === tgtName);
      }
      const context = {
        moduleName: module || res.moduleName,
        packageName: pkg || res.packageName,
        className: clz || res.className,
        testCaseName: tcName,
        targetName: tgtName
      };
      this.state.listHistoryDepth = Navigation.inPageDepth;
      this.showStackTraceView(testCaseClone, context);
      Navigation.push();
    }
  },

  returnFromDetailView() {
    if (this.state.currentView !== 'stack-trace') return;
    // Filter changes inside the detail view add history entries, so go back to the list entry itself.
    const stepsBack = Navigation.inPageDepth - (this.state.listHistoryDepth ?? Navigation.inPageDepth);
    if (stepsBack > 0 && Navigation.hasReportEntry) {
      history.go(-stepsBack);
      return;
    }
    const { wasDetailView, wasRichDetail } = this.showReportView(true, true);
    this.render();
    this.restoreReportScrollAndFocus(wasDetailView, wasRichDetail);
    Navigation.replace();
  },

  showStackTraceView(testCase, context) {
    this.state.currentView = 'stack-trace';
    this.state.currentTestCase = testCase;
    this.state.currentStackTraceContext = context;
    const detailTarget = this.resolveDetailViewTarget(testCase, context);
    this.state.detailViewMode = detailTarget.mode;

    this.renderHeaderAndToolbar();
    this.elements.reportView.classList.add('hidden-view');
    this.elements.stackTraceView.classList.remove('hidden-view');
    if (this.elements.filterControlsGroup) {
      this.elements.filterControlsGroup.classList.add('hidden');
    }
    if (this.elements.searchContainer) this.elements.searchContainer.classList.add('hidden');
    if (this.elements.viewSegments) this.elements.viewSegments.classList.add('hidden');
    if (this.elements.densitySegments) this.elements.densitySegments.classList.add('hidden');

    this.renderStackTraceGrid(testCase, context, detailTarget);
    this.syncDetailViewTitleAndAnnounce(testCase);
    this.renderStackTraceBreadcrumbs(context);
    window.scrollTo(0, 0);
    if (detailTarget.mode === 'rich' && this.elements.stackTraceView) {
      this.elements.stackTraceView.scrollTop = 0;
    }
  },

  syncDetailViewTitleAndAnnounce(testCase) {
    const tcName = testCase ? testCase.name : '';
    if (this.state.detailViewMode === 'rich') {
      this.announce(`Showing details for test case: ${tcName}`);
      document.title = `Test Details: ${tcName} - ${this.baseTitle}`;
    } else {
      this.announce(`Showing stack trace for test case: ${tcName}`);
      document.title = `Stack Trace: ${tcName} - ${this.baseTitle}`;
    }
  },

  resolveDetailViewTarget(testCase, context = {}) {
    const customMatch = globalThis.SuiteRegistry
      ? globalThis.SuiteRegistry.findCustomDetailMatch(testCase, this)
      : null;
    if (customMatch) {
      return { mode: customMatch.plugin.id || 'custom', customMatch, visibleRuns: [] };
    }
    const visibleRuns = this.collectVisibleRuns(testCase, context);
    if (
      visibleRuns.length > 0 &&
      globalThis.RichTestDetailView &&
      (this.usesRichSingleSuiteLayout() || visibleRuns.some(r => this.hasRichReportEntries(r.res)))
    ) {
      return { mode: 'rich', customMatch: null, visibleRuns };
    }
    return { mode: 'classic', customMatch: null, visibleRuns };
  },

  /**
   * Returns a cached lazy detail payload only when `detailId` is an own object property of `window.__AGP_TEST_DETAILS`.
   */
  getCachedLazyDetail(detailId) {
    if (!detailId || typeof detailId !== 'string') return null;
    const store = window.__AGP_TEST_DETAILS;
    if (!store || typeof store !== 'object') return null;
    if (!Object.prototype.hasOwnProperty.call(store, detailId)) return null;
    const payload = store[detailId];
    return (payload && typeof payload === 'object' && !Array.isArray(payload)) ? payload : null;
  },

  /**
   * Lazily loads `details/<detailId>.js` via script injection so offline `file://` reports work without `fetch()`.
   */
  loadLazyDetail(detailId) {
    if (!detailId || typeof detailId !== 'string') {
      return Promise.resolve(null);
    }
    if (!/^[A-Za-z0-9._-]+$/.test(detailId) || detailId.includes('..')) {
      return Promise.resolve(null);
    }
    window.__AGP_TEST_DETAILS = window.__AGP_TEST_DETAILS || {};
    const cached = this.getCachedLazyDetail(detailId);
    if (cached !== null) {
      return Promise.resolve(cached);
    }
    if (!this._detailLoadPromises) {
      this._detailLoadPromises = new Map();
    }
    if (this._detailLoadPromises.has(detailId)) {
      return this._detailLoadPromises.get(detailId);
    }

    const promise = new Promise((resolve) => {
      const script = document.createElement('script');
      script.src = `details/${encodeURIComponent(detailId)}.js`;
      script.async = true;
      script.onload = () => {
        resolve(this.getCachedLazyDetail(detailId));
      };
      script.onerror = () => {
        this._detailLoadPromises.delete(detailId);
        resolve(null);
      };
      (document.head || document.body || document.documentElement).appendChild(script);
    });
    this._detailLoadPromises.set(detailId, promise);
    return promise;
  },

  resolveRunStackTrace(res, suiteName, variantName, targetNode, testCase) {
    if (!res || UIUtils.normalizeStatus(res.status) !== TEST_STATUS.FAIL) return '';
    // Target owns commonStackTraces in RootReport; fall back to testCase only when targetNode omits the field.
    const commonStackTraces = Array.isArray(targetNode && targetNode.commonStackTraces)
      ? targetNode.commonStackTraces
      : (Array.isArray(testCase && testCase.commonStackTraces) ? testCase.commonStackTraces : []);
    if (commonStackTraces.length === 0) return '';

    if (res.stackTraceId) {
      const byId = commonStackTraces.find(st => st && st.id === res.stackTraceId);
      if (byId && byId.stackTrace) return byId.stackTrace;
    }

    const byOcc = commonStackTraces.find(st => {
      const occVars = st && st.occurrences && st.occurrences[suiteName];
      return Array.isArray(occVars) && occVars.includes(variantName);
    });
    if (byOcc && byOcc.stackTrace) return byOcc.stackTrace;

    if (
      !res.stackTraceId &&
      commonStackTraces.length === 1 &&
      commonStackTraces[0] &&
      !commonStackTraces[0].id &&
      (!commonStackTraces[0].occurrences || Object.keys(commonStackTraces[0].occurrences).length === 0)
    ) {
      return commonStackTraces[0].stackTrace || '';
    }
    return '';
  },

  /**
   * Collects all test runs on a testCase matching the active suite and variant filters.
   */
  collectVisibleRuns(testCase, context = {}) {
    if (!testCase) return [];
    const activeSuite = this.state.filters.testSuite;
    const activeVariants = this.state.filters.variants || [];
    const hasKnownVariants = Array.isArray(this.state.variants) && this.state.variants.length > 0;
    const targets = this.getScopedTargetNodes(testCase);
    const runs = [];

    targets.forEach(targetNode => {
      const suiteResults = targetNode.testSuiteResults || [];
      const suitesToInspect = activeSuite === 'all'
        ? suiteResults.filter(sr => sr.testSuiteName !== AGGREGATED_SUITE_NAME)
        : suiteResults.filter(sr => sr.testSuiteName === activeSuite);

      suitesToInspect.forEach(sr => {
        const suiteName = sr.testSuiteName || '';
        const variantMap = sr.variantResults || {};
        const orderedVariants = (activeVariants.length > 0 || hasKnownVariants)
          ? activeVariants.filter(v => Object.prototype.hasOwnProperty.call(variantMap, v))
          : Object.keys(variantMap);

        orderedVariants.forEach(variantName => {
          const res = variantMap[variantName];
          if (!res) return;

          const stackTrace = this.resolveRunStackTrace(res, suiteName, variantName, targetNode, testCase);
          const env = this.getMatchingSuiteEnvironment(suiteName, variantName, context.moduleName, res.envId);
          const descriptor = (this.state.suiteDescriptors || {})[suiteName] || null;

          runs.push({
            targetName: (targetNode !== testCase && targetNode.name) ? targetNode.name : (context.targetName || ''),
            suiteName,
            variantName,
            res,
            stackTrace,
            env,
            descriptor,
          });
        });
      });
    });

    return runs;
  },

  renderStackTraceGrid(testCase, context = {}, resolvedTarget = null) {
    const grid = this.elements.stackTraceGrid;
    grid.innerHTML = '';
    grid.classList.remove('rich-detail-mode');
    if (this.elements.stackTraceView) {
      this.elements.stackTraceView.classList.remove('rich-detail-active');
    }
    if (this.elements.detailTopbarActions) {
      this.elements.detailTopbarActions.innerHTML = '';
    }

    const target = resolvedTarget || this.resolveDetailViewTarget(testCase, context);
    this.state.detailViewMode = target.mode;
    if (target.customMatch) {
      target.customMatch.plugin.renderCustomDetailView(grid, testCase, target.customMatch.items, context, this);
      return;
    }
    if (target.mode === 'rich') {
      if (this.elements.stackTraceView) {
        this.elements.stackTraceView.classList.add('rich-detail-active');
      }
      globalThis.RichTestDetailView.render(this, grid, testCase, target.visibleRuns, context);
      return;
    }
    const activeSuite = this.state.filters.testSuite;
    const activeVariants = this.state.filters.variants;

    // For now, use the first target's properties. This works for all non-GMD current use cases out of the box.
    // Device-specific UI and naming will be added in a subsequent phase (see bug b/525671605).
    const commonStackTraces = testCase.target ? testCase.target.commonStackTraces : (((testCase.targets && testCase.targets[0]) ? testCase.targets[0].commonStackTraces : testCase.commonStackTraces));

    // Filter groups and their internal occurrences based on active filters
    const filteredGroups = (commonStackTraces || []).map(group => {
      const filteredOccurrences = {};
      let hasMatch = false;

      for (const [suite, variants] of Object.entries(group.occurrences)) {
        if (activeSuite !== 'all' && suite !== activeSuite) continue;

        const matchedVariants = variants.filter(v => activeVariants.includes(v));
        if (matchedVariants.length > 0) {
          filteredOccurrences[suite] = matchedVariants;
          hasMatch = true;
        }
      }

      return hasMatch ? { ...group, filteredOccurrences } : null;
    }).filter(g => g !== null);

    if (filteredGroups.length === 0) {
      grid.innerHTML = '<div class="p-8 text-center text-gray-500 w-full">No stack trace available for the selected filters.</div>';
      return;
    }

    const isMultiView = filteredGroups.length > 1;

    filteredGroups.forEach((group, index) => {
      const viewId = `st-view-${index}`;
      const titleId = `st-title-${index}`;

      const variantView = document.createElement("div");
      variantView.className = "variant-code-view";
      variantView.id = viewId;
      if (isMultiView) {
        variantView.classList.add("multi-view");
      }

      const header = document.createElement("div");
      header.className = "variant-header";

      const titleDiv = document.createElement("div");
      titleDiv.className = "flex items-center gap-2 flex-wrap";
      titleDiv.id = titleId;

      for (const [suite, variants] of Object.entries(group.filteredOccurrences)) {
        const tag = document.createElement("span");
        tag.className = "occurrence-tag";
        tag.textContent = `${UIUtils.formatSuiteName(suite)} (${variants.join(", ")})`;
        titleDiv.appendChild(tag);
      }
      header.appendChild(titleDiv);
      variantView.appendChild(header);

      const container = document.createElement("div");
      container.className = "code-container";
      container.setAttribute("tabindex", "0");
      container.setAttribute("aria-labelledby", titleId);

      const pre = document.createElement("pre");
      pre.className = "stack-trace-pre font-mono text-sm text-red-800 whitespace-pre-wrap break-all";
      pre.textContent = group.stackTrace || "";
      container.appendChild(pre);

      variantView.appendChild(container);
      grid.appendChild(variantView);
    });

    // Auto-focus the first stack trace for keyboard users
    const firstContainer = grid.querySelector('.code-container');
    if (firstContainer) {
      setTimeout(() => firstContainer.focus(), 100);
    }
  },

  /**
   * Resolves the parent class node and module/package metadata for a given test case.
   *
   * Frequency & Performance Rationale:
   * - This method is NOT called during bulk report table rendering, so it will NOT hang page rendering.
   * - It is only called on-demand when a user clicks to view/switch stack traces or screenshot details.
   * - It uses `this.testCaseIndex` (an O(1) Map built asynchronously on load) as the primary lookup path.
   * - The linear scan below is purely a fallback in case the index is still populating in the background.
   */
  getClassForTestCase(testCase, context = {}) {
    if (!testCase) return null;
    const { moduleName, packageName, className } = context;

    // 1. Primary Path: Fast O(1) Map lookup
    if (this.testCaseIndex && testCase.name) {
      const exactKey = `${moduleName}:${packageName}:${className}:${testCase.name}`;
      let res = this.testCaseIndex.get(exactKey);
      if (!res) {
        res = this.testCaseIndex.get((testCase.name || '').toLowerCase());
        if (res) {
          if (moduleName && res.moduleName !== moduleName) res = null;
          if (packageName && res.packageName !== packageName) res = null;
          if (className && res.className !== className) res = null;
        }
      }
      if (res?.classNode) {
        return { moduleName: res.moduleName, packageName: res.packageName, className: res.className, classNode: res.classNode };
      }
    }

    if (!this.processedData?.modules) return null;

    // 2. Fallback Path: Linear scan used only if background index is still building or missing entry
    for (const m of this.processedData.modules) {
      if (moduleName && m.name !== moduleName) continue;
      for (const p of (m.packages || [])) {
        if (packageName && p.name !== packageName) continue;
        for (const c of (p.classes || [])) {
          if (className && c.name !== className) continue;
          if ((c.testCases || []).some(tc => tc.name === testCase.name || tc === testCase)) {
            return { moduleName: m.name, packageName: p.name, className: c.name, classNode: c };
          }
        }
      }
    }
    return null;
  },

  /**
   * Finds a test case entry by module, package, class, and test case name.
   *
   * Frequency & Performance Rationale:
   * - Only invoked on single-item user click events (e.g. sidebar switching).
   * - Uses `this.testCaseIndex` Map for O(1) lookup.
   * - Linear scan is only a fallback for when background indexing is incomplete.
   */
  findTestCase(moduleName, packageName, className, testCaseName) {
    // 1. Primary Path: Fast O(1) Map lookup
    if (this.testCaseIndex) {
      const exactKey = `${moduleName}:${packageName}:${className}:${testCaseName}`;
      const res = this.testCaseIndex.get(exactKey);
      if (res) return res;
    }
    // 2. Fallback Path: Linear scan used only if background index is still building
    if (this.processedData?.modules) {
      for (const m of this.processedData.modules) {
        if (!moduleName || m.name === moduleName) {
          for (const p of (m.packages || [])) {
            if (!packageName || p.name === packageName) {
              for (const c of (p.classes || [])) {
                if (!className || c.name === className) {
                  const tc = (c.testCases || []).find(t => t.name === testCaseName);
                  if (tc) return { testCase: tc, moduleName: m.name, packageName: p.name, className: c.name };
                }
              }
            }
          }
        }
      }
    }
    return null;
  },

  getTestCaseOverallStatus(testCase) {
    if (!testCase) return TEST_STATUS.PASS;
    const targets = testCase.target ? [testCase.target] : (testCase.targets || [testCase]);
    let hasFail = false;
    let hasPass = false;

    targets.forEach(t => {
      const results = t.testSuiteResults || [];
      results.forEach(sr => {
        for (const res of Object.values(sr.variantResults || {})) {
          if (res.status === TEST_STATUS.FAIL) hasFail = true;
          if (res.status === TEST_STATUS.PASS) hasPass = true;
        }
      });
    });

    if (hasFail) return TEST_STATUS.FAIL;
    if (hasPass) return TEST_STATUS.PASS;
    return TEST_STATUS.SKIPPED;
  },

  copyToClipboard(text, btn, announceMessage = null) {
    if (!navigator.clipboard) {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
    } else {
      navigator.clipboard.writeText(text);
    }
    const origText = btn.innerHTML;
    btn.innerHTML = `<span>Copied!</span>`;
    setTimeout(() => { btn.innerHTML = origText; }, 1800);
    if (announceMessage) {
      this.announce(announceMessage);
    }
  },

  showReportView(fromDetailView = false, willRender = false) {
    this.announce("Returning to report view");
    document.title = this.baseTitle;
    const wasDetailView = fromDetailView || this.state.currentView === 'stack-trace';
    const wasRichDetail = this.elements.stackTraceView.classList.contains('rich-detail-active');
    this.state.currentView = 'report';
    this.state.detailViewMode = null;
    this.state.currentTestCase = null;
    this.state.currentStackTraceContext = {};
    this.state.listHistoryDepth = null;
    Navigation.hasReportEntry = true;

    this.elements.stackTraceView.classList.remove('rich-detail-active');
    this.elements.stackTraceView.classList.add('hidden-view');
    this.elements.reportView.classList.remove('hidden-view');
    if (this.elements.detailTopbarActions) {
      this.elements.detailTopbarActions.innerHTML = '';
    }
    if (this.elements.filterControlsGroup) {
      this.elements.filterControlsGroup.classList.remove('hidden');
    }
    if (this.elements.searchContainer) this.elements.searchContainer.classList.remove('hidden');
    if (this.elements.viewSegments) this.elements.viewSegments.classList.remove('hidden');
    if (this.elements.densitySegments) this.elements.densitySegments.classList.remove('hidden');

    if (!willRender) {
      this.renderHeaderAndToolbar();
      this.restoreReportScrollAndFocus(wasDetailView, wasRichDetail);
    }
    return { wasDetailView, wasRichDetail };
  },

  restoreReportScrollAndFocus(wasDetailView, wasRichDetail) {
    // The rich detail view can re-render the list, so restore the saved scroll position instead of
    // relying on focus to bring the clicked row back into view.
    if (wasDetailView && wasRichDetail) {
      window.scrollTo(0, this.savedScrollY || 0);
      const mainEl = this.elements.reportView ? this.elements.reportView.querySelector('main') : null;
      if (mainEl) {
        mainEl.scrollTop = this.savedMainScrollTop || 0;
      }
    }

    let triggerToFocus = this.activeTrigger;
    if (triggerToFocus && !document.body.contains(triggerToFocus) && this.activeTriggerMeta && this.elements.resultsData) {
      const m = this.activeTriggerMeta;
      triggerToFocus = Array.from(this.elements.resultsData.querySelectorAll('.stack-trace-trigger')).find(el =>
        (el.dataset.testCase || '') === m.tc &&
        (el.dataset.class || '') === m.cls &&
        (el.dataset.package || '') === m.pkg &&
        (el.dataset.module || '') === m.module &&
        (el.dataset.target || '') === m.target
      ) || null;
    }
    if (triggerToFocus) {
      if (!wasRichDetail) {
        triggerToFocus.focus();
      } else if (document.body.contains(triggerToFocus)) {
        triggerToFocus.focus({ preventScroll: true });
      }
    }
    this.activeTrigger = null;
    this.activeTriggerMeta = null;
  },

  renderStackTraceBreadcrumbs(context) {
    const { moduleName, packageName, className, testCaseName } = context;
    const isRich = this.state.detailViewMode === 'rich';
    if (isRich) {
      const bcFontCls = /\s/.test(String(testCaseName || '')) ? '' : ' font-mono';
      const targetSuffix = context.targetName && !this.hasOnlyDefaultTarget()
        ? ` <span class="l2-breadcrumb-sep" aria-hidden="true">/</span> <span class="l2-breadcrumb-current font-mono">${UIUtils.escapeHTML(context.targetName)}</span>`
        : '';
      this.elements.stackTraceBreadcrumbs.innerHTML = `
        <div class="l2-breadcrumb-left">
          <a href="#" class="l2-breadcrumb-link" data-action="back-to-list" aria-label="Back to tests">Tests</a>
          <span class="l2-breadcrumb-sep" aria-hidden="true">/</span>
          <span class="l2-breadcrumb-current${bcFontCls}">${UIUtils.escapeHTML(testCaseName || '')}</span>${targetSuffix}
        </div>`;
      return;
    }
    let html = `<div class="flex items-center gap-2 text-sm">
        <a href="#" class="breadcrumb-link" data-action="${BREADCRUMB_ACTIONS.GO_TO_MODULES}" aria-label="Go back to Project Overview">Project</a>`;

    if (moduleName) {
      html += `
            <span class="breadcrumb-separator" aria-hidden="true">/</span>
            <a href="#" class="breadcrumb-link" data-action="${BREADCRUMB_ACTIONS.GO_TO_PACKAGES}" data-module-name="${UIUtils.escapeHTML(moduleName)}" aria-label="Go back to module: ${UIUtils.escapeHTML(moduleName)}">${UIUtils.escapeHTML(moduleName)}</a>`;
    }
    if (packageName) {
      html += `
            <span class="breadcrumb-separator" aria-hidden="true">/</span>
            <a href="#" class="breadcrumb-link" data-action="${BREADCRUMB_ACTIONS.GO_TO_CLASSES}" data-module-name="${UIUtils.escapeHTML(moduleName)}" data-package-name="${UIUtils.escapeHTML(packageName)}" aria-label="Go back to package: ${UIUtils.escapeHTML(packageName)}">${UIUtils.escapeHTML(packageName)}</a>`;
    }
    if (className) {
      html += `
            <span class="breadcrumb-separator" aria-hidden="true">/</span>
            <a href="#" class="breadcrumb-link" data-action="${BREADCRUMB_ACTIONS.GO_TO_TEST_CASES}" data-module-name="${UIUtils.escapeHTML(moduleName)}" data-package-name="${UIUtils.escapeHTML(packageName)}" data-class-name="${UIUtils.escapeHTML(className)}" aria-label="Go back to class: ${UIUtils.escapeHTML(className)}">${UIUtils.escapeHTML(className)}</a>`;
    }
    if (testCaseName) {
      if (context.targetName && !this.hasOnlyDefaultTarget()) {
        html += `
            <span class="breadcrumb-separator" aria-hidden="true">/</span>
            <span class="text-gray-600">${UIUtils.escapeHTML(testCaseName)}</span>
            <span class="breadcrumb-separator" aria-hidden="true">/</span>
            <span class="font-semibold text-gray-800">${UIUtils.escapeHTML(context.targetName)}</span>`;
      } else {
        html += `
            <span class="breadcrumb-separator" aria-hidden="true">/</span>
            <span class="font-semibold text-gray-800">${UIUtils.escapeHTML(testCaseName)}</span>`;
      }
    }
    html += `</div>`;

    this.elements.stackTraceBreadcrumbs.innerHTML = html;
  },

  hasOnlyDefaultTarget() {
    const activeSuite = this.state.filters?.testSuite;
    // Rich single-suite layouts only count the active suite's targets.
    const targets = (this.suiteTargetsMap && this.suiteTargetsMap.has(activeSuite) && this.usesRichSingleSuiteLayout())
      ? this.suiteTargetsMap.get(activeSuite)
      : (this.state.targets || []);
    return targets.length === 0 || (targets.length === 1 && (targets[0] === 'unknown_target' || targets[0] === 'default' || targets[0] === ''));
  },

  getChildType(parentType) {
    if (parentType === 'testCase' && this.hasOnlyDefaultTarget()) {
      return null;
    }
    const hierarchy = { 'root': 'module', 'module': 'package', 'package': 'class', 'class': 'testCase', 'testCase': 'target', 'target': null };
    return hierarchy[parentType];
  },

  pluralize(type) {
    const pluralMap = { 'module': 'modules', 'package': 'packages', 'class': 'classes', 'testCase': 'testCases', 'target': 'targets' };
    return pluralMap[type];
  },

  parseSortKey(key) {
    if (!key || key === 'name') {
      return { variant: null, metric: 'name' };
    }
    if (key.startsWith('attr:')) {
      return { variant: null, metric: key };
    }
    const dotIndex = key.indexOf('.');
    if (dotIndex !== -1) {
      return {
        variant: key.substring(0, dotIndex),
        metric: key.substring(dotIndex + 1)
      };
    }
    return {
      variant: null,
      metric: key
    };
  },

  normalizeMetric(metric) {
    if (!metric) return '';
    if (metric.startsWith('attr:')) return metric;
    const m = metric.toLowerCase();
    if (m === TEST_STATUS.PASS || m === TEST_STATUS.PASSED) return TEST_STATUS.PASSED;
    if (m === TEST_STATUS.FAIL || m === TEST_STATUS.FAILED) return TEST_STATUS.FAILED;
    if (m === 'skip' || m === TEST_STATUS.SKIPPED) return TEST_STATUS.SKIPPED;
    if (m === 'rate' || m === 'passrate' || m === 'pass_rate') return 'rate';
    if (m === 'duration' || m === 'time' || m === 'durationms') return 'duration';
    if (m === 'status') return 'status';
    if (m === 'target') return 'target';
    if (m === 'name') return 'name';
    return m;
  },

  isSortActive(key) {
    if (this.state.sort.by === key) return true;
    const { variant, metric } = this.parseSortKey(key);
    const active = this.parseSortKey(this.state.sort.by);
    const normMetric = this.normalizeMetric(metric);
    const activeNormMetric = this.normalizeMetric(active.metric);

    if (activeNormMetric !== normMetric) return false;

    if (active.variant && variant) {
      return active.variant === variant;
    }

    const visibleVariants = this.state.filters.variants && this.state.filters.variants.length > 0
      ? this.state.filters.variants
      : (this.state.variants || []);
    if (!active.variant && visibleVariants.length > 0) {
      return variant === visibleVariants[0];
    }

    return false;
  },

  getNodeMetric(node, variantName, metric) {
    if (!node) return 0;
    const normMetric = this.normalizeMetric(metric);
    const suiteFilter = this.state.filters.testSuite;

    let targetVariants = [];
    if (variantName) {
      targetVariants = [variantName];
    } else if (this.state.filters.variants && this.state.filters.variants.length > 0) {
      targetVariants = [this.state.filters.variants[0]];
    } else if (this.state.variants && this.state.variants.length > 0) {
      targetVariants = [this.state.variants[0]];
    }

    if (normMetric === 'status') {
      const vName = targetVariants[0];
      const res = vName ? this.getVariantResultForTestCase(node, suiteFilter, vName) : null;
      if (!res) return 3;
      const norm = UIUtils.normalizeStatus(res.status);
      if (norm === TEST_STATUS.FAIL) return 0;
      if (norm === TEST_STATUS.SKIPPED) return 1;
      if (norm === TEST_STATUS.PASS) return 2;
      return 3;
    }

    if (normMetric === 'duration') {
      let sumDur = 0;
      let hasDur = false;

      targetVariants.forEach(v => {
        const d = this.getNodeVariantDurationMs(node, v);
        if (d !== null && d !== undefined && !Number.isNaN(Number(d))) {
          sumDur += Number(d);
          hasDur = true;
        }
      });
      return hasDur ? sumDur : null;
    }

    let summariesList = [];
    if (node.target && node.target.testSuiteSummaries) {
      summariesList = [node.target.testSuiteSummaries];
    } else if (node.targets && node.targets.length > 0) {
      summariesList = node.targets.map(t => t.testSuiteSummaries).filter(Boolean);
    } else if (node.testSuiteSummaries) {
      summariesList = [node.testSuiteSummaries];
    }

    if (summariesList.length === 0) return 0;

    let totalPassed = 0;
    let totalFailed = 0;
    let totalSkipped = 0;

    summariesList.forEach(testSuiteSummaries => {
      let suiteSummary = null;
      if (suiteFilter === 'all') {
        suiteSummary = testSuiteSummaries.find(ts => ts.name === AGGREGATED_SUITE_NAME);
      } else {
        suiteSummary = testSuiteSummaries.find(ts => ts.name === suiteFilter);
      }
      if (!suiteSummary || !suiteSummary.variantSummaries) return;

      suiteSummary.variantSummaries.forEach(vs => {
        if (targetVariants.includes(vs.name)) {
          totalPassed += vs.passed || 0;
          totalFailed += vs.failed || 0;
          totalSkipped += vs.skipped || 0;
        }
      });
    });

    if (normMetric === TEST_STATUS.PASSED) return totalPassed;
    if (normMetric === TEST_STATUS.FAILED) return totalFailed;
    if (normMetric === TEST_STATUS.SKIPPED) return totalSkipped;
    if (normMetric === 'rate') {
      if (summariesList.length === 1 && targetVariants.length === 1) {
        const testSuiteSummaries = summariesList[0];
        const suiteSummary = (suiteFilter === 'all')
          ? testSuiteSummaries.find(ts => ts.name === AGGREGATED_SUITE_NAME)
          : testSuiteSummaries.find(ts => ts.name === suiteFilter);
        if (suiteSummary && suiteSummary.variantSummaries) {
          const vs = suiteSummary.variantSummaries.find(s => s.name === targetVariants[0]);
          if (vs && vs.rate !== undefined) return vs.rate;
        }
      }
      const relevant = totalPassed + totalFailed;
      return relevant > 0 ? (totalPassed / relevant) * 100 : 0;
    }
    return 0;
  },

  compareNodes(a, b) {
    const { by, order } = this.state.sort;
    const { variant, metric } = this.parseSortKey(by);
    const normMetric = this.normalizeMetric(metric);

    if (normMetric === 'name') {
      const valA = (a.name || '').toLowerCase();
      const valB = (b.name || '').toLowerCase();
      if (valA < valB) return order === 'asc' ? -1 : 1;
      if (valA > valB) return order === 'asc' ? 1 : -1;
      return 0;
    }

    if (normMetric === 'target') {
      const tgtA = ((a.target && a.target.name) || '').toLowerCase();
      const tgtB = ((b.target && b.target.name) || '').toLowerCase();
      if (tgtA !== tgtB) {
        if (!tgtA) return 1;
        if (!tgtB) return -1;
        if (tgtA < tgtB) return order === 'asc' ? -1 : 1;
        if (tgtA > tgtB) return order === 'asc' ? 1 : -1;
      }
    } else if (normMetric.startsWith('attr:')) {
      const attrKey = normMetric.slice(5);
      const suiteName = this.state.filters.testSuite;
      const vName = variant || (this.state.filters.variants && this.state.filters.variants[0]) || (this.state.variants && this.state.variants[0]);
      const resA = vName ? this.getVariantResultForTestCase(a, suiteName, vName) : null;
      const resB = vName ? this.getVariantResultForTestCase(b, suiteName, vName) : null;
      const attrA = this.getSummaryAttr(resA, attrKey);
      const attrB = this.getSummaryAttr(resB, attrKey);
      const strA = attrA ? (attrA.displayValue || attrA.value || '').toLowerCase() : '';
      const strB = attrB ? (attrB.displayValue || attrB.value || '').toLowerCase() : '';
      if (strA !== strB) {
        if (!strA) return 1;
        if (!strB) return -1;
        if (strA < strB) return order === 'asc' ? -1 : 1;
        if (strA > strB) return order === 'asc' ? 1 : -1;
      }
    } else {
      const valA = this.getNodeMetric(a, variant, normMetric);
      const valB = this.getNodeMetric(b, variant, normMetric);

      if (valA !== valB) {
        if (valA === null || valA === undefined) return 1;
        if (valB === null || valB === undefined) return -1;
        return order === 'asc' ? valA - valB : valB - valA;
      }
    }

    // Tie-breaker: sort by name ascending
    const nameA = (a.name || '').toLowerCase();
    const nameB = (b.name || '').toLowerCase();
    if (nameA < nameB) return -1;
    if (nameA > nameB) return 1;

    const parentA = (a.parent || a.moduleName || '').toLowerCase();
    const parentB = (b.parent || b.moduleName || '').toLowerCase();
    if (parentA < parentB) return -1;
    if (parentA > parentB) return 1;

    return 0;
  }
};

/**
 * Navigation handler for history management (Back/Forward buttons).
 */
const Navigation = {
  isPopping: false,
  inPageDepth: 0,
  hasReportEntry: false,

  init() {
    // Store initial state
    this.inPageDepth = 0;
    this.hasReportEntry = TestReportApp.state.currentView === 'report';
    history.replaceState(this.captureState(), "");

    window.onpopstate = (event) => {
      if (event.state) {
        this.isPopping = true;
        this.applyState(event.state);
        this.isPopping = false;
      }
    };
  },

  captureState() {
    // Deep copy of state, excluding large/unnecessary parts
    const { processedData, variants, ...restOfState } = TestReportApp.state;
    const state = JSON.parse(JSON.stringify(restOfState));

    // Always remove density from history state to retain user choice across navigation
    delete state.density;
    state._navDepth = this.inPageDepth;

    state.chipVisibility = {
      status: !TestReportApp.elements.statusChipContainer.classList.contains('hidden'),
      module: !TestReportApp.elements.modChipContainer.classList.contains('hidden'),
      package: !TestReportApp.elements.pkgChipContainer.classList.contains('hidden'),
      class: !TestReportApp.elements.clsChipContainer.classList.contains('hidden'),
      testCase: !TestReportApp.elements.tcChipContainer.classList.contains('hidden'),
      target: !TestReportApp.elements.tgtChipContainer.classList.contains('hidden')
    };

    return state;
  },

  push() {
    if (this.isPopping) return;
    this.inPageDepth++;
    history.pushState(this.captureState(), "");
  },

  replace() {
    if (this.isPopping) return;
    history.replaceState(this.captureState(), "");
  },

  applyState(state) {
    if (!state) return;

    const oldView = TestReportApp.state.currentView;
    const selectionChanged = (
      TestReportApp.state.selectedModule !== state.selectedModule ||
      TestReportApp.state.selectedPackage !== state.selectedPackage ||
      TestReportApp.state.selectedClass !== state.selectedClass ||
      TestReportApp.state.currentFlatView !== state.currentFlatView ||
      TestReportApp.state.viewMode !== state.viewMode ||
      JSON.stringify(TestReportApp.state.filters) !== JSON.stringify(state.filters) ||
      TestReportApp.state.sort.by !== state.sort.by ||
      TestReportApp.state.sort.order !== state.sort.order
    );

    const { _navDepth, chipVisibility, ...cleanState } = state;
    this.inPageDepth = typeof _navDepth === 'number' ? _navDepth : 0;

    // Restore state
    TestReportApp.state = {
      ...TestReportApp.state,
      ...cleanState
    };
    if (!TestReportApp.state.filters.suiteAttributes) {
      TestReportApp.state.filters.suiteAttributes = {};
    }

    // Restore chip visibility before layout sync inspects chip visibility
    if (chipVisibility && TestReportApp.elements) {
      TestReportApp.elements.statusChipContainer.classList.toggle('hidden', !chipVisibility.status);
      TestReportApp.elements.modChipContainer.classList.toggle('hidden', !chipVisibility.module);
      TestReportApp.elements.pkgChipContainer.classList.toggle('hidden', !chipVisibility.package);
      TestReportApp.elements.clsChipContainer.classList.toggle('hidden', !chipVisibility.class);
      TestReportApp.elements.tcChipContainer.classList.toggle('hidden', !chipVisibility.testCase);
      if (TestReportApp.elements.tgtChipContainer) {
        TestReportApp.elements.tgtChipContainer.classList.toggle('hidden', !chipVisibility.target);
      }
    }

    const actualSuites = (TestReportApp.state.testSuites || []).filter(ts => ts !== AGGREGATED_SUITE_NAME);
    if (actualSuites.length === 1) {
      TestReportApp.state.filters.testSuite = actualSuites[0];
    }
    TestReportApp.syncSortModeWithLayout();

    // Update search UI to match restored state
    if (TestReportApp.elements.searchInput) {
      TestReportApp.elements.searchInput.value = TestReportApp.state.filters.search || '';
      if (TestReportApp.elements.searchInput.value) {
        if (TestReportApp.elements.searchWrapper) {
          TestReportApp.elements.searchWrapper.classList.add('expanded');
        }
        if (TestReportApp.elements.searchRevealBtn) {
          TestReportApp.elements.searchRevealBtn.classList.add('transparent');
        }
        if (TestReportApp.elements.searchClearBtn) {
          TestReportApp.elements.searchClearBtn.classList.remove('hidden');
        }
      } else {
        if (TestReportApp.elements.searchWrapper) {
          TestReportApp.elements.searchWrapper.classList.remove('expanded');
        }
        if (TestReportApp.elements.searchRevealBtn) {
          TestReportApp.elements.searchRevealBtn.classList.remove('transparent');
          TestReportApp.elements.searchRevealBtn.classList.remove('hidden');
        }
        if (TestReportApp.elements.searchClearBtn) {
          TestReportApp.elements.searchClearBtn.classList.add('hidden');
        }
      }
    }

    // Update segments UI
    TestReportApp.updateViewModeUI();

    if (TestReportApp.elements.densitySegments) {
      TestReportApp.elements.densitySegments.querySelectorAll('.segment-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.value === TestReportApp.state.density);
      });
      if (TestReportApp.elements.mainTable) {
        if (TestReportApp.state.density === 'compact') {
          TestReportApp.elements.mainTable.classList.add('table-compact');
        } else {
          TestReportApp.elements.mainTable.classList.remove('table-compact');
        }
      }
    }

    // Rebuild static dropdowns to reflect restored state (test suite, variants, status)
    TestReportApp.populateFilters();

    // Update test suite UI specifically since populateFilters handles the dropdown but not the external UI elements fully unless changed
    TestReportApp.updateTestSuiteUI();

    // Re-render
    if (TestReportApp.state.currentView === 'stack-trace') {
      TestReportApp.showStackTraceView(TestReportApp.state.currentTestCase, TestReportApp.state.currentStackTraceContext);
    } else {
      const willRender = Boolean(selectionChanged || oldView === 'report');
      const { wasDetailView, wasRichDetail } = TestReportApp.showReportView(oldView === 'stack-trace', willRender);
      if (willRender) {
        TestReportApp.render();
        TestReportApp.restoreReportScrollAndFocus(wasDetailView, wasRichDetail);
      }
    }
    TestReportApp.updateFilterButtons();
  }
};

document.addEventListener('DOMContentLoaded', () => {
  TestReportApp.init();
  initHelpHub();
});

/**
 * HELP HUB INITIALIZATION
 */
function initHelpHub() {
  const helpHubFab = document.getElementById("help-hub-fab");
  const helpHubPanel = document.getElementById("help-hub-panel");
  const closeHelpHubBtn = document.getElementById("close-help-hub");

  if (!helpHubFab || !helpHubPanel) return;

  function togglePanel(open) {
    const isOpening = typeof open === 'boolean' ? open : !helpHubPanel.classList.contains("open");
    helpHubPanel.classList.toggle("open", isOpening);
    helpHubFab.setAttribute("aria-expanded", isOpening);
    helpHubPanel.setAttribute("aria-hidden", !isOpening);

    if (isOpening) {
      requestAnimationFrame(() => {
        closeHelpHubBtn?.focus();
      });
    } else {
      helpHubFab.focus();
    }
  }

  // Focus Trap
  helpHubPanel.addEventListener("keydown", (e) => {
    if (e.key !== "Tab") return;

    const focusableElements = helpHubPanel.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];

    if (e.shiftKey) { // Shift + Tab
      if (document.activeElement === firstElement) {
        lastElement.focus();
        e.preventDefault();
      }
    } else { // Tab
      if (document.activeElement === lastElement) {
        firstElement.focus();
        e.preventDefault();
      }
    }
  });

  helpHubFab.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    togglePanel();
  });

  if (closeHelpHubBtn) {
    closeHelpHubBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      togglePanel(false);
    });
  }

  document.addEventListener("click", (e) => {
    if (helpHubPanel.classList.contains("open") && !helpHubPanel.contains(e.target) && !helpHubFab.contains(e.target)) {
      togglePanel(false);
    }
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && helpHubPanel.classList.contains("open")) {
      togglePanel(false);
    }
  });

  const legendItems = helpHubPanel.querySelectorAll(".legend-item");
  legendItems.forEach(item => {
    const header = item.querySelector(".legend-item-header");
    const toggle = () => {
      const isOpen = item.classList.contains("open");
      legendItems.forEach(i => {
        i.classList.remove("open");
        const h = i.querySelector(".legend-item-header");
        if (h) h.setAttribute("aria-expanded", "false");
      });
      item.classList.toggle("open", !isOpen);
      header.setAttribute("aria-expanded", !isOpen);
    };

    header.addEventListener("click", (e) => {
      e.stopPropagation();
      toggle();
    });

    header.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        e.stopPropagation();
        toggle();
      }
    });
  });
}

globalThis.TEST_STATUS = TEST_STATUS;
globalThis.UIUtils = UIUtils;
globalThis.TestReportApp = TestReportApp;
globalThis.Navigation = Navigation;
globalThis.Tooltip = Tooltip;
