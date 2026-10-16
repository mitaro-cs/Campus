<script lang="ts">
	import { put } from '$lib/api';
	import { firstName } from '$lib/names';
	import { session } from '$lib/session.svelte';
	import { toastError } from '$lib/toasts.svelte';
	import type { Reaction } from '$lib/types';

	// Реакции на новость (1.0.2) и комментарий (1.0.4): нажатие видно сразу, сервер подтверждает
	// следом; у остальных – по живым обновлениям. Из NewsCard грузится лениво (бюджет «Сегодня»),
	// место под строку занято. off – запись ещё не на сервере (создана без сети).
	let {
		endpoint,
		reactions = [],
		off = false,
		small = false
	}: { endpoint: string; reactions?: Reaction[]; off?: boolean; small?: boolean } = $props();

	// С 1.0.4 – и несогласие (просьба владельца); порядок – как на сервере (Reactions.ALL).
	const ALL = ['👍', '👎', '❤️', '🔥', '😂', '😮', '😢', '🤡', '❓'];

	let list = $state<Reaction[]>([]);
	let picking = $state(false);
	let busy = 0;
	// Новый ответ сервера (живое обновление ленты) – новый список, пока не ждём своего.
	$effect(() => {
		const next = reactions ?? [];
		if (!busy) list = next;
	});

	const who = (r: Reaction) =>
		r.who.map(firstName).join(', ') +
		(r.count > r.who.length ? ` и ещё ${r.count - r.who.length}` : '');

	async function toggle(emoji: string) {
		picking = false;
		// Новость без сети (ещё не на сервере) – реакции потом.
		if (!session.me || off) return;
		const before = list;
		const cur = list.find((r) => r.emoji === emoji);
		const on = !cur?.mine;
		const me = session.me.user.displayName;
		const changed: Reaction = cur
			? {
					...cur,
					mine: on,
					count: cur.count + (on ? 1 : -1),
					who: on ? [...cur.who, me] : cur.who.filter((n) => n !== me)
				}
			: { emoji, count: 1, mine: true, who: [me] };
		list = ALL.flatMap((e) =>
			e === emoji ? (changed.count > 0 ? [changed] : []) : list.filter((r) => r.emoji === e)
		);
		busy++;
		try {
			list = await put<Reaction[]>(endpoint, { emoji, value: on });
		} catch (e) {
			list = before;
			toastError(e);
		} finally {
			busy--;
		}
	}

	let root: HTMLElement | undefined = $state();
	// Нажали мимо выбора – закрыть.
	$effect(() => {
		if (!picking) return;
		const away = (e: PointerEvent) => {
			if (!root?.contains(e.target as Node)) picking = false;
		};
		document.addEventListener('pointerdown', away);
		return () => document.removeEventListener('pointerdown', away);
	});

	function onkeydown(e: KeyboardEvent) {
		if (e.key === 'Escape' && picking) {
			e.preventDefault();
			e.stopPropagation();
			picking = false;
		}
	}
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div class="reactions" class:small bind:this={root} {onkeydown}>
	{#each list as r (r.emoji)}
		<button
			type="button"
			class="r"
			class:mine={r.mine}
			aria-pressed={r.mine}
			title={who(r)}
			aria-label="{r.emoji} {r.count}: {who(r)}"
			onclick={() => toggle(r.emoji)}
			><span class="e">{r.emoji}</span><span class="num">{r.count}</span></button
		>
	{/each}
	{#if picking}
		<span class="pick" role="group" aria-label="Выберите реакцию">
			{#each ALL as e (e)}
				<button type="button" class="e-btn" aria-label={e} onclick={() => toggle(e)}>{e}</button>
			{/each}
		</span>
	{:else}
		<button
			type="button"
			class="r add"
			aria-label="Поставить реакцию"
			title="Поставить реакцию"
			onclick={() => (picking = true)}>☺︎<b>+</b></button
		>
	{/if}
</div>

<style>
	.reactions {
		position: relative;
		z-index: 2;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 6px;
	}
	.r {
		display: inline-flex;
		align-items: center;
		gap: 5px;
		height: 28px;
		padding: 0 10px;
		border: 1px solid var(--border);
		border-radius: var(--r-full);
		background: var(--surface);
		color: var(--text-2);
		font: inherit;
		font-size: 13px;
		font-weight: 600;
		transition:
			background-color var(--dur) var(--ease),
			border-color var(--dur) var(--ease),
			transform 120ms var(--ease);
	}
	.r:active {
		transform: scale(0.92);
	}
	.r .e {
		font-size: 15px;
		line-height: 1;
	}
	.r.mine {
		border-color: color-mix(in srgb, var(--accent) 55%, transparent);
		background: color-mix(in srgb, var(--accent) 12%, var(--surface));
		color: var(--text);
	}
	.add {
		gap: 1px;
		padding: 0 9px;
		color: var(--text-3);
		font-size: 15px;
	}
	.add b {
		font-size: 12px;
	}
	.pick {
		display: inline-flex;
		flex-wrap: wrap;
		max-width: 100%;
		gap: 2px;
		padding: 2px 4px;
		border: 1px solid var(--border);
		border-radius: var(--r-full);
		background: var(--surface);
		box-shadow: var(--shadow-1, 0 4px 16px rgb(0 0 0 / 0.08));
		animation: pick-in 160ms var(--ease);
	}
	.e-btn {
		display: grid;
		place-items: center;
		width: 30px;
		height: 30px;
		border: 0;
		border-radius: 50%;
		background: none;
		font-size: 18px;
		transition: transform 120ms var(--ease);
	}
	.e-btn:hover {
		transform: scale(1.2);
	}
	/* В комментарии – мельче и без рамки, пока реакций нет. */
	.small .r {
		height: 24px;
		padding: 0 8px;
		font-size: 12px;
	}
	.small .r .e {
		font-size: 13px;
	}
	.small .add {
		border-color: transparent;
		background: none;
	}
	.small .e-btn {
		width: 28px;
		height: 28px;
		font-size: 16px;
	}
	@keyframes pick-in {
		from {
			opacity: 0;
			transform: scale(0.9);
		}
	}
</style>
