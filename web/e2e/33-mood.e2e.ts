import { expect, test } from '@playwright/test';
import { ADMIN, login, watchConsole } from './helpers';

test('статус «для себя»: придумать, сохранить и увидеть у участников', async ({ page }) => {
	const errors = watchConsole(page);
	await login(page, ADMIN);
	await page.goto('/profile?tab=account');
	const field = page.getByLabel('Статус', { exact: true });
	await page.getByRole('button', { name: 'Придумать статус' }).click();
	await expect(field).not.toHaveValue('');
	await field.fill('ищу зачёт по матану 🔍');
	await page.getByRole('button', { name: 'Сохранить' }).first().click();
	await expect(page.getByText('Статус обновлён')).toBeVisible();

	await page.goto('/members');
	await expect(page.getByText('ищу зачёт по матану 🔍')).toBeVisible();
	expect(errors).toEqual([]);
});
