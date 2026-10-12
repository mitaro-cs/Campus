<script lang="ts">
	import {
		ClipboardList,
		Crown,
		FileText,
		MessageCircle,
		Newspaper,
		Sparkles,
		Trophy,
		Zap
	} from '@lucide/svelte';
	import { untrack } from 'svelte';
	import { get } from '$lib/api';
	import { fmtAgo, plural } from '$lib/format';
	import { offline } from '$lib/offline/engine';
	import { session } from '$lib/session.svelte';
	import Avatar from '$lib/ui/Avatar.svelte';
	import Empty from '$lib/ui/Empty.svelte';
	import Tabs from '$lib/ui/Tabs.svelte';

	// Рейтинг активности группы (1.0.4): кто больше всех делает для группы. Очки – за вклад, который
	// видят все (материалы, задания, новости, комментарии); реакции и дни – статистика без очков.
	interface Row {
		userId: number;
		displayName: string;
		avatar: string | null;
		role: string;
		place: number;
		points: number;
		materials: number;
		homework: number;
		news: number;
		comments: number;
		reactions: number;
		days: number;
		lastAt: number;
	}
	interface Board {
		period: string;
		from: number;
		people: number;
		rows: Row[];
		myDone: number;
	}

	let { groupId }: { groupId: number } = $props();

	const PERIODS = [
		{ label: 'Неделя', value: 'week' },
		{ label: 'Месяц', value: 'month' },
		{ label: 'Всё время', value: 'all' }
	];
	let period = $state('month');
	let board = $state<Board | null>(null);
	let failed = $state(false);

	$effect(() => {
		const url = `/api/groups/${groupId}/activity?period=${period}`;
		void offline.version;
		untrack(() => {
			get<Board>(url).then(
				(b) => ((board = b), (failed = false)),
				() => (failed = !board)
			);
		});
	});

	const me = $derived(session.me?.user.id);
	const rows = $derived(board?.rows ?? []);
	const top = $derived(rows.slice(0, 3));
	const mine = $derived(rows.find((r) => r.userId === me));
	const best = $derived(Math.max(1, ...rows.map((r) => r.points)));
	/** Пьедестал: второй – слева, первый – в центре, третий – справа. */
	const podium = $derived(top.length === 1 ? top : [top[1], top[0], ...top.slice(2)]);
	const WHEN: Record<string, string> = {
		week: 'за неделю',
		month: 'за месяц',
		all: 'за всё время'
	};

	const pts = (n: number) => `${n} ${plural(n, ['очко', 'очка', 'очков'])}`;
</script>

