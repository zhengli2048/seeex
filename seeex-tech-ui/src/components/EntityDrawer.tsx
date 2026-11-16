import { Alert, Drawer, Form, Input, InputNumber, Modal, Select, Switch, TreeSelect } from '@arco-design/web-react'
import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import type { FormItemProps, SelectProps, TreeSelectProps } from '@arco-design/web-react'

export type FieldType = 'text' | 'textarea' | 'number' | 'select' | 'treeSelect' | 'switch' | 'password'
export interface FieldSpec {
  name: string
  label: string
  type?: FieldType
  required?: boolean
  rules?: FormItemProps['rules']
  readOnly?: boolean
  placeholder?: string
  options?: SelectProps['options']
  treeData?: TreeSelectProps['treeData']
  render?: ReactNode
  min?: number
  max?: number
  precision?: number
}

interface EntityDrawerProps {
  mode: 'view' | 'create' | 'edit'
  title: string
  fields: FieldSpec[]
  initialValues?: Record<string, unknown>
  visible: boolean
  loading?: boolean
  submitDisabled?: boolean
  topError?: string
  onClose: () => void
  onSubmit: (values: Record<string, unknown>) => void
}

/** 通用抽屉表单：支持查看/新增/编辑三态、未保存确认、服务端错误。 */
export function EntityDrawer(props: EntityDrawerProps) {
  const { mode, title, fields, initialValues, visible, loading, submitDisabled, topError, onClose, onSubmit } = props
  const [form] = Form.useForm()
  const [dirty, setDirty] = useState(false)
  const readOnly = mode === 'view'

  useEffect(() => {
    if (visible) {
      form.resetFields()
      form.setFieldsValue(initialValues ?? {})
      setDirty(false)
    }
  }, [visible, initialValues, form])

  function handleClose() {
    if (loading) return
    if (dirty && mode !== 'view') {
      Modal.confirm({ title: '放弃修改？', content: '当前有未保存的修改，确定放弃？', onOk: () => onClose() })
      return
    }
    onClose()
  }

  function renderField(field: FieldSpec) {
    if (field.render) return field.render
    const common = { placeholder: field.placeholder ?? '请输入' + field.label }
    if (field.type === 'switch') return <Switch disabled={readOnly || field.readOnly || loading} />
    if (field.type === 'number') return <InputNumber min={field.min} max={field.max} precision={field.precision} disabled={readOnly || field.readOnly || loading} style={{ width: '100%' }} />
    if (field.type === 'select') return <Select disabled={readOnly || field.readOnly || loading} showSearch allowClear options={field.options} {...common} />
    if (field.type === 'treeSelect') return <TreeSelect disabled={readOnly || field.readOnly || loading} showSearch allowClear treeData={field.treeData} {...common} />
    if (field.type === 'password') return <Input.Password disabled={readOnly || field.readOnly || loading} {...common} />
    if (field.type === 'textarea') return <Input.TextArea disabled={readOnly || field.readOnly || loading} {...common} />
    return <Input disabled={readOnly || field.readOnly || loading} {...common} />
  }

  return (
    <Drawer
      width={480}
      title={title}
      visible={visible}
      footer={readOnly ? null : undefined}
      okText="保存"
      confirmLoading={loading}
      maskClosable={!loading}
      closable={!loading}
      okButtonProps={{ disabled: loading || submitDisabled }}
      onCancel={handleClose}
      onOk={() => { if (!loading && !submitDisabled) form.submit() }}
      unmountOnExit
    >
      {topError && <Alert type="error" content={topError} style={{ marginBottom: 16 }} />}
      <Form form={form} layout="vertical" disabled={readOnly || loading} onSubmit={(values) => { if (!loading && !submitDisabled) onSubmit(values) }} onChange={() => setDirty(true)}>
        {fields.map((field) => (
          <Form.Item
            key={field.name}
            field={field.name}
            label={field.label}
            triggerPropName={field.type === 'switch' ? 'checked' : 'value'}
            rules={[...(field.required ? [{ required: true, message: '请输入' + field.label }] : []), ...(field.rules ?? [])]}
          >
            {renderField(field)}
          </Form.Item>
        ))}
      </Form>
    </Drawer>
  )
}
