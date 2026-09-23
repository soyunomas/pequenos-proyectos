import assert from 'node:assert/strict';
import { chromium } from 'playwright';

const targets = [
  { name: 'Móvil pequeño', viewport: { width: 320, height: 640 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 },
  { name: 'Móvil', viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 3 },
  { name: 'Escritorio', viewport: { width: 1440, height: 900 }, isMobile: false, hasTouch: false, deviceScaleFactor: 1 },
];
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
try {
  for (const target of targets) {
    const context = await browser.newContext({ ...target });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto('http://127.0.0.1:8765/', { waitUntil: 'networkidle' });
    assert.equal(await page.title(), 'FaceChanger · espejo táctil');
    const canvas = page.locator('#mirror'), before = await canvas.boundingBox();
    assert.ok(before && before.width >= target.viewport.width - 2 &&
      before.height >= target.viewport.height - 2, target.name + ' full screen mirror');
    assert.equal(await page.locator('#view-zoom').count(), 0, 'No zoom slider');

    await page.getByRole('button', { name: 'Abrir ajustes' }).click();
    const panel = page.locator('#panel');
    await panel.waitFor({ state: 'visible', timeout: 3000 });
    await page.waitForTimeout(210);
    assert.equal(await panel.getAttribute('inert'), null, target.name + ' panel interactive');
    const box = await panel.boundingBox();
    assert.ok(box && Math.abs(box.x) < 2 && Math.abs(box.y) < 2 &&
      Math.abs(box.width - target.viewport.width) < 2 &&
      Math.abs(box.height - target.viewport.height) < 2,
    target.name + ' full-screen settings: ' + JSON.stringify(box));

    const select = page.locator('#filters');
    assert.ok(Number.parseFloat(await select.evaluate(el => getComputedStyle(el).fontSize)) >= 18,
      target.name + ' readable selector');
    assert.equal(await select.inputValue(), 'eyes-big', target.name + ' default');
    assert.equal(await select.locator('option').count(), 32);
    assert.equal(await select.locator('optgroup').count(), 5);
    assert.ok(await page.getByText('Elige cómo quieres verte.').isVisible());
    await select.selectOption('nose-big');
    assert.equal(await select.inputValue(), 'nose-big');

    const advanced = page.locator('details.advanced').first();
    await advanced.locator('summary').click();
    await page.locator('#extra-filter').selectOption('mouth-smile');
    await page.getByRole('button', { name: 'Añadir a la mezcla' }).click();
    assert.ok(await page.locator('.effect-title').getByText('Sonrisa gigante').isVisible());

    const canvasWhileSettings = await canvas.boundingBox();
    assert.ok(Math.abs(canvasWhileSettings.width - before.width) < 2,
      target.name + ' menu does not rescale mirror');

    await page.locator('details.advanced').nth(1).locator('summary').click();
    await page.getByRole('button', { name: 'Guardar filtro' }).scrollIntoViewIfNeeded();
    assert.ok(await page.getByRole('button', { name: 'Guardar filtro' }).isVisible(),
      target.name + ' can scroll to advanced actions');

    await page.getByRole('button', { name: 'Ver mi reflejo' }).click();
    await panel.waitFor({ state: 'hidden', timeout: 3000 });
    assert.equal(await panel.getAttribute('inert'), '');
    assert.ok(!(await panel.isVisible()), target.name + ' panel closed');
    assert.deepEqual(errors, [], target.name + ' page errors');
    await context.close();
    console.log('Correcto:', target.name);
  }
} finally {
  await browser.close();
}
