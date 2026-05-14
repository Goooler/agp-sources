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

const SourceViewApp = {
    elements: {},
    classData: null,
    context: {},
    state: {
        selectedVariants: [],
        isLoading: false,
        scrollLock: false
    },

    init() {
        this.cacheElements();
        this.bindEvents();
        this.closeDropdownOnClickOutside();
    },

    cacheElements() {
        this.elements = {
            functionListContainer: document.querySelector('.func-list-container'),
            functionList: document.getElementById('function-list'),
            functionSearch: document.getElementById('function-search'),
            functionSearchClearBtn: document.getElementById('function-search-clear-btn'),
            srcSearchRevealBtn: document.getElementById('src-search-reveal-btn'),
            srcSearchWrapper: document.getElementById('src-search-wrapper'),
            sourceBreadcrumbs: document.getElementById('source-breadcrumbs'),
            variantFilterBtn: document.getElementById('source-variant-filter-btn'),
            sourceVariantFilterText: document.getElementById('source-variant-filter-text'),
            variantFiltersDropdown: document.getElementById('source-variant-filters-dropdown'),
            variantFilters: document.getElementById('source-variant-filters'),
            sourceViewContainer: document.getElementById('source-view-container'),
            scrollLockSegments: document.getElementById('scroll-lock-segments'),
            loadingOverlay: this.createLoadingOverlay()
        };
    },

    createLoadingOverlay() {
        const div = document.createElement('div');
        div.className = 'fixed inset-0 bg-white bg-opacity-75 flex items-center justify-center z-50 hidden';
        div.innerHTML = `
            <div class="flex flex-col items-center">
                <svg class="animate-spin h-10 w-10 text-blue-600 mb-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span class="text-lg font-semibold text-gray-700">Loading Source...</span>
            </div>
        `;
        document.body.appendChild(div);
        return div;
    },

    resetSearchUI() {
        if (this.elements.functionSearch) {
            this.elements.functionSearch.value = '';
        }
        if (this.elements.functionSearchClearBtn) {
            this.elements.functionSearchClearBtn.classList.add('hidden');
        }
        this.handleFunctionSearch({ target: { value: '' } });
    },

    revealSearchUI() {
        if (!this.elements.srcSearchWrapper || !this.elements.srcSearchRevealBtn) return;
        this.elements.srcSearchRevealBtn.classList.add('transparent');
        this.elements.srcSearchWrapper.classList.add('expanded');
        if (this.elements.functionSearch) {
            this.elements.functionSearch.focus();
        }
    },

    collapseSearchUI(animated = true) {
        if (!this.elements.srcSearchWrapper || !this.elements.srcSearchRevealBtn) return;

        this.elements.srcSearchWrapper.classList.remove('expanded');
        this.elements.srcSearchRevealBtn.classList.remove('transparent');
        if (!animated) {
            this.elements.srcSearchRevealBtn.classList.remove('hidden');
        }
    },

    /**
     * Entry point to load specific source files for a class and then render.
     */
    async loadAndRender(classData, context = {}) {
        // Reset selected variants if we are loading a new class (check name and package)
        // Unless we are restoring state from history.
        const isNewClass = !this.classData || this.classData.packageName !== classData.packageName || this.classData.name !== classData.name;
        const isPopping = typeof Navigation !== 'undefined' && Navigation.isPopping;

        if (isNewClass && !isPopping) {
            this.state.selectedVariants = [];
        }

        this.classData = classData;
        this.context = context;

        if (!classData?.variantSourceFilePaths || classData.variantSourceFilePaths.length === 0) {
            this.render();
            return;
        }

        this.toggleLoading(true);

        const requiredPaths = [...new Set(classData.variantSourceFilePaths.map(v => v.path))];

        try {
            await this.fetchSourceFiles(requiredPaths);

            this.render();
        } catch (error) {
            console.error("[SourceView] Failed to load source files:", error);
            this.elements.sourceViewContainer.innerHTML = `
                <div class="p-8 text-center text-red-600">
                    <h3 class="text-lg font-bold">Error Loading Source</h3>
                    <p>Could not load coverage data for ${classData.sourceFileName}.</p>
                </div>`;
        } finally {
            this.toggleLoading(false);
        }
    },

    toggleLoading(show) {
        this.state.isLoading = show;
        if (show) {
            this.elements.loadingOverlay.classList.remove('hidden');
        } else {
            this.elements.loadingOverlay.classList.add('hidden');
        }
    },

    /**
     * Dynamically injects script tags.
     */
    fetchSourceFiles(paths) {
        const promises = paths.map(path => {
            if (window.coverageData && window.coverageData[path]) {
                return Promise.resolve();
            }

            return new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = `sourcefiles/${path}.json.js`;
                script.onload = () => resolve();
                script.onerror = () => reject(new Error(`Failed to load ${script.src}`));
                document.body.appendChild(script);
            });
        });

        return Promise.all(promises);
    },

    render() {
        const availableVariants = [...new Set((this.classData?.variantSourceFilePaths || []).map(v => v.variantName))];
        if (this.state.selectedVariants.length === 0) {
            this.state.selectedVariants = [...availableVariants];
        }

        this.renderBreadcrumbs();
        this.renderFilters(availableVariants);
        this.renderFunctionList();
        this.renderAllVariantViews();
        this.updateVariantButtonText();

        if (typeof Navigation !== 'undefined') {
            Navigation.replace();
        }
    },

    renderBreadcrumbs() {
        const { sourceFileName } = this.classData;
        const packageName = this.classData.packageName || this.context.packageName;
        const { moduleName } = this.context;

        let html = `<div class="flex items-center gap-2 text-sm">
            <a href="#" class="breadcrumb-link" data-action="go-to-modules">Project</a>`;

        if (moduleName) {
            html += `
                <span class="breadcrumb-separator" aria-hidden="true">/</span>
                <a href="#" class="breadcrumb-link" data-action="go-to-packages" data-module-name="${moduleName}">${moduleName}</a>`;
        }
        if (packageName) {
             html += `
                <span class="breadcrumb-separator" aria-hidden="true">/</span>
                <a href="#" class="breadcrumb-link" data-action="go-to-classes" data-module-name="${moduleName}" data-package-name="${packageName}">${packageName}</a>`;
        }

        html += `
            <span class="breadcrumb-separator" aria-hidden="true">/</span>
            <span class="font-semibold text-gray-800">${sourceFileName}</span>
        </div>`;

        this.elements.sourceBreadcrumbs.innerHTML = html;
    },

    bindEvents() {
        this.elements.variantFilterBtn.addEventListener('click', this.toggleVariantDropdown.bind(this));
        this.elements.variantFilterBtn.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                e.stopPropagation();
                this.toggleVariantDropdown();
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                if (this.elements.variantFiltersDropdown.classList.contains('hidden')) {
                    this.toggleVariantDropdown();
                } else {
                    const firstItem = this.elements.variantFiltersDropdown.querySelector('button, [tabindex="0"], input');
                    if (firstItem) firstItem.focus();
                }
            }
        });

        this.elements.variantFiltersDropdown.addEventListener('keydown', (e) => {
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                e.preventDefault();
                const items = Array.from(this.elements.variantFiltersDropdown.querySelectorAll('input:not([disabled]), button:not([disabled]), [role="option"]'))
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

        this.elements.functionSearch.addEventListener('input', this.handleFunctionSearch.bind(this));
        this.elements.functionSearchClearBtn.addEventListener('click', this.handleFunctionSearchClear.bind(this));
        this.elements.functionList.addEventListener('click', this.handleMethodClick.bind(this));
        this.elements.functionList.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                const methodLink = e.target.closest('.method-link');
                if (methodLink) {
                    e.preventDefault();
                    methodLink.click();
                }
            }
        });
        this.elements.sourceBreadcrumbs.addEventListener('click', this.handleBreadcrumbClick.bind(this));
        this.elements.sourceBreadcrumbs.addEventListener('keydown', (e) => {
            if (e.key === ' ') {
                const link = e.target.closest('.breadcrumb-link');
                if (link) {
                    e.preventDefault();
                    link.click();
                }
            }
        });

        if (this.elements.srcSearchRevealBtn) {
            this.elements.srcSearchRevealBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.revealSearchUI();
            });
        }

        if (this.elements.scrollLockSegments) {
            this.elements.scrollLockSegments.addEventListener('click', (e) => {
                const btn = e.target.closest('.segment-btn');
                if (!btn) return;

                this.elements.scrollLockSegments.querySelectorAll('.segment-btn').forEach(b => {
                    b.classList.remove('active');
                    b.setAttribute('aria-pressed', 'false');
                    b.removeAttribute('tabindex');
                });
                btn.classList.add('active');
                btn.setAttribute('aria-pressed', 'true');
                btn.removeAttribute('tabindex');

                this.state.scrollLock = (btn.dataset.value === 'on');
            });
        }



        this.elements.sourceViewContainer.addEventListener('scroll', (e) => {
            if (!this.state.scrollLock) return;
            if (!e.target.classList.contains('code-container')) return;

            // If another container is already the leader, ignore this event to prevent feedback loops.
            if (this.scrollLeader && this.scrollLeader !== e.target) return;

            // Establish the leader and set a timeout to release leadership after inactivity.
            this.scrollLeader = e.target;
            if (this.scrollLeaderTimer) clearTimeout(this.scrollLeaderTimer);
            this.scrollLeaderTimer = setTimeout(() => {
                this.scrollLeader = null;
            }, 100);

            const source = e.target;
            const containers = this.elements.sourceViewContainer.querySelectorAll('.code-container');

            // Sync all other containers to the leader's position.
            requestAnimationFrame(() => {
                containers.forEach(c => {
                    if (c !== source) {
                        c.scrollTop = source.scrollTop;
                        c.scrollLeft = source.scrollLeft;
                    }
                });
            });
        }, true);
    },

    handleBreadcrumbClick(e) {
        const link = e.target.closest('a[data-action]');
        if (!link) return;

        e.preventDefault();
        const { action, moduleName, packageName } = link.dataset;

        switch (action) {
            case 'go-to-modules':
                CoverageReportApp.resetSelection();
                CoverageReportApp.state.currentView = 'modules';
                App.showReportView();
                CoverageReportApp.render(true);
                Navigation.push();
                setTimeout(() => {
                    if (CoverageReportApp.elements.tableHeaders) {
                        const firstHeader = CoverageReportApp.elements.tableHeaders.querySelector('[tabindex="0"]');
                        if (firstHeader) firstHeader.focus();
                    }
                }, 0);
                break;
            case 'go-to-packages':
                CoverageReportApp.state.selectedModule = moduleName;
                CoverageReportApp.state.selectedPackage = null;
                CoverageReportApp.state.currentView = 'packages';
                App.showReportView();
                CoverageReportApp.render(true);
                Navigation.push();
                setTimeout(() => {
                    if (CoverageReportApp.elements.tableHeaders) {
                        const firstHeader = CoverageReportApp.elements.tableHeaders.querySelector('[tabindex="0"]');
                        if (firstHeader) firstHeader.focus();
                    }
                }, 0);
                break;
            case 'go-to-classes':
                CoverageReportApp.state.selectedModule = moduleName;
                CoverageReportApp.state.selectedPackage = packageName;
                CoverageReportApp.state.currentView = 'classes';
                App.showReportView();
                CoverageReportApp.render(true);
                Navigation.push();
                setTimeout(() => {
                    if (CoverageReportApp.elements.tableHeaders) {
                        const firstHeader = CoverageReportApp.elements.tableHeaders.querySelector('[tabindex="0"]');
                        if (firstHeader) firstHeader.focus();
                    }
                }, 0);
                break;
        }
    },

    toggleVariantDropdown() {
        const isHidden = this.elements.variantFiltersDropdown.classList.contains('hidden');
        if (isHidden) {
            this.elements.variantFiltersDropdown.classList.remove('hidden');
            this.elements.variantFilterBtn.setAttribute('aria-expanded', 'true');

            // Focus management
            setTimeout(() => {
                const searchInput = this.elements.variantFiltersDropdown.querySelector('input');
                if (searchInput) {
                    searchInput.focus();
                } else {
                    const firstItem = this.elements.variantFiltersDropdown.querySelector('button, [tabindex="0"], input');
                    if (firstItem) firstItem.focus();
                }
            }, 0);
        } else {
            this.elements.variantFiltersDropdown.classList.add('hidden');
            this.elements.variantFilterBtn.setAttribute('aria-expanded', 'false');
        }
    },

    closeDropdownOnClickOutside() {
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                if (!this.elements.variantFiltersDropdown.classList.contains('hidden')) {
                    this.elements.variantFiltersDropdown.classList.add('hidden');
                    this.elements.variantFilterBtn.setAttribute('aria-expanded', 'false');
                    this.elements.variantFilterBtn.focus();
                } else if (this.elements.srcSearchWrapper && this.elements.srcSearchWrapper.classList.contains('expanded')) {
                    this.collapseSearchUI(true);
                    this.elements.srcSearchRevealBtn.focus();
                }
            }
        });

        document.addEventListener('click', (event) => {
            if (!document.body.contains(event.target)) return;

            if (!this.elements.variantFilterBtn.contains(event.target) && !this.elements.variantFiltersDropdown.contains(event.target)) {
                this.elements.variantFiltersDropdown.classList.add('hidden');
                this.elements.variantFilterBtn.setAttribute('aria-expanded', 'false');
            }

            // Search Input Collapse
            if (this.elements.srcSearchWrapper && this.elements.srcSearchRevealBtn) {
                if (!this.elements.srcSearchWrapper.contains(event.target) && !this.elements.srcSearchRevealBtn.contains(event.target)) {
                    if (this.elements.functionSearch && this.elements.functionSearch.value === '') {
                        this.collapseSearchUI(true);
                    }
                }
            }
        });
    },

    updateVariantButtonText() {
        const selectedCount = this.state.selectedVariants.length;
        const availableVariants = [...new Set((this.classData?.variantSourceFilePaths || []).map(v => v.variantName))];
        const allVariantsCount = availableVariants.length;

        if (selectedCount === allVariantsCount && allVariantsCount > 0) {
            this.elements.sourceVariantFilterText.textContent = 'All';
        } else if (selectedCount === 1) {
            this.elements.sourceVariantFilterText.textContent = this.state.selectedVariants[0];
        } else {
            this.elements.sourceVariantFilterText.textContent = `${selectedCount} Variants`;
        }
    },

    handleFunctionSearch(e) {
        const searchTerm = e.target.value.toLowerCase();
        this.elements.functionList.querySelectorAll('.method-link').forEach(link => {
            const methodName = link.textContent.trim().toLowerCase();
            link.style.display = methodName.includes(searchTerm) ? 'block' : 'none';
        });

        if (searchTerm.length > 0) {
            this.elements.functionSearchClearBtn.classList.remove('hidden');
        } else {
            this.elements.functionSearchClearBtn.classList.add('hidden');
        }
    },

    handleFunctionSearchClear() {
        this.resetSearchUI();
        this.collapseSearchUI(true);
        if (this.elements.srcSearchRevealBtn) {
            this.elements.srcSearchRevealBtn.focus();
        }
    },

    handleMethodClick(e) {
        const methodLink = e.target.closest('.method-link');
        if (!methodLink) return;

        this.elements.functionList.querySelectorAll('.method-link').forEach(el => el.classList.remove('bg-gray-200'));
        methodLink.classList.add('bg-gray-200');

        const methodName = methodLink.dataset.methodName;
        const methodData = this.classData.methods.find(m => m.name === methodName);

        if (methodData && methodData.variantLineNumbers) {
            const selectedVariantMappings = methodData.variantLineNumbers.filter(m => this.state.selectedVariants.includes(m.variantName));

            // If scroll lock is on, we only initiate a smooth scroll for the first variant.
            // Our improved sync listener will ensure all others follow smoothly in lock-step.
            const variantsToScroll = this.state.scrollLock
                ? selectedVariantMappings.slice(0, 1)
                : selectedVariantMappings;

            variantsToScroll.forEach(mapping => {
                const variantContainer = this.elements.sourceViewContainer.querySelector(`.variant-code-view[data-variant="${mapping.variantName}"]`);
                if (variantContainer) {
                    const codeContainer = variantContainer.querySelector('.code-container');
                    const row = variantContainer.querySelector(`tr[data-line-number="${mapping.lineNumber}"]`);
                    if (row && codeContainer) {
                        const newScrollTop = row.offsetTop - (codeContainer.clientHeight / 2) + (row.clientHeight / 2);
                        codeContainer.scrollTo({
                            top: newScrollTop,
                            behavior: 'smooth'
                        });
                    }
                }
            });

            // Highlight the line in all relevant variants regardless of scroll lock state
            selectedVariantMappings.forEach(mapping => {
                const variantContainer = this.elements.sourceViewContainer.querySelector(`.variant-code-view[data-variant="${mapping.variantName}"]`);
                if (variantContainer) {
                    const row = variantContainer.querySelector(`tr[data-line-number="${mapping.lineNumber}"]`);
                    if (row) {
                        row.classList.remove('highlight-blink');
                        void row.offsetWidth; // Trigger reflow
                        row.classList.add('highlight-blink');
                        setTimeout(() => row.classList.remove('highlight-blink'), 1500);
                    }
                }
            });
        }
    },

    renderFilters(availableVariants) {
        const variantOptions = availableVariants.map(v => ({name: v, value: v}));

        UIUtils.buildActionDropdown(this.elements.variantFiltersDropdown, variantOptions, this.state.selectedVariants, () => {
            this.renderAllVariantViews();
            this.updateVariantButtonText();
            if (typeof Navigation !== 'undefined') {
                Navigation.push();
            }
        }, false);
    },

    renderFunctionList() {
        const header = this.elements.functionListContainer.querySelector('h3');
        if(header) header.textContent = "Methods";

        if (!this.classData.methods || this.classData.methods.length === 0) {
            this.elements.functionList.innerHTML = '<div class="text-sm text-gray-500 px-3">No methods found</div>';
            return;
        }

        this.elements.functionList.innerHTML = this.classData.methods.map(method => `
            <div class="method-link w-full text-left px-3 py-2 rounded-lg transition-colors hover:bg-gray-100 text-gray-700 block cursor-pointer" tabindex="0" role="button" data-method-name="${method.name}">
                <div class="text-sm font-mono truncate">${method.name}</div>
            </div>`
        ).join('');

        // Re-apply any existing search filter
        if (this.elements.functionSearch.value.trim().length > 0) {
            this.handleFunctionSearch({ target: this.elements.functionSearch });
        }
    },

    renderAllVariantViews() {
        if (!this.classData?.variantSourceFilePaths || this.classData.variantSourceFilePaths.length === 0) {
            const packageName = this.classData?.packageName || this.context.packageName;
            const sourceFileName = this.classData?.sourceFileName || this.classData?.name;
            const packagePath = (packageName && packageName !== 'default')
                ? packageName.replace(/\./g, '/') + '/'
                : '';
            const fullSourcePath = packagePath + sourceFileName;

            this.elements.sourceViewContainer.innerHTML = `
                <div class="flex items-center justify-center w-full h-full text-red-600">
                    <div class="text-center p-8">
                        <h3 class="text-lg font-bold">No Source Data</h3>
                        <p>Source file "${fullSourcePath}" was not found during generation of report</p>
                    </div>
                </div>`;
            return;
        }

        this.elements.sourceViewContainer.innerHTML = this.state.selectedVariants.map(variant => this.renderVariantView(variant)).join('');
    },

    getCoverageClass(percent, covered, total) {
        if (total === 0) return '';
        if (covered === 0 && total > 0) return 'cell-uncovered';
        if (covered === total) return 'cell-covered';
        return 'cell-partial';
    },

    /**
     * Helper to select the correct coverage object for a given variant group.
     * Prioritizes the active Test Suite (from context). Falls back to Aggregated.
     */
    resolveCoverageForGroup(variantGroup) {
        if (!variantGroup || !variantGroup.testSuiteCoverages) return null;

        const testSuites = variantGroup.testSuiteCoverages;
        const activeSuiteName = this.context.testSuiteName;

        if (activeSuiteName) {
            const specific = testSuites.find(ts => ts.testSuiteName === activeSuiteName);
            if (specific) return specific.variantCoverage;
        }

        const aggregated = testSuites.find(ts => ts.testSuiteName === 'Aggregated');
        if (aggregated) return aggregated.variantCoverage;

        return testSuites[0]?.variantCoverage || null;
    },

    renderVariantView(variantName) {
        const pathObj = this.classData.variantSourceFilePaths.find(v => v.variantName === variantName);
        if (!pathObj) return ``;

        const fileReport = window.coverageData ? window.coverageData[pathObj.path] : null;

        if (!fileReport) {
            return `
                <div class="variant-code-view" data-variant="${variantName}">
                    <div class="variant-header"><div>${variantName}</div></div>
                    <div class="p-4 text-gray-500 text-center">Data not loaded for this variant.</div>
                </div>`;
        }

        const summaryGroup = fileReport.variantCoverageSummary.find(s => s.variantName === variantName);
        const summaryStats = this.resolveCoverageForGroup(summaryGroup);

        const percentValue = summaryStats ? summaryStats.instruction.percent : null;
        const percentDisplay = (percentValue === null || percentValue === undefined) ? '--' : percentValue + '%';
        const covered = summaryStats ? summaryStats.instruction.covered : 0;
        const total = summaryStats ? summaryStats.instruction.total : 0;
        const colorClass = UIUtils.getCoverageClass(percentValue);

        const tbody = fileReport.linesCoverages.map(line => {
            const { lineNumber, lineText, variantCoverageDetails } = line;

            const vGroup = variantCoverageDetails.find(vg => vg.variantName === variantName);
            const vStats = this.resolveCoverageForGroup(vGroup);

            let statusClass = '';
            let branchIndicator = '';

            if (vStats) {
                statusClass = this.getCoverageClass(
                    vStats.instruction.percent,
                    vStats.instruction.covered,
                    vStats.instruction.total
                );

                if (vStats.branch.total > 0) {
                    const bCovered = vStats.branch.covered;
                    const bTotal = vStats.branch.total;
                    const missed = bTotal - bCovered;
                    const color = bCovered === bTotal ? 'green' : (bCovered > 0 ? 'yellow' : 'red');
                    const tooltipText = `${missed} of ${bTotal} branch${bTotal > 1 ? 'es' : ''} missed`;
                    branchIndicator = `<div class="branch-diamond ${color}"><div class="branch-tooltip">${tooltipText}</div></div>`;
                }
            }

            return `
                <tr data-line-number="${lineNumber}">
                    <td class="line-number">${branchIndicator}${lineNumber}</td>
                    <td class="code-cell ${statusClass}"><pre>${lineText || ' '}</pre></td>
                </tr>
            `;
        }).join('');

        return `
            <div class="variant-code-view" data-variant="${variantName}">
                <div class="variant-header">
                    <div>${variantName}</div>
                    <div class="flex items-baseline gap-2 mt-1">
                        <span class="font-bold ${colorClass}">${percentDisplay}</span>
                        <span class="text-xs text-gray-500 font-normal">${covered}/${total} Lines</span>
                    </div>
                </div>
                <div class="code-container">
                    <table class="code-table">
                        <tbody>${tbody}</tbody>
                    </table>
                </div>
            </div>
        `;
    }
};
