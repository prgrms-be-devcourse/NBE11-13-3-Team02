const MAX_EDGE = 1024
const JPEG_QUALITY = 0.8

// 원본 사진은 수 MB라 그대로 보내면 토큰을 크게 쓴다(무료 티어 할당량에 직접 영향).
// 상품을 알아보는 데는 1024px이면 충분하므로 업로드 전에 줄인다.
export async function resizeImage(file) {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height))
  const width = Math.round(bitmap.width * scale)
  const height = Math.round(bitmap.height * scale)

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  canvas.getContext('2d').drawImage(bitmap, 0, 0, width, height)
  bitmap.close()

  const dataUrl = canvas.toDataURL('image/jpeg', JPEG_QUALITY)
  return {
    mimeType: 'image/jpeg',
    data: dataUrl.slice(dataUrl.indexOf(',') + 1),
    previewUrl: dataUrl,
  }
}
