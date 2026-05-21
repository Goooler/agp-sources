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
                    Navigation.push();
                } else {
                    console.error("Class data not found for source view.");
                }
            }
        });

        Navigation.init();
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
        const state = {
            reportState: JSON.parse(JSON.stringify(CoverageReportApp.state)),
            sourceState: {
                classData: SourceViewApp.classData ? {
                    name: SourceViewApp.classData.name,
                    packageName: SourceViewApp.classData.packageName,
                    moduleName: SourceViewApp.context.moduleName
                } : null,
                context: SourceViewApp.context,
                selectedVariants: [...SourceViewApp.state.selectedVariants]
            },
            currentView: document.getElementById('source-view').classList.contains('hidden-view') ? 'report' : 'source',
            chipVisibility: {
                module: !CoverageReportApp.elements.modChipContainer.classList.contains('hidden'),
                package: !CoverageReportApp.elements.pkgChipContainer.classList.contains('hidden'),
                class: !CoverageReportApp.elements.clsChipContainer.classList.contains('hidden')
            }
        };

        // Always clear search from history state
        if (state.reportState && state.reportState.filters) {
            state.reportState.filters.search = '';
        }
        // Always remove density from history state to retain user choice across navigation
        if (state.reportState) {
            delete state.reportState.density;
        }
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

        // Restore Report filters
        CoverageReportApp.applyFilters(state.reportState.filters);

        // Reset search on both apps during navigation
        CoverageReportApp.resetSearchUI();
        CoverageReportApp.collapseSearchUI(false);

        if (typeof SourceViewApp !== 'undefined') {
            SourceViewApp.resetSearchUI();
            SourceViewApp.collapseSearchUI(false);
        }

        CoverageReportApp.state.viewMode = state.reportState.viewMode;
        CoverageReportApp.state.currentView = state.reportState.currentView;
        CoverageReportApp.state.selectedModule = state.reportState.selectedModule;
        CoverageReportApp.state.selectedPackage = state.reportState.selectedPackage;
        CoverageReportApp.state.sort = state.reportState.sort;
        CoverageReportApp.state.columnWidths = state.reportState.columnWidths || {};

        // Re-apply CSS variables for column widths
        for (const [id, width] of Object.entries(CoverageReportApp.state.columnWidths)) {
            document.documentElement.style.setProperty(`--col-width-${id.replace(/\./g, '-')}`, `${width}px`);
        }

        // Restore filter chips visibility
        CoverageReportApp.setFilterChipsVisibility(state.chipVisibility);

        // Update shared header UI
        CoverageReportApp.updateFilterButtons();
        CoverageReportApp.updateVariantButtonText();

        if (state.currentView === 'report') {
            App.showReportView();
            CoverageReportApp.updateVariantDropdown();
            CoverageReportApp.render(true); // pass true to skip replaceState
        } else if (state.sourceState.classData) {
            const classObj = App.findClassObject(
                state.sourceState.classData.moduleName,
                state.sourceState.classData.packageName,
                state.sourceState.classData.name
            );
            if (classObj) {
                SourceViewApp.state.selectedVariants = state.sourceState.selectedVariants;
                App.showSourceView(classObj, state.sourceState.context);
            }
        }
    }
};

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
        container.setAttribute("role", multiSelect ? "group" : "listbox");

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
                let item;

                if (multiSelect) {
                    item = document.createElement("label");
                    item.className = "popover-item";

                    const checkbox = document.createElement("input");
                    checkbox.type = "checkbox";
                    checkbox.className = "popover-checkbox";
                    checkbox.checked = isChecked;
                    item.appendChild(checkbox);

                    const handleChange = () => {
                        if (checkbox.checked) {
                            if (!selectedStateArr.includes(opt.value)) selectedStateArr.push(opt.value);
                        } else {
                            const idx = selectedStateArr.indexOf(opt.value);
                            if (idx > -1) selectedStateArr.splice(idx, 1);
                        }
                        onSelectionChange();
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

                    const label = document.createElement("span");
                    label.textContent = opt.name;
                    item.appendChild(label);
                } else {
                    item = document.createElement("div");
                    item.className = "popover-item w-full text-left cursor-pointer";
                    item.setAttribute("role", "option");
                    item.textContent = opt.name;

                    const isSelected = (selectedStateArr.length === 0 && opt.value === 'Aggregated') ||
                                     (selectedStateArr.length === 1 && selectedStateArr[0] === opt.value);

                    item.setAttribute("tabindex", "0");
                    if (isSelected) {
                        item.classList.add('active-popover-item');
                        item.setAttribute("aria-selected", "true");
                    } else {
                        item.setAttribute("aria-selected", "false");
                    }

                    const triggerSelect = (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        selectedStateArr.length = 0;
                        selectedStateArr.push(opt.value);
                        onSelectionChange();
                        container.classList.add('hidden');

                        let config;
                        if (typeof CoverageReportApp !== 'undefined') {
                            config = CoverageReportApp.getDropdownConfigs().find(c => c.dropdown === container || (c.dropdown && c.dropdown.contains(container)));
                        }
                        if (!config && typeof SourceViewApp !== 'undefined') {
                            if (SourceViewApp.elements.variantFiltersDropdown && (container === SourceViewApp.elements.variantFiltersDropdown || SourceViewApp.elements.variantFiltersDropdown.contains(container))) {
                                config = { btn: SourceViewApp.elements.variantFilterBtn };
                            }
                        }
                        if (config && config.btn) {
                            config.btn.setAttribute('aria-expanded', 'false');
                            config.btn.focus();
                        }
                    };

                    item.addEventListener("click", triggerSelect);
                    item.addEventListener("keydown", (e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            triggerSelect(e);
                        }
                    });
                }

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
        if (percentage === '--' || percentage === null || percentage === undefined) return 'text-gray-500';
        if (percentage >= 80) return 'text-green-600';
        if (percentage >= 60) return 'text-yellow-600';
        return 'text-red-600';
    }
};

