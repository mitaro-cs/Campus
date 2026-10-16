<script lang="ts">
	import { AtSign, Camera } from '@lucide/svelte';
	import { del, patch, put } from '$lib/api';
	import { t } from '$lib/i18n/ru';
	import { fioError } from '$lib/fio';
	import { loadMe, session } from '$lib/session.svelte';
	import { toast, toastError } from '$lib/toasts.svelte';
	import Avatar from '$lib/ui/Avatar.svelte';
	import Button from '$lib/ui/Button.svelte';
	import FioField from '$lib/auth/FioField.svelte';

	// Профиль: фото, ФИО, логин и роли – то, что видят другие.
	const me = $derived(session.me!);
	let displayName = $derived(me.user.displayName);
	let avatarOpen = $state(false);

	async function saveName(e: SubmitEvent) {
		e.preventDefault();
		const problem = fioError(displayName);
		if (problem) return toastError(new Error(problem));
		try {
			await patch('/api/me', { displayName });
			await loadMe();
			toast('ФИО сохранено', 'ok');
		} catch (err) {
			toastError(err);
		}
	}

	// Статус (1.0.4) – что угодно, хоть глупость: видят участники группы под именем.
	const MOODS = [
		'сплю на паре 😴',
		'ищу смысл жизни и конспект',
		'кофе > сон ☕',
		'не трогать до сессии',
		'в поисках зачёта 🔍',
		'дедлайн? не слышал',
		'учусь… наверное 📚',
		'на больничном от понедельников',
		'гуманитарий в мире матана',
		'сдам всё. потом.'
	];
	let mood = $derived(me.user.mood ?? '');
	let moodBusy = $state(false);
	const moodChanged = $derived(mood.trim() !== (me.user.mood ?? ''));

	function randomMood() {
		const others = MOODS.filter((m) => m !== mood);
		mood = others[Math.floor(Math.random() * others.length)];
	}

	async function saveMood(e?: SubmitEvent) {
		e?.preventDefault();
		moodBusy = true;
		try {
			await put('/api/me/mood', { text: mood });
			await loadMe();
			toast(mood.trim() ? 'Статус обновлён' : 'Статус убран', 'ok');
		} catch (err) {
			toastError(err);
		} finally {
			moodBusy = false;
		}
	}

	async function removeAvatar() {
		try {
			await del('/api/me/avatar');
			await loadMe();
		} catch (err) {
			toastError(err);
		}
	}
</script>

<section class="card pane">
	<div class="who">
		<button
			class="avatar-btn"
			onclick={() => (avatarOpen = true)}
			aria-label="Сменить фото"
			title="Сменить фото"
		>
			<Avatar id={me.user.id} name={me.user.displayName} avatar={me.user.avatar} size={88} ring />
			<span class="cam" aria-hidden="true"><Camera size={16} /></span>
		</button>
		<div class="names">
			<strong>{me.user.displayName}</strong>
			<span class="muted login"><AtSign size={14} />{me.user.username}</span>
			<div class="roles">
				{#if me.user.instanceRole}<span class="chip ink">{t.roles[me.user.instanceRole]}</span>{/if}
				{#each me.groups.filter((g) => g.role) as g (g.id)}<span class="chip"
						>{g.name} · {t.roles[g.role!].toLowerCase()}</span
					>{/each}
			</div>
		</div>
	</div>
	<div class="row wrap">
		<Button onclick={() => (avatarOpen = true)}><Camera size={16} /> Сменить фото</Button>
		{#if me.user.avatar}<Button variant="ghost" onclick={removeAvatar}>Убрать фото</Button>{/if}
	</div>
</section>

<section class="card pane">
	<h3>Статус</h3>
	<p class="muted small">
		Пара слов о себе – серьёзно или нет. Видят участники группы под вашим именем.
	</p>
	<form class="mood-form" onsubmit={saveMood}>
		<label class="sr-only" for="mood">Статус</label>
		<input
			id="mood"
			class="input"
			bind:value={mood}
			maxlength="80"
			placeholder="Например: {MOODS[0]}"
			autocomplete="off"
		/>
		<!-- Кубик – эмодзи, а не значок lucide: новый значок утяжелил бы общий код «Сегодня». -->
		<button type="button" class="icon-btn" onclick={randomMood} aria-label="Придумать статус"
			><span class="dice" aria-hidden="true">🎲</span></button
		>
		<Button type="submit" loading={moodBusy} disabled={!moodChanged}>Сохранить</Button>
	</form>
</section>

<section class="card pane">
	<h3>ФИО</h3>
	<p class="muted small">Как вас видят в группе: в списке участников, у новостей и комментариев.</p>
	<form class="name-form" onsubmit={saveName}>
		<FioField bind:value={displayName} />
		<Button type="submit" disabled={displayName.trim() === me.user.displayName}>Сохранить</Button>
	</form>
	<p class="hint">Логин <strong>@{me.user.username}</strong> не меняется – по нему вы входите.</p>
</section>

{#if avatarOpen}
	{#await import('$lib/ui/AvatarCropper.svelte') then m}
		<m.default
			bind:open={avatarOpen}
			endpoint="/api/me/avatar"
			title="Ваше фото"
			ondone={() => loadMe()}
		/>
	{/await}
{/if}

<style>
	.mood-form {
		display: flex;
		gap: var(--s2);
		margin-top: var(--s3);
	}
	.dice {
		font-size: 18px;
		line-height: 1;
	}
	.mood-form .input {
		flex: 1;
		min-width: 0;
	}
	.who {
		display: flex;
		align-items: center;
		gap: var(--s5);
	}
	.avatar-btn {
		position: relative;
		flex: none;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: none;
		line-height: 0;
	}
	.cam {
		position: absolute;
		right: 0;
		bottom: 0;
		display: grid;
		place-items: center;
		width: 30px;
		height: 30px;
		border-radius: 50%;
		background: var(--accent);
		color: var(--accent-text);
		box-shadow: 0 0 0 3px var(--surface);
	}
	.names {
		display: flex;
		flex-direction: column;
		gap: 4px;
		min-width: 0;
	}
	.names strong {
		font-size: 21px;
		letter-spacing: -0.02em;
		line-height: 1.25;
	}
	.login {
		display: inline-flex;
		align-items: center;
		gap: 2px;
	}
	.roles {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		margin-top: 4px;
	}
	.name-form {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: var(--s3);
	}
	.name-form > :global(fieldset) {
		align-self: stretch;
	}
	.hint strong {
		color: var(--text-2);
	}
	@media (max-width: 480px) {
		.who {
			flex-direction: column;
			text-align: center;
		}
		.names {
			align-items: center;
		}
		.roles {
			justify-content: center;
		}
	}
</style>
