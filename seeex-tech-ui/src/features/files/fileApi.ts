import axios from 'axios'
import { api, type PageResult } from '../../lib/api'

export interface FileItem { applicationCode: string; id: number; objectKey: string; fileName: string; contentType: string; fileSize: number; bizType?: string; bizId?: string; visibility: string; status: string; createdAt?: string; confirmedAt?: string; ownerId?: string; tenantId?: string; updatedAt?: string }
export interface UploadUrlResponse { objectKey: string; uploadUrl: string; expiresInSeconds: number }

export async function listFiles(params: { page: number; pageSize: number; keyword?: string; status?: string; contentType?: string; applicationCode: string }) {
  const response = await api.get<{ data: PageResult<FileItem> }>('/files', { params: { page: params.page, page_size: params.pageSize, keyword: params.keyword || undefined, status: params.status || undefined, contentType: params.contentType || undefined, applicationCode: params.applicationCode } })
  return response.data.data
}

export async function uploadFile(file: File, onProgress?: (progress: number) => void, applicationCode = 'platform') {
  if (file.size <= 0 || file.size > 50 * 1024 * 1024) throw new Error('文件大小须在 1 字节到 50MB 之间')
  if (!['image/png', 'image/jpeg', 'image/webp', 'application/pdf', 'text/plain'].includes(file.type)) throw new Error('仅支持 PNG、JPEG、WebP、PDF 和 TXT 文件')
  if (!file.type) throw new Error('无法识别文件类型，请选择图片、PDF 或文本文件')
  const contentType = file.type
  const response = await api.post<{ data: UploadUrlResponse }>('/files/upload-url', null, {
    params: { fileName: file.name, contentType, size: file.size, applicationCode },
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  })
  const upload = response.data.data
  await axios.put(upload.uploadUrl, file, { timeout: 60_000, headers: { 'Content-Type': contentType }, onUploadProgress: (event) => { if (event.total) onProgress?.(Math.round((event.loaded / event.total) * 100)) } })
  await api.post('/files/confirm', null, { params: { objectKey: upload.objectKey, applicationCode } })
  return upload
}

export async function getDownloadUrl(objectKey: string, applicationCode = 'platform') {
  const response = await api.get<{ data: string }>('/files/download-url', { params: { objectKey, applicationCode } })
  return response.data.data
}

export async function deleteFiles(objectKeys: string[], applicationCode = 'platform') {
  await api.delete('/files/batch', { data: objectKeys, params: { applicationCode } })
}
