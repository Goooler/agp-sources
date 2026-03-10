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
 * Main application object to encapsulate state, elements, and logic.
 */
const App = {
    init() {
        // fullReport is consumed from generated report-data.js
        if (typeof fullReport !== 'undefined') {
            CoverageReportApp.init(fullReport);
        } else {
            console.error("fullReport data not found. Ensure report-data.js is loaded.");
        }

        // Initialize SourceViewApp (no data needed initially)
        SourceViewApp.init();

        document.body.addEventListener('click', (e) => {
            if (e.target.closest('.class-link')) {
                e.preventDefault();
                const link = e.target.closest('.class-link');

                const moduleName = link.dataset.moduleName;
                const packageName = link.dataset.packageName;
                const className = link.dataset.className;

                const classObj = this.findClassObject(moduleName, packageName, className);

                if (classObj) {
                    const context = {
                        moduleName: moduleName,
                        packageName: packageName,
                        testSuiteName: CoverageReportApp.state.filters.testSuite
                    };
                    this.showSourceView(classObj, context);
                } else {
                    console.error("Class data not found for source view.");
                }
            }
        });
    },

    findClassObject(moduleName, packageName, className) {
        if (typeof fullReport === 'undefined') return null;

        const module = fullReport.modules.find(m => m.name === moduleName);
        if (!module) return null;

        const pkg = (module.packages || []).find(p => p.name === packageName);
        if (!pkg) return null;

        return (pkg.classes || []).find(c => c.name === className);
    },

    showSourceView(classData, context) {
        document.getElementById('report-view').classList.add('hidden-view');
        document.getElementById('source-view').classList.remove('hidden-view');
        document.getElementById('report-view-controls').classList.add('hidden');
        document.getElementById('source-view-controls').classList.remove('hidden');

        SourceViewApp.loadAndRender(classData, context);
    },

    showReportView() {
        document.getElementById('source-view').classList.add('hidden-view');
        document.getElementById('report-view').classList.remove('hidden-view');
        document.getElementById('source-view-controls').classList.add('hidden');
        document.getElementById('report-view-controls').classList.remove('hidden');
    }
}

/**
 * UI Utilities
 * Collection of helper functions for DOM manipulation and common UI patterns.
 */
const UIUtils = {
    /**
     * Builds a multi-select dropdown with "Select All" / "Clear" actions
     * and a scrollable list of options.
     */
    buildActionDropdown(container, options, selectedStateArr, onSelectionChange, searchable = true, multiSelect = true) {
        if (!container) return;

        container.innerHTML = "";
        container.style.padding = "0";
        container.style.overflow = "hidden";

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
                selectedStateArr.length = 0;
                options.forEach(o => {
                    if (o.value !== "all") selectedStateArr.push(o.value);
                });
                Array.from(listZone.querySelectorAll("input[type='checkbox']")).forEach(cb => cb.checked = true);
                onSelectionChange();
            });

            clearBtn.addEventListener("click", (e) => {
                e.stopPropagation();
                e.preventDefault();
                selectedStateArr.length = 0;
                Array.from(listZone.querySelectorAll("input[type='checkbox']")).forEach(cb => cb.checked = false);
                onSelectionChange();
            });
        }

        const listZone = document.createElement("div");
        listZone.className = "dropdown-scroll-zone";
        container.appendChild(listZone);

        const renderList = () => {
            listZone.innerHTML = "";

            if (options.length === 0) {
                listZone.innerHTML = `<div class="p-4 text-xs text-gray-400 text-center">No options available</div>`;
                return;
            }

            options.forEach(opt => {
                if (opt.value === "all") return;

                const isChecked = selectedStateArr.includes(opt.value);

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
                            if (!selectedStateArr.includes(opt.value)) selectedStateArr.push(opt.value);
                        } else {
                            const idx = selectedStateArr.indexOf(opt.value);
                            if (idx > -1) selectedStateArr.splice(idx, 1);
                        }
                        onSelectionChange();
                    });
                } else {
                    const isSelected = (selectedStateArr.length === 0 && opt.value === 'Aggregated') ||
                                     (selectedStateArr.length === 1 && selectedStateArr[0] === opt.value);

                    if (isSelected) {
                        item.classList.add('active-popover-item');
                    }

                    item.addEventListener("click", (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        selectedStateArr.length = 0;
                        selectedStateArr.push(opt.value);
                        onSelectionChange();
                        container.classList.add('hidden');
                    });
                }

                const label = document.createElement("span");
                label.textContent = opt.name;

                item.appendChild(label);
                listZone.appendChild(item);
            });
        };

        renderList();

        if (searchInput) {
            searchInput.addEventListener("input", (e) => {
                const term = e.target.value.toLowerCase();
                const items = listZone.querySelectorAll(".popover-item");
                items.forEach(item => {
                    const label = item.querySelector("span").textContent.toLowerCase();
                    item.style.display = label.includes(term) ? "flex" : "none";
                });
            });
        }
    },

    getFilterLabel(prefix, selectedArr, totalCount) {
        if (selectedArr.length === 0 || selectedArr.length === totalCount) return `${prefix}: All`;
        if (selectedArr.length === 1) return `${prefix}: ${selectedArr[0]}`;
        return `${prefix}: ${selectedArr.length} Selected`;
    },

    getCoverageClass(percentage) {
        if (percentage === '--') return 'text-gray-500';
        if (percentage >= 80) return 'text-green-600';
        if (percentage >= 60) return 'text-yellow-600';
        return 'text-red-600';
    }
};

