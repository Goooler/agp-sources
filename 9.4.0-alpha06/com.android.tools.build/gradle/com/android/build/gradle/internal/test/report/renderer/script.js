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
/**
 * UI Utilities
 * Collection of helper functions for DOM manipulation and common UI patterns.
 */
const UIUtils = {
  /**
   * Escapes special characters for use in HTML content and attributes.
   */
  escapeHTML(str) {
    if (!str) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
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

const Tooltip = {
  element: null,
  activeTarget: null,
  hideTimeout: null,

  init() {
    this.element = document.getElementById('a11y-tooltip');
    if (!this.element) return;

    document.body.addEventListener('mouseover', (e) => {
      const target = e.target.closest('[data-tooltip], [title], [data-stored-title]');
      if (target && !target.contains(e.relatedTarget)) this.show(target);
    });

    document.body.addEventListener('mouseout', (e) => {
      const target = e.target.closest('[data-tooltip], [title], [data-stored-title]');
      if (target && !target.contains(e.relatedTarget)) {
        this.startHide();
      }
    });

    document.body.addEventListener('focusin', (e) => {
      const target = e.target.closest('[data-tooltip], [title], [data-stored-title]');
      if (target && !target.contains(e.relatedTarget)) this.show(target);
    });

    document.body.addEventListener('focusout', (e) => {
      const target = e.target.closest('[data-tooltip], [title], [data-stored-title]');
      if (target && !target.contains(e.relatedTarget)) {
        this.hide();
      }
    });

    // Dismiss tooltip on scroll (prevent detaching from element)
    window.addEventListener('scroll', () => this.hide(), true);

    // Keep tooltip open when hovering over it
    this.element.addEventListener('mouseenter', () => this.clearHide());
    this.element.addEventListener('mouseleave', () => this.startHide());
  },

  show(target) {
    this.clearHide();

    if (this.activeTarget && this.activeTarget !== target) {
      this.hide();
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
    this.element.textContent = text;
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
    const originalLeft = left;
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

  state: {
    viewMode: 'flat',
    density: 'comfy',
    currentFlatView: 'modules',
    selectedModule: null,
    selectedPackage: null,
    selectedClass: null,
    currentView: 'report',
    currentTestCase: null,
    currentStackTraceContext: {},
    filters: { variants: [], search: '', status: ['passed', 'failed', 'skipped'], testSuite: 'all', modules: [], packages: [], classes: [], testCases: [] },
    sort: { by: 'name', order: 'asc' },
    isResizing: false,
    columnWidths: {},
    variants: [],
    processedData: null
  },
  elements: {},

  init: function () {
    this.baseTitle = document.title;
    this.cacheDOMElements();
    Tooltip.init();

    // Default to Flat Modules View on initial page open unless hash/history specifies otherwise
    if (!window.location.hash || window.location.hash === '#report-view' || window.location.hash === '#') {
      this.state.viewMode = 'flat';
      this.state.currentFlatView = 'modules';
      this.state.selectedModule = null;
      this.state.selectedPackage = null;
      this.state.selectedClass = null;
      this.state.currentView = 'report';
      this.state.currentTestCase = null;
    }

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
      totalModules: document.getElementById('total-modules'),
      totalPackages: document.getElementById('total-packages'),
      totalClasses: document.getElementById('total-classes'),
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
      stackTraceBreadcrumbs: document.getElementById('stack-trace-breadcrumbs'),
      stackTraceGrid: document.getElementById('stack-trace-grid'),
    };
  },

  setupTestResults(rootReport) {
    this.state.variants = rootReport.variants;
    this.state.testSuites = rootReport.testSuites;
    this.state.filters.variants = [...rootReport.variants];

    // Populate header
    if (this.elements.appTitle) this.elements.appTitle.textContent = rootReport.projectName || 'Test Report';
    if (this.elements.reportDate) this.elements.reportDate.textContent = rootReport.timestamp || '';
    if (this.elements.totalModules) this.elements.totalModules.textContent = rootReport.numberOfModules || 0;
    if (this.elements.totalPackages) this.elements.totalPackages.textContent = rootReport.numberOfPackages || 0;
    if (this.elements.totalClasses) this.elements.totalClasses.textContent = rootReport.numberOfClasses || 0;

    this.processedData = rootReport;

    // Build O(1) test case index asynchronously in time-sliced background chunks to prevent UI thread freezing
    this.buildTestCaseIndexAsync(rootReport);
  },

  async buildTestCaseIndexAsync(rootReport) {
    this.testCaseIndex = new Map();

    const annotateType = (node, type) => {
      node.type = type;
      const childType = this.getChildType(type);
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
            const entry = { testCase: tc, moduleName: m.name, packageName: p.name, className: c.name };
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
    const testSuiteOptions = [
      { name: 'All', value: 'all' },
      ...this.state.testSuites.filter(ts => ts !== 'Aggregated').map(ts => ({ name: ts, value: ts }))
    ];
    UIUtils.buildActionDropdown(this.elements.testSuiteFilterList, testSuiteOptions, this.state.filters.testSuite, (newVal) => {
      this.state.filters.testSuite = newVal;

      if (newVal === 'all') {
        this.elements.tsAllState.classList.remove('hidden');
        this.elements.tsSelectedState.classList.add('hidden');
      } else {
        this.elements.tsAllState.classList.add('hidden');
        this.elements.tsSelectedState.classList.remove('hidden');
        this.elements.testSuiteFilterText.textContent = newVal;
      }
      this.render();
      Navigation.push();
    }, false, false);

    // Variants Dropdown
    const variantOptions = this.state.variants.map(v => ({ name: v, value: v }));
    UIUtils.buildActionDropdown(this.elements.variantFilterList, variantOptions, this.state.filters.variants, (newArr) => {
      this.state.filters.variants = newArr;
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
      { name: 'Passed', value: 'passed' },
      { name: 'Failed', value: 'failed' },
      { name: 'Skipped', value: 'skipped' }
    ];
    if (!Array.isArray(this.state.filters.status)) {
      this.state.filters.status = ['passed', 'failed', 'skipped'];
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
          history.back();
        } else if (this.elements.searchWrapper && this.elements.searchWrapper.classList.contains('expanded')) {
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
        btn.addEventListener('keydown', (e) => {
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
      }
    });

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

    this.elements.testSuiteFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.testSuiteFilterDropdown, this.elements.testSuiteFilterBtn));
    this.elements.variantFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.variantFilterDropdown, this.elements.variantFilterBtn));
    this.elements.statusFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.statusFilterDropdown, this.elements.statusFilterBtn));

    if (this.elements.moduleFilterBtn) this.elements.moduleFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.moduleFilterDropdown, this.elements.moduleFilterBtn));
    if (this.elements.packageFilterBtn) this.elements.packageFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.packageFilterDropdown, this.elements.packageFilterBtn));
    if (this.elements.classFilterBtn) this.elements.classFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.classFilterDropdown, this.elements.classFilterBtn));
    if (this.elements.tcFilterBtn) this.elements.tcFilterBtn.addEventListener('click', () => this.toggleDropdown(this.elements.tcFilterDropdown, this.elements.tcFilterBtn));

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
      'testCase': { container: this.elements.tcChipContainer, dropdown: this.elements.tcFilterDropdown, btn: this.elements.tcFilterBtn, stateKey: 'testCases' }
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
            this.state.filters.status = ['passed', 'failed', 'skipped'];
            this.buildStatusDropdown();
          } else {
            this.state.filters[config.stateKey] = [];
          }
          config.container.classList.add('hidden');
          this.handleHeaderFilterChange();
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

          this.render();
          Navigation.push();
        }
      });
    }
    this.elements.resultsData.addEventListener('click', (e) => {
      const stackTrigger = e.target.closest('.stack-trace-trigger');
      if (stackTrigger) {
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
        const link = e.target.closest('.breadcrumb-link');
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
      if (this.state.sort.by === newSortBy) {
        this.state.sort.order = this.state.sort.order === 'asc' ? 'desc' : 'asc';
      } else {
        this.state.sort.by = newSortBy;
        this.state.sort.order = 'asc';
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

    updateChip('module', filters.modules, totalModules, this.elements.moduleFilterText, this.elements.modChipContainer);
    updateChip('package', filters.packages, totalPackages, this.elements.packageFilterText, this.elements.pkgChipContainer);
    updateChip('class', filters.classes, totalClasses, this.elements.classFilterText, this.elements.clsChipContainer);
    updateChip('testCase', filters.testCases, totalTestCases, this.elements.tcFilterText, this.elements.tcChipContainer);

    const isStatusVisible = !this.elements.statusChipContainer.classList.contains('hidden');
    if (isStatusVisible) {
      activeChipsCount++;
      this.toggleAddFilterOption('status', false);
    } else {
      this.toggleAddFilterOption('status', true);
    }

    if (this.elements.addFilterBtn) {
      if (activeChipsCount === 5) { // module, package, class, testCase, status
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
      { btn: this.elements.addFilterBtn, dropdown: this.elements.addFilterDropdown },
      { btn: this.elements.groupByBtn, dropdown: this.elements.groupByDropdown }
    ];
  },

  toggleDropdown(dropdownToToggle, button) {
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
      if (this.elements.searchWrapper && this.elements.searchRevealBtn) {
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
      const viewMap = { 'module': 'modules', 'package': 'packages', 'class': 'classes', 'testCase': 'testCases' };
      if (viewMap[explicitType]) {
        this.state.currentFlatView = viewMap[explicitType];
      }
    } else {
      const { classes, packages, modules, testCases } = this.state.filters;
      const activeChips = [];
      if (!this.elements.tcChipContainer.classList.contains('hidden')) activeChips.push('testCase');
      if (!this.elements.clsChipContainer.classList.contains('hidden')) activeChips.push('class');
      if (!this.elements.pkgChipContainer.classList.contains('hidden')) activeChips.push('package');
      if (!this.elements.modChipContainer.classList.contains('hidden')) activeChips.push('module');

      if (testCases.length > 0 || activeChips.includes('testCase')) {
        this.state.currentFlatView = 'testCases';
      } else if (classes.length > 0 || activeChips.includes('class')) {
        this.state.currentFlatView = 'classes';
      } else if (packages.length > 0 || activeChips.includes('package')) {
        this.state.currentFlatView = 'packages';
      } else {
        // Always fallback to modules if deeper hierarchies aren't active
        this.state.currentFlatView = 'modules';
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

  // --- DATA PROCESSING & FILTERING ---
  getFilteredAndSortedData() {
    if (!this.processedData) return { modules: [] };
    const finalData = JSON.parse(JSON.stringify(this.processedData));

    const applyFilters = (nodes, type, parentMatchesSearch = false) => {
      if (!nodes) return [];
      const hasSearch = !!this.state.filters.search;
      const hasDropdownFilters = this.state.filters.modules.length > 0 ||
        this.state.filters.packages.length > 0 ||
        this.state.filters.classes.length > 0 ||
        this.state.filters.testCases.length > 0;
      const ALL_STATUSES = ['passed', 'failed', 'skipped'];
      const hasStatusFilters = this.state.filters.status.length < ALL_STATUSES.length;
      const hasTestSuiteFilter = this.state.filters.testSuite !== 'all';
      const isFiltering = hasSearch || hasDropdownFilters || hasStatusFilters || hasTestSuiteFilter;

      return nodes.filter(node => {
        // Fast paths: discard outright if missing explicit filters.
        if (type === 'module' && this.state.filters.modules.length > 0 && !this.state.filters.modules.includes(node.name)) return false;
        if (type === 'package' && this.state.filters.packages.length > 0 && !this.state.filters.packages.includes(node.name)) return false;
        if (type === 'class' && this.state.filters.classes.length > 0 && !this.state.filters.classes.includes(node.name)) return false;
        if (type === 'testCase' && this.state.filters.testCases.length > 0 && !this.state.filters.testCases.includes(node.name)) return false;

        // Drill-down selection filters (Flat view only)
        if (this.state.viewMode === 'flat') {
          if (type === 'module' && this.state.selectedModule && this.state.selectedModule !== node.name) return false;
          if (type === 'package' && this.state.selectedPackage && this.state.selectedPackage !== node.name) return false;
          if (type === 'class' && this.state.selectedClass && this.state.selectedClass !== node.name) return false;
        }

        // Search filter
        let selfMatchesSearch = false;
        if (hasSearch) {
          const searchTerm = this.state.filters.search.toLowerCase();
          selfMatchesSearch = node.name.toLowerCase().includes(searchTerm);
        }

        const effectiveMatchesSearch = hasSearch ? (selfMatchesSearch || parentMatchesSearch) : false;

        const childKey = this.pluralize(this.getChildType(type));
        let children = node[childKey] || (type === 'class' ? node.testCases : []);
        let hasVisibleChildren = false;

        // Filter children first
        if (children) {
          const filteredChildren = applyFilters(children, this.getChildType(type), effectiveMatchesSearch);
          if (type === 'class') node.testCases = filteredChildren;
          else node[childKey] = filteredChildren;
          hasVisibleChildren = filteredChildren.length > 0;
        }

        // Test Suite Filter
        if (hasTestSuiteFilter && type !== 'testCase') {
          if (node.testSuiteSummaries) {
            const suiteMatch = node.testSuiteSummaries.find(ts => ts.name === this.state.filters.testSuite);
            if (!suiteMatch) return false;
          } else {
            return false;
          }
        }

        let matchesStatus = true;
        if (hasStatusFilters) {
          const isLeaf = !this.getChildType(type);
          if (isLeaf) {
            let hasFail = false;
            let hasPass = false;
            let hasSkipped = false;

            this.state.filters.variants.forEach(v => {
              const res = this.getVariantResultForTestCase(node, this.state.filters.testSuite, v);
              if (res) {
                if (res.status === 'fail') hasFail = true;
                if (res.status === 'pass') hasPass = true;
                if (res.status === 'skipped') hasSkipped = true;
              }
            });

            matchesStatus = false;
            if (hasPass && this.state.filters.status.includes('passed')) matchesStatus = true;
            if (hasFail && this.state.filters.status.includes('failed')) matchesStatus = true;
            if (hasSkipped && this.state.filters.status.includes('skipped')) matchesStatus = true;

            if (!matchesStatus) return false;
          }
        }

        // Final evaluation check
        if (!isFiltering) return true;

        if (!this.getChildType(type)) { // Leaf node (testCase)
          return hasSearch ? effectiveMatchesSearch : true;
        }

        return effectiveMatchesSearch || hasVisibleChildren;
      });
    };

    const sortNodes = (nodes) => {
      if (!nodes) return;
      nodes.sort((a, b) => {
        const valA = a.name.toLowerCase();
        const valB = b.name.toLowerCase();
        if (valA < valB) return this.state.sort.order === 'asc' ? -1 : 1;
        if (valA > valB) return this.state.sort.order === 'asc' ? 1 : -1;
        return 0;
      });

      nodes.forEach(node => {
        const childKey = this.pluralize(this.getChildType(node.type));
        let children = node[childKey] || (node.type === 'class' ? node.testCases : []);
        if (children) sortNodes(children);
      });
    };

    finalData.modules = applyFilters(finalData.modules, 'module');
    if (this.state.sort.by === 'name') {
      sortNodes(finalData.modules);
    }

    return finalData;
  },

  // --- RENDERING ---
  render() {
    this.updateDynamicFilters();

    if (this.state.currentView === 'stack-trace') {
      this.renderStackTraceGrid(this.state.currentTestCase);
    } else {
      const data = this.getFilteredAndSortedData();
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

    UIUtils.buildActionDropdown(this.elements.moduleFilterDropdown, moduleOptions, filters.modules, (newArr) => {
      filters.packages = [];
      filters.classes = [];
      filters.testCases = [];
      filters.modules = newArr;
      this.handleHeaderFilterChange('module');
      this.updateFilterButtons();
      this.render();
      Navigation.push();
    });

    UIUtils.buildActionDropdown(this.elements.packageFilterDropdown, packageOptions, filters.packages, (newArr) => {
      filters.classes = [];
      filters.testCases = [];
      filters.packages = newArr;
      this.handleHeaderFilterChange('package');
      this.updateFilterButtons();
      this.render();
      Navigation.push();
    });

    UIUtils.buildActionDropdown(this.elements.classFilterDropdown, classOptions, filters.classes, (newArr) => {
      filters.testCases = [];
      filters.classes = newArr;
      this.handleHeaderFilterChange('class');
      this.updateFilterButtons();
      this.render();
      Navigation.push();
    });

    UIUtils.buildActionDropdown(this.elements.tcFilterDropdown, testCaseOptions, filters.testCases, (newArr) => {
      filters.testCases = newArr;
      this.handleHeaderFilterChange('testCase');
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
    this.elements.groupByText.textContent = viewMap[this.state.currentFlatView] || 'Modules';

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
    this.renderHeaders();
    this.renderBreadcrumbs();
    if (this.state.viewMode === 'tree') {
      this.renderTreeRows(data);
    } else {
      this.renderFlatRows(data);
    }
  },

  renderHeaders() {
    const variantsToShow = this.state.filters.variants;
    const sortIndicator = (key) => this.state.sort.by === key ? (this.state.sort.order === 'asc' ? '▲' : '▼') : '';
    const getAriaSort = (key) => this.state.sort.by === key ? (this.state.sort.order === 'asc' ? 'ascending' : 'descending') : 'none';
    let nameHeader = this.state.viewMode === 'tree' ? 'Name' : this.state.currentFlatView.charAt(0).toUpperCase() + this.state.currentFlatView.slice(1);

    let pathHeader = '';
    let pathSubHeader = '';
    if (this.state.viewMode === 'flat' && !this.state.selectedModule) {
      if (this.state.currentFlatView === 'classes' || this.state.currentFlatView === 'testCases') {
        const pathWidth = Math.round(this.state.columnWidths['path'] || 300);
        pathHeader = `<th scope="col" class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30 col-path">Path<div class="resizer" data-resizer-id="path" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${pathWidth}"></div></th>`;
        pathSubHeader = `<th scope="col" class="py-2 px-6 bg-gray-50 z-30 col-path"></th>`;
      } else if (this.state.currentFlatView === 'packages') {
        const moduleWidth = Math.round(this.state.columnWidths['module'] || 200);
        pathHeader = `<th scope="col" class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30 col-module">Module<div class="resizer" data-resizer-id="module" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${moduleWidth}"></div></th>`;
        pathSubHeader = `<th scope="col" class="py-2 px-6 bg-gray-50 z-30 col-module"></th>`;
      }
    }

    const nameWidth = Math.round(this.state.columnWidths['name'] || 400);
    this.elements.tableHeaders.innerHTML = `
            <tr class="border-b border-gray-200">
                <th scope="col" class="py-4 px-6 text-left font-semibold text-gray-700 sticky-name bg-gray-50 z-30 cursor-pointer" data-sort-by="name" tabindex="0" aria-sort="${getAriaSort('name')}">${nameHeader} ${sortIndicator('name')}<div class="resizer" data-resizer-id="name" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${nameWidth}"></div></th>
                ${pathHeader}
                ${variantsToShow.map(v => `<th scope="col" class="py-4 px-4 text-center font-semibold text-gray-700 border-l border-gray-200" colspan="4">${UIUtils.escapeHTML(v)}</th>`).join('')}
            </tr>
            <tr class="border-b border-gray-200">
                <th scope="col" class="py-2 px-6 sticky-name bg-gray-50 z-30"></th>
                ${pathSubHeader}
                ${variantsToShow.map(v => `<th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600 border-l border-gray-200">Pass</th><th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600">Fail</th><th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600">Skip</th><th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600">Pass Rate</th>`).join('')}
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

      const childType = this.getChildType(type);
      const childKey = this.pluralize(childType);
      const children = node[childKey] || (type === 'class' ? node.testCases : []) || [];
      const hasChildren = children.length > 0;
      const uniqueId = `${parentId}-${node.name}`.replace(/[^a-zA-Z0-9-_]/g, '');

      let nameContent = `<span class="font-medium">${UIUtils.escapeHTML(node.name)}</span>`;
      if (type === 'testCase' && this.isTestCaseClickable(node)) {
        nameContent = `<span class="font-medium text-blue-700 hover-underline cursor-pointer stack-trace-trigger" data-module="${UIUtils.escapeHTML(currentContext.moduleName || '')}" data-package="${UIUtils.escapeHTML(currentContext.packageName || '')}" data-class="${UIUtils.escapeHTML(currentContext.className || '')}" data-test-case="${UIUtils.escapeHTML(node.name || '')}" tabindex="0" role="button" aria-label="View details for ${UIUtils.escapeHTML(node.name)}">${UIUtils.escapeHTML(node.name)}</span>`;
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

  renderFlatRows(data) {
    let items = [];
    const view = this.state.currentFlatView;
    if (data.modules) {
      if (view === 'modules') items = data.modules.map(i => ({ ...i, type: 'module' }));
      else if (view === 'packages') items = data.modules.flatMap(m => m.packages.map(i => ({ ...i, parent: m.name, moduleName: m.name, type: 'package' })));
      else if (view === 'classes') items = data.modules.flatMap(m => m.packages.flatMap(p => p.classes.map(i => ({ ...i, parent: p.name, moduleName: m.name, packageName: p.name, type: 'class' }))));
      else if (view === 'testCases') items = data.modules.flatMap(m => m.packages.flatMap(p => p.classes.flatMap(c => c.testCases.map(i => ({ ...i, parent: c.name, moduleName: m.name, packageName: p.name, className: c.name, type: 'testCase' })))));
    }

    this.elements.resultsData.innerHTML = items.map(item => {
      let nameTd = `<td class="py-3 px-6 sticky-name font-medium" title="${UIUtils.escapeHTML(item.name)}">${UIUtils.escapeHTML(item.name)}</td>`;
      if (item.type !== 'testCase') {
        nameTd = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover-underline cursor-pointer" tabindex="0" role="link" title="${UIUtils.escapeHTML(item.name)}" data-name="${UIUtils.escapeHTML(item.name)}" data-type="${item.type}" data-module-name="${UIUtils.escapeHTML(item.moduleName || '')}" data-package-name="${UIUtils.escapeHTML(item.packageName || '')}" data-interactive="flat">${UIUtils.escapeHTML(item.name)}</td>`;
      } else if (this.isTestCaseClickable(item)) {
        nameTd = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover-underline cursor-pointer stack-trace-trigger" data-module="${UIUtils.escapeHTML(item.moduleName || '')}" data-package="${UIUtils.escapeHTML(item.packageName || '')}" data-class="${UIUtils.escapeHTML(item.className || '')}" data-test-case="${UIUtils.escapeHTML(item.name || '')}" tabindex="0" role="button" aria-label="View details for ${UIUtils.escapeHTML(item.name)}">${UIUtils.escapeHTML(item.name)}</td>`;
      } else {
        nameTd = `<td class="py-3 px-6 sticky-name font-medium text-gray-800" title="${UIUtils.escapeHTML(item.name)}">${UIUtils.escapeHTML(item.name)}</td>`;
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
        className: item.className
      };

      return `<tr class="table-row">
            ${nameTd}
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

  getVariantResultForTestCase(testCase, suiteName, variantName) {
    // For now, use the first target's properties. This works for all non-GMD current use cases out of the box.
    // Device-specific UI and naming will be added in a subsequent phase (bug b/525671605).
    const testSuiteResults = (testCase.targets && testCase.targets[0]) ? testCase.targets[0].testSuiteResults : testCase.testSuiteResults;
    if (!testSuiteResults) return null;
    let suitesToSearch = [];
    if (suiteName === 'all') {
      suitesToSearch = testSuiteResults;
    } else {
      const specific = testSuiteResults.find(ts => ts.testSuiteName === suiteName);
      if (specific) suitesToSearch = [specific];
    }

    // Priority: Fail > Pass > Skipped
    let finalRes = null;
    for (const suite of suitesToSearch) {
      const res = suite.variantResults[variantName];
      if (res) {
        if (res.status === 'fail') return res;
        if (res.status === 'pass') finalRes = res;
        if (res.status === 'skipped' && !finalRes) finalRes = res;
      }
    }
    return finalRes;
  },

  hasVisibleFailures(node) {
    if (!node || node.type !== 'testCase') return false;
    const activeSuite = this.state.filters.testSuite;
    const activeVariants = this.state.filters.variants;

    // For now, use the first target's properties. This works for all non-GMD current use cases out of the box.
    // Device-specific UI and naming will be added in a subsequent phase (see bug b/525671605).
    const commonStackTraces = (node.targets && node.targets[0]) ? node.targets[0].commonStackTraces : node.commonStackTraces;
    return (commonStackTraces || []).some(group => {
      for (const [suite, variants] of Object.entries(group.occurrences)) {
        if (activeSuite !== 'all' && suite !== activeSuite) continue;
        if (variants.some(v => activeVariants.includes(v))) return true;
      }
      return false;
    });
  },

  isTestCaseClickable(node) {
    if (!node) return false;
    const screenshotItems = this.getScreenshotData(node);
    if (screenshotItems && screenshotItems.length > 0) return true;
    return this.hasVisibleFailures(node);
  },

  _renderStatusCell(node, context = {}) {
    const variantsToShow = this.state.filters.variants;

    if (!node) {
      return variantsToShow.map(() => `<td colspan="4" class="border-l border-gray-200"></td>`).join('');
    }

    const suiteFilter = this.state.filters.testSuite;

    // For now, use the first target's properties. This works for all non-GMD current use cases out of the box.
    // Device-specific UI and naming will be added in a subsequent phase (see bug b/525671605).
    const testSuiteSummaries = (node.targets && node.targets[0]) ? node.targets[0].testSuiteSummaries : node.testSuiteSummaries;

    return `${variantsToShow.map(v => {
      let variantSummary = null;
      if (suiteFilter === 'all') {
        const aggregatedSuite = testSuiteSummaries ? testSuiteSummaries.find(ts => ts.name === 'Aggregated') : null;
        if (aggregatedSuite) {
          variantSummary = aggregatedSuite.variantSummaries.find(vs => vs.name === v);
        }
      } else {
        const suiteSummary = testSuiteSummaries ? testSuiteSummaries.find(ts => ts.name === suiteFilter) : null;
        if (suiteSummary) {
          variantSummary = suiteSummary.variantSummaries.find(vs => vs.name === v);
        }
      }

      if (!variantSummary || variantSummary.total === 0) return '<td colspan="4" class="text-center text-gray-500 border-l border-gray-200">-</td>';

      const { passed, failed, skipped, rate } = variantSummary;
      const relevantTotal = passed + failed;
      const passRateColor = rate >= 95 ? 'text-green-600' : rate >= 80 ? 'text-yellow-600' : 'text-red-600';

      const filter = this.state.filters.status;
      const showPassed = filter.includes('passed');
      const showFailed = filter.includes('failed');
      const showSkipped = filter.includes('skipped');

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
    const { module, package: pkg, class: clz, testCase: tcName } = element.dataset;
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
      const context = {
        moduleName: module || res.moduleName,
        packageName: pkg || res.packageName,
        className: clz || res.className,
        testCaseName: tcName
      };
      this.showStackTraceView(res.testCase, context);
      Navigation.push();
    }
  },

  showStackTraceView(testCase, context) {
    this.announce(`Showing stack trace for test case: ${testCase.name}`);
    document.title = `Stack Trace: ${testCase.name} - ${this.baseTitle}`;
    this.state.currentView = 'stack-trace';
    this.state.currentTestCase = testCase;
    this.state.currentStackTraceContext = context;

    this.elements.reportView.classList.add('hidden-view');
    this.elements.stackTraceView.classList.remove('hidden-view');
    if (this.elements.filterControlsGroup) {
      this.elements.filterControlsGroup.classList.add('hidden');
    }
    if (this.elements.searchContainer) this.elements.searchContainer.classList.add('hidden');
    if (this.elements.viewSegments) this.elements.viewSegments.classList.add('hidden');
    if (this.elements.densitySegments) this.elements.densitySegments.classList.add('hidden');

    this.renderStackTraceGrid(testCase);
    this.renderStackTraceBreadcrumbs(context);
    window.scrollTo(0, 0);
  },

  renderStackTraceGrid(testCase) {
    const grid = this.elements.stackTraceGrid;
    grid.innerHTML = "";

    const activeSuite = this.state.filters.testSuite;
    const activeVariants = this.state.filters.variants;

    const screenshotItems = this.getScreenshotData(testCase);
    if (screenshotItems.length > 0) {
      this.renderScreenshotTestView(grid, testCase, screenshotItems);
      return;
    }

    // For now, use the first target's properties. This works for all non-GMD current use cases out of the box.
    // Device-specific UI and naming will be added in a subsequent phase (see bug b/525671605).
    const commonStackTraces = (testCase.targets && testCase.targets[0]) ? testCase.targets[0].commonStackTraces : testCase.commonStackTraces;

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
      titleDiv.className = "flex items-center gap-2";
      titleDiv.innerHTML = `
            <svg aria-hidden="true" focusable="false" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-red-600">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
                <line x1="12" y1="9" x2="12" y2="13"></line>
                <line x1="12" y1="17" x2="12.01" y2="17"></line>
            </svg>
            <h2 class="text-sm font-semibold text-gray-900" id="${titleId}">Stack Trace</h2>
        `;
      header.appendChild(titleDiv);

      const occurrencesDiv = document.createElement("div");
      occurrencesDiv.className = "flex flex-wrap gap-1 mt-2";

      for (const [suite, variants] of Object.entries(group.filteredOccurrences)) {
        const tag = document.createElement("span");
        tag.className = "occurrence-tag";
        tag.textContent = `${suite} (${variants.join(", ")})`;
        occurrencesDiv.appendChild(tag);
      }
      header.appendChild(occurrencesDiv);
      variantView.appendChild(header);

      const container = document.createElement("div");
      container.className = "code-container";
      container.setAttribute("tabindex", "0");
      container.setAttribute("aria-labelledby", titleId);

      const pre = document.createElement("pre");
      pre.className = "font-mono text-sm text-red-600 whitespace-pre-wrap break-all";
      pre.textContent = group.stackTrace;
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


  getScreenshotData(testCase) {
    const items = [];
    const targets = testCase.targets || [testCase];

    targets.forEach(target => {
      const results = target.testSuiteResults || [];
      results.forEach(suiteResult => {
        const suiteName = suiteResult.testSuiteName || "screenshotTest";
        const variantResults = suiteResult.variantResults || {};

        for (const [variantName, res] of Object.entries(variantResults)) {
          if (res.refImagePath || res.newImagePath || res.diffImagePath || suiteName === "screenshotTest" || res.previewName) {
            let stackTrace = "";
            const commonStackTraces = target.commonStackTraces || testCase.commonStackTraces || [];
            if (res.stackTraceId) {
              const match = commonStackTraces.find(st => st.id === res.stackTraceId);
              if (match) stackTrace = match.stackTrace;
            }
            if (!stackTrace && commonStackTraces.length > 0) {
              stackTrace = commonStackTraces[0].stackTrace;
            }

            items.push({
              targetName: target.name || "",
              suiteName: suiteName,
              variantName: variantName,
              status: res.status || "pass",
              previewName: res.previewName || testCase.name,
              methodName: res.methodName || testCase.name,
              refImagePath: res.refImagePath || "",
              newImagePath: res.newImagePath || "",
              diffImagePath: res.diffImagePath || "",
              stackTrace: stackTrace
            });
          }
        }
      });
    });
    return items;
  },

  resolveImagePath(imagePath) {
    if (!imagePath) return '';
    if (imagePath.startsWith('http://') || imagePath.startsWith('https://') || imagePath.startsWith('data:') || imagePath.startsWith('file://')) {
      return imagePath;
    }
    let cleanPath = imagePath.startsWith('/') ? imagePath.slice(1) : imagePath;
    if (cleanPath.startsWith('../')) {
      return cleanPath;
    }
    return '../../../../../' + cleanPath;
  },

  renderScreenshotTestView(container, testCase, screenshotItems) {
    const activeVariants = this.state.filters.variants || [];
    let item = screenshotItems.find(i => activeVariants.includes(i.variantName)) || screenshotItems[0];

    const isPassed = item.status === 'pass';
    const isFailed = item.status === 'fail';
    const statusClass = isPassed ? 'pass' : (isFailed ? 'fail' : 'error');
    const statusText = isPassed ? 'PASSED' : (isFailed ? 'FAILED' : 'ERROR');

    const wrapper = document.createElement('div');
    wrapper.className = 'screenshot-view-container';

    // 1. Header Card
    const headerCard = document.createElement('div');
    headerCard.className = 'screenshot-header-card';
    headerCard.innerHTML = `
      <div class="screenshot-title-zone">
        <div class="screenshot-main-title">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
          <span>${UIUtils.escapeHTML(testCase.name)}</span>
        </div>
        <div class="screenshot-meta-badges">
          <span class="meta-pill"><span class="meta-pill-label">Method:</span> ${UIUtils.escapeHTML(item.methodName)}</span>
          <span class="meta-pill"><span class="meta-pill-label">Preview:</span> ${UIUtils.escapeHTML(item.previewName)}</span>
          <span class="meta-pill"><span class="meta-pill-label">Variant:</span> ${UIUtils.escapeHTML(item.variantName)}</span>
          <span class="meta-pill"><span class="meta-pill-label">Suite:</span> ${UIUtils.escapeHTML(item.suiteName)}</span>
        </div>
      </div>
      <div>
        <span class="status-badge-lg ${statusClass}">${statusText}</span>
      </div>
    `;
    wrapper.appendChild(headerCard);

    // 2. Error Section (if test failed or error or stack trace exists)
    if (!isPassed || item.stackTrace) {
      const errorBox = document.createElement('div');
      errorBox.className = 'screenshot-error-box';

      let errorTitle = 'Screenshot Test Failure';
      if (item.stackTrace.includes('ScreenshotImageNotFoundException') || item.stackTrace.includes('Reference image file does not exist')) {
        errorTitle = 'Reference Image Missing';
      } else if (item.stackTrace.includes('Size Mismatch')) {
        errorTitle = 'Image Size Mismatch';
      } else if (item.stackTrace.includes('mismatch') || item.stackTrace.includes('differ')) {
        errorTitle = 'Screenshot Pixel Mismatch';
      }

      const safeTrace = UIUtils.escapeHTML(item.stackTrace);
      errorBox.innerHTML = `
        <div class="error-box-header">
          <div class="error-box-title">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span>${UIUtils.escapeHTML(errorTitle)}</span>
          </div>
          ${item.stackTrace ? `<button class="copy-btn" id="btn-copy-stack-trace">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            <span>Copy Stack Trace</span>
          </button>` : ''}
        </div>
        ${item.stackTrace ? `<pre class="error-stack-pre">${safeTrace}</pre>` : ''}
      `;
      wrapper.appendChild(errorBox);

      const copyBtn = errorBox.querySelector('#btn-copy-stack-trace');
      if (copyBtn) {
        copyBtn.addEventListener('click', () => {
          this.copyToClipboard(item.stackTrace, copyBtn);
        });
      }
    }

    // 3. Image Differences & Comparison Card
    const comparisonCard = document.createElement('div');
    comparisonCard.className = 'screenshot-comparison-card';

    const refUrl = this.resolveImagePath(item.refImagePath);
    const newUrl = this.resolveImagePath(item.newImagePath);
    const diffUrl = this.resolveImagePath(item.diffImagePath);

    comparisonCard.innerHTML = `
      <div class="comparison-toolbar">
        <div class="comparison-title">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="2" width="20" height="20" rx="5" ry="5"/>
            <path d="M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z"/>
            <line x1="17.5" y1="6.5" x2="17.51" y2="6.5"/>
          </svg>
          <span>Screenshot Comparison & Differences</span>
        </div>
        <div class="mode-switcher" role="tablist" aria-label="Comparison View Mode">
          <button class="mode-btn active" data-mode="side-by-side" onclick="TestReportApp.switchScreenshotMode('side-by-side', this)" role="tab" aria-selected="true">
            🔲 Side-by-Side
          </button>
          <button class="mode-btn" data-mode="slider" onclick="TestReportApp.switchScreenshotMode('slider', this)" role="tab" aria-selected="false">
            ↔️ Split Slider
          </button>
        </div>
      </div>

      <!-- Mode 1: Side-by-Side Cards -->
      <div id="sc-view-side-by-side" class="image-cards-grid">
        <!-- Reference Image Card -->
        <div class="img-card">
          <div class="img-card-header">
            <span class="img-card-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>
              Reference Image (Golden)
            </span>
          </div>
          <div class="img-card-body">
            ${item.refImagePath ? `
              <img src="${refUrl}" alt="Reference Image" class="preview-img" draggable="false" onclick="TestReportApp.openLightbox('${refUrl}', 'Reference Image')" onerror="TestReportApp.handleImageError(this, 'Reference Image Missing')">
              <button class="img-zoom-btn" onclick="TestReportApp.openLightbox('${refUrl}', 'Reference Image')">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
                Zoom
              </button>
            ` : `
              <div class="img-placeholder-card">
                <div class="placeholder-icon warn">📷</div>
                <div class="placeholder-title">No Reference Image</div>
                <div class="placeholder-desc">Reference image file has not been saved yet for this test.</div>
              </div>
            `}
          </div>
          <div class="img-card-footer">
            <span class="truncate">${UIUtils.escapeHTML(item.refImagePath || 'N/A')}</span>
          </div>
        </div>

        <!-- Rendered Image Card -->
        <div class="img-card">
          <div class="img-card-header">
            <span class="img-card-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
              Rendered Image (Actual)
            </span>
          </div>
          <div class="img-card-body">
            ${item.newImagePath ? `
              <img src="${newUrl}" alt="Rendered Image" class="preview-img" draggable="false" onclick="TestReportApp.openLightbox('${newUrl}', 'Rendered Image')" onerror="TestReportApp.handleImageError(this, 'Rendered Image Missing')">
              <button class="img-zoom-btn" onclick="TestReportApp.openLightbox('${newUrl}', 'Rendered Image')">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
                Zoom
              </button>
            ` : `
              <div class="img-placeholder-card">
                <div class="placeholder-icon warn">⚠️</div>
                <div class="placeholder-title">No Rendered Image</div>
                <div class="placeholder-desc">Rendered screenshot is not available.</div>
              </div>
            `}
          </div>
          <div class="img-card-footer">
            <span class="truncate">${UIUtils.escapeHTML(item.newImagePath || 'N/A')}</span>
          </div>
        </div>

        <!-- Diff Image Card -->
        <div class="img-card">
          <div class="img-card-header">
            <span class="img-card-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 16v1a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h1"/><rect x="8" y="2" width="13" height="13" rx="2"/></svg>
              Difference Image (Diff)
            </span>
          </div>
          <div class="img-card-body">
            ${item.diffImagePath ? `
              <img src="${diffUrl}" alt="Diff Image" class="preview-img" draggable="false" onclick="TestReportApp.openLightbox('${diffUrl}', 'Difference Image')" onerror="TestReportApp.handleImageError(this, 'Diff Image Missing')">
              <button class="img-zoom-btn" onclick="TestReportApp.openLightbox('${diffUrl}', 'Difference Image')">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
                Zoom
              </button>
            ` : `
              <div class="img-placeholder-card">
                ${isPassed ? `
                  <div class="placeholder-icon pass">✓</div>
                  <div class="placeholder-title" style="color: #16a34a;">No Differences</div>
                  <div class="placeholder-desc">The rendered image matches the reference image perfectly (100% match).</div>
                ` : (item.stackTrace.includes('Size Mismatch') ? `
                  <div class="placeholder-icon warn">📐</div>
                  <div class="placeholder-title" style="color: #d97706;">Size Mismatch</div>
                  <div class="placeholder-desc">Image dimensions differ between reference and actual screenshots. Diff image could not be generated.</div>
                ` : (!item.refImagePath ? `
                  <div class="placeholder-icon">ℹ️</div>
                  <div class="placeholder-title">Reference Missing</div>
                  <div class="placeholder-desc">Reference image does not exist. Update test task to generate reference image.</div>
                ` : `
                  <div class="placeholder-icon warn">❌</div>
                  <div class="placeholder-title" style="color: #dc2626;">No Diff Image</div>
                  <div class="placeholder-desc">No pixel difference image was generated for this test run.</div>
                `))}
              </div>
            `}
          </div>
          <div class="img-card-footer">
            <span class="truncate">${UIUtils.escapeHTML(item.diffImagePath || (isPassed ? 'No Diff (Passed)' : 'N/A'))}</span>
          </div>
        </div>
      </div>

      <!-- Mode 2: Split Slider View -->
      <div class="slider-view-wrapper hidden">
        <div class="slider-controls-bar">
          <span>Drag the divider line to compare Reference (Left) vs Rendered (Right)</span>
          <span class="slider-split-percent text-blue-600 font-bold">50% Reference | 50% Rendered</span>
        </div>
        <div class="slider-container">
          <img src="${newUrl}" class="slider-img-base" alt="Rendered Base" draggable="false">
          <div class="slider-img-overlay" style="clip-path: inset(0 50% 0 0);">
            <img src="${refUrl}" alt="Reference Overlay" draggable="false">
          </div>
          <div class="slider-divider" style="left: 50%;">
            <div class="slider-handle">↔</div>
          </div>
        </div>
      </div>
    `;

    wrapper.appendChild(comparisonCard);

    // 4. File Paths Summary Card
    const pathsCard = document.createElement('div');
    pathsCard.className = 'screenshot-paths-card';
    pathsCard.innerHTML = `
      <h3 class="text-sm font-bold text-gray-900 mb-3 flex items-center gap-2">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
        Image File Paths & Details
      </h3>
      <div class="path-row">
        <span class="path-label">Reference Image Path:</span>
        <span class="path-value">${UIUtils.escapeHTML(item.refImagePath || 'Not set')}</span>
        ${item.refImagePath ? `<button class="copy-btn" id="btn-copy-ref-path">Copy</button>` : ''}
      </div>
      <div class="path-row">
        <span class="path-label">Rendered Image Path:</span>
        <span class="path-value">${UIUtils.escapeHTML(item.newImagePath || 'Not set')}</span>
        ${item.newImagePath ? `<button class="copy-btn" id="btn-copy-new-path">Copy</button>` : ''}
      </div>
      <div class="path-row">
        <span class="path-label">Diff Image Path:</span>
        <span class="path-value">${UIUtils.escapeHTML(item.diffImagePath || 'None')}</span>
        ${item.diffImagePath ? `<button class="copy-btn" id="btn-copy-diff-path">Copy</button>` : ''}
      </div>
    `;
    wrapper.appendChild(pathsCard);

    // Attach copy button listeners
    const copyRefBtn = pathsCard.querySelector('#btn-copy-ref-path');
    if (copyRefBtn) copyRefBtn.addEventListener('click', () => this.copyToClipboard(item.refImagePath, copyRefBtn));

    const copyNewBtn = pathsCard.querySelector('#btn-copy-new-path');
    if (copyNewBtn) copyNewBtn.addEventListener('click', () => this.copyToClipboard(item.newImagePath, copyNewBtn));

    const copyDiffBtn = pathsCard.querySelector('#btn-copy-diff-path');
    if (copyDiffBtn) copyDiffBtn.addEventListener('click', () => this.copyToClipboard(item.diffImagePath, copyDiffBtn));

    container.appendChild(wrapper);

    // Initialize interactive split slider
    setTimeout(() => this.initSplitSlider(comparisonCard), 50);
  },

  switchScreenshotMode(mode, btnElement) {
    const container = btnElement.closest('.screenshot-comparison-card');
    if (!container) return;
    container.querySelectorAll('.mode-btn').forEach(btn => {
      const isActive = btn.dataset.mode === mode;
      btn.classList.toggle('active', isActive);
      btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
    });

    const sideBySideView = container.querySelector('.image-cards-grid');
    const sliderView = container.querySelector('.slider-view-wrapper');

    if (sideBySideView) sideBySideView.classList.toggle('hidden', mode !== 'side-by-side');
    if (sliderView) sliderView.classList.toggle('hidden', mode !== 'slider');
  },

  initSplitSlider(container) {
    if (!container) return;
    const sliderBox = container.querySelector('.slider-container');
    const overlayBox = container.querySelector('.slider-img-overlay');
    const divider = container.querySelector('.slider-divider');
    const percentText = container.querySelector('.slider-split-percent');

    if (!sliderBox || !overlayBox || !divider) return;

    let isDragging = false;

    const setPosition = (clientX) => {
      const rect = sliderBox.getBoundingClientRect();
      let x = clientX - rect.left;
      if (x < 0) x = 0;
      if (x > rect.width) x = rect.width;

      const pct = Math.round((x / rect.width) * 100);
      divider.style.left = `${pct}%`;
      overlayBox.style.clipPath = `inset(0 ${100 - pct}% 0 0)`;
      if (percentText) {
        percentText.textContent = `${pct}% Reference | ${100 - pct}% Rendered`;
      }
    };

    sliderBox.addEventListener('pointerdown', (e) => {
      isDragging = true;
      setPosition(e.clientX);
      sliderBox.setPointerCapture(e.pointerId);
    });

    sliderBox.addEventListener('pointermove', (e) => {
      if (isDragging) {
        setPosition(e.clientX);
      }
    });

    const stopDrag = (e) => {
      if (isDragging) {
        isDragging = false;
        try { sliderBox.releasePointerCapture(e.pointerId); } catch (err) { }
      }
    };

    sliderBox.addEventListener('pointerup', stopDrag);
    sliderBox.addEventListener('pointercancel', stopDrag);
  },

  copyToClipboard(text, btn) {
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
  },

  handleImageError(imgElement, placeholderText) {
    const parent = imgElement.parentElement;
    if (parent) {
      parent.innerHTML = `
        <div class="img-placeholder-card">
          <div class="placeholder-icon warn">⚠️</div>
          <div class="placeholder-title">${UIUtils.escapeHTML(placeholderText)}</div>
          <div class="placeholder-desc">File could not be loaded from disk or path does not exist.</div>
        </div>
      `;
    }
  },

  openLightbox(imgSrc, title) {
    let overlay = document.getElementById('screenshot-lightbox');
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.id = 'screenshot-lightbox';
      overlay.className = 'lightbox-overlay';
      overlay.innerHTML = `
        <div class="lightbox-content">
          <img id="lightbox-main-img" class="lightbox-img" src="" alt="">
          <div class="lightbox-toolbar">
            <span id="lightbox-title" class="font-bold"></span>
            <button class="lightbox-btn" onclick="TestReportApp.closeLightbox()">✕ Close</button>
          </div>
        </div>
      `;
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) TestReportApp.closeLightbox();
      });
      document.body.appendChild(overlay);
    }

    const img = overlay.querySelector('#lightbox-main-img');
    const titleEl = overlay.querySelector('#lightbox-title');
    if (img) img.src = imgSrc;
    if (titleEl) titleEl.textContent = title || 'Image Zoom';

    overlay.classList.remove('hidden');
  },

  closeLightbox() {
    const overlay = document.getElementById('screenshot-lightbox');
    if (overlay) overlay.classList.add('hidden');
  },

  showReportView() {
    this.announce("Returning to report view");
    document.title = this.baseTitle;
    this.state.currentView = 'report';
    this.state.currentTestCase = null;
    this.state.currentStackTraceContext = {};

    this.elements.stackTraceView.classList.add('hidden-view');
    this.elements.reportView.classList.remove('hidden-view');
    if (this.elements.filterControlsGroup) {
      this.elements.filterControlsGroup.classList.remove('hidden');
    }
    if (this.elements.searchContainer) this.elements.searchContainer.classList.remove('hidden');
    if (this.elements.viewSegments) this.elements.viewSegments.classList.remove('hidden');
    if (this.elements.densitySegments) this.elements.densitySegments.classList.remove('hidden');

    if (this.activeTrigger) {
      this.activeTrigger.focus();
      this.activeTrigger = null;
    }
  },

  renderStackTraceBreadcrumbs(context) {
    const { moduleName, packageName, className, testCaseName } = context;
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
      html += `
            <span class="breadcrumb-separator" aria-hidden="true">/</span>
            <span class="font-semibold text-gray-800">${UIUtils.escapeHTML(testCaseName)}</span>`;
    }
    html += `</div>`;

    this.elements.stackTraceBreadcrumbs.innerHTML = html;
  },

  getChildType(parentType) {
    const hierarchy = { 'root': 'module', 'module': 'package', 'package': 'class', 'class': 'testCase', 'testCase': null };
    return hierarchy[parentType];
  },

  pluralize(type) {
    const pluralMap = { 'module': 'modules', 'package': 'packages', 'class': 'classes', 'testCase': 'testCases' };
    return pluralMap[type];
  }
};

/**
 * Navigation handler for history management (Back/Forward buttons).
 */
const Navigation = {
  isPopping: false,

  init() {
    // Store initial state
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

    state.chipVisibility = {
      status: !TestReportApp.elements.statusChipContainer.classList.contains('hidden'),
      module: !TestReportApp.elements.modChipContainer.classList.contains('hidden'),
      package: !TestReportApp.elements.pkgChipContainer.classList.contains('hidden'),
      class: !TestReportApp.elements.clsChipContainer.classList.contains('hidden'),
      testCase: !TestReportApp.elements.tcChipContainer.classList.contains('hidden')
    };

    return state;
  },

  push() {
    if (this.isPopping) return;
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

    // Restore state
    TestReportApp.state = {
      ...TestReportApp.state,
      ...state
    };

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

    // Restore chip visibility
    if (state.chipVisibility && TestReportApp.elements) {
      TestReportApp.elements.statusChipContainer.classList.toggle('hidden', !state.chipVisibility.status);
      TestReportApp.elements.modChipContainer.classList.toggle('hidden', !state.chipVisibility.module);
      TestReportApp.elements.pkgChipContainer.classList.toggle('hidden', !state.chipVisibility.package);
      TestReportApp.elements.clsChipContainer.classList.toggle('hidden', !state.chipVisibility.class);
      TestReportApp.elements.tcChipContainer.classList.toggle('hidden', !state.chipVisibility.testCase);
    }

    // Rebuild static dropdowns to reflect restored state (test suite, variants, status)
    TestReportApp.populateFilters();

    // Update test suite UI specifically since populateFilters handles the dropdown but not the external UI elements fully unless changed
    if (TestReportApp.elements.tsAllState && TestReportApp.elements.tsSelectedState) {
      if (TestReportApp.state.filters.testSuite === 'all') {
        TestReportApp.elements.tsAllState.classList.remove('hidden');
        TestReportApp.elements.tsSelectedState.classList.add('hidden');
      } else {
        TestReportApp.elements.tsAllState.classList.add('hidden');
        TestReportApp.elements.tsSelectedState.classList.remove('hidden');
        if (TestReportApp.elements.testSuiteFilterText) {
          TestReportApp.elements.testSuiteFilterText.textContent = TestReportApp.state.filters.testSuite;
        }
      }
    }

    // Re-render
    if (TestReportApp.state.currentView === 'stack-trace') {
      TestReportApp.showStackTraceView(TestReportApp.state.currentTestCase, TestReportApp.state.currentStackTraceContext);
    } else {
      TestReportApp.showReportView();
      if (selectionChanged || oldView === 'report') {
        TestReportApp.render();
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

