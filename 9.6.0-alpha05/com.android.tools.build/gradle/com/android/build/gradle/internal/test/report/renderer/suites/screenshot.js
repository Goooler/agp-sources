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
 * Screenshot test suite UI plugin (`adapterId = 'screenshot'`).
 * Renders the side-by-side golden/diff/actual comparison view, interactive split slider,
 * class-level sidebar navigation, and zoom lightbox.
 */
const ScreenshotSuitePlugin = {
  id: 'screenshot',
  useRichSingleSuiteList: false,

  getCustomDetailItems(testCase, _app) {
    return this.getScreenshotData(testCase);
  },

  renderCustomDetailView(container, testCase, screenshotItems, context, app) {
    this.renderScreenshotTestView(container, testCase, screenshotItems, context, app);
  },

  getScreenshotData(testCase) {
    const items = [];
    const targets = testCase.target ? [testCase.target] : (testCase.targets || [testCase]);

    targets.forEach(target => {
      const results = target.testSuiteResults || [];
      results.forEach(suiteResult => {
        const suiteName = suiteResult.testSuiteName || "screenshotTest";
        const variantResults = suiteResult.variantResults || {};

        for (const [variantName, res] of Object.entries(variantResults)) {
          if (res.refImagePath || res.newImagePath || res.diffImagePath || res.previewName) {
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

  resolveImagePath(imagePath, app) {
    if (!imagePath) return '';
    let cleanPath = imagePath.replace(/\\/g, '/');
    if (cleanPath.startsWith('http://') || cleanPath.startsWith('https://') || cleanPath.startsWith('data:') || cleanPath.startsWith('file://')) {
      return cleanPath;
    }
    if (cleanPath.startsWith('file:/')) {
      return 'file:///' + cleanPath.slice(6).replace(/^\/+/, '');
    }
    if (cleanPath.startsWith('./')) {
      cleanPath = cleanPath.slice(2);
    }

    // Windows absolute path: e.g. C:/path/to/image.png
    if (/^[a-zA-Z]:\//.test(cleanPath)) {
      return 'file:///' + cleanPath;
    }

    // Unix absolute path: e.g. /path/to/image.png
    if (cleanPath.startsWith('/')) {
      return cleanPath.startsWith('//') ? 'file:' + cleanPath : 'file://' + cleanPath;
    }

    const relRoot = (app && app.state && app.state.relativeRootDir != null)
      ? app.state.relativeRootDir
      : (typeof TEST_DATA_SOURCE !== 'undefined' && TEST_DATA_SOURCE && TEST_DATA_SOURCE.relativeRootDir != null
          ? TEST_DATA_SOURCE.relativeRootDir
          : null);

    if (typeof relRoot === 'string') {
      const prefix = relRoot ? (relRoot.endsWith('/') ? relRoot : relRoot + '/') : '';
      return prefix + cleanPath;
    }

    return '../../../../../' + cleanPath;
  },

  hasValidImagePath(imagePath) {
    if (!imagePath || typeof imagePath !== 'string') return false;
    const lower = imagePath.trim().toLowerCase();
    if (lower === 'no diff' || lower === 'images match' || lower === 'no diff (passed)' || lower === 'none' || lower === 'n/a') return false;
    if (lower.startsWith('data:image/')) return true;
    const cleanForExt = lower.split('?')[0].split('#')[0];
    return cleanForExt.endsWith('.png') || cleanForExt.endsWith('.jpg') || cleanForExt.endsWith('.jpeg') || cleanForExt.endsWith('.webp') || cleanForExt.endsWith('.svg');
  },

  filterSidebarTestCases(inputElement) {
    const query = (inputElement.value || '').toLowerCase();
    const list = inputElement.closest('.screenshot-sidebar').querySelectorAll('.sidebar-test-item');
    list.forEach(item => {
      const name = (item.dataset.testCase || '').toLowerCase();
      item.style.display = name.includes(query) ? 'flex' : 'none';
    });
  },

  switchScreenshotTestCase(clickedElement, app) {
    const { module, package: pkg, class: clz, testCase: tcName } = clickedElement.dataset;
    if (!tcName) return;

    const sidebar = clickedElement.closest('.screenshot-sidebar');
    if (sidebar) {
      sidebar.querySelectorAll('.sidebar-test-item').forEach(el => el.classList.remove('active'));
      clickedElement.classList.add('active');
    }

    const testCaseEntry = app.findTestCase(module, pkg, clz, tcName);
    if (!testCaseEntry) return;

    const mainContent = clickedElement.closest('.screenshot-layout').querySelector('.screenshot-main-content');
    if (mainContent) {
      const activeVariants = app.state.filters.variants || [];
      const screenshotItems = this.getScreenshotData(testCaseEntry.testCase);
      let item = screenshotItems.find(i => activeVariants.includes(i.variantName)) || screenshotItems[0];
      if (item) {
        this.renderScreenshotMainContent(mainContent, testCaseEntry.testCase, item, app);
      }
    }

    const updatedContext = {
      moduleName: testCaseEntry.moduleName || module || '',
      packageName: testCaseEntry.packageName || pkg || '',
      className: testCaseEntry.className || clz || '',
      testCaseName: testCaseEntry.testCase?.name || tcName
    };

    app.state.currentTestCase = testCaseEntry.testCase;
    app.state.currentStackTraceContext = updatedContext;
    app.renderStackTraceBreadcrumbs(updatedContext);
    app.announce(`Showing screenshot test case: ${updatedContext.testCaseName}`);
    document.title = `Stack Trace: ${updatedContext.testCaseName} - ${app.baseTitle}`;
  },

  renderScreenshotTestView(container, testCase, screenshotItems, context = {}, app) {
    const activeVariants = app.state.filters.variants || [];
    let item = screenshotItems.find(i => activeVariants.includes(i.variantName)) || screenshotItems[0];

    const classInfo = app.getClassForTestCase(testCase, context) || {};
    const moduleName = context.moduleName || classInfo.moduleName || '';
    const packageName = context.packageName || classInfo.packageName || '';
    const className = context.className || classInfo.className || 'Test Suite';
    const classNode = classInfo.classNode;
    const classTestCases = classNode ? (classNode.testCases || []) : [testCase];

    container.innerHTML = '';

    const layout = document.createElement('div');
    layout.className = 'screenshot-layout';

    // 1. Left Sidebar: List of all tests in the class file
    const sidebar = document.createElement('aside');
    sidebar.className = 'screenshot-sidebar';

    const testListHtml = classTestCases.map(tc => {
      const isSelected = tc.name === testCase.name;
      const tcStatus = app.getTestCaseOverallStatus(tc);
      const symbolMap = { pass: '✓', fail: '✕', skipped: '⊝' };
      const symbol = symbolMap[tcStatus] || '✓';

      return `
        <li class="sidebar-test-item ${isSelected ? 'active' : ''}" data-module="${UIUtils.escapeHTML(moduleName)}" data-package="${UIUtils.escapeHTML(packageName)}" data-class="${UIUtils.escapeHTML(className)}" data-test-case="${UIUtils.escapeHTML(tc.name)}">
          <span class="tc-status-icon ${tcStatus}">${symbol}</span>
          <span class="tc-name truncate" title="${UIUtils.escapeHTML(tc.name)}">${UIUtils.escapeHTML(tc.name)}</span>
        </li>
      `;
    }).join('');

    sidebar.innerHTML = `
      <div class="sidebar-header">
        <div class="sidebar-class-name">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
          <span class="truncate font-bold" title="${UIUtils.escapeHTML(className)}">${UIUtils.escapeHTML(className)}</span>
        </div>
        <span class="sidebar-count-badge">${classTestCases.length} test${classTestCases.length !== 1 ? 's' : ''}</span>
      </div>
      <div class="sidebar-search-box">
        <input type="text" class="sidebar-search-input" placeholder="Search tests in file...">
      </div>
      <ul class="sidebar-test-list">
        ${testListHtml}
      </ul>
    `;

    sidebar.addEventListener('click', (e) => {
      const itemEl = e.target.closest('.sidebar-test-item');
      if (itemEl) {
        this.switchScreenshotTestCase(itemEl, app);
      }
    });
    const searchInput = sidebar.querySelector('.sidebar-search-input');
    if (searchInput) {
      searchInput.addEventListener('input', () => {
        this.filterSidebarTestCases(searchInput);
      });
    }

    layout.appendChild(sidebar);

    // 2. Right Main Content Panel
    const mainContent = document.createElement('main');
    mainContent.className = 'screenshot-main-content';
    layout.appendChild(mainContent);

    container.appendChild(layout);

    this.renderScreenshotMainContent(mainContent, testCase, item, app);
  },

  renderScreenshotMainContent(mainContent, testCase, item, app) {
    mainContent.innerHTML = '';
    const isPassed = item.status === TEST_STATUS.PASS;
    const isFailed = item.status === TEST_STATUS.FAIL;
    const statusClass = isPassed ? TEST_STATUS.PASS : (isFailed ? TEST_STATUS.FAIL : 'error');
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
          <span class="meta-pill"><span class="meta-pill-label">Suite:</span> ${UIUtils.escapeHTML(UIUtils.formatSuiteName(item.suiteName))}</span>
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
          app.copyToClipboard(item.stackTrace, copyBtn);
        });
      }
    }

    // 3. Image Differences & Comparison Card
    const comparisonCard = document.createElement('div');
    comparisonCard.className = 'screenshot-comparison-card';

    const isSizeMismatch = item.stackTrace && item.stackTrace.includes('Size Mismatch');
    const disableSlider = isPassed || isSizeMismatch;

    const hasValidRef = this.hasValidImagePath(item.refImagePath);
    const hasValidDiff = this.hasValidImagePath(item.diffImagePath);
    const hasValidNew = this.hasValidImagePath(item.newImagePath) || (isPassed && hasValidRef);
    const refUrl = this.resolveImagePath(item.refImagePath, app);
    let newUrl = this.resolveImagePath(item.newImagePath, app);
    if ((!item.newImagePath || !this.hasValidImagePath(item.newImagePath)) && isPassed && item.refImagePath) {
      newUrl = refUrl;
    }
    const diffUrl = this.resolveImagePath(item.diffImagePath, app);

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
        ${!disableSlider ? `
          <div class="mode-switcher" role="tablist" aria-label="Comparison View Mode">
            <button class="mode-btn active" data-mode="side-by-side" role="tab" aria-selected="true">
              🔲 Side-by-Side
            </button>
            <button class="mode-btn" data-mode="slider" role="tab" aria-selected="false">
              ↔️ Split Slider
            </button>
          </div>
        ` : ''}
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
            ${hasValidRef ? `
              <img src="${UIUtils.escapeHTML(refUrl)}" alt="Reference Image" class="preview-img" draggable="false" data-lightbox-src="${UIUtils.escapeHTML(refUrl)}" data-lightbox-title="Reference Image" data-error-title="Reference Image Missing">
              <button class="img-zoom-btn" data-lightbox-src="${UIUtils.escapeHTML(refUrl)}" data-lightbox-title="Reference Image">
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

        <!-- Diff Image Card -->
        <div class="img-card">
          <div class="img-card-header">
            <span class="img-card-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 16v1a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h1"/><rect x="8" y="2" width="13" height="13" rx="2"/></svg>
              Difference Image (Diff)
            </span>
          </div>
          <div class="img-card-body">
            ${hasValidDiff ? `
              <img src="${UIUtils.escapeHTML(diffUrl)}" alt="Diff Image" class="preview-img" draggable="false" data-lightbox-src="${UIUtils.escapeHTML(diffUrl)}" data-lightbox-title="Difference Image" data-error-title="Diff Image Missing">
              <button class="img-zoom-btn" data-lightbox-src="${UIUtils.escapeHTML(diffUrl)}" data-lightbox-title="Difference Image">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
                Zoom
              </button>
            ` : `
              <div class="img-placeholder-card">
                ${isPassed ? `
                  <div class="placeholder-icon pass">✓</div>
                  <div class="placeholder-title" style="color: #16a34a;">No Differences</div>
                  <div class="placeholder-desc">The new image matches the reference image perfectly (100% match).</div>
                ` : (item.stackTrace && item.stackTrace.includes('Size Mismatch') ? `
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

        <!-- New Image Card -->
        <div class="img-card">
          <div class="img-card-header">
            <span class="img-card-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
              New Image (Actual)
            </span>
          </div>
          <div class="img-card-body">
            ${hasValidNew ? `
              <img src="${UIUtils.escapeHTML(newUrl)}" alt="New Image" class="preview-img" draggable="false" data-lightbox-src="${UIUtils.escapeHTML(newUrl)}" data-lightbox-title="New Image" data-error-title="New Image Missing">
              <button class="img-zoom-btn" data-lightbox-src="${UIUtils.escapeHTML(newUrl)}" data-lightbox-title="New Image">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
                Zoom
              </button>
            ` : `
              <div class="img-placeholder-card">
                <div class="placeholder-icon warn">⚠️</div>
                <div class="placeholder-title">No New Image</div>
                <div class="placeholder-desc">New screenshot is not available.</div>
              </div>
            `}
          </div>
          <div class="img-card-footer">
            <span class="truncate">${UIUtils.escapeHTML(item.newImagePath || (isPassed ? 'Same as Reference' : 'N/A'))}</span>
          </div>
        </div>
      </div>

      ${!disableSlider ? `
        <!-- Mode 2: Split Slider View -->
        <div class="slider-view-wrapper hidden">
          <div class="slider-controls-bar">
            <span>Drag the divider line to compare Reference (Left) vs New (Right)</span>
            <span class="slider-split-percent text-blue-600 font-bold">50% Reference | 50% New</span>
          </div>
          <div class="slider-container">
            <span class="slider-badge left-badge">Reference (Golden)</span>
            <span class="slider-badge right-badge">New (Rendered)</span>
            <img src="${UIUtils.escapeHTML(newUrl)}" class="slider-img-base" alt="New Base" draggable="false">
            <div class="slider-img-overlay" style="clip-path: inset(0 50% 0 0);">
              <img src="${UIUtils.escapeHTML(refUrl)}" alt="Reference Overlay" draggable="false">
            </div>
            <div class="slider-divider" style="left: 50%;">
              <div class="slider-handle">↔</div>
            </div>
          </div>
        </div>
      ` : ''}
    `;

    comparisonCard.addEventListener('click', (e) => {
      const modeBtn = e.target.closest('.mode-btn[data-mode]');
      if (modeBtn) {
        this.switchScreenshotMode(modeBtn.dataset.mode, modeBtn);
        return;
      }
      const lightboxTrigger = e.target.closest('[data-lightbox-src]');
      if (lightboxTrigger) {
        this.openLightbox(lightboxTrigger.dataset.lightboxSrc, lightboxTrigger.dataset.lightboxTitle);
      }
    });
    comparisonCard.querySelectorAll('img.preview-img[data-error-title]').forEach((imgEl) => {
      imgEl.addEventListener('error', () => {
        this.handleImageError(imgEl, imgEl.dataset.errorTitle);
      });
    });

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
        <span class="path-label">Diff Image Path:</span>
        <span class="path-value">${UIUtils.escapeHTML(item.diffImagePath || 'None')}</span>
        ${item.diffImagePath ? `<button class="copy-btn" id="btn-copy-diff-path">Copy</button>` : ''}
      </div>
      <div class="path-row">
        <span class="path-label">New Image Path:</span>
        <span class="path-value">${UIUtils.escapeHTML(item.newImagePath || 'Not set')}</span>
        ${item.newImagePath ? `<button class="copy-btn" id="btn-copy-new-path">Copy</button>` : ''}
      </div>
    `;
    wrapper.appendChild(pathsCard);

    // Attach copy button listeners
    const copyRefBtn = pathsCard.querySelector('#btn-copy-ref-path');
    if (copyRefBtn) copyRefBtn.addEventListener('click', () => app.copyToClipboard(item.refImagePath, copyRefBtn));

    const copyDiffBtn = pathsCard.querySelector('#btn-copy-diff-path');
    if (copyDiffBtn) copyDiffBtn.addEventListener('click', () => app.copyToClipboard(item.diffImagePath, copyDiffBtn));

    const copyNewBtn = pathsCard.querySelector('#btn-copy-new-path');
    if (copyNewBtn) copyNewBtn.addEventListener('click', () => app.copyToClipboard(item.newImagePath, copyNewBtn));

    mainContent.appendChild(wrapper);

    // Initialize interactive split slider
    if (!disableSlider) {
      setTimeout(() => this.initSplitSlider(comparisonCard), 50);
    }
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
      const width = rect.width || 1;
      let x = clientX - rect.left;
      if (x < 0) x = 0;
      if (x > width) x = width;

      const pct = Math.round((x / width) * 100);
      divider.style.left = `${pct}%`;
      overlayBox.style.clipPath = `inset(0 ${100 - pct}% 0 0)`;
      if (percentText) {
        percentText.textContent = `${pct}% Reference | ${100 - pct}% New`;
      }
    };

    sliderBox.addEventListener('pointerdown', (e) => {
      isDragging = true;
      setPosition(e.clientX);
      if (typeof sliderBox.setPointerCapture === 'function') {
        sliderBox.setPointerCapture(e.pointerId);
      }
    });

    sliderBox.addEventListener('pointermove', (e) => {
      if (isDragging) {
        setPosition(e.clientX);
      }
    });

    const stopDrag = (e) => {
      if (isDragging) {
        isDragging = false;
        try {
          if (typeof sliderBox.releasePointerCapture === 'function') {
            sliderBox.releasePointerCapture(e.pointerId);
          }
        } catch (_) {}
      }
    };

    sliderBox.addEventListener('pointerup', stopDrag);
    sliderBox.addEventListener('pointercancel', stopDrag);
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
            <button class="lightbox-btn">✕ Close</button>
          </div>
        </div>
      `;
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay || e.target.closest('.lightbox-btn')) {
          ScreenshotSuitePlugin.closeLightbox();
        }
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
};

if (globalThis.SuiteRegistry) {
  globalThis.SuiteRegistry.register('screenshot', ScreenshotSuitePlugin);
}
globalThis.ScreenshotSuitePlugin = ScreenshotSuitePlugin;
