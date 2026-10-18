/**
 * Анимации (1.0.6, просьба владельца): своя настройка вместо системного «уменьшить движение».
 * В Windows «Эффекты анимации» часто выключены сами (быстродействие, удалённый стол), в iPhone –
 * «Уменьшение движения», и Campus был без единой анимации. Теперь по умолчанию – «Всегда», а «Как в
 * системе» и «Выключены» – в «Профиль → Приложение». Итог – `data-motion` на <html> («reduce» –
 * без анимаций); его же ставит скрипт в app.html до отрисовки. CSS проверяет атрибут, а не
 * `@media (prefers-reduced-motion)`.
 */
export type MotionPref = 'on' | 'system' | 'off';

const KEY = 'gb-motion';

export function motionPref(): MotionPref {
	try {
		const v = localStorage.getItem(KEY);
		return v === 'system' || v === 'off' ? v : 'on';
	} catch {
		return 'on';
	}
}

/** Сейчас без анимаций. */
export function reducedMotion(): boolean {
	return typeof document !== 'undefined' && document.documentElement.dataset.motion === 'reduce';
}

export function applyMotion(): void {
	if (typeof document === 'undefined') return;
	const p = motionPref();
	const reduce =
		p === 'off' ||
		(p === 'system' &&
			typeof matchMedia !== 'undefined' &&
			matchMedia('(prefers-reduced-motion: reduce)').matches);
	document.documentElement.dataset.motion = reduce ? 'reduce' : 'full';
}

export function setMotion(p: MotionPref): void {
	try {
		localStorage.setItem(KEY, p);
	} catch {
		/* приватный режим – только до перезагрузки */
	}
	applyMotion();
}
