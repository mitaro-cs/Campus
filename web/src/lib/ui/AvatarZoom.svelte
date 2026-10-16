<script lang="ts">
	import { openFiles } from '$lib/files/viewer.svelte';
	import Avatar from './Avatar.svelte';

	// Аватар, который открывается крупно (1.0.4): просмотр фото – приблизить пальцами, колёсиком,
	// двойным нажатием. Без своей картинки – просто буквы, нажимать нечего.
	let {
		id,
		name,
		avatar = null,
		size = 32,
		ring = false
	}: { id: number; name: string; avatar?: string | null; size?: number; ring?: boolean } = $props();

	function open(e: MouseEvent) {
		// Аватар бывает внутри ссылки (строка списка) – открываем фото, а не переходим.
		e.preventDefault();
		e.stopPropagation();
		if (!avatar) return;
		openFiles(
			[{ id: -1, name, mime: 'image/webp', size: 0, url: `/api/avatars/${avatar}-768.webp` }],
			0
		);
	}
</script>

{#if avatar}
	<button type="button" class="zoom" aria-label="Фото: {name}" onclick={open}
		><Avatar {id} {name} {avatar} {size} {ring} /></button
	>
{:else}
	<Avatar {id} {name} {avatar} {size} {ring} />
{/if}

<style>
	.zoom {
		display: inline-flex;
		flex: none;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: none;
		cursor: zoom-in;
		/* Над растянутой ссылкой строки (::after), как другие кнопки в строках. */
		position: relative;
		z-index: 2;
		transition: transform 150ms var(--ease);
	}
	.zoom:active {
		transform: scale(0.94);
	}
</style>
