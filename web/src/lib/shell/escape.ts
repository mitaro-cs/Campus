/**
 * Esc без выхода из полноэкранного режима (1.0.3). В полноэкранном окне macOS (Safari, окно хоста)
 * Esc, который страница не обработала, выводит из полноэкранного режима – даже когда им закрывали
 * фото или окно. Поэтому Esc всегда «обработан» здесь: сами закрываем то, что сверху, – меню или
 * подсказку (popover), иначе окно (dialog) – как это сделал бы браузер. Свои обработчики Esc
 * (палитра, меню, поля дат, тур) вызывают preventDefault – их Esc сюда не доходит.
 */
export function holdEscape() {
	addEventListener('keydown', (e) => {
		if (e.key !== 'Escape' || e.defaultPrevented || e.isComposing) return;
		e.preventDefault();
		let pops: Element[] = [];
		try {
			pops = [...document.querySelectorAll('[popover]:popover-open')];
		} catch {
			/* браузер без popover */
		}
		const pop = pops.pop() as HTMLElement | undefined;
		if (pop) return pop.hidePopover();
		const open = [...document.querySelectorAll<HTMLDialogElement>('dialog[open]')];
		const top = document.activeElement?.closest<HTMLDialogElement>('dialog[open]') ?? open.pop();
		if (top && top.dispatchEvent(new Event('cancel', { cancelable: true }))) top.close();
	});
}
