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
 * Composable rich test detail view shared across test suites that emit `androidx.report.*`
 * entries. Suite-specific configuration blocks delegate to `SuiteRegistry`.
 */
const RichTestDetailView = {
  resolvePlugin(res, descriptor) {
    const adapterId = (res && res.adapterId) || (descriptor && descriptor.adapterId) || 'default';
    return SuiteRegistry.resolve(adapterId);
  },

  renderCopyContextAction(app, testCase, activeRun, context, getDetailsFn) {
    const actionsEl = app.elements.detailTopbarActions;
    if (!actionsEl) return;
    actionsEl.innerHTML = `
      <button type="button" class="l2-copy-link" id="copy-context-btn" aria-label="Copy structured test context as Markdown">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M8 5H6a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2v-1M8 5a2 2 0 002 2h2a2 2 0 002-2M8 5a2 2 0 012-2h2a2 2 0 012 2m0 0h2a2 2 0 012 2v3m2 4H10m0 0l3-3m-3 3l3 3"></path>
        </svg>
        <span>Copy context</span>
      </button>
    `;
    const btn = actionsEl.querySelector('#copy-context-btn');
    if (!btn) return;

    btn.addEventListener('click', () => {
      const res = (activeRun && activeRun.res) || {};
      const env = (activeRun && activeRun.env) || null;
      const descriptor = (activeRun && activeRun.descriptor) || null;
      const plugin = this.resolvePlugin(res, descriptor);
      const current = typeof getDetailsFn === 'function' ? (getDetailsFn() || {}) : {};
      const detail = current.detail || (res.detailId ? app.getCachedLazyDetail(res.detailId) : null);
      const envDetail = current.envDetail || (env && env.envId ? app.getCachedLazyDetail(env.envId) : null);

      const needTestLoad = Boolean(res.detailId && !detail);
      const needEnvLoad = Boolean(plugin.needsEnvForTestDetail(env, res) && !envDetail);

      if (!needTestLoad && !needEnvLoad) {
        const markdown = this.buildCopyContextMarkdown(app, testCase, activeRun, context, detail, envDetail);
        app.copyToClipboard(markdown, btn, 'Test context copied to clipboard');
        return;
      }

      Promise.all([
        needTestLoad ? app.loadLazyDetail(res.detailId) : Promise.resolve(detail),
        needEnvLoad ? app.loadLazyDetail(env.envId) : Promise.resolve(envDetail),
      ]).then(([loadedDetail, loadedEnvDetail]) => {
        const markdown = this.buildCopyContextMarkdown(app, testCase, activeRun, context, loadedDetail, loadedEnvDetail);
        app.copyToClipboard(markdown, btn, 'Test context copied to clipboard');
      });
    });
  },

  render(app, grid, testCase, runs, context = {}) {
    grid.innerHTML = '';
    grid.classList.add('rich-detail-mode');

    let activeIdx = app.state.activeDetailRunIndex;
    if (activeIdx === null || activeIdx === undefined || activeIdx < 0 || activeIdx >= runs.length) {
      const firstFailedIdx = runs.findIndex(r => UIUtils.normalizeStatus(r.res?.status) === TEST_STATUS.FAIL);
      activeIdx = firstFailedIdx !== -1 ? firstFailedIdx : 0;
      app.state.activeDetailRunIndex = activeIdx;
    }

    const activeRun = runs[activeIdx];
    const res = activeRun.res || {};
    const env = activeRun.env || null;
    const descriptor = activeRun.descriptor || null;
    const plugin = this.resolvePlugin(res, descriptor);
    const normStatus = UIUtils.normalizeStatus(res.status);
    const shouldLoadEnv = plugin.needsEnvForTestDetail(env, res);
    let currentLazyDetail = res.detailId ? app.getCachedLazyDetail(res.detailId) : null;
    let currentEnvDetail = env && env.envId ? app.getCachedLazyDetail(env.envId) : null;

    this.renderCopyContextAction(app, testCase, activeRun, context, () => ({
      detail: currentLazyDetail,
      envDetail: currentEnvDetail,
    }));

    const rootContainer = document.createElement('div');
    rootContainer.className = 'rich-detail-container';

    if (runs.length > 1) {
      const switcher = document.createElement('div');
      switcher.className = 'detail-run-switcher';
      switcher.setAttribute('role', 'tablist');
      switcher.setAttribute('aria-label', 'Test runs');

      switcher.innerHTML = runs.map((r, idx) => {
        const rNorm = UIUtils.normalizeStatus(r.res?.status);
        const dotColor = rNorm === TEST_STATUS.FAIL ? '#dc2626' : (rNorm === TEST_STATUS.PASS ? '#15803d' : '#64748b');
        const labelParts = [(r.descriptor && r.descriptor.title) || r.suiteName, r.variantName];
        if (r.targetName && !app.hasOnlyDefaultTarget()) {
          labelParts.unshift(r.targetName);
        }
        return `
          <button type="button" role="tab" aria-selected="${idx === activeIdx ? 'true' : 'false'}" class="detail-run-pill ${idx === activeIdx ? 'active' : ''}" data-run-idx="${idx}">
            <span style="width: 8px; height: 8px; border-radius: 9999px; background: ${dotColor}; display: inline-block;" aria-hidden="true"></span>
            <span>${UIUtils.escapeHTML(labelParts.filter(Boolean).join(' · '))}</span>
          </button>
        `;
      }).join('');

      switcher.addEventListener('click', (e) => {
        const pill = e.target.closest('button[data-run-idx]');
        if (!pill) return;
        app.state.activeDetailRunIndex = Number(pill.dataset.runIdx) || 0;
        this.render(app, grid, testCase, runs, context);
      });
      rootContainer.appendChild(switcher);
    }

    const summaryCard = document.createElement('section');
    summaryCard.className = 'detail-card detail-summary-card';
    summaryCard.setAttribute('aria-label', 'Test summary');

    const suiteTitle = (descriptor && descriptor.title) || activeRun.suiteName || 'Test';
    const kickerLabel = plugin.getSummaryKickerLabel(suiteTitle);
    const actors = Array.isArray(res.actors) ? res.actors.filter(Boolean) : [];
    const isCrossDevice = actors.length > 1;
    let topologyHtml = '';
    if (isCrossDevice) {
      topologyHtml = `<span class="detail-dot" aria-hidden="true">·</span><span>Cross-device</span>`;
    } else if (actors.length === 1) {
      topologyHtml = `<span class="detail-dot" aria-hidden="true">·</span><span>Single device</span>`;
    }

    const loc = res.sourceLocation || null;
    const fileLine = loc && loc.file
      ? (loc.line !== null && loc.line !== undefined ? `${loc.file}:${loc.line}` : loc.file)
      : '';
    const sourceLocHtml = fileLine
      ? `<span class="detail-dot" aria-hidden="true">·</span><span class="font-mono">${UIUtils.escapeHTML(fileLine)}</span>`
      : '';

    const displayMethodName = (loc && loc.methodName) || res.methodName || testCase.name || '';
    const fqClassName = (loc && loc.className)
      || (context.packageName && context.className && !context.className.startsWith(context.packageName)
        ? `${context.packageName}.${context.className}`
        : (context.className || ''));
    const subtitleParts = [fqClassName];
    const hasMultipleModules = (typeof app.countActiveSuiteModules === 'function'
      ? app.countActiveSuiteModules()
      : ((app.processedData && app.processedData.modules) || []).length) > 1;
    if (hasMultipleModules && context.moduleName) subtitleParts.push(context.moduleName);

    const summaryCols = descriptor && Array.isArray(descriptor.summaryColumns) ? descriptor.summaryColumns : [];
    const rawAttrs = app.getAllSummaryAttrs(res);
    const hasExplicitDevicesAttr = rawAttrs.some(a => a && a.key === 'devices');
    const hasStandardDeviceActors = actors.length > 0 && actors.every(a => {
      const r = String((a && a.role) || 'DEVICE').toUpperCase();
      return r === 'DEVICE' || r === 'SOURCE' || r === 'TARGET';
    });

    const facts = [];
    let renderedDevicesFact = false;

    const buildDevicesFactHtml = (fallbackDisplay = '') => {
      renderedDevicesFact = true;
      const devHeading = isCrossDevice
        ? 'Source <span aria-hidden="true">→</span> Target'
        : 'Device';
      const devValHtml = UIUtils.formatDevicesRichHtml(res, fallbackDisplay);
      const devSubText = UIUtils.formatDevicesFactSub(res, plugin);
      const devSubHtml = devSubText
        ? `<div class="detail-fact-sub">${UIUtils.escapeHTML(devSubText)}</div>`
        : '';
      return `
        <div class="detail-fact">
          <div class="detail-fact-label">${devHeading}</div>
          <div class="detail-fact-value">${devValHtml}</div>
          ${devSubHtml}
        </div>
      `;
    };

    rawAttrs.forEach(attr => {
      if (!attr) return;
      if (attr.key === 'devices') {
        facts.push(buildDevicesFactHtml(attr.displayValue || attr.value));
        return;
      }
      const colDef = summaryCols.find(c => c && c.key === attr.key);
      const label = (colDef && colDef.label) || attr.label || attr.key;
      const formattedVal = attr.displayValue || attr.value;
      const sub = (attr.value && attr.value !== formattedVal)
        ? `<div class="detail-fact-sub font-mono">${UIUtils.escapeHTML(attr.value)}</div>`
        : '';
      facts.push(`
        <div class="detail-fact">
          <div class="detail-fact-label">${UIUtils.escapeHTML(label)}</div>
          <div class="detail-fact-value">${UIUtils.escapeHTML(formattedVal)}</div>
          ${sub}
        </div>
      `);
    });

    if (!hasExplicitDevicesAttr && hasStandardDeviceActors) {
      facts.push(buildDevicesFactHtml(''));
    }

    const steps = Array.isArray(res.steps) ? res.steps : [];
    const failedStepTarget = UIUtils.getFailedStepTarget(res);
    let durationSubHtml = '';
    if (failedStepTarget) {
      durationSubHtml = `<div class="detail-fact-sub">Failed at ${UIUtils.escapeHTML(failedStepTarget)}</div>`;
    } else if (steps.length > 0) {
      const numberedSteps = steps.filter(s => s && s.n !== null && s.n !== undefined);
      const totalStepCount = numberedSteps.length > 0 ? numberedSteps.length : steps.length;
      const executedCount = (numberedSteps.length > 0 ? numberedSteps : steps)
        .filter(s => UIUtils.normalizeStatus(s.status) !== TEST_STATUS.SKIPPED).length;
      durationSubHtml = `<div class="detail-fact-sub">${executedCount} of ${totalStepCount} steps ran</div>`;
    } else if (activeRun.variantName) {
      durationSubHtml = `<div class="detail-fact-sub">Variant: ${UIUtils.escapeHTML(activeRun.variantName)}</div>`;
    }

    facts.push(`
      <div class="detail-fact">
        <div class="detail-fact-label">Duration</div>
        <div class="detail-fact-value num">${UIUtils.escapeHTML(UIUtils.formatDuration(res.durationMs))}</div>
        ${durationSubHtml}
      </div>
    `);

    const actorsHtml = (!renderedDevicesFact && actors.length > 0) ? `
      <div class="detail-actors-section">
        <div class="detail-fact-label">${UIUtils.escapeHTML(plugin.getActorsHeading(actors.length))}</div>
        <div class="detail-actors-grid">
          ${actors.map(actor => {
            const roleUpper = String(actor.role || 'DEVICE').toUpperCase();
            const roleClass = plugin.getActorRoleClass(roleUpper);
            const metaPieces = [];
            if (actor.image) metaPieces.push(`<span class="font-mono">${UIUtils.escapeHTML(actor.image)}</span>`);
            if (actor.playServices) metaPieces.push(`<span>Play services ${UIUtils.escapeHTML(actor.playServices)}</span>`);
            if (actor.serial) metaPieces.push(`<span class="font-mono">${UIUtils.escapeHTML(actor.serial)}</span>`);
            return `
              <div class="detail-actor-card">
                <div class="detail-actor-head">
                  <span class="detail-role-chip ${roleClass}">${UIUtils.escapeHTML(roleUpper)}</span>
                  <span class="detail-actor-model">${UIUtils.escapeHTML(actor.name || actor.id || plugin.defaultActorLabel)}</span>
                  ${actor.api !== null && actor.api !== undefined && actor.api !== '' ? `<span class="detail-actor-api">API ${UIUtils.escapeHTML(actor.api)}</span>` : ''}
                </div>
                ${metaPieces.length > 0 ? `<div class="detail-actor-meta">${metaPieces.join('<span aria-hidden="true">·</span>')}</div>` : ''}
              </div>
            `;
          }).join('')}
        </div>
      </div>
    ` : '';

    const titleFontCls = /\s/.test(String(displayMethodName || '')) ? '' : ' font-mono';
    summaryCard.innerHTML = `
      <div class="detail-summary-head">
        <div class="detail-summary-identity">
          <div class="detail-kicker">
            <span class="detail-kicker-label">${UIUtils.escapeHTML(kickerLabel)}</span>
            ${sourceLocHtml}
            ${topologyHtml}
          </div>
          <h2 class="detail-title${titleFontCls}">${UIUtils.escapeHTML(displayMethodName)}</h2>
          <p class="detail-subtitle font-mono">${UIUtils.escapeHTML(subtitleParts.filter(Boolean).join(' · '))}</p>
        </div>
        <div class="detail-summary-verdict">${UIUtils.statusTag(res.status)}</div>
      </div>
      <div class="detail-facts">${facts.join('')}</div>
      ${actorsHtml}
    `;
    rootContainer.appendChild(summaryCard);

    const callout = res.callout || null;
    const stackTrace = activeRun.stackTrace || '';
    if (normStatus === TEST_STATUS.FAIL || callout || stackTrace) {
      let bannerTitle = callout && callout.title ? callout.title : '';
      if (!bannerTitle) {
        if (failedStepTarget) {
          const isAssertKind = String((callout && callout.kind) || '').toLowerCase().includes('assert');
          bannerTitle = isAssertKind
            ? `Assertion failed at ${failedStepTarget}`
            : `Failed at ${failedStepTarget}`;
        } else {
          bannerTitle = 'Test failed';
        }
      }

      const firstTraceLine = stackTrace ? stackTrace.split(/\r?\n/).find(l => l.trim().length > 0) || '' : '';
      const bannerMessage = (callout && callout.message) || firstTraceLine;
      const likelyCause = callout && callout.likelyCause ? callout.likelyCause : '';
      const likelyCauseHeading = (descriptor && descriptor.likelyCauseHeading) || 'Likely cause';

      const traceLinesCount = stackTrace ? stackTrace.split(/\r?\n/).length : 0;
      const openTraceByDefault = Boolean(stackTrace && !callout && steps.length === 0);

      const calloutEl = document.createElement('section');
      calloutEl.className = 'detail-callout';
      calloutEl.setAttribute('role', 'region');
      calloutEl.setAttribute('aria-label', 'Failure summary');
      calloutEl.innerHTML = `
        <div class="detail-callout-top">
          <span class="detail-callout-icon" aria-hidden="true">&#x2715;</span>
          <div class="detail-callout-body">
            <div class="detail-callout-title">${UIUtils.escapeHTML(bannerTitle)}</div>
            ${bannerMessage ? `<div class="detail-callout-text font-mono">${UIUtils.escapeHTML(bannerMessage)}</div>` : ''}
          </div>
        </div>
        ${likelyCause ? `
          <div class="detail-likely-cause">
            <div class="detail-likely-cause-label">${UIUtils.escapeHTML(likelyCauseHeading)}</div>
            <div class="detail-likely-cause-text">${UIUtils.escapeHTML(likelyCause)}</div>
          </div>
        ` : ''}
        ${stackTrace ? `
          <details class="detail-stacktrace-details" ${openTraceByDefault ? 'open' : ''}>
            <summary class="detail-stacktrace-summary">Full stack trace (${traceLinesCount} line${traceLinesCount === 1 ? '' : 's'})</summary>
            <div class="detail-stacktrace-toolbar">
              <button type="button" class="copy-btn" data-copy-stack="true">Copy stack trace</button>
            </div>
            <pre class="error-stack-pre">${UIUtils.escapeHTML(stackTrace)}</pre>
          </details>
        ` : ''}
      `;

      const copyStackBtn = calloutEl.querySelector('[data-copy-stack="true"]');
      if (copyStackBtn) {
        copyStackBtn.addEventListener('click', (e) => {
          e.preventDefault();
          e.stopPropagation();
          app.copyToClipboard(stackTrace, copyStackBtn, 'Stack trace copied to clipboard');
        });
      }
      rootContainer.appendChild(calloutEl);
    }

    if (steps.length > 0) {
      const actorById = new Map();
      actors.forEach(a => {
        if (a && a.id) actorById.set(String(a.id).toLowerCase(), a);
        if (a && a.role && !actorById.has(String(a.role).toLowerCase())) {
          actorById.set(String(a.role).toLowerCase(), a);
        }
      });

      const stepsSection = document.createElement('section');
      stepsSection.className = 'detail-section';
      stepsSection.setAttribute('aria-label', 'Lifecycle');

      const showStepActors = actors.length > 1 || actors.length === 0;
      const stepsHtml = steps.map(step => {
        const sNorm = UIUtils.normalizeStatus(step.status);
        const stateCls = sNorm === TEST_STATUS.PASS ? 'is-passed' : (sNorm === TEST_STATUS.FAIL ? 'is-failed' : 'is-skipped');
        const statusLabel = sNorm === TEST_STATUS.PASS ? 'PASSED' : (sNorm === TEST_STATUS.FAIL ? 'FAILED' : 'SKIPPED');
        const isPrep = step.n === null || step.n === undefined;
        const markerHtml = sNorm === TEST_STATUS.PASS
          ? '&#x2713;'
          : (sNorm === TEST_STATUS.FAIL ? '&#x2715;' : '');
        const stepNameStr = String(step.name || 'Step').trim();
        const numHtml = isPrep
          ? (stepNameStr.toLowerCase() === 'prepare' ? '' : '<span class="detail-step-num">Prep</span>')
          : `<span class="detail-step-num">${UIUtils.escapeHTML(String(step.n))}</span>`;

        let actorBadgeHtml = '';
        if (step.on && showStepActors) {
          const onKey = String(step.on).toLowerCase();
          const matchedActor = actorById.get(onKey);
          const roleText = matchedActor && matchedActor.role
            ? String(matchedActor.role).toUpperCase()
            : String(step.on).toUpperCase();
          if (actors.length > 1 || roleText !== 'DEVICE') {
            const onRoleCls = plugin.getStepActorRoleClass(roleText);
            actorBadgeHtml = `<span class="detail-step-on ${onRoleCls}">${UIUtils.escapeHTML(roleText)}</span>`;
          }
        }

        const sharedChipHtml = step.shared === true
          ? `<span class="detail-step-shared" title="Shared prerequisite step">SHARED</span>`
          : '';
        const callHtml = step.call
          ? `<code class="detail-step-call font-mono">${UIUtils.escapeHTML(step.call)}</code>`
          : '';
        const durText = UIUtils.formatDuration(step.durationMs);

        return `
          <li class="detail-step ${stateCls}">
            <span class="detail-step-marker" aria-hidden="true"><span>${markerHtml}</span></span>
            <div class="detail-step-main">
              <div class="detail-step-title">
                ${numHtml}
                <span class="detail-step-phase">${UIUtils.escapeHTML(stepNameStr)}</span>
                ${actorBadgeHtml}
                ${sharedChipHtml}
                ${callHtml}
              </div>
            </div>
            <div class="detail-step-duration num">${UIUtils.escapeHTML(durText)}</div>
            <div class="detail-step-status">${UIUtils.escapeHTML(statusLabel)}</div>
          </li>
        `;
      }).join('');

      const lifecycleMeta = plugin.getLifecycleMeta(steps);

      stepsSection.innerHTML = `
        <div class="detail-section-head">
          <h3 class="detail-section-title">Lifecycle</h3>
          <span class="detail-section-meta">${UIUtils.escapeHTML(lifecycleMeta)}</span>
        </div>
        <div class="detail-card">
          <ol class="detail-steps">${stepsHtml}</ol>
        </div>
      `;
      rootContainer.appendChild(stepsSection);
    }

    const logsSlot = document.createElement('div');
    logsSlot.dataset.role = 'logs-slot';
    rootContainer.appendChild(logsSlot);

    const artifactsSlot = document.createElement('div');
    artifactsSlot.dataset.role = 'artifacts-slot';
    rootContainer.appendChild(artifactsSlot);

    const propsEntries = res.properties && typeof res.properties === 'object'
      ? Object.entries(res.properties)
      : [];
    if (propsEntries.length > 0) {
      const propsSection = document.createElement('section');
      propsSection.className = 'detail-section';
      propsSection.setAttribute('aria-label', 'Reported properties');
      propsSection.innerHTML = `
        <div class="detail-section-head">
          <div class="detail-section-head-left">
            <h3 class="detail-section-title">Properties</h3>
            <span class="detail-section-meta">${propsEntries.length}</span>
          </div>
        </div>
        <div class="detail-card">
          <table class="detail-props-table">
            <thead>
              <tr>
                <th scope="col" class="text-left" style="width: 35%;">Property</th>
                <th scope="col" class="text-left">Value</th>
              </tr>
            </thead>
            <tbody>
              ${propsEntries.map(([k, v]) => `
                <tr>
                  <td class="font-mono font-medium text-gray-800">${UIUtils.escapeHTML(k)}</td>
                  <td class="font-mono text-gray-700">${UIUtils.escapeHTML(v)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
      rootContainer.appendChild(propsSection);
    }

    grid.appendChild(rootContainer);

    const renderLogsAndArtifacts = (detail, envDetail, isLoadingLogs) => {
      this.renderLogsSection(app, logsSlot, res, descriptor, detail, isLoadingLogs);
      this.renderArtifactsAndConfigSection(app, artifactsSlot, res, descriptor, env, envDetail);
    };

    const needTestLoad = Boolean(res.detailId && !currentLazyDetail);
    const needEnvLoad = Boolean(shouldLoadEnv && !currentEnvDetail);

    if (needTestLoad || needEnvLoad) {
      renderLogsAndArtifacts(currentLazyDetail, currentEnvDetail, needTestLoad);
      Promise.all([
        needTestLoad ? app.loadLazyDetail(res.detailId) : Promise.resolve(currentLazyDetail),
        needEnvLoad ? app.loadLazyDetail(env.envId) : Promise.resolve(currentEnvDetail),
      ]).then(([loadedDetail, loadedEnvDetail]) => {
        currentLazyDetail = loadedDetail;
        currentEnvDetail = loadedEnvDetail;
        if (!grid.contains(rootContainer)) return;
        renderLogsAndArtifacts(loadedDetail, loadedEnvDetail, false);
      });
    } else {
      renderLogsAndArtifacts(currentLazyDetail, currentEnvDetail, false);
    }
  },

  renderLogsSection(app, container, res, descriptor, detail, isLoading) {
    container.innerHTML = '';
    const logMeta = Array.isArray(res.logMeta) ? res.logMeta : [];
    const plugin = this.resolvePlugin(res, descriptor);

    if (isLoading) {
      const loadingTitle = logMeta.length === 1 && logMeta[0] && logMeta[0].title ? logMeta[0].title : 'Logs';
      container.innerHTML = `
        <section class="detail-section" aria-label="Logs">
          <div class="detail-section-head">
            <div class="detail-section-head-left">
              <h3 class="detail-section-title">${UIUtils.escapeHTML(loadingTitle)}</h3>
              <span class="detail-section-meta">Loading details...</span>
            </div>
          </div>
          <div class="detail-card p-4 text-sm text-gray-500">Loading log streams...</div>
        </section>
      `;
      return;
    }

    const rawStreams = [];
    const splitLogLines = (txt) => {
      if (!txt) return [];
      return String(txt).replace(/\r?\n$/, '').split(/\r?\n/);
    };
    const countLines = (txt) => splitLogLines(txt).length;

    if (detail) {
      if (Array.isArray(detail.logs)) {
        detail.logs.forEach(entry => {
          if (!entry) return;
          const content = entry.content || '';
          if (!content && !entry.artifactPath) return;
          const truncationSuffix = entry.previewTruncated
            ? ' · Inline preview truncated — download full file'
            : (entry.capturedTruncated ? ' · Capped at capture time' : '');
          rawStreams.push({
            title: entry.title || 'Log',
            content,
            lineCount: entry.lineCount !== null && entry.lineCount !== undefined ? entry.lineCount : countLines(content),
            truncationSuffix,
            artifactPath: entry.artifactPath || '',
          });
        });
      }
      if (detail.systemOut) {
        rawStreams.push({
          title: 'Standard output',
          content: detail.systemOut,
          lineCount: countLines(detail.systemOut),
          truncationSuffix: Boolean(detail.systemOutTruncated) ? ' · Truncated' : '',
          artifactPath: '',
        });
      }
      if (detail.systemErr) {
        rawStreams.push({
          title: 'Error output',
          content: detail.systemErr,
          lineCount: countLines(detail.systemErr),
          truncationSuffix: Boolean(detail.systemErrTruncated) ? ' · Truncated' : '',
          artifactPath: '',
        });
      }
    }

    const streams = plugin.filterLogStreams(rawStreams, detail, res);

    if (streams.length === 0) {
      if (logMeta.length === 0) return;
      const metaItemsHtml = logMeta.map(m => {
        const safeHref = UIUtils.sanitizeUrl(m.artifactPath, false);
        return `
          <div class="detail-artifact-block">
            <div class="detail-artifact-head" style="margin-bottom: 0;">
              <div class="detail-artifact-title">
                <span>${UIUtils.escapeHTML(m.title || 'Log file')}</span>
                ${m.lineCount ? `<span class="text-xs text-gray-600">(${UIUtils.escapeHTML(m.lineCount)} lines)</span>` : ''}
              </div>
              ${safeHref ? `<a class="copy-btn" href="${UIUtils.escapeHTML(safeHref)}" download>Download</a>` : ''}
            </div>
          </div>
        `;
      }).join('');
      container.innerHTML = `
        <section class="detail-section" aria-label="Logs">
          <div class="detail-section-head">
            <div class="detail-section-head-left">
              <h3 class="detail-section-title">Logs</h3>
              <span class="detail-section-meta">${logMeta.length} file${logMeta.length === 1 ? '' : 's'}</span>
            </div>
          </div>
          <div class="detail-card">${metaItemsHtml}</div>
        </section>
      `;
      return;
    }

    const formatLogHtml = (text) => {
      if (!text) return '<span class="text-gray-500">(empty)</span>';
      const errPattern = /(\bE\/|\bF\/|\bERROR\b|\bSEVERE\b|\bFATAL\b|Exception\b|AssertionError\b|Caused by:|\bFAILED\b|^\s+at\s+\S+)/;
      const strongPattern = /\]\s+(PASSED|FAILED)\b/;
      return splitLogLines(text).map(line => {
        const escaped = UIUtils.escapeHTML(line);
        if (errPattern.test(line)) {
          return `<span class="detail-log-err">${escaped}</span>`;
        }
        if (strongPattern.test(line)) {
          return `<span class="detail-log-strong">${escaped}</span>`;
        }
        return escaped;
      }).join('\n');
    };

    let activeTabIdx = 0;
    const section = document.createElement('section');
    section.className = 'detail-section';
    section.setAttribute('aria-label', 'Logs');

    const renderActiveStream = () => {
      const current = streams[activeTabIdx] || streams[0];
      const safeDownload = UIUtils.sanitizeUrl(current.artifactPath, false);
      const lineCountText = `${current.lineCount} line${current.lineCount === 1 ? '' : 's'}${current.truncationSuffix || ''}`;

      if (streams.length === 1) {
        section.innerHTML = `
          <div class="detail-section-head">
            <div class="detail-section-head-left">
              <h3 class="detail-section-title">${UIUtils.escapeHTML(current.title)}</h3>
              <span class="detail-section-meta" data-role="log-stream-meta">${UIUtils.escapeHTML(lineCountText)}</span>
            </div>
            <div class="flex items-center gap-2">
              <button type="button" class="copy-btn detail-mini-btn" data-action="copy-log">Copy</button>
              ${safeDownload ? `<a class="copy-btn detail-mini-btn" href="${UIUtils.escapeHTML(safeDownload)}" download>Download</a>` : ''}
            </div>
          </div>
          <div class="detail-card">
            <pre class="detail-log font-mono" tabindex="0">${formatLogHtml(current.content)}</pre>
          </div>
        `;
      } else {
        const tabsHtml = `
          <div class="detail-log-tabs" role="tablist" aria-label="Log streams">
            ${streams.map((s, idx) => `
              <button type="button" role="tab" aria-selected="${idx === activeTabIdx ? 'true' : 'false'}" class="detail-log-tab ${idx === activeTabIdx ? 'active' : ''}" data-log-tab="${idx}">
                <span>${UIUtils.escapeHTML(s.title)}</span>
                <span class="detail-log-tab-count">(${UIUtils.escapeHTML(s.lineCount)})</span>
              </button>
            `).join('')}
          </div>
        `;
        const metaText = `${current.title} · ${lineCountText}`;
        section.innerHTML = `
          <div class="detail-section-head">
            <div class="detail-section-head-left">
              <h3 class="detail-section-title">Logs</h3>
              <span class="detail-section-meta">${streams.length} streams</span>
            </div>
          </div>
          <div class="detail-card">
            ${tabsHtml}
            <div class="detail-log-toolbar">
              <span data-role="log-stream-meta">${UIUtils.escapeHTML(metaText)}</span>
              <div class="flex items-center gap-2">
                <button type="button" class="copy-btn" data-action="copy-log">Copy</button>
                ${safeDownload ? `<a class="copy-btn" href="${UIUtils.escapeHTML(safeDownload)}" download>Download</a>` : ''}
              </div>
            </div>
            <pre class="detail-log font-mono" tabindex="0">${formatLogHtml(current.content)}</pre>
          </div>
        `;
      }

      section.querySelectorAll('button[data-log-tab]').forEach(tabBtn => {
        tabBtn.addEventListener('click', () => {
          activeTabIdx = Number(tabBtn.dataset.logTab) || 0;
          renderActiveStream();
        });
      });

      const copyLogBtn = section.querySelector('[data-action="copy-log"]');
      if (copyLogBtn) {
        copyLogBtn.addEventListener('click', () => {
          app.copyToClipboard(current.content || '', copyLogBtn, `${current.title} copied to clipboard`);
        });
      }
    };

    renderActiveStream();
    container.appendChild(section);
  },

  renderArtifactsAndConfigSection(app, container, res, descriptor, env, envDetail = null) {
    container.innerHTML = '';
    const artifacts = Array.isArray(res.artifacts) ? res.artifacts : [];
    const plugin = this.resolvePlugin(res, descriptor);
    const hasArtifacts = artifacts.length > 0;
    const hasConfigFiles = Boolean(plugin.hasConfigData(env, envDetail, res));

    if (!hasArtifacts && !hasConfigFiles) return;

    const blocksHtml = [];
    const defaultArtifactTitle = plugin.getDefaultArtifactTitle({ hasArtifacts, hasConfigFiles });

    artifacts.forEach(art => {
      if (!art) return;
      const safePath = UIUtils.sanitizeUrl(art.path, false);
      const valEntries = art.values && typeof art.values === 'object' ? Object.entries(art.values) : [];
      const kvGridHtml = valEntries.length > 0 ? `
        <div class="detail-kv-grid">
          ${valEntries.map(([k, v]) => `
            <div class="detail-kv-card">
              <div class="detail-kv-key font-mono">${UIUtils.escapeHTML(k)}</div>
              <div class="detail-kv-val num">${UIUtils.escapeHTML(v)}</div>
            </div>
          `).join('')}
        </div>
      ` : '';

      blocksHtml.push(`
        <div class="detail-artifact-block">
          <div class="detail-artifact-head">
            <div class="detail-artifact-title">${UIUtils.escapeHTML(art.name || defaultArtifactTitle)}</div>
            ${safePath ? `<a class="copy-btn" href="${UIUtils.escapeHTML(safePath)}" download>Download</a>` : ''}
          </div>
          ${kvGridHtml}
        </div>
      `);
    });

    let copyTargets = [];
    if (hasConfigFiles) {
      const rendered = plugin.renderConfigBlocks({ res, env, envDetail, app, UIUtils }) || {};
      if (Array.isArray(rendered.blocksHtml)) {
        blocksHtml.push(...rendered.blocksHtml);
      }
      if (Array.isArray(rendered.copyTargets)) {
        copyTargets = rendered.copyTargets;
      }
    }

    if (blocksHtml.length === 0) return;

    const sectionTitle = plugin.getArtifactsSectionTitle({ hasArtifacts, hasConfigFiles });

    const section = document.createElement('section');
    section.className = 'detail-section';
    section.setAttribute('aria-label', sectionTitle);
    section.innerHTML = `
      <div class="detail-section-head">
        <div class="detail-section-head-left">
          <h3 class="detail-section-title">${UIUtils.escapeHTML(sectionTitle)}</h3>
        </div>
      </div>
      <div class="detail-card">${blocksHtml.join('')}</div>
    `;

    section.querySelectorAll('button[data-copy-config]').forEach(btn => {
      btn.addEventListener('click', () => {
        const idx = Number(btn.dataset.copyConfig) || 0;
        const target = copyTargets[idx];
        if (target && target.content) {
          app.copyToClipboard(target.content, btn, `${target.label || 'Configuration'} copied to clipboard`);
        }
      });
    });

    container.appendChild(section);
  },

  buildCopyContextMarkdown(app, testCase, activeRun, context = {}, detail = null, envDetail = null) {
    const res = (activeRun && activeRun.res) || {};
    const env = (activeRun && activeRun.env) || null;
    const descriptor = (activeRun && activeRun.descriptor)
      || (activeRun && activeRun.suiteName ? (app.state.suiteDescriptors || {})[activeRun.suiteName] : null);
    const plugin = this.resolvePlugin(res, descriptor);
    const loc = res.sourceLocation || null;
    const methodName = (loc && loc.methodName) || res.methodName || (testCase && testCase.name) || 'UnknownTest';
    const className = (loc && loc.className)
      || (context.packageName && context.className && !context.className.startsWith(context.packageName)
        ? `${context.packageName}.${context.className}`
        : (context.className || ''));
    const fileLine = loc && loc.file
      ? (loc.line !== null && loc.line !== undefined ? `${loc.file}:${loc.line}` : loc.file)
      : '';
    const normStatus = UIUtils.normalizeStatus(res.status).toUpperCase() || 'UNKNOWN';
    const durationStr = UIUtils.formatDuration(res.durationMs);

    const lines = [];
    lines.push(`## Test Report Context: ${methodName}`);
    lines.push('');
    lines.push(`- **Test**: \`${className ? `${className}#${methodName}` : methodName}\`${fileLine ? ` (\`${fileLine}\`)` : ''}`);
    const copyFailedStepTarget = UIUtils.getFailedStepTarget(res);
    const failedStepSuffix = copyFailedStepTarget
      ? ` (Failed at ${copyFailedStepTarget})`
      : '';
    lines.push(`- **Status**: ${normStatus}${durationStr !== '—' ? ` · ${durationStr}` : ''}${failedStepSuffix}`);
    if (activeRun && (activeRun.suiteName || activeRun.variantName)) {
      const suiteDisplay = (descriptor && descriptor.title) || activeRun.suiteName || '';
      lines.push(`- **Suite / Variant**: ${suiteDisplay} / ${activeRun.variantName || '—'}${context.moduleName ? ` (${context.moduleName})` : ''}`);
    }

    const summaryCols = descriptor && Array.isArray(descriptor.summaryColumns) ? descriptor.summaryColumns : [];
    app.getAllSummaryAttrs(res).forEach(attr => {
      const colDef = summaryCols.find(c => c && c.key === attr.key);
      const label = (colDef && colDef.label) || attr.label || attr.key;
      const val = attr.displayValue || attr.value;
      lines.push(`- **${label}**: ${val}${attr.value && attr.value !== val ? ` (\`${attr.value}\`)` : ''}`);
    });

    if (env) {
      lines.push('');
      lines.push('### App & Build Environment');
      if (env.applicationName || env.applicationId) {
        lines.push(`- **App**: ${[env.applicationName, env.applicationId ? `\`${env.applicationId}\`` : ''].filter(Boolean).join(' ')}`);
      }
      if (env.versionName || (env.versionCode !== null && env.versionCode !== undefined)) {
        const ver = env.versionName && env.versionCode !== null && env.versionCode !== undefined
          ? `${env.versionName} (${env.versionCode})`
          : (env.versionName || String(env.versionCode));
        lines.push(`- **Version**: ${ver}`);
      }
      const toolchain = [
        env.agpVersion ? `AGP ${env.agpVersion}` : '',
        env.gradleVersion ? `Gradle ${env.gradleVersion}` : '',
        env.testLibraryVersion ? `Test library ${env.testLibraryVersion}` : '',
      ].filter(Boolean);
      if (toolchain.length > 0) lines.push(`- **Toolchain**: ${toolchain.join(' · ')}`);
      if (env.runner) lines.push(`- **Runner**: \`${env.runner}\``);
    }

    if (Array.isArray(res.actors) && res.actors.length > 0) {
      lines.push('');
      lines.push('### Devices');
      res.actors.forEach(a => {
        const details = [
          a.api ? `API ${a.api}` : '',
          a.image || '',
          a.playServices ? `Play services ${a.playServices}` : '',
          a.serial ? `serial ${a.serial}` : '',
        ].filter(Boolean).join(', ');
        lines.push(`- **[${String(a.role || 'DEVICE').toUpperCase()}]** ${a.name || a.id || plugin.defaultActorLabel}${details ? ` (${details})` : ''}`);
      });
    }

    const callout = res.callout || null;
    const stackTrace = (activeRun && activeRun.stackTrace) || '';
    if (callout || stackTrace) {
      lines.push('');
      lines.push('### Failure');
      if (callout && callout.title) lines.push(`- **Summary**: ${callout.title}`);
      if (callout && callout.message) lines.push(`- **Message**: ${callout.message}`);
      if (callout && callout.likelyCause) lines.push(`- **Likely cause**: ${callout.likelyCause}`);
      if (stackTrace) {
        const excerpt = stackTrace.split(/\r?\n/).slice(0, 25).join('\n');
        lines.push('');
        SuiteRegistry.pushFencedBlock(lines, excerpt, 'text');
      }
    }

    if (Array.isArray(res.steps) && res.steps.length > 0) {
      lines.push('');
      lines.push('### Lifecycle');
      res.steps.forEach(s => {
        const st = UIUtils.normalizeStatus(s.status).toUpperCase();
        const num = s.n === null || s.n === undefined ? 'Prep' : `${s.n}.`;
        const onPart = s.on ? ` [${String(s.on).toUpperCase()}]` : '';
        const sharedPart = s.shared === true ? ' [SHARED]' : '';
        const callPart = s.call ? ` \`${s.call}\`` : '';
        lines.push(`- **${st}** ${num} ${s.name || 'Step'}${onPart}${sharedPart}${callPart} (${UIUtils.formatDuration(s.durationMs)})`);
      });
    }

    const artifacts = Array.isArray(res.artifacts) ? res.artifacts : [];
    const hasConfigFiles = Boolean(plugin.hasConfigData(env, envDetail, res));

    if (artifacts.length > 0 || hasConfigFiles) {
      lines.push('');
      lines.push(plugin.copyContextArtifactsHeading({ hasArtifacts: artifacts.length > 0, hasConfigFiles }));
      artifacts.forEach(art => {
        const kv = art.values && typeof art.values === 'object'
          ? Object.entries(art.values).map(([k, v]) => `${k}: ${v}`).join(', ')
          : '';
        lines.push(`- **${art.name || 'Artifact'}**: ${kv || art.path || 'attached'}`);
      });
      if (hasConfigFiles) {
        plugin.appendCopyContextConfig(lines, { res, env, envDetail, app });
      }
    }

    if (res.properties && typeof res.properties === 'object' && Object.keys(res.properties).length > 0) {
      lines.push('');
      lines.push('### Properties');
      Object.entries(res.properties).forEach(([k, v]) => {
        lines.push(`- \`${k}\`: ${v}`);
      });
    }

    if (detail) {
      const logSources = [];
      if (Array.isArray(detail.logs)) {
        detail.logs.forEach(l => {
          if (l && l.content) logSources.push({ title: l.title || 'Log', content: l.content });
        });
      }
      if (detail.systemErr) logSources.push({ title: 'Error output', content: detail.systemErr });
      if (detail.systemOut && logSources.length === 0) logSources.push({ title: 'Standard output', content: detail.systemOut });

      if (logSources.length > 0) {
        const primary = logSources[0];
        const allLines = String(primary.content).split(/\r?\n/);
        const errLines = allLines.filter(l => /(\bE\/|\bF\/|\bERROR\b|\bSEVERE\b|\bFATAL\b|Exception\b|AssertionError\b|Caused by:)/.test(l));
        const selectedLines = (errLines.length > 0 ? errLines.slice(-15) : allLines.slice(-15)).join('\n').trim();
        if (selectedLines) {
          lines.push('');
          lines.push(`### Log Excerpt (${primary.title})`);
          SuiteRegistry.pushFencedBlock(lines, selectedLines, 'text');
        }
      }
    }

    return lines.join('\n');
  },
};

globalThis.RichTestDetailView = RichTestDetailView;
