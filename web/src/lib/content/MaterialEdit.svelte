<script lang="ts">
	import { onMount, untrack } from 'svelte';
	import { Pencil, X } from '@lucide/svelte';
	import { fmtSize } from '$lib/format';
	import DropZone from './DropZone.svelte';
	import { patch } from '$lib/api';
	import { toast } from '$lib/toasts.svelte';
	import type { FileInfo, Material } from '$lib/types';
	import Modal from '$lib/ui/Modal.svelte';
	import Button from '$lib/ui/Button.svelte';

	// Правка материала (1.0.4): название, текст сообщения или описание, адрес ссылки. Окно создаёт
	// editMaterial() из меню материала и само убирает себя после закрытия.
	let {
		material: m,
		onsaved,
		ondone
	}: { material: Material; onsaved: (m: Material) => void; ondone: () => void } = $props();

	let open = $state(false);
	// Поля заполняются один раз – живое обновление списка не стирает введённое.
	const was = untrack(() => ({
		note: m.kind === 'note',
		link: m.kind === 'link',
		title: m.title,
		description: m.description ?? '',
		url: m.url ?? '',
		files: m.file ? [m.file, ...(m.more ?? [])] : []
	}));
	// Файлы материала (тема, 1.0.4): убрать лишние, добавить новые – листаются по порядку.
	let kept = $state<FileInfo[]>(was.files);
	let added = $state<FileInfo[]>([]);
	let uploading = $state(0);
	const filesChanged = $derived(kept.length !== was.files.length || added.length > 0);
	const note = was.note;
	let title = $state(was.title);
	let description = $state(was.description);
	let url = $state(was.url);
	let error = $state('');
	let busy = $state(false);
	const dirty = $derived(
		title !== was.title || description !== was.description || url !== was.url || filesChanged
	);

	onMount(() => {
		open = true;
	});
	$effect(() => {
		// Закрыли – даём окну доиграть анимацию и убираем компонент.
		if (!open) {
			const t = setTimeout(ondone, 300);
			return () => clearTimeout(t);
		}
	});

	async function save(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		error = '';
		try {
			const saved = await patch<Material>(`/api/materials/${m.id}`, {
				title,
				description,
				...(was.link ? { url } : {}),
				...(filesChanged ? { fileIds: [...kept, ...added].map((f) => f.id) } : {})
			});
			toast('Сохранено', 'ok');
			open = false;
			onsaved(saved);
		} catch (err) {
			error = err instanceof Error ? err.message : 'Ошибка';
		} finally {
			busy = false;
		}
	}
</script>

<Modal bind:open {dirty} title={note ? 'Изменить сообщение' : 'Изменить материал'} icon={Pencil}>
	<form id="material-edit" class="stack form" onsubmit={save}>
		<div>
			<label class="label" for="me-title"
				>Название{#if note}
					<span class="faint">(необязательно)</span>{/if}</label
			>
			<input id="me-title" class="input" bind:value={title} maxlength="200" required={!note} />
		</div>
		{#if was.files.length}
			<div>
				<span class="label">Файлы</span>
				<ul class="flist">
					{#each kept as f (f.id)}
						<li>
							<span class="fname">{f.name}</span>
							<span class="faint small num">{fmtSize(f.size)}</span>
							<button
								type="button"
								class="icon-btn"
								aria-label="Убрать {f.name}"
								disabled={kept.length + added.length <= 1}
								onclick={() => (kept = kept.filter((x) => x.id !== f.id))}><X size={16} /></button
							>
						</li>
					{/each}
				</ul>
				<DropZone bind:files={added} bind:uploading max={50} />
			</div>
		{/if}
		{#if was.link}
			<div>
				<label class="label" for="me-url">Адрес</label>
				<input id="me-url" class="input" type="url" bind:value={url} required />
			</div>
		{/if}
		<div>
			<label class="label" for="me-text"
				>{#if note}Текст сообщения{:else}Описание <span class="faint">(необязательно)</span
					>{/if}</label
			>
			<textarea
				id="me-text"
				class="textarea"
				rows={note ? 10 : 4}
				bind:value={description}
				maxlength={note ? 8000 : 2000}
				required={note}></textarea>
			<p class="faint small hint">
				Можно оформить: # заголовок, **жирный**, *курсив*, - список, [ссылка](https://…)
			</p>
		</div>
		{#if error}<p class="error-text" role="alert">{error}</p>{/if}
	</form>
	{#snippet footer()}
		<Button onclick={() => (open = false)}>Отмена</Button>
		<Button variant="primary" type="submit" form="material-edit" loading={busy || uploading > 0}
			>Сохранить</Button
		>
	{/snippet}
</Modal>

<style>
	.form {
		gap: var(--s4);
	}
	.hint {
		margin-top: 6px;
	}
	.flist {
		display: flex;
		flex-direction: column;
		gap: 4px;
		margin: 6px 0 10px;
		padding: 0;
		list-style: none;
	}
	.flist li {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 4px 4px 4px 12px;
		border: 1px solid var(--border);
		border-radius: var(--r-s);
	}
	.fname {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
</style>
