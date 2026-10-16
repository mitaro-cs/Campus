<script lang="ts">
	import { CloudOff, EyeOff, ImagePlus, X } from '@lucide/svelte';
	import { offline } from '$lib/offline/engine';
	import { del, get, patch, post, put as putApi } from '$lib/api';
	import { fmtAgo } from '$lib/format';
	import { shortcut } from '$lib/platform';
	import { shortNames } from '$lib/names';
	import { peek, put } from '$lib/cache';
	import { currentGroup, groups, session } from '$lib/session.svelte';
	import { report } from '$lib/content/moderate';
	import { fly, slide } from '$lib/motion';
	import { toastError } from '$lib/toasts.svelte';
	import type { Comment, FileInfo, Member } from '$lib/types';
	import { openFiles } from '$lib/files/viewer.svelte';
	import Author from './Author.svelte';
	import AvatarZoom from '$lib/ui/AvatarZoom.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Menu, { type MenuItem } from '$lib/ui/Menu.svelte';
	import Prose from '$lib/ui/Prose.svelte';

	let { base, canComment = true }: { base: string; canComment?: boolean } = $props();

	let items = $state<Comment[]>([]);
	let loaded = $state(false);
	let members = $state<Member[]>([]);

	// Подписи – только имена; если имя повторяется среди участников группы или в обсуждении,
	// к нему добавляется фамилия.
	const names = $derived(
		shortNames([
			...members
				.filter((m) => m.status !== 'deleted')
				.map((m) => ({ id: m.userId, displayName: m.displayName })),
			...items.map((c) => c.author)
		])
	);

	$effect(() => {
		const g = currentGroup()?.id ?? groups()[0]?.id;
		if (!g) return;
		const key = `members:${g}`;
		members = peek<Member[]>(key) ?? [];
		get<Member[]>(`/api/groups/${g}/members`)
			.then((m) => (members = put(key, m)))
			.catch(() => {});
	});
	let text = $state('');
	let busy = $state(false);

	// Картинки и гифки (1.0.4): грузятся сразу после выбора (lib/content/commentPics.ts – лениво),
	// превью – с устройства.
	const MAX_PICS = 6;
	type Pic = { key: number; preview: string; name: string; info?: FileInfo; failed?: boolean };
	let pics = $state<Pic[]>([]);
	let picInput: HTMLInputElement | undefined = $state();
	let picSeq = 0;
	const picsBusy = $derived(pics.some((p) => !p.info && !p.failed));

	function addPics(files: Iterable<File>) {
		for (const f of files) {
			if (!f.type.startsWith('image/')) continue;
			if (pics.length >= MAX_PICS) {
				toastError(new Error(`Не больше ${MAX_PICS} картинок`));
				break;
			}
			const pic: Pic = { key: ++picSeq, preview: URL.createObjectURL(f), name: f.name };
			pics.push(pic);
			import('./commentPics')
				.then((m) => m.sendPic(f))
				.then((info) => {
					const p = pics.find((x) => x.key === pic.key);
					if (p) p.info = info;
				})
				.catch((err) => {
					const p = pics.find((x) => x.key === pic.key);
					if (p) p.failed = true;
					toastError(err);
				});
		}
	}

	function dropPic(key: number) {
		const p = pics.find((x) => x.key === key);
		if (p) URL.revokeObjectURL(p.preview);
		pics = pics.filter((x) => x.key !== key);
	}

	/** Вставили картинку (скриншот, гифку из буфера) прямо в поле – она к комментарию. */
	function onPaste(e: ClipboardEvent) {
		const files = [...(e.clipboardData?.files ?? [])].filter((f) => f.type.startsWith('image/'));
		if (!files.length) return;
		e.preventDefault();
		e.stopPropagation();
		addPics(files);
	}

	$effect(() => {
		void offline.version;
		get<Comment[]>(`${base}/comments`)
			// Свои, ещё не дошедшие до сервера, – остаются в конце списка; свой новый с сервера, пока
			// отправка не вернулась, не показываем – иначе на миг два одинаковых.
			.then((c) => {
				const me = session.me?.user.id;
				const fresh = sending
					? c.filter((x) => x.author.id !== me || items.some((y) => y.id === x.id))
					: c;
				items = [...fresh, ...items.filter((x) => x.sending)];
				loaded = true;
			})
			.catch(() => (loaded = true));
	});

	/** Текст как есть, без разметки – пока сервер не вернул готовый комментарий. */
	const plain = (t: string) =>
		t.replace(/[&<>"]/g, (ch) => `&#${ch.charCodeAt(0)};`).replace(/\n/g, '<br>');

	// Свой комментарий – сразу (1.0.2): через туннель ответ идёт полсекунды и дольше, и казалось,
	// что ничего не отправилось. Сервер ответил – подменяем готовым, ошибка – текст возвращается.
	let draftId = 0;
	let sending = 0;
	/** Готовые, что заменили временный, и удалённые человеком: только у удалённых – анимация ухода. */
	const swapped: number[] = [];
	const removed: number[] = [];
	async function send(e: SubmitEvent) {
		e.preventDefault();
		const body = text.trim();
		const me = session.me?.user;
		const ready = pics.filter((p) => p.info);
		if ((!body && !ready.length) || !me || picsBusy) return;
		const sent = pics;
		const temp: Comment = {
			id: -1e12 - ++draftId,
			author: { id: me.id, displayName: me.displayName, avatar: me.avatar, deleted: false },
			bodyHtml: body ? `<p>${plain(body)}</p>` : '',
			createdAt: Date.now(),
			hidden: false,
			canDelete: false,
			files: ready.map((p) => ({ ...p.info!, url: p.preview })),
			sending: true
		};
		items.push(temp);
		text = '';
		pics = [];
		busy = true;
		sending++;
		try {
			const c = await post<Comment>(`${base}/comments`, {
				body,
				files: ready.map((p) => p.info!.id)
			});
			// Превью с устройства больше не нужны – картинки уже с сервера.
			setTimeout(() => sent.forEach((p) => URL.revokeObjectURL(p.preview)), 5000);
			swapped.push(c.id);
			const rest = items.filter((x) => x.id !== temp.id && x.id !== c.id);
			items = [...rest, c].sort((a, b) => a.createdAt - b.createdAt);
		} catch (err) {
			items = items.filter((x) => x.id !== temp.id);
			if (!text) text = body;
			if (!pics.length) pics = sent;
			toastError(err);
		} finally {
			sending--;
			busy = false;
		}
	}

	/** Скрыть или вернуть (модератор): скрытый видят только модераторы и автор. */
	async function toggleHidden(c: Comment) {
		try {
			await putApi(`/api/comments/${c.id}/hidden`, { value: !c.hidden });
			c.hidden = !c.hidden;
		} catch (err) {
			toastError(err);
		}
	}

	// Правка (1.0.4): свой – автор, любой – модератор. Прямо на месте, без окна.
	let editing = $state<number | null>(null);
	let draft = $state('');
	let saving = $state(false);
	function startEdit(c: Comment) {
		editing = c.id;
		draft = c.bodyMd ?? '';
	}
	async function saveEdit(c: Comment) {
		const body = draft.trim();
		if (!body || saving) return;
		if (body === (c.bodyMd ?? '').trim()) return void (editing = null);
		saving = true;
		try {
			const n = await patch<Comment>(`/api/comments/${c.id}`, { body });
			items = items.map((x) => (x.id === c.id ? { ...x, ...n } : x));
			editing = null;
		} catch (err) {
			toastError(err);
		} finally {
			saving = false;
		}
	}
	function editKeys(e: KeyboardEvent, c: Comment) {
		if (e.key === 'Escape') {
			e.preventDefault();
			e.stopPropagation();
			editing = null;
		} else if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
			e.preventDefault();
			saveEdit(c);
		}
	}

	function actions(c: Comment): MenuItem[] {
		const out: MenuItem[] = [];
		if (c.canEdit && editing !== c.id) out.push({ label: 'Изменить', onclick: () => startEdit(c) });
		if (c.canHide)
			out.push({ label: c.hidden ? 'Вернуть' : 'Скрыть', onclick: () => toggleHidden(c) });
		if (c.canDelete) out.push({ label: 'Удалить', danger: true, onclick: () => remove(c) });
		else if (c.author.id !== session.me?.user.id)
			out.push({ label: 'Пожаловаться', onclick: () => report('comment', c.id) });
		return out;
	}

	async function remove(c: Comment) {
		try {
			await del(`/api/comments/${c.id}`);
			removed.push(c.id);
			items = items.filter((x) => x.id !== c.id);
		} catch (err) {
			toastError(err);
		}
	}
</script>

<section class="comments" aria-label="Комментарии">
	<h2>
		Комментарии {#if loaded}<span class="faint num">{items.length}</span>{/if}
	</h2>
	{#each items as c (c.id)}
		<div
			class="comment"
			class:sending={c.sending}
			in:fly={{ y: 6, duration: swapped.includes(c.id) ? 0 : 400 }}
			out:slide={{ duration: removed.includes(c.id) ? 200 : 0 }}
		>
			<div class="head">
				{#if c.author.deleted || !c.author.avatar}
					<Author person={c.author} size={24} label={names.get(c.author.id)} />
				{:else}
					<!-- Фото автора открывается крупно (1.0.4); не через Author – тот в общем коде ленты. -->
					<span class="who">
						<AvatarZoom
							id={c.author.id}
							name={c.author.displayName}
							avatar={c.author.avatar}
							size={24}
						/>
						<span class="who-name" title={c.author.displayName}
							>{names.get(c.author.id) ?? c.author.displayName}</span
						>
					</span>
				{/if}
				<span class="faint small num"
					>{fmtAgo(c.createdAt)}{#if c.editedAt}<span title="Изменено {fmtAgo(c.editedAt)}">
							· изменено</span
						>{/if}</span
				>
				{#if c.pending}<span
						class="chip amber"
						title="Создано без сети – уйдёт на сервер, когда появится интернет"
						><CloudOff size={12} /> ждёт отправки</span
					>{/if}
				{#if c.hidden}<span class="chip" title="Видят только модераторы и автор"
						><EyeOff size={12} /> скрыт</span
					>{/if}
				<span class="spacer"></span>
				<!-- Действия – меню «…» (1.0.4): строкой кнопок на телефоне они переносились под имя. -->
				{#if c.id > 0 && !c.sending}
					{@const items = actions(c)}
					{#if items.length}<Menu {items} label="Действия с комментарием" />{/if}
				{/if}
			</div>
			{#if editing === c.id}
				<div class="edit">
					<label class="sr-only" for="edit-{c.id}">Изменить комментарий</label>
					<!-- svelte-ignore a11y_autofocus -->
					<textarea
						id="edit-{c.id}"
						class="textarea"
						rows="3"
						maxlength="4000"
						autofocus
						bind:value={draft}
						onkeydown={(e) => editKeys(e, c)}></textarea>
					<div class="row">
						<span class="faint small kbd-hint">{shortcut('Enter')} – сохранить, Esc – отменить</span
						>
						<span class="spacer"></span>
						<Button size="s" onclick={() => (editing = null)}>Отмена</Button>
						<Button
							size="s"
							variant="primary"
							loading={saving}
							disabled={!draft.trim()}
							onclick={() => saveEdit(c)}>Сохранить</Button
						>
					</div>
				</div>
			{:else if c.bodyHtml}
				<Prose html={c.bodyHtml} class="text {c.hidden ? 'dim' : ''}" />
			{/if}
			{#if c.files?.length}
				<div class="pics" class:dim={c.hidden}>
					{#each c.files as f, i (f.id)}
						<button
							type="button"
							class="pic"
							aria-label="Открыть {f.name}"
							onclick={() => openFiles(c.files!, i, 'Комментарий')}
							><img
								src={f.url ?? `/api/files/${f.id}`}
								alt={f.name}
								loading="lazy"
								decoding="async"
							/></button
						>
					{/each}
				</div>
			{/if}
			{#if c.id > 0 && !c.sending}
				<div class="react">
					{#await import('./Reactions.svelte') then m}<m.default
							small
							endpoint="/api/comments/{c.id}/reactions"
							reactions={c.reactions}
						/>{/await}
				</div>
			{/if}
		</div>
	{/each}
	{#if canComment}
		<form onsubmit={send} class="new">
			<label class="sr-only" for="comment">Комментарий</label>
			<textarea
				id="comment"
				class="textarea"
				rows="2"
				bind:value={text}
				placeholder="Написать комментарий…"
				maxlength="4000"
				onpaste={onPaste}></textarea>
			{#if pics.length}
				<div class="pics drafts">
					{#each pics as p (p.key)}
						<span class="pic draft" class:loading={!p.info && !p.failed} class:failed={p.failed}>
							<img src={p.preview} alt={p.name} />
							<button
								type="button"
								class="unpick"
								aria-label="Убрать {p.name}"
								onclick={() => dropPic(p.key)}><X size={14} /></button
							>
						</span>
					{/each}
				</div>
			{/if}
			<div class="row">
				<input
					bind:this={picInput}
					type="file"
					accept="image/*"
					multiple
					hidden
					onchange={(e) => {
						addPics(e.currentTarget.files ?? []);
						e.currentTarget.value = '';
					}}
				/>
				<button
					type="button"
					class="link small add-pic"
					disabled={pics.length >= MAX_PICS}
					onclick={() => picInput?.click()}><ImagePlus size={16} /> Картинка или гифка</button
				>
				<span class="spacer"></span><Button
					type="submit"
					variant="primary"
					size="s"
					loading={busy || picsBusy}
					disabled={!text.trim() && !pics.some((p) => p.info)}>Отправить</Button
				>
			</div>
		</form>
	{/if}
</section>

<style>
	.comments {
		display: flex;
		flex-direction: column;
		gap: var(--s3);
		margin-top: var(--s5);
	}
	h2 {
		font-size: 17px;
	}
	.comment.sending {
		opacity: 0.6;
	}
	.comment {
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding: var(--s3) var(--s4);
		background: var(--surface);
		border-radius: var(--r);
		box-shadow: var(--shadow-1);
	}
	.comment :global(.text) {
		padding-left: 32px;
	}
	.comment :global(.text.dim) {
		opacity: 0.6;
	}
	/* Одной строкой: имя, время, справа – «…» (1.0.4). */
	.head {
		display: flex;
		align-items: center;
		gap: 8px;
		min-width: 0;
		white-space: nowrap;
	}
	.head > :global(*) {
		min-width: 0;
	}
	.link {
		border: 0;
		background: none;
		color: var(--text-3);
		padding: 0;
	}
	.link:hover {
		color: var(--text);
	}
	.edit {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding-left: 32px;
	}
	.edit .row {
		display: flex;
		align-items: center;
		gap: 8px;
		flex-wrap: wrap;
	}
	.new {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.new .textarea {
		min-height: 72px;
	}
	.who {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		min-width: 0;
	}
	.who-name {
		font-weight: 560;
		font-size: 14px;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	/* Картинки и гифки (1.0.4): плиткой под текстом, открываются в просмотре. */
	.pics {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		padding-left: 32px;
	}
	.pics.dim {
		opacity: 0.55;
	}
	.pic {
		position: relative;
		display: block;
		width: min(160px, 40vw);
		aspect-ratio: 4 / 3;
		padding: 0;
		border: 0;
		border-radius: var(--r-s);
		overflow: hidden;
		background: var(--surface-2);
		cursor: zoom-in;
	}
	.pic img {
		display: block;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.drafts {
		padding-left: 0;
	}
	.draft {
		width: 72px;
		cursor: default;
	}
	.draft.loading img {
		opacity: 0.5;
	}
	.draft.failed {
		outline: 2px solid var(--danger);
	}
	.unpick {
		position: absolute;
		top: 3px;
		right: 3px;
		display: grid;
		place-items: center;
		width: 22px;
		height: 22px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.6);
		color: #fff;
	}
	.react {
		padding-left: 32px;
	}
	.add-pic {
		display: inline-flex;
		align-items: center;
		gap: 6px;
	}
</style>
