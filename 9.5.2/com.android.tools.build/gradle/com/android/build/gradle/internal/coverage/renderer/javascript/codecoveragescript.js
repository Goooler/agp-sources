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

const AGGREGATED_SUITE_NAME = 'Aggregated';

/**
 * Header Info Tooltips — Content
 * ------------------------------------------------------------------
 * All explanatory copy for header/label tooltips lives here so it can be
 * edited in one place. Each entry has a short `title` and a `body`.
 * Elements opt in with `data-info-key="<key>"`. Entries may be functions
 * when the copy depends on runtime context (e.g. the variant name), in which
 * case the value of `data-info-arg` is passed in.
 */
const HEADER_INFO = {
    // --- Top header stats ---
    'stat.modules': {
        title: 'Modules',
        body: 'Number of Gradle modules (e.g. :app, :core:data) included in this report. Each module is instrumented separately and rolled up into the project totals.',
    },
    'stat.packages': {
        title: 'Packages',
        body: 'Total Kotlin/Java packages across all modules that contain classes analysed for coverage.',
    },
    'stat.classes': {
        title: 'Classes',
        body: 'Total Kotlin/Java classes across all modules that contain executable code and were analysed for coverage. Interfaces and classes with no bytecode are not counted.',
    },

    // --- Header controls ---
    'header.testSuite': {
        title: 'Test Suite',
        body: 'Choose whose coverage you are looking at. “All” merges every suite; pick a single suite to see only the code that suite exercised.',
    },
    'header.groupBy': {
        title: 'Group by',
        body: 'Changes the granularity of the rows below — list the project by Modules, by Packages, or as a flat list of Classes.',
    },

    // --- Main table: name column (contextual) ---
    'col.project': {
        title: 'Project hierarchy',
        body: 'Expandable tree of Modules → Packages → Classes. Coverage shown on a parent row is the aggregate of everything underneath it.',
    },
    'col.modules': {
        title: 'Module',
        body: 'Gradle module name. Coverage is the aggregate of all packages and classes in the module. Click a module to drill into its packages.',
    },
    'col.packages': {
        title: 'Package',
        body: 'Kotlin/Java package. Coverage is the aggregate of all classes in the package. Click a package to drill into its classes.',
    },
    'col.classes': {
        title: 'Class',
        body: 'Class name. Click a class to open the annotated source view with line‑by‑line coverage.',
    },
    'col.path': {
        title: 'Path',
        body: 'Where this class lives: its Gradle module and package.',
    },
    'col.module': {
        title: 'Module',
        body: 'Gradle module that owns this package.',
    },

    // --- Main table: variant + metric columns ---
    'col.variant': (v) => ({
        title: `Build variant · ${v}`,
        body: 'A build variant is a product flavor combined with a build type (e.g. demo + debug). Coverage is measured per variant because each compiles a different set of sources and generated code. The percentage below the name is this variant\'s instruction coverage for the level you are viewing — the whole project, or the module or package you have navigated into.',
    }),
    'col.instruction': {
        title: 'Instruction coverage',
        body: 'Percentage of JVM bytecode instructions executed by the selected test suite. This is the most fine‑grained metric and is not affected by code formatting or line length. Click to sort.',
    },
    'col.branch': {
        title: 'Branch coverage',
        body: 'Percentage of decision branches (if / when / && / || / ?:) that tests actually took. 100 % means every condition was exercised in both its true and false outcome. Click to sort.',
    },

    // --- Source view ---
    'source.methods': {
        title: 'Methods',
        body: 'Methods declared in this class. Click one to jump to its first line in the source view.',
    },
    'source.variantHeader': (v) => ({
        title: `Build variant · ${v}`,
        body: 'Source and coverage for this file as compiled in this variant. When several variants are shown side by side, each column reflects that variant\'s own test run.',
    }),
    'source.fileStats': {
        title: 'File coverage',
        body: 'Instruction coverage for this file under the selected test suite, shown as a percentage and as covered / total.',
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
        // between an "All" and a "selected" layout).
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
 * Main application object to encapsulate state, elements, and logic.
 */
const App = {
    activeTrigger: null,

    init() {
        this.baseTitle = document.title;
        Tooltip.init();
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
                    App.activeTrigger = link;
                    const context = {
                        moduleName: moduleName,
                        packageName: packageName,
                        testSuiteName: CoverageReportApp.state.filters.testSuite,
                        variants: [...CoverageReportApp.state.filters.variants]
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
        CoverageReportApp.announce(`Showing source code for class: ${classData.name}`);
        document.title = `Source: ${classData.name} - ${this.baseTitle}`;
        document.getElementById('report-view').classList.add('hidden-view');
        document.getElementById('source-view').classList.remove('hidden-view');
        document.getElementById('report-view-controls').classList.add('hidden');
        document.getElementById('source-view-controls').classList.remove('hidden');

        SourceViewApp.loadAndRender(classData, context);
    },

    showReportView() {
        CoverageReportApp.announce("Returning to report view");
        document.title = this.baseTitle;
        document.getElementById('source-view').classList.add('hidden-view');
        document.getElementById('report-view').classList.remove('hidden-view');
        document.getElementById('source-view-controls').classList.add('hidden');
        document.getElementById('report-view-controls').classList.remove('hidden');

        CoverageReportApp.updateVariantButtonText();
        CoverageReportApp.updateVariantDropdown();

        if (App.activeTrigger) {
            App.activeTrigger.focus();
            App.activeTrigger = null;
        }
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

            // Restore search UI to match restored state after view is visible
            if (CoverageReportApp.elements.searchInput) {
                CoverageReportApp.elements.searchInput.value = state.reportState.filters.search || '';
                if (CoverageReportApp.elements.searchInput.value) {
                    CoverageReportApp.revealSearchUI();
                    CoverageReportApp.elements.searchClearBtn?.classList.remove('hidden');
                } else {
                    CoverageReportApp.collapseSearchUI(false);
                    CoverageReportApp.elements.searchClearBtn?.classList.add('hidden');
                }
            }

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
    formatSuiteName(name) {
        if (!name) return "";
        const str = String(name);
        return str.charAt(0).toLowerCase() + str.slice(1);
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
                Array.from(listZone.querySelectorAll("input[type='checkbox']")).forEach(cb => cb.checked = true);
                onSelectionChange([...currentState]);
            });

            clearBtn.addEventListener("click", (e) => {
                e.stopPropagation();
                e.preventDefault();
                currentState = [];
                Array.from(listZone.querySelectorAll("input[type='checkbox']")).forEach(cb => cb.checked = false);
                onSelectionChange([]);
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

                let item;

                if (multiSelect) {
                    const isChecked = currentState.includes(opt.value);
                    item = document.createElement("label");
                    item.className = "popover-item";

                    const checkbox = document.createElement("input");
                    checkbox.type = "checkbox";
                    checkbox.className = "popover-checkbox";
                    checkbox.checked = isChecked;
                    item.appendChild(checkbox);

                    const handleChange = () => {
                        if (checkbox.checked) {
                            if (!currentState.includes(opt.value)) currentState.push(opt.value);
                        } else {
                            const idx = currentState.indexOf(opt.value);
                            if (idx > -1) currentState.splice(idx, 1);
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

                    const label = document.createElement("span");
                    label.textContent = opt.name;
                    item.appendChild(label);
                } else {
                    item = document.createElement("div");
                    item.className = "popover-item w-full text-left cursor-pointer";
                    item.setAttribute("role", "option");
                    const itemText = document.createElement("span");
                    itemText.textContent = opt.name;
                    item.appendChild(itemText);

                    const isSelected = currentState === opt.value;

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
                        onSelectionChange(opt.value);
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

    getActualTestSuites() {
        if (!this.fullReport) return [];
        let suites = [];
        if (this.fullReport.testSuiteCoverages && this.fullReport.testSuiteCoverages.length > 0) {
            suites = this.fullReport.testSuiteCoverages.map(ts => ts.name);
        } else if (this.fullReport.modules) {
            suites = [...new Set(this.fullReport.modules.flatMap(m => (m.testSuiteCoverages || []).map(ts => ts.name)))];
        }
        return suites.filter(name => name !== AGGREGATED_SUITE_NAME);
    },

    getDefaultTestSuite() {
        if (!this.fullReport) return AGGREGATED_SUITE_NAME;
        const actualSuites = this.getActualTestSuites();
        if (actualSuites.length === 1) {
            return actualSuites[0];
        }
        const testSuites = this.fullReport.testSuiteCoverages || [];
        const hasAggregated = testSuites.some(ts => ts.name === AGGREGATED_SUITE_NAME);
        return (hasAggregated || testSuites.length === 0) ? AGGREGATED_SUITE_NAME : testSuites[0].name;
    },

    state: {
        viewMode: 'flat', // 'flat' or 'tree'
        currentView: 'modules', // 'modules', 'packages', 'classes'
        selectedModule: null,
        selectedPackage: null,
        filters: { modules: [], testSuite: AGGREGATED_SUITE_NAME, packages: [], classes: [], variants: [], search: '' },
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
        this.state.filters.testSuite = this.getDefaultTestSuite();
        this.cacheDOMElements();
        this.populateHeaderInfo();
        this.populateGlobalStats();
        this.populateFilters();
        // Apply header filter button state (incl. test suite selection) on first load,
        // otherwise the button keeps the static markup until a filter event fires.
        this.updateFilterButtons();
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
        if (dropdownToToggle) {
            const config = this.getDropdownConfigs().find(c => c.dropdown === dropdownToToggle);
            if (config && config.btn && (config.btn.disabled || config.btn.classList.contains('unclickable') || config.btn.getAttribute('aria-disabled') === 'true')) {
                return;
            }
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
            announcer.textContent = '';
            if (this.announceTimeout) clearTimeout(this.announceTimeout);
            this.announceTimeout = setTimeout(() => {
                announcer.textContent = message;
            }, 50);
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

    getAggregatedVariants() {
        if (!this.fullReport.testSuiteCoverages) return [];
        const testSuites = this.fullReport.testSuiteCoverages;
        const agg = testSuites.find(ts => ts.name === AGGREGATED_SUITE_NAME) || testSuites[0];
        return agg ? agg.variantCoverages : [];
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
        const aggVariants = this.getAggregatedVariants();
        if (aggVariants.length > 0) {
            allVariants = aggVariants.map(v => v.name);
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
        if (!newFilters) return;
        const currentFilters = this.state.filters;
        if (newFilters.modules) {
            currentFilters.modules.length = 0;
            currentFilters.modules.push(...newFilters.modules);
        }
        if (newFilters.packages) {
            currentFilters.packages.length = 0;
            currentFilters.packages.push(...newFilters.packages);
        }
        if (newFilters.classes) {
            currentFilters.classes.length = 0;
            currentFilters.classes.push(...newFilters.classes);
        }
        if (newFilters.variants) {
            currentFilters.variants.length = 0;
            currentFilters.variants.push(...newFilters.variants);
        }
        const actualSuites = this.getActualTestSuites();
        if (actualSuites.length === 1) {
            currentFilters.testSuite = actualSuites[0];
        } else if (newFilters.testSuite !== undefined) {
            currentFilters.testSuite = newFilters.testSuite;
        }
        currentFilters.search = newFilters.search || '';
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
        const aggVariants = this.getAggregatedVariants();
        if (aggVariants.length > 0) {
            allVariants = aggVariants.map(v => v.name);
        }
        const variantOptions = allVariants.map(v => ({name: v, value: v}));

        UIUtils.buildActionDropdown(this.elements.variantFilterDropdown, variantOptions, this.state.filters.variants, (newArr) => {
            this.state.filters.variants = newArr;
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
                if (Tooltip.activeTarget) {
                    Tooltip.hide();
                    return;
                }
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
                    if (btn.disabled || btn.classList.contains('unclickable') || btn.getAttribute('aria-disabled') === 'true') {
                        return;
                    }
                    e.stopPropagation();
                    this.toggleDropdown(dropdown);
                });
                btn.addEventListener('keydown', (e) => {
                    if (btn.disabled || btn.classList.contains('unclickable') || btn.getAttribute('aria-disabled') === 'true') {
                        return;
                    }
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
            if (e.target.classList.contains('resizer')) return;
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

        const setColumnWidth = (id, width) => {
            document.documentElement.style.setProperty(`--col-width-${id.replace(/\./g, '-')}`, `${width}px`);
            const resizer = this.elements.tableHeaders.querySelector(`.resizer[data-resizer-id="${id}"]`);
            if (resizer) resizer.setAttribute('aria-valuenow', Math.round(width));
        };

        headerRow.addEventListener('pointerdown', (e) => {
            if (e.target.classList.contains('resizer')) {
                if (this.isResizing) return;

                const resizer = e.target;
                const resizerId = resizer.dataset.resizerId;
                const columnTh = resizer.closest('th');
                const startX = e.pageX;
                const startWidth = columnTh.getBoundingClientRect().width;
                let animationFrameId = null;

                resizer.setPointerCapture(e.pointerId);
                resizer.classList.add('resizing');
                this.isResizing = true;

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
                    setTimeout(() => { this.isResizing = false; }, 0);
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

        const actualSuites = this.getActualTestSuites();
        const isSingleSuite = actualSuites.length === 1;

        if (isSingleSuite) {
            filters.testSuite = actualSuites[0];
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
            if (filters.testSuite === AGGREGATED_SUITE_NAME) {
                if (this.elements.tsAllState) this.elements.tsAllState.classList.remove('hidden');
                if (this.elements.tsSelectedState) this.elements.tsSelectedState.classList.add('hidden');
            } else {
                if (this.elements.tsAllState) this.elements.tsAllState.classList.add('hidden');
                if (this.elements.tsSelectedState) this.elements.tsSelectedState.classList.remove('hidden');
                if (this.elements.testSuiteFilterText) {
                    this.elements.testSuiteFilterText.textContent = UIUtils.formatSuiteName(filters.testSuite);
                }
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

    updateVariantButtonText() {
        const selectedCount = this.state.filters.variants.length;
        let allVariantsCount = 0;
        const aggVariants = this.getAggregatedVariants();
        if (aggVariants.length > 0) {
            allVariantsCount = aggVariants.length;
        }

        if (this.elements.variantFilterBtn) {
            let label = 'Filter by Variant';
            if (selectedCount === allVariantsCount && allVariantsCount > 0) {
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

    renderBreadcrumbs() {
        if(this.state.viewMode !== 'flat') {
            this.elements.flatBreadcrumbs.innerHTML = '';
            return;
        }
        const { selectedModule, selectedPackage } = this.state; let html = '';
        // "Project" is the root link
        if(selectedModule) {
            html += `<a href="#" class="breadcrumb-link" data-action="go-to-modules" aria-label="Go back to Project Overview">Project</a>`;
        } else {
            html += `<span class="breadcrumb-current">Project</span>`;
        }
        // Module level
        if(selectedModule) {
            html += `<span class="breadcrumb-separator" aria-hidden="true">/</span>`;
            if(selectedPackage) {
                html += `<a href="#" class="breadcrumb-link" data-action="go-to-packages" aria-label="Go back to module: ${this.escapeHTML(selectedModule)}">${this.escapeHTML(selectedModule)}</a>`;
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

        const headerName = target.textContent.replace(/[▲▼]/g, '').trim();
        const orderText = this.state.sort.order === 'asc' ? 'ascending' : 'descending';
        this.announce(`Sorted by ${headerName}, ${orderText}`);

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

        const actualSuites = this.getActualTestSuites();
        const isSingleSuite = actualSuites.length === 1;

        if (isSingleSuite) {
            filters.testSuite = actualSuites[0];
            if (this.elements.testSuiteFilterDropdown) {
                this.elements.testSuiteFilterDropdown.innerHTML = '';
            }
        } else {
            const testSuiteOptions = [...new Set(contextModules.flatMap(m => (m.testSuiteCoverages || []).map(ts => ts.name)))]
                    .sort((a, b) => UIUtils.formatSuiteName(a).localeCompare(UIUtils.formatSuiteName(b)))
                    .map(name => ({ name: name === AGGREGATED_SUITE_NAME ? 'all' : UIUtils.formatSuiteName(name), value: name }));
            const aggIndex = testSuiteOptions.findIndex(o => o.value === AGGREGATED_SUITE_NAME);
            if (aggIndex > -1) {
                testSuiteOptions.unshift(testSuiteOptions.splice(aggIndex, 1)[0]);
            }

            UIUtils.buildActionDropdown(this.elements.testSuiteFilterDropdown, testSuiteOptions, filters.testSuite, (newVal) => {
                filters.testSuite = newVal || this.getDefaultTestSuite();
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
        }

        const packageOptions = [...new Set(basePackages.map(p => p.name))]
                .sort()
                .map(name => ({ name, value: name }));

        const classOptions = [...new Set(filteredPackages.flatMap(p => p.classes || []).map(c => c.name))]
                .sort()
                .map(name => ({ name, value: name }));

        UIUtils.buildActionDropdown(this.elements.moduleFilterDropdown, moduleOptions, filters.modules, (newArr) => {
            filters.modules = newArr;
            filters.packages = [];
            filters.classes = [];
            this.handleHeaderFilterChange('module');
            this.updateFilterButtons();
            this.render(true);
            Navigation.push();
        });

        UIUtils.buildActionDropdown(this.elements.packageFilterDropdown, packageOptions, filters.packages, (newArr) => {
            filters.packages = newArr;
            filters.classes = [];
            this.handleHeaderFilterChange('package');
            this.updateFilterButtons();
            this.render(true);
            Navigation.push();
        });

        UIUtils.buildActionDropdown(this.elements.classFilterDropdown, classOptions, filters.classes, (newArr) => {
            filters.classes = newArr;
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

    getEffectiveRoot() {
        const { filters } = this.state;
        const suiteName = filters.testSuite || this.getDefaultTestSuite();
        const suite = this.fullReport.testSuiteCoverages ? this.fullReport.testSuiteCoverages.find(ts => ts.name === suiteName) : null;
        return {
            ...this.fullReport,
            variantCoverages: suite ? suite.variantCoverages : []
        };
    },

    /**
     * Returns the node whose coverage the variant headers should summarise:
     * the package or module the user has drilled into (flat view), otherwise
     * the whole project. Coverage is resolved for the selected test suite.
     *
     * Search and chip filters are intentionally not applied, so the header
     * always shows the real total for the navigated scope (consistent with the
     * project-level header).
     */
    getHeaderScopeRoot() {
        const { viewMode, selectedModule, selectedPackage, filters } = this.state;
        if (viewMode !== 'flat' || !selectedModule) return this.getEffectiveRoot();

        const module = (this.fullReport.modules || []).find(m => m.name === selectedModule);
        if (!module) return this.getEffectiveRoot();

        let node = module;
        if (selectedPackage) {
            const pkg = (module.packages || []).find(p => p.name === selectedPackage);
            if (pkg) node = pkg;
        }

        const suiteName = filters.testSuite || this.getDefaultTestSuite();
        const suite = (node.testSuiteCoverages || []).find(ts => ts.name === suiteName);
        return {
            ...node,
            variantCoverages: suite ? suite.variantCoverages : []
        };
    },

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

        const nameWidth = Math.round(this.state.columnWidths['name'] || 400);
        // Contextual info tooltip for the name column
        const nameInfoKey = viewMode === 'tree'
            ? 'col.project'
            : ({ modules: 'col.modules', packages: 'col.packages', classes: 'col.classes' }[currentView] || 'col.project');
        topHeader.innerHTML = `<th scope="col" class="${firstColClass}" tabindex="0" data-sort-by="name" aria-sort="${getAriaSort('name')}"><span data-info-key="${nameInfoKey}">${mainHeaderTitle}</span> ${sortIndicator('name')}<div class="resizer" data-resizer-id="name" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${nameWidth}"></div></th>`;
        subHeader.innerHTML = `<th scope="col" class="py-2 px-6 sticky-name bg-gray-50 z-30" data-col-id="name"></th>`;

        if (viewMode === 'flat' && !this.state.selectedModule) {
            if (currentView === 'classes') {
                const pathWidth = Math.round(this.state.columnWidths['path'] || 300);
                topHeader.innerHTML += `<th scope="col" class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30 col-path"><span data-info-key="col.path">Path</span><div class="resizer" data-resizer-id="path" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${pathWidth}"></div></th>`;
                subHeader.innerHTML += `<th scope="col" class="py-2 px-6 bg-gray-50 z-30 col-path" data-col-id="path"></th>`;
            } else if (currentView === 'packages') {
                const moduleWidth = Math.round(this.state.columnWidths['module'] || 200);
                topHeader.innerHTML += `<th scope="col" class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50 z-30 col-module"><span data-info-key="col.module">Module</span><div class="resizer" data-resizer-id="module" tabindex="0" role="separator" aria-label="Resize column" aria-orientation="vertical" aria-valuemin="50" aria-valuemax="1000" aria-valuenow="${moduleWidth}"></div></th>`;
                subHeader.innerHTML += `<th scope="col" class="py-2 px-6 bg-gray-50 z-30 col-module" data-col-id="module"></th>`;
            }
        }

        const variants = [...filters.variants].sort();
        const root = this.getHeaderScopeRoot();

        variants.forEach(v => {
            const vals = this.getCoverageValues(root, v);
            const safeV = this.escapeHTML(v);

            topHeader.innerHTML += `<th scope="col" colspan="2" class="py-4 px-4 text-center font-semibold text-gray-700 border-l border-gray-200">
                <div class="flex flex-col"><span data-info-key="col.variant" data-info-arg="${safeV}">${safeV}</span><span class="text-sm font-bold ${vals.instrColor} mt-1">${vals.instrPercent}</span></div>
            </th>`;


            const instrKey = `instruction.${v}.percent`;
            const branchKey = `branch.${v}.percent`;
            const instrStyle = this.getColumnStyle(instrKey);
            const branchStyle = this.getColumnStyle(branchKey);

            subHeader.innerHTML += `<th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600 border-l border-gray-200 cursor-pointer" tabindex="0" data-sort-by="${this.escapeHTML(instrKey)}" aria-sort="${getAriaSort(instrKey)}" aria-label="Sort by Instruction Coverage for ${safeV}" ${instrStyle}><span data-info-key="col.instruction">Instruction</span> ${sortIndicator(instrKey)}</th>
                                    <th scope="col" class="py-2 px-4 text-center text-xs font-medium text-gray-600 cursor-pointer" tabindex="0" data-sort-by="${this.escapeHTML(branchKey)}" aria-sort="${getAriaSort(branchKey)}" aria-label="Sort by Branch Coverage for ${safeV}" ${branchStyle}><span data-info-key="col.branch">Branch</span> ${sortIndicator(branchKey)}</th>`;
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
                <td class="py-3 px-4 text-center" ${this.getColumnStyle(instrKey)} aria-label="${vals.instrPercent} instruction coverage (${vals.instrRatio}) for ${this.escapeHTML(v)}"><div class="flex flex-col"><span class="font-bold ${vals.instrColor}">${vals.instrPercent}</span><span class="text-xs text-gray-500">${vals.instrRatio}</span></div></td>
                <td class="py-3 px-4 text-center" ${this.getColumnStyle(branchKey)} aria-label="${vals.branchPercent} branch coverage (${vals.branchRatio}) for ${this.escapeHTML(v)}"><div class="flex flex-col"><span class="font-bold ${vals.branchColor}">${vals.branchPercent}</span><span class="text-xs text-gray-500">${vals.branchRatio}</span></div></td>
            `}).join('');

            const rowClasses = `table-row border-b border-gray-200 hover:bg-gray-50 ${level > 0 && !isSearching ? 'child-row hidden' : 'child-row'}`;
            const ariaExpanded = hasChildren ? `aria-expanded="${isSearching ? 'true' : 'false'}"` : '';
            const interactiveAttrs = hasChildren
                ? `tabindex="0" role="button" class="flex items-center gap-2 cursor-pointer pl-level-${level}"`
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
                <td class="py-3 px-4 text-center" ${this.getColumnStyle(instrKey)} aria-label="${vals.instrPercent} instruction coverage (${vals.instrRatio}) for ${this.escapeHTML(v)}"><div class="flex flex-col"><span class="font-bold ${vals.instrColor}">${vals.instrPercent}</span><span class="text-xs text-gray-500">${vals.instrRatio}</span></div></td>
                <td class="py-3 px-4 text-center" ${this.getColumnStyle(branchKey)} aria-label="${vals.branchPercent} branch coverage (${vals.branchRatio}) for ${this.escapeHTML(v)}"><div class="flex flex-col"><span class="font-bold ${vals.branchColor}">${vals.branchPercent}</span><span class="text-xs text-gray-500">${vals.branchRatio}</span></div></td>
            `}).join('');

            let nameCell;
            switch (this.state.currentView) {
                case 'packages':
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" tabindex="0" role="link" aria-label="View classes for package: ${this.escapeHTML(item.name)}" title="${this.escapeHTML(item.name)}" data-name="${this.escapeHTML(item.name)}" data-type="${this.escapeHTML(item.type)}" data-module-name="${this.escapeHTML(item.moduleName)}">${this.escapeHTML(item.name)}</td>`;
                    if (!this.state.selectedModule) {
                        nameCell += `<td class="py-3 px-6 text-gray-500 text-sm truncate col-module" title="${this.escapeHTML(item.moduleName)}">${this.escapeHTML(item.moduleName)}</td>`;
                    }
                    break;
                case 'classes':
                    nameCell = `<td class="py-3 px-6 sticky-name cursor-pointer class-link" tabindex="0" role="link" aria-label="View coverage details for class: ${this.escapeHTML(item.name)}" title="${this.escapeHTML(item.name)}" data-class-name="${this.escapeHTML(item.name)}" data-module-name="${this.escapeHTML(item.moduleName)}" data-package-name="${this.escapeHTML(item.packageName)}"><span class="font-medium text-blue-700 hover:underline">${this.escapeHTML(item.name)}</span></td>`;
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
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" tabindex="0" role="link" aria-label="View packages for module: ${this.escapeHTML(item.name)}" title="${this.escapeHTML(item.name)}" data-name="${this.escapeHTML(item.name)}" data-type="${this.escapeHTML(item.type)}" data-module-name="${this.escapeHTML(item.name)}">${this.escapeHTML(item.name)}</td>`;
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