const CoverageReportApp = {
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

    state: {
        viewMode: 'flat', // 'flat' or 'tree'
        currentView: 'modules', // 'modules', 'packages', 'classes'
        selectedModule: null,
        selectedPackage: null,
        filters: { modules: [], testSuite: 'Aggregated', packages: [], classes: [], variants: [], search: '' },
        density: 'comfy',
        sort: { by: 'name', order: 'asc' },
        columnWidths: {},
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
        this.initResizableColumns();
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
                const config = this.getDropdownConfigs().find(c => c.dropdown === dropdownToToggle);
                if (config && config.btn) {
                    config.btn.setAttribute('aria-expanded', 'true');
                }

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
                const config = this.getDropdownConfigs().find(c => c.dropdown === dropdownToToggle);
                if (config && config.btn) {
                    config.btn.setAttribute('aria-expanded', 'false');
                    config.btn.focus();
                }
            }
        }
    },

    announce(message) {
        const announcer = document.getElementById('a11y-announcer');
        if (announcer) {
            announcer.textContent = message;
        }
    },

    populateHeaderInfo() {
        if(this.fullReport.name) this.elements.headerTitle.textContent = this.fullReport.name;
        if(this.fullReport.timeStamp) this.elements.headerDate.textContent = this.fullReport.timeStamp;
    },

    populateGlobalStats() {
        this.elements.totalModules.textContent = this.fullReport.numberOfModules || 0;
        this.elements.totalPackages.textContent = this.fullReport.numberOfPackages || 0;
        this.elements.totalClasses.textContent = this.fullReport.numberOfClasses || 0;
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

        this.updateVariantDropdown();
        this.updateDynamicFilters();
    },

    resetSearchUI() {
        this.state.filters.search = '';
        if (this.elements.searchInput) {
            this.elements.searchInput.value = '';
        }
        if (this.elements.searchClearBtn) {
            this.elements.searchClearBtn.classList.add('hidden');
        }
    },

    applyFilters(newFilters) {
        const currentFilters = this.state.filters;
        currentFilters.modules.length = 0; currentFilters.modules.push(...newFilters.modules);
        currentFilters.packages.length = 0; currentFilters.packages.push(...newFilters.packages);
        currentFilters.classes.length = 0; currentFilters.classes.push(...newFilters.classes);
        currentFilters.variants.length = 0; currentFilters.variants.push(...newFilters.variants);
        currentFilters.testSuite = newFilters.testSuite;
    },

    setFilterChipsVisibility(visibility) {
        if (!visibility) return;
        const mapping = {
            module: this.elements.modChipContainer,
            package: this.elements.pkgChipContainer,
            class: this.elements.clsChipContainer
        };
        Object.entries(mapping).forEach(([key, element]) => {
            if (!element) return;
            if (visibility[key]) element.classList.remove('hidden');
            else element.classList.add('hidden');
        });
    },

    revealSearchUI() {
        if (!this.elements.searchWrapper || !this.elements.searchRevealBtn) return;
        this.elements.searchRevealBtn.classList.add('transparent');
        this.elements.searchWrapper.classList.add('expanded');
        if (this.elements.searchInput) {
            this.elements.searchInput.focus();
        }
    },

    collapseSearchUI(animated = true) {
        if (!this.elements.searchWrapper || !this.elements.searchRevealBtn) return;

        this.elements.searchWrapper.classList.remove('expanded');
        this.elements.searchRevealBtn.classList.remove('transparent');
        if (!animated) {
            this.elements.searchRevealBtn.classList.remove('hidden');
        }
    },

    updateVariantDropdown() {
        let allVariants = [];
        if (this.fullReport.variantCoverages && this.fullReport.variantCoverages.length > 0) {
            allVariants = this.fullReport.variantCoverages.map(v => v.name);
        }
        const variantOptions = allVariants.map(v => ({name: v, value: v}));

        UIUtils.buildActionDropdown(this.elements.variantFilterDropdown, variantOptions, this.state.filters.variants, () => {
            this.updateVariantButtonText();
            this.render(true);
            Navigation.push();
        }, false);
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

    searchTimeout: null,

    bindEvents() {
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                const openDropdownConf = this.getDropdownConfigs().find(c => c.dropdown && !c.dropdown.classList.contains('hidden'));
                if (openDropdownConf) {
                    this.toggleDropdown(openDropdownConf.dropdown);
                } else if (this.elements.searchWrapper && this.elements.searchWrapper.classList.contains('expanded')) {
                    this.collapseSearchUI(true);
                    this.elements.searchRevealBtn.focus();
                }
            }
        });

        this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
            if (btn && dropdown) {
                btn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.toggleDropdown(dropdown);
                });
                btn.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        e.stopPropagation();
                        this.toggleDropdown(dropdown);
                    } else if (e.key === 'ArrowDown') {
                        e.preventDefault();
                        if (dropdown.classList.contains('hidden')) {
                            this.toggleDropdown(dropdown);
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

        if (this.elements.groupByDropdown) {
            this.elements.groupByDropdown.addEventListener('click', this.handleGroupByChange.bind(this));
        }
        this.elements.coverageData.addEventListener('click', this.handleRowClick.bind(this));
        this.elements.coverageData.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                if (e.target.closest('.cursor-pointer, .class-link')) {
                    e.preventDefault();
                    e.target.closest('.cursor-pointer, .class-link').click();
                }
            }
        });
        this.elements.tableHeaders.addEventListener('click', this.handleHeaderClick.bind(this));
        this.elements.tableHeaders.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                if (e.target.closest('[data-sort-by]')) {
                    e.preventDefault();
                    e.target.closest('[data-sort-by]').click();
                }
            }
        });
        this.elements.flatBreadcrumbs.addEventListener('click', this.handleBreadcrumbClick.bind(this));
        this.elements.flatBreadcrumbs.addEventListener('keydown', (e) => {
            if (e.key === ' ') {
                const link = e.target.closest('.breadcrumb-link');
                if (link) {
                    e.preventDefault();
                    link.click();
                }
            }
        });

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
                this.render(true, true);
                Navigation.push();

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
            const handleClose = (e) => {
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
                    this.render(true, true);
                    Navigation.push();

                    // Return focus appropriately
                    const visibleChips = Array.from(document.querySelectorAll('.filter-chip')).filter(chip => {
                        const container = chip.closest('.relative');
                        return container && !container.classList.contains('hidden');
                    });

                    if (visibleChips.length > 0) {
                        // Focus the last visible chip's close button, or the chip itself
                        const lastChip = visibleChips[visibleChips.length - 1];
                        const lastCloseBtn = lastChip.querySelector('.chip-close');
                        if (lastCloseBtn) {
                            lastCloseBtn.focus();
                        } else {
                            lastChip.focus();
                        }
                    } else {
                        this.elements.addFilterBtn.focus();
                    }
                }
            };

            closeBtn.addEventListener('click', handleClose);
        });

        // Search Reveal Logic
        if (this.elements.searchRevealBtn) {
            this.elements.searchRevealBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.revealSearchUI();
            });
        }



        // View Segments (Flat/Tree)
        if (this.elements.viewSegments) {
            this.elements.viewSegments.addEventListener('click', (e) => {
                const btn = e.target.closest('.segment-btn');
                if (!btn) return;

                this.state.viewMode = btn.dataset.value;
                this.resetSelection();
                this.render(true, true);
                Navigation.push();
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

            if (this.searchTimeout) clearTimeout(this.searchTimeout);
            this.searchTimeout = setTimeout(() => {
                this.render(true, false);
                Navigation.replace();
            }, 150);
        });
        this.elements.searchClearBtn.addEventListener('click', () => {
            this.resetSearchUI();
            this.render(true, false);
            this.collapseSearchUI(true);
            Navigation.replace();
            if (this.elements.searchRevealBtn) {
                this.elements.searchRevealBtn.focus();
            }
        });

        // Density Segments (Comfy/Compact)
        if (this.elements.densitySegments) {
            this.elements.densitySegments.addEventListener('click', (e) => {
                const btn = e.target.closest('.segment-btn');
                if (!btn) return;

                this.state.density = btn.dataset.value;
                this.render(true, false);
                Navigation.replace();
            });
        }
    },

    initResizableColumns() {
        const headerRow = this.elements.tableHeaders;
        let activeResizer = null;
        let startX, startWidth, resizerId;
        let animationFrameId = null;

        const setColumnWidth = (id, width) => {
            document.documentElement.style.setProperty(`--col-width-${id.replace(/\./g, '-')}`, `${width}px`);
        };

        const onPointerMove = (e) => {
            if (!activeResizer) return;
            const diffX = e.pageX - startX;
            const newWidth = Math.max(50, startWidth + diffX);

            this.state.columnWidths[resizerId] = newWidth;

            if (animationFrameId) {
                cancelAnimationFrame(animationFrameId);
            }
            animationFrameId = requestAnimationFrame(() => {
                setColumnWidth(resizerId, newWidth);
            });
        };

        const onPointerUp = (e) => {
            if (activeResizer) {
                if (activeResizer.hasPointerCapture(e.pointerId)) {
                    activeResizer.releasePointerCapture(e.pointerId);
                }
                activeResizer.classList.remove('resizing');
                activeResizer.removeEventListener('pointermove', onPointerMove);
                activeResizer.removeEventListener('pointerup', onPointerUp);
                activeResizer.removeEventListener('pointercancel', onPointerUp);
                activeResizer = null;
                setTimeout(() => { this.isResizing = false; }, 0);
            }
        };

        headerRow.addEventListener('pointerdown', (e) => {
            if (e.target.classList.contains('resizer')) {
                activeResizer = e.target;
                resizerId = activeResizer.dataset.resizerId;
                const columnTh = activeResizer.closest('th');
                startX = e.pageX;
                startWidth = columnTh.getBoundingClientRect().width;

                activeResizer.setPointerCapture(e.pointerId);
                activeResizer.classList.add('resizing');
                this.isResizing = true;

                activeResizer.addEventListener('pointermove', onPointerMove);
                activeResizer.addEventListener('pointerup', onPointerUp);
                activeResizer.addEventListener('pointercancel', onPointerUp);

                e.preventDefault();
                e.stopPropagation();
            }
        });

        // Apply initial widths if any
        for (const [id, width] of Object.entries(this.state.columnWidths)) {
            setColumnWidth(id, width);
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
            html += `<span class="breadcrumb-separator" aria-hidden="true">/</span>`;
            if(selectedPackage) {
                html += `<a href="#" class="breadcrumb-link" data-action="go-to-packages">${this.escapeHTML(selectedModule)}</a>`;
            } else {
                html += `<span class="breadcrumb-current">${this.escapeHTML(selectedModule)}</span>`;
            }
        }
        // Package level
        if(selectedPackage) {
            html += `<span class="breadcrumb-separator" aria-hidden="true">/</span>`;
            html += `<span class="breadcrumb-current">${this.escapeHTML(selectedPackage)}</span>`;
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
                this.render(true);
                Navigation.push();
                setTimeout(() => {
                    if (this.elements.tableHeaders) {
                        const firstHeader = this.elements.tableHeaders.querySelector('[tabindex="0"]');
                        if (firstHeader) firstHeader.focus();
                    }
                }, 0);
                break;
            case 'go-to-packages':
                this.state.currentView = 'packages';
                this.state.selectedPackage = null;
                this.render(true);
                Navigation.push();
                setTimeout(() => {
                    if (this.elements.tableHeaders) {
                        const firstHeader = this.elements.tableHeaders.querySelector('[tabindex="0"]');
                        if (firstHeader) firstHeader.focus();
                    }
                }, 0);
                break;
        }
    },

    handleHeaderClick(e) {
        if (this.isResizing || e.target.classList.contains('resizer')) return;
        const target = e.target.closest('[data-sort-by]');

        if (!target) return;

        const newSortBy = target.dataset.sortBy;
        if (this.state.sort.by === newSortBy) {
            this.state.sort.order = this.state.sort.order === 'asc' ? 'desc' : 'asc';
        } else {
            this.state.sort.by = newSortBy;
            this.state.sort.order = 'asc';
        }
        this.render(true);
        Navigation.push();
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
            this.render(true);
            Navigation.push();
        });

        const testSuiteStateWrapper = [filters.testSuite];
        UIUtils.buildActionDropdown(this.elements.testSuiteFilterDropdown, testSuiteOptions, testSuiteStateWrapper, () => {
            filters.testSuite = testSuiteStateWrapper[0] || 'Aggregated';
            this.updateFilterButtons();
            this.render(true);
            Navigation.push();

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
            this.render(true);
            Navigation.push();
        });

        UIUtils.buildActionDropdown(this.elements.classFilterDropdown, classOptions, filters.classes, () => {
            this.handleHeaderFilterChange('class');
            this.updateFilterButtons();
            this.render(true);
            Navigation.push();
        });

    },

    closeDropdownOnClickOutside() {
        document.addEventListener('click', (event) => {
            if (!document.body.contains(event.target)) return;

            this.getDropdownConfigs().forEach(({ btn, dropdown }) => {
                if (btn && dropdown && !btn.contains(event.target) && !dropdown.contains(event.target)) {
                    dropdown.classList.add('hidden');
                    btn.setAttribute('aria-expanded', 'false');
                }
            });

            // Search Input Collapse
            if (this.elements.searchWrapper && this.elements.searchRevealBtn) {
                if (!this.elements.searchWrapper.contains(event.target) && !this.elements.searchRevealBtn.contains(event.target)) {
                    if (this.elements.searchInput && this.elements.searchInput.value === '') {
                        this.collapseSearchUI(true);
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
        if (this.elements.groupByBtn) {
            this.elements.groupByBtn.setAttribute('aria-expanded', 'false');
            this.elements.groupByBtn.focus();
        }
        this.resetSelection();
        this.render(true);
        Navigation.push();
    },

    handleRowClick(e) {
        if (this.state.viewMode === 'flat') {
            const td = e.target.closest('td[data-name]');
            if (td) {
                this.handleFlatRowClick(td);
            }
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
        this.render(true);
        Navigation.push();
    },

    handleTreeRowClick(target) {
        const row = target.closest('tr');
        const arrow = row?.querySelector('.collapsible-arrow:not(.invisible)');
        if (!arrow) return;

        const rowContainer = row.querySelector('.cursor-pointer');
        arrow.classList.toggle('open');
        const children = document.querySelectorAll(`[data-parent-id="${row.dataset.id}"]`);
        if (arrow.classList.contains('open')) {
            if (rowContainer) rowContainer.setAttribute('aria-expanded', 'true');
            children.forEach(child => child.classList.remove('hidden'));
        } else {
            if (rowContainer) rowContainer.setAttribute('aria-expanded', 'false');
            this.collapseDescendants(row);
        }
    },

    collapseDescendants(parentRow) {
        document.querySelectorAll(`[data-parent-id="${parentRow.dataset.id}"]`).forEach(child => {
            child.classList.add('hidden');
            const childArrow = child.querySelector('.collapsible-arrow.open');
            if (childArrow) {
                childArrow.classList.remove('open');
                const rowContainer = child.querySelector('.cursor-pointer');
                if (rowContainer) rowContainer.setAttribute('aria-expanded', 'false');
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

    render(skipReplaceState = false, rebuildDropdowns = true) {
        if (rebuildDropdowns) {
            this.updateDynamicFilters();
        }
        this.updateGroupByText();
        this.renderBreadcrumbs();
        this.updateSegmentsUI();
        let dataToRender = this.getFilteredData();
        dataToRender = this.getSortedData(dataToRender);

        this.renderTable(dataToRender);

        this.updateTooltipsForOverflow();

        this.announce(`Showing ${dataToRender.length} results.`);

        if (!skipReplaceState) {
            Navigation.replace();
        }
    },

    updateSegmentsUI() {
        if (this.elements.viewSegments) {
            this.elements.viewSegments.querySelectorAll('.segment-btn').forEach(b => {
                if (b.dataset.value === this.state.viewMode) {
                    b.classList.add('active');
                    b.setAttribute('aria-pressed', 'true');
                    b.removeAttribute('tabindex');
                } else {
                    b.classList.remove('active');
                    b.setAttribute('aria-pressed', 'false');
                    b.removeAttribute('tabindex');
                }
            });
        }
        if (this.elements.densitySegments) {
            this.elements.densitySegments.querySelectorAll('.segment-btn').forEach(b => {
                if (b.dataset.value === this.state.density) {
                    b.classList.add('active');
                    b.setAttribute('aria-pressed', 'true');
                    b.removeAttribute('tabindex');
                } else {
                    b.classList.remove('active');
                    b.setAttribute('aria-pressed', 'false');
                    b.removeAttribute('tabindex');
                }
            });
            if (this.state.density === 'compact') {
                this.elements.mainTable.classList.add('table-compact');
            } else {
                this.elements.mainTable.classList.remove('table-compact');
            }
        }
    },

    updateGroupByText() {
        if (!this.elements.groupByText) return;
        const viewMap = {
            'modules': 'Modules',
            'packages': 'Packages',
            'classes': 'Classes'
        };
        this.elements.groupByText.textContent = viewMap[this.state.currentView] || 'Modules';

        if (this.elements.groupByDropdown) {
            this.elements.groupByDropdown.querySelectorAll('.dropdown-item').forEach(item => {
                if (item.dataset.value === this.state.currentView) {
                    item.classList.add('active-popover-item');
                    item.setAttribute('aria-selected', 'true');
                } else {
                    item.classList.remove('active-popover-item');
                    item.setAttribute('aria-selected', 'false');
                }
            });
        }

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

    cachedEffectiveData: null,
    cachedTestSuite: null,

    getEffectiveHierarchicalData() {
        if (this.cachedEffectiveData && this.cachedTestSuite === this.state.filters.testSuite) {
            return this.cachedEffectiveData;
        }

        const { filters } = this.state;
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

        this.cachedEffectiveData = this.fullReport.modules.map(m => {
            const moduleWithCoverage = addEffectiveCoverage(m, 'module');
            moduleWithCoverage.packages = (m.packages || []).map(p => {
                const pkgWithCoverage = addEffectiveCoverage(p, 'package');
                pkgWithCoverage.classes = (p.classes || []).map(c => addEffectiveCoverage(c, 'class'));
                return pkgWithCoverage;
            });
            return moduleWithCoverage;
        });
        this.cachedTestSuite = filters.testSuite;
        return this.cachedEffectiveData;
    },

    getFilteredData() {
        const { viewMode, currentView, selectedModule, selectedPackage, filters } = this.state;

        let hierarchicalData = this.getEffectiveHierarchicalData();

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

        if (selectedModule) {
            const module = hierarchicalData.find(m => m.name === selectedModule);
            if (module) {
                if (selectedPackage) {
                    const pkg = module.packages.find(p => p.name === selectedPackage);
                    flatData = (pkg?.classes || []).map(c => ({ ...c, packageName: pkg.name, moduleName: module.name }));
                } else {
                    flatData = (module.packages || []).map(p => ({ ...p, moduleName: module.name }));
                }
            } else {
                flatData = [];
            }
        } else {
            if (currentView === 'packages') {
                flatData = hierarchicalData.flatMap(m =>
                    (m.packages || []).map(p => ({ ...p, moduleName: m.name }))
                );
            } else if (currentView === 'classes') {
                flatData = hierarchicalData.flatMap(m =>
                    (m.packages || []).flatMap(p =>
                        (p.classes || []).map(c => ({ ...c, packageName: p.name, moduleName: m.name }))
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

    getColumnStyle(key, defaultWidth) {
        const varName = `--col-width-${key.replace(/\./g, '-')}`;
        const widthVal = defaultWidth ? `${defaultWidth}px` : 'auto';
        const maxVal = defaultWidth ? `${defaultWidth}px` : 'none';
        return `style="width: var(${varName}, ${widthVal}); min-width: var(${varName}, ${widthVal}); max-width: var(${varName}, ${maxVal});"`;
    },

    getCoverageValues(item, variantName) {
        const found = item.variantCoverages ? item.variantCoverages.find(v => v.name === variantName) : null;

        if (found) {
            return {
                instrPercent: (found.instruction.percent === null || found.instruction.percent === undefined) ? '--' : found.instruction.percent + '%',
                instrRatio: `${found.instruction.covered}/${found.instruction.total}`,
                branchPercent: (found.branch.percent === null || found.branch.percent === undefined) ? '--' : found.branch.percent + '%',
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
        const firstColClass = "py-4 px-6 text-left font-semibold text-gray-700 sticky-name bg-gray-50 z-30 cursor-pointer";
        const sortIndicator = (key) => sort.by === key ? (sort.order === 'asc' ? '▲' : '▼') : '';
        const getAriaSort = (key) => sort.by === key ? (sort.order === 'asc' ? 'ascending' : 'descending') : 'none';

        topHeader.innerHTML = `<th class="${firstColClass}" tabindex="0" data-sort-by="name" aria-sort="${getAriaSort('name')}">${mainHeaderTitle} ${sortIndicator('name')}<div class="resizer" data-resizer-id="name"></div></th>`;
        subHeader.innerHTML = `<th class="py-2 px-6 sticky-name bg-gray-50 z-30" data-col-id="name"></th>`;

        if (viewMode === 'flat' && !this.state.selectedModule) {
            if (currentView === 'classes') {
                topHeader.innerHTML += `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30 col-path">Path<div class="resizer" data-resizer-id="path"></div></th>`;
                subHeader.innerHTML += `<th class="py-2 px-6 bg-gray-50 z-30 col-path" data-col-id="path"></th>`;
            } else if (currentView === 'packages') {
                topHeader.innerHTML += `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30 col-module">Module<div class="resizer" data-resizer-id="module"></div></th>`;
                subHeader.innerHTML += `<th class="py-2 px-6 bg-gray-50 z-30 col-module" data-col-id="module"></th>`;
            }
        }

        const variants = [...filters.variants].sort();

        variants.forEach(v => {
            const vals = this.getCoverageValues(this.fullReport, v);

            topHeader.innerHTML += `<th colspan="2" class="py-4 px-4 text-center font-semibold text-gray-700 border-l border-gray-200">
                <div class="flex flex-col"><span>${this.escapeHTML(v)}</span><span class="text-sm font-bold ${vals.instrColor} mt-1">${vals.instrPercent}</span></div>
            </th>`;


            const instrKey = `instruction.${v}.percent`;
            const branchKey = `branch.${v}.percent`;
            const instrStyle = this.getColumnStyle(instrKey);
            const branchStyle = this.getColumnStyle(branchKey);

            subHeader.innerHTML += `<th class="py-2 px-4 text-center text-xs font-medium text-gray-600 border-l border-gray-200 cursor-pointer" tabindex="0" data-sort-by="${this.escapeHTML(instrKey)}" aria-sort="${getAriaSort(instrKey)}" ${instrStyle}>Instruction ${sortIndicator(instrKey)}<div class="resizer" data-resizer-id="${this.escapeHTML(instrKey)}"></div></th>
                                    <th class="py-2 px-4 text-center text-xs font-medium text-gray-600 cursor-pointer" tabindex="0" data-sort-by="${this.escapeHTML(branchKey)}" aria-sort="${getAriaSort(branchKey)}" ${branchStyle}>Branch ${sortIndicator(branchKey)}<div class="resizer" data-resizer-id="${this.escapeHTML(branchKey)}"></div></th>`;
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
                nameContent = `<span class="font-medium text-blue-700 hover:underline">${this.escapeHTML(item.name)}</span>`;
            } else {
                nameContent = `<span class="font-medium text-gray-900">${this.escapeHTML(item.name)}</span>`;
            }

            const chevron = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="collapsible-arrow w-4 h-4 text-gray-600 ${!hasChildren ? 'invisible' : ''} ${isSearching ? 'open' : ''}"><path d="m9 18 6-6-6-6"></path></svg>`;
            const coverageCells = [...this.state.filters.variants].sort().map(v => {
                const vals = this.getCoverageValues(item, v);
                const instrKey = `instruction.${v}.percent`;
                const branchKey = `branch.${v}.percent`;
                return `
                <td class="py-3 px-4 text-center" ${this.getColumnStyle(instrKey)}><div class="flex flex-col"><span class="font-bold ${vals.instrColor}">${vals.instrPercent}</span><span class="text-xs text-gray-500">${vals.instrRatio}</span></div></td>
                <td class="py-3 px-4 text-center" ${this.getColumnStyle(branchKey)}><div class="flex flex-col"><span class="font-bold ${vals.branchColor}">${vals.branchPercent}</span><span class="text-xs text-gray-500">${vals.branchRatio}</span></div></td>
            `}).join('');

            const rowClasses = `table-row border-b border-gray-200 hover:bg-gray-50 ${level > 0 && !isSearching ? 'child-row hidden' : 'child-row'}`;
            const ariaExpanded = hasChildren ? `aria-expanded="${isSearching ? 'true' : 'false'}"` : '';
            const interactiveAttrs = hasChildren
                ? `tabindex="0" class="flex items-center gap-2 cursor-pointer pl-level-${level}"`
                : `tabindex="0" role="link" class="flex items-center gap-2 cursor-pointer pl-level-${level} class-link" data-class-name="${this.escapeHTML(item.name)}" data-module-name="${this.escapeHTML(context.moduleName)}" data-package-name="${this.escapeHTML(context.packageName)}" data-test-suite-name="${this.escapeHTML(context.testSuiteName || '')}"`;

            return `<tr class="${rowClasses}" data-id="${this.escapeHTML(item.name)}" data-parent-id="${this.escapeHTML(parentId)}">
                <td class="py-3 px-6 sticky-name" title="${this.escapeHTML(item.name)}"><div ${interactiveAttrs} ${ariaExpanded}>${chevron}${nameContent}</div></td>
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
                const instrKey = `instruction.${v}.percent`;
                const branchKey = `branch.${v}.percent`;
                return `
                <td class="py-3 px-4 text-center" ${this.getColumnStyle(instrKey)}><div class="flex flex-col"><span class="font-bold ${vals.instrColor}">${vals.instrPercent}</span><span class="text-xs text-gray-500">${vals.instrRatio}</span></div></td>
                <td class="py-3 px-4 text-center" ${this.getColumnStyle(branchKey)}><div class="flex flex-col"><span class="font-bold ${vals.branchColor}">${vals.branchPercent}</span><span class="text-xs text-gray-500">${vals.branchRatio}</span></div></td>
            `}).join('');

            let nameCell;
            switch (this.state.currentView) {
                case 'packages':
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" tabindex="0" title="${this.escapeHTML(item.name)}" data-name="${this.escapeHTML(item.name)}" data-type="${this.escapeHTML(item.type)}" data-module-name="${this.escapeHTML(item.moduleName)}">${this.escapeHTML(item.name)}</td>`;
                    if (!this.state.selectedModule) {
                        nameCell += `<td class="py-3 px-6 text-gray-500 text-sm truncate col-module" title="${this.escapeHTML(item.moduleName)}">${this.escapeHTML(item.moduleName)}</td>`;
                    }
                    break;
                case 'classes':
                    nameCell = `<td class="py-3 px-6 sticky-name cursor-pointer class-link" tabindex="0" role="link" title="${this.escapeHTML(item.name)}" data-class-name="${this.escapeHTML(item.name)}" data-module-name="${this.escapeHTML(item.moduleName)}" data-package-name="${this.escapeHTML(item.packageName)}"><span class="font-medium text-blue-700 hover:underline">${this.escapeHTML(item.name)}</span></td>`;
                    if (!this.state.selectedModule) {
                        nameCell += `<td class="px-2 col-path" title="${this.escapeHTML(item.moduleName)} > ${this.escapeHTML(item.packageName)}">
                            <div class="flex flex-col" style="overflow: hidden; width: 100%;">
                                <span class="text-xs text-gray-500 truncate-block">${this.escapeHTML(item.moduleName)}</span>
                                <span class="text-sm text-gray-500 truncate-block">${this.escapeHTML(item.packageName)}</span>
                            </div>
                        </td>`;
                    }
                    break;
                default: // modules
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" tabindex="0" title="${this.escapeHTML(item.name)}" data-name="${this.escapeHTML(item.name)}" data-type="${this.escapeHTML(item.type)}" data-module-name="${this.escapeHTML(item.name)}">${this.escapeHTML(item.name)}</td>`;
            }
            return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${nameCell}${coverageCells}</tr>`;
        }).join('');
    }
};

document.addEventListener('DOMContentLoaded', () => App.init());

/**
 * HELP HUB INITIALIZATION
 */
(function() {
    function initHelpHub() {
        const helpHubFab = document.getElementById("help-hub-fab");
        const helpHubPanel = document.getElementById("help-hub-panel");
        const closeHelpHubBtn = document.getElementById("close-help-hub");

        if (!helpHubFab || !helpHubPanel) return;

        function togglePanel(open) {
            const isOpening = open === undefined ? !helpHubPanel.classList.contains("open") : open;
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
            if (header) {
                const toggleItem = (e) => {
                    e.stopPropagation();
                    const isOpen = item.classList.contains("open");
                    legendItems.forEach(other => {
                        if (other !== item) {
                            other.classList.remove("open");
                            other.querySelector(".legend-item-header").setAttribute("aria-expanded", "false");
                        }
                    });
                    item.classList.toggle("open", !isOpen);
                    header.setAttribute("aria-expanded", !isOpen);
                };

                header.addEventListener("click", toggleItem);
                header.addEventListener("keydown", (e) => {
                    if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        toggleItem(e);
                    }
                });
            }
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initHelpHub);
    } else {
        initHelpHub();
    }
})();
