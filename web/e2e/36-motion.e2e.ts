import { expect, test } from '@playwright/test';
import { ADMIN, login, watchConsole } from './helpers';

test('анимации: по умолчанию – всегда, даже когда система просит меньше движения', async ({
	page
}) => {
	const errors = watchConsole(page);
	// Как Windows с выключенными «Эффектами анимации» и iPhone с «Уменьшением движения».
	await page.emulateMedia({ reducedMotion: 'reduce' });
	await login(page, ADMIN);
	const motion = () => page.evaluate(() => document.documentElement.dataset.motion);
	expect(await motion()).toBe('full');

	await page.goto('/profile?tab=appearance');
	const group = page.getByRole('radiogroup', { name: /Анимации/ });
	await group.getByRole('radio', { name: 'По системе' }).click();
	expect(await motion()).toBe('reduce');
	// Выбор помнится и до отрисовки (скрипт в app.html).
	await page.reload();
	expect(await motion()).toBe('reduce');
	await group.getByRole('radio', { name: 'Всегда' }).click();
	expect(await motion()).toBe('full');
	expect(errors).toEqual([]);
});
