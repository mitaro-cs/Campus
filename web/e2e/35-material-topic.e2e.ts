import { expect, test } from '@playwright/test';
import { ADMIN, login, watchConsole } from './helpers';

test('тема в материалах: несколько файлов одним материалом, листаются', async ({ page }) => {
	const errors = watchConsole(page);
	await login(page, ADMIN);
	const { id, subject } = await page.evaluate(async () => {
		const csrf =
			document.cookie
				.split('; ')
				.find((c) => /^(__Host-)?gb_csrf=/.test(c))
				?.split('=')[1] ?? '';
		const up = async (name: string) =>
			(
				await (
					await fetch(`/api/files?name=${encodeURIComponent(name)}`, {
						method: 'POST',
						headers: { 'X-CSRF-Token': csrf, 'Content-Type': 'application/octet-stream' },
						body: `Конспект: ${name}\n`
					})
				).json()
			).id as number;
		const ids = [await up('Часть 1.txt'), await up('Часть 2.txt'), await up('Часть 3.txt')];
		const subjects = await (await fetch('/api/subjects')).json();
		const r = await fetch(`/api/subjects/${subjects[0].id}/materials/batch`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf },
			body: JSON.stringify({ fileIds: ids, topic: 'Билеты по частям', description: '' })
		});
		return { id: (await r.json())[0].id as number, subject: subjects[0].id as number };
	});

	await page.goto(`/subjects/${subject}?tab=materials`);
	const row = page.locator('.mrow', { hasText: 'Билеты по частям' });
	await expect(row.getByText('3 файла')).toBeVisible();

	await page.goto(`/materials/${id}`);
	const tiles = page.getByRole('list', { name: 'Файлы темы' });
	await expect(tiles.getByRole('listitem')).toHaveCount(3);
	await tiles.getByRole('button', { name: 'Открыть Часть 2.txt' }).click();
	const viewer = page.getByRole('dialog', { name: 'Просмотр: Часть 2.txt' });
	await expect(viewer).toBeVisible();
	await expect(viewer.getByText('2 из 3')).toBeVisible();
	await page.keyboard.press('ArrowRight');
	await expect(page.getByRole('dialog', { name: 'Просмотр: Часть 3.txt' })).toBeVisible();
	await page.keyboard.press('Escape');
	expect(errors).toEqual([]);
});
