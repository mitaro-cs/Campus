<script lang="ts">
	import { ChevronLeft, ChevronRight, Download, Minus, Plus, X } from '@lucide/svelte';
	import { cubicOut } from 'svelte/easing';
	import { fmtSize } from '$lib/format';
	import { fileKind, TEXT_LIMIT } from '$lib/fileKinds';
	import FileIcon from '$lib/content/FileIcon.svelte';
	import { closeViewer, viewer } from './viewer.svelte';
	import { saveFile } from './save';
	import { reducedMotion } from '$lib/motionPref';

	// Просмотр файлов поверх приложения: картинки, PDF, видео, аудио и текст открываются сразу,
	// без скачивания и без выхода из приложения. Остальное – «Скачать».
	let dialog: HTMLDialogElement | undefined = $state();
	let zoom = $state(1);
	/** Откуда въезжает новый файл: 1 – справа (вперёд), -1 – слева, 0 – просто появился. */
	let dir = $state(0);
	let text = $state<string | null>(null);
	let textError = $state('');

	const file = $derived(viewer.files[viewer.index]);
	const kind = $derived(file ? fileKind(file.mime, file.name) : 'other');
	const src = $derived(file ? (file.url ?? `/api/files/${file.id}`) : '');
	const pending = $derived(!!file && !file.url && file.id <= 0);
	const many = $derived(viewer.files.length > 1);

	$effect(() => {
		if (!dialog) return;
		if (viewer.open && !dialog.open) {
			dir = 0;
			dialog.showModal();
		}
		if (!viewer.open && dialog.open) dialog.close();
	});

	// Новый файл – масштаб сначала, текст заново.
	$effect(() => {
		void file?.id;
		zoom = 1;
		text = null;
		textError = '';
		if (!file || pending || kind !== 'text') return;
		if (file.size > TEXT_LIMIT) {
			textError = 'Файл большой – скачайте его';
			return;
		}
		const id = file.id;
		fetch(`/api/files/${id}`, { credentials: 'same-origin' })
			.then((r) => (r.ok ? r.text() : Promise.reject(new Error(`Ошибка ${r.status}`))))
			.then((t) => {
				if (file?.id === id) text = t;
			})
			.catch((e) => (textError = e instanceof Error ? e.message : 'Не удалось открыть'));
	});

	function go(step: number) {
		if (!many) return;
		const n = viewer.files.length;
		dir = step;
		viewer.index = (viewer.index + step + n) % n;
	}

	// Смена файла (1.0.3): прежний уезжает в сторону листания, новый въезжает следом – оба видны
	// одновременно (в .stage они в одной ячейке сетки), а не прежний исчезает, а новый появляется.
	const still = reducedMotion;
	function slide(_: Element, { out }: { out: boolean }) {
		const side = out ? -dir : dir;
		return {
			duration: still() || !dir ? 0 : 340,
			easing: cubicOut,
			css: (t: number) =>
				`transform: translateX(${(1 - t) * side * 36}%) scale(${0.96 + 0.04 * t}); opacity: ${t}`
		};
	}

	function onkey(e: KeyboardEvent) {
		if (!viewer.open) return;
		if (e.key === 'ArrowRight') go(1);
		else if (e.key === 'ArrowLeft') go(-1);
		else if ((e.key === '+' || e.key === '=') && zoomable) setZoom(zoom + 0.25);
		else if (e.key === '-' && zoomable) setZoom(zoom - 0.25);
	}

	const zoomable = $derived(kind === 'image' || kind === 'pdf');
	function setZoom(z: number) {
		zoom = Math.round(Math.min(4, Math.max(0.5, z)) * 100) / 100;
	}

	// Листание пальцем (1.0.3): файл едет за пальцем, дальше порога – соседний въезжает с той же
	// стороны, ближе – возвращается на место. Касание левой или правой трети картинки – назад или
	// вперёд, как в Telegram; середина – для двойного касания (приблизить).
	let startX = 0;
	let startY = 0;
	let startAt = 0;
	let tracking = false;
	let drag = $state(0);
	let dragging = $state(false);
	let stage: HTMLDivElement | undefined = $state();
	const scrolls = $derived(kind === 'pdf' || kind === 'text' || kind === 'markdown' || zoom !== 1);

	function down(e: PointerEvent) {
		startX = e.clientX;
		startY = e.clientY;
		startAt = Date.now();
		tracking = e.pointerType === 'touch' && !scrolls;
	}
	function move(e: PointerEvent) {
		if (!tracking) return;
		const dx = e.clientX - startX;
		if (!dragging && Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(e.clientY - startY))
			dragging = true;
		// Один файл и край ленты – только «пружинит».
		if (dragging) drag = has(dx < 0 ? 1 : -1) ? dx : dx * 0.25;
	}
	function up(e: PointerEvent) {
		if (!tracking) return;
		tracking = false;
		const dx = e.clientX - startX;
		const dy = e.clientY - startY;
		const wasDrag = dragging;
		dragging = false;
		if (wasDrag) {
			const step = dx < 0 ? 1 : -1;
			// Сначала листаем: прежний файл уезжает с того места, куда его дотащили.
			if (Math.abs(dx) > 60 && has(step)) go(step);
			drag = 0;
			return;
		}
		drag = 0;
		if (Math.abs(dx) < 10 && Math.abs(dy) < 10 && Date.now() - startAt < 350) {
			const side = sideOf(e.clientX);
			if (side && kind === 'image' && has(side)) go(side);
		}
	}
	function cancel() {
		tracking = false;
		dragging = false;
		drag = 0;
	}
	/** Есть ли файл в эту сторону: пальцем по кругу не листаем (стрелками – можно). */
	function has(step: number): boolean {
		const i = viewer.index + step;
		return many && i >= 0 && i < viewer.files.length;
	}
	/** Левая треть – -1, правая – 1, середина – 0 (только когда файлов несколько). */
	function sideOf(x: number): number {
		if (!many || !stage) return 0;
		const r = stage.getBoundingClientRect();
		const k = (x - r.left) / r.width;
		return k < 0.3 ? -1 : k > 0.7 ? 1 : 0;
	}

	// Двойное касание середины картинки – приблизить или вернуть (масштаб страницы сам не меняется).
	let lastTap = 0;
	function tapImage(e: PointerEvent) {
		if (e.pointerType !== 'touch' || (zoom === 1 && sideOf(e.clientX))) return;
		const now = Date.now();
		if (now - lastTap < 300) setZoom(zoom === 1 ? 2 : 1);
		lastTap = now;
	}
