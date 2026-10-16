import { enabled as offlineEnabled, flushOutbox, offline, syncNow } from './offline/engine';
import { pwa } from './pwa.svelte';
import { refreshUnread } from './notify.svelte';
import { refreshModeration } from './moderation.svelte';
import { loadMe } from './session.svelte';
import { loadSubjects } from './data.svelte';
import { loadPresence } from './presence.svelte';

/**
 * Живые обновления: сервер сообщает, что что-то изменилось (комментарий, новость, задание,
 * материал, а с 0.9.7 – любое изменение: люди, роли, группа, предметы, настройки), – открытые
 * страницы перечитывают себя сами, без перезагрузки. Соединение держим, пока
 * вкладка на экране: на телефоне через туннель одновременно открыто не больше 6 соединений. Если
 * поток не доходит (туннель копит ответ – «hello» не пришло за 8 секунд), раз в 3 секунды
 * спрашиваем короткий номер состояния (`/api/live/seq`) и перечитываем страницу, только когда он
 * сменился (1.0.2: раньше раз в 10 секунд перечитывали всё вслепую – комментарии запаздывали).
 *
 * Поток не должен умирать молча (1.0.4): ответ с ошибкой (перезапуск хоста, страница туннеля)
 * закрывает EventSource навсегда – открываем заново с паузой; сервер шлёт «ping» раз в 25 с – нет
 * ничего дольше минуты (телефон спал, соединение повисло) – переподключаемся; вернулись на вкладку
 * или в сеть – проверяем сразу. Номер из «ping» сверяем: потерянный «change» тоже догоняется.
 */
