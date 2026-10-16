import { isMarkdown } from './materialTypes';
import { del, post, put } from '$lib/api';
import { toast, toastError } from '$lib/toasts.svelte';
import type { Material } from '$lib/types';
import type { MenuItem } from '$lib/ui/Menu.svelte';
import { ask } from '$lib/ui/ask.svelte';
import { report } from '$lib/content/moderate';
import { session } from '$lib/session.svelte';

/** Окно правки – отдельным кусочком, создаётся на время правки и убирает себя само. */
async function editMaterial(m: Material, changed: () => void) {
	const [{ mount, unmount }, { default: MaterialEdit }] = await Promise.all([
		import('svelte'),
		import('./MaterialEdit.svelte')
	]);
	const app = mount(MaterialEdit, {
		target: document.body,
		props: { material: m, onsaved: changed, ondone: () => unmount(app) }
	});
}

export function materialActions(m: Material, changed: () => void): MenuItem[] {
	const out: MenuItem[] = [];
	const act = (path: string, message: string) => async () => {
		try {
			await post(`/api/materials/${m.id}/${path}`);
			toast(message, 'ok');
			changed();
		} catch (e) {
			toastError(e);
		}
	};
	if (m.file) {
		out.push({
			label: 'Скачать',
			onclick: () => (location.href = `/api/files/${m.file!.id}?download=true`)
		});
		// Конспект в Markdown – документом Word (сервер собирает .docx).
		if (isMarkdown(m.file.mime, m.file.name)) {
			out.push({
				label: 'Скачать в Word',
				onclick: () => (location.href = `/api/files/${m.file!.id}/docx`)
			});
		}
	}
	// Изменить (1.0.4) – автор и кто следит за порядком; материал на устройстве ещё не отправлен.
	if (m.can.edit && m.id > 0)
		out.push({ label: 'Изменить', onclick: () => editMaterial(m, changed) });
	// Закрепить сверху – староста, замы и модераторы (как закреплённые новости).
	if (m.can.pin && m.status === 'published') {
		out.push({
			label: m.pinnedAt ? 'Открепить' : 'Закрепить сверху',
			onclick: async () => {
				try {
					await put(`/api/materials/${m.id}/pinned`, { pinned: !m.pinnedAt });
					toast(m.pinnedAt ? 'Откреплено' : 'Закреплено сверху', 'ok');
					changed();
				} catch (e) {
					toastError(e);
				}
			}
		});
	}
	if (m.can.moderate && m.status === 'pending') {
		out.push({ label: 'Одобрить', onclick: act('approve', 'Материал опубликован') });
		out.push({ label: 'Отклонить', danger: true, onclick: act('reject', 'Материал отклонён') });
	}
	// «Скрыть у себя» (0.9.8) – личное: у остальных материал на месте, вернуть – в «Скрытых».
	if (m.id > 0 && m.status === 'published') {
		out.push({
			label: m.dismissed ? 'Показать у себя' : 'Скрыть у себя',
			onclick: async () => {
				try {
					await put(`/api/materials/${m.id}/dismissed`, { value: !m.dismissed });
					toast(m.dismissed ? 'Снова в списке' : 'Скрыто у вас – внизу, в «Скрытых»', 'ok');
					changed();
				} catch (e) {
					toastError(e);
				}
			}
		});
	}
	if (m.can.moderate && m.status === 'published') {
		out.push(
			m.hidden
				? { label: 'Вернуть всем', onclick: act('unhide', 'Материал снова виден') }
				: { label: 'Скрыть для всех', onclick: act('hide', 'Материал скрыт') }
		);
	}
	if (
		!m.can.moderate &&
		m.author.id !== session.me?.user.id &&
		m.id > 0 &&
		m.status === 'published'
	)
		out.push({ label: 'Пожаловаться', onclick: () => report('material', m.id) });
	if (m.can.delete) {
		out.push({
			label: 'Удалить',
			danger: true,
			onclick: async () => {
				if (!(await ask(`Удалить «${m.title}»?`, { ok: 'Удалить', danger: true }))) return;
				try {
					await del(`/api/materials/${m.id}`);
					toast('Удалено', 'ok');
					changed();
				} catch (e) {
					toastError(e);
				}
			}
		});
	}
	return out;
}
