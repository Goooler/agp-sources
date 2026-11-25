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
 * Backup & Restore test suite UI plugin (`adapterId = 'backupRestore'`).
 * Renders suite-level backup rules in the environment bar and displays only the active implicated
 * rule file on individual test detail pages when a backup rule caused a failure.
 */
const BackupSuitePlugin = {
  id: 'backupRestore',
  useRichSingleSuiteList: true,
  defaultActorLabel: 'Android Device',

  getActorRoleClass(roleUpper) {
    if (roleUpper === 'SOURCE') return 'role-source';
    if (roleUpper === 'TARGET') return 'role-target';
    return '';
  },

  getSummaryColumnClass(col) {
    if (col && col.key === 'transport') return 'col-transport';
    if (col && col.key === 'devices') return 'col-devices';
    return 'col-dynamic';
  },

  getStepActorRoleClass(roleUpper) {
    if (roleUpper === 'TARGET') return 'role-target';
    if (roleUpper === 'SOURCE') return '';
    return 'role-device';
  },

  formatManifestAttribute(file) {
    if (!file) return '';
    const attrName = file.key === 'fullBackupContent'
      ? 'android:fullBackupContent'
      : (file.key === 'dataExtractionRules' ? 'android:dataExtractionRules' : (file.key || ''));
    if (attrName && file.reference) {
      return `${attrName}="${file.reference}"`;
    }
    return attrName || file.reference || '';
  },

  getDefaultArtifactTitle() {
    return 'Backup file summary';
  },

  getSummaryKickerLabel(_suiteTitle) {
    return 'Test';
  },

  getCrossDeviceFactSub(serialsText) {
    return serialsText
      ? `${serialsText} · Backup on source, restore on target`
      : 'Backup on source, restore on target';
  },

  getLifecycleMeta(steps) {
    const list = Array.isArray(steps) ? steps : [];
    const numbered = list.filter(s => s && s.n !== null && s.n !== undefined);
    const count = numbered.length > 0 ? numbered.length : list.length;
    return `${count} step${count === 1 ? '' : 's'} · host-driven`;
  },

  filterLogStreams(streams, detail, _res) {
    if (!Array.isArray(streams) || streams.length <= 1) return streams;
    const hasHostLog = Boolean(
      detail &&
      Array.isArray(detail.logs) &&
      detail.logs.some(l => l && String(l.title || '').toLowerCase() === 'host log' && l.content)
    );
    if (!hasHostLog) return streams;
    return streams.filter(s => {
      if (!s || s.title !== 'Error output') return true;
      const lines = String(s.content || '').split(/\r?\n/).map(l => l.trim()).filter(Boolean);
      if (lines.length === 0) return false;
      const isDuplicateJulStderr = lines.every(l =>
        /^(?:[A-Z][a-z]{2}\s+\d{1,2},\s+\d{4}\s+.*\bandroidx\.test\.backup\.|(?:INFO|WARNING|FINE|FINER|FINEST|CONFIG):\s)/.test(l)
      );
      return !isDuplicateJulStderr;
    });
  },

  isRuleImplicated(res) {
    return Boolean(
      res &&
      res.callout &&
      res.callout.likelyCause &&
      String(res.callout.likelyCause).trim().length > 0
    );
  },

  extractNamedCauseFile(likelyCause) {
    if (!likelyCause) return '';
    const match = likelyCause.match(/^\s*([\w.-]+\.xml)\s+(?:excludes|sets|uses|requires)\b/i)
      || likelyCause.match(/\bin\s+([\w.-]+\.xml)\b/i);
    return match && match[1] ? match[1].toLowerCase() : '';
  },

  /**
   * Selects only the effective Android 12+ (API 31+) backup rule file(s), omitting legacy
   * `backup_rules.xml` (`fullBackupContent`) whenever `data_extraction_rules.xml` governs.
   */
  selectActiveRuleFiles(files, env, options = {}) {
    const list = Array.isArray(files) ? files.filter(Boolean) : [];
    if (list.length === 0) return [];

    const likelyCause = options.likelyCause ? String(options.likelyCause) : '';
    const namedFile = this.extractNamedCauseFile(likelyCause);
    if (namedFile) {
      if (namedFile === 'androidmanifest.xml') return [];
      const matched = list.filter(f => String(f.label || '').toLowerCase() === namedFile);
      if (matched.length > 0) return matched;
    }

    const der = list.find(f => f.key === 'dataExtractionRules');
    const fbc = list.find(f => f.key === 'fullBackupContent');
    const others = list.filter(f => f.key !== 'dataExtractionRules' && f.key !== 'fullBackupContent');
    const api31PlusTarget = !env
      || env.targetSdkVersion === null
      || env.targetSdkVersion === undefined
      || Number(env.targetSdkVersion) >= 31;

    if (der) {
      if (der.content) {
        const uncommented = String(der.content).replace(/<!--[\s\S]*?-->/g, '');
        const hasCloud = /<\s*cloud-backup\b/i.test(uncommented);
        const hasD2d = /<\s*device-transfer\b/i.test(uncommented);
        if (options.transport === 'DEVICE_TO_DEVICE') {
          if (hasD2d) return [der, ...others];
          if (api31PlusTarget) return [...others];
          return fbc ? [fbc, ...others] : [...others];
        }
        if (options.transport) {
          if (hasCloud) return [der, ...others];
          return fbc ? [fbc, ...others] : [der, ...others];
        }
        const needsFbcFallback = !hasCloud || (!hasD2d && !api31PlusTarget);
        return needsFbcFallback && fbc ? [der, fbc, ...others] : [der, ...others];
      }
      return [der, ...others];
    }

    if (options.transport === 'DEVICE_TO_DEVICE' && api31PlusTarget) {
      return [...others];
    }
    return fbc ? [fbc, ...others] : [...others];
  },

  resolveActiveRuleSets(env, envDetail, options = {}) {
    const rawLazy = (envDetail && Array.isArray(envDetail.files)) ? envDetail.files.filter(Boolean) : [];
    const rawConfig = (env && Array.isArray(env.configFiles)) ? env.configFiles.filter(Boolean) : [];
    const mergedCandidates = [];
    const seenKeys = new Set();
    rawLazy.forEach(f => {
      mergedCandidates.push(f);
      if (f.key) seenKeys.add(f.key);
    });
    rawConfig.forEach(f => {
      if (f.key && seenKeys.has(f.key)) return;
      mergedCandidates.push(f);
    });
    const activeMerged = this.selectActiveRuleFiles(mergedCandidates, env, options);
    const activeSet = new Set(activeMerged);
    const activeKeys = new Set(activeMerged.map(f => f.key).filter(Boolean));
    const lazyFiles = rawLazy.filter(f => activeSet.has(f) || (f.key && activeKeys.has(f.key)));
    const configFiles = rawConfig.filter(f => activeSet.has(f) || (f.key && activeKeys.has(f.key)));
    return { activeMerged, lazyFiles, configFiles };
  },

  getRawAllowBackup(env) {
    const rawAllow = env && env.attributes ? env.attributes.allowBackup : undefined;
    return rawAllow !== undefined && rawAllow !== null && rawAllow !== '' ? String(rawAllow) : null;
  },

  buildAllowBackupBadgeHtml(env, res, app, UIUtils) {
    const allowBackupVal = this.getRawAllowBackup(env);
    if (allowBackupVal === null) return '';
    const transportAttr = res && app ? app.getSummaryAttr(res, 'transport') : null;
    const rawTransport = transportAttr ? transportAttr.value : '';
    const isAllowBackupFalse = allowBackupVal.toLowerCase() === 'false';
    const isNeutralAllowBackupFalse = Boolean(
      rawTransport === 'DEVICE_TO_DEVICE' &&
      env &&
      env.targetSdkVersion !== null &&
      env.targetSdkVersion !== undefined &&
      Number(env.targetSdkVersion) >= 31 &&
      !(res && res.callout && /allowBackup/i.test(`${res.callout.message || ''} ${res.callout.likelyCause || ''}`))
    );
    const tone = isAllowBackupFalse ? (isNeutralAllowBackupFalse ? 'neutral' : 'fail') : 'pass';
    return `<span class="status-badge-lg status-badge-sm ${tone}">android:allowBackup="${UIUtils.escapeHTML(allowBackupVal)}"</span>`;
  },

  renderRuleBlocks({ lazyFiles, configFiles, allowBadgeHtml, likelyCause, UIUtils }) {
    const blocks = [];
    const copyTargets = [];
    const causeFile = this.extractNamedCauseFile(likelyCause);
    const causeSectionMatch = likelyCause ? likelyCause.match(/<\s*(cloud-backup|device-transfer)\s*>/i) : null;
    const causeSection = causeSectionMatch ? causeSectionMatch[1].toLowerCase() : '';
    const causeDomainMatch = likelyCause ? likelyCause.match(/domain\s*=\s*"([^"]+)"/i) : null;
    const causeDomain = causeDomainMatch ? causeDomainMatch[1] : '';
    const causePathMatch = likelyCause ? likelyCause.match(/path\s*=\s*"([^"]+)"/i) : null;
    const causePath = causePathMatch ? causePathMatch[1] : '';
    const hasExcludeCause = Boolean(
      likelyCause && (/\bexcludes?\b|\bexcluded\b/i.test(likelyCause) || /<\s*exclude\b/i.test(likelyCause))
    );

    const formatRuleXml = (xml, ruleLabel) => {
      if (!xml) return '';
      const fileMatches = !causeFile || !ruleLabel || String(ruleLabel).toLowerCase() === causeFile;
      let activeSection = '';
      let inXmlComment = false;
      const stripLineComments = (rawLine) => {
        let rest = String(rawLine);
        let out = '';
        while (rest.length > 0) {
          if (inXmlComment) {
            const closeIdx = rest.indexOf('-->');
            if (closeIdx === -1) return '';
            rest = rest.slice(closeIdx + 3);
            inXmlComment = false;
          } else {
            const openIdx = rest.indexOf('<!--');
            if (openIdx === -1) {
              out += rest;
              break;
            }
            out += rest.slice(0, openIdx);
            const closeIdx = rest.indexOf('-->', openIdx + 4);
            if (closeIdx === -1) {
              inXmlComment = true;
              break;
            }
            rest = rest.slice(closeIdx + 3);
          }
        }
        return out;
      };
      return String(xml).split(/\r?\n/).map(line => {
        const uncommented = stripLineComments(line);
        const openSec = uncommented.match(/<\s*(cloud-backup|device-transfer)\b/i);
        if (openSec) activeSection = openSec[1].toLowerCase();
        const escaped = UIUtils.escapeHTML(line);
        let shouldHighlight = false;
        if (hasExcludeCause && fileMatches && /<\s*exclude\b/i.test(uncommented)) {
          const sectionOk = !causeSection || activeSection === causeSection;
          const domainOk = !causeDomain || uncommented.includes(`domain="${causeDomain}"`) || uncommented.includes(`domain='${causeDomain}'`);
          const pathOk = !causePath || uncommented.includes(`path="${causePath}"`) || uncommented.includes(`path='${causePath}'`);
          shouldHighlight = sectionOk && domainOk && pathOk;
        }
        if (/<\s*\/\s*(cloud-backup|device-transfer)\s*>/i.test(uncommented)) activeSection = '';
        return shouldHighlight ? `<span class="detail-rule-highlight">${escaped}</span>` : escaped;
      }).join('\n');
    };

    const lazyKeys = new Set(lazyFiles.filter(Boolean).map(f => f.key).filter(Boolean));
    const extraConfigFiles = configFiles.filter(f => f && (!f.key || !lazyKeys.has(f.key)));

    if (lazyFiles.length > 0) {
      lazyFiles.forEach((rule, idx) => {
        if (!rule) return;
        const safeRulePath = UIUtils.sanitizeUrl(rule.artifactPath, false);
        const attrLabel = this.formatManifestAttribute(rule);
        const copyIdx = copyTargets.length;
        if (rule.content) {
          copyTargets.push({ label: rule.label || 'Backup rules', content: rule.content });
        }
        blocks.push(`
          <div class="detail-artifact-block" data-rule-idx="${idx}">
            <div class="detail-artifact-head">
              <div class="detail-artifact-title">
                <span class="font-mono">${UIUtils.escapeHTML(rule.label || 'backup_rules.xml')}</span>
                ${attrLabel ? `<span class="text-xs text-gray-600 font-mono">(${UIUtils.escapeHTML(attrLabel)})</span>` : ''}
                ${idx === 0 ? allowBadgeHtml : ''}
              </div>
              <div class="flex items-center gap-2">
                ${rule.content ? `<button type="button" class="copy-btn" data-copy-config="${copyIdx}">Copy XML</button>` : ''}
                ${safeRulePath ? `<a class="copy-btn" href="${UIUtils.escapeHTML(safeRulePath)}" download>Download</a>` : ''}
              </div>
            </div>
            ${rule.content ? `<pre class="detail-rule-xml font-mono" tabindex="0">${formatRuleXml(rule.content, rule.label)}</pre>` : ''}
          </div>
        `);
      });
    }

    if ((lazyFiles.length === 0 && allowBadgeHtml) || extraConfigFiles.length > 0) {
      const badgeInCard = lazyFiles.length === 0 ? allowBadgeHtml : '';
      const fallbackLinks = extraConfigFiles.map(file => {
        const safeHref = UIUtils.sanitizeUrl(file.artifactPath, false);
        const keyHeading = file.key === 'fullBackupContent'
          ? 'android:fullBackupContent'
          : (file.key === 'dataExtractionRules' ? 'android:dataExtractionRules' : (file.label || file.key || 'Rule'));
        return `
          <div class="detail-kv-card">
            <div class="detail-kv-key font-mono">${UIUtils.escapeHTML(keyHeading)}</div>
            <div class="detail-kv-val font-mono text-sm flex items-center justify-between gap-2">
              <span>${UIUtils.escapeHTML(file.reference || file.label || '')}</span>
              ${safeHref ? `<a class="copy-btn" href="${UIUtils.escapeHTML(safeHref)}" download>Download</a>` : ''}
            </div>
          </div>
        `;
      });

      blocks.push(`
        <div class="detail-artifact-block">
          <div class="detail-artifact-head">
            <div class="detail-artifact-title">
              <span>Manifest backup configuration</span>
              ${badgeInCard}
            </div>
          </div>
          ${fallbackLinks.length > 0 ? `<div class="detail-kv-grid">${fallbackLinks.join('')}</div>` : ''}
        </div>
      `);
    }

    return { blocksHtml: blocks, copyTargets };
  },

  hasSuiteEnvBarExtras(env, app) {
    if (!env) return false;
    if (this.getRawAllowBackup(env) !== null) return true;
    const cached = env.envId && app ? app.getCachedLazyDetail(env.envId) : null;
    const { activeMerged } = this.resolveActiveRuleSets(env, cached);
    return activeMerged.length > 0;
  },

  mountSuiteEnvBarExtras(bar, env, _desc, app, UIUtils) {
    if (!bar || !env) return;
    const allowBackupVal = this.getRawAllowBackup(env);
    const itemsEl = bar.querySelector('.suite-env-items');
    if (itemsEl && allowBackupVal !== null && allowBackupVal.toLowerCase() === 'false') {
      const sep = itemsEl.innerHTML.trim() ? '<span class="suite-env-sep" aria-hidden="true">·</span>' : '';
      itemsEl.innerHTML = `${itemsEl.innerHTML}${sep}<span class="status-badge-lg status-badge-sm fail">android:allowBackup="false"</span>`;
    }

    const cachedDetail = env.envId && app ? app.getCachedLazyDetail(env.envId) : null;
    const { activeMerged: initialActive } = this.resolveActiveRuleSets(env, cachedDetail);
    if (initialActive.length === 0) return;

    const actionsEl = bar.querySelector('.suite-env-actions');
    if (!actionsEl) return;

    const formatBtnLabel = (files, isOpen) => {
      const noun = files.length === 1
        ? `Backup rules (${files[0].label || 'rules.xml'})`
        : `Backup rules (${files.length} files)`;
      return `${UIUtils.escapeHTML(noun)} <span aria-hidden="true">${isOpen ? '▴' : '▾'}</span>`;
    };

    let isOpen = Boolean(app && app.state && env.envId && app.state.suiteRulesOpenEnvId === env.envId);
    actionsEl.innerHTML = `<button type="button" class="suite-env-docs-link suite-env-rules-btn" data-action="toggle-suite-rules" aria-expanded="${isOpen ? 'true' : 'false'}">${formatBtnLabel(initialActive, isOpen)}</button>${actionsEl.innerHTML}`;

    const toggleBtn = actionsEl.querySelector('[data-action="toggle-suite-rules"]');
    const doc = bar.ownerDocument || document;
    const drawer = doc.createElement('div');
    drawer.className = `suite-env-rules-drawer${isOpen ? '' : ' hidden'}`;
    drawer.setAttribute('data-role', 'suite-rules-drawer');
    bar.appendChild(drawer);

    const renderDrawerContent = (loadedEnvDetail) => {
      const { activeMerged, lazyFiles: activeLazy, configFiles: activeConfig } =
        this.resolveActiveRuleSets(env, loadedEnvDetail);
      if (toggleBtn && activeMerged.length > 0) {
        toggleBtn.innerHTML = formatBtnLabel(activeMerged, isOpen);
      }
      const allowBadgeHtml = this.buildAllowBackupBadgeHtml(env, null, app, UIUtils);
      const rendered = this.renderRuleBlocks({
        lazyFiles: activeLazy,
        configFiles: activeConfig,
        allowBadgeHtml,
        likelyCause: '',
        UIUtils,
      });
      drawer.innerHTML = `<div class="suite-env-rules-card detail-card">${rendered.blocksHtml.join('')}</div>`;
      drawer.querySelectorAll('button[data-copy-config]').forEach(btn => {
        btn.addEventListener('click', () => {
          const idx = Number(btn.dataset.copyConfig) || 0;
          const target = rendered.copyTargets[idx];
          if (target && target.content && app) {
            app.copyToClipboard(target.content, btn, `${target.label || 'Backup rules'} copied to clipboard`);
          }
        });
      });
    };

    const ensureLoadedAndRender = () => {
      const currentCached = env.envId && app ? app.getCachedLazyDetail(env.envId) : null;
      const hasLazyPaths = Array.isArray(env.configFiles) && env.configFiles.some(f => f && f.artifactPath);
      if (currentCached || !hasLazyPaths || !env.envId || !app) {
        renderDrawerContent(currentCached);
        return;
      }
      drawer.innerHTML = '<div class="suite-env-rules-card detail-card p-4 text-xs text-gray-500">Loading backup rules...</div>';
      app.loadLazyDetail(env.envId).then(loaded => {
        renderDrawerContent(loaded);
      });
    };

    if (isOpen) {
      ensureLoadedAndRender();
    }

    if (toggleBtn) {
      toggleBtn.addEventListener('click', () => {
        isOpen = !isOpen;
        if (app && app.state) {
          app.state.suiteRulesOpenEnvId = isOpen ? (env.envId || '__open__') : null;
        }
        toggleBtn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        const cachedNow = env.envId && app ? app.getCachedLazyDetail(env.envId) : null;
        const { activeMerged: labelFiles } = this.resolveActiveRuleSets(env, cachedNow);
        toggleBtn.innerHTML = formatBtnLabel(labelFiles.length > 0 ? labelFiles : initialActive, isOpen);
        if (isOpen) {
          drawer.classList.remove('hidden');
          ensureLoadedAndRender();
        } else {
          drawer.classList.add('hidden');
        }
      });
    }
  },

  needsEnvForTestDetail(env, res) {
    if (!this.isRuleImplicated(res)) return false;
    return Boolean(
      env &&
      env.envId &&
      Array.isArray(env.configFiles) &&
      env.configFiles.some(f => f && f.artifactPath)
    );
  },

  hasConfigData(env, envDetail, res) {
    if (!this.isRuleImplicated(res)) return false;
    if (this.getRawAllowBackup(env) !== null) return true;
    if (envDetail && Array.isArray(envDetail.files) && envDetail.files.length > 0) return true;
    return Boolean(env && Array.isArray(env.configFiles) && env.configFiles.length > 0);
  },

  getArtifactsSectionTitle({ hasArtifacts, hasConfigFiles }) {
    if (hasConfigFiles && hasArtifacts) return 'Backup files & rules';
    if (hasConfigFiles) return 'Backup rules';
    return 'Backup files';
  },

  renderConfigBlocks({ res, env, envDetail, app, UIUtils }) {
    if (!this.isRuleImplicated(res)) {
      return { blocksHtml: [], copyTargets: [] };
    }
    const likelyCause = res && res.callout && res.callout.likelyCause ? res.callout.likelyCause : '';
    const transportAttr = app ? app.getSummaryAttr(res, 'transport') : null;
    const rawTransport = transportAttr ? transportAttr.value : '';
    const { lazyFiles: activeLazy, configFiles: activeConfig } = this.resolveActiveRuleSets(
      env,
      envDetail,
      { transport: rawTransport, likelyCause }
    );
    const allowBadgeHtml = this.buildAllowBackupBadgeHtml(env, res, app, UIUtils);

    return this.renderRuleBlocks({
      lazyFiles: activeLazy,
      configFiles: activeConfig,
      allowBadgeHtml,
      likelyCause,
      UIUtils,
    });
  },

  copyContextArtifactsHeading({ hasConfigFiles }) {
    return hasConfigFiles ? '### Backup Files & Rules' : '### Backup Files';
  },

  appendCopyContextConfig(lines, { res, env, envDetail, app }) {
    if (!this.isRuleImplicated(res)) return;
    const likelyCause = res && res.callout && res.callout.likelyCause ? res.callout.likelyCause : '';
    const rawAllow = this.getRawAllowBackup(env);
    if (rawAllow !== null) {
      lines.push(`- **allowBackup**: \`${rawAllow}\``);
    }
    const transportAttr = app && typeof app.getSummaryAttr === 'function' ? app.getSummaryAttr(res, 'transport') : null;
    const rawTransport = transportAttr ? transportAttr.value : '';
    const { lazyFiles, configFiles } = this.resolveActiveRuleSets(
      env,
      envDetail,
      { transport: rawTransport, likelyCause }
    );
    const lazyKeys = new Set(lazyFiles.filter(Boolean).map(f => f.key).filter(Boolean));
    lazyFiles.forEach(rule => {
      if (!rule) return;
      const attrLabel = this.formatManifestAttribute(rule);
      lines.push(`- **Rule file**: \`${rule.label || 'backup_rules.xml'}\`${attrLabel ? ` (${attrLabel})` : ''}`);
      if (rule.content) {
        SuiteRegistry.pushFencedBlock(lines, String(rule.content).trim(), 'xml');
      }
    });
    configFiles.forEach(file => {
      if (!file || (file.key && lazyKeys.has(file.key))) return;
      const attrName = file.key === 'fullBackupContent'
        ? 'android:fullBackupContent'
        : (file.key === 'dataExtractionRules' ? 'android:dataExtractionRules' : (file.label || file.key || 'Rule'));
      lines.push(`- **${attrName}**: \`${file.reference || file.label || ''}\``);
    });
  },
};

if (globalThis.SuiteRegistry) {
  globalThis.SuiteRegistry.register('backupRestore', BackupSuitePlugin);
}
globalThis.BackupSuitePlugin = BackupSuitePlugin;
