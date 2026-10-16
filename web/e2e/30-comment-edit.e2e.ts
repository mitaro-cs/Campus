import { expect, test } from '@playwright/test';
import { ADMIN, login, watchConsole } from './helpers';

test('свой комментарий можно изменить на месте, с пометкой «изменено»', async ({ page }) => {
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
			body: JSON.stringify({ title: 'Правка комментария', body: '', groupIds: [me.groups[0].id] })
		});
		return (await r.json()).id as number;
	});
	await page.goto(`/news/${id}`);
	await page.getByPlaceholder('Написать комментарий…').fill('Встречаемся в 5');
	await page.getByRole('button', { name: 'Отправить' }).click();
	const comments = page.getByRole('region', { name: 'Комментарии' });
	await expect(comments.getByText('Встречаемся в 5')).toBeVisible();

	await comments.getByRole('button', { name: 'Действия с комментарием' }).click();
	await page.getByRole('menuitem', { name: 'Изменить' }).click();
	const box = comments.getByLabel('Изменить комментарий');
	await expect(box).toHaveValue('Встречаемся в 5');
	// Esc – отмена, без выхода из страницы и без изменений.
	await box.press('Escape');
	await expect(box).toHaveCount(0);
	await comments.getByRole('button', { name: 'Действия с комментарием' }).click();
	await page.getByRole('menuitem', { name: 'Изменить' }).click();
	await box.fill('Встречаемся в 6');
	await box.press('Control+Enter');
	await expect(comments.getByText('Встречаемся в 6')).toBeVisible();
	await expect(comments.getByText('· изменено')).toBeVisible();

	// После перезагрузки – то же.
	await page.reload();
	await expect(comments.getByText('Встречаемся в 6')).toBeVisible();
	await expect(comments.getByText('· изменено')).toBeVisible();
	expect(errors).toEqual([]);
});