<div class="board">
	<Tabs tabs={PERIODS} value={period} onchange={(v) => (period = v)} label="Период" />

	{#if failed}
		<Empty title="Рейтинг сейчас недоступен" text="Он появится, когда будет связь с сервером." />
	{:else if board && !rows.length}
		<Empty
			title="Пока тихо"
			text="Добавьте материал, задание или новость {WHEN[period]} – и окажетесь первым в рейтинге."
		/>
	{:else if board}
		<ol class="podium" style:--n={podium.length} aria-label="Лидеры {WHEN[period]}">
			{#each podium as r (r.userId)}
				<li class="step p{r.place > 3 ? 3 : r.place}" class:me={r.userId === me}>
					<span class="ava">
						{#if r.place === 1}<span class="crown" aria-hidden="true"><Crown size={20} /></span
							>{/if}
						<Avatar
							id={r.userId}
							name={r.displayName}
							avatar={r.avatar}
							size={r.place === 1 ? 64 : 52}
						/>
						<span class="medal num" aria-label="{r.place} место">{r.place}</span>
					</span>
					<strong class="name">{r.displayName}</strong>
					<span class="pts num">{pts(r.points)}</span>
					<span class="block" aria-hidden="true"></span>
				</li>
			{/each}
		</ol>

		<div class="mine card">
			<Trophy size={18} />
			{#if mine}
				<span
					>Вы на <strong class="num">{mine.place}</strong> месте из {rows.length} · {pts(
						mine.points
					)}</span
				>
			{:else}
				<span>Вас пока нет в рейтинге {WHEN[period]} – добавьте что-нибудь для группы</span>
			{/if}
			{#if board.myDone}
				<span class="done num"
					>сделано {board.myDone}
					{plural(board.myDone, ['задание', 'задания', 'заданий'])}</span
				>
			{/if}
		</div>

		<ul class="ranks card" aria-label="Рейтинг">
			{#each rows as r (r.userId)}
				<li class:me={r.userId === me} class:leader={r.place <= 3}>
					<span class="place num">{r.place}</span>
					<Avatar id={r.userId} name={r.displayName} avatar={r.avatar} size={40} />
					<div class="who">
						<div class="line">
							<strong>{r.displayName}</strong>{#if r.userId === me}<span class="you">вы</span>{/if}
						</div>
						<div class="facts num">
							{#if r.materials}<span aria-label="Материалы"
									><FileText size={14} />{r.materials}</span
								>{/if}
							{#if r.homework}<span aria-label="Задания"
									><ClipboardList size={14} />{r.homework}</span
								>{/if}
							{#if r.news}<span aria-label="Новости"><Newspaper size={14} />{r.news}</span>{/if}
							{#if r.comments}<span aria-label="Комментарии"
									><MessageCircle size={14} />{r.comments}</span
								>{/if}
							{#if r.reactions}<span aria-label="Реакции"><Sparkles size={14} />{r.reactions}</span
								>{/if}
							<span aria-label="Активных дней"
								><Zap size={14} />{r.days} {plural(r.days, ['день', 'дня', 'дней'])}</span
							>
							<span class="ago">{fmtAgo(r.lastAt)}</span>
						</div>
						<span class="bar" aria-hidden="true"
							><span style:width="{Math.round((r.points / best) * 100)}%"></span></span
						>
					</div>
					<span class="score num"
						><strong>{r.points}</strong><small>{plural(r.points, ['очко', 'очка', 'очков'])}</small
						></span
					>
				</li>
			{/each}
		</ul>

		<div class="how faint small">
			<p class="legend">
				<span><FileText size={13} /> материалы</span>
				<span><ClipboardList size={13} /> задания</span>
				<span><Newspaper size={13} /> новости</span>
				<span><MessageCircle size={13} /> комментарии</span>
				<span><Sparkles size={13} /> реакции</span>
				<span><Zap size={13} /> дни, когда что-то добавлял</span>
			</p>
			<p>
				Очки {WHEN[period]}: материал и задание – 3, новость – 2, комментарий – 1. Реакции и дни –
				для статистики. Скрытое модератором и материалы на проверке не считаются; отметки «сделано»
				видите только вы.
			</p>
		</div>
	{:else}
		<div class="skeleton" aria-hidden="true"></div>
	{/if}
</div>

<style>
	.board {
		--gold: #e2a619;
		--silver: #9aa6b4;
		--bronze: #c07a43;
		display: flex;
		flex-direction: column;
		gap: var(--s3);
	}
	.podium {
		display: grid;
		grid-template-columns: repeat(var(--n), minmax(0, 1fr));
		align-items: end;
		gap: var(--s2);
		max-width: calc(var(--n) * 207px);
		width: 100%;
		margin: var(--s3) auto 0;
		padding: 0;
		list-style: none;
	}
	.step {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 4px;
		min-width: 0;
		text-align: center;
		animation: rise 520ms var(--ease) both;
	}
	.step.p1 {
		--c: var(--gold);
		animation-delay: 120ms;
	}
	.step.p2 {
		--c: var(--silver);
	}
	.step.p3 {
		--c: var(--bronze);
		animation-delay: 60ms;
	}
	.ava {
		position: relative;
		display: grid;
		place-items: center;
		padding: 3px;
		border-radius: 50%;
		background: var(--c);
		box-shadow: 0 6px 22px color-mix(in srgb, var(--c) 40%, transparent);
	}
	.ava :global(.avatar) {
		border: 2px solid var(--surface);
	}
	.crown {
		position: absolute;
		top: -22px;
		color: var(--gold);
		filter: drop-shadow(0 2px 4px color-mix(in srgb, var(--gold) 50%, transparent));
	}
	.medal {
		position: absolute;
		right: -4px;
		bottom: -4px;
		display: grid;
		place-items: center;
		width: 22px;
		height: 22px;
		border: 2px solid var(--surface);
		border-radius: 50%;
		background: var(--c);
		color: #fff;
		font-size: 12px;
		font-weight: 800;
	}
	.name {
		max-width: 100%;
		margin-top: 6px;
		overflow: hidden;
		font-size: 14px;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.pts {
		color: var(--text-2);
		font-size: 13px;
		font-weight: 650;
	}
	.block {
		width: 100%;
		margin-top: 6px;
		border-radius: 14px 14px 4px 4px;
		background: linear-gradient(
			to bottom,
			color-mix(in srgb, var(--c) 34%, var(--surface)),
			color-mix(in srgb, var(--c) 10%, var(--surface))
		);
		border: 1px solid color-mix(in srgb, var(--c) 30%, var(--border));
	}
	.p1 .block {
		height: 92px;
	}
	.p2 .block {
		height: 64px;
	}
	.p3 .block {
		height: 44px;
	}
	.step.me .name {
		color: var(--accent-strong, var(--text));
	}
	@keyframes rise {
		from {
			opacity: 0;
			transform: translateY(14px);
		}
	}

	.mine {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 8px 10px;
		padding: 12px 16px;
		color: var(--text-2);
	}
	.mine :global(svg) {
		color: var(--gold);
	}
	.mine strong {
		color: var(--text);
	}
	.done {
		margin-left: auto;
		padding: 2px 10px;
		border-radius: var(--r-full);
		background: color-mix(in srgb, var(--ok) 14%, transparent);
		color: var(--ok);
		font-size: 12.5px;
		font-weight: 650;
	}

	.ranks {
		margin: 0;
		padding: 0;
		overflow: hidden;
		list-style: none;
	}
	.ranks li {
		display: grid;
		grid-template-columns: 28px 40px minmax(0, 1fr) auto;
		align-items: center;
		gap: 12px;
		padding: 10px 16px;
	}
	.ranks li + li {
		border-top: 1px solid var(--border);
	}
	.ranks li.me {
		background: color-mix(in srgb, var(--accent) 7%, transparent);
	}
	.place {
		color: var(--text-3);
		font-weight: 700;
		text-align: center;
	}
	.leader .place {
		color: var(--text);
	}
	.who {
		display: flex;
		flex-direction: column;
		gap: 4px;
		min-width: 0;
	}
	.line {
		display: flex;
		align-items: center;
		gap: 6px;
		min-width: 0;
	}
	.line strong {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.you {
		padding: 0 7px;
		border-radius: var(--r-full);
		background: var(--accent-soft);
		color: var(--text);
		font-size: 11.5px;
		font-weight: 650;
	}
	.facts {
		display: flex;
		flex-wrap: wrap;
		gap: 4px 12px;
		color: var(--text-3);
		font-size: 12.5px;
	}
	.facts span {
		display: inline-flex;
		align-items: center;
		gap: 4px;
	}
	.bar {
		display: block;
		height: 4px;
		border-radius: 2px;
		background: var(--surface-2);
		overflow: hidden;
	}
	.bar span {
		display: block;
		height: 100%;
		border-radius: 2px;
		background: linear-gradient(90deg, var(--amber), var(--gold));
		transition: width 400ms var(--ease);
	}
	.score {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		line-height: 1.1;
	}
	.score strong {
		font-size: 18px;
	}
	.score small {
		color: var(--text-3);
		font-size: 11px;
	}
	.how p {
		margin: 0 0 6px;
	}
	.legend {
		display: flex;
		flex-wrap: wrap;
		gap: 4px 14px;
	}
	.legend span {
		display: inline-flex;
		align-items: center;
		gap: 4px;
	}
	.skeleton {
		height: 280px;
		border-radius: var(--r-l, 18px);
		background: var(--surface-2);
		opacity: 0.6;
	}
	@media (max-width: 520px) {
		.ranks li {
			grid-template-columns: 22px 40px minmax(0, 1fr) auto;
			gap: 10px;
			padding: 10px 12px;
		}
		.ago {
			display: none !important;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.step {
			animation: none;
		}
	}
</style>
