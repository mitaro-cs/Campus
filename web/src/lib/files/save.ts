/**
 * Скачать файл (1.0.4): через обычный запрос, который service worker отдаёт из копии на
 * устройстве, когда сервер не отвечает. Раньше «Скачать» шло только на сервер – открытый в просмотре
 * файл без сервера не скачивался («Сайт недоступен»). Большие (> 50 МБ) – прямой ссылкой, чтобы не
 * держать их в памяти.
 */
const IN_MEMORY = 50 * 1024 * 1024;

export async function saveFile(file: { id: number; name: string; size?: number }) {
	const direct = `/api/files/${file.id}?download=true`;
	if ((file.size ?? 0) > IN_MEMORY) return void (location.href = direct);
	try {
		const r = await fetch(`/api/files/${file.id}`, { credentials: 'same-origin' });
		const type = r.headers.get('content-type') ?? '';
		// Страница туннеля вместо файла (сервер выключен) – не файл.
		if (!r.ok || (type.startsWith('text/html') && !r.headers.get('x-groupbase')))
			throw new Error('нет файла');
		const url = URL.createObjectURL(await r.blob());
		const a = document.createElement('a');
		a.href = url;
		a.download = file.name;
		document.body.append(a);
		a.click();
		a.remove();
		setTimeout(() => URL.revokeObjectURL(url), 60_000);
	} catch {
		location.href = direct;
	}
}
