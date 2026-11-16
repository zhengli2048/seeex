import { Alert, Button, Drawer, Message, Modal, Space } from '@arco-design/web-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, getApiMessage, notifyApiError } from '../../lib/api'
import { ApplicationFields } from './ApplicationFields'
import { useActionKeys, useDictionaryGate, useDraftGuard, validateForm } from './shared'
import type { ApplicationForm, RequestView } from './types'
export interface EditorValue {
  id?: number
  version?: number
  form: ApplicationForm
}
/** 保存失败保留表单与读取版本，后台刷新不会替换草稿。 */
export function RequestEditor({
  initial,
  actor,
  canFiles,
  onClose,
  onSaved,
}: {
  initial: EditorValue
  actor: number
  canFiles: boolean
  onClose: () => void
  onSaved: (value: RequestView) => void
}) {
  const [form, setForm] = useState<ApplicationForm>(structuredClone(initial.form)),
    [errors, setErrors] = useState<Record<string, string>>({})
  const gate = useDictionaryGate(),
    key = useActionKeys(),
    client = useQueryClient()
  const save = useMutation({
    mutationFn: async () => {
      const body = initial.id ? { form, expectedVersion: initial.version } : form
      return (
        await api.request<{ data: RequestView }>({
          url: initial.id ? `/app-requests/${initial.id}/draft` : '/app-requests',
          method: initial.id ? 'put' : 'post',
          data: body,
          headers: initial.id ? undefined : { 'Idempotency-Key': key('CREATE', body) },
        })
      ).data.data
    },
    onSuccess: (value) => {
      Message.success('草稿已保存，尚未提交审批')
      onSaved(value)
      void client.invalidateQueries({ queryKey: ['onboarding'] })
    },
    onError: notifyApiError,
  })
  const dirty = JSON.stringify(form) !== JSON.stringify(initial.form)
  useDraftGuard(dirty, save.isPending)
  function close() {
    if (save.isPending) return
    if (dirty) Modal.confirm({ title: '放弃未保存的申请草稿？', onOk: onClose })
    else onClose()
  }
  return (
    <Drawer
      className="onboarding-drawer"
      visible
      width={760}
      title={initial.id ? '编辑申请草稿' : '新应用接入申请'}
      confirmLoading={save.isPending}
      maskClosable={!save.isPending}
      closable={!save.isPending}
      cancelButtonProps={{ disabled: save.isPending }}
      okText="保存草稿"
      okButtonProps={{ disabled: save.isPending || !gate.ready }}
      onCancel={close}
      onOk={() => {
        if (save.isPending || !gate.ready) return
        const next = validateForm(form, false)
        setErrors(next)
        if (!Object.keys(next).length) save.mutate()
      }}
    >
      <Alert content="先保存草稿，再到申请详情预检并确认审批路径。申请人和交付收件人由当前登录身份固定，不能代他人申请。" />
      <ApplicationFields
        value={form}
        actor={actor}
        canFiles={canFiles}
        disabled={save.isPending}
        errors={errors}
        onReady={gate.update}
        onChange={(value) => {
          setForm(value)
          setErrors({})
          save.reset()
        }}
      />
      {!gate.ready && <Alert type="warning" content="关联选项仍在读取或读取失败，暂不能保存。" />}
      {save.isError && (
        <Alert
          type="error"
          content={getApiMessage(save.error)}
          action={
            <Space>
              <Button
                disabled={save.isPending || !gate.ready}
                onClick={() => {
                  const next = validateForm(form, false)
                  setErrors(next)
                  if (!Object.keys(next).length) save.mutate()
                }}
              >
                重试保存
              </Button>
              <Button
                disabled={save.isPending}
                onClick={() => Modal.confirm({ title: '放弃草稿并重新载入最新申请？', onOk: onClose })}
              >
                放弃当前编辑
              </Button>
            </Space>
          }
        />
      )}
    </Drawer>
  )
}
