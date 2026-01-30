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
                const testSuiteName = link.dataset.testSuiteName;

                const classObj = this.findClassObject(moduleName, packageName, className, testSuiteName);

                if (classObj) {
                    this.showSourceView(classObj, { moduleName, testSuiteName });
                } else {
                    console.error("Class data not found for source view.");
                }
            }
        });
    },

    findClassObject(moduleName, packageName, className, testSuiteName) {
        if (typeof fullReport === 'undefined') return null;

        const module = fullReport.modules.find(m => m.name === moduleName);
        if (!module) return null;

        let packages = module.packages || [];
        if (testSuiteName) {
            const suite = (module.testSuites || []).find(ts => ts.name === testSuiteName);
            if (suite) packages = suite.packages || [];
        }

        const pkg = packages.find(p => p.name === packageName);
        if (!pkg) return null;

        return (pkg.classes || []).find(c => c.name === className);
    },

    showSourceView(classData, context = {}) {
        document.getElementById('report-view').style.display = 'none';
        document.getElementById('source-view').style.display = 'block';

        SourceViewApp.loadAndRender(classData, context);
    },

    showReportView() {
        document.getElementById('source-view').style.display = 'none';
        document.getElementById('report-view').style.display = 'block';
    }
}

const CoverageReportApp = {
    state: {
        viewMode: 'flat', // 'flat' or 'tree'
        currentView: 'modules', // 'modules', 'packages', 'classes'
        currentHierarchy: 'source', // 'source' or 'tests'
        selectedModule: null,
        selectedTestSuite: null,
        selectedPackage: null,
        filters: { module: 'all', variants: [], search: '' },
        sort: { by: 'name', order: 'asc' },
        searchableList: [],
    },
    elements: {},
    fullReport: null,

    init(fullReport) {
        this.fullReport = fullReport;
        this.cacheDOMElements();
        this.populateHeaderInfo();
        this.populateGlobalStats();
        this.populateFilters();
        this.bindEvents();
        this.setupSearchData();
        this.updateViewToggles();
        this.render();
        this.closeDropdownOnClickOutside();
    },

    cacheDOMElements() {
        this.elements = {
            headerTitle: document.querySelector('.header-title'),
            headerDate: document.querySelector('.header-date'),
            hierarchyFilterBtn: document.getElementById('hierarchy-filter-btn'),
            hierarchyFilterText: document.getElementById('hierarchy-filter-text'),
            hierarchyFilterDropdown: document.getElementById('hierarchy-filter-dropdown'),
            hierarchyFilterList: document.getElementById('hierarchy-filter-list'),
            moduleFilterBtn: document.getElementById('module-filter-btn'),
            moduleFilterText: document.getElementById('module-filter-text'),
            moduleFilterDropdown: document.getElementById('module-filter-dropdown'),
            moduleFilterList: document.getElementById('module-filter-list'),
            variantFilterBtn: document.getElementById('variant-filter-btn'),
            variantFilterText: document.getElementById('variant-filter-text'),
            variantFilterDropdown: document.getElementById('variant-filter-dropdown'),
            variantFilterList: document.getElementById('variant-filter-list'),
            viewModeBtn: document.getElementById('view-mode-btn'),
            viewModeText: document.getElementById('view-mode-text'),
            viewModeDropdown: document.getElementById('view-mode-dropdown'),
            viewModeList: document.getElementById('view-mode-list'),
            searchInput: document.getElementById('search-input'),
            viewToggles: document.getElementById('view-toggles'),
            flatViewControls: document.getElementById('flat-view-controls'),
            tableHeaders: document.getElementById('table-headers'),
            coverageData: document.getElementById('coverage-data'),
            totalModules: document.getElementById('total-modules'),
            totalClasses: document.getElementById('total-classes'),
            totalTests: document.getElementById('total-tests'),
        };
    },

    populateHeaderInfo() {
        if(this.fullReport.name) this.elements.headerTitle.textContent = this.fullReport.name;
        if(this.fullReport.timeStamp) this.elements.headerDate.textContent = this.fullReport.timeStamp;
    },

    populateGlobalStats() {
        if (this.elements.totalTests) {
            this.elements.totalTests.textContent = this.fullReport.numberOfTestsSuites || 0;
        }
    },

    populateFilters() {
        // Hierarchy
        const hierarchyOptions = [
            {name: 'Packages', value: 'source'},
            {name: 'Test Suites', value: 'tests'},
        ];
        this.elements.hierarchyFilterList.innerHTML = hierarchyOptions.map(opt =>
            `<a href="#" data-value="${opt.value}" class="dropdown-item">${opt.name}</a>`
        ).join('');

        // Modules
        const moduleOptions = [{name: 'All', value: 'all'}, ...this.fullReport.modules.map(m => ({name: m.name, value: m.name}))];
        this.elements.moduleFilterList.innerHTML = moduleOptions.map(opt =>
            `<a href="#" data-value="${opt.value}" class="dropdown-item">${opt.name}</a>`
        ).join('');

        // Variants
        let allVariants = [];
        if (this.fullReport.variantCoverages && this.fullReport.variantCoverages.length > 0) {
            allVariants = this.fullReport.variantCoverages.map(v => v.name);
        }

        this.state.filters.variants = [...allVariants];

        const variantOptions = [{name: 'All', value: 'all'}, ...allVariants.map(v => ({name: v, value: v}))];

        this.elements.variantFilterList.innerHTML = variantOptions.map(opt => `
            <label class="flex items-center gap-2 cursor-pointer px-2 py-1.5 hover:bg-gray-100 rounded">
                <input type="checkbox" class="variant-toggle w-4 h-4 text-blue-600 rounded focus:ring-2 focus:ring-blue-500" data-variant="${opt.value}" ${this.state.filters.variants.includes(opt.value) || (this.state.filters.variants.length === allVariants.length && opt.value === 'all') ? 'checked' : ''} />
                <span class="text-sm text-gray-700">${opt.name}</span>
            </label>
        `).join('');

        // View Mode
        const viewOptions = [
            {name: 'Flat', value: 'flat'},
            {name: 'Tree', value: 'tree'},
        ];
        this.elements.viewModeList.innerHTML = viewOptions.map(opt =>
            `<a href="#" data-value="${opt.value}" class="dropdown-item">${opt.name}</a>`
        ).join('');
    },

    bindEvents() {
        this.elements.searchInput.addEventListener('input', () => {
            this.state.filters.search = this.elements.searchInput.value.trim().toLowerCase();
            this.render();
        });
        this.elements.flatViewControls.addEventListener('click', this.handleViewToggle.bind(this));
        this.elements.coverageData.addEventListener('click', this.handleRowClick.bind(this));
        this.elements.tableHeaders.addEventListener('click', this.handleHeaderClick.bind(this));

        this.elements.hierarchyFilterBtn.addEventListener('click', () => this.elements.hierarchyFilterDropdown.classList.toggle('hidden'));
        this.elements.moduleFilterBtn.addEventListener('click', () => this.elements.moduleFilterDropdown.classList.toggle('hidden'));
        this.elements.variantFilterBtn.addEventListener('click', () => this.elements.variantFilterDropdown.classList.toggle('hidden'));
        this.elements.viewModeBtn.addEventListener('click', () => this.elements.viewModeDropdown.classList.toggle('hidden'));

        this.elements.hierarchyFilterList.addEventListener('click', this.handleHierarchyChange.bind(this));
        this.elements.moduleFilterList.addEventListener('click', this.handleFilterSelection.bind(this, 'module'));
        this.elements.variantFilterList.addEventListener('change', this.handleVariantSelection.bind(this));
        this.elements.viewModeList.addEventListener('click', this.handleViewModeChange.bind(this));
    },

    handleVariantSelection(e) {
        const checkbox = e.target;
        const variant = checkbox.dataset.variant;
        let allVariants = [];
         if (this.fullReport.variantCoverages && this.fullReport.variantCoverages.length > 0) {
            allVariants = this.fullReport.variantCoverages.map(v => v.name);
        }

        if (variant === 'all') {
            this.state.filters.variants = checkbox.checked ? allVariants : [];
            this.elements.variantFilterList.querySelectorAll('.variant-toggle').forEach(cb => {
                cb.checked = checkbox.checked;
            });
        } else {
            if (checkbox.checked) {
                this.state.filters.variants.push(variant);
            } else {
                this.state.filters.variants = this.state.filters.variants.filter(v => v !== variant);
            }

            const allCheckbox = this.elements.variantFilterList.querySelector('[data-variant="all"]');
            if(allCheckbox) allCheckbox.checked = this.state.filters.variants.length === allVariants.length;
        }

        this.updateVariantButtonText();
        this.render();
    },

    updateVariantButtonText() {
        const selectedCount = this.state.filters.variants.length;
        let allVariantsCount = 0;
         if (this.fullReport.variantCoverages && this.fullReport.variantCoverages.length > 0) {
            allVariantsCount = this.fullReport.variantCoverages.length;
        }

        if (selectedCount === allVariantsCount && allVariantsCount > 0) {
            this.elements.variantFilterText.textContent = 'All';
        } else if (selectedCount === 1) {
            this.elements.variantFilterText.textContent = this.state.filters.variants[0];
        } else {
            this.elements.variantFilterText.textContent = `${selectedCount} Variants`;
        }
    },

    handleHierarchyChange(e) {
        e.preventDefault();
        const target = e.target.closest('a');
        if(!target) return;

        this.state.currentHierarchy = target.dataset.value;
        this.elements.hierarchyFilterText.textContent = target.textContent;
        this.elements.hierarchyFilterDropdown.classList.add('hidden');
        this.resetSelection();
        if (this.state.viewMode === 'flat') {
            this.state.currentView = 'modules';
        }
        this.updateViewToggles();
        this.setupSearchData();
        this.render();
    },

    updateViewToggles() {
        const { currentHierarchy, viewMode } = this.state;
        const testSuitesButton = this.elements.flatViewControls.querySelector('[data-view="testSuites"]');

        if (currentHierarchy === 'tests' && viewMode === 'flat') {
            testSuitesButton.style.display = 'inline-block';
        } else {
            testSuitesButton.style.display = 'none';
            if (this.state.currentView === 'testSuites') {
                this.state.currentView = 'packages'; // Fallback view
            }
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
        e.preventDefault();
        const target = e.target.closest('a');
        if(!target) return;

        const { value } = target.dataset;
        this.state.filters[filterType] = value;
        this.elements[`${filterType}FilterText`].textContent = target.textContent;
        this.elements[`${filterType}FilterDropdown`].classList.add('hidden');
        this.render();
    },

    closeDropdownOnClickOutside() {
        document.addEventListener('click', (event) => {
            if (!this.elements.hierarchyFilterBtn.contains(event.target) && !this.elements.hierarchyFilterDropdown.contains(event.target)) {
                this.elements.hierarchyFilterDropdown.classList.add('hidden');
            }
            if (!this.elements.moduleFilterBtn.contains(event.target) && !this.elements.moduleFilterDropdown.contains(event.target)) {
                this.elements.moduleFilterDropdown.classList.add('hidden');
            }
            if (!this.elements.variantFilterBtn.contains(event.target) && !this.elements.variantFilterDropdown.contains(event.target)) {
                 this.elements.variantFilterDropdown.classList.add('hidden');
            }
            if (!this.elements.viewModeBtn.contains(event.target) && !this.elements.viewModeDropdown.contains(event.target)) {
                this.elements.viewModeDropdown.classList.add('hidden');
            }
        });
    },

    handleViewToggle(e) {
        const button = e.target.closest('.view-toggle');
        if (!button) return;
        this.state.currentView = button.dataset.view;
        this.resetSelection();
        this.setupSearchData();
        this.render();
    },

    handleViewModeChange(e) {
        e.preventDefault();
        const target = e.target.closest('a');
        if(!target) return;

        this.state.viewMode = target.dataset.value;
        this.elements.viewModeText.textContent = target.textContent;
        this.elements.viewModeDropdown.classList.add('hidden');
        this.elements.viewToggles.style.display = this.state.viewMode === 'flat' ? 'block' : 'none';
        this.updateViewToggles();
        this.resetSelection();
        this.setupSearchData();
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
        const { name, type, moduleName, testSuiteName } = td.dataset;
        if (type === 'module') {
            this.state.selectedModule = name;
            this.state.currentView = this.state.currentHierarchy === 'tests' ? 'testSuites' : 'packages';
        } else if (type === 'testSuite') {
            this.state.selectedModule = moduleName;
            this.state.selectedTestSuite = name;
            this.state.currentView = 'packages';
        } else if (type === 'package') {
            this.state.selectedModule = moduleName;
            if (testSuiteName) this.state.selectedTestSuite = testSuiteName;
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
        this.state.selectedTestSuite = null;
        this.state.selectedPackage = null;
    },

    getPackages(module) {
        if (this.state.currentHierarchy === 'source') {
            return module.packages || [];
        }
        return (module.testSuites || []).flatMap(ts => ts.packages || []);
    },

    getAllClasses() {
        return this.fullReport.modules.flatMap(m => this.getPackages(m).flatMap(p => p.classes || []));
    },

    setupSearchData() {
        const allItems = this.fullReport.modules.flatMap(m => {
            const moduleItem = {...m, type: 'module'};
            if (this.state.currentHierarchy === 'source') {
                const packages = (m.packages || []).flatMap(p => [{...p, type:'package', moduleName: m.name}, ...p.classes.map(c => ({...c, type:'class', moduleName: m.name, packageName: p.name}))]);
                return [moduleItem, ...packages];
            } else {
                const testSuites = (m.testSuites || []).flatMap(ts => {
                    const testSuiteItem = {...ts, type:'testSuite', moduleName: m.name};
                    const packages = (ts.packages || []).flatMap(p => [{...p, type:'package', moduleName: m.name, testSuiteName: ts.name}, ...p.classes.map(c => ({...c, type:'class', moduleName: m.name, testSuiteName: ts.name, packageName: p.name}))]);
                    return [testSuiteItem, ...packages];
                });
                return [moduleItem, ...testSuites];
            }
        });

        if (this.state.viewMode === 'tree') {
            this.state.searchableList = allItems;
        } else {
             switch(this.state.currentView) {
                case 'packages': this.state.searchableList = allItems.filter(i => i.type === 'package'); break;
                case 'classes': this.state.searchableList = allItems.filter(i => i.type === 'class'); break;
                case 'testSuites': this.state.searchableList = allItems.filter(i => i.type === 'testSuite'); break;
                default: this.state.searchableList = allItems.filter(i => i.type === 'module'); break;
            }
        }
    },

    getSortedData(data) {
        const { by, order } = this.state.sort;
        if (!by) return data;

        const getNestedValue = (obj, path) => path.split('.').reduce((acc, part) => acc && acc[part], obj);

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
        this.updateActiveTabs();
        let dataToRender = this.getFilteredData();
        dataToRender = this.getSortedData(dataToRender);

        this.updateSummaryCards(dataToRender);
        this.renderTable(dataToRender);

        this.updateTooltipsForOverflow();
    },

    updateActiveTabs() {
        document.querySelectorAll('.view-toggle').forEach(btn => {
            btn.classList.remove('active');
        });
        const activeButton = this.elements.flatViewControls.querySelector(`[data-view="${this.state.currentView}"]`);
        if (activeButton) {
            activeButton.classList.add('active');
        }
    },

    filterTreeData(modules, term) {
        if (!term) return modules;

        const filterClasses = (classes = []) => {
            return classes.filter(c => c.name.toLowerCase().includes(term));
        };

        const filterPackages = (packages = []) => {
            return packages.map(pkg => {
                if (pkg.name.toLowerCase().includes(term)) {
                    return { ...pkg }; // Keep package and all its children if package name matches
                }
                const filteredClasses = filterClasses(pkg.classes);
                if (filteredClasses.length > 0) {
                    return { ...pkg, classes: filteredClasses }; // Keep package if a child class matches
                }
                return null;
            }).filter(Boolean);
        };

        const filterTestSuites = (suites = []) => {
            return suites.map(ts => {
                if (ts.name.toLowerCase().includes(term)) {
                    return { ...ts }; // Keep test suite and all its descendants
                }
                const filteredPackages = filterPackages(ts.packages);
                if (filteredPackages.length > 0) {
                    return { ...ts, packages: filteredPackages }; // Keep suite if a child package/class matches
                }
                return null;
            }).filter(Boolean);
        };

        return modules.map(mod => {
            if (mod.name.toLowerCase().includes(term)) {
                return { ...mod }; // Keep module and all its descendants
            }

            if (this.state.currentHierarchy === 'source') {
                const filteredPackages = filterPackages(mod.packages || []);
                if (filteredPackages.length > 0) {
                    return { ...mod, packages: filteredPackages };
                }
            } else {
                const filteredTestSuites = filterTestSuites(mod.testSuites || []);
                if (filteredTestSuites.length > 0) {
                    return { ...mod, testSuites: filteredTestSuites };
                }
            }
            return null;
        }).filter(Boolean);
    },

    getFilteredData() {
        const { viewMode, currentView, currentHierarchy, selectedModule, selectedTestSuite, selectedPackage, filters } = this.state;
        let data;

        const allModules = this.fullReport.modules.map(m => ({ ...m, type: 'module' }));

        if (viewMode === 'tree') {
            data = allModules;
        } else {
            const allTestSuites = this.fullReport.modules.flatMap(m => (m.testSuites || []).map(ts => ({ ...ts, type: 'testSuite', moduleName: m.name })));
            const allPackages = this.fullReport.modules.flatMap(m =>
                (currentHierarchy === 'source' ? m.packages || [] : (m.testSuites || []).flatMap(ts => ts.packages.map(p => ({...p, testSuiteName: ts.name}))))
                .map(p => ({ ...p, type: 'package', moduleName: m.name }))
            );
            const allClasses = this.fullReport.modules.flatMap(m =>
                (currentHierarchy === 'source' ? m.packages || [] : (m.testSuites || []).flatMap(ts => ts.packages.map(p => ({...p, testSuiteName: ts.name}))))
                .flatMap(p =>
                    (p.classes || []).map(c => ({
                        ...c,
                        type: 'class',
                        packageName: p.name,
                        moduleName: m.name,
                        testSuiteName: p.testSuiteName
                    }))
                )
            );

            if (selectedPackage) data = allClasses.filter(c => c.packageName === selectedPackage && c.moduleName === selectedModule && (currentHierarchy === 'source' || c.testSuiteName === selectedTestSuite));
            else if (selectedTestSuite) data = allPackages.filter(p => p.testSuiteName === selectedTestSuite && p.moduleName === selectedModule);
            else if (selectedModule) {
                if (currentHierarchy === 'source') data = allPackages.filter(p => p.moduleName === selectedModule);
                else data = allTestSuites.filter(ts => ts.moduleName === selectedModule);
            }
            else if (currentView === 'packages') data = allPackages;
            else if (currentView === 'classes') data = allClasses;
            else if (currentView === 'testSuites') data = allTestSuites;
            else data = allModules;
        }

        if (filters.module !== 'all') {
            const moduleName = filters.module;
            if(viewMode === 'tree') {
                data = data.filter(item => item.name === moduleName);
            } else {
                data = data.filter(item => item.moduleName === moduleName || (item.type === 'module' && item.name === moduleName));
            }
        }

        if (filters.search) {
             if (viewMode === 'tree') {
                 data = this.filterTreeData(data, filters.search.toLowerCase());
             } else {
                 const resultNames = new Set(this.state.searchableList
                     .filter(item => item.name.toLowerCase().includes(filters.search))
                     .map(r => r.name));
                 data = data.filter(item => resultNames.has(item.name));
             }
        }
        return data;
    },

    updateSummaryCards(dataToRender) {
        const { viewMode, currentView } = this.state;
        let relevantClasses;
        let moduleCount = 0;

        if(viewMode === 'tree'){
            relevantClasses = dataToRender.flatMap(m => this.getPackages(m).flatMap(p => p.classes || []));
            moduleCount = dataToRender.length;
        } else {
             if (currentView === 'classes') {
                relevantClasses = dataToRender;
                moduleCount = new Set(dataToRender.map(c => c.moduleName)).size || 1;
            } else if (currentView === 'packages') {
                relevantClasses = dataToRender.flatMap(p => p.classes || []);
                moduleCount = new Set(dataToRender.map(p => p.moduleName)).size || 1;
            } else if (currentView === 'testSuites') {
                relevantClasses = dataToRender.flatMap(ts => (ts.packages || []).flatMap(p => p.classes || []));
                 moduleCount = new Set(dataToRender.map(ts => ts.moduleName)).size || 1;
            } else {
                relevantClasses = dataToRender.flatMap(m => this.getPackages(m).flatMap(p => p.classes || []));
                moduleCount = dataToRender.length;
            }
        }

        this.elements.totalModules.textContent = moduleCount;
        this.elements.totalClasses.textContent = relevantClasses.length;
    },

    renderTable(dataToRender) {
        this.renderHeaders(dataToRender);
        if (this.state.viewMode === 'tree') {
            this.renderTreeRows(dataToRender);
        } else {
            this.renderFlatRows(dataToRender);
        }
    },

    updateTooltipsForOverflow() {
        const cells = this.elements.coverageData.querySelectorAll('.sticky-name');

        cells.forEach(cell => {
            const isOverflowing = cell.scrollWidth > cell.clientWidth;

            if (!isOverflowing) {
                cell.removeAttribute('title');
            }
        });
    },

    getCoverageClass(percentage) {
        if (percentage === '--') return 'text-gray-500';
        if (percentage >= 80) return 'text-green-600';
        if (percentage >= 60) return 'text-yellow-600';
        return 'text-red-600';
    },

    getCoverageValues(item, variantName) {
        const found = item.variantCoverages ? item.variantCoverages.find(v => v.name === variantName) : null;

        if (found) {
            return {
                instrPercent: found.instruction.percent + '%',
                instrRatio: `${found.instruction.covered}/${found.instruction.total}`,
                branchPercent: found.branch.percent + '%',
                branchRatio: `${found.branch.covered}/${found.branch.total}`,
                instrColor: this.getCoverageClass(found.instruction.percent),
                branchColor: this.getCoverageClass(found.branch.percent)
            };
        }

        return {
            instrPercent: '--',
            instrRatio: '0/0',
            branchPercent: '--',
            branchRatio: '0/0',
            instrColor: this.getCoverageClass('--'),
            branchColor: this.getCoverageClass('--')
        };
    },

    renderHeaders(data) {
        const { viewMode, currentView, filters, sort, currentHierarchy } = this.state;
        const topHeader = document.createElement('tr');
        topHeader.className = "border-b border-gray-200";
        const subHeader = document.createElement('tr');
        subHeader.className = "border-b border-gray-200";

        let mainHeaderTitle = viewMode === 'tree' ? 'Module' : currentView.charAt(0).toUpperCase() + currentView.slice(1);
        if(currentView === 'testSuites') mainHeaderTitle = "Test Suite";

        let firstColClass = "py-4 px-6 text-left font-semibold text-gray-700 sticky-name bg-gray-50 z-30";

        const sortIndicator = (key) => sort.by === key ? (sort.order === 'asc' ? '▲' : '▼') : '';
        topHeader.innerHTML = `<th class="${firstColClass}" data-sort-by="name">${mainHeaderTitle} ${sortIndicator('name')}</th>`;
        subHeader.innerHTML = `<th class="py-2 px-6 sticky-name bg-gray-50 z-30"></th>`;

        if (viewMode === 'flat' && (currentView === 'packages' || currentView === 'classes' || currentView === 'testSuites')) {
            let contextTitle = '';
            if (currentView === 'packages') {
                contextTitle = currentHierarchy === 'tests' ? 'Test Suite' : 'Module';
            } else if (currentView === 'classes') {
                contextTitle = 'Package';
            } else if (currentView === 'testSuites') {
                contextTitle = 'Module';
            }
            topHeader.innerHTML += `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50"></th>`;
            subHeader.innerHTML += `<th class="py-2 px-4 text-left text-xs font-medium text-gray-600">${contextTitle}</th>`;

            if (currentView === 'classes' && currentHierarchy === 'tests') {
                topHeader.innerHTML += `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50"></th>`;
                subHeader.innerHTML += `<th class="py-2 px-4 text-left text-xs font-medium text-gray-600">Test Suite</th>`;
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
                (item.testSuites && item.testSuites.length > 0) ||
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
                <td class="py-3 px-6 sticky-name" title="${item.name}"><div class="flex items-center gap-2 cursor-pointer" style="padding-left: ${level * 1.5}rem;">${chevron}${nameContent}</div></td>
                ${coverageCells}
            </tr>`;
        };

        let html = modules.map(module => {
            let moduleContext = { moduleName: module.name };
            let childrenHtml = '';
            if (this.state.currentHierarchy === 'source') {
                childrenHtml = (module.packages || []).map(pkg => {
                    let packageContext = { ...moduleContext, packageName: pkg.name };
                    return renderRow(pkg, 1, 'package', module.name, moduleContext) +
                            (pkg.classes || []).map(cls => renderRow(cls, 2, 'class', pkg.name, packageContext)).join('');
                }).join('');
            } else {
                childrenHtml = (module.testSuites || []).map(ts => {
                    let testSuiteContext = { ...moduleContext, testSuiteName: ts.name };
                    return renderRow(ts, 1, 'testSuite', module.name, moduleContext) +
                            (ts.packages || []).map(pkg => {
                                let packageContext = { ...testSuiteContext, packageName: pkg.name };
                                return renderRow(pkg, 2, 'package', ts.name, testSuiteContext) +
                                        (pkg.classes || []).map(cls => renderRow(cls, 3, 'class', pkg.name, packageContext)).join('');
                            }).join('');
                }).join('');
            }
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
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" title="${item.name}" data-name="${item.name}" data-type="${item.type}" data-module-name="${item.moduleName}" data-test-suite-name="${item.testSuiteName || ''}">${item.name}</td><td class="py-3 px-6">${this.state.currentHierarchy === 'tests' ? item.testSuiteName : item.moduleName}</td>`;
                    break;
                case 'classes':
                    nameCell = `<td class="py-3 px-6 sticky-name" title="${item.name}"><a href="#" class="font-medium text-blue-700 hover:underline class-link" data-class-name="${item.name}" data-module-name="${item.moduleName}" data-package-name="${item.packageName}" data-test-suite-name="${item.testSuiteName || ''}">${item.name}</a></td><td class="py-3 px-6">${item.packageName}</td>`;
                    if (this.state.currentHierarchy === 'tests') {
                        nameCell += `<td class="py-3 px-6">${item.testSuiteName || ''}</td>`;
                    }
                    break;
                case 'testSuites':
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" title="${item.name}" data-name="${item.name}" data-type="${item.type}" data-module-name="${item.moduleName}">${item.name}</td><td class="py-3 px-6">${item.moduleName}</td>`;
                    break;
                default:
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" title="${item.name}" data-name="${item.name}" data-type="${item.type}" data-module-name="${item.moduleName}">${item.name}</td>`;
            }
            return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${nameCell}${coverageCells}</tr>`;
        }).join('');
    }
};

document.addEventListener('DOMContentLoaded', () => App.init());
