<script lang="ts">
	import { CircleAlert, CircleCheck, RefreshCw } from '@lucide/svelte';
	import { onMount } from 'svelte';
	import { get, post, put } from '$lib/api';
	import { fmtAgo } from '$lib/format';
	import { session } from '$lib/session.svelte';
	import { toastError } from '$lib/toasts.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Switch from '$lib/ui/Switch.svelte';
	import { appIcon, iconSrc } from '$lib/appIcon.svelte';
	import UpdateCard from './server/UpdateCard.svelte';
	import type { Status } from './server/types';

	// Какая версия стоит и есть ли новее: GitHub спрашивает сервер, в приложении хоста – ещё и оболочка.
	let s = $state<Status | null>(null);
	let checking = $state(false);
	/** Ставить обновления ночью, когда на сайте никого нет (1.0.3, только приложение хоста). */
	let night = $state<boolean | null>(null);

	async function setNight(v: boolean) {
		try {
			night = (await put<{ night: boolean }>('/api/admin/updates/night', { night: v })).night;
		} catch (e) {
			toastError(e);
		}
	}
	let alive = true;

	const version = $derived(s?.version ?? session.me?.instance.version ?? '');
	const status = $derived.by((): { tone: 'ok' | 'bad' | 'muted'; text: string } => {
		if (!s) return { tone: 'muted', text: 'Узнаём, что с обновлениями…' };
		if (s.update) return { tone: 'ok', text: `Вышла версия ${s.update.version}` };
		const c = s.check;
		if (checking) return { tone: 'muted', text: 'Проверяем…' };
		if (c?.error) return { tone: 'bad', text: `Не удалось проверить: ${c.error}` };
		if (c?.latest && c.checkedAt)
			return { tone: 'ok', text: `Это последняя версия · проверено ${fmtAgo(c.checkedAt)}` };
		return { tone: 'muted', text: 'Проверьте, не вышла ли новая версия' };
	});

	async function check() {
		checking = true;
		try {
			s = await post<Status>('/api/admin/update-check', {});
		} catch (e) {
			toastError(e);
			return;
		} finally {
			checking = false;
		}
		// В окне хоста оболочка проверяет и сама: её ответ приходит через пару секунд – тогда
		// появится кнопка «Обновить сейчас».
		for (const wait of [3000, 6000]) {
			if (!alive || !session.me?.hostWindow || s?.canUpdate) break;
			await new Promise((r) => setTimeout(r, wait));
			if (!alive) break;
			try {
				s = await get<Status>('/api/admin/status');
			} catch {
				break;
			}
		}
	}

	onMount(() => {
		(async () => {
			try {
				s = await get<Status>('/api/admin/status');
			} catch (e) {
				toastError(e);
				return;
			}
			if (s.desktop)
				get<{ night: boolean }>('/api/admin/updates/night')
					.then((r) => (night = r.night))
					.catch(() => {});
			// Ни разу не проверяли (или сервер старше 0.4.7) – проверяем сразу, без кнопки.
			if (alive && !s.update && !s.check?.checkedAt) await check();
		})();
		return () => {
			alive = false;
		};
	});
</script>

<section class="card ver">
	<img class="mark" src={iconSrc(appIcon.id)} alt="" width="56" height="56" />
	<div class="txt">
		<span class="small muted">Установлена версия</span>
		<strong class="num">Campus {version}</strong>
		<span class="state small {status.tone}" role="status">
			{#if status.tone === 'ok'}<CircleCheck
					size={15}
				/>{:else if status.tone === 'bad'}<CircleAlert size={15} />{/if}
			{status.text}
		</span>
	</div>
</section>

{#if s}<UpdateCard {s} />{/if}

<div class="row wrap">
	<Button variant={s?.update ? 'secondary' : 'primary'} loading={checking} onclick={check}
		><RefreshCw size={16} /> Проверить обновления</Button
	>
</div>

{#if s?.desktop && night !== null}
	<div class="night">
		<Switch
			checked={night}
			label="Ставить обновления ночью, когда на сайте никого нет"
			onchange={setNight}
		/>
	</div>
{/if}

<p class="small muted about">
	{#if s?.desktop}
		Приложение само проверяет обновления после запуска и раз в 6 часов. Новая версия скачивается
		заранее, пока сайт работает; на время перезапуска (секунд 20–30) у группы – «Campus
		обновляется», изменения ждут в очереди и уходят сами, а страница сама переходит на новую версию.
	{:else}
		Сервер сам проверяет GitHub раз в 12 часов. Обновление – новый groupbase.jar со страницы выпуска
		и перезапуск сервера, данные сохраняются.
	{/if}
</p>

<style>
	.night {
		margin-top: var(--s4);
	}
	.ver {
		display: flex;
		align-items: center;
		gap: var(--s4);
		margin-bottom: var(--s3);
	}
	.mark {
		flex: none;
		border-radius: 14px;
		box-shadow: 0 0 0 1px var(--border);
	}
	.txt {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	strong {
		font-size: 22px;
		letter-spacing: -0.02em;
	}
	.state {
		display: inline-flex;
		align-items: flex-start;
		gap: 6px;
		margin-top: 2px;
	}
	.state :global(svg) {
		flex: none;
		margin-top: 2px;
	}
	.state.ok {
		color: var(--ok);
	}
	.state.bad {
		color: var(--danger);
	}
	.state.muted {
		color: var(--text-3);
	}
	.about {
		margin-top: var(--s3);
		max-width: 60ch;
	}
</style>
