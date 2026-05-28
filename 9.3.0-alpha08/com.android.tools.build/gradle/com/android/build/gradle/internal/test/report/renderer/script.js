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
      this.elements.resultsData.innerHTML = `<tr><td colspan="100%" class="text-center text-red-600 font-bold" style="padding: 2rem;">Error: Data file not loaded.</td></tr>`;
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

    const annotateType = (node, type) => {
        node.type = type;
        const childType = this.getChildType(type);
        if (!childType) return;

        const childKey = this.pluralize(childType);
        const children = node[childKey] || (type === 'class' ? node.testCases : []) || [];
        children.forEach(child => annotateType(child, childType));
    };
    rootReport.modules.forEach(m => annotateType(m, 'module'));

    this.processedData = rootReport;
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

                // When changing the view, we reset selections if we are viewing a lower granularity
                if (this.state.currentFlatView === 'modules') {
                    this.state.selectedModule = null;
                    this.state.selectedPackage = null;
                    this.state.selectedClass = null;
                } else if (this.state.currentFlatView === 'packages') {
                    this.state.selectedPackage = null;
                    this.state.selectedClass = null;
                } else if (this.state.currentFlatView === 'classes') {
                    this.state.selectedClass = null;
                }

                this.render();
                Navigation.push();
            }
        });
    }
    this.elements.resultsData.addEventListener('click', (e) => {
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
        this.elements.breadcrumbs.innerHTML = `<span class="breadcrumb-current">Project Overview</span>`;
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
        html += `<span class="breadcrumb-separator">/</span>`;
        if (selectedPackage) {
            html += `<a href="#" class="breadcrumb-link" data-action="${BREADCRUMB_ACTIONS.GO_TO_PACKAGES}" aria-label="Go back to module: ${UIUtils.escapeHTML(selectedModule)}">${UIUtils.escapeHTML(selectedModule)}</a>`;
        } else {
            html += `<span class="breadcrumb-current">${UIUtils.escapeHTML(selectedModule)}</span>`;
        }
    }

    // Package level
    if (selectedPackage) {
        html += `<span class="breadcrumb-separator">/</span>`;
        if (selectedClass) {
            html += `<a href="#" class="breadcrumb-link" data-action="${BREADCRUMB_ACTIONS.GO_TO_CLASSES}" aria-label="Go back to package: ${UIUtils.escapeHTML(selectedPackage)}">${UIUtils.escapeHTML(selectedPackage)}</a>`;
        } else {
            html += `<span class="breadcrumb-current">${UIUtils.escapeHTML(selectedPackage)}</span>`;
        }
    }

    // Class level
    if (selectedClass) {
        html += `<span class="breadcrumb-separator">/</span>`;
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
      if (type === 'testCase' && this.hasVisibleFailures(node)) {
          nameContent = `<span class="font-medium text-blue-700 hover-underline cursor-pointer stack-trace-trigger" onclick="TestReportApp.openStackTrace(this)" data-module="${UIUtils.escapeHTML(currentContext.moduleName || '')}" data-package="${UIUtils.escapeHTML(currentContext.packageName || '')}" data-class="${UIUtils.escapeHTML(currentContext.className || '')}" data-test-case="${UIUtils.escapeHTML(node.name || '')}" tabindex="0" role="button" aria-label="View stack trace for ${UIUtils.escapeHTML(node.name)}">${UIUtils.escapeHTML(node.name)}</span>`;
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
        } else if (this.hasVisibleFailures(item)) {
            nameTd = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover-underline cursor-pointer stack-trace-trigger" onclick="TestReportApp.openStackTrace(this)" data-module="${UIUtils.escapeHTML(item.moduleName || '')}" data-package="${UIUtils.escapeHTML(item.packageName || '')}" data-class="${UIUtils.escapeHTML(item.className || '')}" data-test-case="${UIUtils.escapeHTML(item.name || '')}" tabindex="0" role="button" aria-label="View stack trace for ${UIUtils.escapeHTML(item.name)}">${UIUtils.escapeHTML(item.name)}</td>`;
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
      if (!testCase.testSuiteResults) return null;
      let suitesToSearch = [];
      if (suiteName === 'all') {
          suitesToSearch = testCase.testSuiteResults;
      } else {
          const specific = testCase.testSuiteResults.find(ts => ts.testSuiteName === suiteName);
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

    return (node.commonStackTraces || []).some(group => {
        for (const [suite, variants] of Object.entries(group.occurrences)) {
            if (activeSuite !== 'all' && suite !== activeSuite) continue;
            if (variants.some(v => activeVariants.includes(v))) return true;
        }
        return false;
    });
  },

  _renderStatusCell(node, context = {}) {
    const variantsToShow = this.state.filters.variants;

    if (!node) {
      return variantsToShow.map(() => `<td colspan="4" class="border-l border-gray-200"></td>`).join('');
    }

    const suiteFilter = this.state.filters.testSuite;

    return `${variantsToShow.map(v => {
        let variantSummary = null;
        if (suiteFilter === 'all') {
            const aggregatedSuite = node.testSuiteSummaries.find(ts => ts.name === 'Aggregated');
            if (aggregatedSuite) {
                variantSummary = aggregatedSuite.variantSummaries.find(vs => vs.name === v);
            }
        } else {
            const suiteSummary = node.testSuiteSummaries.find(ts => ts.name === suiteFilter);
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

    // Find the test case object in the processed data
    const findTestCase = (nodes) => {
        for (const m of nodes) {
            if (m.name === module) {
                for (const p of m.packages) {
                    if (p.name === pkg) {
                        for (const c of p.classes) {
                            if (c.name === clz) {
                                return c.testCases.find(t => t.name === tcName);
                            }
                        }
                    }
                }
            }
        }
        return null;
    };

    const testCase = findTestCase(this.processedData.modules);
    if (testCase) {
        this.activeTrigger = element;
        const context = { moduleName: module, packageName: pkg, className: clz, testCaseName: tcName };
        this.showStackTraceView(testCase, context);
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

    // Filter groups and their internal occurrences based on active filters
    const filteredGroups = (testCase.commonStackTraces || []).map(group => {
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

    // Always clear search from history state to avoid messy history from keystrokes
    if (state.filters) {
      state.filters.search = '';
    }

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

    // Reset search UI on navigation
    if (TestReportApp.elements.searchInput) {
      TestReportApp.elements.searchInput.value = '';
      if (TestReportApp.elements.searchClearBtn) {
        TestReportApp.elements.searchClearBtn.classList.add('hidden');
      }
      if (TestReportApp.elements.searchWrapper) {
        TestReportApp.elements.searchWrapper.classList.remove('expanded');
      }
      if (TestReportApp.elements.searchRevealBtn) {
        TestReportApp.elements.searchRevealBtn.classList.remove('hidden');
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

