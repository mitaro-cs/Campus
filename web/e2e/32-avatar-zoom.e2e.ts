import { expect, test } from '@playwright/test';
import { ADMIN, login, watchConsole } from './helpers';

test('аватарку можно открыть крупно и приблизить', async ({ page }) => {
	const errors = watchConsole(page);
	await login(page, ADMIN);
	// Своё фото – картинкой 600×600 из canvas.
	const name = await page.evaluate(async () => {
		const csrf =
			document.cookie
				.split('; ')
				.find((c) => /^(__Host-)?gb_csrf=/.test(c))
				?.split('=')[1] ?? '';
		const c = document.createElement('canvas');
		c.width = c.height = 600;
		const g = c.getContext('2d')!;
		g.fillStyle = '#3b82f6';
		g.fillRect(0, 0, 600, 600);
		g.fillStyle = '#fff';
		g.fillRect(150, 150, 300, 300);
		const blob = await new Promise<Blob>((r) => c.toBlob((b) => r(b!), 'image/png'));
		await fetch('/api/me/avatar', {
			method: 'PUT',
			headers: { 'X-CSRF-Token': csrf, 'Content-Type': 'image/png' },
			body: blob
		});
		return (await (await fetch('/api/me')).json()).user.displayName as string;
	});
	await page.goto('/members');
	await page
		.getByRole('button', { name: `Фото: ${name}` })
		.first()
		.click();
	const viewer = page.getByRole('dialog', { name: `Просмотр: ${name}` });
	await expect(viewer).toBeVisible();
	const img = viewer.locator('img');
	await expect(img).toHaveAttribute('src', /-768\.webp$/);
	await expect.poll(() => img.evaluate((i: HTMLImageElement) => i.naturalWidth)).toBe(600);
	await viewer.getByRole('button', { name: 'Увеличить' }).click();
	await expect(viewer.getByText('125%')).toBeVisible();
	await page.keyboard.press('Escape');
	await expect(viewer).toBeHidden();
	await expect(page).toHaveURL(/\/members/);
	expect(errors).toEqual([]);
});
