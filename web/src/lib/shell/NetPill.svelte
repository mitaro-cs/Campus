<script lang="ts">
	import { CloudOff, CloudUpload, WifiOff } from '@lucide/svelte';
	import { slide } from '$lib/motion';
	import { request } from '$lib/api';
	import { flushOutbox, offline, syncNow } from '$lib/offline/engine';
	import { pwa } from '$lib/pwa.svelte';

	// Плашка связи над страницей (0.9): «Вы не в сети», «Сервер не работает», «Отправляем» + очередь;
	// с 1.0.3 «Вы не в сети» – и когда браузер считает себя в сети, но не ответил даже туннель;
	// с 1.0.3 – «Campus обновляется» на время перезапуска ради новой версии.
	// Отдельным файлом (1.0.2): нужна редко, а её значки утяжеляли «Сегодня».

	// Сервер группы недоступен (выключен компьютер хоста или у человека пропал интернет, а браузер
	// этого не заметил): раз в 30 секунд проверяем, не вернулся ли он, – и сразу отправляем
	// сделанное без него. Браузер сам знает, что сети нет, – ждём его события «online».
	$effect(() => {
		if (!pwa.offline) return;
		const id = setInterval(async () => {
			if (!navigator.onLine) return;
			try {
				await request('/api/health');
				await flushOutbox();
				await syncNow();
			} catch {
				/* всё ещё недоступен */
			}
		}, 30_000);
		return () => clearInterval(id);
	});
</script>

<div class="net" role="status" transition:slide|global>
	<span class="pill" class:send={!pwa.offline || pwa.restarting}>
		{#if pwa.restarting}<CloudUpload size={14} /> Campus обновляется – всё сохранится
		{:else if !pwa.offline}<CloudUpload size={14} /> Отправляем
		{:else if pwa.network}<CloudOff size={14} /> Сервер не работает
		{:else}<WifiOff size={14} /> Вы не в сети{/if}{#if offline.pending}<b>{offline.pending}</b>{/if}
	</span>
</div>

<style>
	.net {
		display: flex;
		justify-content: center;
		padding: 6px 16px 0;
	}
	.pill {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 4px 12px;
		border-radius: var(--r-full);
		background: var(--amber-soft);
		color: var(--amber);
		font-size: 12.5px;
		font-weight: 650;
	}
	.pill.send {
		background: var(--surface-2);
		color: var(--text-2);
	}
	.pill b {
		min-width: 18px;
		padding: 0 5px;
		border-radius: var(--r-full);
		background: currentColor;
		text-align: center;
	}
	.pill b {
		color: var(--surface);
	}
</style>
