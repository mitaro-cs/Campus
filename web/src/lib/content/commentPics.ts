import { uploadFile } from '$lib/upload';
import type { FileInfo } from '$lib/types';

/**
 * Картинки и гифки в комментарии (1.0.4) – отдельным кусочком: грузится, когда картинку выбрали
 * или вставили (страницы с комментариями на грани бюджета). Фото с телефона – до 2048 точек
 * (JPEG); гифки – как есть, иначе перестанут двигаться.
 */
async function smaller(file: File): Promise<File> {
	if (!/^image\/(jpeg|png|webp|heic|heif)$/.test(file.type) || file.size < 700_000) return file;
	try {
		const bitmap = await createImageBitmap(file);
		const k = Math.min(1, 2048 / Math.max(bitmap.width, bitmap.height));
		const canvas = document.createElement('canvas');
		canvas.width = Math.round(bitmap.width * k);
		canvas.height = Math.round(bitmap.height * k);
		canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
		bitmap.close();
		const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, 'image/jpeg', 0.86));
		if (!blob || blob.size >= file.size) return file;
		return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' });
	} catch {
		return file;
	}
}

export async function sendPic(file: File): Promise<FileInfo> {
	return uploadFile(await smaller(file));
}
