import { describe, expect, it } from 'vitest';
import { safeNext } from './next';

const O = 'https://bik2401.example';

describe('safeNext', () => {
	it('свой адрес – как есть', () => {
		expect(safeNext('/homework/5?tab=1#c', O)).toBe('/homework/5?tab=1#c');
		expect(safeNext('/invite/abc', O)).toBe('/invite/abc');
	});

	it('чужой сайт и мусор – на главную', () => {
		for (const bad of [
			null,
			'',
			'https://evil.example/',
			'//evil.example',
			'/\\evil.example',
			'/\t/evil.example',
			'/\n/evil.example',
			'javascript:alert(1)'
		])
			expect(safeNext(bad, O)).toBe('/');
	});
});