export function startLive(): () => void {
	if (typeof EventSource === 'undefined') return () => {};
	let source: EventSource | null = null;
	let lastSeq = 0;
	let polling = false;
	let helloTimer: ReturnType<typeof setTimeout> | undefined;
	let pollTimer: ReturnType<typeof setInterval> | undefined;
	let hideTimer: ReturnType<typeof setTimeout> | undefined;
	let refreshTimer: ReturnType<typeof setTimeout> | undefined;
	let retryTimer: ReturnType<typeof setTimeout> | undefined;
	/** Когда поток последний раз подавал голос (любое событие, включая «ping»). */
	let heard = 0;
	/** Пауза перед повторным подключением после ошибки: 2, 4, 8… до 30 с. */
	let backoff = 2_000;
	const SILENT = 60_000;

	// Несколько изменений подряд (задание с файлами) – одно перечитывание.
	function refresh() {
		clearTimeout(refreshTimer);
		refreshTimer = setTimeout(() => {
			// Страницы перечитывают себя сразу (не все изменения попадают в копию на устройстве –
			// роли, группа, настройки), копия обновляется следом.
			offline.version++;
			if (offline.ready && offlineEnabled()) syncNow();
			refreshModeration();
			// Роли, группы, правила и предметы – в шапке и меню на каждой странице.
			loadMe().catch(() => {});
			loadSubjects().catch(() => {});
		}, 120);
	}

	// Бесшовное обновление (1.0.3): сервер говорит «сейчас перезапуск» – плашка «Campus обновляется»,
	// изменения ждут в очереди. Вернулся (hello или ответ без «restarting») – очередь уходит сразу,
	// страница перечитывает себя, а новая версия сайта подтягивается и ставится, когда удобно.
	let stuck: ReturnType<typeof setTimeout> | undefined;
	function restarting(on: boolean) {
		if (on) {
			if (pwa.restarting) return;
			pwa.restarting = Date.now();
			// Не вернулся за 3 минуты – это уже не обновление: обычная плашка «Сервер не работает».
			stuck = setTimeout(() => (pwa.restarting = 0), 180_000);
			return;
		}
		if (!pwa.restarting) return;
		pwa.restarting = 0;
		clearTimeout(stuck);
		if (offline.ready && offlineEnabled())
			flushOutbox()
				.then(() => syncNow())
				.catch(() => {});
		refresh();
		navigator.serviceWorker
			?.getRegistration()
			.then((r) => r?.update())
			.catch(() => {});
	}

	function data(e: MessageEvent): { seq?: number; restarting?: boolean } {
		try {
			return JSON.parse(e.data);
		} catch {
			return {};
		}
	}

	function seqOf(e: MessageEvent): number {
		try {
			return Number(JSON.parse(e.data).seq) || 0;
		} catch {
			return 0;
		}
	}

	/** Номер состояния с сервера: отличается – перечитываем (номер может и уменьшиться – копия на
	 *  втором компьютере хоста взяла снимок). */
	function seen(seq: number) {
		if (lastSeq && seq && seq !== lastSeq) refresh();
		if (seq) lastSeq = seq;
	}

	function open() {
		if (source || polling) return;
		clearTimeout(retryTimer);
		retryTimer = undefined;
		const s = new EventSource('/api/live');
		source = s;
		heard = Date.now();
		helloTimer = setTimeout(fallBack, 8000);
		const alive = () => {
			heard = Date.now();
		};
		s.addEventListener('ping', (e) => {
			alive();
			seen(seqOf(e as MessageEvent));
		});
		s.onerror = () => {
			// Обычный обрыв EventSource переподключает сам (CONNECTING); ответ с ошибкой – CLOSED, и
			// дальше он молчит навсегда: открываем заново сами.
			if (source !== s || s.readyState !== EventSource.CLOSED) return;
			close();
			retryTimer = setTimeout(open, backoff);
			// Пока потока нет – не пропустить изменения: перечитаем, когда он вернётся (hello).
			backoff = Math.min(backoff * 2, 30_000);
		};
		s.addEventListener('hello', (e) => {
			clearTimeout(helloTimer);
			alive();
			backoff = 2_000;
			restarting(!!data(e as MessageEvent).restarting);
			// Переподключились после обрыва, а на сервере уже что-то новое.
			seen(seqOf(e as MessageEvent));
			loadPresence(true);
		});
		// Кто-то открыл или закрыл сайт (1.0.2) – «на сайте» и точки у участников.
		s.addEventListener('presence', () => {
			alive();
			loadPresence(true);
		});
		s.addEventListener('change', (e) => {
			alive();
			lastSeq = seqOf(e as MessageEvent) || lastSeq;
			refresh();
		});
		s.addEventListener('bell', () => {
			alive();
			refreshUnread();
		});
		s.addEventListener('restarting', () => {
			alive();
			restarting(true);
		});
	}

	function close() {
		clearTimeout(helloTimer);
		clearTimeout(retryTimer);
		retryTimer = undefined;
		source?.close();
		source = null;
	}

	/** Поток открыт, но молчит дольше минуты (повис после сна, туннель потерял его) – заново. */
	function revive() {
		if (polling || document.visibilityState !== 'visible') return;
		if (!source) {
			if (!retryTimer) open();
			return;
		}
		if (Date.now() - heard > SILENT) {
			close();
			open();
		}
	}

	function fallBack() {
		close();
		polling = true;
		let ticks = 0;
		pollTimer = setInterval(async () => {
			if (document.visibilityState !== 'visible') return;
			if (++ticks % 5 === 0) loadPresence(true);
			try {
				const res = await fetch('/api/live/seq', { credentials: 'same-origin' });
				if (!res.ok) return;
				const json = await res.json();
				restarting(!!json.restarting);
				const seq = Number(json.seq) || 0;
				if (seq !== lastSeq) {
					lastSeq = seq;
					refresh();
				}
			} catch {
				/* нет связи – спросим через 3 секунды */
			}
		}, 3_000);
	}

	function onVisibility() {
		clearTimeout(hideTimer);
		if (document.visibilityState === 'visible') {
			if (!source && !polling) {
				open();
				refresh();
			} else if (polling) refresh();
			else revive();
		} else {
			hideTimer = setTimeout(close, 30_000);
		}
	}

	// Сеть вернулась – поток мог умереть, пока её не было: открываем заново и перечитываем.
	function onOnline() {
		if (polling || document.visibilityState !== 'visible') return;
		close();
		backoff = 2_000;
		open();
		refresh();
	}

	open();
	const watchTimer = setInterval(revive, 15_000);
	document.addEventListener('visibilitychange', onVisibility);
	addEventListener('online', onOnline);
	return () => {
		close();
		clearInterval(pollTimer);
		clearInterval(watchTimer);
		removeEventListener('online', onOnline);
		clearTimeout(hideTimer);
		clearTimeout(refreshTimer);
		clearTimeout(stuck);
		document.removeEventListener('visibilitychange', onVisibility);
	};
}
