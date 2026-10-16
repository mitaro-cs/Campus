import { expect, test } from '@playwright/test';
import { ADMIN, login, watchConsole } from './helpers';

test('сообщение в материалах – с Markdown, его можно изменить', async ({ page }) => {
	const errors = watchConsole(page);
	await login(page, ADMIN);
	const id = await page.evaluate(async () => {
		const csrf =
			document.cookie
				.split('; ')
				.find((c) => /^(__Host-)?gb_csrf=/.test(c))
				?.split('=')[1] ?? '';
		const subjects = await (await fetch('/api/subjects')).json();
		const r = await fetch(`/api/subjects/${subjects[0].id}/materials`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf },
			body: JSON.stringify({
				kind: 'note',
				description: '## План текста\n\n- **введение**\n- вывод'
			})
		});
		return (await r.json()).id as number;
	});
	await page.goto(`/materials/${id}`);
	await expect(page.getByRole('heading', { level: 1, name: 'План текста' })).toBeVisible();
	// Разметка – заголовком и жирным, а не «##» и «**».
	await expect(page.getByRole('heading', { level: 2, name: 'План текста' })).toBeVisible();
	await expect(page.locator('.note-text strong')).toHaveText('введение');
	await expect(page.getByText('## План')).toHaveCount(0);

	await page.getByRole('button', { name: 'Действия' }).click();
	await page.getByRole('menuitem', { name: 'Изменить' }).click();
	const dialog = page.getByRole('dialog', { name: 'Изменить сообщение' });
	await expect(dialog.getByLabel('Текст сообщения')).toHaveValue(/## План текста/);
	await dialog.getByLabel('Название').fill('План сочинения');
	await dialog.getByLabel('Текст сообщения').fill('## План сочинения\n\n1. Вступление\n2. Итог');
	await dialog.getByRole('button', { name: 'Сохранить' }).click();
	await expect(dialog).toBeHidden();
	await expect(page.getByRole('heading', { level: 1, name: 'План сочинения' })).toBeVisible();
	await expect(page.locator('.note-text li')).toHaveText(['Вступление', 'Итог']);

	await page.reload();
	await expect(page.getByRole('heading', { level: 1, name: 'План сочинения' })).toBeVisible();
	expect(errors).toEqual([]);
});
