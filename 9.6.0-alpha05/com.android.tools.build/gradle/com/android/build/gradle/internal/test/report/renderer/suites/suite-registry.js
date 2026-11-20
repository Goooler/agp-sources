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
 * Registry of suite-specific UI plugins keyed by `SuiteReportAdapter.adapterId`.
 *
 * Generic report rendering (matrix tables, single-suite lists, steps, logs, artifacts, and
 * environment bars) lives in `script.js` and `detail-view.js`. Suite-specific layouts or
 * configuration views live in dedicated plugins under `suites/`.
 */
const DEFAULT_SUITE_PLUGIN = Object.freeze({
  id: 'default',
  useRichSingleSuiteList: false,
  defaultActorLabel: 'Device',

  getCustomDetailItems(_testCase, _app) {
    return null;
  },

  renderCustomDetailView(_container, _testCase, _items, _context, _app) {},

  getActorsHeading(count) {
    return `Devices (${count})`;
  },

  getSummaryColumnClass(col) {
    return col && col.key === 'devices' ? 'col-devices' : 'col-dynamic';
  },

  getSummaryKickerLabel(suiteTitle) {
    return suiteTitle || 'Test';
  },

  getCrossDeviceFactSub(serialsText) {
    return serialsText || 'Cross-device run';
  },

  getLifecycleMeta(steps) {
    const count = Array.isArray(steps) ? steps.length : 0;
    return `${count} step${count === 1 ? '' : 's'}`;
  },

  filterLogStreams(streams, _detail, _res) {
    return streams;
  },

  getActorRoleClass(_roleUpper) {
    return '';
  },

  getStepActorRoleClass(_roleUpper) {
    return 'role-device';
  },

  getDefaultArtifactTitle(_ctx) {
    return 'Artifact';
  },

  hasSuiteEnvBarExtras(_env, _app) {
    return false;
  },

  mountSuiteEnvBarExtras(_bar, _env, _desc, _app, _UIUtils) {},

  needsEnvForTestDetail(env, _res) {
    return Boolean(
      env &&
      env.envId &&
      Array.isArray(env.configFiles) &&
      env.configFiles.some(f => f && f.artifactPath)
    );
  },

  hasConfigData(env, envDetail, _res) {
    return Boolean(
      (envDetail && Array.isArray(envDetail.files) && envDetail.files.length > 0) ||
      (env && Array.isArray(env.configFiles) && env.configFiles.length > 0)
    );
  },

  getArtifactsSectionTitle({ hasArtifacts, hasConfigFiles }) {
    if (hasConfigFiles && hasArtifacts) return 'Artifacts & configuration';
    if (hasConfigFiles) return 'Configuration';
    return 'Artifacts';
  },

  renderConfigBlocks({ env, envDetail, UIUtils }) {
    const blocks = [];
    const copyTargets = [];
    const lazyFiles = envDetail && Array.isArray(envDetail.files) ? envDetail.files : [];
    const configFiles = env && Array.isArray(env.configFiles) ? env.configFiles : [];
    const lazyKeys = new Set(lazyFiles.filter(Boolean).map(f => f.key).filter(Boolean));
    const extraConfigFiles = configFiles.filter(f => f && (!f.key || !lazyKeys.has(f.key)));

    if (lazyFiles.length > 0) {
      lazyFiles.forEach((file, idx) => {
        if (!file) return;
        const safePath = UIUtils.sanitizeUrl(file.artifactPath, false);
        const copyIdx = copyTargets.length;
        if (file.content) {
          copyTargets.push({ label: file.label || 'Configuration file', content: file.content });
        }
        blocks.push(`
          <div class="detail-artifact-block" data-config-idx="${idx}">
            <div class="detail-artifact-head">
              <div class="detail-artifact-title">
                <span class="font-mono">${UIUtils.escapeHTML(file.label || file.key || 'Configuration')}</span>
                ${file.reference ? `<span class="text-xs text-gray-600 font-mono">(${UIUtils.escapeHTML(file.reference)})</span>` : ''}
              </div>
              <div class="flex items-center gap-2">
                ${file.content ? `<button type="button" class="copy-btn" data-copy-config="${copyIdx}">Copy</button>` : ''}
                ${safePath ? `<a class="copy-btn" href="${UIUtils.escapeHTML(safePath)}" download>Download</a>` : ''}
              </div>
            </div>
            ${file.content ? `<pre class="detail-rule-xml font-mono" tabindex="0">${UIUtils.escapeHTML(file.content)}</pre>` : ''}
          </div>
        `);
      });
    }

    if (extraConfigFiles.length > 0) {
      const cards = extraConfigFiles.map(file => {
        const safePath = UIUtils.sanitizeUrl(file.artifactPath, false);
        return `
          <div class="detail-kv-card">
            <div class="detail-kv-key font-mono">${UIUtils.escapeHTML(file.label || file.key || 'File')}</div>
            <div class="detail-kv-val font-mono text-sm flex items-center justify-between gap-2">
              <span>${UIUtils.escapeHTML(file.reference || file.label || '')}</span>
              ${safePath ? `<a class="copy-btn" href="${UIUtils.escapeHTML(safePath)}" download>Download</a>` : ''}
            </div>
          </div>
        `;
      });
      if (cards.length > 0) {
        blocks.push(`
          <div class="detail-artifact-block">
            <div class="detail-kv-grid">${cards.join('')}</div>
          </div>
        `);
      }
    }

    return { blocksHtml: blocks, copyTargets };
  },

  copyContextArtifactsHeading({ hasConfigFiles }) {
    return hasConfigFiles ? '### Artifacts & Configuration' : '### Artifacts';
  },

  appendCopyContextConfig(lines, { env, envDetail }) {
    if (env && env.attributes && typeof env.attributes === 'object') {
      Object.entries(env.attributes).forEach(([k, v]) => {
        lines.push(`- **${k}**: \`${v}\``);
      });
    }
    const lazyFiles = envDetail && Array.isArray(envDetail.files) ? envDetail.files : [];
    const configFiles = env && Array.isArray(env.configFiles) ? env.configFiles : [];
    const lazyKeys = new Set(lazyFiles.filter(Boolean).map(f => f.key).filter(Boolean));
    lazyFiles.forEach(file => {
      if (!file) return;
      lines.push(`- **Configuration file**: \`${file.label || file.key || 'config'}\`${file.reference ? ` (${file.reference})` : ''}`);
      if (file.content) {
        SuiteRegistry.pushFencedBlock(lines, String(file.content).trim(), 'text');
      }
    });
    configFiles.forEach(file => {
      if (!file || (file.key && lazyKeys.has(file.key))) return;
      lines.push(`- **${file.label || file.key || 'Configuration'}**: \`${file.reference || file.label || ''}\``);
    });
  },
});

