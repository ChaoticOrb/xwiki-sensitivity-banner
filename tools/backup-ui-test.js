const { chromium } = require('playwright');
const fs = require('fs');
const S = process.argv[2];
const assert = (c, m) => { if (!c) { console.log('FAIL: ' + m); process.exitCode = 1; } else console.log('ok: ' + m); };
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ acceptDownloads: true });
  const backupReqs = [], restoreReqs = [];
  const TOTAL = 1203;
  await page.route('**/mock/backup**', route => {
    const u = new URL(route.request().url());
    const off = +u.searchParams.get('offset'), lim = +u.searchParams.get('limit');
    backupReqs.push([off, lim]);
    const rows = [];
    for (let i = off; i < Math.min(off + lim, TOTAL); i++) rows.push({ page: 'Space.Page' + i, owner: 'Management', security: 'Sensitive', country: 'UK' });
    route.fulfill({ contentType: 'application/json', body: JSON.stringify({ total: TOTAL, offset: off, wiki: 'xwiki', rows }) });
  });
  await page.route('**/mock/restore**', route => {
    const body = new URLSearchParams(route.request().postData());
    const mode = body.get('mode'); const rows = JSON.parse(body.get('rows'));
    restoreReqs.push({ mode, n: rows.length, token: body.get('form_token'), method: route.request().method() });
    const counts = { same: 0, restore: 0, differs: 0, missing: 0, invalid: 0, norights: 0, applied: 0 };
    const problems = [];
    for (const r of rows) {
      const k = r.page.startsWith('Same') ? 'same' : r.page.startsWith('Blank') ? 'restore' : r.page.startsWith('Diff') ? 'differs' : 'missing';
      counts[k]++;
      if (mode === 'fill' && k === 'restore') counts.applied++;
      if (mode === 'overwrite' && (k === 'restore' || k === 'differs')) counts.applied++;
      if (k === 'missing' || k === 'differs') problems.push({ page: r.page, result: k });
    }
    route.fulfill({ contentType: 'application/json', body: JSON.stringify({ mode, counts, problems }) });
  });
  page.on('pageerror', e => console.log('PAGEERROR', e.message));
  page.on('console', m => { if (m.type() === 'error') console.log('CONSOLE', m.text()); });
  await page.route('http://sb.test/harness.html', route => route.fulfill({ contentType: 'text/html', body: fs.readFileSync(S + '/harness.html', 'utf8') }));
  await page.goto('http://sb.test/harness.html');
  // 1. backup
  const [dl] = await Promise.all([page.waitForEvent('download'), page.click('#sb-create')]);
  const saved = JSON.parse(fs.readFileSync(await dl.path(), 'utf8'));
  assert(JSON.stringify(backupReqs.map(r => r[0])) === '[0,500,1000]', 'backup read in chunks at offsets 0,500,1000: ' + JSON.stringify(backupReqs));
  assert(saved.count === TOTAL && saved.rows.length === TOTAL && saved.format === 1, 'backup file has all ' + TOTAL + ' rows and format 1');
  assert(/^sensitivity-backup-\d{4}-\d{2}-\d{2}\.json$/.test(dl.suggestedFilename()), 'file name ' + dl.suggestedFilename());
  // 2. restore gating
  const mk = (name, rows) => { const f = S + '/' + name; fs.writeFileSync(f, JSON.stringify({ format: 1, extension: 'sensitivity-banner', wiki: 'xwiki', created: 'x', count: rows.length, rows })); return f; };
  const rowsA = [];
  for (let i = 0; i < 250; i++) rowsA.push({ page: (i % 4 == 0 ? 'Same' : i % 4 == 1 ? 'Blank' : i % 4 == 2 ? 'Diff' : 'Gone') + i, owner: 'Management', security: 'Open', country: 'UK' });
  const fileA = mk('a.json', rowsA), fileB = mk('b.json', rowsA.slice(0, 10));
  assert(await page.isDisabled('#sb-dry'), 'dry run disabled before a file is chosen');
  await page.setInputFiles('#sb-file', fileA);
  await page.waitForFunction(() => document.getElementById('sb-status').textContent.includes('Loaded 250'));
  assert(!(await page.isDisabled('#sb-dry')) && await page.isDisabled('#sb-fill') && await page.isDisabled('#sb-over'), 'after choosing a file only dry run is enabled');
  await page.click('#sb-dry');
  await page.waitForFunction(() => document.getElementById('sb-status').textContent.includes('Dry run complete'));
  assert(restoreReqs.length === 3 && restoreReqs.every(r => r.mode === 'dryrun' && r.n <= 100 && r.method === 'POST' && r.token === 'TOKEN123'), 'dry run sent 3 POSTs of at most 100 rows, mode dryrun, token present: ' + JSON.stringify(restoreReqs.map(r => r.n)));
  const txt = await page.textContent('#sb-result');
  assert(/DRY RUN, nothing was changed/.test(txt) && /Pages changed by this run: 0/.test(txt), 'dry run reports nothing changed');
  assert(/would be filled: 63/.test(txt) && /differ from the backup: 62/.test(txt) && /Already identical: 63/.test(txt), 'counts as expected: ' + txt.split('\n').slice(1, 6).join(' | '));
  assert(!(await page.isDisabled('#sb-fill')) && !(await page.isDisabled('#sb-over')), 'restore buttons enabled after dry run');
  // 3. a different file re-locks
  await page.setInputFiles('#sb-file', fileB);
  await page.waitForFunction(() => document.getElementById('sb-status').textContent.includes('Loaded 10'));
  assert(await page.isDisabled('#sb-fill') && await page.isDisabled('#sb-over'), 'choosing a different file disables the restore buttons again');
  // 4. back to file A, dry run, decline then accept confirmation
  await page.setInputFiles('#sb-file', fileA);
  await page.waitForFunction(() => document.getElementById('sb-status').textContent.includes('Loaded 250'));
  await page.click('#sb-dry');
  await page.waitForFunction(() => document.getElementById('sb-status').textContent.includes('Dry run complete'));
  const before = restoreReqs.length;
  page.once('dialog', d => d.dismiss());
  await page.click('#sb-over');
  await page.waitForTimeout(300);
  assert(restoreReqs.length === before, 'declining the overwrite confirmation sends nothing');
  page.once('dialog', d => d.accept());
  await page.click('#sb-fill');
  await page.waitForFunction(() => document.getElementById('sb-status').textContent.includes('Restore complete'));
  const fillReqs = restoreReqs.slice(before);
  assert(fillReqs.length === 3 && fillReqs.every(r => r.mode === 'fill'), 'fill mode sent after accepting confirmation');
  assert(/Pages changed by this run: 63/.test(await page.textContent('#sb-result')), 'fill reports 63 pages changed');
  assert(await page.isDisabled('#sb-fill') && await page.isDisabled('#sb-over'), 'restore buttons lock again after a restore, a new dry run is needed');
  // 5. wrong file type
  const bad = S + '/bad.json'; fs.writeFileSync(bad, JSON.stringify({ hello: 1 }));
  await page.setInputFiles('#sb-file', bad);
  await page.waitForFunction(() => document.getElementById('sb-status').textContent.includes('Could not use that file'));
  assert(await page.isDisabled('#sb-dry'), 'a file that is not a backup is rejected and nothing is enabled');
  await browser.close();
})().catch(e => { console.log('ERROR', e.message); process.exit(1); });
