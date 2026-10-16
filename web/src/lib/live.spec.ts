import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const offline = { version: 0, ready: false };
vi.mock('./offline/engine', () => ({
	enabled: () => false,
	flushOutbox: async () => {},
	syncNow: async () => {},
	offline
}));
vi.mock('./pwa.svelte', () => ({ pwa: { restarting: 0 } }));
vi.mock('./notify.svelte', () => ({ refreshUnread: () => {} }));
vi.mock('./moderation.svelte', () => ({ refreshModeration: () => {} }));
vi.mock('./session.svelte', () => ({ loadMe: async () => {} }));
vi.mock('./data.svelte', () => ({ loadSubjects: async () => {} }));
vi.mock('./presence.svelte', () => ({ loadPresence: () => {} }));

/** Поддельный EventSource: события и ошибки – руками из теста. */
class FakeSource {
	static CONNECTING = 0;
	static OPEN = 1;
	static CLOSED = 2;
	static all: FakeSource[] = [];
	readyState = FakeSource.CONNECTING;
	onerror: (() => void) | null = null;
	closed = false;
	private listeners = new Map<string, ((e: { data: string }) => void)[]>();
	constructor(public url: string) {
		FakeSource.all.push(this);
	}
	addEventListener(name: string, fn: (e: { data: string }) => void) {
		this.listeners.set(name, [...(this.listeners.get(name) ?? []), fn]);
	}
	emit(name: string, data: object) {
		this.readyState = FakeSource.OPEN;
		for (const fn of this.listeners.get(name) ?? []) fn({ data: JSON.stringify(data) });
	}
	fail() {
		this.readyState = FakeSource.CLOSED;
		this.onerror?.();
	}
	close() {
		this.closed = true;
		this.readyState = FakeSource.CLOSED;
	}
}

beforeEach(() => {
	vi.useFakeTimers();
	FakeSource.all = [];
	offline.version = 0;
	vi.stubGlobal('EventSource', FakeSource);
	vi.stubGlobal('document', {
		visibilityState: 'visible',
		addEventListener: () => {},
		removeEventListener: () => {}
	});
	vi.stubGlobal('addEventListener', () => {});
	vi.stubGlobal('removeEventListener', () => {});
});
afterEach(() => {
	vi.useRealTimers();
	vi.unstubAllGlobals();
});

describe('живые обновления', () => {
	it('после ответа с ошибкой поток открывается заново, а не молчит до перезагрузки', async () => {
		const { startLive } = await import('./live');
		const stop = startLive();
		FakeSource.all[0].emit('hello', { seq: 5 });
		FakeSource.all[0].fail();
		expect(FakeSource.all).toHaveLength(1);
		vi.advanceTimersByTime(2_000);
		expect(FakeSource.all).toHaveLength(2);
		// Пока потока не было, на сервере что-то изменилось – страница перечитает себя.
		FakeSource.all[1].emit('hello', { seq: 9 });
		vi.advanceTimersByTime(200);
		expect(offline.version).toBe(1);
		stop();
	});

	it('поток, который молчит больше минуты, открывается заново', async () => {
		const { startLive } = await import('./live');
		const stop = startLive();
		FakeSource.all[0].emit('hello', { seq: 1 });
		vi.advanceTimersByTime(45_000);
		FakeSource.all[0].emit('ping', { seq: 1 });
		vi.advanceTimersByTime(45_000);
		expect(FakeSource.all).toHaveLength(1);
		vi.advanceTimersByTime(30_000);
		expect(FakeSource.all[0].closed).toBe(true);
		expect(FakeSource.all).toHaveLength(2);
		stop();
	});

	it('«ping» с новым номером догоняет потерянное изменение', async () => {
		const { startLive } = await import('./live');
		const stop = startLive();
		FakeSource.all[0].emit('hello', { seq: 3 });
		FakeSource.all[0].emit('ping', { seq: 3 });
		vi.advanceTimersByTime(200);
		expect(offline.version).toBe(0);
		FakeSource.all[0].emit('ping', { seq: 4 });
		vi.advanceTimersByTime(200);
		expect(offline.version).toBe(1);
		stop();
	});
});
