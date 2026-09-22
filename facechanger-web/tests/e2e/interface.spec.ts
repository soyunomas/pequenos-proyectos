import { expect, test } from '@playwright/test';
test.use({ permissions: ['camera'] });
test('editor en español y controles accesibles aunque no haya rostro', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Deforma tu reflejo' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Nariz grande' })).toBeVisible();
  await page.getByRole('button', { name: 'Nariz grande' }).click();
  await expect(page.getByRole('button', { name: 'Nariz grande' })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('mirror-canvas')).toBeVisible();
});
