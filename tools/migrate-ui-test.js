// Tests the Sensitivity Migration admin script in Chromium against a mock of the wiki handler.
// Usage: node tools/migrate-ui-test.js OUTDIR   (after tools/make-migrate-harness.py OUTDIR)
const { chromium } = require('playwright');
const fs = require('fs');
const OUT = process.argv[2];
let failures = 0;
const check = (cond, msg) => { if (!cond) { failures++; console.log('FAIL: ' + msg); } else { console.log('ok: ' + msg); } };

const OPTIONS = { owner: ['Management', 'Senior Management', 'Standard Staff', 'R&D'], security: ['Sensitive', 'Open'], country: ['UK', 'Europe'] };
const FIELDS = ['owner', 'security', 'country'];
const blank = () => ({ owner: '', security: '', country: '' });

function makeStore() {
  const s = {
    'Space.A': { content: '{{sensitivity owner="Management" security="Sensitive" country="UK"/}}\n\nBody A', values: blank(), canEdit: true },
    'Space.B': { content: '{sensitivity:owner=Standard Staff|security=Open|country=Europe}\nBody B', values: blank(), canEdit: true },
    'Space.C': { content: '{{confluence_sensitivity owner="senior management" security="open"/}}\nBody C', values: blank(), canEdit: true },
    'Space.D': { content: '{{sensitivity owner="Interns" security="Open"/}}\nBody D', values: blank(), canEdit: true },
    'Space.E': { content: '{{sensitivity owner="Management"/}}\n{{sensitivity owner="Standard Staff"/}}\nBody E', values: blank(), canEdit: true },
    'Space.F': { content: 'Our sensitivity analysis is a different topic.', values: blank(), canEdit: true },
    'Space.G': { content: '{{sensitivity owner="Management"/}}\nBody G', values: { owner: 'Management', security: '', country: '' }, canEdit: true },
    'Space.H': { content: '{{sensitivity owner="Management"/}}\nBody H', values: blank(), canEdit: false },
    'Space.I': { content: '{{sensitivity /}}\nBody I', values: blank(), canEdit: true },
    'Space.J': { content: '{{sensitivity owner="Management"}}ignored{{/sensitivity}}\nBody J', values: blank(), canEdit: true }
  };
  for (let i = 0; i < 60; i++) s['Filler.P' + i] = { content: 'This page mentions sensitivity only in passing ' + i, values: blank(), canEdit: true };
  return s;
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  page.on('pageerror', e => { failures++; console.log('PAGEERROR', e.message); });
  await page.route('http://sb.test/harness.html', route => route.fulfill({ contentType: 'text/html', body: fs.readFileSync(OUT + '/migrate-harness.html', 'utf8') }));
  const store = makeStore();
  const log = [];
  await page.route('**/mock/mg**', route => {
    const req = route.request();
    const u = new URL(req.url());
    const post = req.method() === 'POST' ? new URLSearchParams(req.postData()) : null;
    const q = k => (post ? post.get(k) : null) || u.searchParams.get(k);
    const send = (status, obj) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(obj) });
    const op = q('op');
    log.push({ op, mode: q('mode'), method: req.method() });
    if (post && post.get('form_token') !== 'TOKEN123') return send(403, { error: 'Invalid form token' });
    if (op === 'config') return send(200, { fields: FIELDS, options: OPTIONS });
    if (op === 'list') {
      const m = q('macro').toLowerCase();
      const names = Object.keys(store).filter(n => store[n].content.toLowerCase().includes(m)).sort();
      return send(200, { total: names.length, names });
    }
    if (op === 'content') {
      const names = JSON.parse(q('names'));
      if (names.length > 25) return send(400, { error: 'too many' });
      return send(200, { pages: names.map(n => ({ page: n, content: store[n].content, values: store[n].values, canEdit: store[n].canEdit })) });
    }
    if (op === 'apply' || op === 'clean') {
      const rows = JSON.parse(q('rows')); const mode = q('mode');
      if (rows.length > (op === 'apply' ? 100 : 25)) return send(400, { error: 'too many' });
      const counts = op === 'apply' ? { ready: 0, hasvalues: 0, invalid: 0, missing: 0, norights: 0, applied: 0 } : { removable: 0, differs: 0, invalid: 0, missing: 0, norights: 0, notfound: 0, applied: 0 };
      const problems = [];
      for (const r of rows) {
        const vals = {}; let bad = !r.page; let any = false;
        for (const f of FIELDS) {
          vals[f] = String(r[f] || '').trim();
          if (vals[f]) { any = true; if (op === 'apply' && !OPTIONS[f].includes(vals[f])) bad = true; }
        }
        if (!any) bad = true;
        if (op === 'clean' && (!Array.isArray(r.remove) || r.remove.length < 1)) bad = true;
        let outcome = 'invalid';
        if (!bad) {
          const p = store[r.page];
          if (!p) outcome = 'missing';
          else if (!p.canEdit) outcome = 'norights';
          else if (op === 'apply') outcome = FIELDS.some(f => p.values[f]) ? 'hasvalues' : 'ready';
          else if (!FIELDS.every(f => p.values[f] === vals[f])) outcome = 'differs';
          else if (!r.remove.every(x => p.content.includes(x))) outcome = 'notfound';
          else outcome = 'removable';
        }
        counts[outcome]++;
        const okName = op === 'apply' ? 'ready' : 'removable';
        if (outcome === okName && mode === 'apply') {
          const p = store[r.page];
          if (op === 'apply') FIELDS.forEach(f => { p.values[f] = vals[f]; });
          else r.remove.forEach(x => { p.content = p.content.split(x).join(''); });
          counts.applied++;
        } else if (outcome !== okName) problems.push({ page: r.page, result: outcome });
      }
      return send(200, { mode, counts, problems });
    }
    return send(400, { error: 'Unknown operation' });
  });
  await page.goto('http://sb.test/harness.html');

  // ---- parser and planner unit checks, run in the real page
  const core = (fn, ...args) => page.evaluate(([f, a]) => window.SbMigrateCore[f](...a), [fn, args]);
  const cfg = { macro: 'sensitivity', names: { owner: 'owner', security: 'security', country: 'country' } };
  const an = (content, c) => page.evaluate(([x, c2, o]) => window.SbMigrateCore.analyze(x, c2, o), [content, c || cfg, OPTIONS]);

  let a = await an('{{sensitivity owner="Management" security="Sensitive" country="UK"/}}\n\nText');
  check(a.status === 'parsed' && a.values.owner === 'Management' && a.values.security === 'Sensitive' && a.values.country === 'UK', 'xwiki self closing macro is parsed');
  a = await an('{{confluence_sensitivity owner="management" security="OPEN"/}}');
  check(a.status === 'parsed' && a.values.owner === 'Management' && a.values.security === 'Open' && a.values.country === '', 'confluence_ prefix and different case are matched to the configured option text, missing parameter stays empty');
  a = await an('x {sensitivity:owner=Standard Staff|security=Open|country=Europe} y');
  check(a.status === 'parsed' && a.values.owner === 'Standard Staff' && a.values.country === 'Europe', 'literal Confluence markup is parsed');
  a = await an('{{sensitivity owner="Management"}}ignored body{{/sensitivity}}');
  check(a.status === 'parsed' && a.values.owner === 'Management' && a.removals[0] === '{{sensitivity owner="Management"}}ignored body{{/sensitivity}}', 'open and close form is parsed and removed as a whole');
  check((await an('{sensitivity}')).status === 'empty' && (await an('{{sensitivity /}}')).status === 'empty', 'a macro without values is reported as empty');
  a = await an('{{sensitivity owner="Interns" security="Open"/}}');
  check(a.status === 'unmatched' && a.unmatched.length === 1 && a.unmatched[0].value === 'Interns', 'a value that is not in the lists is reported, not guessed');
  check((await an('{{sensitivity owner="Management"/}} {{sensitivity owner="Standard Staff"/}}')).status === 'conflict', 'two disagreeing macros are a conflict');
  a = await an('{{sensitivity owner="Management"/}}\n{sensitivity:owner=management}');
  check(a.status === 'parsed' && a.removals.length === 2, 'two agreeing macros in different forms are accepted and both removed');
  check((await an('Our sensitivity analysis')).status === 'nomacro', 'the word on its own is not a macro');
  check((await an('{{sensitivityfoo owner="Management"/}}')).status === 'nomacro', 'a longer macro name is not matched');
  a = await an('{{sensitivity owner="R&amp;D"/}}');
  check(a.status === 'parsed' && a.values.owner === 'R&D', 'html entities in values are decoded');
  a = await an('{{classification team="management" level="Open"/}}', { macro: 'classification', names: { owner: 'team', security: 'level', country: 'country' } });
  check(a.status === 'parsed' && a.values.owner === 'Management' && a.values.security === 'Open', 'custom macro and parameter names work');
  check((await core('parseXWikiParams', 'owner="a~"b" other="~~"')).owner === 'a"b', 'tilde escaped quotes in macro parameters are read');
  check((await core('removalString', 'before\n{{sensitivity owner="Management"/}}\nafter', { start: 7, end: 7 + '{{sensitivity owner="Management"/}}'.length })) === '{{sensitivity owner="Management"/}}\n', 'a macro alone on its line is removed with its line break');
  check((await core('removalString', 'inline {{sensitivity owner="Management"/}} text', { start: 7, end: 7 + '{{sensitivity owner="Management"/}}'.length })) === '{{sensitivity owner="Management"/}}', 'an inline macro leaves the surrounding text and spacing alone');

  // ---- the real macro format: one call with Sensitivity, Security and Country
  const realCfg = { macro: 'sensitivity', names: { owner: 'Sensitivity', security: 'Security', country: 'Country' } };
  const real = '{{confluence_sensitivity Sensitivity="Standard Staff" Security="Open" Country="UK" /}}\n\nBody';
  a = await an(real, realCfg);
  check(a.status === 'parsed' && a.values.owner === 'Standard Staff' && a.values.security === 'Open' && a.values.country === 'UK', 'the real format (capitalised names, space before the closing slash) is parsed when Owner is mapped to Sensitivity');
  check(a.removals[0] === '{{confluence_sensitivity Sensitivity="Standard Staff" Security="Open" Country="UK" /}}\n', 'its removal text includes the space before the slash and the line break');
  a = await an(real);
  check(a.status === 'parsed' && a.values.owner === '' && a.values.security === 'Open' && a.values.country === 'UK' && a.unmapped.length === 1 && a.unmapped[0] === 'sensitivity', 'with the default names Owner is not read and the unused Sensitivity parameter is reported as unmapped');
  check(JSON.stringify(await core('nameList', 'Owner, , Sensitivity ,owner', 'owner')) === '["owner","sensitivity"]', 'a field accepts several comma separated names, lower cased, without duplicates');
  const aliasCfg = { macro: 'sensitivity', names: { owner: 'owner, sensitivity', security: 'security', country: 'country' } };
  check((await an('{{sensitivity Owner="Management" /}}', aliasCfg)).values.owner === 'Management' && (await an('{{sensitivity Sensitivity="Standard Staff" /}}', aliasCfg)).values.owner === 'Standard Staff', 'either alias supplies the value');
  check((await an('{{sensitivity Owner="Management" /}} {{sensitivity Sensitivity="Standard Staff" /}}', aliasCfg)).status === 'conflict', 'two macros that disagree through different aliases are still a conflict');

  // ---- UI flow
  const status = () => page.textContent('#sb-mg-status');
  const result = () => page.textContent('#sb-mg-result');
  const dis = id => page.isDisabled(id);
  check(await dis('#sb-mg-apply-m') && await dis('#sb-mg-apply-c'), 'both apply buttons are disabled before any dry run');

  await page.fill('#sb-mg-macro', 'bad name!');
  await page.click('#sb-mg-scan-m');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Failed'));
  check(/letters, digits and underscores/.test(await status()) && !log.some(l => l.op === 'list'), 'an invalid macro name is rejected before anything is sent');
  await page.fill('#sb-mg-macro', 'sensitivity');

  await page.click('#sb-mg-scan-m');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Dry run complete'));
  let txt = await result();
  check(/DRY RUN, nothing was changed \(migration\)/.test(txt), 'migration dry run says nothing was changed');
  check(/Pages containing the macro name: 70/.test(txt) && /No macro found in the page source: 61/.test(txt), 'counts pages and pages without a macro: ' + txt.split('\n').slice(1, 3).join(' | '));
  check(/Macro found but no recognised values: 1/.test(txt) && /not in your lists, skipped: 1/.test(txt) && /Several macros disagree, skipped: 1/.test(txt), 'empty, unmatched and conflicting pages are skipped');
  check(/Already have sensitivity values, skipped: 1/.test(txt) && /No edit rights, skipped: 1/.test(txt) && /Ready to migrate: 4/.test(txt), 'pages with values and pages without rights are skipped, 4 are ready');
  check(/interns/i.test(txt) && /owner="Interns"/.test(txt), 'the unmatched value is listed with its page for the admin');
  check(/The wiki agrees on 4 of 4 pages/.test(txt), 'the wiki side dry run agrees');
  check(Object.values(store).every(p => !FIELDS.some(f => p.values[f] && p !== store['Space.G'])), 'the dry run wrote nothing');
  check(log.filter(l => l.op === 'content').length === 3, 'content was fetched in chunks of 25 (3 requests for 70 pages)');
  check(!(await dis('#sb-mg-apply-m')) && await dis('#sb-mg-apply-c'), 'only the migration apply button is enabled after the migration dry run');

  await page.fill('#sb-mg-owner', 'team');
  check(await dis('#sb-mg-apply-m'), 'changing a setting disables apply until the dry run is repeated');
  await page.fill('#sb-mg-owner', 'owner');
  check(await dis('#sb-mg-apply-m'), 'putting the setting back does not re-enable apply without a new dry run');
  await page.click('#sb-mg-scan-m');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('You can now apply'));

  const before = log.filter(l => l.mode === 'apply').length;
  page.once('dialog', d => d.dismiss());
  await page.click('#sb-mg-apply-m');
  await page.waitForTimeout(300);
  check(log.filter(l => l.mode === 'apply').length === before, 'declining the confirmation sends nothing');
  page.once('dialog', d => { check(/4 pages/.test(d.message()), 'the confirmation names the number of pages'); d.accept(); });
  await page.click('#sb-mg-apply-m');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Finished'));
  check(/Pages changed: 4 of 4/.test(await result()), 'migration reports 4 pages changed');
  check(store['Space.A'].values.owner === 'Management' && store['Space.A'].values.security === 'Sensitive' && store['Space.A'].values.country === 'UK', 'page A has its values');
  check(store['Space.B'].values.owner === 'Standard Staff' && store['Space.B'].values.country === 'Europe', 'page B (literal markup) has its values');
  check(store['Space.C'].values.owner === 'Senior Management' && store['Space.C'].values.security === 'Open', 'page C stored the option text as configured, not the lower case text from the macro');
  check(store['Space.J'].values.owner === 'Management' && store['Space.J'].values.security === '', 'page J (open and close form, one value) has its value');
  check(!store['Space.D'].values.owner && !store['Space.E'].values.owner && !store['Space.H'].values.owner && !store['Space.I'].values.owner, 'unmatched, conflicting, no rights and empty pages were not touched');
  check(store['Space.G'].values.owner === 'Management' && !store['Space.G'].values.security, 'a page that already had values kept them');
  check(store['Space.A'].content.includes('{{sensitivity') && store['Space.B'].content.includes('{sensitivity'), 'the migration step leaves the old macro in place');
  check(await dis('#sb-mg-apply-m'), 'apply locks again after it has run');

  // ---- step 2
  await page.click('#sb-mg-scan-c');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Dry run complete'));
  txt = await result();
  check(/DRY RUN, nothing was changed \(macro removal\)/.test(txt) && /Macro can be removed: 5/.test(txt), 'removal dry run finds 5 removable macros: ' + (txt.match(/Macro can be removed: \d+/) || [''])[0]);
  check(/Page has no stored values yet, skipped: 1/.test(txt) && /not in your lists, skipped: 1/.test(txt) && /Several macros disagree, skipped: 1/.test(txt), 'a page that was not migrated is not touched by removal');
  check(store['Space.A'].content.includes('{{sensitivity'), 'the removal dry run changed no content');
  check(await dis('#sb-mg-apply-m') && !(await dis('#sb-mg-apply-c')), 'only the removal apply button is enabled');
  page.once('dialog', d => d.accept());
  await page.click('#sb-mg-apply-c');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Finished'));
  check(/Pages changed: 5 of 5/.test(await result()), 'removal reports 5 pages changed');
  check(store['Space.A'].content === '\nBody A', 'page A: macro and its line break removed, the rest untouched: ' + JSON.stringify(store['Space.A'].content));
  check(store['Space.B'].content === 'Body B' && store['Space.C'].content === 'Body C' && store['Space.J'].content === 'Body J' && store['Space.G'].content === 'Body G', 'pages B, C, J and G lost only the macro');
  check(store['Space.D'].content.includes('Interns') && store['Space.E'].content.includes('Standard Staff') && store['Space.H'].content.includes('{{sensitivity') && store['Space.I'].content.includes('{{sensitivity'), 'skipped pages keep their macros');
  check(store['Space.F'].content === 'Our sensitivity analysis is a different topic.', 'a page that only mentions the word is never edited');

  // ---- phase 2: the real macro format end to end, default settings first, then Owner mapped to Sensitivity
  for (const k of Object.keys(store)) delete store[k];
  store['Real.R1'] = { content: '{{confluence_sensitivity Sensitivity="Management" Security="Sensitive" Country="UK" /}}\nBody R1', values: blank(), canEdit: true };
  store['Real.R2'] = { content: '{{confluence_sensitivity Sensitivity="standard staff" Security="Open" Country="Europe" /}}\nBody R2', values: blank(), canEdit: true };
  store['Real.R3'] = { content: '{{confluence_sensitivity Sensitivity="Value" Security="Open" Country="UK" /}}\nBody R3', values: blank(), canEdit: true };
  await page.fill('#sb-mg-owner', 'owner');
  await page.click('#sb-mg-scan-m');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Dry run complete'));
  txt = await result();
  check(/Parameter names found on the macro: country \(3\), security \(3\), sensitivity \(3\)/.test(txt), 'the dry run lists the parameter names it found: ' + (txt.match(/Parameter names found[^\n]*/) || [''])[0]);
  check(/WARNING, not mapped to any field and ignored: sensitivity \(3\)/.test(txt), 'the dry run warns that Sensitivity is not mapped to any field');
  check(/no value for each field: owner: 3, security: 0, country: 0/.test(txt), 'the dry run shows that every page would be left without an Owner: ' + (txt.match(/Of the [^\n]*/) || [''])[0]);
  page.once('dialog', d => { check(/ignored: sensitivity/.test(d.message()), 'the apply confirmation repeats which parameters are ignored'); d.dismiss(); });
  await page.click('#sb-mg-apply-m');
  await page.waitForTimeout(300);
  check(!store['Real.R1'].values.security, 'nothing was written while the warning stood and the confirmation was declined');
  await page.fill('#sb-mg-owner', 'Sensitivity');
  check(/Settings changed/.test(await status()) && await dis('#sb-mg-apply-m'), 'changing the Owner box discards the dry run');
  await page.click('#sb-mg-scan-m');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Dry run complete'));
  txt = await result();
  check(!/WARNING/.test(txt) && /Ready to migrate: 2/.test(txt) && /not in your lists, skipped: 1/.test(txt) && /owner="Value"/.test(txt), 'with Owner mapped there is no warning, 2 pages are ready and the page with the unknown Owner value is listed and skipped');
  page.once('dialog', d => d.accept());
  await page.click('#sb-mg-apply-m');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Finished'));
  check(store['Real.R1'].values.owner === 'Management' && store['Real.R1'].values.security === 'Sensitive' && store['Real.R1'].values.country === 'UK', 'R1 has all three values');
  check(store['Real.R2'].values.owner === 'Standard Staff' && store['Real.R2'].values.security === 'Open' && store['Real.R2'].values.country === 'Europe', 'R2 has all three values, with the Owner stored as the configured option text');
  check(!store['Real.R3'].values.owner && !store['Real.R3'].values.security, 'R3, whose Owner value is not in the lists, was left alone completely');
  await page.click('#sb-mg-scan-c');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Dry run complete'));
  check(/Macro can be removed: 2/.test(await result()), 'the removal dry run finds the 2 migrated pages');
  page.once('dialog', d => d.accept());
  await page.click('#sb-mg-apply-c');
  await page.waitForFunction(() => document.getElementById('sb-mg-status').textContent.includes('Finished'));
  check(store['Real.R1'].content === 'Body R1' && store['Real.R2'].content === 'Body R2', 'the real format macros are removed completely, including the space before the slash and the line break');
  check(store['Real.R3'].content.includes('Sensitivity="Value"'), 'R3 keeps its macro');

  await browser.close();
  console.log(failures === 0 ? '\nALL PASSED' : '\n' + failures + ' FAILURE(S)');
  process.exit(failures === 0 ? 0 : 1);
})().catch(e => { console.log('ERROR', e.message); process.exit(1); });
