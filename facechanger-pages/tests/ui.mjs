import assert from 'node:assert/strict';
import { chromium } from 'playwright';

const targets = [
  { name: 'Móvil pequeño', viewport: { width: 320, height: 640 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 },
  { name: 'Móvil', viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 3 },
  { name: 'Escritorio', viewport: { width: 1440, height: 900 }, isMobile: false, hasTouch: false, deviceScaleFactor: 1 },
];
const browser = await chromium.launch({headless:true, args:['--no-sandbox']});
try {
  for (const target of targets) {
    const context = await browser.newContext({ ...target });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto('http://127.0.0.1:8765/', { waitUntil: 'networkidle' });
    assert.equal(await page.title(),'FaceChanger · espejo táctil');
    const canvas = page.locator('#mirror');
    const before = await canvas.boundingBox();
    assert.ok(before && before.width >= target.viewport.width - 2 && before.height >= target.viewport.height - 2, target.name+' canvas');
    await page.getByRole('button', {name:'Abrir ajustes'}).click();
    const panel = page.locator('#panel');
    await panel.waitFor({state:'visible',timeout:3000});
    await page.waitForTimeout(320);
    assert.ok(await panel.isVisible(), target.name+' panel');
    assert.equal(await panel.getAttribute('inert'),null);
    assert.equal(await page.locator('#filters').evaluate(el=>getComputedStyle(el).fontSize),'17px');
    const { right, width } = await panel.evaluate(el=>{const r=el.getBoundingClientRect();return {right:r.right,width:r.width}});
    assert.ok(right <= target.viewport.width+.5 && width > 280, target.name+' panel width');
    assert.equal(await page.locator('#filters optgroup').count(),5);
    assert.equal(await page.locator('#filters option').count(),32);
    await page.locator('#filters').selectOption('eye-droop');
    assert.equal(await page.locator('#filters').inputValue(),'eye-droop');
    await page.locator('#extra-filter').selectOption('mouth-smile');
    await page.getByRole('button',{name:'Añadir a la mezcla'}).click();
    assert.ok(await page.locator('.effect-title').getByText('Sonrisa gigante').isVisible());
    await page.locator('#view-zoom').evaluate(el=>{el.value='175';el.dispatchEvent(new Event('input',{bubbles:true}))});
    const zoomed = await canvas.boundingBox();
    assert.ok(zoomed.width > before.width * 1.7, target.name+' zoom');
    await page.getByRole('button',{name:'Restablecer encuadre · 100 %'}).click();
    const reset = await canvas.boundingBox();
    assert.ok(Math.abs(reset.width-before.width)<2,target.name+' reset');
    await page.getByRole('button',{name:'Cerrar ajustes',exact:true}).click();
    await panel.waitFor({state:'hidden',timeout:3000});
    assert.equal(await panel.getAttribute('inert'),'');
    assert.ok(!(await panel.isVisible()), target.name+' panel closed');
    assert.deepEqual(errors,[],target.name+' page errors');
    await context.close();
    console.log('Correcto:',target.name);
  }
} finally { await browser.close(); }
