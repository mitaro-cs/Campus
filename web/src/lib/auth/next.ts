/**
 * Куда вернуться после входа (аудит 1.0.2): только свой сайт. Браузер выбрасывает из адреса табы и
 * переводы строк – «/\t/чужой.сайт» стал бы «//чужой.сайт», поэтому адрес разбираем как браузер и
 * сверяем источник, а не смотрим на первые символы.
 */
export function safeNext(raw: string | null, origin: string): string {
	if (!raw || !raw.startsWith('/')) return '/';
	try {
		const u = new URL(raw, origin);
		if (u.origin !== origin) return '/';
		return u.pathname + u.search + u.hash;
	} catch {
		return '/';
	}
}
