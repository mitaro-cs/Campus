<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { t } from '$lib/i18n/ru';
	import { groups, session } from '$lib/session.svelte';
	import People from '$lib/content/People.svelte';
	import Tabs from '$lib/ui/Tabs.svelte';

	// «Участники»: люди группы и (1.0.4) рейтинг активности – вкладкой, код рейтинга грузится при открытии.
	const tab = $derived(page.url.searchParams.get('tab') === 'rating' ? 'rating' : 'people');
	const groupId = $derived(session.groupId ?? groups()[0]?.id ?? null);
	const TABS = [
		{ label: 'Люди', value: 'people' },
		{ label: 'Рейтинг', value: 'rating' }
	];
</script>

<svelte:head><title>{t.nav.members} · Campus</title></svelte:head>

<div class="page-head">
	<h1>{t.nav.members}</h1>
</div>

{#if groupId !== null}
	<div class="switch">
		<Tabs
			tabs={TABS}
			value={tab}
			label="Участники"
			onchange={(v) =>
				goto(v === 'rating' ? '?tab=rating' : '?', { replaceState: true, noScroll: true })}
		/>
	</div>
{/if}

{#if tab === 'rating' && groupId !== null}
	{#await import('$lib/content/Leaderboard.svelte') then m}<m.default {groupId} />{/await}
{:else}
	<People />
{/if}

<style>
	.switch {
		margin-bottom: var(--s3);
	}
</style>
