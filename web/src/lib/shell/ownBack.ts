/**
 * «Назад» своей кнопкой или своим жестом (1.0.4). Для браузера это тот же popstate, что и системный
 * жест Safari, – а системный уже анимирован, и переход (transition.ts) его пропускает. Своё «назад»
 * помечаем, чтобы страница въезжала, а не менялась рывком.
 */
let at = -Infinity;

export function backOwn(): void {
	at = performance.now();
	history.back();
}

/** Был ли этот popstate от своей кнопки (метка живёт секунду и снимается при чтении). */
export function takeOwnBack(): boolean {
	const own = performance.now() - at < 1000;
	at = -Infinity;
	return own;
}
