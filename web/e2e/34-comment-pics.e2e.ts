import { expect, test } from '@playwright/test';
import { ADMIN, login, watchConsole } from './helpers';

// Гифка 1×1 – двигаться ей нечем, но тип тот же.
const GIF = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');

test('в комментарии – гифка без текста и реакция с несогласием', async ({ page }) => {
	const errors = watchConsole(page);
	await login(page, ADMIN);
	const id = await page.evaluate(async () => {
		const csrf =
			document.cookie
				.split('; ')
				.find((c) => /^(__Host-)?gb_csrf=/.test(c))
				?.split('=')[1] ?? '';
		const me = await (await fetch('/api/me')).json();
		const r = await fetch('/api/news', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf },
			body: JSON.stringify({ title: 'Мемы к экзамену', body: '', groupIds: [me.groups[0].id] })
		});
		return (await r.json()).id as number;
	});
	await page.goto(`/news/${id}`);
	const comments = page.getByRole('region', { name: 'Комментарии' });
	await comments
		.locator('input[type=file]')
		.setInputFiles({ name: 'кот.gif', mimeType: 'image/gif', buffer: GIF });
	await expect(comments.getByRole('button', { name: 'Убрать кот.gif' })).toBeVisible();
	const send = comments.getByRole('button', { name: 'Отправить' });
	await expect(send).toBeEnabled();
	await send.click();
	const pic = comments.getByRole('button', { name: 'Открыть кот.gif' });
	await expect(pic).toBeVisible();
	await expect(pic.locator('img')).toHaveAttribute('src', /^\/api\/files\/\d+$/);

	// Открывается в просмотре.
	await pic.click();
	await expect(page.getByRole('dialog', { name: 'Просмотр: кот.gif' })).toBeVisible();
	await page.keyboard.press('Escape');

	await comments.getByRole('button', { name: 'Поставить реакцию' }).click();
	await comments.getByRole('button', { name: '👎' }).click();
	await expect(comments.getByRole('button', { name: /^👎 1/ })).toHaveAttribute(
		'aria-pressed',
		'true'
	);
	await page.reload();
	await expect(comments.getByRole('button', { name: /^👎 1/ })).toBeVisible();
	await expect(comments.getByRole('button', { name: 'Открыть кот.gif' })).toBeVisible();
	expect(errors).toEqual([]);
});
