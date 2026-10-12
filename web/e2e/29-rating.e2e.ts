import { expect, test } from '@playwright/test';
import { ADMIN, login, watchConsole } from './helpers';

test('рейтинг активности: свой вклад, периоды, без вылезания за экран', async ({ page }) => {
	const errors = watchConsole(page);
	await login(page, ADMIN);
	// Свой вклад: новость в группу.
	const status = await page.evaluate(async () => {
		const csrf =
			document.cookie
				.split('; ')
				.find((c) => /^(__Host-)?gb_csrf=/.test(c))
				?.split('=')[1] ?? '';
		const me = await (await fetch('/api/me')).json();
		const r = await fetch('/api/news', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf },
			body: JSON.stringify({ title: 'Рейтинг', body: '', groupIds: [me.groups[0].id] })
		});
		return r.status;
	});
	expect(status).toBe(200);

	await page.goto('/members');
	await page.getByRole('button', { name: 'Рейтинг' }).click();
	await expect(page).toHaveURL(/tab=rating/);
	const list = page.getByRole('list', { name: 'Рейтинг' });
	await expect(list).toBeVisible();
	await expect(list.getByText('вы', { exact: true })).toBeVisible();
	await expect(page.getByText(/Вы на \d+ месте/)).toBeVisible();

	await page.getByRole('button', { name: 'Неделя' }).click();
	await expect(list.getByText('вы', { exact: true })).toBeVisible();
	await page.getByRole('button', { name: 'Всё время' }).click();
	await expect(page.getByText(/Очки за всё время/)).toBeVisible();

	await page.setViewportSize({ width: 375, height: 800 });
	expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375);
	await page.screenshot({ path: 'test-results/shots/rating.png', fullPage: true });

	// Обратно к людям.
	await page.getByRole('button', { name: 'Люди' }).click();
	await expect(page.getByRole('searchbox', { name: 'Поиск людей' })).toBeVisible();
	expect(errors).toEqual([]);
});