const SuiteRegistry = {
  _plugins: new Map(),

  markdownFence(text, lang = '') {
    const str = String(text ?? '');
    const safeLang = String(lang ?? '').replace(/[`\r\n]+/g, '');
    let maxRun = 0;
    const matches = str.match(/`+/g);
    if (matches) {
      for (const m of matches) {
        if (m.length > maxRun) maxRun = m.length;
      }
    }
    const fence = '`'.repeat(Math.max(3, maxRun + 1));
    return { open: `${fence}${safeLang}`, close: fence };
  },

  pushFencedBlock(lines, text, lang = 'text') {
    const str = String(text ?? '');
    const { open, close } = this.markdownFence(str, lang);
    lines.push(open);
    lines.push(str);
    lines.push(close);
  },

  register(adapterId, plugin) {
    if (!adapterId || !plugin) return;
    this._plugins.set(adapterId, {
      ...DEFAULT_SUITE_PLUGIN,
      ...plugin,
      id: adapterId,
    });
  },

  resolve(adapterId) {
    if (adapterId && this._plugins.has(adapterId)) {
      return this._plugins.get(adapterId);
    }
    return DEFAULT_SUITE_PLUGIN;
  },

  findCustomDetailMatch(testCase, app) {
    // Rich single-suite layouts render their own detail view.
    if (app && app.usesRichSingleSuiteLayout()) return null;
    for (const plugin of this._plugins.values()) {
      if (typeof plugin.getCustomDetailItems === 'function') {
        const items = plugin.getCustomDetailItems(testCase, app);
        if (Array.isArray(items) && items.length > 0) {
          return { plugin, items };
        }
      }
    }
    return null;
  },
};

globalThis.SuiteRegistry = SuiteRegistry;