const CoverageReportApp = {
    state: {
        viewMode: 'flat', // 'flat' or 'tree'
        currentView: 'modules', // 'modules', 'packages', 'classes'
        selectedModule: null,
        selectedPackage: null,
        filters: { modules: [], testSuite: 'Aggregated', packages: [], classes: [], variants: [], search: '' },
        density: 'comfy',
        sort: { by: 'name', order: 'asc' },
    },
    elements: {},
    fullReport: null,
    allPackages: [],
    allClasses: [],

    init(fullReport) {
        this.fullReport = fullReport;
        this.cacheDOMElements();
        this.populateHeaderInfo();
        this.populateGlobalStats();
        this.populateFilters();
        this.bindEvents();
        this.render();
        this.closeDropdownOnClickOutside();
    },

    cacheDOMElements() {
        this.elements = {
            headerTitle: document.querySelector('.header-title'),
            headerDate: document.querySelector('.header-date'),
            testSuiteFilterBtn: document.getElementById('testsuite-filter-btn'),
            testSuiteFilterText: document.getElementById('testsuite-filter-text'),
            testSuiteFilterDropdown: document.getElementById('testsuite-filter-dropdown'),
            testSuiteFilterList: document.getElementById('testsuite-filter-list'),
            tsAllState: document.getElementById('ts-all-state'),
            tsSelectedState: document.getElementById('ts-selected-state'),

            // Chips containers
            modChipContainer: document.getElementById('mod-chip-container'),
            pkgChipContainer: document.getElementById('pkg-chip-container'),
            clsChipContainer: document.getElementById('cls-chip-container'),

            // Buttons & Dropdowns
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

            variantFilterBtn: document.getElementById('var-filter-btn'),
            variantFilterDropdown: document.getElementById('var-dropdown'),
            variantFilterList: document.getElementById('var-filter-list'),

            addFilterBtn: document.getElementById('add-filter-btn'),
            addFilterDropdown: document.getElementById('add-filter-dropdown'),
            addFilterList: document.getElementById('add-filter-list'),

            searchRevealBtn: document.getElementById('search-reveal-btn'),
            searchWrapper: document.getElementById('search-wrapper'),
            searchInput: document.getElementById('search-input'),
            searchClearBtn: document.getElementById('search-clear-btn'),

            viewSegments: document.getElementById('view-segments'),
            densitySegments: document.getElementById('density-segments'),
            mainTable: document.querySelector('.table-container table'),

            flatBreadcrumbs: document.getElementById('flat-breadcrumbs'),
            groupByBtn: document.getElementById('group-by-btn'),
            groupByText: document.getElementById('group-by-text'),
            groupByDropdown: document.getElementById('group-by-dropdown'),

            tableHeaders: document.getElementById('table-headers'),
            coverageData: document.getElementById('coverage-data'),
            totalModules: document.getElementById('total-modules'),
            totalPackages: document.getElementById('total-packages'),
            totalClasses: document.getElementById('total-classes'),
        };
    },

    toggleDropdown(dropdownToToggle) {
        this.getDropdownConfigs().forEach(({ dropdown }) => {
            if (dropdown && dropdown !== dropdownToToggle) {
                dropdown.classList.add('hidden');
            }
        });

        if (dropdownToToggle) {
            dropdownToToggle.classList.toggle('hidden');
        }
    },

    populateHeaderInfo() {
        if(this.fullReport.name) this.elements.headerTitle.textContent = this.fullReport.name;
        if(this.fullReport.timeStamp) this.elements.headerDate.textContent = this.fullReport.timeStamp;
    },

    populateGlobalStats() {
        this.elements.totalModules.textContent = this.fullReport.modules.length;
        let totalPackages = 0;
        let totalClasses = 0;
        this.fullReport.modules.forEach(module => {
            const packages = module.packages || [];
            totalPackages += packages.length;
            packages.forEach(pkg => {
                totalClasses += (pkg.classes || []).length;
            });
        });
        this.elements.totalPackages.textContent = totalPackages;
        this.elements.totalClasses.textContent = totalClasses;
    },

    populateFilters() {
        this.allPackages = this.fullReport.modules.flatMap(m => (m.packages || []).map(p => ({ name: p.name, moduleName: m.name })) );
        this.allClasses = this.fullReport.modules.flatMap(m =>
            (m.packages || []).flatMap(p =>
                (p.classes || []).map(c => ({ name: c.name, packageName: p.name, moduleName: m.name }))
            )
        );

        // Variants
        let allVariants = [];
        if (this.fullReport.variantCoverages && this.fullReport.variantCoverages.length > 0) {
            allVariants = this.fullReport.variantCoverages.map(v => v.name);
        }

        if (this.state.filters.variants.length === 0 && allVariants.length > 0) {
            this.state.filters.variants = [...allVariants];
        }

        const variantOptions = allVariants.map(v => ({name: v, value: v}));

        UIUtils.buildActionDropdown(this.elements.variantFilterDropdown, variantOptions, this.state.filters.variants, () => {
            this.updateVariantButtonText();
            this.render();
        }, false);

        this.updateDynamicFilters();
    },

    getDropdownConfigs() {
        return [
            { btn: this.elements.moduleFilterBtn, dropdown: this.elements.moduleFilterDropdown },
            { btn: this.elements.testSuiteFilterBtn, dropdown: this.elements.testSuiteFilterDropdown },
            { btn: this.elements.packageFilterBtn, dropdown: this.elements.packageFilterDropdown },
            { btn: this.elements.classFilterBtn, dropdown: this.elements.classFilterDropdown },
            { btn: this.elements.variantFilterBtn, dropdown: this.elements.variantFilterDropdown },
            { btn: this.elements.addFilterBtn, dropdown: this.elements.addFilterDropdown },
            { btn: this.elements.groupByBtn, dropdown: this.elements.groupByDropdown }
        ];
    },

    bindEvents() {
        this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
            if (btn && dropdown) {
                btn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.toggleDropdown(dropdown);
                });
            }
        });

        if (this.elements.groupByDropdown) {
            this.elements.groupByDropdown.addEventListener('click', this.handleGroupByChange.bind(this));
        }
        this.elements.coverageData.addEventListener('click', this.handleRowClick.bind(this));
        this.elements.tableHeaders.addEventListener('click', this.handleHeaderClick.bind(this));
        this.elements.flatBreadcrumbs.addEventListener('click', this.handleBreadcrumbClick.bind(this));

        this.elements.testSuiteFilterList.addEventListener('click', this.handleFilterSelection.bind(this, 'testSuite'));
        if (this.elements.moduleFilterList) this.elements.moduleFilterList.addEventListener('click', this.handleFilterSelection.bind(this, 'module'));
        if (this.elements.packageFilterList) this.elements.packageFilterList.addEventListener('click', this.handleFilterSelection.bind(this, 'package'));
        if (this.elements.classFilterList) this.elements.classFilterList.addEventListener('click', this.handleFilterSelection.bind(this, 'class'));
        if (this.elements.variantFilterList) this.elements.variantFilterList.addEventListener('change', this.handleVariantSelection.bind(this));

        // Add Filter Logic
        if (this.elements.addFilterList) {
            this.elements.addFilterList.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                const target = e.target.closest('.dropdown-item');
                if (!target) return;

                const filterType = target.dataset.filterType;
                if (filterType === 'module') this.elements.modChipContainer.classList.remove('hidden');
                if (filterType === 'package') this.elements.pkgChipContainer.classList.remove('hidden');
                if (filterType === 'class') this.elements.clsChipContainer.classList.remove('hidden');

                this.elements.addFilterDropdown.classList.add('hidden');
                this.handleHeaderFilterChange(filterType);
                this.updateFilterButtons();
                this.render();

                // Auto-open newly added dropdown
                setTimeout(() => {
                    if (filterType === 'module') {
                        this.toggleDropdown(this.elements.moduleFilterDropdown);
                        this.elements.moduleFilterDropdown.classList.remove('hidden');
                    }
                    if (filterType === 'package') {
                        this.toggleDropdown(this.elements.packageFilterDropdown);
                        this.elements.packageFilterDropdown.classList.remove('hidden');
                    }
                    if (filterType === 'class') {
                        this.toggleDropdown(this.elements.classFilterDropdown);
                        this.elements.classFilterDropdown.classList.remove('hidden');
                    }
                }, 0);
            });
        }

        // Close Filter Logic
        document.querySelectorAll('.chip-close').forEach(closeBtn => {
            closeBtn.addEventListener('click', (e) => {
                e.stopPropagation(); // Prevent dropdown from opening
                const filterType = closeBtn.dataset.filterClose;
                if (filterType) {
                    // Mapping data-filter-close values to state.filters array keys
                    const filterKey = filterType === 'module' ? 'modules' :
                                      filterType === 'package' ? 'packages' :
                                      filterType === 'class' ? 'classes' : filterType;

                    this.state.filters[filterKey] = [];

                    if (filterType === 'module') this.elements.modChipContainer.classList.add('hidden');
                    if (filterType === 'package') this.elements.pkgChipContainer.classList.add('hidden');
                    if (filterType === 'class') this.elements.clsChipContainer.classList.add('hidden');

                    this.handleHeaderFilterChange();
                    this.updateFilterButtons();
                    this.render();
                }
            });
        });

        // Search Reveal Logic
        if (this.elements.searchRevealBtn) {
            this.elements.searchRevealBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.elements.searchRevealBtn.classList.add('hidden');
                this.elements.searchWrapper.classList.add('expanded');
                this.elements.searchInput.focus();
            });
        }

        // View Segments (Flat/Tree)
        if (this.elements.viewSegments) {
            this.elements.viewSegments.addEventListener('click', (e) => {
                const btn = e.target.closest('.segment-btn');
                if (!btn) return;

                // Update UI Active State
                this.elements.viewSegments.querySelectorAll('.segment-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');

                // Update State and Render
                this.state.viewMode = btn.dataset.value;
                this.resetSelection();
                this.render();
            });
        }

        this.elements.searchInput.addEventListener('input', () => {
            const searchTerm = this.elements.searchInput.value.trim().toLowerCase();
            this.state.filters.search = searchTerm;
            if (searchTerm.length > 0) {
                this.elements.searchClearBtn.classList.remove('hidden');
            } else {
                this.elements.searchClearBtn.classList.add('hidden');
            }
            this.render();
        });
        this.elements.searchClearBtn.addEventListener('click', () => {
            this.elements.searchInput.value = '';
            this.state.filters.search = '';
            this.elements.searchClearBtn.classList.add('hidden');
            this.render();
            this.elements.searchInput.focus();
        });

        // Density Segments (Comfy/Compact)
        if (this.elements.densitySegments) {
            this.elements.densitySegments.addEventListener('click', (e) => {
                const btn = e.target.closest('.segment-btn');
                if (!btn) return;

                this.elements.densitySegments.querySelectorAll('.segment-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');

                this.state.density = btn.dataset.value;
                if (this.state.density === 'compact') {
                    this.elements.mainTable.classList.add('table-compact');
                } else {
                    this.elements.mainTable.classList.remove('table-compact');
                }
            });
        }
    },

    handleVariantSelection(e) {
        // This is now handled by buildActionDropdown's onSelectionChange callback
    },

    updateFilterButtons() {
        let activeChipsCount = 0;
        const { filters } = this.state;

        const updateChip = (type, stateArray, totalCount, textElement, chipContainer) => {
            if (!textElement) return;
            textElement.textContent = UIUtils.getFilterLabel(type.charAt(0).toUpperCase() + type.slice(1), stateArray, totalCount);
            if (stateArray.length > 0 || !chipContainer.classList.contains('hidden')) {
                chipContainer.classList.remove('hidden');
                activeChipsCount++;
                this.toggleAddFilterOption(type, false);
            } else {
                this.toggleAddFilterOption(type, true);
            }
        };

        updateChip('module', filters.modules, this.fullReport.modules.length, this.elements.moduleFilterText, this.elements.modChipContainer);
        updateChip('package', filters.packages, this.allPackages.length, this.elements.packageFilterText, this.elements.pkgChipContainer);
        updateChip('class', filters.classes, this.allClasses.length, this.elements.classFilterText, this.elements.clsChipContainer);

        // Hide Add Filter button if all chips are visible
        if (this.elements.addFilterBtn) {
            if (activeChipsCount === 3) {
                this.elements.addFilterBtn.closest('#add-filter-container').classList.add('hidden');
            } else {
                this.elements.addFilterBtn.closest('#add-filter-container').classList.remove('hidden');
            }
        }

        if (filters.testSuite === 'Aggregated') {
            this.elements.tsAllState.classList.remove('hidden');
            this.elements.tsSelectedState.classList.add('hidden');
        } else {
            this.elements.tsAllState.classList.add('hidden');
            this.elements.tsSelectedState.classList.remove('hidden');
            this.elements.testSuiteFilterText.textContent = filters.testSuite;
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

    updateVariantButtonText() {
        const selectedCount = this.state.filters.variants.length;
        let allVariantsCount = 0;
         if (this.fullReport.variantCoverages && this.fullReport.variantCoverages.length > 0) {
            allVariantsCount = this.fullReport.variantCoverages.length;
        }

        if (this.elements.variantFilterBtn) {
            if (selectedCount === allVariantsCount && allVariantsCount > 0) {
                this.elements.variantFilterBtn.setAttribute('data-tooltip', 'Filter by Variant: All');
            } else if (selectedCount === 1) {
                this.elements.variantFilterBtn.setAttribute('data-tooltip', `Filter by Variant: ${this.state.filters.variants[0]}`);
            } else {
                this.elements.variantFilterBtn.setAttribute('data-tooltip', `Filter by Variant: ${selectedCount} Selected`);
            }
        }
    },

    renderBreadcrumbs() {
        if(this.state.viewMode !== 'flat') {
            this.elements.flatBreadcrumbs.innerHTML = '';
            return;
        }
        const { selectedModule, selectedPackage } = this.state; let html = '';
        // "Project" is the root link
        if(selectedModule) {
            html += `<a href="#" class="breadcrumb-link" data-action="go-to-modules">Project</a>`;
        } else {
            html += `<span class="breadcrumb-current">Project</span>`;
        }
        // Module level
        if(selectedModule) {
            html += `<span class="breadcrumb-separator">/</span>`;
            if(selectedPackage) {
                html += `<a href="#" class="breadcrumb-link" data-action="go-to-packages">${selectedModule}</a>`;
            } else {
                html += `<span class="breadcrumb-current">${selectedModule}</span>`;
            }
        }
        // Package level
        if(selectedPackage) {
            html += `<span class="breadcrumb-separator">/</span>`;
            html += `<span class="breadcrumb-current">${selectedPackage}</span>`;
        }
        this.elements.flatBreadcrumbs.innerHTML = html;
    },

    handleBreadcrumbClick(e) {
        const link = e.target.closest('a[data-action]');
        if(!link) return;
        e.preventDefault();
        const action = link.dataset.action;
        switch(action) {
            case 'go-to-modules':
                this.state.currentView = 'modules';
                this.resetSelection();
                this.render();
                break;
            case 'go-to-packages':
                this.state.currentView = 'packages';
                this.state.selectedPackage = null;
                this.render();
                break;
        }
    },

    handleHeaderClick(e) {
        const th = e.target.closest('[data-sort-by]');
        if (!th) return;

        const newSortBy = th.dataset.sortBy;
        if (this.state.sort.by === newSortBy) {
            this.state.sort.order = this.state.sort.order === 'asc' ? 'desc' : 'asc';
        } else {
            this.state.sort.by = newSortBy;
            this.state.sort.order = 'asc';
        }
        this.render();
    },

    handleFilterSelection(filterType, e) {
        // This is now handled by buildActionDropdown's onSelectionChange callback
    },

    handleHeaderFilterChange(explicitType = null) {
        if (this.state.viewMode !== 'flat') return;

        if (explicitType) {
            const viewMap = { 'module': 'modules', 'package': 'packages', 'class': 'classes' };
            if (viewMap[explicitType]) {
                this.state.currentView = viewMap[explicitType];
            }
        } else {
            const { classes, packages, modules } = this.state.filters;
            const activeChips = [];
            if (!this.elements.clsChipContainer.classList.contains('hidden')) activeChips.push('class');
            if (!this.elements.pkgChipContainer.classList.contains('hidden')) activeChips.push('package');
            if (!this.elements.modChipContainer.classList.contains('hidden')) activeChips.push('module');

            if (classes.length > 0 || activeChips.includes('class')) {
                this.state.currentView = 'classes';
            } else if (packages.length > 0 || activeChips.includes('package')) {
                this.state.currentView = 'packages';
            } else {
                // Always fallback to modules if deeper hierarchies aren't active
                this.state.currentView = 'modules';
            }
        }

        // Reset drill-down context to avoid confusing states when grouping abruptly changes
        this.resetSelection();
    },

    updateDynamicFilters() {
        const { selectedModule, selectedPackage, filters } = this.state;
        let contextModules = this.fullReport.modules;

        if (selectedModule) {
            contextModules = contextModules.filter(m => m.name === selectedModule);
        }

        if (filters.modules.length > 0) {
            contextModules = contextModules.filter(m => filters.modules.includes(m.name));
        }

        let basePackages = contextModules.flatMap(m => m.packages || []);
        if (selectedPackage) {
            basePackages = basePackages.filter(p => p.name === selectedPackage);
        }

        let filteredPackages = basePackages;
        if (filters.packages.length > 0) {
            filteredPackages = basePackages.filter(p => filters.packages.includes(p.name));
        }

        const moduleOptions = this.fullReport.modules.map(m => ({ name: m.name, value: m.name }));

        const testSuiteOptions = [...new Set(contextModules.flatMap(m => (m.testSuiteCoverages || []).map(ts => ts.name)))]
                .sort()
                .map(name => ({ name: name === 'Aggregated' ? 'All' : name, value: name }));
        const aggIndex = testSuiteOptions.findIndex(o => o.value === 'Aggregated');
        if (aggIndex > -1) {
            testSuiteOptions.unshift(testSuiteOptions.splice(aggIndex, 1)[0]);
        }

        const packageOptions = [...new Set(basePackages.map(p => p.name))]
                .sort()
                .map(name => ({ name, value: name }));

        const classOptions = [...new Set(filteredPackages.flatMap(p => p.classes || []).map(c => c.name))]
                .sort()
                .map(name => ({ name, value: name }));

        UIUtils.buildActionDropdown(this.elements.moduleFilterDropdown, moduleOptions, filters.modules, () => {
            filters.packages = [];
            filters.classes = [];
            this.handleHeaderFilterChange('module');
            this.updateFilterButtons();
            this.render();
        });

        const testSuiteStateWrapper = [filters.testSuite];
        UIUtils.buildActionDropdown(this.elements.testSuiteFilterDropdown, testSuiteOptions, testSuiteStateWrapper, () => {
            filters.testSuite = testSuiteStateWrapper[0] || 'Aggregated';
            this.updateFilterButtons();
            this.render();

            if (typeof SourceViewApp !== 'undefined' && SourceViewApp.classData) {
                SourceViewApp.context.testSuiteName = filters.testSuite;
                if (!document.getElementById('source-view').classList.contains('hidden-view')) {
                    SourceViewApp.render();
                }
            }
        }, false, false);

        UIUtils.buildActionDropdown(this.elements.packageFilterDropdown, packageOptions, filters.packages, () => {
            filters.classes = [];
            this.handleHeaderFilterChange('package');
            this.updateFilterButtons();
            this.render();
        });

        UIUtils.buildActionDropdown(this.elements.classFilterDropdown, classOptions, filters.classes, () => {
            this.handleHeaderFilterChange('class');
            this.updateFilterButtons();
            this.render();
        });
    },

    closeDropdownOnClickOutside() {
        document.addEventListener('click', (event) => {
            this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
                if (btn && dropdown && !btn.contains(event.target) && !dropdown.contains(event.target)) {
                    dropdown.classList.add('hidden');
                }
            });

            // Search Input Collapse
            if (this.elements.searchWrapper && this.elements.searchRevealBtn) {
                if (!this.elements.searchWrapper.contains(event.target) && !this.elements.searchRevealBtn.contains(event.target)) {
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

    handleGroupByChange(e) {
        e.preventDefault();
        const target = e.target.closest('.dropdown-item');
        if (!target) return;

        this.state.currentView = target.dataset.value;
        this.elements.groupByDropdown.classList.add('hidden');
        this.resetSelection();
        this.render();
    },

    handleRowClick(e) {
        if (this.state.viewMode === 'flat') {
            const td = e.target.closest('td[data-name]');
            if (td) this.handleFlatRowClick(td);
        } else {
            this.handleTreeRowClick(e.target);
        }
    },

    handleFlatRowClick(td) {
        const { name, type, moduleName } = td.dataset;
        if (type === 'module') {
            this.state.selectedModule = name;
            this.state.currentView = 'packages';
        } else if (type === 'package') {
            this.state.selectedModule = moduleName;
            this.state.selectedPackage = name;
            this.state.currentView = 'classes';
        }
        this.render();
    },

    handleTreeRowClick(target) {
        const row = target.closest('tr');
        const arrow = row?.querySelector('.collapsible-arrow:not(.invisible)');
        if (!arrow) return;

        arrow.classList.toggle('open');
        const children = document.querySelectorAll(`[data-parent-id="${row.dataset.id}"]`);
        if (arrow.classList.contains('open')) {
            children.forEach(child => child.classList.remove('hidden'));
        } else {
            this.collapseDescendants(row);
        }
    },

    collapseDescendants(parentRow) {
        document.querySelectorAll(`[data-parent-id="${parentRow.dataset.id}"]`).forEach(child => {
            child.classList.add('hidden');
            const childArrow = child.querySelector('.collapsible-arrow.open');
            if (childArrow) {
                childArrow.classList.remove('open');
                this.collapseDescendants(child);
            }
        });
    },

    resetSelection() {
        this.state.selectedModule = null;
        this.state.selectedPackage = null;
    },

    getSortedData(data) {
        const { by, order } = this.state.sort;
        if (!by) return data;

        return [...data].sort((a, b) => {
            let valueA, valueB;

            if (by === 'name') {
                valueA = a.name;
                valueB = b.name;
            } else {
                const parts = by.split('.');
                const type = parts[0];
                const variant = parts[1];
                const field = parts[2];

                const va = a.variantCoverages ? a.variantCoverages.find(v => v.name === variant) : null;
                const vb = b.variantCoverages ? b.variantCoverages.find(v => v.name === variant) : null;

                valueA = va ? va[type][field] : 0;
                valueB = vb ? vb[type][field] : 0;
            }

            if (typeof valueA === 'string') {
                return order === 'asc' ? valueA.localeCompare(valueB) : valueB.localeCompare(valueA);
            }
            const numA = valueA || 0;
            const numB = valueB || 0;
            return order === 'asc' ? numA - numB : numB - numA;
        });
    },

    render() {
        this.updateDynamicFilters();
        this.updateGroupByText();
        this.renderBreadcrumbs();
        let dataToRender = this.getFilteredData();
        dataToRender = this.getSortedData(dataToRender);

        this.renderTable(dataToRender);

        this.updateTooltipsForOverflow();
    },

    updateGroupByText() {
        if (!this.elements.groupByText) return;
        const viewMap = {
            'modules': 'Modules',
            'packages': 'Packages',
            'classes': 'Classes'
        };
        this.elements.groupByText.textContent = viewMap[this.state.currentView] || 'Modules';

        // Hide Group By entirely in Tree mode
        if (this.elements.groupByBtn) {
            this.elements.groupByBtn.parentElement.style.display = this.state.viewMode === 'flat' ? 'block' : 'none';
        }
    },

    filterHierarchicalData(modules, term) {
        if (!term) return modules;

        term = term.toLowerCase();

        return modules.map(module => {
            if (module.name.toLowerCase().includes(term)) {
                return { ...module };
            }
            const filteredPackages = (module.packages || []).map(pkg => {
                if (pkg.name.toLowerCase().includes(term)) {
                    return { ...pkg };
                }

                const filteredClasses = (pkg.classes || []).filter(cls =>
                    cls.name.toLowerCase().includes(term)
                );

                if (filteredClasses.length > 0) {
                    return { ...pkg, classes: filteredClasses };
                }

                return null;
            }).filter(Boolean);

            if (filteredPackages.length > 0) {
                return { ...module, packages: filteredPackages };
            }

            return null;
        }).filter(Boolean);
    },

    getFilteredData() {
        const { viewMode, currentView, selectedModule, selectedPackage, filters } = this.state;

        const getEffectiveCoverage = (item) => {
            if (!item.testSuiteCoverages) return [];

            const suite = item.testSuiteCoverages.find(ts => ts.name === filters.testSuite);

            return suite ? suite.variantCoverages : [];
        };

        const addEffectiveCoverage = (item, type, context = {}) => {
            const newItem = { ...item, type, ...context, testSuiteName: filters.testSuite };
            newItem.variantCoverages = getEffectiveCoverage(item);
            return newItem;
        };

        let hierarchicalData = this.fullReport.modules.map(m => {
            const moduleWithCoverage = addEffectiveCoverage(m, 'module');
            moduleWithCoverage.packages = (m.packages || []).map(p => {
                const pkgWithCoverage = addEffectiveCoverage(p, 'package');
                pkgWithCoverage.classes = (p.classes || []).map(c => addEffectiveCoverage(c, 'class'));
                return pkgWithCoverage;
            });
            return moduleWithCoverage;
        });

        if (filters.search) {
            hierarchicalData = this.filterHierarchicalData(hierarchicalData, filters.search);
        }

        if (filters.modules.length > 0) {
            hierarchicalData = hierarchicalData.filter(m => filters.modules.includes(m.name));
        }
        if (filters.packages.length > 0) {
            hierarchicalData = hierarchicalData.map(m => ({
                ...m,
                packages: m.packages.filter(p => filters.packages.includes(p.name))
            })).filter(m => m.packages.length > 0);
        }
        if (filters.classes.length > 0) {
            hierarchicalData = hierarchicalData.map(m => ({
                ...m,
                packages: m.packages.map(p => ({
                    ...p,
                    classes: p.classes.filter(c => filters.classes.includes(c.name))
                })).filter(p => p.classes.length > 0)
            })).filter(m => m.packages.length > 0);
        }
        if (viewMode === 'tree') {
            return hierarchicalData;
        }

        let flatData;

        if (selectedPackage) {
            const module = hierarchicalData.find(m => m.name === selectedModule);
            const pkg = module?.packages.find(p => p.name === selectedPackage);
            flatData = (pkg?.classes || []).map(c => addEffectiveCoverage(c, 'class', { packageName: pkg.name, moduleName: module.name }));
        } else if (selectedModule) {
            const module = hierarchicalData.find(m => m.name === selectedModule);
            flatData = (module?.packages || []).map(p => addEffectiveCoverage(p, 'package', { moduleName: module.name }));
        } else {
            if (currentView === 'packages') {
                flatData = hierarchicalData.flatMap(m =>
                    (m.packages || []).map(p => addEffectiveCoverage(p, 'package', { moduleName: m.name }))
                );
            } else if (currentView === 'classes') {
                flatData = hierarchicalData.flatMap(m =>
                    (m.packages || []).flatMap(p =>
                        (p.classes || []).map(c => addEffectiveCoverage(c, 'class', { packageName: p.name, moduleName: m.name }))
                    )
                );
            } else {
                flatData = hierarchicalData;
            }
        }

        return flatData;
    },

    renderTable(dataToRender) {
        this.renderHeaders();
        if (this.state.viewMode === 'tree') {
            this.renderTreeRows(dataToRender);
        } else {
            this.renderFlatRows(dataToRender);
        }
    },

    updateTooltipsForOverflow() {
        // Sticky name columns (Class/Package/Module names)
        const nameCells = this.elements.coverageData.querySelectorAll('.sticky-name');
        nameCells.forEach(cell => {
            const isOverflowing = cell.scrollWidth > cell.clientWidth;
            if (!isOverflowing) {
                cell.removeAttribute('title');
            }
        });

        // Truncated context columns (Module/Package paths)
        const pathCells = this.elements.coverageData.querySelectorAll('.truncate, .max-w-300');
        pathCells.forEach(cell => {
            // Check if any child block is overflowing
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

    getCoverageValues(item, variantName) {
        const found = item.variantCoverages ? item.variantCoverages.find(v => v.name === variantName) : null;

        if (found) {
            return {
                instrPercent: found.instruction.percent + '%',
                instrRatio: `${found.instruction.covered}/${found.instruction.total}`,
                branchPercent: found.branch.percent + '%',
                branchRatio: `${found.branch.covered}/${found.branch.total}`,
                instrColor: UIUtils.getCoverageClass(found.instruction.percent),
                branchColor: UIUtils.getCoverageClass(found.branch.percent)
            };
        }

        return {
            instrPercent: '--',
            instrRatio: '0/0',
            branchPercent: '--',
            branchRatio: '0/0',
            instrColor: UIUtils.getCoverageClass('--'),
            branchColor: UIUtils.getCoverageClass('--')
        };
    },

    renderHeaders() {
        const { viewMode, currentView, filters, sort } = this.state;
        const topHeader = document.createElement('tr');
        topHeader.className = "border-b border-gray-200";
        const subHeader = document.createElement('tr');
        subHeader.className = "border-b border-gray-200";

        const mainHeaderTitle = viewMode === 'tree' ? 'Module' : currentView.charAt(0).toUpperCase() + currentView.slice(1);
        const firstColClass = "py-4 px-6 text-left font-semibold text-gray-700 sticky-name bg-gray-50 z-30";
        const sortIndicator = (key) => sort.by === key ? (sort.order === 'asc' ? '▲' : '▼') : '';
        topHeader.innerHTML = `<th class="${firstColClass}" data-sort-by="name">${mainHeaderTitle} ${sortIndicator('name')}</th>`;
        subHeader.innerHTML = `<th class="py-2 px-6 sticky-name bg-gray-50 z-30"></th>`;

        if (viewMode === 'flat' && !this.state.selectedModule) {
            if (currentView === 'classes') {
                topHeader.innerHTML += `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30">Path</th>`;
                subHeader.innerHTML += `<th class="py-2 px-6 sticky-name bg-gray-50 z-30"></th>`;
            } else if (currentView === 'packages') {
                topHeader.innerHTML += `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30">Module</th>`;
                subHeader.innerHTML += `<th class="py-2 px-6 sticky-name bg-gray-50 z-30"></th>`;
            }
        }

        const variants = [...filters.variants].sort();

        variants.forEach(v => {
            const vals = this.getCoverageValues(this.fullReport, v);

            topHeader.innerHTML += `<th colspan="2" class="py-4 px-4 text-center font-semibold text-gray-700 border-l border-gray-200">
                <div class="flex flex-col"><span>${v}</span><span class="text-sm font-bold ${vals.instrColor} mt-1">${vals.instrPercent}</span></div>
            </th>`;

            const instrKey = `instruction.${v}.percent`;
            const branchKey = `branch.${v}.percent`;
            subHeader.innerHTML += `<th class="py-2 px-4 text-center text-xs font-medium text-gray-600 border-l border-gray-200" data-sort-by="${instrKey}">Instruction ${sortIndicator(instrKey)}</th>
                                    <th class="py-2 px-4 text-center text-xs font-medium text-gray-600" data-sort-by="${branchKey}">Branch ${sortIndicator(branchKey)}</th>`;
        });

        this.elements.tableHeaders.innerHTML = '';
        this.elements.tableHeaders.appendChild(topHeader);
        this.elements.tableHeaders.appendChild(subHeader);
    },

    renderTreeRows(modules) {
        const isSearching = !!this.state.filters.search;

        const renderRow = (item, level, type, parentId = '', context = {}) => {
            const hasChildren = type !== 'class' && (
                (item.packages && item.packages.length > 0) ||
                (item.classes && item.classes.length > 0)
            );

            let nameContent;
            if (type === 'class') {
                nameContent = `<a href="#" class="font-medium text-blue-700 hover:underline class-link" data-class-name="${item.name}" data-module-name="${context.moduleName}" data-package-name="${context.packageName}" data-test-suite-name="${context.testSuiteName || ''}">${item.name}</a>`;
            } else {
                nameContent = `<span class="font-medium text-gray-900">${item.name}</span>`;
            }

            const chevron = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="collapsible-arrow w-4 h-4 text-gray-600 ${!hasChildren ? 'invisible' : ''} ${isSearching ? 'open' : ''}"><path d="m9 18 6-6-6-6"></path></svg>`;
            const coverageCells = [...this.state.filters.variants].sort().map(v => {
                const vals = this.getCoverageValues(item, v);
                return `
                <td class="py-3 px-4 text-center"><div class="flex flex-col"><span class="font-bold ${vals.instrColor}">${vals.instrPercent}</span><span class="text-xs text-gray-500">${vals.instrRatio}</span></div></td>
                <td class="py-3 px-4 text-center"><div class="flex flex-col"><span class="font-bold ${vals.branchColor}">${vals.branchPercent}</span><span class="text-xs text-gray-500">${vals.branchRatio}</span></div></td>
            `}).join('');

            const rowClasses = `table-row border-b border-gray-200 hover:bg-gray-50 ${level > 0 && !isSearching ? 'child-row hidden' : 'child-row'}`;

            return `<tr class="${rowClasses}" data-id="${item.name}" data-parent-id="${parentId}">
                <td class="py-3 px-6 sticky-name" title="${item.name}"><div class="flex items-center gap-2 cursor-pointer pl-level-${level}">${chevron}${nameContent}</div></td>
                ${coverageCells}
            </tr>`;
        };

        let html = modules.map(module => {
            let moduleContext = { moduleName: module.name };
            let childrenHtml = (module.packages || []).map(pkg => {
                let packageContext = { ...moduleContext, packageName: pkg.name };
                return renderRow(pkg, 1, 'package', module.name, packageContext) +
                    (pkg.classes || []).map(cls => renderRow(cls, 2, 'class', pkg.name, packageContext)).join('');
            }).join('');

            return renderRow(module, 0, 'module', '', moduleContext) + childrenHtml;
        }).join('');
        this.elements.coverageData.innerHTML = html;
    },

    renderFlatRows(data) {
        if (!data.length) {
            this.elements.coverageData.innerHTML = `<tr><td colspan="10" class="text-center py-8 text-gray-500">No results found.</td></tr>`;
            return;
        }
        this.elements.coverageData.innerHTML = data.map(item => {
            const variants = [...this.state.filters.variants].sort();
            const coverageCells = variants.map(v => {
                const vals = this.getCoverageValues(item, v);
                return `
                <td class="py-3 px-4 text-center"><div class="flex flex-col"><span class="font-bold ${vals.instrColor}">${vals.instrPercent}</span><span class="text-xs text-gray-500">${vals.instrRatio}</span></div></td>
                <td class="py-3 px-4 text-center"><div class="flex flex-col"><span class="font-bold ${vals.branchColor}">${vals.branchPercent}</span><span class="text-xs text-gray-500">${vals.branchRatio}</span></div></td>
            `}).join('');

            let nameCell;
            switch (this.state.currentView) {
                case 'packages':
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" title="${item.name}" data-name="${item.name}" data-type="${item.type}" data-module-name="${item.moduleName}">${item.name}</td>`;
                    if (!this.state.selectedModule) {
                        nameCell += `<td class="py-3 px-6 text-gray-500 text-sm truncate max-w-150" title="${item.moduleName}">${item.moduleName}</td>`;
                    }
                    break;
                case 'classes':
                    nameCell = `<td class="py-3 px-6 sticky-name" title="${item.name}"><a href="#" class="font-medium text-blue-700 hover:underline class-link" data-class-name="${item.name}" data-module-name="${item.moduleName}" data-package-name="${item.packageName}">${item.name}</a></td>`;
                    if (!this.state.selectedModule) {
                        nameCell += `<td class="px-2 max-w-300" title="${item.moduleName} > ${item.packageName}">
                            <div class="flex flex-col" style="overflow: hidden; width: 100%;">
                                <span class="text-xs text-gray-500 truncate-block">${item.moduleName}</span>
                                <span class="text-sm text-gray-500 truncate-block">${item.packageName}</span>
                            </div>
                        </td>`;
                    }
                    break;
                default: // modules
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" title="${item.name}" data-name="${item.name}" data-type="${item.type}" data-module-name="${item.name}">${item.name}</td>`;
            }
            return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${nameCell}${coverageCells}</tr>`;
        }).join('');
    }
};

document.addEventListener('DOMContentLoaded', () => App.init());
