/*
 * Copyright (C) 2025 The Android Open Source Project
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
   * Builds a multi-select dropdown with "Select All" / "Clear" actions
   * and a scrollable list of options.
   */
  buildActionDropdown(container, options, initialState, onSelectionChange, searchable = true, multiSelect = true) {
    if (!container) return;

    container.innerHTML = "";
    container.style.padding = "0";
    container.style.overflow = "hidden";

    let currentState = multiSelect
      ? (Array.isArray(initialState) ? [...initialState] : [])
      : initialState;

    let searchInput = null;
    if (searchable) {
      const searchContainer = document.createElement("div");
      searchContainer.className = "dropdown-search-zone";

      searchInput = document.createElement("input");
      searchInput.type = "text";
      searchInput.className = "popover-search";
      searchInput.placeholder = "Search...";

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

        const item = document.createElement("label");
        item.className = "popover-item";

        if (multiSelect) {
          const checkbox = document.createElement("input");
          checkbox.type = "checkbox";
          checkbox.className = "popover-checkbox";
          checkbox.checked = isChecked;
          item.appendChild(checkbox);

          item.addEventListener("change", (e) => {
            e.stopPropagation();
            if (checkbox.checked) {
              if (!currentState.includes(opt.value)) currentState.push(opt.value);
            } else {
              currentState = currentState.filter(v => v !== opt.value);
            }
            onSelectionChange([...currentState]);
          });
        } else {
          if (isChecked) {
            item.classList.add('active-popover-item');
          }

          item.addEventListener("click", (e) => {
            e.preventDefault();
            e.stopPropagation();
            currentState = opt.value;
            onSelectionChange(currentState);
            renderList(searchInput ? searchInput.value : "");

            // Close dropdown
            const dropdownMenu = container.closest('.dropdown-menu');
            if (dropdownMenu) {
              dropdownMenu.classList.add('hidden');
              const btn = document.querySelector(`[aria-controls="${dropdownMenu.id}"]`) || dropdownMenu.previousElementSibling;
              if (btn) btn.setAttribute('aria-expanded', 'false');
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
 * Main application object.
 * DEPENDENCY: Requires 'TEST_DATA_SOURCE' to be defined in data.js
 */
const TestReportApp = {
  state: {
    viewMode: 'flat',
    density: 'comfy',
    currentFlatView: 'modules',
    selectedModule: null,
    selectedPackage: null,
    selectedClass: null,
    filters: { variants: [], search: '', status: ['passed', 'failed', 'skipped'], testSuite: 'all', modules: [], packages: [], classes: [], testCases: [] },
    sort: { by: 'name', order: 'asc' },
    variants: [],
    processedData: null
  },
  elements: {},

  init: function () {
    this.cacheDOMElements();

    // Directly access the global variable from data.js
    if (typeof TEST_DATA_SOURCE !== 'undefined') {
      this.setupTestResults(TEST_DATA_SOURCE);
      this.populateFilters();
      this.bindEvents();
      this.closeDropdownsOnClickOutside();
      this.render();
    } else {
      console.error("TEST_DATA_SOURCE is not defined. Make sure data.js is loaded before script.js");
      this.elements.resultsData.innerHTML = `<tr><td colspan="100%" class="text-center text-red-600 font-bold" style="padding: 2rem;">Error: Data file not loaded.</td></tr>`;
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
      searchRevealBtn: document.getElementById('search-reveal-btn'),
      searchWrapper: document.getElementById('search-wrapper'),
      searchClearBtn: document.getElementById('search-clear-btn'),
    };
  },

  setupTestResults(testCaseData) {
    // Deep copy to avoid mutating the original source if used elsewhere
    const dataCopy = JSON.parse(JSON.stringify(testCaseData));
    this.state.variants = dataCopy.variants;
    this.state.filters.variants = [...dataCopy.variants]; // Default to all selected

    // Populate header
    if (this.elements.appTitle) this.elements.appTitle.textContent = dataCopy.projectName || 'Test Report';
    if (this.elements.reportDate) this.elements.reportDate.textContent = dataCopy.timestamp || '';
    if (this.elements.totalModules) this.elements.totalModules.textContent = dataCopy.numberOfModules || 0;
    if (this.elements.totalPackages) this.elements.totalPackages.textContent = dataCopy.numberOfPackages || 0;
    if (this.elements.totalClasses) this.elements.totalClasses.textContent = dataCopy.numberOfClasses || 0;

    const processNode = (node, type) => {
      node.type = type;
      const childKey = this.pluralize(this.getChildType(type));
      let children = node[childKey];

      if (type === 'class') {
        children = node.testCases || [];
      }

      if (children) {
        children.forEach(child => processNode(child, this.getChildType(type)));
      }

      node.summary = this._calculateSummaryFromChildren(children);
    };

    const suiteSet = new Set();
    const extractSuites = (nodes) => {
      if (!nodes) return;
      nodes.forEach(n => {
        if (n.testSuiteSummaries) {
          n.testSuiteSummaries.forEach(ts => suiteSet.add(ts.name));
        }
        if (n.packages) extractSuites(n.packages);
        if (n.classes) extractSuites(n.classes);
      });
    };
    extractSuites(dataCopy.modules);
    this.state.testSuites = Array.from(suiteSet).sort();

    dataCopy.modules.forEach(module => processNode(module, 'module'));
    dataCopy.summary = this._calculateSummaryFromChildren(dataCopy.modules);
    this.processedData = dataCopy;
  },

  populateFilters() {
    // Test Suite Dropdown
    const testSuiteOptions = [
      { name: 'All', value: 'all' },
      ...this.state.testSuites.map(ts => ({ name: ts, value: ts }))
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
    }, false, false);

    // Variants Dropdown
    const variantOptions = this.state.variants.map(v => ({ name: v, value: v }));
    UIUtils.buildActionDropdown(this.elements.variantFilterList, variantOptions, this.state.filters.variants, (newArr) => {
      this.state.filters.variants = newArr;
      this.updateVariantButtonText();
      this.render();
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
    }, false, true);
  },

  updateVariantButtonText() {
    const selectedCount = this.state.filters.variants.length;
    const totalCount = this.state.variants.length;

    if (this.elements.variantFilterBtn) {
      if (selectedCount === totalCount && totalCount > 0) {
          this.elements.variantFilterBtn.setAttribute('data-tooltip', 'Filter by Variant: All');
      } else if (selectedCount === 1) {
          this.elements.variantFilterBtn.setAttribute('data-tooltip', `Filter by Variant: ${this.state.filters.variants[0]}`);
      } else {
          this.elements.variantFilterBtn.setAttribute('data-tooltip', `Filter by Variant: ${selectedCount} Selected`);
      }
    }
  },

  // --- EVENT BINDING & HANDLING ---
  bindEvents() {
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
            }
        });
    });

    if (this.elements.viewSegments) {
        this.elements.viewSegments.addEventListener('click', (e) => {
            const btn = e.target.closest('.segment-btn');
            if (!btn) return;
            this.elements.viewSegments.querySelectorAll('.segment-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            this.state.viewMode = btn.dataset.value;
            this.resetSelection();
            this.render();
        });
    }

    if (this.elements.densitySegments) {
        this.elements.densitySegments.addEventListener('click', (e) => {
            const btn = e.target.closest('.segment-btn');
            if (!btn) return;
            this.elements.densitySegments.querySelectorAll('.segment-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
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
            }
        });
    }
    this.elements.resultsData.addEventListener('click', (e) => {
        if (this.state.viewMode === 'flat') {
            const clickable = e.target.closest('.nav-link');
            if (clickable) {
                e.preventDefault();
                this.handleFlatRowClick(clickable);
            }
        } else {
            const treeToggle = e.target.closest('.tree-toggle');
            if (treeToggle) {
                e.preventDefault();
                this.handleTreeRowClick(treeToggle);
            }
        }
    });
    this.elements.breadcrumbs.addEventListener('click', (e) => {
        const link = e.target.closest('a[data-action]');
        if (!link) return;
        e.preventDefault();
        const action = link.dataset.action;
        if (action === 'go-to-modules') {
            this.state.selectedModule = null;
            this.state.selectedPackage = null;
            this.state.selectedClass = null;
            this.state.currentFlatView = 'modules';
        } else if (action === 'go-to-packages') {
            this.state.selectedPackage = null;
            this.state.selectedClass = null;
            this.state.currentFlatView = 'packages';
        } else if (action === 'go-to-classes') {
            this.state.selectedClass = null;
            this.state.currentFlatView = 'classes';
        }
        this.render();
    });

    this.elements.tableHeaders.addEventListener('click', (e) => { const th = e.target.closest('[data-sort-by]'); if (!th) return; const newSortBy = th.dataset.sortBy; if (this.state.sort.by === newSortBy) { this.state.sort.order = this.state.sort.order === 'asc' ? 'desc' : 'asc'; } else { this.state.sort.by = newSortBy; this.state.sort.order = 'asc'; } this.render(); });

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
          { btn: this.elements.addFilterBtn, dropdown: this.elements.addFilterDropdown }
      ];
  },

  toggleDropdown(dropdown, button) {
    const isHidden = dropdown.classList.contains('hidden');
    this.closeAllDropdowns();
    if (isHidden) {
        dropdown.classList.remove('hidden');
        if (button) button.setAttribute('aria-expanded', 'true');
    }
  },

  closeAllDropdowns() {
    this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
        if (dropdown) dropdown.classList.add('hidden');
        if (btn && btn.hasAttribute('aria-expanded')) btn.setAttribute('aria-expanded', 'false');
    });
  },

  closeDropdownsOnClickOutside() {
    document.addEventListener('click', (e) => {
      this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
          if (btn && dropdown && !btn.contains(e.target) && !dropdown.contains(e.target)) {
              dropdown.classList.add('hidden');
              if (btn.hasAttribute('aria-expanded')) {
                  btn.setAttribute('aria-expanded', 'false');
              }
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
  },

  handleTreeRowClick(target) {
    const row = target.closest('tr');
    if (!row) return;
    const arrow = row.querySelector('.collapsible-arrow');
    if (!arrow.classList.contains('invisible')) {
      arrow.classList.toggle('open');
      const isOpen = arrow.classList.contains('open');
      document.querySelectorAll(`[data-parent-id="${row.dataset.id}"]`).forEach(child => {
        child.classList.toggle('hidden', !isOpen);
        if (!isOpen) { const childArrow = child.querySelector('.collapsible-arrow.open'); if (childArrow) { childArrow.classList.remove('open'); this.collapseDescendants(child); } }
      });
    }
  },

  collapseDescendants(parentRow) {
    document.querySelectorAll(`[data-parent-id="${parentRow.dataset.id}"]`).forEach(child => {
      child.classList.add('hidden');
      const childArrow = child.querySelector('.collapsible-arrow.open');
      if (childArrow) { childArrow.classList.remove('open'); this.collapseDescendants(child); }
    });
  },

  // --- DATA PROCESSING & FILTERING ---
  getFilteredAndSortedData() {
    if (!this.processedData) return { modules: [] };
    const finalData = JSON.parse(JSON.stringify(this.processedData));

    const applyFilters = (nodes, type, parentMatchesSearch = false) => {
      if (!nodes) return [];
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
        let selfMatchesSearch = true;
        if (this.state.filters.search) {
          const searchTerm = this.state.filters.search.toLowerCase();
          selfMatchesSearch = node.name.toLowerCase().includes(searchTerm);
        }

        const effectiveMatchesSearch = selfMatchesSearch || parentMatchesSearch;

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
        if (this.state.filters.testSuite !== 'all' && type !== 'testCase') {
          if (node.testSuiteSummaries) {
            const suiteMatch = node.testSuiteSummaries.find(ts => ts.name === this.state.filters.testSuite);
            if (!suiteMatch) return false;
            node.summary = suiteMatch.summary;
          } else {
            return false;
          }
        }

        let matchesStatus = true;
        if (this.state.filters.status.length < 3) {
          const isLeaf = !this.getChildType(type);
          if (isLeaf) {
            let hasFail = false;
            let hasPass = false;
            let hasSkipped = false;

            this.state.filters.variants.forEach(v => {
              const statusVal = node[v];
              const status = (typeof statusVal === 'object' && statusVal !== null) ? statusVal.status : statusVal;
              if (status === 'fail') hasFail = true;
              if (status === 'pass') hasPass = true;
              if (status === 'skipped') hasSkipped = true;
            });

            matchesStatus = false;
            if (hasPass && this.state.filters.status.includes('passed')) matchesStatus = true;
            if (hasFail && this.state.filters.status.includes('failed')) matchesStatus = true;
            if (hasSkipped && this.state.filters.status.includes('skipped')) matchesStatus = true;
          } else {
            matchesStatus = hasVisibleChildren;
          }
        }

        // Final evaluation check
        if (!this.getChildType(type)) { // Leaf node (testCase)
            return matchesStatus && effectiveMatchesSearch;
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
    const data = this.getFilteredAndSortedData();
    this.renderTable(data);
    this.updateGroupByText();
    this.updateTooltipsForOverflow();
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
    });

    UIUtils.buildActionDropdown(this.elements.packageFilterDropdown, packageOptions, filters.packages, (newArr) => {
      filters.classes = [];
      filters.testCases = [];
      filters.packages = newArr;
      this.handleHeaderFilterChange('package');
      this.updateFilterButtons();
      this.render();
    });

    UIUtils.buildActionDropdown(this.elements.classFilterDropdown, classOptions, filters.classes, (newArr) => {
      filters.testCases = [];
      filters.classes = newArr;
      this.handleHeaderFilterChange('class');
      this.updateFilterButtons();
      this.render();
    });

    UIUtils.buildActionDropdown(this.elements.tcFilterDropdown, testCaseOptions, filters.testCases, (newArr) => {
      filters.testCases = newArr;
      this.handleHeaderFilterChange('testCase');
      this.updateFilterButtons();
      this.render();
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
    let nameHeader = this.state.viewMode === 'tree' ? 'Name' : this.state.currentFlatView.charAt(0).toUpperCase() + this.state.currentFlatView.slice(1);

    let pathHeader = '';
    let pathSubHeader = '';
    if (this.state.viewMode === 'flat' && !this.state.selectedModule) {
      if (this.state.currentFlatView === 'classes' || this.state.currentFlatView === 'testCases') {
        pathHeader = `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30">Path</th>`;
        pathSubHeader = `<th class="py-2 px-6 bg-gray-50 z-30"></th>`;
      } else if (this.state.currentFlatView === 'packages') {
        pathHeader = `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30">Module</th>`;
        pathSubHeader = `<th class="py-2 px-6 bg-gray-50 z-30"></th>`;
      }
    }

    this.elements.tableHeaders.innerHTML = `
            <tr class="border-b border-gray-200">
                <th class="py-4 px-6 text-left font-semibold text-gray-700 sticky-name bg-gray-50 z-30" data-sort-by="name">${nameHeader} ${sortIndicator('name')}</th>
                ${pathHeader}
                ${variantsToShow.map(v => `<th class="py-4 px-4 text-center font-semibold text-gray-700 border-l border-gray-200" colspan="4">${v}</th>`).join('')}
            </tr>
            <tr class="border-b border-gray-200">
                <th class="py-2 px-6 sticky-name bg-gray-50 z-30"></th>
                ${pathSubHeader}
                ${variantsToShow.map(v => `<th class="py-2 px-4 text-center text-xs font-medium text-gray-600 border-l border-gray-200">Pass</th><th class="py-2 px-4 text-center text-xs font-medium text-gray-600">Fail</th><th class="py-2 px-4 text-center text-xs font-medium text-gray-600">Skip</th><th class="py-2 px-4 text-center text-xs font-medium text-gray-600">Pass Rate</th>`).join('')}
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
        html += `<a href="#" class="breadcrumb-link" data-action="go-to-modules">Project</a>`;
    } else {
        html += `<span class="breadcrumb-current">Project</span>`;
    }

    // Module level
    if (selectedModule) {
        html += `<span class="breadcrumb-separator">/</span>`;
        if (selectedPackage) {
            html += `<a href="#" class="breadcrumb-link" data-action="go-to-packages">${selectedModule}</a>`;
        } else {
            html += `<span class="breadcrumb-current">${selectedModule}</span>`;
        }
    }

    // Package level
    if (selectedPackage) {
        html += `<span class="breadcrumb-separator">/</span>`;
        if (selectedClass) {
            html += `<a href="#" class="breadcrumb-link" data-action="go-to-classes">${selectedPackage}</a>`;
        } else {
            html += `<span class="breadcrumb-current">${selectedPackage}</span>`;
        }
    }

    // Class level
    if (selectedClass) {
        html += `<span class="breadcrumb-separator">/</span>`;
        html += `<span class="breadcrumb-current">${selectedClass}</span>`;
    }

    this.elements.breadcrumbs.innerHTML = html;
  },

  renderTreeRows(data) {
    let html = '';
    const renderNode = (node, parentId, level) => {
      const type = node.type;
      const childType = this.getChildType(type);
      const childKey = this.pluralize(childType);
      const children = node[childKey] || (type === 'class' ? node.testCases : []) || [];
      const hasChildren = children.length > 0;
      const uniqueId = `${parentId}-${node.name}`.replace(/[^a-zA-Z0-9-_]/g, '');

      const nameContent = `<span class="font-medium">${node.name}</span>`;
      const chevron = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="collapsible-arrow ${!hasChildren ? 'invisible' : ''}"><path d="m9 18 6-6-6-6"></path></svg>`;

      html += `
                <tr class="table-row ${level > 0 ? 'hidden' : ''}" data-id="${uniqueId}" data-parent-id="${parentId}">
                    <td class="py-3 px-6 sticky-name" title="${node.name}">
                        <div class="tree-toggle" style="padding-left: ${level * 1.0}rem;">
                            ${chevron} ${nameContent}
                        </div>
                    </td>
                    ${this._renderStatusCell(type === 'testCase' ? node : node.summary, type === 'testCase')}
                </tr>`;

      if (hasChildren) {
        children.forEach(child => renderNode(child, uniqueId, level + 1));
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
        let nameCell = `<div class="font-medium">${item.name}</div>`;
        if (view !== 'testCases') {
            nameCell = `<div class="font-medium nav-link text-blue-700 hover-underline cursor-pointer" data-name="${item.name}" data-type="${item.type}" data-module-name="${item.moduleName || ''}" data-package-name="${item.packageName || ''}">${item.name}</div>`;
        }

        let pathCell = '';
        if (this.state.viewMode === 'flat' && !this.state.selectedModule) {
            if (view === 'packages') {
                pathCell = `<td class="py-3 px-6 text-gray-500 text-sm truncate max-w-150" title="${item.moduleName}">${item.moduleName}</td>`;
            } else if (view === 'classes') {
                pathCell = `<td class="px-2 max-w-300" title="${item.moduleName} > ${item.packageName}">
                    <div class="flex flex-col" style="overflow: hidden; width: 100%;">
                        <span class="text-xs text-gray-500 truncate-block">${item.moduleName}</span>
                        <span class="text-sm text-gray-500 truncate-block">${item.packageName}</span>
                    </div>
                </td>`;
            } else if (view === 'testCases') {
                pathCell = `<td class="px-2 max-w-300" title="${item.moduleName} > ${item.packageName} > ${item.className}">
                    <div class="flex flex-col" style="overflow: hidden; width: 100%;">
                        <span class="text-xs text-gray-500 truncate-block">${item.moduleName}</span>
                        <span class="text-sm text-gray-500 truncate-block">${item.packageName} > ${item.className}</span>
                    </div>
                </td>`;
            }
        }
        
        return `<tr class="table-row">
            <td class="py-3 px-6 sticky-name" title="${item.name}">
                 ${nameCell}
            </td>
            ${pathCell}
            ${this._renderStatusCell(view === 'testCases' ? item : item.summary, view === 'testCases')}
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
  _calculateSummaryFromChildren(children) {
    if (!children || children.length === 0) {
      // Return empty summary with 0s
      const summary = { total: 0, passed: 0, failed: 0, skipped: 0, passRate: 0 };
      this.state.variants.forEach(v => {
        summary[v] = { passed: 0, failed: 0, skipped: 0, total: 0, rate: 0 };
      });
      return summary;
    }

    const summary = { total: 0, passed: 0, failed: 0, skipped: 0, passRate: 0 };
    this.state.variants.forEach(v => {
      summary[v] = { passed: 0, failed: 0, skipped: 0, total: 0, rate: 0 };
    });

    children.forEach(child => {
      this.state.variants.forEach(v => {
        if (child.type === 'testCase') {
          const statusVal = child[v];
          const status = (typeof statusVal === 'object' && statusVal !== null) ? statusVal.status : statusVal;
          if (status === 'pass') summary[v].passed++;
          else if (status === 'fail') summary[v].failed++;
          else if (status === 'skipped') summary[v].skipped++;
          summary[v].total++;
        } else {
          // It's a node with summary
          summary[v].passed += child.summary[v].passed;
          summary[v].failed += child.summary[v].failed;
          summary[v].skipped += child.summary[v].skipped;
          summary[v].total += child.summary[v].total;
        }
      });
    });

    // Calculate rates
    let totalPassed = 0;
    let totalFailed = 0;
    let totalSkipped = 0;

    this.state.variants.forEach(v => {
      const s = summary[v];
      const relevant = s.passed + s.failed;
      s.rate = relevant > 0 ? (s.passed / relevant) * 100 : 100; // Default to 100 if no tests? Or 0?
      // If total is 0, rate is 0?
      if (s.total === 0) s.rate = 0;

      totalPassed += s.passed;
      totalFailed += s.failed;
      totalSkipped += s.skipped;
    });
    summary.passed = totalPassed;
    summary.failed = totalFailed;
    summary.skipped = totalSkipped;

    summary.total = summary.passed + summary.failed + summary.skipped;
    const relevantTotal = summary.passed + summary.failed;
    summary.passRate = relevantTotal > 0 ? (summary.passed / relevantTotal) * 100 : 100;
    return summary;
  },

  _renderStatusCell(summaryOrNode, isNode = false) {
    // If isNode is true, summaryOrNode is the node itself (for functions), otherwise it's a summary object
    if (!summaryOrNode) {
      const colspan = (this.state.filters.variants.length) * 4;
      return `<td colspan="${colspan}"></td>`;
    }

    const variantsToShow = this.state.filters.variants;

    if (isNode) {
      // Rendering for a function row - show direct status
      return `${variantsToShow.map(v => {
        const statusVal = summaryOrNode[v];
        const status = (typeof statusVal === 'object' && statusVal !== null) ? statusVal.status : statusVal;
        const stackTrace = (typeof statusVal === 'object' && statusVal !== null) ? statusVal.stackTrace : null;

        let cellContent = '-';
        let cellClass = 'py-3 px-4 text-center text-gray-500 border-l border-gray-200';

        if (status === 'pass') {
          cellContent = 'Passed';
          cellClass = 'py-3 px-4 text-center text-green-600 font-medium border-l border-gray-200';
        } else if (status === 'fail') {
          if (stackTrace) {
            cellContent = 'Failure';
            cellClass = 'py-3 px-4 text-center text-red-600 font-bold border-l border-gray-200 clickable-status';
            return `<td colspan="4" class="${cellClass}" onclick="TestReportApp.openStackTrace(this)" data-stack-trace="${encodeURIComponent(stackTrace)}">${cellContent}</td>`;
          } else {
            cellContent = 'Failed';
            cellClass = 'py-3 px-4 text-center text-red-600 font-bold border-l border-gray-200';
          }
        } else if (status === 'skipped') {
          cellContent = 'Skipped';
          cellClass = 'py-3 px-4 text-center text-yellow-600 border-l border-gray-200';
        }

        return `<td colspan="4" class="${cellClass}">${cellContent}</td>`;
      }).join('')}`;
    }

    // Rendering for a summary row
    return `${variantsToShow.map(v => {
      const stats = summaryOrNode[v];
      if (!stats) return '<td colspan="4" class="text-center text-gray-500 border-l border-gray-200">-</td>';

      const { passed, failed, skipped, total, rate } = stats;
      const passRateColor = rate >= 95 ? 'text-green-600' : rate >= 80 ? 'text-yellow-600' : 'text-red-600';
      const relevantTotal = passed + failed;

      const filter = this.state.filters.status;
      const showPassed = filter.includes('passed');
      const showFailed = filter.includes('failed');
      const showSkipped = filter.includes('skipped');

      return `
                <td class="py-3 px-4 text-center ${showPassed ? 'text-green-600' : 'text-gray-500'} font-medium border-l border-gray-200">${showPassed ? passed : '-'}</td>
                <td class="py-3 px-4 text-center ${showFailed ? (failed > 0 ? 'text-red-600 font-bold' : 'text-gray-500') : 'text-gray-500'}">${showFailed ? failed : '-'}</td>
                <td class="py-3 px-4 text-center ${showSkipped ? 'text-yellow-600' : 'text-gray-500'}">${showSkipped ? skipped : '-'}</td>
                <td class="py-3 px-4 text-center">
                    <div class="flex flex-col">
                        <span class="font-bold ${passRateColor}">${rate.toFixed(1)}%</span>
                        <span class="text-xs text-gray-500">${passed}/${relevantTotal}</span>
                    </div>
                </td>`;
    }).join('')}`;
  },

  openStackTrace(element) {
    const stackTrace = decodeURIComponent(element.dataset.stackTrace);
    const modal = document.getElementById('stack-trace-modal');
    const content = document.getElementById('stack-trace-content');
    content.textContent = stackTrace;
    modal.classList.remove('hidden');
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

document.addEventListener('DOMContentLoaded', () => {
  TestReportApp.init();

  // Modal close handlers
  const modal = document.getElementById('stack-trace-modal');
  const closeBtn = document.getElementById('close-modal');

  if (closeBtn) {
    closeBtn.addEventListener('click', () => {
      modal.classList.add('hidden');
    });
  }

  if (modal) {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        modal.classList.add('hidden');
      }
    });
  }

  // Close on Escape key
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      TestReportApp.closeAllDropdowns();
      if (modal && !modal.classList.contains('hidden')) {
        modal.classList.add('hidden');
      }
    }
  });
});