</script>

<svelte:window onkeydown={onkey} />

<dialog
	bind:this={dialog}
	class="viewer"
	aria-label={file ? `Просмотр: ${file.name}` : 'Просмотр файла'}
	onclose={() => viewer.open && closeViewer()}
>
	{#if viewer.open && file}
		<header class="bar">
			<button class="icon-btn" onclick={closeViewer} aria-label="Закрыть"><X size={20} /></button>
			<div class="name">
				<strong>{file.name}</strong>
				<span class="small num"
					>{file.url ? 'Фото профиля' : fmtSize(file.size)}{many
						? ` · ${viewer.index + 1} из ${viewer.files.length}`
						: ''}{viewer.title ? ` · ${viewer.title}` : ''}</span
				>
			</div>
			{#if zoomable && !pending}
				<div class="zoom" role="group" aria-label="Масштаб">
					<button class="icon-btn" onclick={() => setZoom(zoom - 0.25)} aria-label="Уменьшить"
						><Minus size={18} /></button
					>
					<button class="pct num" onclick={() => setZoom(1)} title="Вписать в экран"
						>{Math.round(zoom * 100)}%</button
					>
					<button class="icon-btn" onclick={() => setZoom(zoom + 0.25)} aria-label="Увеличить"
						><Plus size={18} /></button
					>
				</div>
			{/if}
			{#if !pending && !file.url}
				<a
					class="icon-btn"
					href="{src}?download=true"
					download={file.name}
					onclick={(e) => (e.preventDefault(), saveFile(file))}
					aria-label="Скачать"><Download size={19} /></a
				>
			{/if}
		</header>

		<!-- svelte-ignore a11y_no_static_element_interactions -->
		<div
			bind:this={stage}
			class="stage"
			class:scroll={scrolls}
			onpointerdown={down}
			onpointermove={move}
			onpointerup={up}
			onpointercancel={cancel}
		>
			{#key file.id}
				<div
					class="content {kind}"
					class:dragging
					class:first={!dir}
					style:translate={drag ? `${drag}px 0` : null}
					in:slide={{ out: false }}
					out:slide={{ out: true }}
				>
					{#if pending}
						<div class="none">
							<FileIcon mime={file.mime} size={34} />
							<p>Файл ещё на этом устройстве – откроется, когда уйдёт на сервер.</p>
						</div>
					{:else if kind === 'image'}
						<img
							{src}
							alt={file.name}
							style:width={zoom === 1 ? null : `${zoom * 100}%`}
							class:zoomed={zoom !== 1}
							onpointerup={tapImage}
							ondblclick={() => setZoom(zoom === 1 ? 2 : 1)}
						/>
					{:else if kind === 'pdf'}
						{#await import('./PdfView.svelte') then m}<m.default {src} {zoom} />{/await}
					{:else if kind === 'video'}
						<!-- svelte-ignore a11y_media_has_caption -->
						<video {src} controls playsinline preload="metadata"></video>
					{:else if kind === 'audio'}
						<div class="none">
							<FileIcon mime={file.mime} size={34} />
							<audio {src} controls preload="metadata"></audio>
						</div>
					{:else if kind === 'markdown'}
						{#await import('./MarkdownNote.svelte') then m}<m.default
								fileId={file.id}
								dark
							/>{/await}
					{:else if kind === 'text'}
						{#if textError}<p class="none">{textError}</p>
						{:else if text === null}<p class="none faint">Открываем…</p>
						{:else}<pre>{text}</pre>{/if}
					{:else}
						<div class="none">
							<FileIcon mime={file.mime} size={40} />
							<p>
								<strong>{file.name}</strong><br /><span class="dim"
									>Этот файл не открыть прямо здесь – скачайте его и откройте в подходящей
									программе.</span
								>
							</p>
							<a
								class="btn-dl"
								href="{src}?download=true"
								download={file.name}
								onclick={(e) => (e.preventDefault(), saveFile(file))}
								><Download size={17} /> Скачать · {fmtSize(file.size)}</a
							>
						</div>
					{/if}
				</div>
			{/key}
		</div>

		{#if many}
			<button class="nav prev" onclick={() => go(-1)} aria-label="Предыдущий файл"
				><ChevronLeft size={24} /></button
			>
			<button class="nav next" onclick={() => go(1)} aria-label="Следующий файл"
				><ChevronRight size={24} /></button
			>
			<div class="dots" aria-hidden="true">
				{#each viewer.files as f, i (f.id)}<span class:on={i === viewer.index}></span>{/each}
			</div>
		{/if}
	{/if}
</dialog>

<style>
	.viewer {
		width: 100vw;
		height: 100dvh;
		max-width: none;
		max-height: none;
		margin: 0;
		padding: 0;
		border: 0;
		background: #0e0f12;
		color: #f3f4f6;
		overflow: hidden;
	}
	.viewer[open] {
		display: flex;
		flex-direction: column;
		animation: viewer-in 220ms var(--ease);
	}
	.viewer::backdrop {
		background: transparent;
	}
	@keyframes viewer-in {
		from {
			opacity: 0;
			transform: scale(0.985);
		}
	}
	.bar {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: calc(8px + env(safe-area-inset-top)) 10px 8px;
		background: linear-gradient(rgb(0 0 0 / 0.45), transparent);
	}
	.name {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		line-height: 1.3;
	}
	.name strong {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-weight: 600;
	}
	.name span {
		color: rgb(255 255 255 / 0.6);
	}
	.icon-btn {
		display: grid;
		place-items: center;
		flex: none;
		width: 40px;
		height: 40px;
		border: 0;
		border-radius: 12px;
		background: transparent;
		color: inherit;
	}
	.icon-btn:hover {
		background: rgb(255 255 255 / 0.1);
		text-decoration: none;
	}
	.zoom {
		display: flex;
		align-items: center;
	}
	.pct {
		min-width: 52px;
		height: 32px;
		border: 0;
		border-radius: 8px;
		background: rgb(255 255 255 / 0.08);
		color: inherit;
		font: inherit;
		font-size: 13px;
	}
	@media (max-width: 520px) {
		.pct {
			display: none;
		}
	}
	/* Сетка из одной ячейки: при смене файла прежний и новый лежат друг на друге. */
	.stage {
		position: relative;
		flex: 1;
		min-height: 0;
		display: grid;
		grid-template: 1fr / minmax(0, 1fr);
		overflow: hidden;
		touch-action: pan-y;
	}
	.stage.scroll {
		overflow: auto;
		touch-action: pan-x pan-y;
	}
	.content {
		grid-area: 1 / 1;
		margin: auto;
		padding: 12px 12px calc(28px + env(safe-area-inset-bottom));
		max-width: 100%;
		transition: translate 240ms var(--ease);
	}
	.content.dragging {
		transition: none;
	}
	/* Первый файл при открытии – чуть снизу; соседние въезжают сбоку (slide). */
	.content.first {
		animation: content-in 260ms var(--ease);
	}
	@keyframes content-in {
		from {
			opacity: 0;
			transform: translateY(6px) scale(0.98);
		}
	}
	:global(:root[data-motion='reduce']) .content {
		animation: none;
		transition: none;
	}
	.content.pdf {
		width: min(100%, 980px);
		margin: 0 auto;
	}
	.content.image {
		display: grid;
		place-items: center;
		width: 100%;
		height: 100%;
		padding: 0 12px 24px;
	}
	img {
		max-width: 100%;
		max-height: 100%;
		object-fit: contain;
		border-radius: 6px;
		user-select: none;
		-webkit-user-select: none;
		cursor: zoom-in;
	}
	img.zoomed {
		max-width: none;
		max-height: none;
		cursor: zoom-out;
	}
	video {
		display: block;
		max-width: 100%;
		max-height: calc(100dvh - 120px);
		border-radius: 8px;
		background: black;
	}
	.content.text,
	.content.markdown {
		width: min(100%, 980px);
		margin: 0 auto;
	}
	pre {
		margin: 0;
		padding: 16px;
		border-radius: 10px;
		background: rgb(255 255 255 / 0.06);
		color: #e5e7eb;
		font:
			13px/1.55 ui-monospace,
			'SF Mono',
			Menlo,
			Consolas,
			monospace;
		white-space: pre-wrap;
		overflow-wrap: anywhere;
		user-select: text;
	}
	.none {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 16px;
		max-width: 380px;
		margin: 15vh auto 0;
		text-align: center;
		color: rgb(255 255 255 / 0.85);
	}
	.dim {
		color: rgb(255 255 255 / 0.6);
		font-size: 14px;
	}
	.btn-dl {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		height: 44px;
		padding: 0 20px;
		border-radius: var(--r-full);
		background: white;
		color: #111;
		font-weight: 600;
	}
	.btn-dl:hover {
		text-decoration: none;
		transform: translateY(-1px);
	}
	.nav {
		position: absolute;
		top: 50%;
		display: grid;
		place-items: center;
		width: 44px;
		height: 44px;
		border: 0;
		border-radius: 50%;
		background: rgb(255 255 255 / 0.12);
		color: white;
		backdrop-filter: blur(8px);
		transition: background-color var(--dur) var(--ease);
	}
	.nav:hover {
		background: rgb(255 255 255 / 0.22);
	}
	.prev {
		left: 12px;
	}
	.next {
		right: 12px;
	}
	@media (hover: none) {
		.nav {
			display: none;
		}
	}
	.dots {
		position: absolute;
		left: 50%;
		bottom: calc(10px + env(safe-area-inset-bottom));
		display: flex;
		gap: 6px;
		transform: translateX(-50%);
	}
	.dots span {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: rgb(255 255 255 / 0.3);
		transition:
			width 200ms var(--ease),
			background-color 200ms var(--ease);
	}
	.dots span.on {
		width: 18px;
		border-radius: 3px;
		background: white;
	}
</style>
